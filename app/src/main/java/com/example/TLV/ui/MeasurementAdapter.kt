package com.example.TLV.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.TLV.databinding.ItemMeasurementBinding
import com.example.TLV.firebase.StudentMeasurement

class MeasurementAdapter : ListAdapter<StudentMeasurement, MeasurementAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemMeasurementBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StudentMeasurement) {
            binding.tvName.text = item.name
            binding.tvLrn.text = "LRN: ${item.lrn}"
            binding.tvTimestamp.text = item.timestamp
            binding.tvHeight.text = "${item.height} cm"
            binding.tvWeight.text = "${item.weight} kg"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMeasurementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<StudentMeasurement>() {
        override fun areItemsTheSame(oldItem: StudentMeasurement, newItem: StudentMeasurement): Boolean =
            oldItem.lrn == newItem.lrn && oldItem.timestamp == newItem.timestamp

        override fun areContentsTheSame(oldItem: StudentMeasurement, newItem: StudentMeasurement): Boolean =
            oldItem == newItem
    }
}