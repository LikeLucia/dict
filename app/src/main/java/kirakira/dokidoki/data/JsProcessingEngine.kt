package kirakira.dokidoki.data

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

class ProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

private const val LINE_CHUNK = 256
private const val MAX_LINE_BYTES = 256 * 1024
private const val MAX_BATCH_BYTES = 2 * 1024 * 1024
private const val MAX_ZIP_ENTRY_BYTES = 256L * 1024L * 1024L
private const val MAX_ZIP_TOTAL_BYTES = 512L * 1024L * 1024L
private const val MAX_ZIP_ENTRIES = 10_000
private const val POLYPHONIC_BATCH = 256
private const val READ_BUFFER_CHARS = 64 * 1024
private const val PROGRESS_INTERVAL_NS = 80_000_000L
private val SAMPLE_REGEX = Regex("第 (\\d+) 行 ([^：]+)：(.*)")

/**
 * Runs the original dict processing script inside a small embedded QuickJS runtime.
 * The UI only sees [ProcessingEngine], so the scripting layer can be replaced by native Kotlin later.
 */
interface JsStringProvider {
    fun value(): String
}

private class MutableJsStringProvider(var text: String) : JsStringProvider {
    override fun value(): String = text
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count: Long = 0L
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) count++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) count += read
        return read
    }
}

private class LineBudgetInputStream(input: InputStream, private val checkActive: () -> Unit) : FilterInputStream(input) {
    private var lineBytes = 0
    private var bytesSinceCheck = 0
    override fun read(): Int = super.read().also { if (it >= 0) account(byteArrayOf(it.toByte())) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) account(buffer, offset, it) }
    private fun account(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size) {
        for (i in offset until offset + count) {
            val value = bytes[i].toInt() and 0xff
            if (value == 10 || value == 13) lineBytes = 0 else {
                lineBytes++
                if (lineBytes > MAX_LINE_BYTES) throw ProcessingException("单行超过 " + (MAX_LINE_BYTES / 1024) + " KiB 字节预算，未截断输入")
            }
            if (++bytesSinceCheck >= 4096) { checkActive(); bytesSinceCheck = 0 }
        }
    }
}

private class ZipBudgetInputStream(input: InputStream, private val entryName: String, private val budget: ZipDecompressionBudget, private val checkActive: () -> Unit) : FilterInputStream(input) {
    override fun read(): Int = super.read().also { if (it >= 0) account(1) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) account(it) }
    private fun account(bytes: Int) { checkActive(); budget.add(bytes, entryName) }
}

private class ZipDecompressionBudget {
    var totalBytes: Long = 0L
        private set
    private var entryBytes: Long = 0L
    fun startEntry() { entryBytes = 0L }
    fun add(bytes: Int, entryName: String) {
        entryBytes += bytes
        totalBytes += bytes
        if (entryBytes > MAX_ZIP_ENTRY_BYTES) throw ProcessingException("ZIP 条目 " + entryName + " 解压超过 256 MiB 预算")
        if (totalBytes > MAX_ZIP_TOTAL_BYTES) throw ProcessingException("ZIP 总解压量超过 512 MiB 预算")
    }
}

private class BufferedLineReader(
    private val reader: Reader,
    private val checkActive: () -> Unit,
    bufferSize: Int = READ_BUFFER_CHARS,
) {
    private val buffer = CharArray(bufferSize)
    private var position = 0
    private var limit = 0
    private var lookahead = NO_LOOKAHEAD

    fun forEachLine(onLine: (String, String) -> Unit) {
        val line = StringBuilder(128)
        var charsSinceCheck = 0
        while (true) {
            val code = readChar()
            if (++charsSinceCheck >= 4096) { checkActive(); charsSinceCheck = 0 }
            if (code == -1) {
                if (line.isNotEmpty()) onLine(line.toString(), "")
                return
            }
            when (code.toChar()) {
                '\r' -> {
                    val next = readChar()
                    if (next == '\n'.code) {
                        onLine(line.toString(), "\r\n")
                    } else {
                        lookahead = next
                        onLine(line.toString(), "\r")
                    }
                    line.setLength(0)
                }
                '\n' -> {
                    onLine(line.toString(), "\n")
                    line.setLength(0)
                }
                else -> {
                    if (line.length >= MAX_LINE_BYTES) throw ProcessingException("单行超过 " + (MAX_LINE_BYTES / 1024) + " KiB 字符预算，未截断输入")
                    line.append(code.toChar())
                }
            }
        }
    }

    fun close() = reader.close()

    private fun readChar(): Int {
        if (lookahead != NO_LOOKAHEAD) {
            val value = lookahead
            lookahead = NO_LOOKAHEAD
            return value
        }
        if (position >= limit) {
            checkActive()
            limit = reader.read(buffer)
            position = 0
            if (limit <= 0) return -1
        }
        return buffer[position++].code
    }

    private companion object {
        const val NO_LOOKAHEAD = -2
    }
}

class JsProcessingEngine(context: Context) : ProcessingEngine {
    private val appContext = context.applicationContext
    private val auxTables by lazy { AuxTables(appContext) }
    private val mutex = Mutex()
    private val quickJsDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dict-quickjs").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val closed = AtomicBoolean(false)
    private var quickJs: QuickJs? = null
    private var loadedTableKey: String? = null
    private var tableProvider: MutableJsStringProvider? = null
    private var lineProvider: MutableJsStringProvider? = null
    private var weightProvider: MutableJsStringProvider? = null

    override suspend fun process(
        file: InputFile,
        options: ProcessOptions,
        onProgress: (Float) -> Unit,
    ): ProcessOutcome {
        if (file.name.endsWith(".tsv", ignoreCase = true)) {
            throw ProcessingException("暂不支持 TSV 文件")
        }
        var completedOutcome: ProcessOutcome? = null
        try {
            return withContext(quickJsDispatcher) {
                mutex.withLock {
                    if (closed.get()) throw ProcessingException("处理引擎已关闭")
                    val started = System.nanoTime()
                    try {
                        ensureLoaded()
                        val js = quickJs ?: error("JS runtime unavailable")
                        val tableKey = if (options.mode == ProcessMode.PINYIN_ONLY || options.mode == ProcessMode.AUX_STRIP) {
                            "none"
                        } else {
                            options.auxType.key
                        }
                        if (loadedTableKey != tableKey) {
                            val tableJson = if (tableKey == "none") "{}" else auxTables.json(options.auxType)
                            val provider = tableProvider ?: throw ProcessingException("表格数据通道未初始化")
                            provider.text = tableJson
                            js.evaluate("__setTable(" + JSONObject.quote(tableKey) + ",__tableString.value())")
                            loadedTableKey = tableKey
                        }
                        val activeContext = coroutineContext
                        val checkActive: () -> Unit = { activeContext.ensureActive() }
                        val outcome = if (file.name.endsWith(".zip", ignoreCase = true)) {
                            processZip(js, file, options, started, onProgress, checkActive)
                        } else {
                            processText(js, file, options, started, onProgress, checkActive)
                        }
                        completedOutcome = outcome
                        outcome
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ProcessingException) {
                        throw e
                    } catch (e: OutOfMemoryError) {
                        throw ProcessingException("处理该词库时内存不足，请尝试关闭其他应用后重试", e)
                    } catch (e: LinkageError) {
                        throw ProcessingException("内置拼音引擎加载失败，请重新安装应用后重试", e)
                    } catch (e: Exception) {
                        throw ProcessingException(e.message ?: "处理失败", e)
                    } catch (e: Throwable) {
                        throw ProcessingException("处理失败，请检查词库文件后重试", e)
                    }
                }
                    }
        } catch (error: Throwable) {
            completedOutcome?.let { outcome ->
                outcome.outputs.forEach { output -> runCatching { output.file.delete() } }
                outcome.result.ambiguousFiles.forEach { summary ->
                    runCatching { File(summary.dataPath).delete() }
                    runCatching { File(summary.indexPath).delete() }
                }
            }
            throw error
        }
    }

    suspend fun prewarm() = withContext(quickJsDispatcher) {
        mutex.withLock {
            if (!closed.get()) runCatching { ensureLoaded() }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        quickJsDispatcher.dispatch(EmptyCoroutineContext) {
            try {
                quickJs?.close()
            } finally {
                quickJs = null
                loadedTableKey = null
                tableProvider = null
                lineProvider = null
                weightProvider = null
                quickJsDispatcher.close()
            }
        }
    }

    private fun ensureLoaded() {
        if (quickJs != null) return
        val js = QuickJs.create()
        var transferred = false
        try {
            val pinyin = appContext.assets.open("pinyin-pro.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val engine = appContext.assets.open("dict_engine.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
            js.evaluate(pinyin, "pinyin-pro.js")
            js.evaluate(engine, "dict_engine.js")
            val tables = MutableJsStringProvider("{}")
            val lines = MutableJsStringProvider("[]")
            val weights = MutableJsStringProvider(loadPinyinWeights())
            js.set("__tableString", JsStringProvider::class.java, tables)
            js.set("__linesString", JsStringProvider::class.java, lines)
            js.set("__weightString", JsStringProvider::class.java, weights)
            js.evaluate("__setPinyinWeights(__weightString.value())")
            tableProvider = tables
            lineProvider = lines
            weightProvider = weights
            quickJs = js
            loadedTableKey = null
            transferred = true
        } catch (e: LinkageError) {
            throw ProcessingException("内置拼音引擎加载失败，请重新安装应用后重试", e)
        } catch (e: Exception) {
            throw ProcessingException("内置拼音引擎加载失败", e)
        } finally {
            if (!transferred) {
                tableProvider = null
                lineProvider = null
                weightProvider = null
                quickJs = null
                loadedTableKey = null
                runCatching { js.close() }
            }
        }
    }

    private fun loadPinyinWeights(): String {
        return appContext.assets.open("zi_weights.json.deflate").use { raw ->
            val inflater = Inflater(true)
            try {
                InflaterInputStream(raw, inflater).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                inflater.end()
            }
        }
    }

    private fun processText(
        js: QuickJs,
        file: InputFile,
        options: ProcessOptions,
        started: Long,
        onProgress: (Float) -> Unit,
        checkActive: () -> Unit,
    ): ProcessOutcome {
        checkActive()
        val outputFile = newOutputFile(".txt")
        var source: CountingInputStream? = null
        var detailWriter: AmbiguousDetailWriter? = null
        var success = false
        try {
            val writer = AmbiguousDetailWriter(File(appContext.noBackupFilesDir, "kirakira-session/details"))
            detailWriter = writer
            val totalBytes = file.file.length().coerceAtLeast(1L)
            val activeSource = CountingInputStream(FileInputStream(file.file).buffered(READ_BUFFER_CHARS))
            source = activeSource
            val lineInput = activeSource
            var lastProgressAt = 0L
            fun reportProgress(force: Boolean = false) {
                val now = System.nanoTime()
                if (!force && now - lastProgressAt < PROGRESS_INTERVAL_NS) return
                lastProgressAt = now
                onProgress((activeSource.count.toFloat() / totalBytes).coerceIn(0f, 0.99f))
            }
            onProgress(0f)
            val out = BufferedOutputStream(
                SpaceCheckingOutputStream(FileOutputStream(outputFile), outputFile.parentFile!!),
                READ_BUFFER_CHARS,
            )
            try {
                val report = processLines(
                    js, lineInput, options, out, checkActive, closeInput = true,
                    fileName = file.name, fileId = file.id,
                    fileModifiedAt = file.file.lastModified(), detailWriter = writer,
                ) { reportProgress() }
                out.flush()
                writer.close()
                reportProgress(force = true)
                val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
                val result = buildResult(file.name, report, elapsed, writer.summaries())
                checkActive()
                val outcome = ProcessOutcome(result, listOf(ProcessOutput(file.name, outputFile, outputFile.length(), result)))
                onProgress(1f)
                success = true
                return outcome
            } finally {
                runCatching { out.close() }
            }
        } finally {
            runCatching { source?.close() }
            runCatching { detailWriter?.close() }
            if (!success) {
                runCatching { outputFile.delete() }
                runCatching { detailWriter?.deleteFiles() }
            }
        }
    }

    private fun processZip(
        js: QuickJs,
        file: InputFile,
        options: ProcessOptions,
        started: Long,
        onProgress: (Float) -> Unit,
        checkActive: () -> Unit,
    ): ProcessOutcome {
        checkActive()
        val outputFile = newOutputFile(".zip")
        var detailWriter: AmbiguousDetailWriter? = null
        var source: CountingInputStream? = null
        var success = false
        try {
            val writer = AmbiguousDetailWriter(File(appContext.noBackupFilesDir, "kirakira-session/details"))
            detailWriter = writer
            val totalBytes = file.file.length().coerceAtLeast(1L)
            val activeSource = CountingInputStream(FileInputStream(file.file).buffered(READ_BUFFER_CHARS))
            source = activeSource
            var zipIn: ZipInputStream? = null
            var out: BufferedOutputStream? = null
            var zipOut: ZipOutputStream? = null
            var entries = 0
            val zipBudget = ZipDecompressionBudget()
            var lastProgressAt = 0L
            fun reportProgress(force: Boolean = false) {
                val now = System.nanoTime()
                if (!force && now - lastProgressAt < PROGRESS_INTERVAL_NS) return
                lastProgressAt = now
                onProgress((activeSource.count.toFloat() / totalBytes).coerceIn(0f, 0.99f))
            }
            onProgress(0f)
            var totalLines = 0
            var pinyinAdded = 0
            var auxReplaced = 0
            var ambiguous = 0
            val counts = linkedMapOf<String, Int>()
            val missing = StringBuilder()
            try {
                val activeZipIn = ZipInputStream(activeSource, Charsets.UTF_8).also { zipIn = it }
                val activeOut = BufferedOutputStream(
                    SpaceCheckingOutputStream(FileOutputStream(outputFile), outputFile.parentFile!!),
                    READ_BUFFER_CHARS,
                ).also { out = it }
                val activeZipOut = ZipOutputStream(activeOut, Charsets.UTF_8).also { zipOut = it }
                while (true) {
                    checkActive()
                    val entry = activeZipIn.nextEntry ?: break
                    entries++
                    if (entries > MAX_ZIP_ENTRIES) throw ProcessingException("ZIP 条目数超过 10000 预算")
                    val name = entry.name
                    if (entry.size > MAX_ZIP_ENTRY_BYTES) throw ProcessingException("ZIP 条目 $name 解压超过 256 MiB 预算")
                    zipBudget.startEntry()
                    val boundedEntry = ZipBudgetInputStream(activeZipIn, name, zipBudget, checkActive)
                    if (entry.isDirectory) {
                        val discard = ByteArray(READ_BUFFER_CHARS)
                        while (true) { checkActive(); val read = boundedEntry.read(discard); if (read < 0) break }
                        activeZipIn.closeEntry()
                        activeZipOut.putNextEntry(ZipEntry(name))
                        activeZipOut.closeEntry()
                        continue
                    }
                    val isDictionary = name.endsWith(".yaml", true) || name.endsWith(".yml", true) ||
                        name.endsWith(".txt", true)
                    if (!isDictionary) {
                        activeZipOut.putNextEntry(ZipEntry(name))
                        copyStream(boundedEntry, activeZipOut, checkActive)
                        activeZipOut.closeEntry()
                        activeZipIn.closeEntry()
                        reportProgress(force = true)
                        continue
                    }
                    activeZipOut.putNextEntry(ZipEntry(name))
                    val report = processLines(
                        js, boundedEntry, options, activeZipOut, checkActive, closeInput = false,
                        fileName = file.name + " / " + name, fileId = file.id + "/" + entries,
                        fileModifiedAt = file.file.lastModified(), detailWriter = writer,
                    ) { reportProgress() }
                    activeZipIn.closeEntry()
                    activeZipOut.closeEntry()
                    totalLines += report.optInt("no", 0)
                    pinyinAdded += report.optInt("pyAdded", 0)
                    auxReplaced += report.optInt("auxAdded", 0)
                    ambiguous += countPolyphonic(report)
                    mergeCounts(report, counts)
                    val miss = report.optJSONArray("missing")
                    if (miss != null) for (i in 0 until miss.length()) missing.append(miss.optString(i))
                }
                if (entries == 0) throw ProcessingException("ZIP 中没有可读取的条目")
                activeZipOut.finish()
                activeOut.flush()
                writer.close()
            } finally {
                runCatching { zipOut?.close() }
                runCatching { out?.close() }
                runCatching { zipIn?.close() }
            }
            checkActive()
            val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
            val summaries = writer.summaries()
            val result = ProcessResult(
                totalLines = totalLines,
                pinyinAdded = pinyinAdded,
                auxReplaced = auxReplaced,
                ambiguousCount = ambiguous,

                elapsedSeconds = elapsed,
                outputName = file.name,
                counts = counts,
                samples = emptyList(),
                missingChars = missing.toString(),
                ambiguousFiles = summaries,
            )
            val outcome = ProcessOutcome(result, listOf(ProcessOutput(file.name, outputFile, outputFile.length(), result)))
            onProgress(1f)
            success = true
            return outcome
        } finally {
            runCatching { source?.close() }
            runCatching { detailWriter?.close() }
            if (!success) {
                runCatching { outputFile.delete() }
                runCatching { detailWriter?.deleteFiles() }
            }
        }
    }

    private fun processLines(
        js: QuickJs,
        input: InputStream,
        options: ProcessOptions,
        output: OutputStream,
        checkActive: () -> Unit,
        closeInput: Boolean,
        fileName: String,
        fileId: String,
        fileModifiedAt: Long,
        detailWriter: AmbiguousDetailWriter,
        onProgress: () -> Unit,
    ): JSONObject {
        var provider: MutableJsStringProvider? = null
        var reader: BufferedLineReader? = null
        try {
            startState(js, options)
            val activeProvider = lineProvider ?: throw ProcessingException("行数据通道未初始化")
            provider = activeProvider
            val chunkLines = ArrayList<JSONObject>(LINE_CHUNK)
            val chunkDelimiters = ArrayList<String>(LINE_CHUNK)
            var chunkBytes = 0
            val encoding = Charset.forName(options.inputEncoding.engineValue)

            fun flushChunk() {
                if (chunkLines.isEmpty()) return
                val json = JSONArray()
                chunkLines.forEach { json.put(it) }
                activeProvider.text = json.toString()
                val resultJson = js.evaluate("__lines(JSON.parse(__linesString.value()))") as? String
                    ?: throw ProcessingException("拼音引擎返回为空")
                val result = JSONArray(resultJson)
                if (result.length() != chunkLines.size) throw ProcessingException("拼音引擎行数不匹配")
                for (i in chunkLines.indices) {
                    checkActive()
                    if (result.isNull(i)) continue
                    output.write(result.getString(i).toByteArray(Charsets.UTF_8))
                    output.write(chunkDelimiters[i].toByteArray(Charsets.UTF_8))
                }
                chunkLines.clear()
                chunkDelimiters.clear()
                chunkBytes = 0
                do {
                    checkActive()
                    val detailJson = js.evaluate("__takePolyphonicDetails($POLYPHONIC_BATCH)") as? String
                        ?: throw ProcessingException("多音字详情返回为空")
                    val details = JSONArray(detailJson)
                    detailWriter.append(details, fileName, fileId, fileModifiedAt)
                } while (details.length() == POLYPHONIC_BATCH)
                activeProvider.text = "[]"
                onProgress()
            }

            fun addLine(text: String, delimiter: String) {
                checkActive()
                val lineBytes = ((text.length.toLong() + delimiter.length) * 6L + 64L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (lineBytes > MAX_BATCH_BYTES) {
                    throw ProcessingException("单行 JSON 批次估算超过 " + MAX_BATCH_BYTES + " 字节预算，未截断输入")
                }
                if (chunkLines.isNotEmpty() && (chunkLines.size >= LINE_CHUNK || chunkBytes + lineBytes > MAX_BATCH_BYTES)) flushChunk()
                chunkLines.add(JSONObject().put("text", text).put("delimiter", delimiter))
                chunkDelimiters.add(delimiter)
                chunkBytes += lineBytes
                if (chunkLines.size % 64 == 0) {
                    checkActive()
                    onProgress()
                }
                if (chunkLines.size >= LINE_CHUNK || chunkBytes >= MAX_BATCH_BYTES) flushChunk()
            }

            val decoder = encoding.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val boundedInput = LineBudgetInputStream(input, checkActive)
            reader = BufferedLineReader(InputStreamReader(boundedInput, decoder), checkActive)
            reader.forEachLine { text, delimiter -> addLine(text, delimiter) }
            flushChunk()
            val report = JSONObject(js.evaluate("__report()") as? String ?: "{}")
            if (report.optString("phase") == "header" || report.optString("autoPhase") == "header") {
                throw ProcessingException("Rime 头部缺少结束标记 ...，已停止处理")
            }
            return report
        } finally {
            if (closeInput) runCatching { reader?.close() }
            provider?.text = "[]"
            runCatching { js.evaluate("__clear()") }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream, checkActive: () -> Unit) {
        val buffer = ByteArray(READ_BUFFER_CHARS)
        while (true) {
            checkActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private fun newOutputFile(extension: String): File =
        File.createTempFile("dict-out-", extension, appContext.cacheDir)

    private fun startState(js: QuickJs, options: ProcessOptions) {
        val args = listOf(
            JSONObject.quote(options.auxType.key),
            JSONObject.quote(options.mode.engineValue),
            JSONObject.quote(options.tone.engineValue),
            JSONObject.quote(options.outputDelimiter.engineValue),
            JSONObject.quote(options.inputEncoding.engineValue),
            options.replaceAux.toString(),
            JSONObject.quote(options.auxSeparator),
            (options.wordColumn - 1).toString(),
            (options.pinyinColumn - 1).toString(),
            JSONObject.quote(options.weight.toString()),
            options.replacePinyin.toString(),
        ).joinToString(",")
        js.evaluate("__start($args)")
    }

    private fun buildResult(
        name: String,
        report: JSONObject,
        elapsed: Double,
        ambiguousFiles: List<AmbiguousFileSummary>,
    ): ProcessResult {
        val counts = linkedMapOf<String, Int>()
        mergeCounts(report, counts)
        val rawSamples = report.optJSONArray("samples")
        val missing = report.optJSONArray("missing")?.let { arr ->
            buildString { for (i in 0 until arr.length()) append(arr.optString(i)) }
        } ?: ""
        return ProcessResult(
            totalLines = report.optInt("no", 0),
            pinyinAdded = report.optInt("pyAdded", 0),
            auxReplaced = report.optInt("auxAdded", 0),
            ambiguousCount = countPolyphonic(report),

            elapsedSeconds = elapsed,
            outputName = name,
            counts = counts,
            samples = (0 until (rawSamples?.length() ?: 0)).map { rawSamples!!.optString(it) },
            missingChars = missing,
            ambiguousFiles = ambiguousFiles,
        )
    }

    private fun countPolyphonic(report: JSONObject): Int = report.optInt("polyphonic", 0)

    private fun mergeCounts(report: JSONObject, target: MutableMap<String, Int>) {
        val counts = report.optJSONObject("counts") ?: return
        val keys = counts.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            target[key] = (target[key] ?: 0) + counts.optInt(key, 0)
        }
    }

    private fun parseSample(raw: String): Pair<Int, String> {
        val m = SAMPLE_REGEX.find(raw)
        return if (m != null) {
            (m.groupValues[1].toIntOrNull() ?: 0) to (m.groupValues[2] + "：" + m.groupValues[3])
        } else {
            0 to raw
        }
    }
}
