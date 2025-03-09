package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ShareScreenActivity : AppCompatActivity() {
    private lateinit var sharebtn : Button
    private lateinit var textview : TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_screen)

        sharebtn = findViewById(R.id.shareButton)
        textview = findViewById(R.id.textView)

        val animation = AnimationUtils.loadAnimation(this,R.anim.slide)
        textview.startAnimation(animation)

        sharebtn.setOnClickListener {
            shareApp()
        }


    }

    private fun shareApp() {
        val appPackageName = packageName
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out this awesome expense tracker app: https://play.google.com/store/apps/details?id=$appPackageName")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share app via"))
    }
}