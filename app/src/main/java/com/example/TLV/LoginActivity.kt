package com.example.TLV

import ConnectivityReceiver
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity(), ConnectivityReceiver.ConnectivityListener {

    private lateinit var connectivityReceiver: ConnectivityReceiver
    private lateinit var auth: FirebaseAuth // Firebase Auth instance
    private lateinit var sharedPreferences: android.content.SharedPreferences // SharedPreferences instance
    private var isPasswordVisible = false // Track password visibility

    companion object {
        private const val SHARED_PREFS = "shared_prefs"
        private const val REMEMBERED_EMAIL = "remembered_email"
        private const val REMEMBERED_PASSWORD = "remembered_password"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Register the connectivity receiver
        connectivityReceiver = ConnectivityReceiver(this)
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, intentFilter)

        // Initialize Firebase Auth and SharedPreferences
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        // Check if user is already logged in
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)
        val logTime = sharedPreferences.getLong("logTime", 0)
        if (isLoggedIn && (logTime + 48 * 60 * 60 * 1000 > System.currentTimeMillis())) { // 48 hours time-to-live
            auth.signInWithEmailAndPassword(REMEMBERED_EMAIL, REMEMBERED_PASSWORD)
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else if (logTime + 48 * 60 * 60 * 1000 <= System.currentTimeMillis()) {
            sharedPreferences.edit().putBoolean("isLoggedIn", false).apply()
            sharedPreferences.edit().putLong("logTime", 0).apply()
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
        }

        // UI Elements
        val emailEdt = findViewById<EditText>(R.id.idEdtEmail)
        val passwordEdt = findViewById<EditText>(R.id.idEdtPassword)
        val loginBtn = findViewById<Button>(R.id.idBtnLogin)
        val rememberMeCheckBox = findViewById<CheckBox>(R.id.idBtnRemember_me)

        // Load saved email if "Remember Me" was checked
        val rememberedEmail = sharedPreferences.getString(REMEMBERED_EMAIL, null)
        if (!rememberedEmail.isNullOrEmpty()) {
            emailEdt.setText(rememberedEmail)
            rememberMeCheckBox.isChecked = true
        }

        // Email validation
        emailEdt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val drawable: Drawable? =
                    if (Patterns.EMAIL_ADDRESS.matcher(s.toString()).matches()) {
                        ResourcesCompat.getDrawable(resources, R.drawable.done_icon, null)
                    } else {
                        ResourcesCompat.getDrawable(resources, R.drawable.mail_icon, null)
                    }
                emailEdt.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Password visibility toggle
        passwordEdt.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (passwordEdt.right - passwordEdt.compoundDrawables[2].bounds.width())) {
                    isPasswordVisible = !isPasswordVisible
                    passwordEdt.transformationMethod =
                        if (isPasswordVisible) null else PasswordTransformationMethod()
                    passwordEdt.setCompoundDrawablesWithIntrinsicBounds(
                        null, null,
                        if (isPasswordVisible) ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.hide_pass_icon,
                            null
                        ) else ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.show_pass_icon,
                            null
                        ),
                        null
                    )
                    passwordEdt.setSelection(passwordEdt.text.length)
                    passwordEdt.performClick() // Notify accessibility services
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Login Button OnClickListener
        loginBtn.setOnClickListener {
            val email = emailEdt.text.toString().trim()
            val password = passwordEdt.text.toString().trim()

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT)
                    .show()
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT)
                    .show()
            } else {
                loginUser(email, password, rememberMeCheckBox.isChecked)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the connectivity receiver
        unregisterReceiver(connectivityReceiver)
    }

    override fun onNetworkConnectionChanged(isConnected: Boolean) {
        if (!isConnected) {
            Toast.makeText(
                this,
                "No internet connection. Switching to offline mode.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(this, OfflineActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun loginUser(email: String, password: String, rememberMe: Boolean) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    val editor = sharedPreferences.edit()
                    // Save email if "Remember Me" is checked
                    if (rememberMe) {
                        editor.putString(REMEMBERED_EMAIL, email)
                        editor.putString(REMEMBERED_PASSWORD, password)
                        editor.apply()
                    } else {
                        // Clear saved email if not checked
                        editor.remove(REMEMBERED_EMAIL).apply()
                    }

                    // Save login status
                    editor.putBoolean("isLoggedIn", true)
                    editor.putLong("logTime", System.currentTimeMillis())
                    editor.putString("email", email)
                    editor.apply()

                    // Navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Login failed
                    val errorMessage = task.exception?.message ?: "Authentication failed"
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }
}
