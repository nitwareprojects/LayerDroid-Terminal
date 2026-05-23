package com.nitware.layerdroid.terminal

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
            Toast.makeText(this, "Arquivo não especificado", Toast.LENGTH_SHORT).show()
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
        val name = file?.name ?: "novo"
        val modified = isModified()
        val ro = if (readOnly) " (somente leitura)" else ""
        val mark = if (modified) " *" else ""
        binding.toolbar.title = "GNU nano $mark"
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
            "^G Ajuda" to { showHelp() },
            "^O Salvar" to { saveFile() },
            "^W Buscar" to { showSearch() },
            "^K Cortar" to { cutLine() },
            "^U Colar" to { pasteLine() },
            "^_ Linha" to { gotoLine() },
            "^A Início" to { binding.etEditor.setSelection(0) },
            "^E Fim" to { binding.etEditor.setSelection(binding.etEditor.text.length) },
            "^X Sair" to { attemptExit() }
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
                    Toast.makeText(this, "Sem permissão de leitura", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                if (f.length() > 10 * 1024 * 1024) {
                    Toast.makeText(this, "Arquivo muito grande (>10MB)", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                originalContent = f.readText()
                binding.etEditor.setText(originalContent)
                showStatus("Lido ${countLines(originalContent)} linha(s) de ${f.name}")
            } else {
                binding.etEditor.setText("")
                showStatus("[ Novo arquivo: ${f.name} ]")
            }
            updateTitle()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveFile() {
        if (readOnly) {
            Toast.makeText(this, "Arquivo em modo somente leitura", Toast.LENGTH_SHORT).show()
            return
        }
        val f = file ?: return
        try {
            val content = binding.etEditor.text.toString()
            f.parentFile?.mkdirs()
            f.writeText(content)
            originalContent = content
            updateTitle()
            updateStatus()
            showStatus("Gravadas ${countLines(content)} linha(s) em ${f.name}")
            Toast.makeText(this, "Salvo: ${f.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        showStatus("Linha recortada (${cut.trimEnd().length} chars)")
    }

    private fun pasteLine() {
        val et = binding.etEditor
        val toPaste = clipboard.text
        if (toPaste.isEmpty()) {
            showStatus("Buffer vazio")
            return
        }
        val pos = et.selectionStart.coerceAtLeast(0)
        et.text.insert(pos, toPaste)
        showStatus("Colado")
    }

    private fun showSearch() {
        val input = EditText(this).apply {
            hint = "Buscar..."
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
            setBackgroundColor(Color.parseColor("#0D1117"))
            typeface = Typeface.MONOSPACE
        }
        AlertDialog.Builder(this)
            .setTitle("Buscar")
            .setView(input)
            .setPositiveButton("Buscar") { _, _ ->
                val query = input.text.toString()
                if (query.isEmpty()) return@setPositiveButton
                val text = binding.etEditor.text.toString()
                val start = binding.etEditor.selectionEnd.coerceAtLeast(0)
                var idx = text.indexOf(query, start, ignoreCase = true)
                if (idx == -1) idx = text.indexOf(query, 0, ignoreCase = true)
                if (idx >= 0) {
                    binding.etEditor.setSelection(idx, idx + query.length)
                    binding.etEditor.requestFocus()
                    showStatus("Encontrado na posição $idx")
                } else {
                    showStatus("\"$query\" não encontrado")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun gotoLine() {
        val input = EditText(this).apply {
            hint = "Número da linha"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
        }
        AlertDialog.Builder(this)
            .setTitle("Ir para linha")
            .setView(input)
            .setPositiveButton("Ir") { _, _ ->
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
                showStatus("Linha $current")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showHelp() {
        val help = """
            GNU nano — Editor de texto

            Atalhos disponíveis:
            ^G  Mostrar esta ajuda
            ^O  Salvar o arquivo
            ^W  Buscar texto
            ^K  Cortar linha atual
            ^U  Colar linha cortada
            ^_  Ir para linha específica
            ^A  Ir para início do arquivo
            ^E  Ir para fim do arquivo
            ^X  Sair do nano

            Edite o texto livremente.
            Modificações são marcadas com * no topo.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Ajuda")
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
        binding.tvCursor.text = "Lin $lineNum, Col $colNum"
    }

    private fun isModified(): Boolean = binding.etEditor.text.toString() != originalContent

    private fun countLines(s: String): Int = if (s.isEmpty()) 0 else s.count { it == '\n' } + 1

    private fun attemptExit() {
        if (!isModified() || readOnly) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Arquivo modificado")
            .setMessage("Salvar mudanças em \"${file?.name}\"?")
            .setPositiveButton("Salvar") { _, _ -> saveFile(); finish() }
            .setNegativeButton("Descartar") { _, _ -> finish() }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        attemptExit()
    }

    // Static-ish clipboard for ^K / ^U within Nano session
    private object clipboard {
        var text: String = ""
    }
}
