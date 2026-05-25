package com.nitware.layerdroid.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitware.layerdroid.terminal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TerminalViewModel by viewModels()
    private lateinit var adapter: TerminalAdapter
    private var ctrlActive = false

    companion object {
        // label → action  (| separates visual groups)
        private val SPECIAL_KEYS = listOf(
            "CTRL"  to "CTRL",
            "ESC"   to "ESC",
            "TAB"   to "TAB",
            "↑"     to "UP",
            "↓"     to "DOWN",
            "←"     to "LEFT",
            "→"     to "RIGHT",
            "HOME"  to "HOME",
            "END"   to "END",
            "/"     to "/",
            "-"     to "-",
            "|"     to "|",
            "&"     to "&",
            ">"     to ">",
            "<"     to "<",
            "~"     to "~",
            "."     to ".",
            ":"     to ":",
            ";"     to ";",
            "*"     to "*",
            "!"     to "!",
            "'"     to "'",
            "\""    to "\""
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkTermsOfUse()
        setupToolbar()
        setupRecyclerView()
        setupSpecialKeys()
        setupInput()
        observeViewModel()
    }

    private fun checkTermsOfUse() {
        val prefs = getSharedPreferences("layerdroid", MODE_PRIVATE)
        if (prefs.getBoolean("terms_accepted", false)) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Terms of Use — LayerDroid v${BuildConfig.VERSION_NAME}")
            .setMessage(
                "Please read before continuing.\n\n" +
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
            .setCancelable(false)
            .setPositiveButton("Accept") { _, _ ->
                prefs.edit().putBoolean("terms_accepted", true).apply()
            }
            .setNegativeButton("Decline") { _, _ ->
                finish()
            }
            .show()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            title = "LayerDroid Terminal"
            subtitle = "~"
            setTitleTextColor(Color.parseColor("#56D364"))
            setSubtitleTextColor(Color.parseColor("#8B949E"))
            setBackgroundColor(Color.parseColor("#161B22"))
            inflateMenu(R.menu.terminal_menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_scripts -> { startActivity(android.content.Intent(this@MainActivity, ScriptsActivity::class.java)); true }
                    R.id.action_clear -> { viewModel.executeCommand("clear"); true }
                    R.id.action_copy -> { copySelection(); true }
                    R.id.action_paste -> { pasteClipboard(); true }
                    R.id.action_neofetch -> { viewModel.executeCommand("neofetch"); true }
                    R.id.action_help -> { viewModel.executeCommand("help"); true }
                    R.id.action_about -> { startActivity(android.content.Intent(this@MainActivity, AboutActivity::class.java)); true }
                    else -> false
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = TerminalAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            this.adapter = this@MainActivity.adapter
            setBackgroundColor(Color.parseColor("#0D1117"))
            setHasFixedSize(false)
        }
    }

    private fun setupSpecialKeys() {
        val scrollView = binding.specialKeysScroll
        val container = binding.specialKeysContainer

        SPECIAL_KEYS.forEach { (label, action) ->
            val isModifier = action in listOf("CTRL", "ESC", "TAB")
            val btn = Button(this).apply {
                text = label
                textSize = if (isModifier) 11f else 13f
                setTextColor(if (isModifier) Color.parseColor("#79C0FF") else Color.parseColor("#E6EDF3"))
                isAllCaps = false
                stateListAnimator = null
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(if (isModifier) Color.parseColor("#161B22") else Color.parseColor("#21262D"))
                    setStroke(1, if (isModifier) Color.parseColor("#388BFD") else Color.parseColor("#30363D"))
                }
                val vPad = dpToPx(4)
                val hPad = dpToPx(if (isModifier) 9 else 8)
                setPadding(hPad, vPad, hPad, vPad)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = dpToPx(4)
                    topMargin = dpToPx(4)
                    bottomMargin = dpToPx(4)
                }
                layoutParams = params
                setOnClickListener { handleSpecialKey(action) }
            }
            container.addView(btn)
        }
    }

    private fun handleSpecialKey(action: String) {
        when (action) {
            "ESC" -> {
                binding.etInput.text?.clear()
                ctrlActive = false
                updateModifierState()
            }
            "TAB" -> handleTab()
            "CTRL" -> {
                ctrlActive = !ctrlActive
                updateModifierState()
                if (ctrlActive) Toast.makeText(this, "CTRL — press C, D, L, A or E", Toast.LENGTH_SHORT).show()
            }
            "UP" -> {
                val prev = viewModel.getPreviousCommand()
                if (prev != null) {
                    binding.etInput.setText(prev)
                    binding.etInput.setSelection(prev.length)
                }
            }
            "DOWN" -> {
                val next = viewModel.getNextCommand()
                if (next != null) {
                    binding.etInput.setText(next)
                    binding.etInput.setSelection(next.length)
                }
            }
            "HOME" -> binding.etInput.setSelection(0)
            "END" -> binding.etInput.setSelection(binding.etInput.text?.length ?: 0)
            "LEFT" -> {
                val pos = (binding.etInput.selectionStart - 1).coerceAtLeast(0)
                binding.etInput.setSelection(pos)
            }
            "RIGHT" -> {
                val pos = (binding.etInput.selectionStart + 1).coerceAtMost(binding.etInput.text?.length ?: 0)
                binding.etInput.setSelection(pos)
            }
            else -> {
                if (ctrlActive) {
                    handleCtrlCombo(action)
                    ctrlActive = false
                    updateModifierState()
                } else {
                    val start = binding.etInput.selectionStart.coerceAtLeast(0)
                    binding.etInput.text?.insert(start, action)
                }
            }
        }
    }

    private fun handleCtrlCombo(key: String) {
        when (key.uppercase()) {
            "C" -> {
                binding.etInput.text?.clear()
                viewModel.executeCommand("")
                Toast.makeText(this, "^C", Toast.LENGTH_SHORT).show()
            }
            "D" -> {
                Toast.makeText(this, "^D – EOF", Toast.LENGTH_SHORT).show()
            }
            "L" -> viewModel.executeCommand("clear")
            "A" -> binding.etInput.setSelection(0)
            "E" -> binding.etInput.setSelection(binding.etInput.text?.length ?: 0)
            else -> Toast.makeText(this, "^$key not mapped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleTab() {
        val input = binding.etInput.text?.toString() ?: ""
        val completions = viewModel.getTabCompletions(input)
        when {
            completions.isEmpty() -> { /* beep */ }
            completions.size == 1 -> {
                val parts = input.split(" ")
                val completed = if (parts.size <= 1) completions[0]
                                else parts.dropLast(1).joinToString(" ") + " " + completions[0]
                binding.etInput.setText(completed)
                binding.etInput.setSelection(completed.length)
            }
            else -> {
                val msg = completions.take(10).joinToString("  ")
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateModifierState() {
        val ctrlIdx = SPECIAL_KEYS.indexOfFirst { it.first == "CTRL" }
        fun setActive(idx: Int, active: Boolean) {
            if (idx < 0) return
            (binding.specialKeysContainer.getChildAt(idx) as? Button)?.background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(if (active) Color.parseColor("#1F6FEB") else Color.parseColor("#161B22"))
                    setStroke(1, if (active) Color.parseColor("#58A6FF") else Color.parseColor("#388BFD"))
                }
        }
        setActive(ctrlIdx, ctrlActive)
    }

    private fun setupInput() {
        binding.etInput.apply {
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
            hint = "Type a command..."
            setBackgroundColor(Color.TRANSPARENT)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_SEND
            setSingleLine(true)

            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    submitCommand()
                    true
                } else false
            }
        }

        binding.btnSend.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#238636"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener { submitCommand() }
        }

        binding.root.setOnClickListener {
            binding.etInput.requestFocus()
            showKeyboard()
        }
    }

    private fun submitCommand() {
        val input = binding.etInput.text?.toString()?.trim() ?: return
        binding.etInput.text?.clear()
        viewModel.executeCommand(input)
    }

    private fun observeViewModel() {
        viewModel.lines.observe(this) { lines ->
            adapter.submitList(lines.toList()) {
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }

        viewModel.currentDir.observe(this) { dir ->
            binding.tvPrompt.text = viewModel.getPrompt()
            binding.toolbar.subtitle = dir.replace(
                android.os.Environment.getExternalStorageDirectory().absolutePath, "~"
            ).let { if (it.length > 40) "…" + it.takeLast(38) else it }
        }

        viewModel.isRunning.observe(this) { running ->
            binding.progressBar.visibility = if (running) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !running
            binding.etInput.isEnabled = !running
        }

        viewModel.shouldExit.observe(this) { exit ->
            if (exit) finish()
        }

        viewModel.launchIntent.observe(this) { intent ->
            if (intent != null) {
                startActivity(intent)
                viewModel.consumedLaunchIntent()
            }
        }
    }

    private fun copySelection() {
        val lines = viewModel.lines.value ?: return
        val text = lines.takeLast(50).joinToString("\n") { it.text }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal output", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val start = binding.etInput.selectionStart.coerceAtLeast(0)
        binding.etInput.text?.insert(start, text)
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        if (binding.etInput.text?.isNotEmpty() == true) {
            binding.etInput.text?.clear()
        } else {
            super.onBackPressed()
        }
    }
}
