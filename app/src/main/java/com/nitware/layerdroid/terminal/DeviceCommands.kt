package com.nitware.layerdroid.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

object DeviceCommands {

    fun battery(context: Context): List<TerminalLine> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        val charging = bm.isCharging
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "carregando"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "descarregando"
            BatteryManager.BATTERY_STATUS_FULL -> "cheia"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "não carregando"
            else -> "desconhecido"
        }
        val bar = buildBar(level)
        return listOf(
            TerminalLine("Bateria", TerminalLine.Type.SUCCESS),
            TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM),
            TerminalLine("  Nível:    $level%  $bar", TerminalLine.Type.OUTPUT),
            TerminalLine("  Status:   $statusStr ${if (charging) "⚡" else ""}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Corrente: ${current}mA", TerminalLine.Type.OUTPUT)
        )
    }

    fun clipboardGet(context: Context): List<TerminalLine> {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (text.isNullOrEmpty()) listOf(TerminalLine("(clipboard vazio)", TerminalLine.Type.WARNING))
            else listOf(TerminalLine(text, TerminalLine.Type.OUTPUT))
        } catch (e: Exception) {
            listOf(TerminalLine("clip: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    fun clipboardSet(context: Context, args: List<String>): List<TerminalLine> {
        val text = args.joinToString(" ")
        if (text.isEmpty()) return listOf(TerminalLine("Usage: copy <texto>", TerminalLine.Type.WARNING))
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("LayerDroid", text))
            listOf(TerminalLine("✓ Copiado ${text.length} caractere(s)", TerminalLine.Type.SUCCESS))
        } catch (e: Exception) {
            listOf(TerminalLine("copy: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    @Suppress("DEPRECATION")
    fun vibrate(context: Context, args: List<String>): List<TerminalLine> {
        val durMs = args.firstOrNull()?.toLongOrNull() ?: 200L
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(durMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(durMs)
            }
            listOf(TerminalLine("✓ Vibrou por ${durMs}ms", TerminalLine.Type.SUCCESS))
        } catch (e: Exception) {
            listOf(TerminalLine("vibrate: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    fun notify(context: Context, args: List<String>): List<TerminalLine> {
        if (args.isEmpty()) return listOf(TerminalLine("Usage: notify <título> [mensagem...]", TerminalLine.Type.WARNING))
        val title = args[0]
        val msg = args.drop(1).joinToString(" ").ifEmpty { "(sem mensagem)" }
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "layerdroid_notify"
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(channelId, "LayerDroid", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(channel)
            }
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(msg)
                .setAutoCancel(true)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notif)
            listOf(TerminalLine("✓ Notificação enviada: $title", TerminalLine.Type.SUCCESS))
        } catch (e: SecurityException) {
            listOf(TerminalLine("notify: permissão POST_NOTIFICATIONS negada (Android 13+).", TerminalLine.Type.ERROR))
        } catch (e: Exception) {
            listOf(TerminalLine("notify: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    fun share(context: Context, args: List<String>): Pair<List<TerminalLine>, Intent?> {
        val text = args.joinToString(" ")
        if (text.isEmpty()) return Pair(listOf(TerminalLine("Usage: share <texto>", TerminalLine.Type.WARNING)), null)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Compartilhar via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Pair(
            listOf(TerminalLine("✓ Abrindo diálogo de compartilhamento...", TerminalLine.Type.SUCCESS)),
            chooser
        )
    }

    fun torch(context: Context, args: List<String>): List<TerminalLine> {
        val on = when (args.firstOrNull()?.lowercase()) {
            "on", "ligar", "1", "true" -> true
            "off", "desligar", "0", "false" -> false
            else -> return listOf(TerminalLine("Usage: torch on|off", TerminalLine.Type.WARNING))
        }
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return listOf(TerminalLine("torch: dispositivo sem lanterna", TerminalLine.Type.ERROR))
            cm.setTorchMode(id, on)
            listOf(TerminalLine("✓ Lanterna ${if (on) "ligada" else "desligada"}", TerminalLine.Type.SUCCESS))
        } catch (e: SecurityException) {
            listOf(TerminalLine("torch: permissão de câmera negada.", TerminalLine.Type.ERROR))
        } catch (e: Exception) {
            listOf(TerminalLine("torch: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    @Volatile private var tts: TextToSpeech? = null

    fun ttsSpeak(context: Context, args: List<String>): List<TerminalLine> {
        val text = args.joinToString(" ")
        if (text.isEmpty()) return listOf(TerminalLine("Usage: tts <texto>", TerminalLine.Type.WARNING))
        return try {
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale("pt", "BR")
                    }
                }
                Thread.sleep(700)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lyd-tts")
            listOf(TerminalLine("🔊 \"$text\"", TerminalLine.Type.SUCCESS))
        } catch (e: Exception) {
            listOf(TerminalLine("tts: ${e.message}", TerminalLine.Type.ERROR))
        }
    }

    fun volumeInfo(context: Context): List<TerminalLine> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun row(label: String, stream: Int): TerminalLine {
            val cur = am.getStreamVolume(stream)
            val max = am.getStreamMaxVolume(stream)
            val pct = if (max > 0) cur * 100 / max else 0
            return TerminalLine("  %-12s %2d/%2d  %s %d%%".format(label, cur, max, buildBar(pct), pct), TerminalLine.Type.OUTPUT)
        }
        return listOf(
            TerminalLine("Volume", TerminalLine.Type.SUCCESS),
            TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM),
            row("Mídia",    AudioManager.STREAM_MUSIC),
            row("Toque",    AudioManager.STREAM_RING),
            row("Notif.",   AudioManager.STREAM_NOTIFICATION),
            row("Alarme",   AudioManager.STREAM_ALARM),
            row("Chamada",  AudioManager.STREAM_VOICE_CALL),
            row("Sistema",  AudioManager.STREAM_SYSTEM)
        )
    }

    @Suppress("DEPRECATION")
    fun wifiInfo(context: Context): List<TerminalLine> {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ipInt = info.ipAddress
            val ip = "${ipInt and 0xff}.${ipInt shr 8 and 0xff}.${ipInt shr 16 and 0xff}.${ipInt shr 24 and 0xff}"
            listOf(
                TerminalLine("Wi-Fi", TerminalLine.Type.SUCCESS),
                TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM),
                TerminalLine("  SSID:      ${info.ssid}", TerminalLine.Type.OUTPUT),
                TerminalLine("  BSSID:     ${info.bssid}", TerminalLine.Type.OUTPUT),
                TerminalLine("  IP local:  $ip", TerminalLine.Type.OUTPUT),
                TerminalLine("  Link:      ${info.linkSpeed} Mbps", TerminalLine.Type.OUTPUT),
                TerminalLine("  RSSI:      ${info.rssi} dBm", TerminalLine.Type.OUTPUT)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("wifi: ${e.message} (geralmente exige ACCESS_FINE_LOCATION)", TerminalLine.Type.ERROR))
        }
    }

    fun deviceInfo(context: Context): List<TerminalLine> {
        return listOf(
            TerminalLine("Dispositivo", TerminalLine.Type.SUCCESS),
            TerminalLine("─".repeat(40), TerminalLine.Type.SYSTEM),
            TerminalLine("  Marca:       ${Build.BRAND}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Modelo:      ${Build.MODEL}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Fabricante:  ${Build.MANUFACTURER}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Produto:     ${Build.PRODUCT}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Placa:       ${Build.BOARD}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Hardware:    ${Build.HARDWARE}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", TerminalLine.Type.OUTPUT),
            TerminalLine("  ABIs:        ${Build.SUPPORTED_ABIS.joinToString(", ")}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Bootloader:  ${Build.BOOTLOADER}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Build ID:    ${Build.DISPLAY}", TerminalLine.Type.OUTPUT),
            TerminalLine("  Fingerprint: ${Build.FINGERPRINT}", TerminalLine.Type.OUTPUT)
        )
    }

    private fun buildBar(pct: Int, width: Int = 20): String {
        val filled = (pct.coerceIn(0, 100) * width / 100).coerceAtMost(width)
        return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]"
    }
}
