package com.nitware.layerdroid.terminal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nitware.layerdroid.terminal.databinding.ActivityEditPackageBinding

class EditPackageActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_EDIT_NAME = "edit_name"

        fun newIntent(context: Context, editName: String? = null): Intent =
            Intent(context, EditPackageActivity::class.java).apply {
                if (editName != null) putExtra(EXTRA_EDIT_NAME, editName)
            }
    }

    private lateinit var binding: ActivityEditPackageBinding
    private lateinit var pkgManager: PkgManager
    private var editName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPackageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pkgManager = PkgManager(this)
        editName = intent.getStringExtra(EXTRA_EDIT_NAME)

        setupToolbar()
        loadData()
        binding.btnSave.setOnClickListener { savePackage() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = if (editName != null) "Edit: $editName" else "New Package"
        binding.toolbar.setTitleTextColor(Color.parseColor("#56D364"))
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
            binding.etCode.setText(if (file.exists()) file.readText() else "#!/system/bin/sh\n")
        } else {
            binding.etVersion.setText("1.0")
            binding.etCode.setText("#!/system/bin/sh\n# Write your script here\n\necho \"Hello!\"\n")
        }
    }

    private fun savePackage() {
        val name = (editName ?: binding.etName.text.toString().trim())
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
        val description = binding.etDescription.text.toString().trim()
        val version = binding.etVersion.text.toString().trim().ifEmpty { "1.0" }
        val author = binding.etAuthor.text.toString().trim()
        val content = binding.etCode.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Script name is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (content.isBlank()) {
            Toast.makeText(this, "Script content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        pkgManager.upsertPackage(name, description, version, author, content)
        Toast.makeText(this, "Saved: $name", Toast.LENGTH_SHORT).show()
        finish()
    }
}
