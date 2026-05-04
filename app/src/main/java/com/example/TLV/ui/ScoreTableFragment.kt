package com.example.TLV.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.TLV.databinding.FragmentScoreTableBinding
import com.example.TLV.firebase.FirebaseHelper
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScoreTableFragment : Fragment() {

    private var _binding: FragmentScoreTableBinding? = null
    private val binding get() = _binding!!

    private val firebaseHelper = FirebaseHelper()
    private lateinit var adapter: ScoreAdapter

    private var listeners: List<ListenerRegistration> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScoreTableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ScoreAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        startListening()
    }

    fun refresh() {
        startListening()
    }

    private fun startListening() {
        listeners.forEach { it.remove() }

        val today = firebaseHelper.getCurrentDate()

        listeners = firebaseHelper.listenToRatings(today) { list ->
            if (_binding == null) return@listenToRatings

            adapter.submitList(list)

            binding.tvEmpty.visibility =
                if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility =
                if (list.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        startListening()   // force reload when tab becomes visible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listeners.forEach { it.remove() }
        listeners = emptyList()
        _binding = null

    }
}