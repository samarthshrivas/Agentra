package com.agentra.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.agentra.app.R
import com.agentra.app.databinding.ItemLogBinding

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<LogEntry>()

    data class LogEntry(
        val timestamp: Long,
        val message: String,
        val type: LogType
    )

    enum class LogType {
        USER, AGENT, ACTION, ERROR, INFO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount() = logs.size

    fun addLog(message: String, type: LogType) {
        logs.add(LogEntry(System.currentTimeMillis(), message, type))
        notifyItemInserted(logs.size - 1)
    }

    fun clear() {
        logs.clear()
        notifyDataSetChanged()
    }

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry) {
            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            binding.tvTimestamp.text = timeFormat.format(java.util.Date(entry.timestamp))
            binding.tvMessage.text = entry.message

            val colorRes = when (entry.type) {
                LogType.USER -> R.color.log_user
                LogType.AGENT -> R.color.log_agent
                LogType.ACTION -> R.color.log_action
                LogType.ERROR -> R.color.log_error
                LogType.INFO -> R.color.on_surface_variant
            }
            binding.tvMessage.setTextColor(binding.root.context.getColor(colorRes))
        }
    }
}
