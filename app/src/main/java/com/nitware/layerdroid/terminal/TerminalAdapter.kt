package com.nitware.layerdroid.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitware.layerdroid.terminal.databinding.ItemTerminalLineBinding

class TerminalAdapter : ListAdapter<TerminalLine, TerminalAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TerminalLine>() {
            override fun areItemsTheSame(a: TerminalLine, b: TerminalLine) = a === b
            override fun areContentsTheSame(a: TerminalLine, b: TerminalLine) = a == b
        }

        private val COLOR_OUTPUT    = Color.parseColor("#C9D1D9")
        private val COLOR_ERROR     = Color.parseColor("#FF7B72")
        private val COLOR_SUCCESS   = Color.parseColor("#56D364")
        private val COLOR_INFO      = Color.parseColor("#58A6FF")
        private val COLOR_WARNING   = Color.parseColor("#E3B341")
        private val COLOR_DIRECTORY = Color.parseColor("#79C0FF")
        private val COLOR_SYSTEM    = Color.parseColor("#8B949E")
        private val COLOR_PROMPT    = Color.parseColor("#56D364")
        private val COLOR_COMMAND   = Color.parseColor("#E6EDF3")
        private val COLOR_SEPARATOR = Color.parseColor("#21262D")
    }

    inner class ViewHolder(val binding: ItemTerminalLineBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTerminalLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = getItem(position)
        holder.binding.tvLine.apply {
            text = line.text
            typeface = Typeface.MONOSPACE
            textSize = 13f

            setTextColor(
                when (line.type) {
                    TerminalLine.Type.PROMPT    -> COLOR_PROMPT
                    TerminalLine.Type.COMMAND   -> COLOR_COMMAND
                    TerminalLine.Type.OUTPUT    -> COLOR_OUTPUT
                    TerminalLine.Type.ERROR     -> COLOR_ERROR
                    TerminalLine.Type.SUCCESS   -> COLOR_SUCCESS
                    TerminalLine.Type.INFO      -> COLOR_INFO
                    TerminalLine.Type.WARNING   -> COLOR_WARNING
                    TerminalLine.Type.DIRECTORY -> COLOR_DIRECTORY
                    TerminalLine.Type.SYSTEM    -> COLOR_SYSTEM
                    TerminalLine.Type.SEPARATOR -> COLOR_SEPARATOR
                }
            )

            setBackgroundColor(Color.TRANSPARENT)

            val boldTypes = setOf(
                TerminalLine.Type.PROMPT,
                TerminalLine.Type.ERROR,
                TerminalLine.Type.SUCCESS
            )
            typeface = if (line.type in boldTypes) {
                Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            } else {
                Typeface.MONOSPACE
            }
        }
    }
}
