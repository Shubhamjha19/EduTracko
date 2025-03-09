package com.example.safeedutrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.safeedutrack.network.AuthHelper
import kotlinx.coroutines.launch

class StudentDetails : AppCompatActivity() {

    private val TAG = "StudentDetailsActivity"

    // EditText fields and button from your layout
    private lateinit var etStudentName: EditText
    private lateinit var etContact: EditText
    private lateinit var etRollNumber: EditText
    private lateinit var etDepartment: EditText
    private lateinit var etClass: EditText
    private lateinit var etStudentId: EditText
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the student details are already available
        lifecycleScope.launch {
            val authHelper = AuthHelper(context = this@StudentDetails)
            // Optionally pass an email if you have one; otherwise, it will use SharedPreferences
            val studentDetails = authHelper.retrieveStudentDetails(this@StudentDetails)
            if (studentDetails != null) {
                Log.d(TAG, "Student details already exist: $studentDetails. Navigating to MainActivity.")
                startActivity(Intent(this@StudentDetails, MainActivity::class.java))
                finish()
                return@launch
            } else {
                Log.d(TAG, "No student details found. Initializing StudentDetails UI.")
                // Initialize the UI only if details are not available.
                initializeUI()
            }
        }
    }

    private fun initializeUI() {
        Log.d(TAG, "onCreate: Starting StudentDetailsActivity")
        // Ensure this layout file matches your XML file (e.g., activity_student_details.xml)
        setContentView(R.layout.activity_student_details)

        // Initialize views
        etStudentName = findViewById(R.id.et_student_name)
        etContact = findViewById(R.id.et_contact)
        etRollNumber = findViewById(R.id.et_roll_number)
        etDepartment = findViewById(R.id.et_department)
        etClass = findViewById(R.id.et_class)
        etStudentId = findViewById(R.id.et_student_id)
        btnSubmit = findViewById(R.id.btn_submit)
        Log.d(TAG, "onCreate: Views initialized successfully")

        // Set click listener on the Submit button
        btnSubmit.setOnClickListener {
            Log.d(TAG, "Submit button clicked")
            val name = etStudentName.text.toString().trim()
            val contact = etContact.text.toString().trim()
            val rollNumber = etRollNumber.text.toString().trim()
            val department = etDepartment.text.toString().trim()
            val className = etClass.text.toString().trim()
            val studentId = etStudentId.text.toString().trim()
            Log.d(TAG, "Input collected - Name: $name, Contact: $contact, Roll Number: $rollNumber, Department: $department, Class: $className, Student ID: $studentId")

            // Basic validation to ensure no field is empty
            if (name.isEmpty() || contact.isEmpty() || rollNumber.isEmpty() ||
                department.isEmpty() || className.isEmpty() || studentId.isEmpty()
            ) {
                Log.w(TAG, "Validation failed: One or more fields are empty")
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Launch a coroutine to call the suspend function for storing details
            lifecycleScope.launch {
                Log.d(TAG, "Coroutine launched for storing student details")
                val authHelper = AuthHelper(context = this@StudentDetails)
                Log.d(TAG, "AuthHelper instance created")
                val isStored = authHelper.storeStudentDetails(
                    context = this@StudentDetails,
                    name = name,
                    contact = contact,
                    rollNumber = rollNumber,
                    department = department,
                    className = className,
                    studentId = studentId
                )
                Log.d(TAG, "storeStudentDetails result: $isStored")

                if (isStored) {
                    Log.i(TAG, "Student details stored successfully")
                    // Proceed to the next screen.
                    Log.d(TAG, "Navigating to MainActivity for Students")
                    startActivity(Intent(this@StudentDetails, MainActivity::class.java))
                    finish()
                } else {
                    Log.e(TAG, "Failed to store student details")
                    Toast.makeText(this@StudentDetails, "Failed to store details", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
