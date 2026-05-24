package com.nitware.layerdroid.terminal

import android.content.Context
import org.json.JSONObject
import java.io.File

class PkgManager(private val context: Context) {

    companion object {
        const val DEFAULT_REPO_URL =
            "https://raw.githubusercontent.com/nitwareprojects/LayerDroid-Terminal/claude/android-terminal-app-s78uq/app/src/main/assets/pkg-core.json"
        private const val BUNDLED_ASSET = "pkg-core.json"
    }

    private val baseDir = File(context.filesDir, "pkg").apply { mkdirs() }
    val scriptsDir: File = File(baseDir, "scripts").apply { mkdirs() }
    private val manifestCacheFile = File(baseDir, "manifest.json")
    private val configFile = File(baseDir, "repo.txt")
    private val shell = ShellExecutor()

    var repoUrl: String
        get() = if (configFile.exists()) configFile.readText().trim().ifEmpty { DEFAULT_REPO_URL }
                else DEFAULT_REPO_URL
        set(value) { configFile.writeText(value) }

    fun isInstalled(name: String): Boolean = scriptFile(name).exists()

    fun scriptFile(name: String): File = File(scriptsDir, "$name.sh")

    suspend fun cmdUpdate(): List<TerminalLine> {
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("Updating manifest from $repoUrl ...", TerminalLine.Type.INFO))
        return try {
            val text = HttpClient.get(repoUrl, timeoutMs = 8000)
            JSONObject(text)
            manifestCacheFile.writeText(text)
            val manifest = JSONObject(text)
            val count = manifest.optJSONArray("scripts")?.length() ?: 0
            lines.add(TerminalLine("Manifest updated: ${manifest.optString("repo")} ($count scripts)", TerminalLine.Type.SUCCESS))
            lines
        } catch (e: Exception) {
            lines.add(TerminalLine("Warning: could not fetch remote manifest: ${e.message}", TerminalLine.Type.WARNING))
            lines.add(TerminalLine("  Falling back to bundled pkg-core manifest...", TerminalLine.Type.WARNING))
            try {
                val bundled = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
                val manifest = JSONObject(bundled)
                manifestCacheFile.writeText(bundled)
                val count = manifest.optJSONArray("scripts")?.length() ?: 0
                lines.add(TerminalLine("Loaded bundled manifest: ${manifest.optString("repo")} ($count scripts)", TerminalLine.Type.SUCCESS))
            } catch (ex: Exception) {
                lines.add(TerminalLine("  Could not load bundled manifest: ${ex.message}", TerminalLine.Type.WARNING))
            }
            lines
        }
    }

    fun cmdList(): List<TerminalLine> {
        val installed = scriptsDir.listFiles { _, name -> name.endsWith(".sh") }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()
        if (installed.isEmpty()) {
            return listOf(
                TerminalLine("No scripts installed.", TerminalLine.Type.WARNING),
                TerminalLine("Use 'pkg available' to list packages from the repository.", TerminalLine.Type.SYSTEM)
            )
        }
        val manifest = loadManifest()
        val byName = manifest.scriptIndex()
        val out = mutableListOf<TerminalLine>(
            TerminalLine("Installed scripts (${installed.size}):", TerminalLine.Type.INFO)
        )
        installed.forEach { f ->
            val name = f.nameWithoutExtension
            val meta = byName[name]
            val ver = meta?.optString("version", "?") ?: "local"
            val desc = meta?.optString("description") ?: "(local script)"
            out.add(TerminalLine("  * %-18s v%-6s %s".format(name, ver, desc), TerminalLine.Type.OUTPUT))
        }
        out.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Run with: pkg run <name>  or  <name> directly.", TerminalLine.Type.SYSTEM))
        return out
    }

    fun cmdAvailable(): List<TerminalLine> {
        val manifest = loadManifest()
        val scripts = manifest.optJSONArray("scripts") ?: return listOf(
            TerminalLine("Manifest is empty.", TerminalLine.Type.WARNING)
        )
        val out = mutableListOf<TerminalLine>(
            TerminalLine("Repository: ${manifest.optString("repo")}", TerminalLine.Type.INFO),
            TerminalLine("Available packages (${scripts.length()}):", TerminalLine.Type.INFO),
            TerminalLine("", TerminalLine.Type.OUTPUT)
        )
        for (i in 0 until scripts.length()) {
            val s = scripts.getJSONObject(i)
            val name = s.optString("name")
            val ver = s.optString("version", "?")
            val desc = s.optString("description", "")
            val mark = if (isInstalled(name)) "✓" else "·"
            val type = if (isInstalled(name)) TerminalLine.Type.SUCCESS else TerminalLine.Type.OUTPUT
            out.add(TerminalLine("  $mark %-18s v%-6s %s".format(name, ver, desc), type))
        }
        out.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Install with: pkg install <name>", TerminalLine.Type.SYSTEM))
        return out
    }

    fun cmdSearch(query: String): List<TerminalLine> {
        if (query.isBlank()) return listOf(TerminalLine("Usage: pkg search <term>", TerminalLine.Type.WARNING))
        val manifest = loadManifest()
        val scripts = manifest.optJSONArray("scripts") ?: return emptyList()
        val q = query.lowercase()
        val out = mutableListOf<TerminalLine>()
        for (i in 0 until scripts.length()) {
            val s = scripts.getJSONObject(i)
            val name = s.optString("name")
            val desc = s.optString("description", "")
            if (q in name.lowercase() || q in desc.lowercase()) {
                val ver = s.optString("version", "?")
                val mark = if (isInstalled(name)) "✓" else "·"
                out.add(TerminalLine("  $mark %-18s v%-6s %s".format(name, ver, desc), TerminalLine.Type.OUTPUT))
            }
        }
        return if (out.isEmpty()) listOf(TerminalLine("No package contains \"$query\".", TerminalLine.Type.WARNING))
               else listOf(TerminalLine("Results for \"$query\":", TerminalLine.Type.INFO)) + out
    }

    fun cmdInfo(name: String): List<TerminalLine> {
        val meta = findInManifest(name) ?: return listOf(
            TerminalLine("pkg: package '$name' not found in manifest.", TerminalLine.Type.ERROR)
        )
        val installed = isInstalled(name)
        val out = mutableListOf<TerminalLine>()
        out.add(TerminalLine("Name:        ${meta.optString("name")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Version:     ${meta.optString("version", "?")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Author:      ${meta.optString("author", "unknown")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Description: ${meta.optString("description", "")}", TerminalLine.Type.OUTPUT))
        if (meta.has("url")) out.add(TerminalLine("URL:         ${meta.optString("url")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Installed:   ${if (installed) "yes (${scriptFile(name).length()} bytes)" else "no"}",
            if (installed) TerminalLine.Type.SUCCESS else TerminalLine.Type.WARNING))
        return out
    }

    suspend fun cmdInstall(name: String): List<TerminalLine> {
        val meta = findInManifest(name) ?: return listOf(
            TerminalLine("pkg: package '$name' not found.", TerminalLine.Type.ERROR),
            TerminalLine("Try 'pkg update' to refresh the manifest.", TerminalLine.Type.SYSTEM)
        )
        val out = mutableListOf<TerminalLine>()
        out.add(TerminalLine("Installing $name...", TerminalLine.Type.INFO))
        val dest = scriptFile(name)
        return try {
            val content = when {
                meta.has("inline") -> meta.getString("inline")
                meta.has("url") -> HttpClient.get(meta.getString("url"), timeoutMs = 15000)
                else -> return out + TerminalLine("pkg: package has no content (missing 'inline' or 'url').", TerminalLine.Type.ERROR)
            }
            dest.writeText(content)
            dest.setExecutable(true)
            out.add(TerminalLine("Installed: $name v${meta.optString("version", "?")} (${content.length} bytes)",
                TerminalLine.Type.SUCCESS))
            out.add(TerminalLine("  Run with: $name  or  pkg run $name", TerminalLine.Type.SYSTEM))
            out
        } catch (e: Exception) {
            out.add(TerminalLine("Failed: ${e.message}", TerminalLine.Type.ERROR))
            out
        }
    }

    fun cmdRemove(name: String): List<TerminalLine> {
        val f = scriptFile(name)
        return if (!f.exists()) {
            listOf(TerminalLine("pkg: '$name' is not installed.", TerminalLine.Type.WARNING))
        } else if (f.delete()) {
            listOf(TerminalLine("Removed: $name", TerminalLine.Type.SUCCESS))
        } else {
            listOf(TerminalLine("Failed to remove '$name'.", TerminalLine.Type.ERROR))
        }
    }

    suspend fun cmdRun(name: String, args: List<String>, workingDir: File): List<TerminalLine> {
        val f = scriptFile(name)
        if (!f.exists()) return listOf(TerminalLine("pkg: '$name' is not installed. Use 'pkg install $name'.", TerminalLine.Type.ERROR))
        val quotedArgs = args.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        return shell.executeLines("sh '${f.absolutePath}' $quotedArgs", workingDir, timeoutMs = 30_000L)
    }

    fun cmdRepo(): List<TerminalLine> {
        return listOf(
            TerminalLine("Current repository:", TerminalLine.Type.INFO),
            TerminalLine("  $repoUrl", TerminalLine.Type.OUTPUT),
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("Change with: pkg setrepo <url>", TerminalLine.Type.SYSTEM),
            TerminalLine("Reset:       pkg setrepo default", TerminalLine.Type.SYSTEM)
        )
    }

    fun cmdSetRepo(url: String): List<TerminalLine> {
        if (url.isBlank()) return listOf(TerminalLine("Usage: pkg setrepo <url>", TerminalLine.Type.WARNING))
        val newUrl = if (url == "default" || url == "reset") DEFAULT_REPO_URL else url
        repoUrl = newUrl
        manifestCacheFile.delete()
        return listOf(
            TerminalLine("Repository changed to:", TerminalLine.Type.SUCCESS),
            TerminalLine("  $newUrl", TerminalLine.Type.OUTPUT),
            TerminalLine("Run 'pkg update' to fetch the new manifest.", TerminalLine.Type.SYSTEM)
        )
    }

    fun help(): List<TerminalLine> = listOf(
        TerminalLine("pkg — LayerDroid Script Manager", TerminalLine.Type.SUCCESS),
        TerminalLine("", TerminalLine.Type.OUTPUT),
        TerminalLine("Commands:", TerminalLine.Type.INFO),
        TerminalLine("  pkg update            Download/update manifest from repository", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg list              List installed scripts", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg available         List scripts available in repository", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg search <term>     Search packages by name/description", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg info <name>       Show package details", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg install <name>    Install a package", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg remove <name>     Remove an installed package", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg run <name> [args] Run an installed script", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg repo              Show current repository URL", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg setrepo <url>     Set new repository (or 'default')", TerminalLine.Type.OUTPUT),
        TerminalLine("", TerminalLine.Type.OUTPUT),
        TerminalLine("Tip: installed scripts can be called directly by name.", TerminalLine.Type.SYSTEM)
    )

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun loadManifest(): JSONObject {
        return try {
            val text = if (manifestCacheFile.exists()) manifestCacheFile.readText()
                       else context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            JSONObject().apply { put("repo", "empty"); put("scripts", org.json.JSONArray()) }
        }
    }

    private fun findInManifest(name: String): JSONObject? {
        val manifest = loadManifest()
        val scripts = manifest.optJSONArray("scripts") ?: return null
        for (i in 0 until scripts.length()) {
            val s = scripts.getJSONObject(i)
            if (s.optString("name") == name) return s
        }
        return null
    }

    private fun JSONObject.scriptIndex(): Map<String, JSONObject> {
        val arr = optJSONArray("scripts") ?: return emptyMap()
        val map = mutableMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            map[s.optString("name")] = s
        }
        return map
    }
}
