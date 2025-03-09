package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safeedutrack.R

class WelcomeScreen : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        var btn = findViewById<Button>(R.id.createAccountButton)
        val alreadyacc = findViewById<TextView>(R.id.alreadyacc)

         btn.setOnClickListener {

             val intent = Intent(this@WelcomeScreen, SignupScreen::class.java)
             startActivity(intent)
         }

        alreadyacc.setOnClickListener {
            val intent = Intent(this@WelcomeScreen, RoleSelectionScreen::class.java)
            startActivity(intent)
        }




    }
}