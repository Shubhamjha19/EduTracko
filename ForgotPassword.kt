package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPassword : AppCompatActivity() {


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val sendCodeButton = findViewById<Button>(R.id.signUpButton)
        val backToLoginLink = findViewById<TextView>(R.id.backToLoginLink)
        val backArrow = findViewById<ImageView>(R.id.backA)

        sendCodeButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendResetPasswordEmail(email)
        }

        backToLoginLink.setOnClickListener {
            val intent = Intent(this@ForgotPassword, LoginScreen::class.java)
            startActivity(intent)
        }
        backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun sendResetPasswordEmail(email: String) {
        val requestBody = mapOf("email" to email)

        SupabaseClient.authService.forgotPassword(requestBody).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@ForgotPassword,
                        "Password reset email sent!",
                        Toast.LENGTH_SHORT

                    ).show()
                    // Navigate to the Set New Password screen after sending the reset email
                    val intent = Intent(this@ForgotPassword, SetNewPasswordScreen::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@ForgotPassword,
                        "Failed to send reset email: ${response.message()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@ForgotPassword, "Error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

}