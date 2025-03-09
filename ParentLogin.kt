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

class ParentLogin : AppCompatActivity() {

    private lateinit var supabaseClient: SupabaseClient
    private lateinit var loadingDialog: FullScreenLoadingDialog
    private lateinit var authHelper: AuthHelper

    private val TAG = "ParentLogin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Use the parent's login layout
        setContentView(R.layout.activity_parent_login)

        supabaseClient = SupabaseClient
        loadingDialog = FullScreenLoadingDialog(this)
        authHelper = AuthHelper(this)

        // Adjust layout for system insets (using the parent's main container ID)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.parent_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find Views using parent's login layout IDs
        val backA = findViewById<ImageView>(R.id.parent_barrow)
        val emailEditText = findViewById<EditText>(R.id.parent_emailEdit)
        // Here, the parent's number is entered in place of a password.
        val parentNumberEditText = findViewById<EditText>(R.id.parent_passwordEdit)
        val loginButton = findViewById<Button>(R.id.parent_loginButton)
        // If you have a "Forgot Password" option in this layout, keep it; otherwise, you can remove it.
        val forgotPassText = findViewById<TextView>(R.id.parent_forgotpass)

        // Forgot Password Navigation (if applicable)
        forgotPassText.setOnClickListener {
            Log.d(TAG, "Forgot Password clicked")
            startActivity(Intent(this, VerificationScreen::class.java))
        }

        // Back Navigation
        backA.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            onBackPressedDispatcher.onBackPressed()
        }

        // Login Button Click – using parent's number instead of a password
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val parentNumber = parentNumberEditText.text.toString().trim()

            Log.d(TAG, "Parent Login button clicked with email: $email")

            if (email.isNotEmpty() && parentNumber.isNotEmpty()) {
                // Show loading dialog and call the parent login function
                showLoading(true)
                Log.d(TAG, "Parent Login started for $email")
                loginParentInApp(email, parentNumber)
            } else {
                Log.e(TAG, "Login failed - Empty fields")
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * LOGIN the parent with child's email and parent number
     */
    private fun loginParentInApp(email: String, parentNumber: String) {
        // Use ParentPrefs exclusively to store credentials
        val parentPrefs = getSharedPreferences("ParentPrefs", MODE_PRIVATE)
        val keepLoggedIn = findViewById<CheckBox>(R.id.parent_loginCheck).isChecked

        Log.d(TAG, "Parent login process started for email: $email")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Sending parent login request for email: $email")
                val isLoginSuccessful = authHelper.loginParent(email, parentNumber)

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    if (isLoginSuccessful) {
                        Log.d(TAG, "Parent login successful for email: $email")

                        // Save login state and email if "Keep me logged in" is checked.
                        if (keepLoggedIn) {
                            Log.d(TAG, "User chose to stay logged in. Saving credentials in ParentPrefs.")
                            // Save the entered child's email and parent's number in ParentPrefs
                            parentPrefs.edit().apply {
                                putString("childEmail", email)
                                putString("parentNumber", parentNumber)
                                apply()
                            }
                        } else {
                            Log.d(TAG, "User did not check 'Keep me logged in'. Not saving credentials in LoginPrefs.")
                        }

                        // Proceed to the Parent Dashboard.
                        Log.d(TAG, "Navigating to ParentDashboard")
                        val intent = Intent(this@ParentLogin, ParentDashboard::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e(TAG, "Parent login failed for email: $email. Invalid credentials.")
                        Toast.makeText(
                            this@ParentLogin,
                            "Invalid email or parent number. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e(TAG, "Error during parent login: ${e.localizedMessage}", e)
                    Toast.makeText(this@ParentLogin, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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

            // Set dialog window properties to make it fully transparent except for the ProgressBar
            val window = window
            window?.setBackgroundDrawableResource(android.R.color.transparent)  // Make the background fully transparent
            window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)  // Full screen dialog

            // Initialize ProgressBar
            progressBar = findViewById(R.id.progressBar)

            // Set ProgressBar color using the app theme
            progressBar.indeterminateDrawable.setTint(context.getColor(R.color.app_theme))

            // Prevent the dialog from being dismissed by back press
            setCancelable(false)
        }
    }
}
