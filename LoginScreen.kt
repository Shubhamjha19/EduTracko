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

class LoginScreen : AppCompatActivity() {

    private lateinit var supabaseClient: SupabaseClient
    private lateinit var loadingDialog: FullScreenLoadingDialog
    private lateinit var authHelper: AuthHelper

    private val TAG = "LoginScreen"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_screen)

        supabaseClient = SupabaseClient
        loadingDialog = FullScreenLoadingDialog(this)
        authHelper = AuthHelper(this)

        // Adjust layout for system insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find Views
        val signup = findViewById<TextView>(R.id.signup)
        val backA = findViewById<ImageView>(R.id.barrow)
        val emailEditText = findViewById<EditText>(R.id.emailEdit)
        val passwordEditText = findViewById<EditText>(R.id.passwordEdit)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val forgotPassText = findViewById<TextView>(R.id.forgotpass)






        // Signup Navigation
        signup.setOnClickListener {
            Log.d(TAG, "Signup clicked")
            startActivity(Intent(this, SignupScreen::class.java))
        }

        // Forgot Password Navigation
        forgotPassText.setOnClickListener {
            Log.d(TAG, "Forgot Password clicked")
            startActivity(Intent(this, VerificationScreen::class.java))
        }

        // Back Navigation
        backA.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            onBackPressedDispatcher.onBackPressed()
        }

        // Login Button Click
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            Log.d(TAG, "Login button clicked with email: $email")

            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Show loading dialog and call the login function
                showLoading(true)
                Log.d(TAG, "Login started for $email")
                loginUserInApp(email, password)
            } else {
                Log.e(TAG, "Login failed - Empty fields")
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }



    /**
     * LOGIN the user with email and password
     */
    private fun loginUserInApp(email: String, password: String) {
        val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val keepLoggedIn = findViewById<CheckBox>(R.id.loginCheck).isChecked

        Log.d(TAG, "Login process started for email: $email")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Sending login request for email: $email")
                val isLoginSuccessful = authHelper.loginUser(email, password)

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    if (isLoginSuccessful) {
                        // Save login state and email if "Keep me logged in" is checked.
                        if (keepLoggedIn) {
                            Log.d(TAG, "User chose to stay logged in. Saving credentials.")
                            sharedPreferences.edit().apply {
                                putBoolean("isLoggedIn", true)
                                putString("userEmail", email)
                                apply()
                                Log.d(TAG, "Login successful for email: $email")
                                authHelper.userSession(context = this@LoginScreen,"Logged In")
                            }
                        } else {
                            Log.d(TAG, "User did not check 'Keep me logged in'. Not saving credentials.")
                        }

                        // Proceed to the next screen.
                        Log.d(TAG, "Navigating to StudentDetails")
                        startActivity(Intent(this@LoginScreen, StudentDetails::class.java))
                        finish()
                    } else {
                        Log.e(TAG, "Login failed for email: $email. Invalid credentials.")
                        Toast.makeText(this@LoginScreen, "Invalid email or password. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e(TAG, "Error during login: ${e.localizedMessage}", e)
                    Toast.makeText(this@LoginScreen, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
            setContentView(R.layout.dialog_fullscreen_loading)  // Ensure this points to the updated XML layout

            // Set dialog window properties to make it fully transparent except the ProgressBar
            val window = window
            window?.setBackgroundDrawableResource(android.R.color.transparent)  // Make the background fully transparent
            window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)  // Full screen dialog

            // Initialize ProgressBar
            progressBar = findViewById(R.id.progressBar)

            // Set ProgressBar color using theme
            progressBar.indeterminateDrawable.setTint(context.getColor(R.color.app_theme))

            // Prevent the dialog from being dismissed by back press
            setCancelable(false)
        }
    }
}