@file:Suppress("DEPRECATION")

package com.example.sharedpreferences

import ConnectivityReceiver
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.sharedpreferences.databinding.ActivityMainBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import com.example.sharedpreferences.ui.dashboard.DashboardFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), ConnectivityReceiver.ConnectivityListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var connectivityReceiver: ConnectivityReceiver
    private val firebaseHelper = FirebaseHelper()


    companion object {
        private const val SHARED_PREFS = "shared_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        setupToolbar()
        setupBottomNavigation()
        setupLogoutButton()
        setupConnectivityReceiver()
        setupQRScanner()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment
        val navController: NavController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            toolbar.title = when (destination.id) {
                R.id.navigation_home -> "Home"
                R.id.navigation_dashboard -> "Dashboard"
                R.id.navigation_notifications -> "Notifications"
                else -> "App"
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView: BottomNavigationView = binding.navView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment
        val navController: NavController = navHostFragment.navController

        bottomNavigationView.setupWithNavController(navController)
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            sharedPreferences.edit().apply {
                putBoolean("isLoggedIn", false)
                putLong("logTime", 0)
                apply()
            }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupConnectivityReceiver() {
        connectivityReceiver = ConnectivityReceiver(this)
        val intentFilter = IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, intentFilter)
    }

    private fun setupQRScanner() {
        val qrScannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val scannedData = result.data?.getStringExtra("SCAN_RESULT")
                    if (scannedData != null) {
                        lifecycleScope.launch {
                            handleScannedData(scannedData)
                        }
                    } else {
                        Log.e("QRScan", "No data received from QR scanner.")
                    }
                }
            }

        binding.btnScanner.setOnClickListener {
            val intentIntegrator = IntentIntegrator(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("Scan a QR Code")
                setBeepEnabled(true)
                setBarcodeImageEnabled(true)
            }
            qrScannerLauncher.launch(intentIntegrator.createScanIntent())
        }
    }


    private suspend fun handleScannedData(scannedData: String?) {
        if (scannedData != null) {
            Log.d("QRScan", "Scanned Data: $scannedData")
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as? NavHostFragment
            val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull {
                it is DashboardFragment
            } as? DashboardFragment

            currentFragment?.updateScannedData(scannedData) ?: Log.e(
                "MainActivity",
                "DashboardFragment not active or not found"
            )

            val bundle = Bundle().apply {
                putString("scannedData", scannedData)
            }
            val navController =
                (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment).navController
            navController.navigate(R.id.navigation_dashboard, bundle)

            firebaseHelper.uploadScannedLRNToFirebase(scannedData)
        } else {
            Log.e("QRScan", "No data received from QR scanner.")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectivityReceiver)
    }

    override fun onNetworkConnectionChanged(isConnected: Boolean) {
        if (!isConnected) {
            Toast.makeText(this, "No internet connection. Switching to offline mode.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, OfflineActivity::class.java))
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment).navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
