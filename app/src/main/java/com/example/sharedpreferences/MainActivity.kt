package com.example.sharedpreferences

import ConnectivityReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.sharedpreferences.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity(), ConnectivityReceiver.ConnectivityListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var connectivityReceiver: ConnectivityReceiver

    companion object {
        private const val SHARED_PREFS = "shared_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // View Binding setup
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        // Setup Toolbar
        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        // Setup BottomNavigationView
        val bottomNavigationView: BottomNavigationView = binding.navView

        // Get NavController from NavHostFragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment
        val navController: NavController = navHostFragment.navController

        // Define top-level destinations
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )

        val logoutBtn = findViewById<Button>(R.id.btnLogout)

        // Link NavController with ActionBar and BottomNavigationView
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNavigationView.setupWithNavController(navController)

        // Update Toolbar title manually
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home -> toolbar.title = "Home"
                R.id.navigation_dashboard -> toolbar.title = "Dashboard"
                R.id.navigation_notifications -> toolbar.title = "Notifications"
                else -> toolbar.title = "App"
            }
        }

        // Logout functionality
        logoutBtn.setOnClickListener {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            val editor = sharedPreferences.edit()
            editor.putBoolean("isLoggedIn", false)
            editor.putLong("logTime", 0)
            editor.apply()

            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Register the ConnectivityReceiver
        connectivityReceiver = ConnectivityReceiver(this)
        val intentFilter = IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the ConnectivityReceiver
        unregisterReceiver(connectivityReceiver)
    }

    override fun onNetworkConnectionChanged(isConnected: Boolean) {
        if (!isConnected) {
            Toast.makeText(this, "No internet connection. Switching to offline mode.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, OfflineActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment).navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
