package com.nitware.layerdroid.terminal

import android.content.Context
import org.json.JSONObject
import java.io.File

class PkgManager(private val context: Context) {

    companion object {
        const val DEFAULT_REPO_URL =
            "https://raw.githubusercontent.com/nitwareprojects/layerdroid-pkg/main/manifest.json"
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
        lines.add(TerminalLine("↓ Atualizando manifest de $repoUrl ...", TerminalLine.Type.INFO))
        return try {
            val text = HttpClient.get(repoUrl, timeoutMs = 8000)
            JSONObject(text)
            manifestCacheFile.writeText(text)
            val manifest = JSONObject(text)
            val count = manifest.optJSONArray("scripts")?.length() ?: 0
            lines.add(TerminalLine("✓ Manifest atualizado: ${manifest.optString("repo")} (${count} scripts)", TerminalLine.Type.SUCCESS))
            lines
        } catch (e: Exception) {
            lines.add(TerminalLine("✗ Falha ao baixar manifest: ${e.message}", TerminalLine.Type.ERROR))
            lines.add(TerminalLine("  Usando manifest embutido (pkg-core).", TerminalLine.Type.WARNING))
            lines
        }
    }

    fun cmdList(): List<TerminalLine> {
        val installed = scriptsDir.listFiles { _, name -> name.endsWith(".sh") }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()
        if (installed.isEmpty()) {
            return listOf(
                TerminalLine("Nenhum script instalado.", TerminalLine.Type.WARNING),
                TerminalLine("Use 'pkg available' para listar pacotes do repositório.", TerminalLine.Type.SYSTEM)
            )
        }
        val manifest = loadManifest()
        val byName = manifest.scriptIndex()
        val out = mutableListOf<TerminalLine>(
            TerminalLine("Scripts instalados (${installed.size}):", TerminalLine.Type.INFO)
        )
        installed.forEach { f ->
            val name = f.nameWithoutExtension
            val meta = byName[name]
            val ver = meta?.optString("version", "?") ?: "local"
            val desc = meta?.optString("description") ?: "(script local)"
            out.add(TerminalLine("  ● %-18s v%-6s %s".format(name, ver, desc), TerminalLine.Type.OUTPUT))
        }
        out.add(TerminalLine("", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Execute com: pkg run <nome>  ou  <nome> direto.", TerminalLine.Type.SYSTEM))
        return out
    }

    fun cmdAvailable(): List<TerminalLine> {
        val manifest = loadManifest()
        val scripts = manifest.optJSONArray("scripts") ?: return listOf(
            TerminalLine("Manifest vazio.", TerminalLine.Type.WARNING)
        )
        val out = mutableListOf<TerminalLine>(
            TerminalLine("Repositório: ${manifest.optString("repo")}", TerminalLine.Type.INFO),
            TerminalLine("Pacotes disponíveis (${scripts.length()}):", TerminalLine.Type.INFO),
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
        out.add(TerminalLine("Instale com: pkg install <nome>", TerminalLine.Type.SYSTEM))
        return out
    }

    fun cmdSearch(query: String): List<TerminalLine> {
        if (query.isBlank()) return listOf(TerminalLine("Usage: pkg search <termo>", TerminalLine.Type.WARNING))
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
        return if (out.isEmpty()) listOf(TerminalLine("Nenhum pacote contém \"$query\".", TerminalLine.Type.WARNING))
               else listOf(TerminalLine("Resultados para \"$query\":", TerminalLine.Type.INFO)) + out
    }

    fun cmdInfo(name: String): List<TerminalLine> {
        val meta = findInManifest(name) ?: return listOf(
            TerminalLine("pkg: pacote '$name' não encontrado no manifest.", TerminalLine.Type.ERROR)
        )
        val installed = isInstalled(name)
        val out = mutableListOf<TerminalLine>()
        out.add(TerminalLine("Nome:        ${meta.optString("name")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Versão:      ${meta.optString("version", "?")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Autor:       ${meta.optString("author", "anônimo")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Descrição:   ${meta.optString("description", "")}", TerminalLine.Type.OUTPUT))
        if (meta.has("url")) out.add(TerminalLine("URL:         ${meta.optString("url")}", TerminalLine.Type.OUTPUT))
        out.add(TerminalLine("Instalado:   ${if (installed) "sim (${scriptFile(name).length()} bytes)" else "não"}",
            if (installed) TerminalLine.Type.SUCCESS else TerminalLine.Type.WARNING))
        return out
    }

    suspend fun cmdInstall(name: String): List<TerminalLine> {
        val meta = findInManifest(name) ?: return listOf(
            TerminalLine("pkg: pacote '$name' não encontrado.", TerminalLine.Type.ERROR),
            TerminalLine("Tente 'pkg update' para atualizar o manifest.", TerminalLine.Type.SYSTEM)
        )
        val out = mutableListOf<TerminalLine>()
        out.add(TerminalLine("↓ Instalando $name...", TerminalLine.Type.INFO))
        val dest = scriptFile(name)
        return try {
            val content = when {
                meta.has("inline") -> meta.getString("inline")
                meta.has("url") -> HttpClient.get(meta.getString("url"), timeoutMs = 15000)
                else -> return out + TerminalLine("pkg: pacote sem conteúdo (sem 'inline' nem 'url').", TerminalLine.Type.ERROR)
            }
            dest.writeText(content)
            dest.setExecutable(true)
            out.add(TerminalLine("✓ Instalado: $name v${meta.optString("version", "?")} (${content.length} bytes)",
                TerminalLine.Type.SUCCESS))
            out.add(TerminalLine("  Execute com: $name  ou  pkg run $name", TerminalLine.Type.SYSTEM))
            out
        } catch (e: Exception) {
            out.add(TerminalLine("✗ Falha: ${e.message}", TerminalLine.Type.ERROR))
            out
        }
    }

    fun cmdRemove(name: String): List<TerminalLine> {
        val f = scriptFile(name)
        return if (!f.exists()) {
            listOf(TerminalLine("pkg: '$name' não está instalado.", TerminalLine.Type.WARNING))
        } else if (f.delete()) {
            listOf(TerminalLine("✓ Removido: $name", TerminalLine.Type.SUCCESS))
        } else {
            listOf(TerminalLine("✗ Falha ao remover '$name'.", TerminalLine.Type.ERROR))
        }
    }

    suspend fun cmdRun(name: String, args: List<String>, workingDir: File): List<TerminalLine> {
        val f = scriptFile(name)
        if (!f.exists()) return listOf(TerminalLine("pkg: '$name' não está instalado. Use 'pkg install $name'.", TerminalLine.Type.ERROR))
        val quotedArgs = args.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        return shell.executeLines("sh '${f.absolutePath}' $quotedArgs", workingDir, timeoutMs = 30_000L)
    }

    fun cmdRepo(): List<TerminalLine> {
        return listOf(
            TerminalLine("Repositório atual:", TerminalLine.Type.INFO),
            TerminalLine("  $repoUrl", TerminalLine.Type.OUTPUT),
            TerminalLine("", TerminalLine.Type.OUTPUT),
            TerminalLine("Trocar com: pkg setrepo <url>", TerminalLine.Type.SYSTEM),
            TerminalLine("Resetar:    pkg setrepo default", TerminalLine.Type.SYSTEM)
        )
    }

    fun cmdSetRepo(url: String): List<TerminalLine> {
        if (url.isBlank()) return listOf(TerminalLine("Usage: pkg setrepo <url>", TerminalLine.Type.WARNING))
        val newUrl = if (url == "default" || url == "reset") DEFAULT_REPO_URL else url
        repoUrl = newUrl
        manifestCacheFile.delete()
        return listOf(
            TerminalLine("✓ Repositório alterado para:", TerminalLine.Type.SUCCESS),
            TerminalLine("  $newUrl", TerminalLine.Type.OUTPUT),
            TerminalLine("Execute 'pkg update' para baixar o novo manifest.", TerminalLine.Type.SYSTEM)
        )
    }

    fun help(): List<TerminalLine> = listOf(
        TerminalLine("pkg — Gerenciador de scripts do LayerDroid", TerminalLine.Type.SUCCESS),
        TerminalLine("", TerminalLine.Type.OUTPUT),
        TerminalLine("Comandos:", TerminalLine.Type.INFO),
        TerminalLine("  pkg update            Baixa/atualiza o manifest do repositório", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg list              Lista scripts instalados", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg available         Lista scripts disponíveis no repositório", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg search <termo>    Busca pacotes por nome/descrição", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg info <nome>       Detalhes de um pacote", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg install <nome>    Instala um pacote", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg remove <nome>     Remove um pacote instalado", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg run <nome> [args] Executa um script instalado", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg repo              Mostra URL do repositório atual", TerminalLine.Type.OUTPUT),
        TerminalLine("  pkg setrepo <url>     Define novo repositório (ou 'default')", TerminalLine.Type.OUTPUT),
        TerminalLine("", TerminalLine.Type.OUTPUT),
        TerminalLine("Dica: scripts instalados podem ser chamados direto pelo nome.", TerminalLine.Type.SYSTEM)
    )

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun loadManifest(): JSONObject {
        return try {
            val text = if (manifestCacheFile.exists()) manifestCacheFile.readText()
                       else context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            JSONObject().apply { put("repo", "vazio"); put("scripts", org.json.JSONArray()) }
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
