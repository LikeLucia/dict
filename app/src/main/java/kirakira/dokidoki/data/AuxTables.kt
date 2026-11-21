package kirakira.dokidoki.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class AuxTables(context: Context) {
    private val tables: Map<AuxType, Map<String, String>> = load(context.applicationContext)
    private val jsonCache = HashMap<AuxType, String>()

    fun map(type: AuxType): Map<String, String> = tables[type] ?: emptyMap()

    @Synchronized
    fun json(type: AuxType): String = jsonCache.getOrPut(type) {
        val map = map(type)
        val sb = StringBuilder(map.size * 18)
        sb.append('{')
        var first = true
        for ((char, code) in map) {
            if (!first) sb.append(',')
            first = false
            sb.append(JSONObject.quote(char)).append(':').append(JSONObject.quote(code))
        }
        sb.append('}')
        sb.toString()
    }

    private fun load(context: Context): Map<AuxType, Map<String, String>> {
        val result = LinkedHashMap<AuxType, MutableMap<String, String>>()
        for (type in AuxType.entries) result[type] = HashMap(100_000)
        context.assets.open("aux_tables.tsv.deflate").use { raw ->
            val inflater = Inflater(true)
            try {
                InflaterInputStream(raw, inflater).use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val parts = line.split('\t', limit = 5)
                            if (parts.size < 5) return@forEach
                            val char = parts[0]
                            if (parts[1].isNotEmpty() && parts[1] != "-") result[AuxType.MOQI]!![char] = parts[1]
                            if (parts[2].isNotEmpty() && parts[2] != "-") result[AuxType.ZIRAN]!![char] = parts[2]
                            if (parts[3].isNotEmpty() && parts[3] != "-") result[AuxType.XIAOHE]!![char] = parts[3]
                            if (parts[4].isNotEmpty() && parts[4] != "-") result[AuxType.YUANMO]!![char] = parts[4]
                        }
                    }
                }
            } finally {
                inflater.end()
            }
        }
        return result
    }
}