package com.example.sharedpreferences

import android.content.Intent
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.sharedpreferences.databinding.ActivityOfflineBinding
import android.content.Context

class OfflineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences // SharedPreferences instance
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

        val loginBtn = findViewById<Button>(R.id.btnLogin)

        /*val db = Room.databaseBuilder(this, AppDatabase::class.java, "dashboard_db").build()*/


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
        // Edited by Ash
        loginBtn.setOnClickListener {
            Toast.makeText(this, "Login test", Toast.LENGTH_SHORT).show()
            val editor = sharedPreferences.edit()
            // Navigate to LoginActivity
            editor.putBoolean("isLoggedIn", false)
            editor.putLong("logTime", 0)
            editor.apply()

            // Navigate to MainActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // End of edit
    }

    // Handle Up button in the ActionBar
    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_dashboard) as NavHostFragment).navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    /*fun addDataOffline(name: String, studentNumber: String, timestamp: Long, context: Context) {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "dashboard_db").build()
        Thread {
            db.dashboardDataDao().insertData(DashboardData(name = name, studentNumber = studentNumber, timestamp = timestamp))
        }.start()
    }*/

    /*fun fetchOfflineData(context: Context): List<DashboardData> {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "dashboard_db").build()
        return db.dashboardDataDao().getAllData()
    }*/

    /*fun syncData(context: Context) {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "dashboard_db").build()
        val localData = db.dashboardDataDao().getAllData()
        val firestore = FirebaseFirestore.getInstance()

        for (data in localData) {
            firestore.collection("validStudents")
                .add(data)
                .addOnSuccessListener {
                    Thread { db.dashboardDataDao().clearAllData() }.start() // Clear local data
                }
                .addOnFailureListener {
                    firestore.collection("invalidCollection").add(data)
                }
        }
    }*/

}
