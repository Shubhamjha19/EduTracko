package com.example.safeedutrack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ContactUs : AppCompatActivity() {

    lateinit var shreyaemail: TextView
    //    lateinit var shubhemail: TextView
    lateinit var shreyaNo: TextView
    //    lateinit var shubhNo: TextView
    lateinit var askQuery: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_contact_us)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        shreyaemail = findViewById(R.id.shreyaemail)
//        shubhemail = findViewById(R.id.shubhemail)
        shreyaNo = findViewById(R.id.shreyaNo)
//        shubhNo = findViewById(R.id.shubhNo)
        askQuery = findViewById(R.id.askQuery)

        askQuery.setOnClickListener {
            startActivity(Intent(this,AskQueryScreenActivity::class.java))
            finish()
        }

        shreyaemail.setOnClickListener {
            val emailId = shreyaemail.getText().toString()

            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailId))

            intent.type = "message/rfc822"

            // startActivity with intent with chooser as Email client using createChooser function
            startActivity(Intent.createChooser(intent, "Choose an Email client :"))

        }
//        shubhemail.setOnClickListener {
//            val emailId = shubhemail.getText().toString()
//
//            val intent = Intent(Intent.ACTION_SEND)
//            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailId))
//
//            intent.type = "message/rfc822"
//
//            // startActivity with intent with chooser as Email client using createChooser function
//            startActivity(Intent.createChooser(intent, "Choose an Email client :"))
//
//        }

        shreyaNo.setOnClickListener {
            val number = shreyaNo.text.toString()

            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
            startActivity(intent)
        }
//        shubhNo.setOnClickListener {
//            val number = shubhNo.text.toString()
//
//            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
//            startActivity(intent)
//        }


    }
}