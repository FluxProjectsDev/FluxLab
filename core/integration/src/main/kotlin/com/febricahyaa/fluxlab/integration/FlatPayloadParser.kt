package com.febricahyaa.fluxlab.integration

data class FlatPayload(
    val fields: Map<String, String>,
    val warnings: List<String>,
    val json: Boolean,
)

object FlatPayloadParser {
    fun parse(input: String): FlatPayload {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return FlatPayload(emptyMap(), listOf("empty payload"), false)
        return if (trimmed.startsWith('{')) parseJsonObject(trimmed) else parseLines(trimmed)
    }

    private fun parseLines(input: String): FlatPayload {
        val fields = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        input.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val equals = line.indexOf('=')
            val whitespace = line.indexOfFirst(Char::isWhitespace)
            val split = when {
                equals > 0 && (whitespace < 0 || equals < whitespace) -> equals
                whitespace > 0 -> whitespace
                else -> -1
            }
            if (split < 1) {
                warnings += "line ${index + 1} has no value"
                return@forEachIndexed
            }
            val key = line.substring(0, split).trim()
            val value = line.substring(split + 1).trim()
            if (fields.put(key, value) != null) warnings += "duplicate field: $key"
        }
        return FlatPayload(fields, warnings, false)
    }

    private fun parseJsonObject(input: String): FlatPayload {
        val fields = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        var index = 1

        fun skipSpace() { while (index < input.length && input[index].isWhitespace()) index++ }
        fun string(): String? {
            if (index >= input.length || input[index] != '"') return null
            index++
            val out = StringBuilder()
            while (index < input.length) {
                val char = input[index++]
                when {
                    char == '"' -> return out.toString()
                    char == '\\' && index < input.length -> {
                        val escaped = input[index++]
                        out.append(
                            when (escaped) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'b' -> '\b'
                                'f' -> '\u000C'
                                else -> escaped
                            },
                        )
                    }
                    else -> out.append(char)
                }
            }
            return null
        }

        skipSpace()
        while (index < input.length) {
            skipSpace()
            if (index < input.length && input[index] == '}') {
                index++
                break
            }
            val key = string()
            if (key == null) {
                warnings += "incomplete JSON key"
                break
            }
            skipSpace()
            if (index >= input.length || input[index++] != ':') {
                warnings += "missing colon after $key"
                break
            }
            skipSpace()
            val value = if (index < input.length && input[index] == '"') {
                string()
            } else {
                val start = index
                var nested = 0
                while (index < input.length) {
                    val char = input[index]
                    if (char == '[' || char == '{') nested++
                    if (char == ']' || char == '}') {
                        if (nested == 0) break
                        nested--
                    }
                    if (char == ',' && nested == 0) break
                    index++
                }
                input.substring(start, index).trim().takeIf { it.isNotEmpty() }
            }
            if (value == null) {
                warnings += "missing value for $key"
                break
            }
            if (fields.put(key, value) != null) warnings += "duplicate field: $key"
            skipSpace()
            when {
                index >= input.length -> {
                    warnings += "JSON object was not closed"
                    break
                }
                input[index] == ',' -> index++
                input[index] == '}' -> {
                    index++
                    break
                }
                else -> {
                    warnings += "unexpected character after $key"
                    break
                }
            }
        }
        skipSpace()
        if (index < input.length) warnings += "trailing JSON content"
        return FlatPayload(fields, warnings, true)
    }
}
