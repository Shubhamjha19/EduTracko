package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class RoleSelectionScreen : AppCompatActivity() {
    private lateinit var adminbutton : TextView
    private lateinit var studbutton : TextView
    private lateinit var parentbutton : TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_role_selection_screen)


        adminbutton = findViewById(R.id.adminbtn)
        studbutton = findViewById(R.id.studbtn)
        parentbutton = findViewById(R.id.parentbtn)

        adminbutton.setOnClickListener {
            val intent = Intent(this, AdminLogin::class.java)
            startActivity(intent)

        }
        studbutton.setOnClickListener {
            val intent = Intent(this,LoginScreen::class.java)
            startActivity(intent)

        }

        parentbutton.setOnClickListener {
            val intent = Intent(this,ParentLogin::class.java)
            startActivity(intent)

        }

    }
}