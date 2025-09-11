package com.example.TLV.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.TLV.databinding.FragmentMeasurementBinding
import com.example.TLV.firebase.FirebaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeasurementFragment : Fragment() {

    private var _binding: FragmentMeasurementBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAdapter: ArrayAdapter<String>
    private lateinit var lrnAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeasurementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        fetchMeasurementData()
    }

    private fun setupAdapters() {
        nameAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        lrnAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)

        binding.idEdtName.setAdapter(nameAdapter)
        binding.idEdtLRN.setAdapter(lrnAdapter)

        lifecycleScope.launch {
            nameAdapter.addAll(firebaseHelper.getAllValidNames())
            lrnAdapter.addAll(firebaseHelper.getAllValidLRNs())
        }
    }

    private fun setupListeners() {
        binding.idBtnClear.setOnClickListener {
            binding.idEdtName.text.clear()
            binding.idEdtLRN.text.clear()
            binding.idEdtHeight.text.clear()
            binding.idEdtWeight.text.clear()
        }

        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val lrn = binding.idEdtLRN.text.toString().trim()
            val height = binding.idEdtHeight.text.toString().trim().toFloatOrNull()
            val weight = binding.idEdtWeight.text.toString().trim().toFloatOrNull()

            if ((name.isNotEmpty() || lrn.isNotEmpty()) && height != null && weight != null) {
                lifecycleScope.launch {
                    val student = firebaseHelper.getStudentByName(name)
                        ?: firebaseHelper.getStudentByLRN(lrn)
                    student?.let {
                        val timestamp = getCurrentTimestamp()
                        firebaseHelper.addStudentToMeasurementsCollection(it.name, it.lrn, timestamp, height, weight)
                        // You would update your table display here
                    }
                }
            }
        }
    }

    private fun fetchMeasurementData() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            val students = firebaseHelper.getStudentsFromMeasurementsCollection()
                .filter { it.timestamp.startsWith(currentDate) }
            // Display these students in your UI table
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
