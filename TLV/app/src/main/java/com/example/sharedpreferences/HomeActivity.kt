package com.example.sharedpreferences

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    // Constants for shared preferences keys
    companion object {
        const val SHARED_PREFS = "shared_prefs"
        const val EMAIL_KEY = "email_key"
        const val PASSWORD_KEY = "password_key"
        const val REMEMBERED_EMAIL = "remembered_email"
    }

    // Variables for shared preferences
    private lateinit var sharedpreferences: SharedPreferences
    private var email: String? = null
    private var rememberedEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Initialize shared preferences
        sharedpreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        // Retrieve email from shared preferences
        email = sharedpreferences.getString(EMAIL_KEY, null)
        rememberedEmail = sharedpreferences.getString(REMEMBERED_EMAIL, null)

        // Update UI to show logged-in user’s email
        val welcomeTV = findViewById<TextView>(R.id.idTVWelcome)
        welcomeTV.text = "Welcome, ${email ?: "User"}!"

        // Logout Button functionality
        val logoutBtn = findViewById<Button>(R.id.idBtnLogout)
        logoutBtn.setOnClickListener {
            val editor = sharedpreferences.edit()

            // Check if "Remember Me" was toggled
            if (rememberedEmail != null && rememberedEmail == email) {
                // Keep remembered email, but clear the session-specific data
                editor.remove(PASSWORD_KEY)
            } else {
                // Clear all data if "Remember Me" was not toggled
                editor.clear()
            }

            editor.apply()

            // Redirect to LoginActivity
            val intent = Intent(this@HomeActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
