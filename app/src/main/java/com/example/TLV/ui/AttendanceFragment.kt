package com.example.TLV.ui

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
import com.example.TLV.databinding.FragmentAttendanceBinding
import com.example.TLV.firebase.FirebaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAdapter: ArrayAdapter<String>
    private lateinit var lrnAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        fetchAttendanceData()
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
        }

        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val lrn = binding.idEdtLRN.text.toString().trim()
            if (name.isNotEmpty() || lrn.isNotEmpty()) {
                lifecycleScope.launch {
                    val student = firebaseHelper.getStudentByName(name)
                        ?: firebaseHelper.getStudentByLRN(lrn)
                    student?.let {
                        val timestamp = getCurrentTime()
                        firebaseHelper.addStudentToAttendanceCollection(it.name, it.lrn, timestamp)
                        // You would update your table display here
                    }
                }
            }
        }
    }

    fun updateScannedData(scannedData: String) {
        val qrCodeData = scannedData.trim()
        if (qrCodeData.isEmpty()) {
            Toast.makeText(requireContext(), "Invalid QR code format", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val student = firebaseHelper.getStudentByQR(qrCodeData)
                student?.let {
                    addStudentToAttendanceTable(it.name, it.lrn)
                } ?: Toast.makeText(requireContext(), "Invalid QR code scanned", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error processing scanned QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addStudentToAttendanceTable(name: String, lrn: String) {
        val timestamp = getCurrentTimestamp("HH:mm")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("Attendance", "Adding student: Name=$name, LRN=$lrn, Timestamp=$timestamp")
                if (firebaseHelper.checkStudentInAttendanceCollection(lrn)) {
                    firebaseHelper.addStudentToAttendanceCollection(name, lrn, timestamp)
                    displayInAttendanceTable(name, lrn, timestamp)
                } else {
                    showToast("Student already in attendance database")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error adding student to attendance database")
            }
        }
    }

    private fun createTableRow(vararg cellTexts: String): TableRow {
        return TableRow(requireContext()).apply {
            cellTexts.forEach { text ->
                addView(TextView(requireContext()).apply {
                    this.text = text
                    setPadding(10, 10, 10, 10)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    gravity = Gravity.CENTER
                })
            }
            gravity = Gravity.CENTER
        }
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentTimestamp(format: String): String {
        return SimpleDateFormat(format, Locale.getDefault()).format(Date())
    }



    private fun displayInAttendanceTable(name: String, lrn: String, timestamp: String) {
        if (binding.idTableLayoutAttendance.childCount == 1) {
            binding.idTableLayoutAttendance.removeViewAt(1) // remove placeholder
        }
        binding.idTableLayoutAttendance.addView(createTableRow(name, lrn, timestamp))
    }

    private fun fetchAttendanceData() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            val students = firebaseHelper.getStudentsFromAttendanceCollection(currentDate)
            // Display these students in your UI table
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
