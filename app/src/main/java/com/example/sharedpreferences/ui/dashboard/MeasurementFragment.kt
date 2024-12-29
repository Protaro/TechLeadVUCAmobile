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

/*
TODO:
Fix issue with AttendanceFragment and MeasurementFragment not
being able to communicate with Firebase (PERMISSION_DENIED)
 */

class MeasurementFragment : Fragment() {

    private var _binding: FragmentMeasurementBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAutoCompleteAdapter: ArrayAdapter<String>
    private lateinit var studentNumberAutoCompleteAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeasurementBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Restrict name input to strings
        binding.idEdtName.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            if (source.matches("[a-zA-Z ]*".toRegex())) source else ""
        })

        // Restrict student number input to integers
        binding.idEdtStudentNumber.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            if (source.matches("[0-9]*".toRegex())) source else ""
        })

        setupAutocompleteAdapters()
        fetchAndDisplayCurrentMeasurementsCollection()

        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val studentNumber = binding.idEdtStudentNumber.text.toString().trim()
            val height = binding.idEdtHeight.text.toString().trim().toFloat()
            val weight = binding.idEdtWeight.text.toString().trim().toFloat()

            if (name.isNotEmpty() || studentNumber.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val student = if (name.isNotEmpty()) {
                        firebaseHelper.getStudentByName(name)
                    } else {
                        firebaseHelper.getStudentByNumber(studentNumber)
                    }

                    if (student != null) {
                        addStudentToMeasurementTable(student.name, student.studentNumber, height, weight)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Invalid name or student number",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please input either a name or student number",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return root
    }

    private fun setupAutocompleteAdapters() {
        // Initialize adapters
        nameAutoCompleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        studentNumberAutoCompleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)

        // Assign adapters to AutoCompleteTextView fields
        binding.idEdtName.setAdapter(nameAutoCompleteAdapter)
        binding.idEdtStudentNumber.setAdapter(studentNumberAutoCompleteAdapter)

        // Populate adapters with Firestore data
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val validNames = firebaseHelper.getAllValidNames()
                val validStudentNumbers = firebaseHelper.getAllValidStudentNumbers()
                nameAutoCompleteAdapter.addAll(validNames)
                studentNumberAutoCompleteAdapter.addAll(validStudentNumbers)
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

    private fun addStudentToMeasurementTable(name: String, studentNumber: String, height: Float, weight: Float) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.Main).launch {
            try {
                firebaseHelper.addStudentToMeasurementsCollection(name, studentNumber, timestamp, height, weight)
                displayInTable(name, studentNumber, height, weight, timestamp)
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
                    displayInTable(student.name, student.studentNumber, student.height, student.weight, student.timestamp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Failed to fetch current date collection",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun displayInTable(name: String, studentNumber: String, height: Float?, weight: Float?, timestamp: String) {
        val tableRow = TableRow(requireContext())

        val nameTextView = TextView(requireContext()).apply {
            text = name
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val studentNumberTextView = TextView(requireContext()).apply {
            text = studentNumber
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val heightTextView = TextView(requireContext()).apply {
            text = height.toString()
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val weightTextView = TextView(requireContext()).apply {
            text = weight.toString()
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
            addView(studentNumberTextView)
            addView(timestampTextView)
            addView(heightTextView)
            addView(weightTextView)
            gravity = Gravity.CENTER
        }

        binding.idTableLayout.addView(tableRow)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
