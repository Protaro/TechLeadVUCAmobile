package com.example.sharedpreferences.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sharedpreferences.databinding.FragmentDashboardBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAutoCompleteAdapter: ArrayAdapter<String>
    private lateinit var lrnAutoCompleteAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val scannedData = arguments?.getString("scannedData")
        scannedData?.let {
            fetchStudentDetailsByQR(it)
        }

        setupAutocompleteAdapters()
        fetchAndDisplayCurrentDateCollection()
        fetchAndDisplayMeasurementCollection()


        setupEventListeners()

        return root
    }

    private fun setupAutocompleteAdapters() {
        nameAutoCompleteAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        lrnAutoCompleteAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)

        binding.idEdtName.setAdapter(nameAutoCompleteAdapter)
        binding.idEdtLRN.setAdapter(lrnAutoCompleteAdapter)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val validNames = firebaseHelper.getAllValidNames()
                val validLRNs = firebaseHelper.getAllValidLRNs()
                nameAutoCompleteAdapter.addAll(validNames)
                lrnAutoCompleteAdapter.addAll(validLRNs)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch autocomplete suggestions")
            }
        }
    }

    private fun setupEventListeners() {
        binding.idEdtName.setOnItemClickListener { _, _, position, _ ->
            val selectedName = nameAutoCompleteAdapter.getItem(position)
            selectedName?.let { fetchStudentDetailsByName(it) }
        }

        binding.idEdtLRN.setOnItemClickListener { _, _, position, _ ->
            val selectedLRN = lrnAutoCompleteAdapter.getItem(position)
            selectedLRN?.let { fetchStudentDetailsByLRN(it) }
        }

        binding.idBtnAddRow.setOnClickListener { handleAddRowClick() }

        binding.idBtnClear.setOnClickListener { handleClearClick() }

        binding.checkBoxMeasurement.setOnCheckedChangeListener { _, isChecked ->
            handleMeasurementCheckboxChange(isChecked)
        }

        binding.checkBoxAttendance.setOnCheckedChangeListener { _, isChecked ->
            handleAttendanceCheckboxChange(isChecked)
        }
    }

    private fun fetchStudentDetailsByName(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val student = firebaseHelper.getStudentByName(name)
                student?.let {
                    binding.idEdtLRN.setText(it.lrn)
                } ?: showToast("No LRN found for the selected name")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch LRN for the selected name")
            }
        }
    }

    private fun fetchStudentDetailsByLRN(lrn: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val student = firebaseHelper.getStudentByLRN(lrn)
                student?.let {
                    binding.idEdtName.setText(it.name)
                } ?: showToast("Failed to fetch name for the selected LRN")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch name for the selected LRN")
            }
        }
    }

    private fun fetchStudentDetailsByQR(qrCode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val student = firebaseHelper.getStudentByQR(qrCode)
                student?.let {
                    binding.idEdtName.setText(it.name)
                } ?: showToast("Failed to fetch name for the QR scanned")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch name for the QR scanned")
            }
        }
    }


    private fun handleAddRowClick() {
        val name = binding.idEdtName.text.toString().trim()
        val lrn = binding.idEdtLRN.text.toString().trim()
        val height = binding.idEdtHeight.text.toString().trim()
        val weight = binding.idEdtWeight.text.toString().trim()

        if (name.isEmpty() && lrn.isEmpty()) {
            showToast("Please input either a name or student number")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val student = firebaseHelper.getStudentByName(name)
                    ?: firebaseHelper.getStudentByLRN(lrn)

                student?.let {
                    processStudentData(it.name, it.lrn, height, weight)
                } ?: showToast("Invalid name or student number")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error processing student data")
            }
        }
    }

    private fun handleClearClick() {
        // Clear the text of the input fields
        binding.idEdtName.text.clear()
        binding.idEdtLRN.text.clear()
        binding.idEdtHeight.text.clear()
        binding.idEdtWeight.text.clear()
    }

    private fun processStudentData(name: String, lrn: String, height: String, weight: String) {
        if (binding.checkBoxMeasurement.isChecked && height.isNotEmpty() && weight.isNotEmpty()) {
            val heightValue = height.toFloatOrNull()
            val weightValue = weight.toFloatOrNull()

            if (heightValue != null && weightValue != null) {
                if (binding.checkBoxAttendance.isChecked) {
                    addStudentToMeasurementTable(name, lrn, heightValue, weightValue)
                    addStudentToAttendanceTable(name, lrn)
                } else {
                    addStudentToMeasurementTable(name, lrn, heightValue, weightValue)
                }
            } else {
                showToast("Please input valid height and weight")
            }
        } else {
            addStudentToAttendanceTable(name, lrn)
        }
    }

    private fun handleMeasurementCheckboxChange(isChecked: Boolean) {
        binding.layoutMeasurement.visibility = if (isChecked) View.VISIBLE else View.GONE
        binding.ScrollViewMeasurement.visibility = if (isChecked) View.VISIBLE else View.GONE
        binding.ScrollViewAttendance.visibility = if (isChecked) View.GONE else View.VISIBLE
        binding.checkBoxAttendance.isChecked = false

        binding.idBtnAddRow.text = if (isChecked) {
            "Log Measurement"
        } else {
            "Log Feeding Program Attendance"
        }
    }

    private fun handleAttendanceCheckboxChange(isChecked: Boolean) {
        binding.idBtnAddRow.text = if (isChecked) {
            "Log Feeding Program Attendance & Measurement"
        } else {
            "Log Measurement"
        }
    }

    private fun addStudentToAttendanceTable(name: String, lrn: String) {
        val timestamp = getCurrentTimestamp("HH:mm")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("Attendance", "Adding student: Name=$name, LRN=$lrn, Timestamp=$timestamp")
                firebaseHelper.addStudentToDateCollection(name, lrn, timestamp)
                displayInTableAttendance(name, lrn, timestamp)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error adding student to attendance database")
            }
        }
    }

    private fun addStudentToMeasurementTable(name: String, lrn: String, height: Float, weight: Float) {
        val timestamp = getCurrentTimestamp("yyyy-MM-dd HH:mm:ss")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.addStudentToMeasurementsCollection(name, lrn, timestamp, height, weight)
                displayInMeasurementTable(name, lrn, timestamp, height.toString(), weight.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error adding student to measurement database")
            }
        }
    }

    private fun displayInTableAttendance(name: String, lrn: String, timestamp: String) {
        val tableRow = createTableRow(name, lrn, timestamp)
        binding.idTableLayoutAttendance.addView(tableRow)
    }

    private fun displayInMeasurementTable(name: String, lrn: String, timestamp: String, height: String, weight: String) {
        val tableRow = createTableRow(name, lrn, height, weight, timestamp)
        binding.idTableLayoutMeasurement.addView(tableRow)
    }

    private fun createTableRow(vararg cellTexts: String): TableRow {
        val tableRow = TableRow(requireContext())
        cellTexts.forEach { text ->
            val textView = TextView(requireContext()).apply {
                this.text = text
                setPadding(10, 10, 10, 10)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = Gravity.CENTER
            }
            tableRow.addView(textView)
        }
        tableRow.gravity = Gravity.CENTER
        return tableRow
    }

    private fun fetchAndDisplayCurrentDateCollection() {
        val currentDate = getCurrentTimestamp("yyyy-MM-dd")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val students = firebaseHelper.getStudentsFromDateCollection(currentDate)
                students.forEach { student ->
                    displayInTableAttendance(student.name, student.lrn, student.timestamp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch current date collection")
            }
        }
    }

    private fun fetchAndDisplayMeasurementCollection() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val students = firebaseHelper.getStudentsFromMeasurementsCollection()
                students.forEach { student ->
                    displayInMeasurementTable(
                        student.name,
                        student.lrn,
                        student.timestamp,
                        student.height.toString(),
                        student.weight.toString()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch measurement collection")
            }
        }
    }

    fun updateScannedData(scannedData: String) {
        val qrCodeData = scannedData.trim()
        if (qrCodeData.isEmpty()) {
            showToast("Invalid QR code format")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Use getStudentByQR to fetch student details
                val student = firebaseHelper.getStudentByQR(qrCodeData)
                student?.let {
                    addStudentToAttendanceTable(it.name, it.lrn) // Ensure `it.name` and `it.lrn` are valid
                } ?: showToast("Invalid QR code scanned")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error processing scanned QR code")
            }
        }
    }


    private fun getCurrentTimestamp(format: String): String {
        return SimpleDateFormat(format, Locale.getDefault()).format(Date())
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
