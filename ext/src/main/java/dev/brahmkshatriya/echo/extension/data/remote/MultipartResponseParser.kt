package dev.brahmkshatriya.echo.extension.data.remote

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

object MultipartResponseParser {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun extractJsonFromMultipart(responseBody: String): String {
        var braceCount = 0
        var jsonStart = -1
        var jsonEnd = -1
        var inString = false
        var escapeNext = false
        
        for (i in responseBody.indices) {
            val char = responseBody[i]
            
            if (escapeNext) {
                escapeNext = false
                continue
            }
            
            if (char == '\\' && inString) {
                escapeNext = true
                continue
            }
            
            if (char == '"') {
                inString = !inString
                continue
            }
            
            if (!inString) {
                when (char) {
                    '{' -> {
                        if (braceCount == 0) {
                            jsonStart = i
                        }
                        braceCount++
                    }
                    '}' -> {
                        braceCount--
                        if (braceCount == 0 && jsonStart != -1) {
                            jsonEnd = i + 1
                            return responseBody.substring(jsonStart, jsonEnd)
                        }
                    }
                }
            }
        }
        
        if (jsonStart == -1) {
            throw Exception("No JSON found in response")
        }
        
        throw Exception("Incomplete JSON found in response (unclosed braces)")
    }

    fun <T> parse(jsonText: String, deserializer: DeserializationStrategy<T>): T {
        return json.decodeFromString(deserializer, jsonText)
    }
    
    fun <T> extractAndParse(responseBody: String, deserializer: DeserializationStrategy<T>): T {
        val jsonText = extractJsonFromMultipart(responseBody)
        return parse(jsonText, deserializer)
    }

    fun <T> encode(value: T, serializer: kotlinx.serialization.SerializationStrategy<T>): String {
        return json.encodeToString(serializer, value)
    }
}
