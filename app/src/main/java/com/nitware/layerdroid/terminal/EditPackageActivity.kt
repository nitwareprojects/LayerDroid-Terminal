package com.nitware.layerdroid.terminal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nitware.layerdroid.terminal.databinding.ActivityEditPackageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditPackageActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_EDIT_NAME = "edit_name"

        private val SYMBOL_KEYS = listOf(
            "TAB" to "\t", "#" to "#", "$" to "$", "{" to "{", "}" to "}",
            "(" to "(", ")" to ")", "[" to "[", "]" to "]",
            "|" to "|", "&" to "&", ";" to ";", ">" to ">", "<" to "<",
            "\"" to "\"", "'" to "'", "`" to "`", "\\" to "\\",
            "=" to "=", "!" to "!", "-" to "-", "/" to "/", "~" to "~", "*" to "*"
        )

        private val TEMPLATES = linkedMapOf(
            "Basic script" to "#!/system/bin/sh\n# Script name\n# Description\n\necho \"Hello from LayerDroid!\"\n",
            "Network tool" to "#!/system/bin/sh\n# Network tool\n\nHOST=\"\${1:-google.com}\"\necho \"Checking \$HOST...\"\nwget -q --spider --timeout=5 \"https://\$HOST\" 2>/dev/null \\\n  && echo \"[OK] \$HOST is reachable\" \\\n  || echo \"[FAIL] \$HOST is unreachable\"\n",
            "System info" to "#!/system/bin/sh\n# System info snapshot\n\necho \"--- Device ---\"\necho \"Model:   \$(getprop ro.product.model 2>/dev/null)\"\necho \"Android: \$(getprop ro.build.version.release 2>/dev/null)\"\necho \"--- Memory ---\"\ngrep 'MemTotal\\|MemAvailable' /proc/meminfo 2>/dev/null\necho \"--- Storage ---\"\ndf -h /sdcard 2>/dev/null | tail -1\n",
            "Loop / monitor" to "#!/system/bin/sh\n# Loop monitor (Ctrl+C to stop)\n\nINTERVAL=\${1:-2}\necho \"Monitoring every \${INTERVAL}s — press Ctrl+C to stop\"\nwhile true; do\n  echo \"--- \$(date) ---\"\n  # Add your command here\n  sleep \"\$INTERVAL\"\ndone\n"
        )

        fun newIntent(context: Context, editName: String? = null): Intent =
            Intent(context, EditPackageActivity::class.java).apply {
                if (editName != null) putExtra(EXTRA_EDIT_NAME, editName)
            }
    }

    private lateinit var binding: ActivityEditPackageBinding
    private lateinit var pkgManager: PkgManager
    private var editName: String? = null
    private var originalContent: String = ""
    private var isDirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPackageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pkgManager = PkgManager(this)
        editName = intent.getStringExtra(EXTRA_EDIT_NAME)

        setupToolbar()
        setupSymbolBar()
        setupCodeEditor()
        loadData()
        binding.btnSave.setOnClickListener { savePackage() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { attemptExit() }
        binding.toolbar.title = if (editName != null) "Edit: $editName" else "New Package"
        binding.toolbar.subtitle = if (editName != null) "pkg package" else "choose a template or write from scratch"
        binding.toolbar.setTitleTextColor(Color.parseColor("#56D364"))
    }

    private fun setupSymbolBar() {
        SYMBOL_KEYS.forEach { (label, value) ->
            val btn = Button(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#C9D1D9"))
                isAllCaps = false
                stateListAnimator = null
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(Color.parseColor("#21262D"))
                }
                val v = dpToPx(3); val h = dpToPx(8)
                setPadding(h, v, h, v)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(4)
                    topMargin = dpToPx(4)
                    bottomMargin = dpToPx(4)
                }
                setOnClickListener { insertAtCursor(value) }
            }
            binding.symbolContainer.addView(btn)
        }
    }

    private fun setupCodeEditor() {
        binding.etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) { updateStatus() }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isDirty = binding.etCode.text.toString() != originalContent
                updateTitle()
            }
        })
    }

    private fun loadData() {
        if (editName != null) {
            binding.etName.setText(editName)
            binding.etName.isEnabled = false
            binding.etName.setTextColor(Color.parseColor("#8B949E"))

            val meta = pkgManager.getScriptMeta(editName!!)
            binding.etDescription.setText(meta?.optString("description", "") ?: "")
            binding.etVersion.setText(meta?.optString("version", "1.0") ?: "1.0")
            binding.etAuthor.setText(meta?.optString("author", "") ?: "")

            val file = pkgManager.scriptFile(editName!!)
            val content = if (file.exists()) file.readText() else "#!/system/bin/sh\n"
            binding.etCode.setText(content)
            originalContent = content
        } else {
            binding.etVersion.setText("1.0")
            showTemplatePicker()
        }
        updateStatus()
    }

    private fun showTemplatePicker() {
        val names = TEMPLATES.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose a template")
            .setItems(names) { _, idx ->
                val content = TEMPLATES.values.toList()[idx]
                binding.etCode.setText(content)
                originalContent = content
                isDirty = false
                updateTitle()
                updateStatus()
            }
            .setNegativeButton("Blank") { _, _ ->
                val content = "#!/system/bin/sh\n"
                binding.etCode.setText(content)
                originalContent = content
            }
            .setCancelable(false)
            .show()
    }

    private fun updateTitle() {
        val dirty = if (isDirty) " •" else ""
        binding.toolbar.title = if (editName != null) "Edit: $editName$dirty" else "New Package$dirty"
    }

    private fun updateStatus() {
        val text = binding.etCode.text.toString()
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        val chars = text.length
        binding.tvLineCount.text = "$lines lines  $chars chars"

        val name = (editName ?: binding.etName.text?.toString() ?: "").trim()
        binding.tvStatus.text = if (name.isEmpty()) "no name set" else "pkg run $name"
    }

    private fun insertAtCursor(value: String) {
        val et = binding.etCode
        val start = et.selectionStart.coerceAtLeast(0)
        et.text?.insert(start, value)
    }

    private fun savePackage() {
        val name = (editName ?: binding.etName.text.toString().trim())
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
        val description = binding.etDescription.text.toString().trim()
        val version = binding.etVersion.text.toString().trim().ifEmpty { "1.0" }
        val author = binding.etAuthor.text.toString().trim()
        val content = binding.etCode.text.toString()

        if (name.isEmpty()) {
            binding.etName.error = "Required"
            binding.etName.requestFocus()
            return
        }
        if (content.isBlank()) {
            Toast.makeText(this, "Script content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (editName == null && pkgManager.isInstalled(name)) {
            AlertDialog.Builder(this)
                .setTitle("Package already exists")
                .setMessage("Overwrite \"$name\"?")
                .setPositiveButton("Overwrite") { _, _ -> doSave(name, description, version, author, content) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        doSave(name, description, version, author, content)
    }

    private fun doSave(name: String, description: String, version: String, author: String, content: String) {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Saving..."

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pkgManager.upsertPackage(name, description, version, author, content)
                }
                Toast.makeText(this@EditPackageActivity, "Saved: $name  •  pkg run $name", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Save Package"
                Toast.makeText(this@EditPackageActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun attemptExit() {
        if (!isDirty) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Discard changes to this package?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() = attemptExit()

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
