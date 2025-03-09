package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safeedutrack.network.AuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignupScreen : AppCompatActivity() {

    private lateinit var authHelper: AuthHelper
    private lateinit var otpService: OtpService

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_screen)

        // Adjust layout for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        authHelper = AuthHelper(this)

        val signupBtn = findViewById<Button>(R.id.signUpButton)
        val loginText = findViewById<TextView>(R.id.logintext)
        val backArrow = findViewById<ImageView>(R.id.backarrow)
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val phoneEditText = findViewById<EditText>(R.id.numEditText)
        val parentEditText = findViewById<EditText>(R.id.parentnumEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)

        // Navigate to Login screen
        loginText.setOnClickListener {
            startActivity(Intent(this, LoginScreen::class.java))
            finish()
        }

        // Navigate back
        backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        signupBtn.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val phoneNumber = phoneEditText.text.toString().trim()
            val parentNumber = parentEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (!isValidInput(
                    email,
                    phoneNumber,
                    parentNumber,
                    password,
                    confirmPassword
                )
            ) return@setOnClickListener

            // Check if the email already exists
            checkIfEmailExists(email) { emailExists ->
                if (emailExists) {
                    showToast("Email is already registered")
                } else {
                    // Check if the phone number already exists
                    checkIfPhoneNumberExists(phoneNumber) { phoneExists ->
                        if (phoneExists) {
                            showToast("Phone number is already registered")
                        } else {
                            // Send OTP if phone number doesn't exist
                            otpService = OtpService(this)
                            otpService.sendOtp(phoneNumber, object : OtpService.OtpVerificationListener {
                                override fun onOtpVerified(success: Boolean) {
                                    if (success) {
                                        // Proceed with signup after OTP verification
                                        storeUserData(email, password, phoneNumber, parentNumber)
                                    } else {
                                        showToast("OTP verification failed. Please try again.")
                                    }
                                }
                            })
                        }
                    }

                }
            }
        }
    }

    // Check if the phone number is already registered
    private fun checkIfPhoneNumberExists(phoneNumber: String, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isExists = authHelper.isPhoneNumberExists(phoneNumber) // Method in AuthHelper to check if phone number exists
                withContext(Dispatchers.Main) {
                    callback(isExists)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error checking phone number: ${e.localizedMessage}")
                }
            }
        }
    }

    // Check if the email is already registered
    private fun checkIfEmailExists(email: String, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isExists = authHelper.isEmailExists(email) // Method in AuthHelper to check if email exists
                withContext(Dispatchers.Main) {
                    callback(isExists)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error checking email: ${e.localizedMessage}")
                }
            }
        }
    }

    // Validate user input
    private fun isValidInput(email: String, phoneNumber: String,parentNumber: String, password: String, confirmPassword: String): Boolean {
        return when {
            email.isEmpty() || phoneNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                showToast("All fields are required")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showToast("Invalid email format")
                false
            }
            phoneNumber.length != 10 -> {
                showToast("Phone number must be 10 digits")
                false
            }
            parentNumber.length != 10 -> {
                showToast("Phone number must be 10 digits")
                false
            }
            password.length < 6 -> {
                showToast("Password must be at least 6 characters long")
                false
            }
            password != confirmPassword -> {
                showToast("Passwords do not match")
                false
            }
            else -> true
        }
    }

    // Store user data in the system
    private fun storeUserData(email: String, password: String, phoneNumber: String, parentNumber: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isSuccess = authHelper.storeUserData(email, password, phoneNumber, parentNumber )
                withContext(Dispatchers.Main) {
                    if (isSuccess) {
                        showToast("User registered successfully")
                        navigateToLogin(email)
                    } else {
                        showToast("Failed to store user data")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error: ${e.localizedMessage}")
                }
            }
        }
    }


    // Navigate to the Login screen
    private fun navigateToLogin(email: String) {
        val intent = Intent(this, LoginScreen::class.java)
        intent.putExtra("user_email", email) // Pass the email as an extra
        startActivity(intent)
        finish()
    }

    // Show a toast message
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
