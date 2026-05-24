package com.nitware.layerdroid.terminal

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val processor = CommandProcessor(application)

    private val _lines = MutableLiveData<List<TerminalLine>>(emptyList())
    val lines: LiveData<List<TerminalLine>> = _lines

    private val _currentDir = MutableLiveData(processor.currentDir.absolutePath)
    val currentDir: LiveData<String> = _currentDir

    private val _shouldClear = MutableLiveData(false)
    val shouldClear: LiveData<Boolean> = _shouldClear

    private val _shouldExit = MutableLiveData(false)
    val shouldExit: LiveData<Boolean> = _shouldExit

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _launchIntent = MutableLiveData<Intent?>()
    val launchIntent: LiveData<Intent?> = _launchIntent

    fun consumedLaunchIntent() { _launchIntent.value = null }

    private var historyIndex = -1

    init {
        addWelcome()
    }

    private fun addWelcome() {
        val welcome = listOf(
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("  ╔══════════════════════════════════════╗", TerminalLine.Type.SUCCESS),
            TerminalLine("  ║                                      ║", TerminalLine.Type.SUCCESS),
            TerminalLine("  ║   >_ LayerDroid Terminal  v1.0       ║", TerminalLine.Type.SUCCESS),
            TerminalLine("  ║      Terminal Android — Powered Up   ║", TerminalLine.Type.SUCCESS),
            TerminalLine("  ║                                      ║", TerminalLine.Type.SUCCESS),
            TerminalLine("  ╚══════════════════════════════════════╝", TerminalLine.Type.SUCCESS),
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("  • 'help'            → listar todos os comandos", TerminalLine.Type.INFO),
            TerminalLine("  • 'neofetch'        → informações do sistema", TerminalLine.Type.INFO),
            TerminalLine("  • 'http GET <url>'  → requisições HTTP reais", TerminalLine.Type.INFO),
            TerminalLine("  • 'nano <arquivo>'  → editor de texto", TerminalLine.Type.INFO),
            TerminalLine("  • 'calc sqrt(16)'   → calculadora avançada", TerminalLine.Type.INFO),
            TerminalLine("  • 'termux-info'     → integração com Termux", TerminalLine.Type.INFO),
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("  Pipes: ls | grep foo   Redirects: cmd > file", TerminalLine.Type.SYSTEM),
            TerminalLine("", TerminalLine.Type.OUTPUT)
        )
        _lines.value = welcome
    }

    fun executeCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            appendPromptLine(trimmed)
            return
        }

        processor.commandHistory.add(trimmed)
        historyIndex = -1

        appendLines(
            listOf(TerminalLine(buildPromptString(trimmed), TerminalLine.Type.PROMPT))
        )

        _isRunning.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { processor.process(trimmed) }
                when {
                    result.shouldClear -> {
                        _lines.value = emptyList()
                        _shouldClear.value = true
                    }
                    result.shouldExit -> {
                        _shouldExit.value = true
                    }
                    else -> {
                        result.newDir?.let {
                            processor.currentDir = it
                            _currentDir.value = it.absolutePath
                        }
                        appendLines(result.lines)
                        result.launchIntent?.let { _launchIntent.value = it }
                    }
                }
            } finally {
                _isRunning.value = false
            }
        }
    }

    private fun appendPromptLine(input: String) {
        appendLines(listOf(TerminalLine(buildPromptString(input), TerminalLine.Type.PROMPT)))
    }

    private fun appendLines(newLines: List<TerminalLine>) {
        val current = _lines.value.orEmpty().toMutableList()
        current.addAll(newLines)
        _lines.value = current
    }

    fun getPreviousCommand(): String? {
        val history = processor.commandHistory
        if (history.isEmpty()) return null
        historyIndex = (historyIndex + 1).coerceAtMost(history.size - 1)
        return history[history.size - 1 - historyIndex]
    }

    fun getNextCommand(): String? {
        if (historyIndex <= 0) {
            historyIndex = -1
            return ""
        }
        historyIndex--
        val history = processor.commandHistory
        return history[history.size - 1 - historyIndex]
    }

    fun getTabCompletions(partial: String): List<String> {
        val parts = partial.split(" ")
        return if (parts.size <= 1) {
            getCommandCompletions(partial)
        } else {
            getPathCompletions(parts.last())
        }
    }

    private fun getCommandCompletions(partial: String): List<String> {
        val builtins = listOf(
            "help","clear","echo","pwd","cd","ls","ll","la","cat","mkdir","rm",
            "rmdir","touch","cp","mv","find","grep","head","tail","wc","stat","file","tree",
            "date","uname","whoami","id","hostname","uptime","free","df","ps","top","lscpu",
            "lsblk","mount","neofetch","getprop","pm","am","logcat","dumpsys","settings","service",
            "input","ifconfig","ip","ping","netstat","wget","curl","history","alias","unalias",
            "env","export","unset","which","man","banner","matrix","fortune","cowsay","sl","rev",
            "sort","uniq","awk","sed","tr","cut","tar","chmod","chown","du","kill","base64",
            "md5sum","sha256sum","exit","quit",
            "nano","vi","vim","edit","view","less","more",
            "pkg",
            "weather","wttr","myip","ipinfo","gh","gh-repo","tldr","define","joke","catfact","fact",
            "coin","crypto","qr","http","fetch","dns","nslookup","dig","port","speedtest",
            "hash","encode","decode","jq","calc","math","open","browse",
            "python","python3","node","nodejs","php","ruby","lua","git","ssh","termux-info",
            "battery","clip","copy","paste","vibrate","notify","share","torch","tts","volume","wifi","device"
        )
        return builtins.filter { it.startsWith(partial) }
    }

    private fun getPathCompletions(partial: String): List<String> {
        return try {
            val dir = if (partial.contains("/")) {
                File(processor.currentDir, partial.substringBeforeLast("/"))
            } else {
                processor.currentDir
            }
            val prefix = partial.substringAfterLast("/")
            dir.listFiles()
                ?.filter { it.name.startsWith(prefix) }
                ?.map { if (it.isDirectory) "${it.name}/" else it.name }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildPromptString(input: String): String {
        return "${getPrompt()}$input"
    }

    fun getPrompt(): String {
        val dirPath = processor.currentDir.absolutePath
        val home = android.os.Environment.getExternalStorageDirectory().absolutePath
        val display = if (dirPath == home) "~"
                      else if (dirPath.startsWith(home)) "~" + dirPath.removePrefix(home)
                      else dirPath
        return "user@android:$display\$ "
    }

    fun getCurrentDirFile(): File = processor.currentDir
}
