package com.example.safeedutrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class VerificationScreen : AppCompatActivity() {
    private lateinit var otpField1: EditText
    private lateinit var otpField2: EditText
    private lateinit var otpField3: EditText
    private lateinit var otpField4: EditText
    private lateinit var continueButton: View
    private lateinit var emailTextView: TextView

    private lateinit var apiService: ApiService
    private var userEmail: String = "" // Will be populated with the logged-in user's email

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_verification_screen)

        otpField1 = findViewById(R.id.otpField1)
        otpField2 = findViewById(R.id.otpField2)
        otpField3 = findViewById(R.id.otpField3)
        otpField4 = findViewById(R.id.otpField4)
        continueButton = findViewById(R.id.continueButton)
        emailTextView = findViewById(R.id.emailInfo)

        // Set up Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://yoururl.supabase.co") // Use your server's URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)

        // Fetch the user's email from Supabase
        getUserEmail()

        // Request OTP when the activity is created
        sendOtp()

        continueButton.setOnClickListener {
            val otp = otpField1.text.toString() + otpField2.text.toString() +
                    otpField3.text.toString() + otpField4.text.toString()

            if (otp.length == 4) {
                verifyOtp(otp)
            } else {
                Toast.makeText(this, "Please enter a valid OTP", Toast.LENGTH_SHORT).show()
            }

            if (otp.any { !it.isDigit() }) {
                Toast.makeText(this, "OTP must contain only numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (otp.length != 4) {
                Toast.makeText(this, "OTP must be 4 digits long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (otp.length != 4 || otp.any { !it.isDigit() }) {
                Toast.makeText(this, "Please enter a valid 4-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    // Extension function to validate OTP
    private fun String.isValidOtp(): Boolean {
        return length == 4 && all { it.isDigit() }
    }

    // Fetch the current logged-in user's email from Supabase
    private fun getUserEmail() {

        userEmail = intent.getStringExtra("user_email") ?: "" // Retrieve email from Intent
        emailTextView.text = "We sent a code to\n$userEmail"


    }

    // Send OTP to the user's email
    private fun sendOtp() {
        val requestBody = mapOf("email" to userEmail)  // Use the fetched dynamic email

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response<Void> = SupabaseClient.authService.sendOtp(requestBody)
                Log.d("VerificationScreen", "Response Code: ${response.code()}, Body: ${response.body()}")
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "OTP Sent!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        val errorBody = response.errorBody()?.string()
                        Log.e("VerificationScreen", "Error Body: $errorBody")
                        Toast.makeText(applicationContext, "Failed to send OTP", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("VerificationScreen", "Exception: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error sending OTP", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Verify the OTP entered by the user
    private fun verifyOtp(otp: String) {
        val otpRequest = OtpRequest(userEmail, otp)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response<AuthResponse> = SupabaseClient.authService.verifyOtp(otpRequest)
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "OTP Verified!", Toast.LENGTH_SHORT).show()
                        // Proceed to next screen or action
                        val intent = Intent(this@VerificationScreen,SetNewPasswordScreen::class.java)
                        startActivity(intent)
                        finish()


                    }
                } else {
                    runOnUiThread {
                        val errorMessage = response.errorBody()?.string() ?: "Failed to verify OTP"
                        Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                       //remove below line
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Verification failed. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
