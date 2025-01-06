package com.example.sharedpreferences.ui.dashboard

import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.sharedpreferences.databinding.FragmentMeasurementBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MeasurementFragment : Fragment() {

    private var _binding: FragmentMeasurementBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAutoCompleteAdapter: ArrayAdapter<String>
    private lateinit var lrnAutoCompleteAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeasurementBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        // Restrict name input to strings
//        binding.idEdtName.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
//            if (source.matches("[a-zA-Z ]*".toRegex())) source else ""
//        })
//
//        // Restrict lrn input to integers
//        binding.idEdtLRN.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
//            if (source.matches("[0-9]*".toRegex())) source else ""
//        })

        setupAutocompleteAdapters()
        fetchAndDisplayCurrentMeasurementsCollection()

        // Handle name selection autofill
        binding.idEdtName.setOnItemClickListener { _, _, position, _ ->
            val selectedName = nameAutoCompleteAdapter.getItem(position)
            if (!selectedName.isNullOrEmpty()) {
                // Fetch the student by name and update the LRN field
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val student = firebaseHelper.getStudentByName(selectedName)
                        if (student != null) {
                            binding.idEdtLRN.setText(student.lrn) // Auto-fill LRN
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "No LRN found for the selected name",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            "Failed to fetch LRN for the selected name",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }


        // Handle LRN selection autofill
        binding.idEdtLRN.setOnItemClickListener { _, _, position, _ ->
            val selectedLRN = lrnAutoCompleteAdapter.getItem(position)
            if (!selectedLRN.isNullOrEmpty()) {
                binding.idEdtLRN.setText(selectedLRN) // Explicitly set the text

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val student = firebaseHelper.getStudentByLRN(selectedLRN)
                        if (student != null) {
                            binding.idEdtName.setText(student.name) // Auto-fill Name
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            "Failed to fetch name for the selected LRN",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Add row button logic
        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val lrn = binding.idEdtLRN.text.toString().trim()
            val height = binding.idEdtHeight.text.toString().trim().toFloatOrNull()
            val weight = binding.idEdtWeight.text.toString().trim().toFloatOrNull()

            if ((name.isNotEmpty() || lrn.isNotEmpty()) && height != null && weight != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    val student = if (name.isNotEmpty()) {
                        firebaseHelper.getStudentByName(name)
                    } else {
                        firebaseHelper.getStudentByLRN(lrn)
                    }

                    if (student != null) {
                        addStudentToMeasurementTable(student.name, student.lrn, height, weight)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Invalid name or LRN",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please input valid name, LRN, height, and weight",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return root
    }

    private fun setupAutocompleteAdapters() {
        nameAutoCompleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        lrnAutoCompleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)

        binding.idEdtName.setAdapter(nameAutoCompleteAdapter)
        binding.idEdtLRN.setAdapter(lrnAutoCompleteAdapter)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val validNames = firebaseHelper.getAllValidNames()
                val validLRNs = firebaseHelper.getAllValidLRNs()
                nameAutoCompleteAdapter.addAll(validNames)
                lrnAutoCompleteAdapter.addAll(validLRNs)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Failed to fetch autocomplete suggestions",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun addStudentToMeasurementTable(name: String, lrn: String, height: Float, weight: Float) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.Main).launch {
            try {
                firebaseHelper.addStudentToMeasurementsCollection(name, lrn, timestamp, height, weight)
                displayInTable(name, lrn, height, weight, timestamp)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Error adding student to database",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun fetchAndDisplayCurrentMeasurementsCollection() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val students = firebaseHelper.getStudentsFromMeasurementsCollection()

                students.forEach { student ->
                    displayInTable(student.name, student.lrn, student.height, student.weight, student.timestamp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Failed to fetch current measurements",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun displayInTable(name: String, lrn: String, height: Float?, weight: Float?, timestamp: String) {
        val tableRow = TableRow(requireContext())

        val nameTextView = TextView(requireContext()).apply {
            text = name
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val lrnTextView = TextView(requireContext()).apply {
            text = lrn
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val heightTextView = TextView(requireContext()).apply {
            text = height?.toString() ?: ""
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val weightTextView = TextView(requireContext()).apply {
            text = weight?.toString() ?: ""
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val timestampTextView = TextView(requireContext()).apply {
            text = timestamp
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        tableRow.apply {
            addView(nameTextView)
            addView(lrnTextView)
            addView(heightTextView)
            addView(weightTextView)
            addView(timestampTextView)
            gravity = Gravity.CENTER
        }

        binding.idTableLayout.addView(tableRow)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
