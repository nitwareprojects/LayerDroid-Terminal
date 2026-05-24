package com.nitware.layerdroid.terminal

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nitware.layerdroid.terminal.databinding.ActivityNanoBinding
import java.io.File

class NanoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_READ_ONLY = "read_only"

        fun newIntent(context: Context, path: String, readOnly: Boolean = false): Intent {
            return Intent(context, NanoActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, path)
                putExtra(EXTRA_READ_ONLY, readOnly)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private lateinit var binding: ActivityNanoBinding
    private var file: File? = null
    private var originalContent: String = ""
    private var readOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNanoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)
        file = File(path)

        setupToolbar()
        setupEditor()
        setupBottomBar()
        loadFile()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setBackgroundColor(Color.parseColor("#0D1A0D"))
            setTitleTextColor(Color.parseColor("#56D364"))
            setSubtitleTextColor(Color.parseColor("#8B949E"))
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationOnClickListener { attemptExit() }
        }
        updateTitle()
    }

    private fun updateTitle() {
        val name = file?.name ?: "new"
        val modified = isModified()
        val ro = if (readOnly) " (read-only)" else ""
        val mark = if (modified) " *" else ""
        binding.toolbar.title = "nano $mark"
        binding.toolbar.subtitle = "$name$ro"
    }

    private fun setupEditor() {
        binding.etEditor.apply {
            setTextColor(Color.parseColor("#E6EDF3"))
            setBackgroundColor(Color.parseColor("#0D1117"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            isEnabled = !readOnly
            isFocusable = !readOnly
            isFocusableInTouchMode = !readOnly
            setHorizontallyScrolling(true)
            setHorizontalScrollBarEnabled(true)
            setVerticalScrollBarEnabled(true)
            isVerticalScrollBarEnabled = true

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateTitle()
                    updateStatus()
                }
            })
        }
    }

    private fun setupBottomBar() {
        val shortcuts: List<Pair<String, () -> Unit>> = listOf(
            "^G Help"  to { showHelp() },
            "^O Save"  to { saveFile() },
            "^W Find"  to { showSearch() },
            "^K Cut"   to { cutLine() },
            "^U Paste" to { pasteLine() },
            "^_ Line"  to { gotoLine() },
            "^A Start" to { binding.etEditor.setSelection(0) },
            "^E End"   to { binding.etEditor.setSelection(binding.etEditor.text.length) },
            "^X Exit"  to { attemptExit() }
        )

        shortcuts.forEach { (label, action) ->
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#C9D1D9"))
                setBackgroundColor(Color.parseColor("#21262D"))
                val pad = (8 * resources.displayMetrics.density).toInt()
                val hPad = (10 * resources.displayMetrics.density).toInt()
                setPadding(hPad, pad, hPad, pad)
                isAllCaps = false
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (4 * resources.displayMetrics.density).toInt() }
                setOnClickListener { action() }
            }
            binding.bottomBar.addView(btn)
        }
    }

    private fun loadFile() {
        val f = file ?: return
        try {
            if (f.exists()) {
                if (!f.canRead()) {
                    Toast.makeText(this, "No read permission", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                if (f.length() > 10 * 1024 * 1024) {
                    Toast.makeText(this, "File too large (>10MB)", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                originalContent = f.readText()
                binding.etEditor.setText(originalContent)
                showStatus("Read ${countLines(originalContent)} line(s) from ${f.name}")
            } else {
                binding.etEditor.setText("")
                showStatus("[ New file: ${f.name} ]")
            }
            updateTitle()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveFile() {
        if (readOnly) {
            Toast.makeText(this, "File is read-only", Toast.LENGTH_SHORT).show()
            return
        }
        val f = file ?: return

        // On Android 11+ external storage requires MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            f.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath) &&
            !Environment.isExternalStorageManager()) {
            AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage(
                    "To save files on /sdcard, LayerDroid needs the " +
                    "'All Files Access' permission.\n\n" +
                    "Go to Settings → Apps → LayerDroid → Permissions → Files and Media → " +
                    "Allow management of all files."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    pendingSaveAfterPermission = true
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .setNegativeButton("Save Internally") { _, _ -> saveToInternalFallback() }
                .show()
            return
        }

        writeFile(f)
    }

    private fun writeFile(f: File) {
        try {
            val content = binding.etEditor.text.toString()
            f.parentFile?.mkdirs()
            f.writeText(content)
            originalContent = content
            updateTitle()
            updateStatus()
            showStatus("Wrote ${countLines(content)} line(s) to ${f.name}")
            Toast.makeText(this, "Saved: ${f.absolutePath}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showStatus("Save error: ${e.message}")
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToInternalFallback() {
        val f = file ?: return
        val internal = File(filesDir, f.name)
        file = internal
        writeFile(internal)
        showStatus("Saved internally: ${internal.absolutePath}")
    }

    private fun cutLine() {
        val et = binding.etEditor
        val text = et.text.toString()
        val pos = et.selectionStart.coerceAtLeast(0)
        val lineStart = text.lastIndexOf('\n', pos - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', pos).let { if (it < 0) text.length else it + 1 }
        val cut = text.substring(lineStart, lineEnd)
        clipboard.text = cut
        val newText = text.removeRange(lineStart, lineEnd)
        et.setText(newText)
        et.setSelection(lineStart.coerceAtMost(newText.length))
        showStatus("Line cut (${cut.trimEnd().length} chars)")
    }

    private fun pasteLine() {
        val et = binding.etEditor
        val toPaste = clipboard.text
        if (toPaste.isEmpty()) {
            showStatus("Buffer empty")
            return
        }
        val pos = et.selectionStart.coerceAtLeast(0)
        et.text.insert(pos, toPaste)
        showStatus("Pasted")
    }

    private fun showSearch() {
        val input = EditText(this).apply {
            hint = "Search..."
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
            setBackgroundColor(Color.parseColor("#0D1117"))
            typeface = Typeface.MONOSPACE
        }
        AlertDialog.Builder(this)
            .setTitle("Find")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString()
                if (query.isEmpty()) return@setPositiveButton
                val text = binding.etEditor.text.toString()
                val start = binding.etEditor.selectionEnd.coerceAtLeast(0)
                var idx = text.indexOf(query, start, ignoreCase = true)
                if (idx == -1) idx = text.indexOf(query, 0, ignoreCase = true)
                if (idx >= 0) {
                    binding.etEditor.setSelection(idx, idx + query.length)
                    binding.etEditor.requestFocus()
                    showStatus("Found at position $idx")
                } else {
                    showStatus("\"$query\" not found")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun gotoLine() {
        val input = EditText(this).apply {
            hint = "Line number"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
        }
        AlertDialog.Builder(this)
            .setTitle("Go to line")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val lineNum = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val text = binding.etEditor.text.toString()
                var current = 1
                var pos = 0
                while (current < lineNum && pos < text.length) {
                    val next = text.indexOf('\n', pos)
                    if (next == -1) break
                    pos = next + 1
                    current++
                }
                binding.etEditor.setSelection(pos.coerceAtMost(text.length))
                binding.etEditor.requestFocus()
                showStatus("Line $current")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelp() {
        val help = """
            nano — Text Editor

            Shortcuts:
            ^G  Show this help
            ^O  Save file
            ^W  Find text
            ^K  Cut current line
            ^U  Paste cut line
            ^_  Go to line number
            ^A  Go to start of file
            ^E  Go to end of file
            ^X  Exit nano

            Edit text freely.
            Unsaved changes are marked with * in the title.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Help")
            .setMessage(help)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun updateStatus() {
        val text = binding.etEditor.text.toString()
        val pos = binding.etEditor.selectionStart.coerceAtLeast(0)
        val lineNum = text.substring(0, pos.coerceAtMost(text.length)).count { it == '\n' } + 1
        val colNum = pos - (text.lastIndexOf('\n', pos - 1).let { if (it < 0) -1 else it })
        binding.tvCursor.text = "Ln $lineNum, Col $colNum"
    }

    private fun isModified(): Boolean = binding.etEditor.text.toString() != originalContent

    private fun countLines(s: String): Int = if (s.isEmpty()) 0 else s.count { it == '\n' } + 1

    private fun attemptExit() {
        if (!isModified() || readOnly) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Save changes to \"${file?.name}\"?")
            .setPositiveButton("Save") { _, _ -> saveFile(); finish() }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private var pendingSaveAfterPermission = false

    override fun onResume() {
        super.onResume()
        if (pendingSaveAfterPermission) {
            pendingSaveAfterPermission = false
            saveFile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        attemptExit()
    }

    private object clipboard {
        var text: String = ""
    }
}
