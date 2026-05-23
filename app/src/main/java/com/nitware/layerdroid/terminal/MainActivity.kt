package com.nitware.layerdroid.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitware.layerdroid.terminal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TerminalViewModel by viewModels()
    private lateinit var adapter: TerminalAdapter
    private var ctrlActive = false
    private var altActive = false

    companion object {
        private val SPECIAL_KEYS = listOf(
            "ESC" to "ESC",
            "TAB" to "TAB",
            "CTRL" to "CTRL",
            "ALT" to "ALT",
            "/" to "/",
            "-" to "-",
            "|" to "|",
            "&" to "&",
            ">" to ">",
            "<" to "<",
            "~" to "~",
            "'" to "'",
            "\"" to "\"",
            "HOME" to "HOME",
            "END" to "END",
            "↑" to "UP",
            "↓" to "DOWN",
            "←" to "LEFT",
            "→" to "RIGHT"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSpecialKeys()
        setupInput()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            title = "LayerDroid Terminal"
            setTitleTextColor(Color.parseColor("#56D364"))
            setBackgroundColor(Color.parseColor("#0D1A0D"))
            inflateMenu(R.menu.terminal_menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_clear -> { viewModel.executeCommand("clear"); true }
                    R.id.action_copy -> { copySelection(); true }
                    R.id.action_paste -> { pasteClipboard(); true }
                    R.id.action_neofetch -> { viewModel.executeCommand("neofetch"); true }
                    R.id.action_help -> { viewModel.executeCommand("help"); true }
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
            val btn = Button(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#C9D1D9"))
                setBackgroundColor(Color.parseColor("#21262D"))
                val pad = dpToPx(8)
                val hPad = dpToPx(12)
                setPadding(hPad, pad, hPad, pad)
                isAllCaps = false
                stateListAnimator = null

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(4)
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
                altActive = false
                updateModifierState()
            }
            "TAB" -> handleTab()
            "CTRL" -> {
                ctrlActive = !ctrlActive
                altActive = false
                updateModifierState()
                if (ctrlActive) Toast.makeText(this, "CTRL ativo – pressione C, D, L, A ou E", Toast.LENGTH_SHORT).show()
            }
            "ALT" -> {
                altActive = !altActive
                ctrlActive = false
                updateModifierState()
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
            else -> Toast.makeText(this, "^$key não mapeado", Toast.LENGTH_SHORT).show()
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
        val altIdx  = SPECIAL_KEYS.indexOfFirst { it.first == "ALT" }
        fun setActive(idx: Int, active: Boolean) {
            (binding.specialKeysContainer.getChildAt(idx) as? Button)?.apply {
                setBackgroundColor(
                    if (active) Color.parseColor("#1F6FEB") else Color.parseColor("#21262D")
                )
            }
        }
        setActive(ctrlIdx, ctrlActive)
        setActive(altIdx, altActive)
    }

    private fun setupInput() {
        binding.etInput.apply {
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
            hint = "Digite um comando..."
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
            setBackgroundColor(Color.parseColor("#238636"))
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
        }

        viewModel.isRunning.observe(this) { running ->
            binding.progressBar.visibility = if (running) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !running
            binding.etInput.isEnabled = !running
        }

        viewModel.shouldExit.observe(this) { exit ->
            if (exit) finish()
        }
    }

    private fun copySelection() {
        val lines = viewModel.lines.value ?: return
        val text = lines.takeLast(50).joinToString("\n") { it.text }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal output", text))
        Toast.makeText(this, "Copiado para a área de transferência", Toast.LENGTH_SHORT).show()
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
