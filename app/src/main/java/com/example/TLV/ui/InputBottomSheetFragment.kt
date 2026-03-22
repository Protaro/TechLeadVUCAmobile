package com.example.TLV.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.TLV.MainActivity
import com.example.TLV.ScannerActivity
import com.example.TLV.databinding.FragmentInputBottomSheetBinding
import com.example.TLV.firebase.FirebaseHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class InputBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentInputBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val firebaseHelper = FirebaseHelper()

    private lateinit var nameAdapter: ArrayAdapter<String>
    private lateinit var lrnAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAutoComplete()
        setupExpandableSections()
        setupListeners()
    }

    private fun setupAutoComplete() {
        nameAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line
        )
        lrnAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line
        )

        binding.edName.setAdapter(nameAdapter)
        binding.edLrn.setAdapter(lrnAdapter)

        lifecycleScope.launch {
            nameAdapter.addAll(firebaseHelper.getAllValidNames())
            lrnAdapter.addAll(firebaseHelper.getAllValidLRNs())
        }

        binding.edName.setOnItemClickListener { _, _, _, _ ->
            val selectedName = binding.edName.text.toString().trim()
            if (selectedName.isNotEmpty()) {
                lifecycleScope.launch {
                    val student = firebaseHelper.getStudentByName(selectedName)
                    student?.let { binding.edLrn.setText(it.lrn) }
                }
            }
        }

        binding.edLrn.setOnItemClickListener { _, _, _, _ ->
            val selectedLrn = binding.edLrn.text.toString().trim()
            if (selectedLrn.isNotEmpty()) {
                lifecycleScope.launch {
                    val student = firebaseHelper.getStudentByLRN(selectedLrn)
                    student?.let { binding.edName.setText(it.name) }
                }
            }
        }
    }

    private fun setupExpandableSections() {
        binding.cardMeasurement.setOnClickListener {
            binding.layoutMeasurementFields.visibility =
                if (binding.layoutMeasurementFields.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.cardScore.setOnClickListener {
            binding.layoutScoreFields.visibility =
                if (binding.layoutScoreFields.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.sliderLiteracy.addOnChangeListener { _, value, _ ->
            binding.tvLiteracyValue.text = value.toInt().toString()
        }

        binding.sliderNumeracy.addOnChangeListener { _, value, _ ->
            binding.tvNumeracyValue.text = value.toInt().toString()
        }
    }

    private fun setupListeners() {
        val scannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.getStringExtra("SCAN_RESULT")?.let { scannedLrn ->
                    val cleanLrn = scannedLrn.trim()
                    binding.edLrn.setText(cleanLrn)

                    lifecycleScope.launch {
                        val student = firebaseHelper.getStudentByLRN(cleanLrn)
                        if (student != null) {
                            binding.edName.setText(student.name)
                        } else {
                            Toast.makeText(
                                context,
                                "Student not found for this LRN",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        binding.btnScanQr.setOnClickListener {
            val intent = Intent(requireContext(), ScannerActivity::class.java)
            scannerLauncher.launch(intent)
        }

        binding.btnSubmit.setOnClickListener {
            submitData()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun submitData() {
        val name = binding.edName.text.toString().trim()
        val lrn = binding.edLrn.text.toString().trim()

        if (name.isEmpty() && lrn.isEmpty()) {
            Toast.makeText(context, "Please enter name or LRN", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val student = firebaseHelper.getStudentByName(name)
                ?: firebaseHelper.getStudentByLRN(lrn)

            if (student == null) {
                Toast.makeText(context, "Student not found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            var success = true

            // Use consistent timestamp method — change if your helper uses different name
            val timestamp = firebaseHelper.getCurrentTimestamp()

            // Attendance
            if (binding.cbLogAttendance.isChecked) {
                firebaseHelper.addStudentToAttendanceCollection(student.lrn, timestamp)
            }

            // Measurement
            if (binding.layoutMeasurementFields.visibility == View.VISIBLE) {
                val heightStr = binding.edHeight.text.toString().trim()
                val weightStr = binding.edWeight.text.toString().trim()

                if (heightStr.isNotEmpty() && weightStr.isNotEmpty()) {
                    val height = heightStr.toFloatOrNull()
                    val weight = weightStr.toFloatOrNull()
                    if (height != null && weight != null) {
                        firebaseHelper.addStudentToMeasurementsCollection(
                            student.name, student.lrn, timestamp, height, weight
                        )
                    } else {
                        success = false
                        Toast.makeText(context, "Invalid height/weight", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Scores
            if (binding.layoutScoreFields.visibility == View.VISIBLE) {
                val literacy = binding.sliderLiteracy.value.toInt().toString()
                val numeracy = binding.sliderNumeracy.value.toInt().toString()

                firebaseHelper.addStudentToFilipinoCollection(student.lrn, literacy, timestamp)
                firebaseHelper.addStudentToMathCollection(student.lrn, numeracy, timestamp)
            }

            if (success) {
                Toast.makeText(context, "Data logged successfully", Toast.LENGTH_SHORT).show()
                dismiss()
                refreshTables()
            }
        }
    }

    private fun refreshTables() {
        (requireActivity() as? MainActivity)?.refreshAllTables()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}