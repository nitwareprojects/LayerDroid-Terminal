package com.nitware.layerdroid.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitware.layerdroid.terminal.databinding.ActivityScriptsBinding
import java.io.File

class ScriptsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScriptsBinding
    private lateinit var pkgManager: PkgManager
    private lateinit var adapter: ScriptsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScriptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pkgManager = PkgManager(this)
        setupToolbar()
        setupRecyclerView()
        binding.fab.setOnClickListener { showNewScriptDialog() }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = "Scripts"
        binding.toolbar.setTitleTextColor(Color.parseColor("#56D364"))
    }

    private fun setupRecyclerView() {
        adapter = ScriptsAdapter(
            this,
            emptyList(),
            onEdit = { file -> startActivity(NanoActivity.newIntent(this, file.absolutePath)) },
            onDelete = { file -> confirmDelete(file) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScriptsActivity)
            adapter = this@ScriptsActivity.adapter
        }
    }

    private fun refreshList() {
        val scripts = pkgManager.scriptsDir
            .listFiles { _, n -> n.endsWith(".sh") }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()
        adapter.update(scripts)
        binding.tvEmpty.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (scripts.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete script")
            .setMessage("Delete \"${file.nameWithoutExtension}\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (file.delete()) {
                    Toast.makeText(this, "Deleted: ${file.nameWithoutExtension}", Toast.LENGTH_SHORT).show()
                    refreshList()
                } else {
                    Toast.makeText(this, "Could not delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewScriptDialog() {
        val input = EditText(this).apply {
            hint = "my-script"
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#484F58"))
            setBackgroundColor(Color.parseColor("#0D1117"))
            typeface = Typeface.MONOSPACE
        }
        AlertDialog.Builder(this)
            .setTitle("New script")
            .setMessage("Name (letters, numbers, - and _ only):")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                    .replace(Regex("[^a-zA-Z0-9_-]"), "")
                if (name.isEmpty()) {
                    Toast.makeText(this, "Invalid name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val file = pkgManager.scriptFile(name)
                if (file.exists()) {
                    Toast.makeText(this, "'$name' already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                file.parentFile?.mkdirs()
                file.writeText("#!/system/bin/sh\n# $name\n\necho \"Hello from $name!\"\n")
                file.setExecutable(true)
                startActivity(NanoActivity.newIntent(this, file.absolutePath))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
