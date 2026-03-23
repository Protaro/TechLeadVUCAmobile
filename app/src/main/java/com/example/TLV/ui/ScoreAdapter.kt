package com.example.TLV.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.TLV.databinding.ItemScoreBinding
import com.example.TLV.firebase.StudentRatings

class ScoreAdapter : ListAdapter<StudentRatings, ScoreAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemScoreBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StudentRatings) {
            binding.tvName.text = "Student (LRN: ${item.lrn})"  // name not stored here - adjust if you fetch it
            binding.tvLrn.text = "LRN: ${item.lrn}"
            binding.tvLiteracy.text = "${item.literacy} / 10"
            binding.tvNumeracy.text = "${item.numeracy} / 10"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<StudentRatings>() {
        override fun areItemsTheSame(oldItem: StudentRatings, newItem: StudentRatings): Boolean =
            oldItem.lrn == newItem.lrn && oldItem.timestamp == newItem.timestamp

        override fun areContentsTheSame(oldItem: StudentRatings, newItem: StudentRatings): Boolean =
            oldItem == newItem
    }
}