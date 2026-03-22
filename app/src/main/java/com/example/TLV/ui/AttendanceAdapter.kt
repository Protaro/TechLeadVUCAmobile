package com.example.TLV.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.TLV.databinding.ItemAttendanceBinding
import com.example.TLV.firebase.Student

class AttendanceAdapter : ListAdapter<Student, AttendanceAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemAttendanceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(student: Student) {
            binding.tvName.text = student.name
            binding.tvLrn.text = "LRN: ${student.lrn}"
            binding.tvTimestamp.text = student.timestamp
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Student>() {
        override fun areItemsTheSame(oldItem: Student, newItem: Student): Boolean =
            oldItem.lrn == newItem.lrn && oldItem.timestamp == newItem.timestamp

        override fun areContentsTheSame(oldItem: Student, newItem: Student): Boolean =
            oldItem == newItem
    }
}