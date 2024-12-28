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
import com.example.sharedpreferences.databinding.FragmentMeasurementBinding
import com.example.sharedpreferences.databinding.FragmentAttendanceBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val toggleDashboard = binding.toggleDashboard

//       binding.toggleDashboard.setOnCheckedChangeListener { _, isChecked ->
//           Toast.makeText(requireContext(), if (isChecked) "Measurement" else "Attendance", Toast.LENGTH_SHORT).show()
//       }

        toggleDashboard.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId) {
                loadLayout(1)
            }
            else {
                loadLayout(0)
            }
        }
        loadLayout(0)
        return root
    }

    private fun loadLayout(checkedId: Int) {
        if (checkedId == 1) {
            val measurementBinding = FragmentMeasurementBinding.inflate(layoutInflater)
            binding.toggleContainer.removeAllViews()
            binding.toggleContainer.addView(measurementBinding.root)
        }
        else {
            val attendanceBinding = FragmentAttendanceBinding.inflate(layoutInflater)
            binding.toggleContainer.removeAllViews()
            binding.toggleContainer.addView(attendanceBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
