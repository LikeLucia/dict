package kirakira.dokidoki.data

object ExportNaming {
    fun safeName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return cleaned.ifBlank { "dict-output.txt" }
    }

    fun uniqueName(name: String, existing: Collection<String>): String {
        if (name !in existing) return name
        var index = 1
        while (true) {
            val candidate = numberedName(name, index)
            if (candidate !in existing) return candidate
            index++
        }
    }

    fun numberedName(name: String, index: Int): String {
        val lower = name.lowercase()
        val compound = listOf(".dict.yaml", ".dict.yml").firstOrNull { lower.endsWith(it) }
        val ext = when {
            compound != null -> name.substring(name.length - compound.length)
            else -> {
                val dot = name.lastIndexOf('.')
                if (dot > 0) name.substring(dot) else ""
            }
        }
        val base = name.substring(0, name.length - ext.length)
        return base + " (" + index + ")" + ext
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "yaml", "yml" -> "application/yaml"
        "txt" -> "text/plain"
        "tsv" -> "text/tab-separated-values"
        "zip" -> "application/zip"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
