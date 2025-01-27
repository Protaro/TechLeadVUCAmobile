package com.example.TLV.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.TLV.databinding.FragmentDashboardBinding
import com.example.TLV.firebase.FirebaseHelper
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

        arguments?.getString("scannedData")?.let { fetchStudentDetailsByQR(it) }

        setupAutocompleteAdapters()
        fetchAndDisplayCollections()
        setupEventListeners()

        // Set default visibility for tables
        showDefaultTable()

        return root
    }

    private fun setupAutocompleteAdapters() {
        nameAutoCompleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        lrnAutoCompleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)

        binding.idEdtName.setAdapter(nameAutoCompleteAdapter)
        binding.idEdtLRN.setAdapter(lrnAutoCompleteAdapter)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                nameAutoCompleteAdapter.addAll(firebaseHelper.getAllValidNames())
                lrnAutoCompleteAdapter.addAll(firebaseHelper.getAllValidLRNs())
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch autocomplete suggestions")
            }
        }
    }

    private fun setupEventListeners() {
        binding.idEdtName.setOnItemClickListener { _, _, position, _ ->
            nameAutoCompleteAdapter.getItem(position)?.let { fetchStudentDetailsByName(it) }
        }

        binding.idEdtLRN.setOnItemClickListener { _, _, position, _ ->
            lrnAutoCompleteAdapter.getItem(position)?.let { fetchStudentDetailsByLRN(it) }
        }

        binding.idBtnAddRow.setOnClickListener { handleAddRowClick() }
        binding.idBtnClear.setOnClickListener { handleClearClick() }

        binding.checkBoxMeasurement.setOnCheckedChangeListener { _, isChecked -> handleMeasurementCheckboxChange(isChecked) }
        binding.checkBoxAttendance.setOnCheckedChangeListener { _, isChecked -> handleAttendanceCheckboxChange(isChecked) }
        binding.checkBoxRating.setOnCheckedChangeListener { _, isChecked -> handleRatingCheckboxChange(isChecked) }

        setupSliderListeners()
    }

    private fun setupSliderListeners() {
        binding.sliderLiteracy.setOnSeekBarChangeListener(createSliderChangeListener { progress ->
            binding.literacyValue.text = "Value: $progress"
        })

        binding.sliderNumeracy.setOnSeekBarChangeListener(createSliderChangeListener { progress ->
            binding.numeracyValue.text = "Value: $progress"
        })
    }

    private fun createSliderChangeListener(onProgressChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            onProgressChanged(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun fetchStudentDetailsByName(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.getStudentByName(name)?.let {
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
                firebaseHelper.getStudentByLRN(lrn)?.let {
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
                firebaseHelper.getStudentByQR(qrCode)?.let {
                    binding.idEdtName.setText(it.name)
                    binding.idEdtLRN.setText(it.lrn)
                    updateScannedData(qrCode)
                } ?: showToast("Failed to fetch name for the QR scanned")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch name for the QR scanned")
            }
        }
    }

    private fun fetchAndDisplayCollections() {
        fetchAndDisplayAttendanceCollection()
        fetchAndDisplayMeasurementCollection()
        fetchAndDisplayRatingsCollection()
    }

    private fun fetchAndDisplayAttendanceCollection() {
        val currentDate = getCurrentTimestamp("yyyy-MM-dd")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.getStudentsFromAttendanceCollection(currentDate).forEach { student ->
                    displayInAttendanceTable(student.name, student.lrn, student.timestamp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch attendance collection")
            }
        }
    }

    private fun fetchAndDisplayMeasurementCollection() {
        val currentDate = getCurrentTimestamp("yyyy-MM-dd")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.getStudentsFromMeasurementsCollection()
                    .filter { it.timestamp.startsWith(currentDate) }
                    .forEach { student ->
                        displayInMeasurementTable(student.name, student.lrn, student.height.toString(), student.weight.toString())
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch measurement collection")
            }
        }
    }

    private fun fetchAndDisplayRatingsCollection() {
        val currentDate = getCurrentTimestamp("yyyy-MM-dd")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.getStudentsFromRatingsCollection()
                    .filter { it.timestamp?.startsWith(currentDate) == true }
                    .forEach { rating ->
                        firebaseHelper.getStudentByLRN(rating.lrn)?.let { studentDetails ->
                            displayInRatingsTable(studentDetails.name, rating.lrn, rating.numeracy.toString(), rating.literacy.toString())
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to fetch ratings collection")
            }
        }
    }

    private fun handleAddRowClick() {
        val name = binding.idEdtName.text.toString().trim()
        val lrn = binding.idEdtLRN.text.toString().trim()
        val height = binding.idEdtHeight.text.toString().trim()
        val weight = binding.idEdtWeight.text.toString().trim()
        val literacy = binding.sliderLiteracy.progress.toString()
        val numeracy = binding.sliderNumeracy.progress.toString()

        if (name.isEmpty() && lrn.isEmpty()) {
            showToast("Please input either a name or student number")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val student = firebaseHelper.getStudentByName(name) ?: firebaseHelper.getStudentByLRN(lrn)
                student?.let {
                    processStudentData(it.name, it.lrn, height, weight, literacy, numeracy)
                } ?: showToast("Invalid name or student number")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error processing student data")
            }
        }
    }

    private fun handleClearClick() {
        binding.idEdtName.text.clear()
        binding.idEdtLRN.text.clear()
        binding.idEdtHeight.text.clear()
        binding.idEdtWeight.text.clear()
    }

    private fun processStudentData(name: String, lrn: String, height: String, weight: String, literacy: String?, numeracy: String?) {
        val heightValue = height.toFloatOrNull()
        val weightValue = weight.toFloatOrNull()

        // Check if height and weight are valid
        if (binding.checkBoxMeasurement.isChecked && heightValue != null && weightValue != null) {
            // Add measurement data
            addStudentToMeasurementTable(name, lrn, heightValue, weightValue)
        }

        // Check if attendance should be logged
        if (binding.checkBoxAttendance.isChecked) {
            addStudentToAttendanceTable(name, lrn)
        }

        // Check if ratings should be logged
        if (binding.checkBoxRating.isChecked) {
            addStudentToRatingTable(name, lrn, literacy, numeracy)
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

    private fun addStudentToMeasurementTable(name: String, lrn: String, height: Float, weight: Float) {
        val timestamp = getCurrentTimestamp("yyyy-MM-dd HH:mm:ss")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.addStudentToMeasurementsCollection(name, lrn, timestamp, height, weight)
                displayInMeasurementTable(name, lrn, height.toString(), weight.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error adding student to measurement database")
            }
        }
    }


    private fun addStudentToRatingTable(name: String, lrn: String, numeracy: String?, literacy: String?) {
        val timestamp = getCurrentTimestamp("yyyy-MM-dd HH:mm:ss")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Add to Literacy Scores
                firebaseHelper.addStudentToLiteracyCollection(lrn, literacy, timestamp)
                // Add to Numeracy Scores
                firebaseHelper.addStudentToNumeracyCollection(lrn, numeracy, timestamp)
                displayInRatingsTable(name, lrn, numeracy, literacy)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error adding student to ratings database: ${e.message}")
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

    private fun handleAttendanceCheckboxChange(isChecked: Boolean) {
        binding.ScrollViewAttendance.visibility = if (isChecked) View.VISIBLE else View.GONE
        updateButtonText()
        showRelevantTables() // Update the visibility of tables based on checkbox states
    }

    private fun handleMeasurementCheckboxChange(isChecked: Boolean) {
        binding.layoutMeasurement.visibility = if (isChecked) View.VISIBLE else View.GONE
        binding.ScrollViewMeasurement.visibility = if (isChecked) View.VISIBLE else View.GONE
        updateButtonText()
        showRelevantTables() // Update the visibility of tables based on checkbox states
        updateAttendanceCheckboxVisibility()

    }

    private fun handleRatingCheckboxChange(isChecked: Boolean) {
        binding.layoutRating.visibility = if (isChecked) View.VISIBLE else View.GONE
        binding.idTableLayoutRatings.visibility = if (isChecked) View.VISIBLE else View.GONE
        updateButtonText()
        showRelevantTables() // Update the visibility of tables based on checkbox states
        updateAttendanceCheckboxVisibility()
    }

    private fun updateAttendanceCheckboxVisibility() {
        val isMeasurementChecked = binding.checkBoxMeasurement.isChecked
        val isRatingChecked = binding.checkBoxRating.isChecked

        // Show the attendance checkbox if either measurement or rating is checked
        binding.checkBoxAttendance.visibility = if (isMeasurementChecked || isRatingChecked) View.VISIBLE else View.GONE

        // Uncheck the attendance checkbox if both measurement and rating are unchecked
        if (!isMeasurementChecked && !isRatingChecked) {
            binding.checkBoxAttendance.isChecked = false
        }
    }

    private fun showRelevantTables() {
        val isAttendanceChecked = binding.checkBoxAttendance.isChecked
        val isMeasurementChecked = binding.checkBoxMeasurement.isChecked
        val isRatingChecked = binding.checkBoxRating.isChecked

        // Show measurement table if measurement checkbox is checked
        binding.ScrollViewMeasurement.visibility = if (isMeasurementChecked) View.VISIBLE else View.GONE

        // Show ratings table if rating checkbox is checked
        binding.ScrollViewRatings.visibility = if (isRatingChecked) View.VISIBLE else View.GONE

        // Show attendance table if attendance checkbox is checked while other checkboxes are checked
        if (isMeasurementChecked || isRatingChecked){
            binding.ScrollViewAttendance.visibility = if (isAttendanceChecked) View.VISIBLE else View.GONE
        }
        // Show default table if no checkbox are checked
        else {
            showDefaultTable()
        }
    }

    private fun updateButtonText() {
        val isAttendanceChecked = binding.checkBoxAttendance.isChecked
        val isMeasurementChecked = binding.checkBoxMeasurement.isChecked
        val isRatingChecked = binding.checkBoxRating.isChecked

        binding.idBtnAddRow.text = when {
            isAttendanceChecked && isMeasurementChecked && isRatingChecked -> "Log Attendance, Measurement & Ratings"
            isAttendanceChecked && isMeasurementChecked -> "Log Attendance & Measurement"
            isAttendanceChecked && isRatingChecked -> "Log Attendance & Ratings"
            isMeasurementChecked && isRatingChecked -> "Log Measurement & Ratings"
            isAttendanceChecked -> "Log Attendance"
            isMeasurementChecked -> "Log Measurement"
            isRatingChecked -> "Log Ratings"
            else -> "Log Attendance" // Default text when no checkboxes are selected
        }
    }

    private fun showDefaultTable() {
        binding.ScrollViewAttendance.visibility = View.VISIBLE
        binding.ScrollViewMeasurement.visibility = View.GONE
        binding.ScrollViewRatings.visibility = View.GONE
    }

    private fun displayInAttendanceTable(name: String, lrn: String, timestamp: String) {
        if (binding.idTableLayoutAttendance.childCount == 1) {
            binding.idTableLayoutAttendance.removeViewAt(1) // Remove placeholder if it exists
        }
        binding.idTableLayoutAttendance.addView(createTableRow(name, lrn, timestamp))
    }

    private fun displayInMeasurementTable(name: String, lrn: String, height: String, weight: String) {
        if (binding.idTableLayoutMeasurement.childCount == 1) {
            binding.idTableLayoutMeasurement.removeViewAt(1) // Remove placeholder if it exists
        }
        for (i in 0 until binding.idTableLayoutMeasurement.childCount) {
            val tableRow = binding.idTableLayoutMeasurement.getChildAt(i) as? TableRow
            tableRow?.let {
                val lrnTextView = it.getChildAt(1) as? TextView
                if (lrnTextView?.text.toString() == lrn) {
                    it.getChildAt(2).apply { (this as TextView).text = height }
                    it.getChildAt(3).apply { (this as TextView).text = weight }
                    return
                }
            }
        }
        binding.idTableLayoutMeasurement.addView(createTableRow(name, lrn, height, weight))
    }

    private fun displayInRatingsTable(name: String, lrn: String, numeracy: String?, literacy: String?) {
        // Check if the table already has a row for the given name
        for (i in 0 until binding.idTableLayoutRatings.childCount) {
            val tableRow = binding.idTableLayoutRatings.getChildAt(i) as? TableRow
            tableRow?.let {
                val nameTextView = it.getChildAt(0) as? TextView // Assuming name is in the first column
                if (nameTextView?.text.toString() == name) {
                    // Update existing row
                    it.getChildAt(1).apply { (this as TextView).text = literacy }
                    it.getChildAt(2).apply { (this as TextView).text = numeracy }
                    return
                }
            }
        }

        // If no existing row was found, add a new row
        binding.idTableLayoutRatings.addView(createTableRow(name, literacy.toString(), numeracy.toString()))
    }

    fun updateScannedData(scannedData: String) {
        val qrCodeData = scannedData.trim()
        if (qrCodeData.isEmpty()) {
            showToast("Invalid QR code format")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firebaseHelper.getStudentByQR(qrCodeData)?.let {
                    addStudentToAttendanceTable(it.name, it.lrn)
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