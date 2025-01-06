package com.example.sharedpreferences.ui.dashboard

import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.sharedpreferences.databinding.FragmentDashboardBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAutoCompleteAdapter: ArrayAdapter<String>
    private lateinit var studentNumberAutoCompleteAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Restrict name input to strings
        binding.idEdtName.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            if (source.matches("[a-zA-Z, ]*".toRegex())) source else ""
        })

        // Restrict student number input to integers
        binding.idEdtStudentNumber.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            if (source.matches("[0-9]*".toRegex())) source else ""
        })

        setupAutocompleteAdapters()
        fetchAndDisplayCurrentDateCollection()
        fetchAndDisplayMeasurementCollection()

        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val studentNumber = binding.idEdtStudentNumber.text.toString().trim()
            val height = binding.idEdtHeight.text.toString().trim()
            val weight = binding.idEdtWeight.text.toString().trim()

            if (name.isNotEmpty() || studentNumber.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val student = if (name.isNotEmpty()) {
                        firebaseHelper.getStudentByName(name)
                    } else {
                        firebaseHelper.getStudentByNumber(studentNumber)
                    }

                    if (student != null) {
                        if (binding.checkBoxMeasurement.isChecked && height.isNotEmpty() && weight.isNotEmpty()){
                            if (binding.checkBoxAttendance.isChecked) {
                                addStudentToMeasurementTable(student.name, student.studentNumber, binding.idEdtHeight.text.toString().toFloat(), binding.idEdtWeight.text.toString().toFloat())
                                addStudentToAttendanceTable(student.name, student.studentNumber)
                            }
                            else if (!binding.checkBoxAttendance.isChecked){
                                addStudentToMeasurementTable(student.name, student.studentNumber, binding.idEdtHeight.text.toString().toFloat(), binding.idEdtWeight.text.toString().toFloat())
                            }
                        }
                        else if (!binding.checkBoxMeasurement.isChecked){
                            addStudentToAttendanceTable(student.name, student.studentNumber)
                        }
                        else {
                            Toast.makeText(
                                requireContext(),
                                "Please input height and weight",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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

        binding.checkBoxMeasurement.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.idBtnAddRow.text = "Log Measurement"
                binding.layoutMeasurement.visibility = View.VISIBLE
                binding.ScrollViewAttendance.visibility = View.GONE
                binding.ScrollViewMeasurement.visibility = View.VISIBLE

            } else {
                binding.layoutMeasurement.visibility = View.GONE
                binding.ScrollViewAttendance.visibility = View.VISIBLE
                binding.ScrollViewMeasurement.visibility = View.GONE
                binding.checkBoxAttendance.isChecked = false
                binding.idBtnAddRow.text = "Log Feeding Program Attendance"
            }
        }

        binding.checkBoxAttendance.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.idBtnAddRow.text = "Log Feeding Program Attendance & Measurement"
            } else {
                binding.idBtnAddRow.text = "Log Measurement"
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

    private fun addStudentToAttendanceTable(name: String, studentNumber: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.Main).launch {
            try {
                firebaseHelper.addStudentToDateCollection(name, studentNumber, timestamp)
                displayInAttendanceTable(name, studentNumber, timestamp)
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

    private fun addStudentToMeasurementTable(name: String, studentNumber: String, height: Float, weight: Float) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.Main).launch {
            try {
                firebaseHelper.addStudentToMeasurementsCollection(name, studentNumber, timestamp, height, weight)
                displayInMeasurementTable(name, studentNumber, timestamp, height.toString(), weight.toString())
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

    private fun fetchAndDisplayCurrentDateCollection() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val students = firebaseHelper.getStudentsFromDateCollection(currentDate)

                students.forEach { student ->
                    displayInAttendanceTable(student.name, student.studentNumber, student.timestamp)
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

    private fun fetchAndDisplayMeasurementCollection() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val students = firebaseHelper.getStudentsFromMeasurementsCollection()

                students.forEach { student ->
                    displayInMeasurementTable(student.name, student.studentNumber, student.timestamp, student.height.toString(), student.weight.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Failed to fetch measurement collection",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun displayInAttendanceTable(name: String, studentNumber: String, timestamp: String) {
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
            gravity = Gravity.CENTER
        }

        binding.idTableLayoutAttendance.addView(tableRow)
    }

    private fun displayInMeasurementTable(name: String, studentNumber: String, timestamp: String, height: String, weight: String) {
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

        val timestampTextView = TextView(requireContext()).apply {
            text = timestamp
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val heightTextView = TextView(requireContext()).apply {
            text = height
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        val weightTextView = TextView(requireContext()).apply {
            text = weight
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        tableRow.apply {
            addView(nameTextView)
            addView(studentNumberTextView)
            addView(heightTextView)
            addView(weightTextView)
            addView(timestampTextView)
            gravity = Gravity.CENTER
        }

        binding.idTableLayoutMeasurement.addView(tableRow)
    }
}
