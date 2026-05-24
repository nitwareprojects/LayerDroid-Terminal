package com.nitware.layerdroid.terminal

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitware.layerdroid.terminal.databinding.ActivityScriptsBinding

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
        binding.fab.setOnClickListener {
            startActivity(EditPackageActivity.newIntent(this))
        }
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
            onEdit = { item ->
                startActivity(EditPackageActivity.newIntent(this, item.file.nameWithoutExtension))
            },
            onDelete = { item -> confirmDelete(item) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScriptsActivity)
            adapter = this@ScriptsActivity.adapter
        }
    }

    private fun refreshList() {
        val files = pkgManager.scriptsDir
            .listFiles { _, n -> n.endsWith(".sh") }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()

        val items = files.map { file ->
            val meta = pkgManager.getScriptMeta(file.nameWithoutExtension)
            ScriptItem(
                file = file,
                description = meta?.optString("description", "") ?: "",
                version = meta?.optString("version", "") ?: ""
            )
        }

        adapter.update(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmDelete(item: ScriptItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete package")
            .setMessage("Delete \"${item.file.nameWithoutExtension}\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (item.file.delete()) {
                    Toast.makeText(this, "Deleted: ${item.file.nameWithoutExtension}", Toast.LENGTH_SHORT).show()
                    refreshList()
                } else {
                    Toast.makeText(this, "Could not delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
