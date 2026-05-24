package com.nitware.layerdroid.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(private val context: Context) {

    companion object {
        private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        private const val TERMUX_BIN    = "$TERMUX_PREFIX/bin"
    }

    private val hasTermux: Boolean get() = File(TERMUX_BIN).canExecute()

    var currentDir: File = Environment.getExternalStorageDirectory().let {
        if (it.canRead()) it else context.filesDir
    }
    private val shell = ShellExecutor()
    private val pkg = PkgManager(context)
    private val aliases = mutableMapOf<String, String>()
    private val envVars: MutableMap<String, String> = mutableMapOf<String, String>().apply {
        val base = "/system/bin:/system/xbin:/sbin"
        put("HOME", currentDir.absolutePath)
        put("SHELL", "layerdroid")
        put("TERM", "xterm-256color")
        put("USER", "user")
        put("ANDROID_ROOT", "/system")
        put("PATH", if (File(TERMUX_BIN).canExecute()) "$TERMUX_BIN:$base" else base)
        if (File(TERMUX_BIN).canExecute()) {
            put("PREFIX", TERMUX_PREFIX)
            put("LD_LIBRARY_PATH", "$TERMUX_PREFIX/lib")
        }
    }
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

        // Delegate to shell for pipes, redirects, compound statements
        if (" | " in expanded || " > " in expanded || " >> " in expanded ||
            " < " in expanded || " && " in expanded || " || " in expanded || "; " in expanded) {
            return shell.executeLines(expanded, currentDir, envVars = envVars).let { Result(it) }
        }

        val parts = parseArgs(expanded)
        if (parts.isEmpty()) return Result(emptyList())

        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        return when (cmd) {
            "help", "?" -> cmdHelp(args)
            "clear", "cls" -> Result(emptyList(), shouldClear = true)
            "exit", "quit", "q" -> Result(listOf(TerminalLine("Exiting...", TerminalLine.Type.SYSTEM)), shouldExit = true)
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
            "su" -> Result(listOf(TerminalLine("su: Permission denied (app does not have root)", TerminalLine.Type.ERROR)))
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
            "coin", "btc", "bitcoin", "crypto" -> Result(NetCommands.coin(args))
            "qr", "qrcode" -> {
                val (lines, url) = NetCommands.qrCode(args)
                val intent = url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
                Result(lines, launchIntent = intent)
            }
            "http", "fetch" -> Result(NetCommands.httpRequest(args))
            "dns", "nslookup", "dig" -> Result(NetCommands.dns(args))
            "port", "portcheck" -> Result(NetCommands.portCheck(args))
            "speedtest", "speed" -> Result(NetCommands.speedtest())

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

            // ─── Encoding / crypto ────
            "hash" -> cmdHash(args)
            "encode" -> cmdEncode(args)
            "decode" -> cmdDecode(args)
            "jq" -> cmdJq(args)
            "calc", "math" -> cmdCalc(args)

            // ─── Open in browser ──────
            "open", "browse", "xdg-open" -> cmdOpen(args)

            // ─── Runtime / Termux integration ─────
            "python", "python3", "python2" -> cmdRuntime("python3", "python", args = args)
            "node", "nodejs" -> cmdRuntime("node", "nodejs", args = args)
            "php" -> cmdRuntime("php", args = args)
            "ruby" -> cmdRuntime("ruby", args = args)
            "lua" -> cmdRuntime("lua", args = args)
            "git" -> cmdRuntime("git", args = args)
            "ssh" -> cmdSsh(args)
            "termux-info", "termux" -> cmdTermuxInfo()

            else -> {
                // Try to execute as an installed script via pkg
                if (pkg.isInstalled(cmd)) {
                    Result(pkg.cmdRun(cmd, args, currentDir))
                } else {
                    val result = shell.executeLines(expanded, currentDir)
                    if (result.isEmpty()) {
                        Result(listOf(TerminalLine("$cmd: command not found. Type 'help' to see the list.", TerminalLine.Type.ERROR)))
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
                TerminalLine("pkg: unknown sub-command '$sub'", TerminalLine.Type.ERROR),
                TerminalLine("Use 'pkg help' to see available commands.", TerminalLine.Type.SYSTEM)
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

        val termuxStatus = if (hasTermux) "Termux:  ${File(TERMUX_BIN).listFiles()?.size ?: 0} pkgs"
                           else "Termux:  not installed"

        val logo = listOf(
            "  +--------------------+",
            "  | >_ LayerDroid      |",
            "  +--------------------+",
            "  |  #   ####          |",
            "  |  #   #  #          |",
            "  |  #   #  #          |",
            "  |  #   #  #          |",
            "  |  #### ####         |",
            "  +--------------------+",
            "  |  Android Terminal  |",
            "  +--------------------+",
            "  |   > _  v 1 . 0     |"
        )
        val info = listOf(
            "user@$hostname",
            "-".repeat("user@$hostname".length),
            "OS:      Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Kernel:  $kernel",
            "Device:  ${Build.MANUFACTURER} ${Build.MODEL}",
            "Board:   ${Build.BOARD}",
            "CPU:     ${Build.HARDWARE} ($cores cores, $abi)",
            "Memory:  $usedMem / $totalMem",
            "Storage: $storageUsed / $storageTotal",
            "Battery: $battery",
            "Uptime:  $uptime",
            "Shell:   LayerDroid Terminal v1.0",
            termuxStatus
        )

        val infoTypes = listOf(
            TerminalLine.Type.SUCCESS, TerminalLine.Type.SYSTEM,
            TerminalLine.Type.INFO,    TerminalLine.Type.OUTPUT,
            TerminalLine.Type.SUCCESS, TerminalLine.Type.OUTPUT,
            TerminalLine.Type.INFO,    TerminalLine.Type.WARNING,
            TerminalLine.Type.INFO,    TerminalLine.Type.SUCCESS,
            TerminalLine.Type.OUTPUT,  TerminalLine.Type.OUTPUT,
            TerminalLine.Type.INFO
        )

        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        logo.forEach { lines.add(TerminalLine(it, TerminalLine.Type.SUCCESS)) }
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        info.forEachIndexed { i, r ->
            lines.add(TerminalLine("  $r", infoTypes.getOrElse(i) { TerminalLine.Type.OUTPUT }))
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

        // Try system wget/curl first
        val shellResult = shell.executeLines(
            "wget -q --show-progress '$url' 2>&1 || curl -L -O '$url' 2>&1",
            currentDir, 30000L, envVars
        )
        if (shellResult.isNotEmpty() && shellResult.none { "not found" in it.text || "No such file" in it.text }) {
            return Result(shellResult)
        }
        // Fallback: built-in download
        return try {
            val filename = url.substringAfterLast("/").substringBefore("?").ifEmpty { "download" }
            val dest = File(currentDir, filename)
            val lines = mutableListOf<TerminalLine>()
            lines.add(TerminalLine("--  $url", TerminalLine.Type.INFO))
            val bytes = HttpClient.download(url, dest)
            lines.add(TerminalLine("'${dest.name}' saved [${bytes / 1024} KB]", TerminalLine.Type.SUCCESS))
            Result(lines)
        } catch (e: Exception) {
            Result(listOf(TerminalLine("wget: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private suspend fun cmdCurl(args: List<String>): Result {
        // Try system curl
        val shellResult = shell.executeLines("curl ${args.joinToString(" ")} 2>&1", currentDir, 15000L, envVars)
        if (shellResult.isNotEmpty() && shellResult.none { "not found" in it.text || "No such file" in it.text }) {
            return Result(shellResult)
        }
        // Fallback: built-in HttpClient
        val url = args.firstOrNull { it.startsWith("http") }
            ?: return Result(listOf(TerminalLine("Usage: curl [options] <url>", TerminalLine.Type.WARNING)))
        val method = run { val i = args.indexOf("-X"); if (i >= 0 && i + 1 < args.size) args[i + 1].uppercase() else "GET" }
        val data = run { val i = args.indexOf("-d").takeIf { it >= 0 } ?: args.indexOf("--data").takeIf { it >= 0 }; if (i != null && i + 1 < args.size) args[i + 1] else null }
        val extraHeaders = mutableMapOf<String, String>()
        for (i in args.indices) {
            if ((args[i] == "-H" || args[i] == "--header") && i + 1 < args.size) {
                val h = args[i + 1]
                val c = h.indexOf(":"); if (c > 0) extraHeaders[h.substring(0, c).trim()] = h.substring(c + 1).trim()
            }
        }
        return try {
            val body = if (method in listOf("POST", "PUT", "PATCH") || data != null) {
                HttpClient.post(url, data ?: "", headers = extraHeaders)
            } else {
                HttpClient.get(url, headers = extraHeaders)
            }
            Result(body.lines().take(200).map { TerminalLine(it, TerminalLine.Type.OUTPUT) })
        } catch (e: Exception) {
            Result(listOf(TerminalLine("curl: ${e.message}", TerminalLine.Type.ERROR)))
        }
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
            "nano" to "nano <file>\n  Open text editor.\n  ^O save, ^X exit, ^W search, ^_ go to line.",
            "vi"   to "vi <file>\n  Alias for nano.",
            "edit" to "edit <file>\n  Alias for nano.",
            "view" to "view <file>\n  Open file in read-only mode."
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

    // ─── Encoding / Hash ──────────────────────────────────────────────────────

    private fun cmdHash(args: List<String>): Result {
        val algo = args.firstOrNull()?.uppercase()
            ?: return Result(listOf(TerminalLine("Usage: hash <md5|sha1|sha256|sha512> <text>", TerminalLine.Type.WARNING)))
        val text = args.drop(1).joinToString(" ")
        if (text.isEmpty()) return Result(listOf(TerminalLine("hash: text required", TerminalLine.Type.WARNING)))
        val mdAlgo = when (algo) {
            "MD5"          -> "MD5"
            "SHA1", "SHA-1" -> "SHA-1"
            "SHA256", "SHA-256" -> "SHA-256"
            "SHA512", "SHA-512" -> "SHA-512"
            else -> return Result(listOf(TerminalLine("hash: invalid algorithm. Use: md5, sha1, sha256, sha512", TerminalLine.Type.ERROR)))
        }
        return try {
            val hash = MessageDigest.getInstance(mdAlgo).digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
            Result(listOf(
                TerminalLine("$mdAlgo  $hash", TerminalLine.Type.SUCCESS)
            ))
        } catch (e: Exception) {
            Result(listOf(TerminalLine("hash: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdEncode(args: List<String>): Result {
        val type = args.firstOrNull()?.lowercase()
            ?: return Result(listOf(TerminalLine("Usage: encode <base64|url|hex> <text>", TerminalLine.Type.WARNING)))
        val text = args.drop(1).joinToString(" ")
        if (text.isEmpty()) return Result(listOf(TerminalLine("encode: text required", TerminalLine.Type.WARNING)))
        return when (type) {
            "base64", "b64" -> Result(listOf(TerminalLine(
                Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP), TerminalLine.Type.SUCCESS
            )))
            "url" -> Result(listOf(TerminalLine(
                URLEncoder.encode(text, "UTF-8"), TerminalLine.Type.SUCCESS
            )))
            "hex" -> Result(listOf(TerminalLine(
                text.toByteArray().joinToString("") { "%02x".format(it) }, TerminalLine.Type.SUCCESS
            )))
            else -> Result(listOf(TerminalLine("encode: invalid type. Use: base64, url, hex", TerminalLine.Type.ERROR)))
        }
    }

    private fun cmdDecode(args: List<String>): Result {
        val type = args.firstOrNull()?.lowercase()
            ?: return Result(listOf(TerminalLine("Usage: decode <base64|url|hex> <encoded>", TerminalLine.Type.WARNING)))
        val text = args.drop(1).joinToString(" ")
        if (text.isEmpty()) return Result(listOf(TerminalLine("decode: text required", TerminalLine.Type.WARNING)))
        return when (type) {
            "base64", "b64" -> try {
                Result(listOf(TerminalLine(String(Base64.decode(text, Base64.DEFAULT)), TerminalLine.Type.SUCCESS)))
            } catch (_: Exception) { Result(listOf(TerminalLine("decode: invalid base64", TerminalLine.Type.ERROR))) }
            "url" -> try {
                Result(listOf(TerminalLine(URLDecoder.decode(text, "UTF-8"), TerminalLine.Type.SUCCESS)))
            } catch (_: Exception) { Result(listOf(TerminalLine("decode: invalid URL encoding", TerminalLine.Type.ERROR))) }
            "hex" -> try {
                val bytes = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                Result(listOf(TerminalLine(String(bytes), TerminalLine.Type.SUCCESS)))
            } catch (_: Exception) { Result(listOf(TerminalLine("decode: invalid hex", TerminalLine.Type.ERROR))) }
            else -> Result(listOf(TerminalLine("decode: invalid type. Use: base64, url, hex", TerminalLine.Type.ERROR)))
        }
    }

    // ─── JSON query ───────────────────────────────────────────────────────────

    private fun cmdJq(args: List<String>): Result {
        val query = args.firstOrNull { it.startsWith(".") }
        val fileArg = args.lastOrNull { !it.startsWith(".") }
            ?: return Result(listOf(
                TerminalLine("Usage: jq [.field] <file.json>", TerminalLine.Type.WARNING),
                TerminalLine("  ex:  jq data.json", TerminalLine.Type.OUTPUT),
                TerminalLine("  ex:  jq .name data.json", TerminalLine.Type.OUTPUT)
            ))
        val file = resolveFile(fileArg)
        if (!file.exists()) return Result(listOf(TerminalLine("jq: $fileArg: file not found", TerminalLine.Type.ERROR)))
        return try {
            val content = file.readText().trim()
            val parsed: Any = if (content.startsWith("[")) JSONArray(content) else JSONObject(content)
            val result = if (query != null && query.length > 1 && parsed is JSONObject) {
                val keys = query.removePrefix(".").split(".")
                var cur: Any? = parsed
                for (k in keys) cur = (cur as? JSONObject)?.opt(k)
                when (cur) {
                    is JSONObject -> cur.toString(2)
                    is JSONArray -> cur.toString(2)
                    null -> "null"
                    else -> cur.toString()
                }
            } else {
                if (parsed is JSONObject) parsed.toString(2) else (parsed as JSONArray).toString(2)
            }
            Result(result.lines().map { TerminalLine(it, TerminalLine.Type.OUTPUT) })
        } catch (e: Exception) {
            Result(listOf(TerminalLine("jq: ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    // ─── Calculator ───────────────────────────────────────────────────────────

    private fun cmdCalc(args: List<String>): Result {
        val expr = args.joinToString(" ").trim()
        if (expr.isEmpty()) return Result(listOf(
            TerminalLine("Usage: calc <expression>", TerminalLine.Type.WARNING),
            TerminalLine("  ex: calc 2+2   calc sqrt(16)   calc pi*2   calc 10^3", TerminalLine.Type.OUTPUT)
        ))
        return try {
            val result = MathParser(expr.replace(" ", "")).parse()
            val display = if (result == Math.floor(result) && !result.isInfinite()) result.toLong().toString()
                          else "%.10g".format(result).trimEnd('0').trimEnd('.')
            Result(listOf(TerminalLine("= $display", TerminalLine.Type.SUCCESS)))
        } catch (e: Exception) {
            Result(listOf(TerminalLine("calc: invalid expression — ${e.message}", TerminalLine.Type.ERROR)))
        }
    }

    private class MathParser(private val e: String) {
        private var p = 0
        fun parse() = expr().also { if (p < e.length) throw IllegalArgumentException("unexpected '${e[p]}'") }
        private fun expr(): Double {
            var r = term()
            while (p < e.length && (e[p] == '+' || e[p] == '-')) { val op = e[p++]; r = if (op == '+') r + term() else r - term() }
            return r
        }
        private fun term(): Double {
            var r = pow()
            while (p < e.length && (e[p] == '*' || e[p] == '/')) { val op = e[p++]; r = if (op == '*') r * pow() else r / pow() }
            return r
        }
        private fun pow(): Double { val b = unary(); return if (p < e.length && e[p] == '^') { p++; Math.pow(b, unary()) } else b }
        private fun unary(): Double { if (p < e.length && e[p] == '-') { p++; return -primary() }; if (p < e.length && e[p] == '+') p++; return primary() }
        private fun primary(): Double {
            if (p < e.length && e[p] == '(') { p++; val r = expr(); if (p < e.length && e[p] == ')') p++; return r }
            val wordEnd = e.indexOfFirst { idx -> idx >= p && !e[idx].isLetter() }.takeIf { it > p } ?: run { var i = p; while (i < e.length && e[i].isLetter()) i++; i }
            if (wordEnd > p) {
                val name = e.substring(p, wordEnd); p = wordEnd
                if (p < e.length && e[p] == '(') {
                    p++; val a = expr(); if (p < e.length && e[p] == ')') p++
                    return when (name) {
                        "sqrt" -> Math.sqrt(a); "abs" -> Math.abs(a); "sin" -> Math.sin(a)
                        "cos" -> Math.cos(a); "tan" -> Math.tan(a); "log" -> Math.log10(a)
                        "ln" -> Math.log(a); "floor" -> Math.floor(a); "ceil" -> Math.ceil(a)
                        "round" -> Math.round(a).toDouble(); "exp" -> Math.exp(a)
                        else -> throw IllegalArgumentException("unknown function: $name")
                    }
                }
                return when (name) { "pi" -> Math.PI; "e" -> Math.E; else -> throw IllegalArgumentException("unknown constant: $name") }
            }
            val start = p
            if (p < e.length && e[p] == '.') p++
            while (p < e.length && (e[p].isDigit() || e[p] == '.')) p++
            if (p == start) throw IllegalArgumentException("number expected at position $p")
            return e.substring(start, p).toDouble()
        }
    }

    // ─── Open URL ─────────────────────────────────────────────────────────────

    private fun cmdOpen(args: List<String>): Result {
        val raw = args.firstOrNull()
            ?: return Result(listOf(TerminalLine("Usage: open <url>", TerminalLine.Type.WARNING)))
        val url = if (!raw.startsWith("http")) "https://$raw" else raw
        return Result(
            listOf(TerminalLine("Opening: $url", TerminalLine.Type.INFO)),
            launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    // ─── Runtime (Python / Node / Git / etc.) ─────────────────────────────────

    private suspend fun cmdRuntime(vararg names: String, args: List<String>): Result {
        val candidates = buildList {
            names.forEach { n -> add(n); add("$TERMUX_BIN/$n") }
        }
        for (bin in candidates) {
            val result = shell.executeLines("$bin ${args.joinToString(" ")} 2>&1", currentDir, 30000L, envVars)
            if (result.isNotEmpty() && result.none { "not found" in it.text || "No such file" in it.text || "cannot find" in it.text }) {
                return Result(result)
            }
        }
        val name = names.first()
        return Result(listOf(
            TerminalLine("$name: not found on this system", TerminalLine.Type.ERROR),
            TerminalLine("  If Termux is installed: pkg install $name", TerminalLine.Type.INFO),
            if (hasTermux) TerminalLine("  Termux detected — run: pkg install $name in Termux", TerminalLine.Type.WARNING)
            else TerminalLine("  Install Termux to use real Linux packages", TerminalLine.Type.WARNING)
        ))
    }

    private suspend fun cmdSsh(args: List<String>): Result {
        if (args.isEmpty()) return Result(listOf(TerminalLine("Usage: ssh [user@]host [-p port]", TerminalLine.Type.WARNING)))
        // Try via Termux or system ssh
        val result = shell.executeLines("ssh ${args.joinToString(" ")} 2>&1", currentDir, 30000L, envVars)
        if (result.isNotEmpty() && result.none { "not found" in it.text }) return Result(result)
        return Result(listOf(
            TerminalLine("ssh: SSH client not found", TerminalLine.Type.ERROR),
            TerminalLine("  Install Termux and run: pkg install openssh", TerminalLine.Type.INFO)
        ))
    }

    private fun cmdTermuxInfo(): Result {
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("Termux Integration", TerminalLine.Type.INFO))
        lines.add(TerminalLine("-".repeat(40), TerminalLine.Type.SYSTEM))
        if (hasTermux) {
            val binDir = File(TERMUX_BIN)
            val pkgCount = binDir.listFiles()?.size ?: 0
            lines.add(TerminalLine("Status:   Detected and active", TerminalLine.Type.SUCCESS))
            lines.add(TerminalLine("Prefix:   $TERMUX_PREFIX", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("Binaries: $pkgCount executables available", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("PATH:     ${envVars["PATH"]}", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("Termux binaries available in this session.", TerminalLine.Type.SUCCESS))
        } else {
            lines.add(TerminalLine("Status:   Not detected", TerminalLine.Type.WARNING))
            lines.add(TerminalLine("Install Termux (F-Droid or Play Store) to get:", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("  * Python, Node.js, Ruby, PHP", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("  * Git, SSH, curl, wget (native)", TerminalLine.Type.OUTPUT))
            lines.add(TerminalLine("  * 1000+ Linux packages", TerminalLine.Type.OUTPUT))
        }
        return Result(lines)
    }

    private fun cmdHelp(args: List<String>): Result {
        if (args.isNotEmpty()) return cmdMan(args)
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  +--------------------------------------+", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("  |    LayerDroid Terminal  v1.0         |", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("  |    Advanced Android Terminal         |", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("  +--------------------------------------+", TerminalLine.Type.SUCCESS))
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        fun sec(title: String) { lines.add(TerminalLine("  $title", TerminalLine.Type.INFO)) }
        fun cmd(text: String) { lines.add(TerminalLine("    $text", TerminalLine.Type.OUTPUT)) }

        sec("FILES & NAVIGATION")
        cmd("ls [-la]  ll  la  cd  pwd  tree  stat  file  du")
        cmd("cat  mkdir  rm [-rf]  touch  cp  mv  chmod  ln")
        cmd("grep [-inv]  head  tail  wc  find  sort  uniq")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("TEXT EDITOR")
        cmd("nano <file>  vi  vim  edit  view (read-only)")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("INTERNET & HTTP")
        cmd("http [GET|POST] <url> [Header:V] [key=val]")
        cmd("curl <url>    wget <url>")
        cmd("weather [city]   myip   ipinfo [ip]   speedtest")
        cmd("dns <host>   port <host> <port>")
        cmd("gh <user>   gh-repo <owner/repo>   tldr <cmd>")
        cmd("define <word>   joke   catfact   fact")
        cmd("coin [btc,eth,sol]   qr <text>")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("CRYPTO / ENCODING")
        cmd("hash <md5|sha1|sha256|sha512> <text>")
        cmd("encode <base64|url|hex> <text>")
        cmd("decode <base64|url|hex> <encoded>")
        cmd("jq [.field] <file.json>")
        cmd("calc <expr>   ex: calc sqrt(16)*pi")
        cmd("base64  md5sum  sha256sum")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("PACKAGES (pkg help for details)")
        cmd("pkg update   pkg list   pkg available   pkg search <q>")
        cmd("pkg install <name>   pkg remove <name>   pkg run <name>")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("RUNTIMES (requires Termux)")
        cmd("python3 [script]   node [script]   php   ruby   lua")
        cmd("git <cmd>   ssh [user@]host   termux-info")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("ANDROID DEVICE")
        cmd("battery  device  wifi  volume  sensor")
        cmd("clip  copy <txt>  vibrate [ms]  notify <title> <msg>")
        cmd("share <txt>  torch on|off  tts <text>  open <url>")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("SYSTEM")
        cmd("neofetch  uname  whoami  id  hostname  date  uptime")
        cmd("free  df  ps  top  lscpu  lsblk  mount  getprop")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("NATIVE ANDROID")
        cmd("pm list packages [-3|-s]   logcat   dumpsys [svc]")
        cmd("am start   service list   settings   input text")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("NETWORK")
        cmd("ping  ifconfig  ip  netstat  dns  port  speedtest")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("SHELL")
        cmd("echo  history [-c]  alias  export  env  which  man")
        cmd("env  unset  clear  exit  awk  sed  tr  cut  xargs")
        cmd("Pipes and redirects supported:  cmd1 | cmd2 > file")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))

        sec("FUN")
        cmd("neofetch  banner <txt>  cowsay <txt>  matrix")
        cmd("fortune  sl  rev")
        lines.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        lines.add(TerminalLine("  'man <cmd>' for details  •  TAB to complete", TerminalLine.Type.SYSTEM))
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
            "\"rm -rf /: don't try this at home.\" – Anonymous sysadmin",
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
                TerminalLine("Usage: nano <file>", TerminalLine.Type.WARNING),
                TerminalLine("       vi <file>", TerminalLine.Type.WARNING),
                TerminalLine("       view <file>  (read-only)", TerminalLine.Type.WARNING)
            ))
        val file = resolveFile(filename)
        if (file.isDirectory) {
            return Result(listOf(TerminalLine("nano: ${filename}: is a directory", TerminalLine.Type.ERROR)))
        }
        val intent = NanoActivity.newIntent(context, file.absolutePath, readOnly)
        val msg = if (file.exists()) "Opening ${file.name}..." else "Creating ${file.name}..."
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
