package com.nitware.layerdroid.terminal

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nitware.layerdroid.terminal.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setTitleTextColor(Color.parseColor("#56D364"))
        binding.toolbar.navigationIcon?.setTint(Color.parseColor("#56D364"))

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        binding.tvGithub.setOnClickListener {
            openUrl("https://github.com/nitwareprojects/LayerDroid-Terminal")
        }
        binding.tvReportBug.setOnClickListener {
            openUrl("https://github.com/nitwareprojects/LayerDroid-Terminal/issues")
        }
        binding.tvReleases.setOnClickListener {
            openUrl("https://github.com/nitwareprojects/LayerDroid-Terminal/releases")
        }
        binding.tvTerms.setOnClickListener {
            showTerms()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { }
    }

    private fun showTerms() {
        AlertDialog.Builder(this)
            .setTitle("Terms of Use — LayerDroid v${BuildConfig.VERSION_NAME}")
            .setMessage(
                "1. Shell Command Execution\n" +
                "LayerDroid executes shell commands on your device via /system/bin/sh. " +
                "Commands run within the app's sandboxed permissions — no root access is " +
                "granted or required. You are solely responsible for the commands you run.\n\n" +
                "2. Network Access\n" +
                "Commands such as weather, ipcheck, pkg update and netcheck connect to " +
                "external servers. You are responsible for any data charges. No network " +
                "requests are made without your explicit action.\n\n" +
                "3. File System Access\n" +
                "The app reads and writes files in its private storage. Saving to /sdcard " +
                "on Android 11+ requires granting the \"All Files Access\" permission.\n\n" +
                "4. Scripts & Packages\n" +
                "Scripts installed via pkg are community-contributed shell scripts. Always " +
                "review a script before running it. The developers are not responsible for " +
                "third-party script content or behavior.\n\n" +
                "5. No Warranty\n" +
                "This software is provided \"as is\", without warranty of any kind. The " +
                "developers are not liable for data loss, device damage, or any other harm.\n\n" +
                "6. Privacy\n" +
                "LayerDroid does not collect, store, or transmit personal data. " +
                "Network requests are made only when you run network commands.\n\n" +
                "Source: github.com/nitwareprojects/LayerDroid-Terminal"
            )
            .setPositiveButton("Close", null)
            .show()
    }
}
