package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AskQueryScreenActivity : AppCompatActivity() {
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var number: EditText
    private lateinit var urquestion: EditText
    private lateinit var sendbtn : Button
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ask_query_screen)

        name = findViewById(R.id.name)
        email = findViewById(R.id.email)
        number = findViewById(R.id.number)
        urquestion = findViewById(R.id.askurquery)
        sendbtn = findViewById(R.id.sendurquery)

        sendbtn.setOnClickListener {
            val personName = name.text.toString()
            val emailAdd = email.text.toString()
            val phoneNo = number.text.toString()
            val query = urquestion.text.toString()
            val recipientEmail = "shreyalande4436@gmail.com"

            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAdd))
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(query))






            // set type of intent
            intent.type = "message/rfc822"

            // startActivity with intent with chooser as Email client using createChooser function
            startActivity(Intent.createChooser(intent, "Choose an Email client :"))

        }





    }
}