package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.safeedutrack.network.AuthHelper
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashScreen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        // Adjust layout for system insets.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Start animation on the splash screen image.
        val slogan = findViewById<TextView>(R.id.app_slogan)
        val pic = findViewById<ImageView>(R.id.imageView2)
        val animation = AnimationUtils.loadAnimation(this, R.anim.hover)
        pic.startAnimation(animation)

        // Retrieve shared preferences from Admin, Parent, and Login.
        val adminPrefs: SharedPreferences = getSharedPreferences("AdminPrefs", MODE_PRIVATE)
        val parentPrefs: SharedPreferences = getSharedPreferences("ParentPrefs", MODE_PRIVATE)
        val loginPrefs: SharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)

        // Retrieve credentials.
        val adminEmail = adminPrefs.getString("adminEmail", "")
        val adminPassword = adminPrefs.getString("adminPassword", "")
        val parentChildEmail = parentPrefs.getString("childEmail", "")
        val parentNumber = parentPrefs.getString("parentNumber", "")
        val isUserLoggedIn = loginPrefs.getBoolean("isLoggedIn", false)

        // Check which type of user is logged in.
        when {
            !adminEmail.isNullOrEmpty() && !adminPassword.isNullOrEmpty() -> {
                Log.d(TAG, "Admin is already logged in: $adminEmail")
                navigateToAdminDashboard()
            }
            !parentChildEmail.isNullOrEmpty() && !parentNumber.isNullOrEmpty() -> {
                Log.d(TAG, "Parent is already logged in: $parentChildEmail")
                navigateToParentDashboard()
            }
            isUserLoggedIn -> {
                // For students, check Supabase to see if their details exist.
                val userEmail = loginPrefs.getString("userEmail", "")
                if (userEmail.isNullOrEmpty()) {
                    Log.e(TAG, "User is logged in but email is missing in LoginPrefs")
                    navigateToWelcomeScreen()
                } else {
                    Log.d(TAG, "Regular student logged in with email: $userEmail. Checking for student details in Supabase.")
                    lifecycleScope.launch {
                        // Assume AuthHelper.retrieveStudentDetails returns the details if found, otherwise null.
                        val authHelper = AuthHelper(context = this@SplashScreen)
                        val studentDetails = authHelper.retrieveStudentDetails(this@SplashScreen)
                        if (studentDetails != null) {
                            Log.d(TAG, "Student details found for $userEmail. Navigating to MainActivity.")
                            navigateToUserDashboard()
                        } else {
                            Log.d(TAG, "No student details found for $userEmail. Navigating to StudentDetails screen.")
                            navigateToStudentDetails()
                        }
                    }
                }
            }
            else -> {
                Log.d(TAG, "No user logged in. Navigating to WelcomeScreen.")
                navigateToWelcomeScreen()
            }
        }
    }

    /**
     * Navigates to the Admin Dashboard.
     */
    private fun navigateToAdminDashboard() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, AdminDashboard::class.java))
            finish()
        }, 1000)
    }

    /**
     * Navigates to the Parent Dashboard.
     */
    private fun navigateToParentDashboard() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ParentDashboard::class.java))
            finish()
        }, 1000)
    }

    /**
     * Navigates to the User Dashboard (MainActivity) for students with details.
     */
    private fun navigateToUserDashboard() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1000)
    }

    /**
     * Navigates to the Student Details screen for students who need to enter their data.
     */
    private fun navigateToStudentDetails() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, StudentDetails::class.java))
            finish()
        }, 1000)
    }

    /**
     * Navigates to the Welcome Screen (login/sign-up) if no user is logged in.
     */
    private fun navigateToWelcomeScreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, WelcomeScreen::class.java))
            finish()
        }, 3000) // 3-second delay for splash effect.
    }
}
