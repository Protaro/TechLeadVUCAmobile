package com.example.sharedpreferences

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Get the NavHostFragment and NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment
        val navController: NavController = navHostFragment.navController

        // Setup ActionBar with NavController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        // Optionally, update the title manually when navigating between fragments
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home -> toolbar.title = "Home"
                R.id.navigation_dashboard -> toolbar.title = "Dashboard"
                R.id.navigation_notifications -> toolbar.title = "Notifications"
                else -> toolbar.title = "App"
            }
        }
    }

    // To handle Up button or navigation from the ActionBar
    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment).navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
