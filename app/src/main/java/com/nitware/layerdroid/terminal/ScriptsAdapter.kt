package com.nitware.layerdroid.terminal

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

data class ScriptItem(
    val file: File,
    val description: String,
    val version: String
)

class ScriptsAdapter(
    private val context: Context,
    private var items: List<ScriptItem>,
    private val onEdit: (ScriptItem) -> Unit,
    private val onDelete: (ScriptItem) -> Unit
) : RecyclerView.Adapter<ScriptsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvVersion: TextView = view.findViewById(R.id.tvVersion)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.file.nameWithoutExtension
        holder.tvVersion.text = if (item.version.isNotEmpty()) "v${item.version}" else ""
        holder.tvDescription.text = item.description.ifEmpty { "(no description)" }
        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<ScriptItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
