package com.nitware.layerdroid.terminal

import org.json.JSONArray
import org.json.JSONObject

object NetCommands {

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
        val url = "http://ip-api.com/json/${HttpClient.urlEncode(ip)}?fields=status,message,country,regionName,city,zip,lat,lon,timezone,isp,org,as,query"
        return try {
            val json = JSONObject(HttpClient.get(url, timeoutMs = 8000))
            if (json.optString("status") != "success") {
                return listOf(TerminalLine("ipinfo: ${json.optString("message", "erro")}", TerminalLine.Type.ERROR))
            }
            listOf(
                TerminalLine("IP:        ${json.optString("query")}", TerminalLine.Type.SUCCESS),
                TerminalLine("Localização: ${json.optString("city")}, ${json.optString("regionName")}, ${json.optString("country")} (${json.optString("zip")})", TerminalLine.Type.OUTPUT),
                TerminalLine("Coords:    ${json.optDouble("lat")}, ${json.optDouble("lon")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Timezone:  ${json.optString("timezone")}", TerminalLine.Type.OUTPUT),
                TerminalLine("ISP:       ${json.optString("isp")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Org:       ${json.optString("org")}", TerminalLine.Type.OUTPUT),
                TerminalLine("AS:        ${json.optString("as")}", TerminalLine.Type.OUTPUT)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("ipinfo: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

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
                TerminalLine("⭐ Stars:    ${json.optInt("stargazers_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("🍴 Forks:    ${json.optInt("forks_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("👁  Watch:    ${json.optInt("watchers_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("⚠  Issues:   ${json.optInt("open_issues_count")}", TerminalLine.Type.OUTPUT),
                TerminalLine("Licença:    ${json.optJSONObject("license")?.optString("spdx_id") ?: "—"}", TerminalLine.Type.OUTPUT),
                TerminalLine("URL:        ${json.optString("html_url")}", TerminalLine.Type.INFO)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("gh-repo: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

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
            } catch (e: Exception) { /* try next platform */ }
        }
        return listOf(TerminalLine("tldr: página não encontrada para '$cmd'", TerminalLine.Type.ERROR))
    }

    suspend fun define(args: List<String>): List<TerminalLine> {
        val word = args.firstOrNull() ?: return listOf(
            TerminalLine("Usage: define <palavra>  (em inglês)", TerminalLine.Type.WARNING)
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
            out.add(TerminalLine("${entry.optString("word")}", TerminalLine.Type.SUCCESS))
            val phonetic = entry.optString("phonetic")
            if (phonetic.isNotEmpty()) out.add(TerminalLine(phonetic, TerminalLine.Type.SYSTEM))
            val meanings = entry.optJSONArray("meanings")
            if (meanings != null) {
                for (i in 0 until meanings.length()) {
                    val m = meanings.getJSONObject(i)
                    out.add(TerminalLine("", TerminalLine.Type.OUTPUT))
                    out.add(TerminalLine("[${m.optString("partOfSpeech")}]", TerminalLine.Type.INFO))
                    val defs = m.optJSONArray("definitions") ?: continue
                    val limit = minOf(3, defs.length())
                    for (j in 0 until limit) {
                        val d = defs.getJSONObject(j)
                        out.add(TerminalLine("  ${j+1}. ${d.optString("definition")}", TerminalLine.Type.OUTPUT))
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
            listOf(
                TerminalLine("🐱  ${json.optString("fact")}", TerminalLine.Type.INFO)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("catfact: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun uselessFact(): List<TerminalLine> {
        return try {
            val json = JSONObject(HttpClient.get(
                "https://uselessfacts.jsph.pl/api/v2/facts/random?language=en",
                timeoutMs = 6000
            ))
            listOf(TerminalLine("ℹ  ${json.optString("text")}", TerminalLine.Type.INFO))
        } catch (e: Exception) {
            listOf(TerminalLine("fact: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    suspend fun coin(): List<TerminalLine> {
        return try {
            val json = JSONObject(HttpClient.get("https://api.coindesk.com/v1/bpi/currentprice.json", timeoutMs = 8000))
            val bpi = json.getJSONObject("bpi")
            val updated = json.getJSONObject("time").optString("updated")
            listOf(
                TerminalLine("Bitcoin (BTC) — $updated", TerminalLine.Type.INFO),
                TerminalLine("  USD ${bpi.getJSONObject("USD").optString("rate")}", TerminalLine.Type.SUCCESS),
                TerminalLine("  EUR ${bpi.getJSONObject("EUR").optString("rate")}", TerminalLine.Type.SUCCESS),
                TerminalLine("  GBP ${bpi.getJSONObject("GBP").optString("rate")}", TerminalLine.Type.SUCCESS)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("coin: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    fun qrCode(args: List<String>): Pair<List<TerminalLine>, String?> {
        val text = args.joinToString(" ")
        if (text.isEmpty()) return Pair(listOf(TerminalLine("Usage: qr <texto>", TerminalLine.Type.WARNING)), null)
        val url = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&margin=20&data=${HttpClient.urlEncode(text)}"
        val lines = listOf(
            TerminalLine("QR Code gerado:", TerminalLine.Type.SUCCESS),
            TerminalLine("  Conteúdo: $text", TerminalLine.Type.OUTPUT),
            TerminalLine("  URL:      $url", TerminalLine.Type.INFO),
            TerminalLine("Abrindo no navegador...", TerminalLine.Type.SYSTEM)
        )
        return Pair(lines, url)
    }
}
