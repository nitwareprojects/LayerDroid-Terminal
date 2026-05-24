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
        val result = withTimeoutOrNull(timeoutMs) {
            try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(workingDir)
                    .redirectErrorStream(false)
                if (envVars.isNotEmpty()) {
                    pb.environment().putAll(envVars)
                }
                val process = pb.start()
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                process.waitFor()
                Result(stdout.trimEnd('\n'), stderr.trimEnd('\n'), process.exitValue())
            } catch (e: Exception) {
                Result("", e.message ?: "Execution failed", -1)
            }
        }
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
