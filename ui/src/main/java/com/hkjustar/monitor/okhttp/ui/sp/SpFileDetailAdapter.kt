package com.hkjustar.monitor.okhttp.ui.sp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hkjustar.monitor.okhttp.R
import com.hkjustar.monitor.okhttp.data.SpSubData
import com.hkjustar.monitor.okhttp.databinding.ItemSpFileDetailBinding

/**
 * <pre>
 *      author  : Allen
 *      date    : 2022/8/6
 *      desc    :
 * </pre>
 */
class SpFileDetailAdapter : RecyclerView.Adapter<SpFileDetailAdapter.SpFileDetailViewHolder>() {

    private var list: List<SpSubData> = listOf()

    var onItemClick: ((SpSubData) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpFileDetailViewHolder {
        return SpFileDetailViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_sp_file_detail, parent, false)
        )
    }

    override fun onBindViewHolder(holder: SpFileDetailViewHolder, position: Int) {
        holder.bindData(list[position])
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(list[position])
        }
    }

    override fun getItemCount() = list.size

    fun setData(list: List<SpSubData>) {
        this.list = list
        notifyDataSetChanged()
    }

    inner class SpFileDetailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = ItemSpFileDetailBinding.bind(view)

        fun bindData(data: SpSubData) {
            binding.tvKey.text = data.keyName
            binding.tvValue.text = "${data.keyValue?.value}"
        }
    }
}