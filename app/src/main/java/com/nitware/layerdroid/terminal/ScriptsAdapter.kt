package com.nitware.layerdroid.terminal

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ScriptsAdapter(
    private val context: Context,
    private var scripts: List<File>,
    private val onEdit: (File) -> Unit,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<ScriptsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = scripts[position]
        holder.tvName.text = file.nameWithoutExtension
        holder.tvSize.text = formatSize(file.length())
        holder.btnEdit.setOnClickListener { onEdit(file) }
        holder.btnDelete.setOnClickListener { onDelete(file) }
    }

    override fun getItemCount() = scripts.size

    fun update(newList: List<File>) {
        scripts = newList
        notifyDataSetChanged()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        else -> "${bytes / 1024} KB"
    }
}
