package com.example.safeedutrack

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safeedutrack.network.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SetNewPasswordScreen : AppCompatActivity() {
    private lateinit var supabase: SupabaseClient
    private lateinit var apiService: AuthService
    private var userEmail: String = "" // The user's email passed from VerificationScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_set_new_password_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get the user's email passed from VerificationScreen
        userEmail = intent.getStringExtra("userEmail") ?: ""

        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordInput)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordInput)
        val resetPasswordButton = findViewById<Button>(R.id.resetPasswordButton)

        // Set up Retrofit for the API call
        val retrofit = Retrofit.Builder()
            .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co") // Use your Supabase base URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(AuthService::class.java)

        resetPasswordButton.setOnClickListener {
            val newPassword = newPasswordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()
            if (newPassword == confirmPassword) {
                Toast.makeText(this, "Please enter both passwords", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if(newPassword.isEmpty() || confirmPassword.isEmpty()){
                Toast.makeText(this,"Please enter both passwords",Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updatePassword(newPassword)


        }
    }

    private fun updatePassword(newPassword: String) {
        val request = UpdatePasswordRequest(userEmail, newPassword)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response<Void> = apiService.updatePassword(request)
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Password updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Optionally navigate to the login screen
                        finish() // Finish this activity and return to the login screen
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Failed to update password",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Error updating password",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}