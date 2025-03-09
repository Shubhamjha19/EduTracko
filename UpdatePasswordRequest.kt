package com.example.safeedutrack

data class UpdatePasswordRequest(
    val email: String,
    val newPassword: String
)
