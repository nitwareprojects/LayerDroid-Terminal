package com.nitware.layerdroid.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(private val context: Context) {

    var currentDir: File = Environment.getExternalStorageDirectory().let {
        if (it.canRead()) it else context.filesDir
    }
    private val shell = ShellExecutor()
    private val pkg = PkgManager(context)
    private val aliases = mutableMapOf<String, String>()
    private val envVars = mutableMapOf<String, String>(
        "HOME" to currentDir.absolutePath,
        "SHELL" to "layerdroid",
        "TERM" to "xterm-256color",
        "USER" to "user",
        "PATH" to "/system/bin:/system/xbin:/sbin"
    )
    val commandHistory = mutableListOf<String>()

    data class Result(
        val lines: List<TerminalLine>,
        val newDir: File? = null,
        val shouldClear: Boolean = false,
        val shouldExit: Boolean = false,
        val launchIntent: Intent? = null
    )

    suspend fun process(rawInput: String): Result {
        val input = rawInput.trim()
        if (input.isEmpty()) return Result(emptyList())

        val expanded = aliases[input.substringBefore(" ")]?.let {
            "$it ${input.substringAfter(" ", "")}"
        }?.trim() ?: input

        val parts = parseArgs(expanded)
        if (parts.isEmpty()) return Result(emptyList())

        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        return when (cmd) {
            "help", "?" -> cmdHelp(args)
            "clear", "cls" -> Result(emptyList(), shouldClear = true)
            "exit", "quit", "q" -> Result(listOf(TerminalLine("Saindo...", TerminalLine.Type.SYSTEM)), shouldExit = true)
            "echo" -> cmdEcho(args)
            "pwd" -> Result(listOf(TerminalLine(currentDir.absolutePath, TerminalLine.Type.OUTPUT)))
            "cd" -> cmdCd(args)
            "ls" -> cmdLs(args)
            "ll" -> cmdLs(listOf("-la") + args)
            "la" -> cmdLs(listOf("-a") + args)
            "cat" -> cmdCat(args)
            "mkdir" -> cmdMkdir(args)
            "rm" -> cmdRm(args)
            "rmdir" -> cmdRmdir(args)
            "touch" -> cmdTouch(args)
            "cp" -> cmdCp(args)
            "mv" -> cmdMv(args)
            "find" -> cmdFind(args)
            "grep" -> cmdGrep(args)
            "head" -> cmdHead(args)
            "tail" -> cmdTail(args)
            "wc" -> cmdWc(args)
            "stat" -> cmdStat(args)
            "file" -> cmdFile(args)
            "tree" -> cmdTree(args)
            "date" -> cmdDate(args)
            "uname" -> cmdUname(args)
            "whoami" -> Result(listOf(TerminalLine("user", TerminalLine.Type.OUTPUT)))
            "id" -> cmdId()
            "hostname" -> cmdHostname()
            "uptime" -> cmdUptime()
            "free" -> cmdFree(args)
            "df" -> cmdDf(args)
            "ps" -> cmdPs(args)
            "top" -> cmdTop()
            "lscpu" -> cmdLscpu()
            "lsblk" -> cmdLsblk()
            "mount" -> cmdMount()
            "neofetch", "fetch" -> cmdNeofetch()
            "getprop" -> cmdGetprop(args)
            "pm" -> cmdPm(args)
            "am" -> shell.executeLines("am ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "logcat" -> cmdLogcat(args)
            "dumpsys" -> cmdDumpsys(args)
            "settings" -> cmdSettings(args)
            "service" -> cmdService(args)
            "input" -> shell.executeLines("input ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "ifconfig", "ip" -> cmdIfconfig(args, cmd)
            "ping" -> cmdPing(args)
            "netstat" -> cmdNetstat()
            "wget" -> cmdWget(args)
            "curl" -> cmdCurl(args)
            "history" -> cmdHistory(args)
            "alias" -> cmdAlias(args)
            "unalias" -> cmdUnalias(args)
            "env" -> cmdEnv()
            "export" -> cmdExport(args)
            "unset" -> cmdUnset(args)
            "which" -> cmdWhich(args)
            "man" -> cmdMan(args)
            "banner" -> cmdBanner(args)
            "neofetch2" -> cmdNeofetch()
            "matrix" -> cmdMatrix()
            "fortune" -> cmdFortune()
            "cowsay" -> cmdCowsay(args)
            "sl" -> cmdSl()
            "rev" -> cmdRev(args)
            "base64" -> shell.executeLines("base64 ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "md5sum" -> shell.executeLines("md5sum ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "sha256sum" -> shell.executeLines("sha256sum ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "sort" -> shell.executeLines("sort ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "uniq" -> shell.executeLines("uniq ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "awk" -> shell.executeLines("awk ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "sed" -> shell.executeLines("sed ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "tr" -> shell.executeLines("tr ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "cut" -> shell.executeLines("cut ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "xargs" -> shell.executeLines("xargs ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "tar" -> shell.executeLines("tar ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "zip", "unzip" -> shell.executeLines("$cmd ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "chmod" -> shell.executeLines("chmod ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "chown" -> shell.executeLines("chown ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "ln" -> shell.executeLines("ln ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "du" -> cmdDu(args)
            "kill" -> shell.executeLines("kill ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "pkill" -> shell.executeLines("pkill ${args.joinToString(" ")}", currentDir).let { Result(it) }
            "su" -> Result(listOf(TerminalLine("su: Permission denied (app não tem root)", TerminalLine.Type.ERROR)))
            "nano", "vi", "vim", "edit" -> cmdNano(args, readOnly = false)
            "view", "less", "more" -> cmdNano(args, readOnly = true)

            // ─── Package manager ─────
            "pkg" -> cmdPkg(args)

            // ─── Internet commands ─────
            "weather", "clima" -> Result(NetCommands.weather(args))
            "weather-full", "wttr" -> Result(NetCommands.weatherAscii(args))
            "myip", "publicip" -> Result(NetCommands.myIp())
            "ipinfo", "geoip" -> Result(NetCommands.ipInfo(args))
            "gh", "github" -> Result(NetCommands.github(args))
            "gh-repo", "ghrepo" -> Result(NetCommands.githubRepo(args))
            "tldr", "cheat" -> Result(NetCommands.tldr(args))
            "define", "dict" -> Result(NetCommands.define(args))
            "joke", "piada" -> Result(NetCommands.joke())
            "catfact" -> Result(NetCommands.catFact())
            "fact", "uselessfact" -> Result(NetCommands.uselessFact())
            "coin", "btc", "bitcoin" -> Result(NetCommands.coin())
            "qr", "qrcode" -> {
                val (lines, url) = NetCommands.qrCode(args)
                val intent = url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
                Result(lines, launchIntent = intent)
            }

            // ─── Device commands ─────
            "battery", "bat" -> Result(DeviceCommands.battery(context))
            "clip", "paste", "clipget" -> Result(DeviceCommands.clipboardGet(context))
            "copy", "clipset" -> Result(DeviceCommands.clipboardSet(context, args))
            "vibrate", "buzz" -> Result(DeviceCommands.vibrate(context, args))
            "notify", "notification" -> Result(DeviceCommands.notify(context, args))
            "share" -> {
                val (lines, intent) = DeviceCommands.share(context, args)
                Result(lines, launchIntent = intent)
            }
            "torch", "flashlight", "lanterna" -> Result(DeviceCommands.torch(context, args))
            "tts", "say", "speak" -> Result(DeviceCommands.ttsSpeak(context, args))
            "volume", "vol" -> Result(DeviceCommands.volumeInfo(context))
            "wifi" -> Result(DeviceCommands.wifiInfo(context))
            "device", "deviceinfo" -> Result(DeviceCommands.deviceInfo(context))
            else -> {
                // Tenta executar como script instalado via pkg
                if (pkg.isInstalled(cmd)) {
                    Result(pkg.cmdRun(cmd, args, currentDir))
                } else {
                    val result = shell.executeLines(expanded, currentDir)
                    if (result.isEmpty()) {
                        Result(listOf(TerminalLine("$cmd: comando não encontrado. Digite 'help' para ver a lista.", TerminalLine.Type.ERROR)))
                    } else {
                        Result(result)
                    }
                }
            }
        }
    }

    // ─── Package Manager ───────────────────────────────────────────────────────

    private suspend fun cmdPkg(args: List<String>): Result {
        val sub = args.firstOrNull()?.lowercase()
            ?: return Result(pkg.help())
        val rest = args.drop(1)
        return when (sub) {
            "update", "up", "refresh"     -> Result(pkg.cmdUpdate())
            "list", "ls", "installed"     -> Result(pkg.cmdList())
            "available", "avail", "all"   -> Result(pkg.cmdAvailable())
            "search", "find"              -> Result(pkg.cmdSearch(rest.joinToString(" ")))
            "info", "show"                -> Result(rest.firstOrNull()?.let { pkg.cmdInfo(it) }
                                              ?: listOf(TerminalLine("Usage: pkg info <nome>", TerminalLine.Type.WARNING)))
            "install", "add", "i"         -> Result(rest.firstOrNull()?.let { pkg.cmdInstall(it) }
                                              ?: listOf(TerminalLine("Usage: pkg install <nome>", TerminalLine.Type.WARNING)))
            "remove", "uninstall", "rm"   -> Result(rest.firstOrNull()?.let { pkg.cmdRemove(it) }
                                              ?: listOf(TerminalLine("Usage: pkg remove <nome>", TerminalLine.Type.WARNING)))
            "run", "exec"                 -> Result(rest.firstOrNull()?.let { pkg.cmdRun(it, rest.drop(1), currentDir) }
                                              ?: listOf(TerminalLine("Usage: pkg run <nome> [args...]", TerminalLine.Type.WARNING)))
            "repo"                        -> Result(pkg.cmdRepo())
            "setrepo"                     -> Result(pkg.cmdSetRepo(rest.joinToString(" ")))
            "help", "-h", "--help", "?"   -> Result(pkg.help())
            else                          -> Result(listOf(
                TerminalLine("pkg: sub-comando desconhecido '$sub'", TerminalLine.Type.ERROR),
                TerminalLine("Use 'pkg help' para ver os comandos disponíveis.", TerminalLine.Type.SYSTEM)
            ))
        }
    }

    // ─── Navigation ────────────────────────────────────────────────────────────

    private fun cmdCd(args: List<String>): Result {
        val target = when {
            args.isEmpty() || args[0] == "~" -> envVars["HOME"] ?: currentDir.absolutePath
            args[0] == "-" -> currentDir.parent ?: currentDir.absolutePath
            args[0].startsWith("~/") -> (envVars["HOME"] ?: "") + args[0].substring(1)
            args[0].startsWith("/") -> args[0]
            else -> File(currentDir, args[0]).canonicalPath
        }
        val dir = File(target)
        return if (dir.exists() && dir.isDirectory) {
            if (dir.canRead()) {
                Result(emptyList(), newDir = dir)
            } else {
                Result(listOf(TerminalLine("cd: ${dir.absolutePath}: Permission denied", TerminalLine.Type.ERROR)))
            }
        } else {
            Result(listOf(TerminalLine("cd: ${target}: No such file or directory", TerminalLine.Type.ERROR)))
        }
    }

    // ─── File Listing ──────────────────────────────────────────────────────────

    private fun cmdLs(args: List<String>): Result {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val paths = args.filter { !it.startsWith("-") }
        val showAll = 'a' in flags || 'A' in flags
        val longFormat = 'l' in flags

        val targetDir = if (paths.isEmpty()) currentDir else {
            val p = paths[0]
            if (p.startsWith("/")) File(p) else File(currentDir, p)
        }

        if (!targetDir.exists()) return Result(listOf(
            TerminalLine("ls: cannot access '${targetDir.path}': No such file or directory", TerminalLine.Type.ERROR)
        ))

        if (targetDir.isFile) {
            return Result(listOf(TerminalLine(formatLsEntry(targetDir, longFormat), TerminalLine.Type.OUTPUT)))
        }

        val entries = (targetDir.listFiles() ?: emptyArray())
            .filter { showAll || !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        if (entries.isEmpty()) return Result(emptyList())

        return if (longFormat) {
            val lines = mutableListOf<TerminalLine>()
            lines.add(TerminalLine("total ${entries.size}", TerminalLine.Type.SYSTEM))
            entries.forEach { f ->
                val type = if (f.isDirectory) TerminalLine.Type.DIRECTORY else TerminalLine.Type.OUTPUT
                lines.add(TerminalLine(formatLsLong(f), type))
            }
            Result(lines)
        } else {
            val cols = 3
            val colWidth = (entries.maxOfOrNull { it.name.length } ?: 10) + 2
            val rows = (entries.size + cols - 1) / cols
            val lines = mutableListOf<TerminalLine>()
            for (row in 0 until rows) {
                val sb = StringBuilder()
                for (col in 0 until cols) {
                    val idx = row + col * rows
                    if (idx < entries.size) {
                        val name = if (entries[idx].isDirectory) "${entries[idx].name}/" else entries[idx].name
                        sb.append(name.padEnd(colWidth))
                    }
                }
                val type = if (entries.getOrNull(row)?.isDirectory == true) TerminalLine.Type.DIRECTORY
                           else TerminalLine.Type.OUTPUT
                lines.add(TerminalLine(sb.trimEnd().toString(), type))
            }
            Result(lines)
        }
    }

    private fun formatLsLong(f: File): String {
        val perms = buildString {
            append(if (f.isDirectory) 'd' else '-')
            append(if (f.canRead()) 'r' else '-')
            append(if (f.canWrite()) 'w' else '-')
            append(if (f.canExecute()) 'x' else '-')
            append("r--r--")
        }
        val size = f.length()
        val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
        val date = sdf.format(Date(f.lastModified()))
        return "$perms  1 user user ${size.toString().padStart(8)} $date ${f.name}"
    }

    private fun formatLsEntry(f: File, longFormat: Boolean): String {
        return if (longFormat) formatLsLong(f) else f.name
    }

    // ─── File Operations ───────────────────────────────────────────────────────

    private fun cmdCat(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: cat <file>", TerminalLine.Type.WARNING)))
        val file = resolveFile(args[0])
        if (!file.exists()) return Result(listOf(TerminalLine("cat: ${args[0]}: No such file or directory", TerminalLine.Type.ERROR)))
        if (file.isDirectory) return Result(listOf(TerminalLine("cat: ${args[0]}: Is a directory", TerminalLine.Type.ERROR)))
        return try {
            val lines = file.readLines()
            if (lines.isEmpty()) Result(emptyList())
            else Result(lines.map { TerminalLine(it, TerminalLine.Type.OUTPUT) })
        } catch (e: Exception) {
            Result(listOf(TerminalLine("cat: ${args[0]}: Permission denied", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdMkdir(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: mkdir [-p] <dir>", TerminalLine.Type.WARNING)))
        val parents = "-p" in args
        val dirs = args.filter { !it.startsWith("-") }
        val lines = mutableListOf<TerminalLine>()
        dirs.forEach { d ->
            val dir = resolveFile(d)
            val ok = if (parents) dir.mkdirs() else dir.mkdir()
            if (!ok && !dir.exists()) lines.add(TerminalLine("mkdir: cannot create directory '$d'", TerminalLine.Type.ERROR))
        }
        return Result(lines)
    }

    private fun cmdRm(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: rm [-rf] <path>", TerminalLine.Type.WARNING)))
        val recursive = "-r" in args || "-rf" in args || "-fr" in args
        val files = args.filter { !it.startsWith("-") }
        val lines = mutableListOf<TerminalLine>()
        files.forEach { f ->
            val file = resolveFile(f)
            if (!file.exists()) {
                lines.add(TerminalLine("rm: cannot remove '$f': No such file or directory", TerminalLine.Type.ERROR))
            } else {
                val ok = if (recursive) file.deleteRecursively() else file.delete()
                if (!ok) lines.add(TerminalLine("rm: cannot remove '$f': Permission denied", TerminalLine.Type.ERROR))
            }
        }
        return Result(lines)
    }

    private fun cmdRmdir(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: rmdir <dir>", TerminalLine.Type.WARNING)))
        val lines = mutableListOf<TerminalLine>()
        args.forEach { d ->
            val dir = resolveFile(d)
            if (!dir.exists()) lines.add(TerminalLine("rmdir: failed to remove '$d': No such file or directory", TerminalLine.Type.ERROR))
            else if (!dir.isDirectory) lines.add(TerminalLine("rmdir: failed to remove '$d': Not a directory", TerminalLine.Type.ERROR))
            else if ((dir.listFiles()?.size ?: 0) > 0) lines.add(TerminalLine("rmdir: failed to remove '$d': Directory not empty", TerminalLine.Type.ERROR))
            else if (!dir.delete()) lines.add(TerminalLine("rmdir: failed to remove '$d': Permission denied", TerminalLine.Type.ERROR))
        }
        return Result(lines)
    }

    private fun cmdTouch(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: touch <file>", TerminalLine.Type.WARNING)))
        val lines = mutableListOf<TerminalLine>()
        args.forEach { f ->
            val file = resolveFile(f)
            try {
                if (!file.exists()) file.createNewFile()
                else file.setLastModified(System.currentTimeMillis())
            } catch (e: Exception) {
                lines.add(TerminalLine("touch: cannot touch '$f': ${e.message}", TerminalLine.Type.ERROR))
            }
        }
        return Result(lines)
    }

    private fun cmdCp(args: List<String>): Result {
        val nonFlag = args.filter { !it.startsWith("-") }
        if (nonFlag.size < 2) return Result(listOf(TerminalLine("Usage: cp [-r] <src> <dst>", TerminalLine.Type.WARNING)))
        return try {
            val src = resolveFile(nonFlag[0])
            val dst = resolveFile(nonFlag[1])
            src.copyRecursively(dst, overwrite = true)
            Result(emptyList())
        } catch (e: Exception) {
            Result(listOf(TerminalLine("cp: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdMv(args: List<String>): Result {
        val nonFlag = args.filter { !it.startsWith("-") }
        if (nonFlag.size < 2) return Result(listOf(TerminalLine("Usage: mv <src> <dst>", TerminalLine.Type.WARNING)))
        return try {
            val src = resolveFile(nonFlag[0])
            val dst = resolveFile(nonFlag[1])
            if (!src.renameTo(dst)) {
                src.copyRecursively(dst, overwrite = true)
                src.deleteRecursively()
            }
            Result(emptyList())
        } catch (e: Exception) {
            Result(listOf(TerminalLine("mv: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdFind(args: List<String>): Result {
        val path = args.firstOrNull { !it.startsWith("-") } ?: "."
        val nameIdx = args.indexOf("-name")
        val pattern = if (nameIdx >= 0 && nameIdx + 1 < args.size) args[nameIdx + 1] else null
        val maxDepthIdx = args.indexOf("-maxdepth")
        val maxDepth = if (maxDepthIdx >= 0 && maxDepthIdx + 1 < args.size) args[maxDepthIdx + 1].toIntOrNull() ?: Int.MAX_VALUE else Int.MAX_VALUE

        val root = resolveFile(path)
        val results = mutableListOf<TerminalLine>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth) return
            dir.listFiles()?.forEach { f ->
                val matches = pattern == null || f.name.matches(Regex(pattern.replace("*", ".*").replace("?", ".")))
                if (matches) results.add(TerminalLine(f.absolutePath, TerminalLine.Type.OUTPUT))
                if (f.isDirectory) walk(f, depth + 1)
            }
        }
        walk(root, 0)
        return Result(results)
    }

    private fun cmdGrep(args: List<String>): Result {
        val ignoreCase = "-i" in args
        val lineNum = "-n" in args
        val invertMatch = "-v" in args
        val nonFlags = args.filter { !it.startsWith("-") }
        if (nonFlags.isEmpty()) return Result(listOf(TerminalLine("Usage: grep [-inv] <pattern> [file]", TerminalLine.Type.WARNING)))
        val pattern = nonFlags[0]
        val file = if (nonFlags.size > 1) resolveFile(nonFlags[1]) else null

        return try {
            val lines = file?.readLines() ?: return Result(listOf(TerminalLine("grep: no input file", TerminalLine.Type.WARNING)))
            val regex = Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
            val results = mutableListOf<TerminalLine>()
            lines.forEachIndexed { idx, line ->
                val matched = regex.containsMatchIn(line)
                if (matched != invertMatch) {
                    val display = if (lineNum) "${idx + 1}:$line" else line
                    results.add(TerminalLine(display, TerminalLine.Type.OUTPUT))
                }
            }
            Result(results)
        } catch (e: Exception) {
            Result(listOf(TerminalLine("grep: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdHead(args: List<String>): Result {
        val nIdx = args.indexOf("-n")
        val n = if (nIdx >= 0 && nIdx + 1 < args.size) args[nIdx + 1].toIntOrNull() ?: 10 else 10
        val file = args.lastOrNull { !it.startsWith("-") }
            ?: return Result(listOf(TerminalLine("Usage: head [-n N] <file>", TerminalLine.Type.WARNING)))
        return try {
            val lines = resolveFile(file).readLines().take(n)
            Result(lines.map { TerminalLine(it, TerminalLine.Type.OUTPUT) })
        } catch (e: Exception) {
            Result(listOf(TerminalLine("head: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdTail(args: List<String>): Result {
        val nIdx = args.indexOf("-n")
        val n = if (nIdx >= 0 && nIdx + 1 < args.size) args[nIdx + 1].toIntOrNull() ?: 10 else 10
        val file = args.lastOrNull { !it.startsWith("-") }
            ?: return Result(listOf(TerminalLine("Usage: tail [-n N] <file>", TerminalLine.Type.WARNING)))
        return try {
            val lines = resolveFile(file).readLines().takeLast(n)
            Result(lines.map { TerminalLine(it, TerminalLine.Type.OUTPUT) })
        } catch (e: Exception) {
            Result(listOf(TerminalLine("tail: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdWc(args: List<String>): Result {
        val onlyLines = "-l" in args
        val file = args.lastOrNull { !it.startsWith("-") }
            ?: return Result(listOf(TerminalLine("Usage: wc [-lwc] <file>", TerminalLine.Type.WARNING)))
        return try {
            val text = resolveFile(file).readText()
            val lines = text.lines().size
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            val chars = text.length
            val out = if (onlyLines) "$lines $file" else "$lines $words $chars $file"
            Result(listOf(TerminalLine(out, TerminalLine.Type.OUTPUT)))
        } catch (e: Exception) {
            Result(listOf(TerminalLine("wc: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdStat(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: stat <file>", TerminalLine.Type.WARNING)))
        val file = resolveFile(args[0])
        if (!file.exists()) return Result(listOf(TerminalLine("stat: ${args[0]}: No such file or directory", TerminalLine.Type.ERROR)))
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val lines = listOf(
            TerminalLine("  File: ${file.absolutePath}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Size: ${file.length()}  Blocks: ${file.length() / 512}  ${if (file.isDirectory) "Directory" else "Regular File"}", TerminalLine.Type.OUTPUT),
            TerminalLine("Access: ${if (file.canRead()) "r" else "-"}${if (file.canWrite()) "w" else "-"}${if (file.canExecute()) "x" else "-"}", TerminalLine.Type.OUTPUT),
            TerminalLine("Modify: ${sdf.format(Date(file.lastModified()))}", TerminalLine.Type.OUTPUT)
        )
        return Result(lines)
    }

    private fun cmdFile(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: file <path>", TerminalLine.Type.WARNING)))
        val file = resolveFile(args[0])
        val type = when {
            !file.exists() -> "ERROR: No such file"
            file.isDirectory -> "directory"
            file.extension in listOf("png","jpg","jpeg","gif","webp") -> "image file"
            file.extension in listOf("mp3","wav","ogg","flac","m4a") -> "audio file"
            file.extension in listOf("mp4","mkv","avi","mov","webm") -> "video file"
            file.extension in listOf("txt","log","md","csv") -> "ASCII text"
            file.extension in listOf("apk") -> "Android application package"
            file.extension in listOf("zip","jar") -> "Zip archive"
            file.extension in listOf("gz","bz2","xz") -> "compressed data"
            file.extension in listOf("sh","bash") -> "shell script"
            else -> "data"
        }
        return Result(listOf(TerminalLine("${file.name}: $type", TerminalLine.Type.OUTPUT)))
    }

    private fun cmdTree(args: List<String>): Result {
        val path = args.firstOrNull { !it.startsWith("-") } ?: "."
        val maxDepth = run {
            val idx = args.indexOf("-L")
            if (idx >= 0 && idx + 1 < args.size) args[idx + 1].toIntOrNull() ?: 3 else 3
        }
        val root = resolveFile(path)
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine(root.name, TerminalLine.Type.DIRECTORY))
        var dirs = 0; var files = 0

        fun walk(dir: File, prefix: String, depth: Int) {
            if (depth > maxDepth) return
            val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            entries.forEachIndexed { i, f ->
                val isLast = i == entries.size - 1
                val connector = if (isLast) "└── " else "├── "
                val childPrefix = if (isLast) "$prefix    " else "$prefix│   "
                val type = if (f.isDirectory) TerminalLine.Type.DIRECTORY else TerminalLine.Type.OUTPUT
                lines.add(TerminalLine("$prefix$connector${f.name}", type))
                if (f.isDirectory) { dirs++; walk(f, childPrefix, depth + 1) } else files++
            }
        }
        walk(root, "", 0)
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("$dirs directories, $files files", TerminalLine.Type.SYSTEM))
        return Result(lines)
    }

    private fun cmdDu(args: List<String>): Result {
        val human = "-h" in args
        val path = args.lastOrNull { !it.startsWith("-") } ?: "."
        val f = resolveFile(path)
        fun size(file: File): Long = if (file.isDirectory) file.walkTopDown().sumOf { it.length() } else file.length()
        fun fmt(bytes: Long) = if (human) {
            when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}K"
                bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0 / 1024.0)}M"
                else -> "${"%.1f".format(bytes / 1024.0 / 1024.0 / 1024.0)}G"
            }
        } else "${bytes / 1024}"
        return Result(listOf(TerminalLine("${fmt(size(f))}\t${f.absolutePath}", TerminalLine.Type.OUTPUT)))
    }

    // ─── System Info ───────────────────────────────────────────────────────────

    private fun cmdDate(args: List<String>): Result {
        val fmtArg = args.firstOrNull { it.startsWith("+") }?.substring(1)
        val sdf = SimpleDateFormat(
            fmtArg?.replace("%Y", "yyyy")?.replace("%m", "MM")?.replace("%d", "dd")
                ?.replace("%H", "HH")?.replace("%M", "mm")?.replace("%S", "ss")
                ?: "EEE MMM dd HH:mm:ss zzz yyyy",
            Locale.US
        )
        return Result(listOf(TerminalLine(sdf.format(Date()), TerminalLine.Type.OUTPUT)))
    }

    private fun cmdUname(args: List<String>): Result {
        val all = "-a" in args
        val kernel = try { File("/proc/version").readText().substringAfter("version ").substringBefore(" (") } catch (e: Exception) { Build.VERSION.RELEASE }
        val lines = when {
            all -> listOf(TerminalLine("Linux android ${kernel} ${Build.BOARD} GNU/Linux", TerminalLine.Type.OUTPUT))
            "-r" in args -> listOf(TerminalLine(kernel, TerminalLine.Type.OUTPUT))
            "-m" in args -> listOf(TerminalLine(Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64", TerminalLine.Type.OUTPUT))
            "-s" in args -> listOf(TerminalLine("Linux", TerminalLine.Type.OUTPUT))
            "-n" in args -> listOf(TerminalLine("android", TerminalLine.Type.OUTPUT))
            else -> listOf(TerminalLine("Linux", TerminalLine.Type.OUTPUT))
        }
        return Result(lines)
    }

    private fun cmdId(): Result {
        val uid = android.os.Process.myUid()
        return Result(listOf(TerminalLine("uid=$uid(user) gid=$uid(user) groups=$uid(user)", TerminalLine.Type.OUTPUT)))
    }

    private fun cmdHostname(): Result {
        val hostname = try {
            File("/proc/sys/kernel/hostname").readText().trim()
        } catch (e: Exception) {
            Build.HOST.substringBefore(".")
        }
        return Result(listOf(TerminalLine(hostname, TerminalLine.Type.OUTPUT)))
    }

    private fun cmdUptime(): Result {
        return try {
            val rawUptime = File("/proc/uptime").readText().trim().split(" ")[0].toDouble()
            val total = rawUptime.toLong()
            val days = total / 86400
            val hours = (total % 86400) / 3600
            val mins = (total % 3600) / 60
            val secs = total % 60
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            val now = sdf.format(Date())
            val up = buildString {
                if (days > 0) append("${days}d ")
                append("${hours.toString().padStart(2,'0')}:${mins.toString().padStart(2,'0')}:${secs.toString().padStart(2,'0')}")
            }
            Result(listOf(TerminalLine(" $now up $up,  1 user,  load average: 0.00, 0.00, 0.00", TerminalLine.Type.OUTPUT)))
        } catch (e: Exception) {
            Result(listOf(TerminalLine("uptime: unable to read /proc/uptime", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdFree(args: List<String>): Result {
        val human = "-h" in args
        return try {
            val meminfo = File("/proc/meminfo").readLines()
            fun getValue(key: String): Long {
                return meminfo.firstOrNull { it.startsWith(key) }
                    ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()?.times(1024) ?: 0L
            }
            fun fmt(bytes: Long) = if (human) {
                when {
                    bytes < 1024 -> "${bytes}B"
                    bytes < 1024*1024 -> "${"%.0f".format(bytes/1024.0)}K"
                    bytes < 1024*1024*1024 -> "${"%.1f".format(bytes/1024.0/1024.0)}M"
                    else -> "${"%.1f".format(bytes/1024.0/1024.0/1024.0)}G"
                }
            } else "${bytes/1024}"

            val total = getValue("MemTotal:")
            val free = getValue("MemFree:")
            val avail = getValue("MemAvailable:")
            val buffers = getValue("Buffers:")
            val cached = getValue("Cached:")
            val used = total - avail
            val label = if (human) "" else "         "
            val lines = listOf(
                TerminalLine("              total        used        free      shared     buffers      cached", TerminalLine.Type.SYSTEM),
                TerminalLine("Mem:  ${fmt(total).padStart(12)} ${fmt(used).padStart(12)} ${fmt(free).padStart(12)} ${fmt(0).padStart(11)} ${fmt(buffers).padStart(11)} ${fmt(cached).padStart(11)}", TerminalLine.Type.OUTPUT),
                TerminalLine("Swap:          0           0           0", TerminalLine.Type.OUTPUT)
            )
            Result(lines)
        } catch (e: Exception) {
            Result(listOf(TerminalLine("free: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdDf(args: List<String>): Result {
        val human = "-h" in args
        fun fmt(bytes: Long) = if (human) {
            when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024*1024 -> "${"%.0f".format(bytes/1024.0)}K"
                bytes < 1024*1024*1024 -> "${"%.1f".format(bytes/1024.0/1024.0)}M"
                else -> "${"%.1f".format(bytes/1024.0/1024.0/1024.0)}G"
            }
        } else "${bytes/1024}"

        val mounts = listOf(
            "/" to File("/"),
            "/data" to File("/data"),
            "/sdcard" to Environment.getExternalStorageDirectory(),
            "/system" to File("/system")
        )
        val lines = mutableListOf(
            TerminalLine("Filesystem            Size    Used   Avail Use% Mounted on", TerminalLine.Type.SYSTEM)
        )
        mounts.forEach { (name, dir) ->
            try {
                val stat = StatFs(dir.absolutePath)
                val total = stat.blockCountLong * stat.blockSizeLong
                val avail = stat.availableBlocksLong * stat.blockSizeLong
                val used = total - avail
                val pct = if (total > 0) "${(used * 100 / total)}%" else "0%"
                lines.add(TerminalLine("%-20s %7s %7s %7s %4s %s".format(
                    name, fmt(total), fmt(used), fmt(avail), pct, name
                ), TerminalLine.Type.OUTPUT))
            } catch (e: Exception) { }
        }
        return Result(lines)
    }

    private fun cmdPs(args: List<String>): Result {
        val lines = mutableListOf(
            TerminalLine("USER       PID  PPID  VSIZE   RSS   WCHAN  PC         NAME", TerminalLine.Type.SYSTEM)
        )
        try {
            File("/proc").listFiles()
                ?.filter { it.name.matches(Regex("\\d+")) }
                ?.sortedBy { it.name.toInt() }
                ?.take(50)
                ?.forEach { procDir ->
                    try {
                        val pid = procDir.name
                        val status = File(procDir, "status").readLines()
                        fun getVal(k: String) = status.firstOrNull { it.startsWith(k) }
                            ?.substringAfter(":")?.trim() ?: "?"
                        val name = getVal("Name:").take(15)
                        val ppid = getVal("PPid:")
                        val vmrss = getVal("VmRSS:").substringBefore(" ").trim().padStart(6)
                        val vmsize = getVal("VmSize:").substringBefore(" ").trim().padStart(7)
                        lines.add(TerminalLine("%-10s %5s %5s %7s %6s   ?      ?          $name".format(
                            "user", pid, ppid, vmsize, vmrss
                        ), TerminalLine.Type.OUTPUT))
                    } catch (e: Exception) { }
                }
        } catch (e: Exception) {
            lines.add(TerminalLine("ps: ${e.message}", TerminalLine.Type.ERROR))
        }
        return Result(lines)
    }

    private fun cmdTop(): Result {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMem = memInfo.totalMem / 1024 / 1024
        val availMem = memInfo.availMem / 1024 / 1024
        val usedMem = totalMem - availMem

        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("top - ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} up 0 days, 0 users", TerminalLine.Type.SYSTEM))
        lines.add(TerminalLine("Tasks: running", TerminalLine.Type.SYSTEM))
        lines.add(TerminalLine("Cpu(s): sys, user, idle", TerminalLine.Type.SYSTEM))
        lines.add(TerminalLine("Mem: ${totalMem}M total, ${usedMem}M used, ${availMem}M free", TerminalLine.Type.INFO))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND", TerminalLine.Type.SYSTEM))
        try {
            File("/proc").listFiles()
                ?.filter { it.name.matches(Regex("\\d+")) }
                ?.take(20)
                ?.forEach { procDir ->
                    try {
                        val pid = procDir.name.padStart(5)
                        val name = File(procDir, "comm").readText().trim().take(12)
                        lines.add(TerminalLine("$pid user      20   0      ?      ?      ? S   0.0   0.0   0:00.00 $name", TerminalLine.Type.OUTPUT))
                    } catch (e: Exception) { }
                }
        } catch (e: Exception) { }
        return Result(lines)
    }

    private fun cmdLscpu(): Result {
        return try {
            val cpuinfo = File("/proc/cpuinfo").readText()
            val processor = cpuinfo.lines().firstOrNull { it.startsWith("Processor") || it.startsWith("model name") }
                ?.substringAfter(":")?.trim() ?: Build.HARDWARE
            val cores = Runtime.getRuntime().availableProcessors()
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"
            val lines = listOf(
                TerminalLine("Architecture:          $abi", TerminalLine.Type.OUTPUT),
                TerminalLine("CPU(s):                $cores", TerminalLine.Type.OUTPUT),
                TerminalLine("Model name:            $processor", TerminalLine.Type.OUTPUT),
                TerminalLine("CPU max MHz:           ?", TerminalLine.Type.OUTPUT),
                TerminalLine("Board:                 ${Build.BOARD}", TerminalLine.Type.OUTPUT),
                TerminalLine("Hardware:              ${Build.HARDWARE}", TerminalLine.Type.OUTPUT)
            )
            Result(lines)
        } catch (e: Exception) {
            Result(listOf(TerminalLine("lscpu: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdLsblk(): Result {
        val lines = mutableListOf(
            TerminalLine("NAME       MAJ:MIN  RM    SIZE RO TYPE MOUNTPOINT", TerminalLine.Type.SYSTEM)
        )
        listOf("/data" to "data", "/sdcard" to "sdcard", "/system" to "system").forEach { (path, name) ->
            try {
                val stat = StatFs(path)
                val size = stat.blockCountLong * stat.blockSizeLong / 1024 / 1024 / 1024
                lines.add(TerminalLine("%-10s 8:0       0   %4dG  0 disk %s".format(name, size, path), TerminalLine.Type.OUTPUT))
            } catch (e: Exception) { }
        }
        return Result(lines)
    }

    private fun cmdMount(): Result {
        return try {
            val lines = File("/proc/mounts").readLines()
                .take(20)
                .map { TerminalLine(it, TerminalLine.Type.OUTPUT) }
            Result(lines)
        } catch (e: Exception) {
            Result(listOf(TerminalLine("mount: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdNeofetch(): Result {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMem = "%.1f".format(memInfo.totalMem / 1024.0 / 1024.0 / 1024.0) + "G"
        val usedMem = "%.1f".format((memInfo.totalMem - memInfo.availMem) / 1024.0 / 1024.0 / 1024.0) + "G"

        val kernel = try { File("/proc/version").readText().let {
            it.substringAfter("version ").substringBefore(" (")
        }} catch (e: Exception) { Build.VERSION.RELEASE }

        val extDir = Environment.getExternalStorageDirectory()
        val storageStat = try { StatFs(extDir.absolutePath) } catch (e: Exception) { null }
        val storageTotal = storageStat?.let { "%.0f".format(it.blockCountLong * it.blockSizeLong / 1024.0 / 1024.0 / 1024.0) + "G" } ?: "?"
        val storageUsed  = storageStat?.let {
            "%.0f".format((it.blockCountLong - it.availableBlocksLong) * it.blockSizeLong / 1024.0 / 1024.0 / 1024.0) + "G"
        } ?: "?"

        val battery = try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
        } catch (e: Exception) { "?" }

        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"
        val hostname = try { File("/proc/sys/kernel/hostname").readText().trim() } catch (e: Exception) { "android" }

        val uptime = try {
            val secs = File("/proc/uptime").readText().trim().split(" ")[0].toDouble().toLong()
            val h = secs / 3600; val m = (secs % 3600) / 60
            "${h}h ${m}m"
        } catch (e: Exception) { "?" }

        val logo = listOf(
            "  ┌─────────────────┐   ",
            "  │  >_  LayerDroid │   ",
            "  └─────────────────┘   ",
            "   ╔═══╗ ╔═══╗ ╔═══╗   ",
            "   ║   ║ ║   ║ ║   ║   ",
            "   ╚═══╝ ╚═══╝ ╚═══╝   ",
            "                        ",
            "                        ",
            "                        ",
            "                        ",
            "                        ",
            "                        "
        )
        val info = listOf(
            "user@$hostname",
            "─".repeat("user@$hostname".length),
            "OS:       Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Kernel:   $kernel",
            "Device:   ${Build.MANUFACTURER} ${Build.MODEL}",
            "Board:    ${Build.BOARD}",
            "CPU:      ${Build.HARDWARE} ($cores cores, $abi)",
            "Memory:   $usedMem / $totalMem",
            "Storage:  $storageUsed / $storageTotal",
            "Battery:  $battery",
            "Uptime:   $uptime",
            "Shell:    LayerDroid Terminal v1.0"
        )

        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        val maxRows = maxOf(logo.size, info.size)
        for (i in 0 until maxRows) {
            val l = logo.getOrElse(i) { "                        " }
            val r = info.getOrElse(i) { "" }
            val type = when {
                i == 0 -> TerminalLine.Type.SUCCESS
                i == 1 -> TerminalLine.Type.SYSTEM
                else -> TerminalLine.Type.OUTPUT
            }
            lines.add(TerminalLine("$l  $r", type))
        }
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        return Result(lines)
    }

    // ─── Android-specific ─────────────────────────────────────────────────────

    private suspend fun cmdGetprop(args: List<String>): Result {
        val key = args.firstOrNull()
        return if (key != null) {
            shell.executeLines("getprop $key", currentDir)
                .let { Result(it.ifEmpty { listOf(TerminalLine("", TerminalLine.Type.OUTPUT)) }) }
        } else {
            shell.executeLines("getprop", currentDir).let { Result(it) }
        }
    }

    private suspend fun cmdPm(args: List<String>): Result {
        val subCmd = args.firstOrNull() ?: return Result(listOf(
            TerminalLine("Usage: pm <list|install|uninstall|clear> [options]", TerminalLine.Type.WARNING)
        ))
        return when (subCmd) {
            "list" -> {
                val sub2 = args.getOrNull(1)
                when (sub2) {
                    "packages" -> {
                        val thirdParty = "-3" in args
                        val system = "-s" in args
                        val pm = context.packageManager
                        @Suppress("DEPRECATION")
                        val apps = pm.getInstalledApplications(0)
                        val filtered = apps.filter { app ->
                            when {
                                thirdParty -> app.sourceDir?.startsWith("/data") == true
                                system -> app.sourceDir?.startsWith("/system") == true
                                else -> true
                            }
                        }
                        val lines = filtered.sortedBy { it.packageName }
                            .map { TerminalLine("package:${it.packageName}", TerminalLine.Type.OUTPUT) }
                        Result(lines)
                    }
                    "features" -> shell.executeLines("pm list features", currentDir).let { Result(it) }
                    else -> Result(listOf(TerminalLine("pm list: unknown sub-command '$sub2'", TerminalLine.Type.ERROR)))
                }
            }
            "path" -> {
                val pkg = args.getOrNull(1) ?: return Result(listOf(TerminalLine("pm path: package name required", TerminalLine.Type.ERROR)))
                try {
                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                    Result(listOf(TerminalLine("package:${info.sourceDir}", TerminalLine.Type.OUTPUT)))
                } catch (e: Exception) {
                    Result(listOf(TerminalLine("pm path: package not found: $pkg", TerminalLine.Type.ERROR)))
                }
            }
            else -> shell.executeLines("pm ${args.joinToString(" ")}", currentDir).let { Result(it) }
        }
    }

    private suspend fun cmdLogcat(args: List<String>): Result {
        val n = run { val i = args.indexOf("-n"); if (i >= 0 && i+1 < args.size) args[i+1] else "50" }
        return shell.executeLines("logcat -d -t $n 2>&1 | tail -$n", currentDir, 5000L).let { Result(it) }
    }

    private suspend fun cmdDumpsys(args: List<String>): Result {
        val service = args.firstOrNull() ?: "battery"
        return shell.executeLines("dumpsys $service 2>&1 | head -80", currentDir, 8000L).let { Result(it) }
    }

    private suspend fun cmdSettings(args: List<String>): Result {
        return shell.executeLines("settings ${args.joinToString(" ")}", currentDir).let { Result(it) }
    }

    private suspend fun cmdService(args: List<String>): Result {
        return shell.executeLines("service ${args.joinToString(" ")} 2>&1 | head -60", currentDir).let { Result(it) }
    }

    // ─── Network ───────────────────────────────────────────────────────────────

    private fun cmdIfconfig(args: List<String>, cmd: String): Result {
        return try {
            val netDevLines = File("/proc/net/dev").readLines().drop(2)
            val lines = mutableListOf<TerminalLine>()
            netDevLines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 10) {
                    val iface = parts[0].trimEnd(':')
                    val rxBytes = parts[1].toLongOrNull() ?: 0
                    val txBytes = parts[9].toLongOrNull() ?: 0
                    fun fmt(b: Long) = when {
                        b < 1024 -> "${b}B"
                        b < 1024*1024 -> "${"%.1f".format(b/1024.0)}KB"
                        else -> "${"%.1f".format(b/1024.0/1024.0)}MB"
                    }
                    lines.add(TerminalLine("$iface:", TerminalLine.Type.SUCCESS))
                    lines.add(TerminalLine("    RX bytes:${fmt(rxBytes)}  TX bytes:${fmt(txBytes)}", TerminalLine.Type.OUTPUT))
                }
            }
            Result(lines.ifEmpty { listOf(TerminalLine("No network interfaces found", TerminalLine.Type.WARNING)) })
        } catch (e: Exception) {
            Result(listOf(TerminalLine("ifconfig: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private suspend fun cmdPing(args: List<String>): Result {
        val host = args.firstOrNull { !it.startsWith("-") }
            ?: return Result(listOf(TerminalLine("Usage: ping <host>", TerminalLine.Type.WARNING)))
        val count = run { val i = args.indexOf("-c"); if (i >= 0 && i+1 < args.size) args[i+1].toIntOrNull() ?: 4 else 4 }

        return withContext(Dispatchers.IO) {
            try {
                val lines = mutableListOf<TerminalLine>()
                lines.add(TerminalLine("PING $host ($host): ${count} packets", TerminalLine.Type.INFO))
                var received = 0
                repeat(count) { i ->
                    val start = System.currentTimeMillis()
                    val reachable = InetAddress.getByName(host).isReachable(3000)
                    val elapsed = System.currentTimeMillis() - start
                    if (reachable) {
                        received++
                        lines.add(TerminalLine("64 bytes from $host: icmp_seq=$i ttl=64 time=${elapsed}ms", TerminalLine.Type.SUCCESS))
                    } else {
                        lines.add(TerminalLine("Request timeout for icmp_seq $i", TerminalLine.Type.ERROR))
                    }
                }
                lines.add(TerminalLine("--- $host ping statistics ---", TerminalLine.Type.SYSTEM))
                lines.add(TerminalLine("$count packets transmitted, $received received, ${count-received} lost", TerminalLine.Type.OUTPUT))
                Result(lines)
            } catch (e: Exception) {
                Result(listOf(TerminalLine("ping: ${host}: ${e.message}", TerminalLine.Type.ERROR)))
            }
        }
    }

    private fun cmdNetstat(): Result {
        return try {
            val lines = mutableListOf(TerminalLine("Proto  Local Address           Foreign Address         State", TerminalLine.Type.SYSTEM))
            listOf("/proc/net/tcp", "/proc/net/tcp6", "/proc/net/udp").forEach { path ->
                try {
                    val proto = path.substringAfterLast("/").replace("6","").uppercase()
                    File(path).readLines().drop(1).take(20).forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            fun parseAddr(hex: String): String {
                                val (ip, port) = hex.split(":")
                                val ipInt = ip.toLongOrNull(16) ?: 0L
                                val portInt = port.toIntOrNull(16) ?: 0
                                val a = (ipInt and 0xFF).toString()
                                val b = ((ipInt shr 8) and 0xFF).toString()
                                val c = ((ipInt shr 16) and 0xFF).toString()
                                val d = ((ipInt shr 24) and 0xFF).toString()
                                return "$a.$b.$c.$d:$portInt"
                            }
                            val local = parseAddr(parts[1])
                            val remote = parseAddr(parts[2])
                            val state = when (parts[3]) {
                                "01" -> "ESTABLISHED"; "0A" -> "LISTEN"; "06" -> "TIME_WAIT"
                                else -> "UNKNOWN"
                            }
                            lines.add(TerminalLine("%-6s %-23s %-23s %s".format(proto, local, remote, state), TerminalLine.Type.OUTPUT))
                        }
                    }
                } catch (e: Exception) { }
            }
            Result(lines)
        } catch (e: Exception) {
            Result(listOf(TerminalLine("netstat: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private suspend fun cmdWget(args: List<String>): Result {
        val url = args.firstOrNull { !it.startsWith("-") }
            ?: return Result(listOf(TerminalLine("Usage: wget <url>", TerminalLine.Type.WARNING)))
        return shell.executeLines("curl -L -O '$url' 2>&1", currentDir, 30000L).let { Result(it) }
    }

    private suspend fun cmdCurl(args: List<String>): Result {
        return shell.executeLines("curl ${args.joinToString(" ")} 2>&1", currentDir, 15000L).let { Result(it) }
    }

    // ─── Utility ───────────────────────────────────────────────────────────────

    private fun cmdEcho(args: List<String>): Result {
        val noNewline = "-n" in args
        val text = args.filter { it != "-n" && it != "-e" }.joinToString(" ")
        return Result(listOf(TerminalLine(text, TerminalLine.Type.OUTPUT)))
    }

    private fun cmdHistory(args: List<String>): Result {
        if ("-c" in args) {
            commandHistory.clear()
            return Result(listOf(TerminalLine("History cleared.", TerminalLine.Type.SUCCESS)))
        }
        val n = args.firstOrNull { it.toIntOrNull() != null }?.toInt() ?: commandHistory.size
        return Result(
            commandHistory.takeLast(n).mapIndexed { i, cmd ->
                TerminalLine("  ${(commandHistory.size - n + i + 1).toString().padStart(4)}  $cmd", TerminalLine.Type.OUTPUT)
            }
        )
    }

    private fun cmdAlias(args: List<String>): Result {
        if (args.isEmpty()) {
            return Result(aliases.map { (k, v) -> TerminalLine("alias $k='$v'", TerminalLine.Type.OUTPUT) })
        }
        val eq = args[0].indexOf('=')
        return if (eq > 0) {
            val name = args[0].substring(0, eq)
            val value = args[0].substring(eq + 1).trim('\'', '"')
            aliases[name] = value
            Result(emptyList())
        } else {
            val v = aliases[args[0]]
            if (v != null) Result(listOf(TerminalLine("alias ${args[0]}='$v'", TerminalLine.Type.OUTPUT)))
            else Result(listOf(TerminalLine("alias: ${args[0]}: not found", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdUnalias(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: unalias <name>", TerminalLine.Type.WARNING)))
        aliases.remove(args[0])
        return Result(emptyList())
    }

    private fun cmdEnv(): Result {
        return Result(envVars.map { (k, v) -> TerminalLine("$k=$v", TerminalLine.Type.OUTPUT) })
    }

    private fun cmdExport(args: List<String>): Result {
        if (args.isEmpty()) return cmdEnv()
        val eq = args[0].indexOf('=')
        return if (eq > 0) {
            val k = args[0].substring(0, eq)
            val v = args[0].substring(eq + 1)
            envVars[k] = v
            Result(emptyList())
        } else {
            Result(listOf(TerminalLine("export: ${args[0]}: invalid format (use KEY=value)", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdUnset(args: List<String>): Result {
        args.forEach { envVars.remove(it) }
        return Result(emptyList())
    }

    private fun cmdWhich(args: List<String>): Result {
        val cmd = args.firstOrNull() ?: return Result(listOf(TerminalLine("Usage: which <command>", TerminalLine.Type.WARNING)))
        val paths = listOf("/system/bin", "/system/xbin", "/sbin")
        for (p in paths) {
            val f = File(p, cmd)
            if (f.exists()) return Result(listOf(TerminalLine(f.absolutePath, TerminalLine.Type.OUTPUT)))
        }
        val builtins = setOf("help","clear","echo","pwd","cd","ls","cat","mkdir","rm","touch","cp","mv",
            "find","grep","head","tail","wc","date","uname","whoami","id","hostname","uptime","free",
            "df","ps","top","neofetch","history","alias","env","export","which","man","banner","matrix",
            "fortune","cowsay","ping","ifconfig","netstat","pm","getprop","logcat","dumpsys")
        return if (cmd in builtins) {
            Result(listOf(TerminalLine("$cmd: shell built-in command", TerminalLine.Type.OUTPUT)))
        } else {
            Result(listOf(TerminalLine("$cmd: not found", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdMan(args: List<String>): Result {
        val cmd = args.firstOrNull() ?: return Result(listOf(TerminalLine("Usage: man <command>", TerminalLine.Type.WARNING)))
        val manPages = mapOf(
            "ls" to "ls [-la] [path]\n  List directory contents.\n  -a  show hidden files\n  -l  long format",
            "cd" to "cd [path]\n  Change the current directory.\n  cd ~  go to home\n  cd -  go to parent",
            "cat" to "cat <file>\n  Print file contents to terminal.",
            "grep" to "grep [-inv] <pattern> <file>\n  Search for PATTERN in FILE.\n  -i  ignore case\n  -n  show line numbers\n  -v  invert match",
            "find" to "find [path] [-name pattern] [-maxdepth N]\n  Search for files.",
            "ps" to "ps\n  Report a snapshot of running processes.",
            "free" to "free [-h]\n  Display amount of free and used memory.\n  -h  human-readable sizes",
            "df" to "df [-h]\n  Report file system disk space usage.\n  -h  human-readable sizes",
            "ping" to "ping [-c count] <host>\n  Send ICMP ECHO_REQUEST to network hosts.",
            "neofetch" to "neofetch\n  Display system information alongside ASCII art.",
            "pm" to "pm list packages [-3|-s]\n  Manage Android packages.\n  -3  third-party only\n  -s  system only",
            "getprop" to "getprop [key]\n  Get Android system properties.",
            "banner" to "banner <text>\n  Display text as ASCII art banner.",
            "cowsay" to "cowsay <text>\n  A cow says your message.",
            "matrix" to "matrix\n  Display the Matrix digital rain.",
            "fortune" to "fortune\n  Display a random quote.",
            "history" to "history [-c] [n]\n  Show command history.\n  -c  clear history",
            "uname" to "uname [-a|-r|-m|-s|-n]\n  Print system information.",
            "alias" to "alias [name=value]\n  Create command aliases.",
            "export" to "export KEY=value\n  Set environment variables.",
            "nano" to "nano <arquivo>\n  Abre o editor de texto.\n  ^O salvar, ^X sair, ^W buscar, ^_ ir para linha.",
            "vi"   to "vi <arquivo>\n  Alias para nano.",
            "edit" to "edit <arquivo>\n  Alias para nano.",
            "view" to "view <arquivo>\n  Abre arquivo em modo somente leitura."
        )
        val page = manPages[cmd] ?: return Result(listOf(TerminalLine("No manual entry for $cmd", TerminalLine.Type.ERROR)))
        val lines = mutableListOf(
            TerminalLine("NAME", TerminalLine.Type.SUCCESS),
            TerminalLine("    $cmd", TerminalLine.Type.OUTPUT),
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("SYNOPSIS / DESCRIPTION", TerminalLine.Type.SUCCESS)
        )
        page.lines().forEach { lines.add(TerminalLine("    $it", TerminalLine.Type.OUTPUT)) }
        return Result(lines)
    }

    private fun cmdHelp(args: List<String>): Result {
        if (args.isNotEmpty()) return cmdMan(args)
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("╔═══════════════════════════════════════╗", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("║      LayerDroid Terminal v1.0         ║", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("╚═══════════════════════════════════════╝", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("NAVEGAÇÃO & ARQUIVOS", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  ls [-la]    ll    la    cd    pwd", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  cat    mkdir    rm [-rf]    touch", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  cp    mv    find    tree    stat    file", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  grep [-inv]    head    tail    wc    du", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("EDITOR", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  nano <arquivo>    vi    vim    edit", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  view <arquivo>  (somente leitura)", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("PACOTES / SCRIPTS  (pkg help para detalhes)", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  pkg update    pkg list    pkg available", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  pkg install <nome>    pkg run <nome>    pkg remove <nome>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("INTERNET", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  weather [cidade]    myip    ipinfo [ip]", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  gh <user>    gh-repo <owner/repo>    tldr <cmd>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  define <palavra>    joke    catfact    coin    qr <texto>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("DISPOSITIVO", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  battery    device    wifi    volume", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  clip   copy <txt>   vibrate [ms]   notify <título> <msg>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  share <txt>    torch on|off    tts <texto>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("SISTEMA", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  uname    whoami    id    hostname    date", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  uptime    free    df    ps    top    lscpu", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  mount    lsblk    getprop    neofetch", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("ANDROID", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  pm list packages [-3|-s]    pm path <pkg>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  logcat    dumpsys [service]    settings", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  am start    service list    input text", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("REDE", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  ifconfig    ping    netstat    curl    wget", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("SHELL", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  echo    history [-c]    alias    export", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  env    unset    which    man    clear    exit", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  chmod    sort    grep    sed    awk    base64", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("DIVERSÃO", TerminalLine.Type.INFO))
        lines.add(TerminalLine("  neofetch    banner <text>    cowsay <text>", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  matrix    fortune    sl    rev", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  Dica: use 'man <cmd>' para detalhes de cada comando.", TerminalLine.Type.SYSTEM))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        return Result(lines)
    }

    // ─── Fun Commands ──────────────────────────────────────────────────────────

    private fun cmdBanner(args: List<String>): Result {
        val text = args.joinToString(" ").uppercase().take(10)
        if (text.isEmpty()) return Result(listOf(TerminalLine("Usage: banner <text>", TerminalLine.Type.WARNING)))

        val charMap = mapOf(
            'A' to listOf(" ██ "," ████ "," ██ ██ ","██████","██  ██"),
            'B' to listOf("█████ ","██  ██","█████ ","██  ██","█████ "),
            'C' to listOf(" ████","██   ","██   ","██   "," ████"),
            'D' to listOf("████ ","██ ██","██ ██","██ ██","████ "),
            'E' to listOf("█████","██   ","████ ","██   ","█████"),
            'F' to listOf("█████","██   ","████ ","██   ","██   "),
            'G' to listOf(" ████","██   ","██ ██","██ ██"," ████"),
            'H' to listOf("██ ██","██ ██","█████","██ ██","██ ██"),
            'I' to listOf("████","  ██","  ██","  ██","████"),
            'J' to listOf(" ████","   ██","   ██","██ ██"," ███ "),
            'K' to listOf("██ ██","██ ██","████ ","██ ██","██ ██"),
            'L' to listOf("██   ","██   ","██   ","██   ","█████"),
            'M' to listOf("██████","███ ██","██████","██  ██","██  ██"),
            'N' to listOf("██  ██","███ ██","██████","██ ███","██  ██"),
            'O' to listOf(" ████","██  ██","██  ██","██  ██"," ████"),
            'P' to listOf("████ ","██ ██","████ ","██   ","██   "),
            'Q' to listOf(" ████","██  ██","██  ██","██ ███"," ██ ██"),
            'R' to listOf("████ ","██ ██","████ ","██ ██","██  ██"),
            'S' to listOf(" ████","██   "," ███ ","   ██","████ "),
            'T' to listOf("██████","  ██  ","  ██  ","  ██  ","  ██  "),
            'U' to listOf("██  ██","██  ██","██  ██","██  ██"," ████ "),
            'V' to listOf("██  ██","██  ██","██  ██"," ████ ","  ██  "),
            'W' to listOf("██  ██","██  ██","██████","███ ██","██  ██"),
            'X' to listOf("██  ██"," ████ ","  ██  "," ████ ","██  ██"),
            'Y' to listOf("██  ██","██  ██"," ████ ","  ██  ","  ██  "),
            'Z' to listOf("██████","   ██ ","  ██  "," ██   ","██████"),
            '0' to listOf(" ████","██  ██","██  ██","██  ██"," ████ "),
            '1' to listOf("  ██","  ██","  ██","  ██","  ██"),
            ' ' to listOf("   ","   ","   ","   ","   "),
            '!' to listOf("██","██","██","  ","██"),
            '?' to listOf(" ████","   ██","  ██ ","     ","  ██ ")
        )

        val rows = 5
        val lines = Array(rows) { StringBuilder() }
        for (ch in text) {
            val glyph = charMap[ch] ?: charMap[' ']!!
            for (row in 0 until rows) {
                lines[row].append(glyph.getOrElse(row) { "    " })
                lines[row].append("  ")
            }
        }
        return Result(lines.map { TerminalLine(it.toString(), TerminalLine.Type.SUCCESS) })
    }

    private fun cmdMatrix(): Result {
        val chars = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ01234567890!@#$%"
        val width = 40
        val lines = mutableListOf<TerminalLine>()
        repeat(12) {
            val line = (1..width).map { chars.random() }.joinToString("")
            lines.add(TerminalLine(line, TerminalLine.Type.SUCCESS))
        }
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  Wake up, Neo...", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("  The Matrix has you.", TerminalLine.Type.INFO))
        return Result(lines)
    }

    private fun cmdFortune(): Result {
        val fortunes = listOf(
            "\"Any sufficiently advanced technology is indistinguishable from magic.\" – Arthur C. Clarke",
            "\"The best way to predict the future is to invent it.\" – Alan Kay",
            "\"Programs must be written for people to read, and only incidentally for machines to execute.\" – SICP",
            "\"Talk is cheap. Show me the code.\" – Linus Torvalds",
            "\"First, solve the problem. Then, write the code.\" – John Johnson",
            "\"Code is like humor. When you have to explain it, it's bad.\" – Cory House",
            "\"The most disastrous thing that you can ever learn is your first programming language.\" – Alan Kay",
            "\"It's not a bug – it's an undocumented feature.\" – Anonymous",
            "\"There are only two hard things in CS: cache invalidation and naming things.\" – Phil Karlton",
            "\"The computer was born to solve problems that did not exist before.\" – Bill Gates",
            "\"Walking on water and developing software from a specification are easy if both are frozen.\" – Edward V. Berard",
            "\"Always code as if the guy who ends up maintaining your code will be a violent psychopath who knows where you live.\" – Martin Golding",
            "\"rm -rf /: não tente isso em casa.\" – Sysadmin anônimo",
            "\"99 little bugs in the code. 99 little bugs. Take one down, patch it around... 127 little bugs in the code.\" – Anonymous"
        )
        return Result(listOf(
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine(fortunes.random(), TerminalLine.Type.INFO),
            TerminalLine("", TerminalLine.Type.OUTPUT)
        ))
    }

    private fun cmdCowsay(args: List<String>): Result {
        val text = args.joinToString(" ").ifEmpty { "Moo!" }
        val bubbleWidth = text.length + 2
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine(" ${"_".repeat(bubbleWidth)}", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("< $text >", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine(" ${"-".repeat(bubbleWidth)}", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("        \\   ^__^", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("         \\  (oo)\\_______", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("            (__)\\       )\\/\\", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("                ||----w |", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("                ||     ||", TerminalLine.Type.OUTPUT))
        return Result(lines)
    }

    private fun cmdSl(): Result {
        return Result(listOf(
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("      ====        ________                ___________ ", TerminalLine.Type.SUCCESS),
            TerminalLine("  _D _|  |_______/        \\__I_I_____===__|_________| ", TerminalLine.Type.SUCCESS),
            TerminalLine("   |(_)---  |   H\\________/ |   |        =|___ ___|   ", TerminalLine.Type.SUCCESS),
            TerminalLine("   /     |  |   H  |  |     |   |         ||_| |_||   ", TerminalLine.Type.SUCCESS),
            TerminalLine("  |      |  |   H  |__--------------------| [___] |   ", TerminalLine.Type.SUCCESS),
            TerminalLine("  | ________|___H__/__|_____/[][]~\\_______|       |   ", TerminalLine.Type.SUCCESS),
            TerminalLine("  |/ |   |-----------I_____I [][] []  D   |=======|__ ", TerminalLine.Type.SUCCESS),
            TerminalLine("__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__ ", TerminalLine.Type.SUCCESS),
            TerminalLine(" |/-=|___|=    ||    ||    ||    |_____/~\\___/          ", TerminalLine.Type.SUCCESS),
            TerminalLine("  \\_/      \\O=====O=====O=====O_/      \\_/            ", TerminalLine.Type.SUCCESS),
            TerminalLine("", TerminalLine.Type.OUTPUT)
        ))
    }

    private fun cmdRev(args: List<String>): Result {
        val text = args.joinToString(" ")
        return if (text.isNotEmpty()) {
            Result(listOf(TerminalLine(text.reversed(), TerminalLine.Type.OUTPUT)))
        } else {
            Result(listOf(TerminalLine("Usage: rev <text>", TerminalLine.Type.WARNING)))
        }
    }

    // ─── Editor ────────────────────────────────────────────────────────────────

    private fun cmdNano(args: List<String>, readOnly: Boolean): Result {
        val filename = args.firstOrNull { !it.startsWith("-") }
            ?: return Result(listOf(
                TerminalLine("Usage: nano <arquivo>", TerminalLine.Type.WARNING),
                TerminalLine("       vi <arquivo>", TerminalLine.Type.WARNING),
                TerminalLine("       view <arquivo>  (somente leitura)", TerminalLine.Type.WARNING)
            ))
        val file = resolveFile(filename)
        if (file.isDirectory) {
            return Result(listOf(TerminalLine("nano: ${filename}: é um diretório", TerminalLine.Type.ERROR)))
        }
        val intent = NanoActivity.newIntent(context, file.absolutePath, readOnly)
        val msg = if (file.exists()) "Abrindo ${file.name}..." else "Criando ${file.name}..."
        return Result(
            lines = listOf(TerminalLine(msg, TerminalLine.Type.INFO)),
            launchIntent = intent
        )
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveFile(path: String): File {
        return when {
            path.startsWith("/") -> File(path)
            path.startsWith("~/") -> File(envVars["HOME"] ?: currentDir.absolutePath, path.substring(2))
            path == "~" -> File(envVars["HOME"] ?: currentDir.absolutePath)
            else -> File(currentDir, path)
        }.canonicalFile
    }

    private fun parseArgs(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\'' && !inDouble -> { inSingle = !inSingle }
                c == '"' && !inSingle -> { inDouble = !inDouble }
                c == ' ' && !inSingle && !inDouble -> {
                    if (current.isNotEmpty()) { result.add(current.toString()); current.clear() }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
