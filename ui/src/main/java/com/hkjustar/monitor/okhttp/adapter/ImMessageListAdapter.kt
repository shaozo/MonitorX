package com.hkjustar.monitor.okhttp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hkjustar.monitor.okhttp.databinding.ItemImMonitorBinding
import com.hkjustar.monitor.okhttp.im.ImMessageRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImMessageListAdapter : RecyclerView.Adapter<ImMessageListAdapter.ViewHolder>() {

    var itemClick: ((ImMessageRecord) -> Unit)? = null
    private var data: List<ImMessageRecord> = emptyList()

    fun setData(list: List<ImMessageRecord>) {
        data = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImMonitorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(data[position])
    }

    inner class ViewHolder(
        private val binding: ItemImMonitorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var item: ImMessageRecord? = null

        init {
            binding.root.setOnClickListener {
                itemClick?.invoke(item ?: return@setOnClickListener)
            }
        }

        fun bind(record: ImMessageRecord) {
            item = record
            binding.tvDirection.text = "IM"
            binding.tvType.text = "TYPE(${record.type})"
            binding.tvSummary.text = record.content.takeIf { it.isNotBlank() } ?: "-"
            binding.tvTranslated.visibility = View.GONE
            binding.tvTime.text = DATE_FORMAT.format(Date(record.timestamp))
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
}
