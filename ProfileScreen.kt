package com.example.safeedutrack

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class ProfileScreen : AppCompatActivity() {

    private val TAG = "ProfileScreen"

    private lateinit var authHelper: AuthHelper

    private lateinit var profileImage: ImageView
    private lateinit var editProfileText: TextView

    private val PREFS_NAME = "StudentPrefs"
    private val REQUEST_IMAGE_PICK = 1
    private val PROFILE_IMAGE_KEY = "profile_image_url"

    // Declare supabaseService so it can be referenced later.
    private val supabaseService: SupabaseService by lazy {
        Retrofit.Builder()
            .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile_screen)

        Log.d(TAG, "onCreate: Profile screen initialized")
        authHelper = AuthHelper(this)

        profileImage = findViewById(R.id.profileImage)
        editProfileText = findViewById(R.id.editProfileText)

        // Load the profile image (integrated Supabase fetch and SharedPreferences fallback)
        loadProfileImage()

        // Retrieve latest student details from Supabase.
        loadStudentDetailsFromSupabase()

        // Read the saved email from SharedPreferences
        val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val userEmail = sharedPreferences.getString("userEmail", "No Email") ?: "No Email"
        Log.d(TAG, "Retrieved user email from SharedPreferences: $userEmail")

        // Fetch the phone number for the user email and update SharedPreferences.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val phoneNumber =
                    authHelper.fetchPhoneNumberForEmail(this@ProfileScreen, supabaseService)
                withContext(Dispatchers.Main) {
                    sharedPreferences.edit().apply {
                        putString("userPhoneNumber", phoneNumber)
                        apply()
                    }
                    Log.d(TAG, "Stored phone number '$phoneNumber' in SharedPreferences.")

                    val emailTextView = findViewById<TextView>(R.id.userEmail)
                    val phoneTextView = findViewById<TextView>(R.id.userPhone)
                    emailTextView.text = userEmail
                    phoneTextView.text = phoneNumber
                    Log.d(TAG, "Updated UI - Email: ${emailTextView.text}, Phone: ${phoneTextView.text}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error while fetching phone number: ${e.localizedMessage}", e)
                    Toast.makeText(
                        this@ProfileScreen,
                        "Failed to fetch phone number",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        profileImage.setOnClickListener { openImagePicker() }
        editProfileText.setOnClickListener { openImagePicker() }

        // Logout button functionality.
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    authHelper.userSession(context = this@ProfileScreen, "Logged Out")
                    Log.d(TAG, "Logout button clicked. Navigating to WelcomeScreen.")
                    val loginPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                    loginPrefs.edit().clear().apply()
                    Log.d(TAG, "LoginPrefs cleared.")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Error during logOut: ${e.localizedMessage}", e)
                        Toast.makeText(
                            this@ProfileScreen,
                            "Error: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            val intent = Intent(this@ProfileScreen, RoleSelectionScreen::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            Toast.makeText(this, "Logout Successful!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
            finish()
        }
    }

    /**
     * This function loads the profile image by first attempting to fetch the URL from Supabase.
     * If no image is fetched, it falls back to the image URL stored in SharedPreferences.
     */
    private fun loadProfileImage() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedImageUrl = sharedPrefs.getString(PROFILE_IMAGE_KEY, null)
        val loginPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        val userEmail = loginPrefs.getString("userEmail", null)

        Log.d(TAG, "loadProfileImage: Attempting to fetch profile image from Supabase for email: $userEmail")
        if (userEmail != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val fetchedImageUrl = authHelper.fetchProfileImageUri(userEmail)
                withContext(Dispatchers.Main) {
                    if (!fetchedImageUrl.isNullOrEmpty()) {
                        Log.d(TAG, "loadProfileImage: Fetched image URL from Supabase: $fetchedImageUrl")
                        Picasso.get().load(fetchedImageUrl).into(profileImage)
                    } else if (!storedImageUrl.isNullOrEmpty()) {
                        Log.d(TAG, "loadProfileImage: No image fetched from Supabase, falling back to stored image URL: $storedImageUrl")
                        Picasso.get().load(storedImageUrl).into(profileImage)
                    } else {
                        Log.d(TAG, "loadProfileImage: No profile image available from Supabase or SharedPreferences")
                    }
                }
            }
        } else {
            Log.d(TAG, "loadProfileImage: User email not found in SharedPreferences. Loading stored image if available.")
            if (!storedImageUrl.isNullOrEmpty()) {
                Picasso.get().load(storedImageUrl).into(profileImage)
            } else {
                Log.d(TAG, "loadProfileImage: No stored profile image available.")
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                saveProfileImageLocally(uri) // Save locally
                uploadImageToSupabase(uri)
            }
        }
    }

    private fun saveProfileImageLocally(uri: Uri) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(PROFILE_IMAGE_KEY, uri.toString()).apply()
    }

    private fun uploadImageToSupabase(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = getFileFromUri(uri) ?: return@launch
            val fileName = "profile_${System.currentTimeMillis()}.jpg"

            // Upload to Supabase Storage
            val success = authHelper.uploadProfileImage(fileName, file.readBytes())
            if (success) {
                val publicUrl =
                    "https://xdfyutgivtoadckozjbc.supabase.co/storage/v1/object/public/profile_pics/$fileName"

                // Save profile image URL locally
                saveProfileImageUrl(publicUrl)

                // Fetch email from SharedPreferences
                val sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                val email = sharedPreferences.getString("email", null)

                if (!email.isNullOrEmpty()) {
                    // Update the profile image URL in the profile table
                    authHelper.updateProfileImageInTable(
                        this@ProfileScreen,
                        mapOf("profile_img_url" to publicUrl)
                    )
                }

                withContext(Dispatchers.Main) {
                    Picasso.get().load(publicUrl).into(profileImage)
                }
            } else {
                Log.e(TAG, "Upload failed")
            }
        }
    }

    private fun saveProfileImageUrl(url: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(PROFILE_IMAGE_KEY, url).apply()
    }

    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver = applicationContext.contentResolver
        val tempFile = File.createTempFile("upload", ".jpg", cacheDir)
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputStream: OutputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Uri to File: ${e.message}")
            null
        }
    }

    // NEW: Function to load student details from Supabase using the provided retrieveStudentDetails function.
    private fun loadStudentDetailsFromSupabase() {
        lifecycleScope.launch {
            val sharedPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
            val email = sharedPrefs.getString("userEmail", null)
            val studentDetails = authHelper.retrieveStudentDetails(this@ProfileScreen, email)
            studentDetails?.let {
                findViewById<TextView>(R.id.userName).text = it.name
                findViewById<TextView>(R.id.userPhone).text = it.contact
                findViewById<TextView>(R.id.rollNo).text = it.roll_number
                findViewById<TextView>(R.id.dept).text = it.department
                findViewById<TextView>(R.id.classid).text = it.class_name
                findViewById<TextView>(R.id.studId).text = it.student_id
            }
        }
    }
}
