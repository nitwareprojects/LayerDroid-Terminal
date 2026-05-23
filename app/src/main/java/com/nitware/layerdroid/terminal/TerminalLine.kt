package com.nitware.layerdroid.terminal

data class TerminalLine(
    val text: String,
    val type: Type = Type.OUTPUT
) {
    enum class Type {
        PROMPT,
        COMMAND,
        OUTPUT,
        ERROR,
        SUCCESS,
        INFO,
        WARNING,
        DIRECTORY,
        SYSTEM,
        SEPARATOR
    }
}
