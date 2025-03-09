package com.example.safeedutrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.app.Dialog
import android.content.Context
import android.view.Window
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safeedutrack.network.AuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminLogin : AppCompatActivity() {

    private lateinit var supabaseClient: SupabaseClient
    private lateinit var loadingDialog: FullScreenLoadingDialog
    private lateinit var authHelper: AuthHelper

    private val TAG = "AdminLogin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Use the admin login layout
        setContentView(R.layout.activity_admin_login)

        supabaseClient = SupabaseClient
        loadingDialog = FullScreenLoadingDialog(this)
        authHelper = AuthHelper(this)

        // Adjust layout for system insets using the admin's main container ID
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find views using admin login layout IDs
        val backArrow = findViewById<ImageView>(R.id.admin_barrow)
        val emailEditText = findViewById<EditText>(R.id.admin_emailEdit)
        val passwordEditText = findViewById<EditText>(R.id.admin_passwordEdit)
        val loginButton = findViewById<Button>(R.id.admin_loginButton)
        val forgotPassText = findViewById<TextView>(R.id.admin_forgotpass)

        // Forgot Password Navigation (if applicable)
        forgotPassText.setOnClickListener {
            Log.d(TAG, "Forgot Password clicked")
            startActivity(Intent(this, VerificationScreen::class.java))
        }

        // Back Navigation
        backArrow.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            onBackPressedDispatcher.onBackPressed()
        }

        // Login Button Click
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            Log.d(TAG, "Admin Login button clicked with email: $email")

            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Show loading dialog and call the admin login function
                showLoading(true)
                Log.d(TAG, "Admin Login started for $email")
                loginAdminInApp(email, password)
            } else {
                Log.e(TAG, "Login failed - Empty fields")
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Logs in the admin with the provided email and password.
     */
    private fun loginAdminInApp(email: String, password: String) {
        // Use AdminPrefs exclusively to store credentials
        val adminPrefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE)
        val keepLoggedIn = findViewById<CheckBox>(R.id.admin_loginCheck).isChecked

        Log.d(TAG, "Admin login process started for email: $email")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Sending admin login request for email: $email")
                val isLoginSuccessful = authHelper.loginAdmin(email, password)

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    if (isLoginSuccessful) {
                        Log.d(TAG, "Admin login successful for email: $email")
                        Toast.makeText(this@AdminLogin, "Login successful", Toast.LENGTH_SHORT).show()

                        // Save login state and credentials if "Keep me logged in" is checked.
                        if (keepLoggedIn) {
                            Log.d(TAG, "User chose to stay logged in. Saving credentials in AdminPrefs.")
                            adminPrefs.edit().apply {
                                putString("adminEmail", email)
                                putString("adminPassword", password)
                                apply()
                            }
                        } else {
                            Log.d(TAG, "User did not check 'Keep me logged in'. Not saving credentials.")
                        }

                        // Proceed to the Admin Dashboard.
                        Log.d(TAG, "Navigating to AdminDashboard")
                        val intent = Intent(this@AdminLogin, AdminDashboard::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e(TAG, "Admin login failed for email: $email. Invalid credentials.")
                        Toast.makeText(
                            this@AdminLogin,
                            "Invalid email or password. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e(TAG, "Error during admin login: ${e.localizedMessage}", e)
                    Toast.makeText(this@AdminLogin, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            Log.d(TAG, "Showing loading dialog")
            loadingDialog.show() // Show full-screen loading dialog
        } else {
            Log.d(TAG, "Hiding loading dialog")
            loadingDialog.dismiss() // Dismiss the loading dialog
        }
    }

    inner class FullScreenLoadingDialog(context: Context) : Dialog(context) {

        private lateinit var progressBar: ProgressBar

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // Set the dialog to be full screen
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_fullscreen_loading)  // Ensure this layout exists and is updated as needed

            // Make the dialog background transparent and full screen
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

            // Initialize ProgressBar
            progressBar = findViewById(R.id.progressBar)
            progressBar.indeterminateDrawable.setTint(context.getColor(R.color.app_theme))

            // Prevent the dialog from being dismissed via the back button
            setCancelable(false)
        }
    }
}
