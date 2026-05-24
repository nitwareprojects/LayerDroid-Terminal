package com.nitware.layerdroid.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

object NetCommands {

    // ─── Weather ──────────────────────────────────────────────────────────────

    suspend fun weather(args: List<String>): List<TerminalLine> {
        val location = args.joinToString("+").ifEmpty { "" }
        val url = "https://wttr.in/${HttpClient.urlEncode(location).replace("%2B", "+")}?lang=pt&format=4"
        return try {
            val resp = HttpClient.get(url, timeoutMs = 8000)
            listOf(TerminalLine(resp.trim(), TerminalLine.Type.SUCCESS))
        } catch (e: Exception) {
            listOf(TerminalLine("weather: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun weatherAscii(args: List<String>): List<TerminalLine> {
        val location = args.joinToString("+").ifEmpty { "" }
        val url = "https://wttr.in/${HttpClient.urlEncode(location).replace("%2B", "+")}?0&lang=pt&T"
        return try {
            val resp = HttpClient.get(url, timeoutMs = 12000)
            resp.lines().map { TerminalLine(it, TerminalLine.Type.SUCCESS) }
        } catch (e: Exception) {
            listOf(TerminalLine("weather: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── Network info ─────────────────────────────────────────────────────────

    suspend fun myIp(): List<TerminalLine> {
        return try {
            val ip = HttpClient.get("https://api.ipify.org", timeoutMs = 6000).trim()
            listOf(
                TerminalLine("IP público:", TerminalLine.Type.INFO),
                TerminalLine("  $ip", TerminalLine.Type.SUCCESS)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("myip: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun ipInfo(args: List<String>): List<TerminalLine> {
        val ip = args.firstOrNull() ?: ""
        val url = "https://ip-api.com/json/${HttpClient.urlEncode(ip)}?fields=status,message,country,regionName,city,zip,lat,lon,timezone,isp,org,as,query"
        return try {
            val json = JSONObject(HttpClient.get(url, timeoutMs = 8000))
            if (json.optString("status") != "success") {
                return listOf(TerminalLine("ipinfo: ${json.optString("message", "erro")}", TerminalLine.Type.ERROR))
            }
            listOf(
                TerminalLine("IP:          ${json.optString("query")}", TerminalLine.Type.SUCCESS),
                TerminalLine("Localização: ${json.optString("city")}, ${json.optString("regionName")}, ${json.optString("country")}", TerminalLine.Type.OUTPUT),
                TerminalLine("CEP:         ${json.optString("zip")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Coords:      ${json.optDouble("lat")}, ${json.optDouble("lon")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Timezone:    ${json.optString("timezone")}", TerminalLine.Type.OUTPUT),
                TerminalLine("ISP:         ${json.optString("isp")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Org:         ${json.optString("org")}", TerminalLine.Type.OUTPUT),
                TerminalLine("AS:          ${json.optString("as")}", TerminalLine.Type.OUTPUT)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("ipinfo: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── DNS lookup ───────────────────────────────────────────────────────────

    suspend fun dns(args: List<String>): List<TerminalLine> {
        val host = args.firstOrNull()
            ?: return listOf(TerminalLine("Usage: dns <hostname>", TerminalLine.Type.WARNING))
        return try {
            val addresses = withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
            val lines = mutableListOf<TerminalLine>()
            lines.add(TerminalLine("DNS: $host", TerminalLine.Type.INFO))
            lines.add(TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM))
            addresses.forEach { addr ->
                val type = if (addr.hostAddress?.contains(":") == true) "AAAA" else "A   "
                lines.add(TerminalLine("  $type  ${addr.hostAddress}", TerminalLine.Type.SUCCESS))
            }
            lines
        } catch (e: Exception) {
            listOf(TerminalLine("dns: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── Port check ───────────────────────────────────────────────────────────

    suspend fun portCheck(args: List<String>): List<TerminalLine> {
        val host = args.getOrNull(0)
            ?: return listOf(TerminalLine("Usage: port <host> <port> [port2 ...]", TerminalLine.Type.WARNING))
        val ports = args.drop(1).mapNotNull { it.toIntOrNull() }
        if (ports.isEmpty()) return listOf(TerminalLine("port: informe pelo menos uma porta", TerminalLine.Type.WARNING))

        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("Verificando portas em $host...", TerminalLine.Type.INFO))
        ports.forEach { port ->
            val start = System.currentTimeMillis()
            val open = withContext(Dispatchers.IO) {
                try {
                    Socket().use { s -> s.connect(InetSocketAddress(host, port), 5000); true }
                } catch (_: Exception) { false }
            }
            val ms = System.currentTimeMillis() - start
            val status = if (open) TerminalLine.Type.SUCCESS else TerminalLine.Type.ERROR
            val label = if (open) "OPEN  " else "CLOSED"
            lines.add(TerminalLine("  :$port  $label  ${ms}ms", status))
        }
        return lines
    }

    // ─── HTTP request (HTTPie-style) ──────────────────────────────────────────

    suspend fun httpRequest(args: List<String>): List<TerminalLine> {
        if (args.isEmpty()) return listOf(
            TerminalLine("Usage: http [GET|POST|PUT|DELETE] <url> [Header:Value] [key=value ...]", TerminalLine.Type.WARNING),
            TerminalLine("  ex: http GET https://api.github.com/users/torvalds", TerminalLine.Type.OUTPUT),
            TerminalLine("  ex: http POST https://httpbin.org/post name=Linus", TerminalLine.Type.OUTPUT)
        )

        var method = "GET"
        var urlStr = ""
        val extraHeaders = mutableMapOf<String, String>()
        val bodyMap = mutableMapOf<String, String>()

        for (arg in args) {
            when {
                arg.uppercase() in listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD") ->
                    method = arg.uppercase()
                arg.startsWith("http://") || arg.startsWith("https://") ->
                    urlStr = arg
                arg.matches(Regex("[A-Za-z0-9_-]+:.+")) && !arg.startsWith("http") -> {
                    val colon = arg.indexOf(":")
                    extraHeaders[arg.substring(0, colon).trim()] = arg.substring(colon + 1).trim()
                }
                arg.contains("=") -> {
                    val eq = arg.indexOf("=")
                    bodyMap[arg.substring(0, eq)] = arg.substring(eq + 1)
                }
            }
        }

        if (urlStr.isEmpty()) return listOf(TerminalLine("http: URL necessária (http:// ou https://)", TerminalLine.Type.ERROR))

        return try {
            val start = System.currentTimeMillis()
            val (code, body) = when (method) {
                "POST", "PUT", "PATCH" -> {
                    val jsonBody = if (bodyMap.isNotEmpty()) {
                        JSONObject().also { obj -> bodyMap.forEach { (k, v) -> obj.put(k, v) } }.toString()
                    } else ""
                    val resp = HttpClient.post(urlStr, jsonBody, headers = extraHeaders)
                    Pair(200, resp)
                }
                else -> HttpClient.getWithHeaders(urlStr, headers = extraHeaders)
            }
            val elapsed = System.currentTimeMillis() - start

            val lines = mutableListOf<TerminalLine>()
            val codeType = when (code) {
                in 200..299 -> TerminalLine.Type.SUCCESS
                in 300..399 -> TerminalLine.Type.WARNING
                else -> TerminalLine.Type.ERROR
            }
            lines.add(TerminalLine("$method $urlStr", TerminalLine.Type.INFO))
            lines.add(TerminalLine("HTTP $code  •  ${elapsed}ms", codeType))
            lines.add(TerminalLine("─".repeat(50), TerminalLine.Type.SYSTEM))

            val trimmed = body.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    val pretty = if (trimmed.startsWith("{")) JSONObject(trimmed).toString(2)
                                 else JSONArray(trimmed).toString(2)
                    pretty.lines().take(80).forEach { lines.add(TerminalLine(it, TerminalLine.Type.OUTPUT)) }
                } catch (_: Exception) {
                    body.lines().take(80).forEach { lines.add(TerminalLine(it, TerminalLine.Type.OUTPUT)) }
                }
            } else {
                body.lines().take(80).forEach { lines.add(TerminalLine(it, TerminalLine.Type.OUTPUT)) }
            }
            if (body.lines().size > 80) lines.add(TerminalLine("... (${body.lines().size - 80} linhas omitidas)", TerminalLine.Type.SYSTEM))
            lines
        } catch (e: Exception) {
            listOf(TerminalLine("http: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── GitHub ───────────────────────────────────────────────────────────────

    suspend fun github(args: List<String>): List<TerminalLine> {
        val user = args.firstOrNull() ?: return listOf(
            TerminalLine("Usage: gh <username>", TerminalLine.Type.WARNING)
        )
        return try {
            val json = JSONObject(HttpClient.get("https://api.github.com/users/$user", timeoutMs = 8000))
            listOf(
                TerminalLine("@${json.optString("login")}  ${json.optString("name", "")}", TerminalLine.Type.SUCCESS),
                TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM),
                TerminalLine("Bio:        ${json.optString("bio", "—")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Empresa:    ${json.optString("company", "—")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Local:      ${json.optString("location", "—")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Repos:      ${json.optInt("public_repos")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Followers:  ${json.optInt("followers")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Following:  ${json.optInt("following")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Criado em:  ${json.optString("created_at").take(10)}", TerminalLine.Type.OUTPUT),
                TerminalLine("URL:        ${json.optString("html_url")}", TerminalLine.Type.INFO)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("gh: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun githubRepo(args: List<String>): List<TerminalLine> {
        val repo = args.firstOrNull() ?: return listOf(
            TerminalLine("Usage: gh-repo <owner/repo>", TerminalLine.Type.WARNING)
        )
        return try {
            val json = JSONObject(HttpClient.get("https://api.github.com/repos/$repo", timeoutMs = 8000))
            listOf(
                TerminalLine("${json.optString("full_name")}", TerminalLine.Type.SUCCESS),
                TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM),
                TerminalLine("Desc:       ${json.optString("description", "—")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Linguagem:  ${json.optString("language", "—")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Stars:      ${json.optInt("stargazers_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Forks:      ${json.optInt("forks_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Issues:     ${json.optInt("open_issues_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Licença:    ${json.optJSONObject("license")?.optString("spdx_id") ?: "—"}", TerminalLine.Type.OUTPUT),
                TerminalLine("URL:        ${json.optString("html_url")}", TerminalLine.Type.INFO)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("gh-repo: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── TLDR ─────────────────────────────────────────────────────────────────

    suspend fun tldr(args: List<String>): List<TerminalLine> {
        val cmd = args.firstOrNull() ?: return listOf(
            TerminalLine("Usage: tldr <comando>", TerminalLine.Type.WARNING)
        )
        val platforms = listOf("common", "linux", "android", "osx")
        for (platform in platforms) {
            try {
                val url = "https://raw.githubusercontent.com/tldr-pages/tldr/main/pages/$platform/$cmd.md"
                val text = HttpClient.get(url, timeoutMs = 6000)
                val lines = mutableListOf<TerminalLine>()
                text.lines().forEach { line ->
                    val type = when {
                        line.startsWith("# ") -> TerminalLine.Type.SUCCESS
                        line.startsWith("> ") -> TerminalLine.Type.INFO
                        line.startsWith("- ") -> TerminalLine.Type.WARNING
                        line.startsWith("`") -> TerminalLine.Type.SYSTEM
                        else -> TerminalLine.Type.OUTPUT
                    }
                    lines.add(TerminalLine(line, type))
                }
                return lines
            } catch (_: Exception) { /* try next platform */ }
        }
        return listOf(TerminalLine("tldr: página não encontrada para '$cmd'", TerminalLine.Type.ERROR))
    }

    // ─── Dictionary ───────────────────────────────────────────────────────────

    suspend fun define(args: List<String>): List<TerminalLine> {
        val word = args.firstOrNull() ?: return listOf(
            TerminalLine("Usage: define <word>  (inglês)", TerminalLine.Type.WARNING)
        )
        return try {
            val resp = HttpClient.get(
                "https://api.dictionaryapi.dev/api/v2/entries/en/${HttpClient.urlEncode(word)}",
                timeoutMs = 8000
            )
            val arr = JSONArray(resp)
            if (arr.length() == 0) return listOf(TerminalLine("Sem definição para '$word'", TerminalLine.Type.WARNING))
            val entry = arr.getJSONObject(0)
            val out = mutableListOf<TerminalLine>()
            out.add(TerminalLine(entry.optString("word"), TerminalLine.Type.SUCCESS))
            val phonetic = entry.optString("phonetic")
            if (phonetic.isNotEmpty()) out.add(TerminalLine(phonetic, TerminalLine.Type.SYSTEM))
            val meanings = entry.optJSONArray("meanings")
            if (meanings != null) {
                for (i in 0 until meanings.length()) {
                    val m = meanings.getJSONObject(i)
                    out.add(TerminalLine("", TerminalLine.Type.OUTPUT))
                    out.add(TerminalLine("[${m.optString("partOfSpeech")}]", TerminalLine.Type.INFO))
                    val defs = m.optJSONArray("definitions") ?: continue
                    for (j in 0 until minOf(3, defs.length())) {
                        val d = defs.getJSONObject(j)
                        out.add(TerminalLine("  ${j + 1}. ${d.optString("definition")}", TerminalLine.Type.OUTPUT))
                        val ex = d.optString("example")
                        if (ex.isNotEmpty()) out.add(TerminalLine("     ex: \"$ex\"", TerminalLine.Type.SYSTEM))
                    }
                }
            }
            out
        } catch (e: Exception) {
            listOf(TerminalLine("define: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── Fun / misc ───────────────────────────────────────────────────────────

    suspend fun joke(): List<TerminalLine> {
        return try {
            val resp = HttpClient.get(
                "https://icanhazdadjoke.com/",
                timeoutMs = 6000,
                headers = mapOf("Accept" to "text/plain")
            )
            listOf(
                TerminalLine("", TerminalLine.Type.OUTPUT),
                TerminalLine("  $resp", TerminalLine.Type.INFO),
                TerminalLine("", TerminalLine.Type.OUTPUT)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("joke: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun catFact(): List<TerminalLine> {
        return try {
            val json = JSONObject(HttpClient.get("https://catfact.ninja/fact", timeoutMs = 6000))
            listOf(TerminalLine("  ${json.optString("fact")}", TerminalLine.Type.INFO))
        } catch (e: Exception) {
            listOf(TerminalLine("catfact: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun uselessFact(): List<TerminalLine> {
        return try {
            val json = JSONObject(
                HttpClient.get("https://uselessfacts.jsph.pl/api/v2/facts/random?language=en", timeoutMs = 6000)
            )
            listOf(TerminalLine("  ${json.optString("text")}", TerminalLine.Type.INFO))
        } catch (e: Exception) {
            listOf(TerminalLine("fact: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── Crypto prices (CoinGecko — free, no key) ────────────────────────────

    suspend fun coin(args: List<String> = emptyList()): List<TerminalLine> {
        val coins = if (args.isNotEmpty()) args.joinToString(",").lowercase()
                    else "bitcoin,ethereum,solana,dogecoin"
        return try {
            val url = "https://api.coingecko.com/api/v3/simple/price" +
                      "?ids=$coins&vs_currencies=usd,eur,brl&include_24hr_change=true"
            val json = JSONObject(HttpClient.get(url, timeoutMs = 10000))

            fun fmt(v: Double) = if (v >= 1000) "%,.0f".format(v) else "%.4f".format(v)
            fun chg(v: Double) = (if (v >= 0) "+" else "") + "%.2f".format(v) + "%"

            val lines = mutableListOf<TerminalLine>()
            lines.add(TerminalLine("Crypto Prices  (CoinGecko)", TerminalLine.Type.INFO))
            lines.add(TerminalLine("─".repeat(46), TerminalLine.Type.SYSTEM))
            for (key in json.keys()) {
                val c = json.optJSONObject(key) ?: continue
                val usd = c.optDouble("usd", 0.0)
                val eur = c.optDouble("eur", 0.0)
                val brl = c.optDouble("brl", 0.0)
                val change = c.optDouble("usd_24h_change", 0.0)
                val t = if (change >= 0) TerminalLine.Type.SUCCESS else TerminalLine.Type.ERROR
                lines.add(TerminalLine(key.uppercase().padEnd(12) + "$ ${fmt(usd).padStart(12)}  ${chg(change)}", t))
                lines.add(TerminalLine("            € ${fmt(eur).padStart(12)}  R\$ ${fmt(brl)}", TerminalLine.Type.OUTPUT))
            }
            lines
        } catch (e: Exception) {
            listOf(TerminalLine("coin: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    // ─── QR Code ──────────────────────────────────────────────────────────────

    fun qrCode(args: List<String>): Pair<List<TerminalLine>, String?> {
        val text = args.joinToString(" ")
        if (text.isEmpty()) return Pair(
            listOf(TerminalLine("Usage: qr <texto>", TerminalLine.Type.WARNING)), null
        )
        val url = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&margin=20&data=${HttpClient.urlEncode(text)}"
        return Pair(
            listOf(
                TerminalLine("QR Code gerado:", TerminalLine.Type.SUCCESS),
                TerminalLine("  Conteúdo: $text", TerminalLine.Type.OUTPUT),
                TerminalLine("  URL:      $url", TerminalLine.Type.INFO),
                TerminalLine("Abrindo no navegador...", TerminalLine.Type.SYSTEM)
            ),
            url
        )
    }

    // ─── Speed test ───────────────────────────────────────────────────────────

    suspend fun speedtest(): List<TerminalLine> {
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("Testando velocidade de download (1 MB)...", TerminalLine.Type.INFO))
        return try {
            val testUrl = "https://speed.cloudflare.com/__down?bytes=1048576"
            val start = System.currentTimeMillis()
            val bytes = withContext(Dispatchers.IO) {
                val conn = (URL(testUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 60_000
                    setRequestProperty("User-Agent", "LayerDroid-Terminal/1.0")
                }
                var count = 0L
                try {
                    conn.inputStream.use { input ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            count += n
                        }
                    }
                } finally { conn.disconnect() }
                count
            }
            val elapsed = (System.currentTimeMillis() - start) / 1000.0
            val mbps = (bytes * 8.0 / 1_000_000.0) / elapsed
            lines.add(TerminalLine("Download:  ${"%.2f".format(mbps)} Mbps", TerminalLine.Type.SUCCESS))
            lines.add(TerminalLine("Recebidos: ${bytes / 1024} KB em ${"%.2f".format(elapsed)}s", TerminalLine.Type.OUTPUT))
            lines
        } catch (e: Exception) {
            lines.add(TerminalLine("speedtest: ${e.message}", TerminalLine.Type.ERROR))
            lines
        }
    }
}
