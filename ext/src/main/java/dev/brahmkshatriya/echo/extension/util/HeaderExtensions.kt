package dev.brahmkshatriya.echo.extension.util

import okhttp3.Headers

fun Map<String, String>.toHeaders(): Headers {
    return Headers.Builder().apply {
        forEach { (key, value) -> add(key, value) }
    }.build()
}
