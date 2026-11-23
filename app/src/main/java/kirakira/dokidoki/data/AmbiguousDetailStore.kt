package kirakira.dokidoki.data

import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

internal const val MIN_FREE_SPACE_BYTES = 4L * 1024L * 1024L
private const val MAX_DETAIL_RECORD_BYTES = 16 * 1024 * 1024
private const val JSONL_INDEX_RECORD_BYTES = 8L
private const val PAGE_BYTES = 2 * 1024 * 1024

internal class SpaceCheckingOutputStream(
    private val delegate: OutputStream,
    private val directory: File,
) : OutputStream() {
    override fun write(value: Int) {
        ensureSpace(1)
        delegate.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureSpace(length)
        delegate.write(buffer, offset, length)
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()

    private fun ensureSpace(nextBytes: Int) {
        val available = StatFs(directory.absolutePath).availableBytes
        if (available < MIN_FREE_SPACE_BYTES + nextBytes) {
            throw java.io.IOException("磁盘空间不足：临时写入至少保留 4 MiB 可用空间")
        }
    }
}

/** Writes full-detail records incrementally; memory retains only one detail batch and group offsets. */
internal class AmbiguousDetailWriter(private val directory: File) : AutoCloseable {
    private data class MutableGroup(
        val fileId: String,
        val fileName: String,
        val startIndex: Int,
        var count: Int,
        var modifiedAt: Long,
    )

    private var dataFile: File? = null
    private var indexFile: File? = null
    private var dataOutput: OutputStream? = null
    private var indexOutput: DataOutputStream? = null
    private val groups = LinkedHashMap<String, MutableGroup>()
    private var dataLength = 0L
    private var recordCount = 0
    private var closed = false

    fun append(details: JSONArray, fileName: String, fileId: String, modifiedAt: Long) {
        if (details.length() == 0) return
        repeat(details.length()) { row ->
            val detail = details.optJSONObject(row) ?: return@repeat
            val readings = detail.optJSONArray("readings") ?: JSONArray()
            val characters = LinkedHashSet<String>()
            repeat(readings.length()) { readingIndex ->
                readings.optJSONObject(readingIndex)?.optString("character")?.takeIf { it.isNotEmpty() }?.let(characters::add)
            }
            val word = detail.optString("word")
            writeRecord(JSONObject().apply {
                put("fileName", fileName)
                put("fileId", fileId)
                put("line", detail.optInt("line"))
                put("message", "多音字：$word（" + characters.joinToString("、") + "）")
                put("pinyin", detail.optString("pinyin"))
                put("readings", readings)
            }, fileName, fileId, modifiedAt)
        }
    }

    fun appendEncodedEntry(item: JSONObject) {
        val fileName = item.optString("fileName")
        val fileId = item.optString("fileId", fileName)
        if (!item.has("fileId")) item.put("fileId", fileId)
        writeRecord(item, fileName, fileId, 0L)
    }

    private fun writeRecord(item: JSONObject, fileName: String, fileId: String, modifiedAt: Long) {
        check(!closed) { "多音字明细写入器已关闭" }
        ensureOpen()
        val out = requireNotNull(dataOutput)
        val idx = requireNotNull(indexOutput)
        val group = groups.getOrPut(fileId) { MutableGroup(fileId, fileName, recordCount, 0, modifiedAt) }
        val bytes = item.toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_DETAIL_RECORD_BYTES) throw ProcessingException("多音字明细记录超过 16 MiB，已停止并清除临时文件")
        val offset = dataLength
        out.write(bytes)
        out.write(10)
        dataLength += bytes.size + 1L
        idx.writeLong(offset)
        group.count++
        recordCount++
    }

    fun summaries(): List<AmbiguousFileSummary> = groups.values.map { group ->
        AmbiguousFileSummary(
            fileId = group.fileId,
            fileName = group.fileName,
            count = group.count,
            dataPath = requireNotNull(dataFile).absolutePath,
            indexPath = requireNotNull(indexFile).absolutePath,
            startIndex = group.startIndex,
            modifiedAt = group.modifiedAt,
        )
    }

    private fun ensureOpen() {
        if (dataOutput != null) return
        if (!directory.exists() && !directory.mkdirs()) throw java.io.IOException("无法创建多音字临时目录")
        dataFile = File.createTempFile("dict-details-", ".jsonl", directory)
        indexFile = File.createTempFile("dict-details-", ".idx", directory)
        dataOutput = BufferedOutputStream(SpaceCheckingOutputStream(FileOutputStream(dataFile!!), directory), 64 * 1024)
        indexOutput = DataOutputStream(BufferedOutputStream(SpaceCheckingOutputStream(FileOutputStream(indexFile!!), directory), 32 * 1024))
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            dataOutput?.flush()
            indexOutput?.flush()
        } finally {
            runCatching { dataOutput?.close() }
            runCatching { indexOutput?.close() }
            dataOutput = null
            indexOutput = null
        }
    }

    fun deleteFiles() {
        try {
            close()
        } finally {
            dataFile?.delete()
            indexFile?.delete()
        }
    }
}

data class AmbiguousDetailPage(val entries: List<AmbiguousEntry>, val nextOffset: Int?)

internal object AmbiguousDetailReader {
    fun readPage(
        summary: AmbiguousFileSummary,
        offset: Int,
        limit: Int,
        query: String,
        checkActive: () -> Unit = {},
    ): AmbiguousDetailPage {
        require(offset >= 0 && limit in 1..500)
        val q = query.trim()
        val numeric = q.isNotEmpty() && q.all(Char::isDigit)
        val exact = q.drop(1).takeIf { q.startsWith("l", true) && it.isNotEmpty() && it.all(Char::isDigit) }
        val firstRecord = if (q.isEmpty()) offset else 0
        if (firstRecord >= summary.count) return AmbiguousDetailPage(emptyList(), null)
        val dataOffset = RandomAccessFile(summary.indexPath, "r").use { index ->
            index.seek((summary.startIndex.toLong() + firstRecord) * JSONL_INDEX_RECORD_BYTES)
            index.readLong()
        }
        val found = ArrayList<AmbiguousEntry>(limit)
        var matched = firstRecord
        var pageBytes = 0
        FileInputStream(summary.dataPath).use { file ->
            file.channel.position(dataOffset)
            val input = BufferedInputStream(file, 64 * 1024)
            for (local in firstRecord until summary.count) {
                checkActive()
                val bytes = readRecord(input)
                // Unfiltered pages start directly at their index offset. Search scans the group
                // with a bounded buffer, without materializing prior matches or a whole-file index.
                val entry = decodeEntry(String(bytes, StandardCharsets.UTF_8))
                if (!matches(entry, q, numeric, exact)) continue
                if (matched++ < offset) continue
                if (found.size == limit || (found.isNotEmpty() && pageBytes + bytes.size > PAGE_BYTES)) {
                    return AmbiguousDetailPage(found, offset + found.size)
                }
                found.add(entry)
                pageBytes += bytes.size
            }
        }
        return AmbiguousDetailPage(found, null)
    }

    private fun readRecord(input: InputStream): ByteArray {
        val bytes = ByteArrayOutputStream(512)
        while (true) {
            val b = input.read()
            if (b == 10) return bytes.toByteArray()
            if (b < 0) throw java.io.EOFException("多音字明细记录不完整")
            if (bytes.size() >= MAX_DETAIL_RECORD_BYTES) error("多音字明细记录超过 16 MiB")
            bytes.write(b)
        }
    }

    private fun decodeEntry(raw: String): AmbiguousEntry {
        val item = JSONObject(raw)
        val readingsJson = item.optJSONArray("readings") ?: JSONArray()
        val readings = List(readingsJson.length()) { i ->
            val reading = readingsJson.getJSONObject(i)
            val others = reading.optJSONArray("others") ?: JSONArray()
            AmbiguousReading(
                character = reading.optString("character"),
                used = reading.optString("used"),
                others = List(others.length()) { others.optString(it) },
                toneUnspecified = reading.optBoolean("toneUnspecified"),
            )
        }
        return AmbiguousEntry(
            fileName = item.optString("fileName"), line = item.optInt("line"), message = item.optString("message"),
            fileId = item.optString("fileId"), pinyin = item.optString("pinyin"), readings = readings,
        )
    }

    private fun matches(entry: AmbiguousEntry, q: String, numeric: Boolean, lineQuery: String?): Boolean =
        q.isEmpty() || entry.message.contains(q, true) || entry.pinyin.contains(q, true) ||
            entry.readings.any { reading -> reading.character.contains(q) || reading.used.contains(q, true) || reading.others.any { it.contains(q, true) } } ||
            (numeric && entry.line.toString().contains(q)) || (lineQuery != null && entry.line.toString() == lineQuery)
}
