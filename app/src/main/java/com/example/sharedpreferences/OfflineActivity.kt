@file:Suppress("DEPRECATION")

package com.example.sharedpreferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.sharedpreferences.databinding.ActivityOfflineBinding
import com.example.sharedpreferences.firebase.FirebaseHelper
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class OfflineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private val firebaseHelper = FirebaseHelper()

    companion object {
        private const val SHARED_PREFS = "shared_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // View Binding setup
        binding = ActivityOfflineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        // Setup Toolbar
        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        setupQRScanner()
        setupLoginButton()
    }

    private fun setupQRScanner() {
        val qrScannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val scannedData = result.data?.getStringExtra("SCAN_RESULT")
                    if (scannedData != null) {
                        handleScannedDataOffline(scannedData)
                    } else {
                        Toast.makeText(this, "No data received from QR scanner.", Toast.LENGTH_SHORT).show()
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

    private fun handleScannedDataOffline(scannedData: String) {
        // Save data locally
        val dataKey = "scanned_data_${System.currentTimeMillis()}"
        val editor = sharedPreferences.edit()
        editor.putString(dataKey, scannedData)
        editor.apply()

        Toast.makeText(this, "Data saved locally: $scannedData", Toast.LENGTH_SHORT).show()
        syncScannedData()
    }

    private fun setupLoginButton() {
        val loginBtn: Button = binding.btnLogin
        loginBtn.setOnClickListener {
            sharedPreferences.edit().apply {
                putBoolean("isLoggedIn", false)
                apply()
            }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun syncScannedData() {
        val allEntries = sharedPreferences.all
        lifecycleScope.launch {
            for ((key, value) in allEntries) {
                if (key.startsWith("scanned_data_") && value is String) {
                    try {
                        // Simulate upload to Firebase
                        firebaseHelper.uploadScannedLRNToFirebase(value)
                        sharedPreferences.edit().remove(key).apply() // Remove after syncing
                    } catch (e: Exception) {
                        Toast.makeText(this@OfflineActivity, "Failed to sync: $value", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Toast.makeText(this@OfflineActivity, "Data sync complete.", Toast.LENGTH_SHORT).show()
        }
    }
}
