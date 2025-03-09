package com.example.safeedutrack

data class OtpRequest(
    val email: String, // You can get the email from the previous screen
    val otp: String, // The OTP entered by the user
    val type: String = "email" // Type can be 'email' or 'phone'

)

