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
import com.example.sharedpreferences.databinding.FragmentAttendanceBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAutoCompleteAdapter: ArrayAdapter<String>
    private lateinit var lrnAutoCompleteAdapter: ArrayAdapter<String>


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val scannedData = arguments?.getString("scannedData")
        if (!scannedData.isNullOrEmpty()) {
            updateScannedData(scannedData)
        }


        setupAutocompleteAdapters()
        fetchAndDisplayCurrentDateCollection()

        binding.idEdtName.setOnItemClickListener { _, _, position, _ ->
            val selectedName = nameAutoCompleteAdapter.getItem(position)
            if (!selectedName.isNullOrEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val student = firebaseHelper.getStudentByName(selectedName)
                        if (student != null) {
                            binding.idEdtLRN.setText(student.lrn) // Auto-fill LRN
                        } else if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "No LRN found for the selected name",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to fetch LRN for the selected name",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        binding.idEdtLRN.setOnItemClickListener { _, _, position, _ ->
            val selectedLRN = lrnAutoCompleteAdapter.getItem(position)
            if (!selectedLRN.isNullOrEmpty()) {
                binding.idEdtLRN.setText(selectedLRN)

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val student = firebaseHelper.getStudentByLRN(selectedLRN)
                        if (student != null) {
                            binding.idEdtName.setText(student.name) // Auto-fill Name
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to fetch name for the selected LRN",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val lrn = binding.idEdtLRN.text.toString().trim()

            if (name.isNotEmpty() || lrn.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val student = if (name.isNotEmpty()) {
                            firebaseHelper.getStudentByName(name)
                        } else {
                            firebaseHelper.getStudentByLRN(lrn)
                        }

                        if (student != null) {
                            addStudentToTable(student.name, student.lrn)
                        } else if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Invalid name or LRN",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Error adding student to table",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    "Please input either a name or LRN",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

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
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to fetch autocomplete suggestions",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    fun updateScannedData(scannedData: String) {
        if (!isAdded) return

        val lrn = scannedData.trim()
        Log.d("QRScanAtt", "Scanned Data: $scannedData")
        if (lrn.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val student = firebaseHelper.getStudentByLRN(lrn)
                    if (student != null) {
                        addStudentToTable(student.name, lrn)
                    } else if (isAdded) {
                        Toast.makeText(requireContext(), "Invalid LRN scanned", Toast.LENGTH_SHORT)
                            .show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "Error processing scanned LRN",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else if (isAdded) {
            Toast.makeText(requireContext(), "Invalid QR code format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addStudentToTable(name: String, lrn: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var finalName = name
                var finalLrn = lrn

                if (finalName.isNotEmpty() && finalLrn.isEmpty()) {
                    val student = firebaseHelper.getStudentByName(finalName)
                    if (student != null) {
                        finalLrn = student.lrn
                        binding.idEdtLRN.setText(finalLrn)
                    }
                }

                if (finalLrn.isNotEmpty() && finalName.isEmpty()) {
                    val student = firebaseHelper.getStudentByLRN(finalLrn)
                    if (student != null) {
                        finalName = student.name
                        binding.idEdtName.setText(finalName)
                    }
                }

                if (finalName.isNotEmpty() && finalLrn.isNotEmpty()) {
                    firebaseHelper.addStudentToDateCollection(finalName, finalLrn, timestamp)
                    displayInTable(finalName, finalLrn, timestamp)
                } else if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Invalid input. Both name and LRN must be valid.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Error adding student to database",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun fetchAndDisplayCurrentDateCollection() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val students = firebaseHelper.getStudentsFromDateCollection(currentDate)

                students.forEach { student ->
                    displayInTable(student.name, student.lrn, student.timestamp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to fetch current date collection",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun displayInTable(name: String, lrn: String, timestamp: String) {
        if (!isAdded) return

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

        val timestampTextView = TextView(requireContext()).apply {
            text = timestamp
            setPadding(10, 10, 10, 10)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
        }

        tableRow.apply {
            addView(nameTextView)
            addView(lrnTextView)
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





