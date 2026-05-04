package com.example.TLV.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.TLV.databinding.FragmentAttendanceTableBinding
import com.example.TLV.firebase.FirebaseHelper
import com.google.firebase.firestore.ListenerRegistration

class AttendanceTableFragment : Fragment() {

    private var _binding: FragmentAttendanceTableBinding? = null
    private val binding get() = _binding!!

    private val firebaseHelper = FirebaseHelper()
    private lateinit var adapter: AttendanceAdapter

    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceTableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AttendanceAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        startListening()
    }

    fun refresh() {
        startListening()
    }

    private fun startListening() {
        listenerRegistration?.remove()

        val today = firebaseHelper.getCurrentDate()

        listenerRegistration = firebaseHelper.listenToAttendance(today) { list ->
            if (_binding == null) return@listenToAttendance
            adapter.submitList(list)
            binding.tvEmpty.visibility    = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (list.isEmpty()) View.GONE  else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()

        startListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
        _binding = null
    }
}