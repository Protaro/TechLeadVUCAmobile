package com.example.TLV

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.TLV.databinding.ActivityMainBinding
import com.example.TLV.firebase.FirebaseHelper
import com.example.TLV.ui.AttendanceTableFragment
import com.example.TLV.ui.InputBottomSheetFragment
import com.example.TLV.ui.MeasurementTableFragment
import com.example.TLV.ui.ScoreTableFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            FirebaseHelper().ensureDailyDocumentsExist()
        }

        setupToolbarAndLogout()
        setupViewPagerAndTabs()
        setupFab()
    }

    private fun setupViewPagerAndTabs() {
        val viewPager: ViewPager2 = binding.viewPager
        val tabLayout: TabLayout = binding.tabLayout

        // Set up adapter with 3 fragments
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> AttendanceTableFragment()
                    1 -> MeasurementTableFragment()
                    2 -> ScoreTableFragment()
                    else -> AttendanceTableFragment() // fallback
                }
            }
        }

        // Connect tabs with viewpager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Attendance"
                1 -> "Measure"
                2 -> "Scores"
                else -> "?"
            }
        }.attach()

        viewPager.setCurrentItem(0, false)
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            InputBottomSheetFragment().show(supportFragmentManager, "input_bottom_sheet")
        }
    }

    private fun setupToolbarAndLogout() {
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        binding.btnLogout.setOnClickListener {
            val prefs = getSharedPreferences("shared_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("isLoggedIn", false).apply()

            FirebaseAuth.getInstance().signOut()

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    fun refreshAllTables() {
        supportFragmentManager.fragments.forEach { fragment ->
            when (fragment) {
                is AttendanceTableFragment -> fragment.refresh()
                is MeasurementTableFragment -> fragment.refresh()
                is ScoreTableFragment -> fragment.refresh()
            }
        }
    }
}