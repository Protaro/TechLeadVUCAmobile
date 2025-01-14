package com.example.sharedpreferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class RegisterActivity : AppCompatActivity() {

    // Creating constant keys for shared preferences.
    companion object {
        const val SHARED_PREFS = "shared_prefs"
        const val EMAIL_KEY = "email_key"
        const val PASSWORD_KEY = "password_key"
    }

    // Variables for shared preferences.
    private lateinit var sharedPreferences: SharedPreferences
    private var email: String? = null
    private var password: String? = null
    private var isPasswordVisible = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initializing EditTexts and Button
        val emailEdt = findViewById<EditText>(R.id.idEdtEmail)
        val passwordEdt = findViewById<EditText>(R.id.idEdtPassword)
        val loginBtn = findViewById<Button>(R.id.idBtnLogin)

        // Getting data stored in shared preferences
        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)
        email = sharedPreferences.getString(EMAIL_KEY, null)
        password = sharedPreferences.getString(PASSWORD_KEY, null)

        // Add email validation
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

        // Handle password visibility toggle
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
                        )
                        else ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.show_pass_icon,
                            null
                        ),
                        null
                    )

                    passwordEdt.setSelection(passwordEdt.text.length)
                    // Call performClick() to notify accessibility services
                    passwordEdt.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Override performClick for the EditText
        passwordEdt.setOnClickListener {
            // Perform any additional actions if necessary
        }


        // OnClickListener for login button
        loginBtn.setOnClickListener {
            if (TextUtils.isEmpty(emailEdt.text.toString()) || TextUtils.isEmpty(passwordEdt.text.toString())) {
                Toast.makeText(this, "Please Enter Email and Password", Toast.LENGTH_SHORT).show()
            } else {
                val editor = sharedPreferences.edit()
                editor.putString(EMAIL_KEY, emailEdt.text.toString())
                editor.putString(PASSWORD_KEY, passwordEdt.text.toString())
                editor.apply()

                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
