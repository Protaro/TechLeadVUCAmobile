package com.example.TLV.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.TLV.databinding.FragmentScoreBinding
import com.example.TLV.firebase.FirebaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScoreFragment : Fragment() {

    private var _binding: FragmentScoreBinding? = null
    private val binding get() = _binding!!
    private val firebaseHelper = FirebaseHelper()
    private lateinit var nameAdapter: ArrayAdapter<String>
    private lateinit var lrnAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        fetchScoreData()
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
        binding.sliderLiteracy.setOnSeekBarChangeListener(createSliderChangeListener {
            binding.literacyValue.text = "Literacy: $it"
        })

        binding.sliderNumeracy.setOnSeekBarChangeListener(createSliderChangeListener {
            binding.numeracyValue.text = "Numeracy: $it"
        })

        binding.idBtnClear.setOnClickListener {
            binding.idEdtName.text.clear()
            binding.idEdtLRN.text.clear()
            binding.sliderLiteracy.progress = 0
            binding.sliderNumeracy.progress = 0
        }

        binding.idBtnAddRow.setOnClickListener {
            val name = binding.idEdtName.text.toString().trim()
            val lrn = binding.idEdtLRN.text.toString().trim()
            val literacy = binding.sliderLiteracy.progress.toString()
            val numeracy = binding.sliderNumeracy.progress.toString()

            if (name.isNotEmpty() || lrn.isNotEmpty()) {
                lifecycleScope.launch {
                    val student = firebaseHelper.getStudentByName(name)
                        ?: firebaseHelper.getStudentByLRN(lrn)
                    student?.let {
                        val timestamp = getCurrentTimestamp()
                        firebaseHelper.addStudentToLiteracyCollection(it.lrn, literacy, timestamp)
                        firebaseHelper.addStudentToNumeracyCollection(it.lrn, numeracy, timestamp)
                        // You would update your table display here
                    }
                }
            }
        }
    }

    private fun createSliderChangeListener(onProgressChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun fetchScoreData() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            val ratings = firebaseHelper.getStudentsFromRatingsCollection()
            ratings.filter { it.timestamp?.startsWith(currentDate) == true }
                .forEach { rating ->
                    firebaseHelper.getStudentByLRN(rating.lrn)?.let { studentDetails ->
                        // Display in table
                    }
                }
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
