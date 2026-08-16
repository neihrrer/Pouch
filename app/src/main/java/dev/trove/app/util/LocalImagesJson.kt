package dev.trove.app.util

/**
 * Minimal JSON object codec for the offline-image map
 * (original image URL -> local absolute path). URLs never contain raw
 * quotes/backslashes, so escaping stays simple.
 */
object LocalImagesJson {

    fun encode(map: Map<String, String>): String =
        map.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"${escape(k)}\":\"${escape(v)}\""
        }

    fun decode(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        val regex = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        for (m in regex.findAll(json)) {
            val key = unescape(m.groupValues[1])
            val value = unescape(m.groupValues[2])
            if (key.isNotEmpty()) map[key] = value
        }
        return map
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\\\", "\\")
}
