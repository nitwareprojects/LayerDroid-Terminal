package com.nitware.layerdroid.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object HttpClient {

    private const val USER_AGENT = "LayerDroid-Terminal/1.0 (Android)"
    private const val DEFAULT_TIMEOUT = 10_000

    suspend fun get(
        url: String,
        timeoutMs: Int = DEFAULT_TIMEOUT,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                throw IOException("HTTP $code: ${conn.responseMessage}${if (errText.isNotEmpty()) " — $errText" else ""}")
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        timeoutMs: Int = DEFAULT_TIMEOUT,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            instanceFollowRedirects = true
        }
        try {
            conn.outputStream.bufferedWriter().use { it.write(body) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                throw IOException("HTTP $code: ${conn.responseMessage}${if (errText.isNotEmpty()) " — $errText" else ""}")
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun getWithHeaders(
        url: String,
        timeoutMs: Int = DEFAULT_TIMEOUT,
        headers: Map<String, String> = emptyMap()
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            val body = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(
        url: String,
        dest: File,
        timeoutMs: Int = 30_000,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: ${conn.responseMessage}")
            val total = conn.contentLengthLong
            dest.parentFile?.mkdirs()
            var copied = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        copied += n
                        onProgress?.invoke(copied, total)
                    }
                }
            }
            copied
        } finally {
            conn.disconnect()
        }
    }

    fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
