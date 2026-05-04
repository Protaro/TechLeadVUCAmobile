package com.example.TLV.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.TLV.databinding.FragmentMeasurementTableBinding
import com.example.TLV.firebase.FirebaseHelper
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeasurementTableFragment : Fragment() {

    private var _binding: FragmentMeasurementTableBinding? = null
    private val binding get() = _binding!!

    private val firebaseHelper = FirebaseHelper()
    private lateinit var adapter: MeasurementAdapter

    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeasurementTableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MeasurementAdapter()
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

        listenerRegistration = firebaseHelper.listenToMeasurements(today) { list ->
            if (_binding == null) return@listenToMeasurements

            adapter.submitList(list)

            binding.tvEmpty.visibility =
                if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility =
                if (list.isEmpty()) View.GONE else View.VISIBLE
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