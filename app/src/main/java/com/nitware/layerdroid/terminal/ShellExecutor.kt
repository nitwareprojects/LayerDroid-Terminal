package com.nitware.layerdroid.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class ShellExecutor {

    data class Result(
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    suspend fun execute(
        command: String,
        workingDir: File,
        timeoutMs: Long = 10_000L,
        envVars: Map<String, String> = emptyMap()
    ): Result = withContext(Dispatchers.IO) {
        var process: Process? = null
        val result = withTimeoutOrNull(timeoutMs) {
            try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(workingDir)
                    .redirectErrorStream(true) // merge stderr → no deadlock, one stream to drain
                if (envVars.isNotEmpty()) pb.environment().putAll(envVars)

                process = pb.start()
                // With redirectErrorStream(true) there is only one stream; reading it directly
                // is safe — the stream closes when the process exits, so readText() returns naturally.
                val output = process!!.inputStream.bufferedReader().readText()
                val exitCode = process!!.waitFor()
                Result(output.trimEnd('\n'), "", exitCode)
            } catch (e: Exception) {
                Result("", e.message ?: "Execution failed", -1)
            }
        }
        // Kill subprocess if it outlived the timeout (prevents zombie processes)
        process?.destroy()
        result ?: Result("", "Command timed out after ${timeoutMs / 1000}s", -1)
    }

    suspend fun executeLines(
        command: String,
        workingDir: File,
        timeoutMs: Long = 10_000L,
        envVars: Map<String, String> = emptyMap()
    ): List<TerminalLine> {
        val result = execute(command, workingDir, timeoutMs, envVars)
        val lines = mutableListOf<TerminalLine>()
        if (result.stdout.isNotEmpty()) {
            result.stdout.lines().forEach { lines.add(TerminalLine(it, TerminalLine.Type.OUTPUT)) }
        }
        if (result.stderr.isNotEmpty()) {
            result.stderr.lines().forEach { lines.add(TerminalLine(it, TerminalLine.Type.ERROR)) }
        }
        return lines
    }
}
