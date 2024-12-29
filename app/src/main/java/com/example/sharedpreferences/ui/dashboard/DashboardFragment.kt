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
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toggleDashboard.setOnCheckedChangeListener { _, checkedId ->
            loadLayout(checkedId)
        }
        loadLayout(false)
    }

    private fun loadLayout(checkedId: Boolean) {
        val fragmentManager = childFragmentManager
        val transaction = fragmentManager.beginTransaction()

        if (checkedId) {
            transaction.replace(binding.toggleContainer.id, MeasurementFragment())
        }
        else {
            transaction.replace(binding.toggleContainer.id, AttendanceFragment())
        }

        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
