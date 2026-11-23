package kirakira.dokidoki.data

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.Reader

/** Version-1 details migrate one record at a time, never via readText/JSONArray of the full list. */
internal fun readSessionMetadata(source: Reader, detailDirectory: File): JSONObject {
    val created = mutableListOf<AmbiguousDetailWriter>()
    try {
        JsonReader(source).use { reader ->
            val root = JSONObject()
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name != "resultsByFile") {
                    root.put(name, readSessionValue(reader))
                    if (name == "version") require(root.optInt("version") in 1..2) { "不支持的记录版本" }
                    continue
                }
                val results = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val id = reader.nextName()
                    if (id.isBlank() || id.any { !it.isLetterOrDigit() && it != '-' }) {
                        reader.skipValue()
                        continue
                    }
                    val result = JSONObject()
                    var legacyWriter: AmbiguousDetailWriter? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (key != "ambiguousLines") {
                            result.put(key, readSessionValue(reader))
                            continue
                        }
                        val writer = AmbiguousDetailWriter(detailDirectory)
                        legacyWriter = writer
                        created.add(writer)
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val entry = readSessionValue(reader) as? JSONObject
                                ?: error("旧多音字记录格式无效")
                            writer.appendEncodedEntry(entry)
                        }
                        reader.endArray()
                        writer.close()
                    }
                    reader.endObject()
                    legacyWriter?.let { writer ->
                        if (result.has("ambiguousFiles")) {
                            writer.deleteFiles()
                        } else {
                            result.put("ambiguousFiles", JSONArray().apply {
                                writer.summaries().forEach { summary ->
                                    put(JSONObject().apply {
                                        put("fileId", summary.fileId)
                                        put("fileName", summary.fileName)
                                        put("count", summary.count)
                                        put("dataFile", File(summary.dataPath).name)
                                        put("indexFile", File(summary.indexPath).name)
                                        put("startIndex", summary.startIndex)
                                        put("modifiedAt", summary.modifiedAt)
                                    })
                                }
                            })
                        }
                    }
                    results.put(id, result)
                }
                reader.endObject()
                root.put(name, results)
            }
            reader.endObject()
            require(root.optInt("version") in 1..2) { "不支持的记录版本" }
            return root
        }
    } catch (error: Throwable) {
        created.forEach { writer -> runCatching { writer.deleteFiles() } }
        throw error
    }
}

private fun readSessionValue(reader: JsonReader): Any? = when (reader.peek()) {
    JsonToken.BEGIN_OBJECT -> JSONObject().apply {
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            put(key, readSessionValue(reader))
        }
        reader.endObject()
    }
    JsonToken.BEGIN_ARRAY -> JSONArray().apply {
        reader.beginArray()
        while (reader.hasNext()) put(readSessionValue(reader))
        reader.endArray()
    }
    JsonToken.STRING -> reader.nextString()
    JsonToken.NUMBER -> reader.nextString().let { it.toLongOrNull() ?: it.toDouble() }
    JsonToken.BOOLEAN -> reader.nextBoolean()
    JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
    else -> error("本地记录 JSON 格式无效")
}
