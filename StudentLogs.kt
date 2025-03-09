package com.example.safeedutrack

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.squareup.picasso.Picasso
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class StudentLogs : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationsAdapter: StudNotificationsAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_student_logs)

        // Initialize UI elements for displaying student details.
        val tvStudentName = findViewById<TextView>(R.id.tvStudentName)
        val tvRollNo = findViewById<TextView>(R.id.tvRollNo)
        val tvClass = findViewById<TextView>(R.id.tvClass)
        val tvPhoneNumber = findViewById<TextView>(R.id.tvPhoneNumber)
        val tvParentsNumber = findViewById<TextView>(R.id.tvParentsNumber)
        val tvPrnNumber = findViewById<TextView>(R.id.tvPrnNumber)

        // Initialize the ImageView for the student's profile image.
        val ivStudentProfileImage = findViewById<ImageView>(R.id.ivStudentImage)

        // Initialize the RecyclerView and its adapter.
        recyclerView = findViewById(R.id.studentLogsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        notificationsAdapter = StudNotificationsAdapter(emptyList())
        recyclerView.adapter = notificationsAdapter

        // Build Retrofit instance and create your SupabaseService.
        val retrofit = Retrofit.Builder()
            .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val supabaseService = retrofit.create(SupabaseService::class.java)
        val authHelper = AuthHelper(this)

        // Retrieve the student email from the Intent extras.
        val studentEmail = intent.getStringExtra("student_email") ?: ""
        Log.d("StudentLogs", "Student email from intent: $studentEmail")

        // Use a single coroutine to fetch the profile image and student details.
        lifecycleScope.launch {
            // Fetch and load the student's profile image from Supabase.
            if (studentEmail.isNotEmpty()) {
                Log.d("StudentLogs", "Fetching profile image for student email: $studentEmail")
                val profileImageUrl = authHelper.fetchProfileImageUri(studentEmail)
                if (!profileImageUrl.isNullOrEmpty()) {
                    Log.d("StudentLogs", "Profile image URL fetched: $profileImageUrl")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Picasso.get()
                            .load(profileImageUrl)
                            .placeholder(R.drawable.student) // optional placeholder
                            .error(R.drawable.student)       // optional error image
                            .into(ivStudentProfileImage)
                        Log.d("StudentLogs", "Profile image loaded into ImageView")
                    }
                } else {
                    Log.d("StudentLogs", "No profile image URL returned for student email: $studentEmail")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        // Load default image if no URL is returned.
                        Picasso.get().load(R.drawable.student).into(ivStudentProfileImage)
                    }
                }
            } else {
                Log.d("StudentLogs", "Student email is empty; cannot fetch profile image.")
            }

            // Retrieve and display student details from Supabase.
            val studentDetails = authHelper.retrieveStudentDetails(this@StudentLogs, studentEmail)
            if (studentDetails != null) {
                Log.d("StudentLogs", "Student details retrieved: $studentDetails")
                tvStudentName.text = "Name: ${studentDetails.name}"
                tvRollNo.text = "Roll No: ${studentDetails.roll_number}"
                tvClass.text = "Class: ${studentDetails.class_name}"
                tvPhoneNumber.text = "Phone: ${studentDetails.contact}"
                tvPrnNumber.text = "PRN: ${studentDetails.student_id ?: "N/A"}"
            } else {
                Log.d("StudentLogs", "No student details found for email: $studentEmail")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@StudentLogs, "Student details not found.", Toast.LENGTH_SHORT).show()
                }
            }

            // Fetch parent's number using the intent email.
            val parentNumber = authHelper.fetchParentNumberForEmail(this@StudentLogs, supabaseService, studentEmail)
            Log.d("StudentLogsParent", "Parent's Number retrieved: $parentNumber")
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                tvParentsNumber.text = "Parent Number: ${if (parentNumber.isEmpty()) "N/A" else parentNumber}"
            }
        }

        // Poll for notifications periodically.
        lifecycleScope.launch {
            while (true) {
                Log.d("StudentLogs", "Polling for notifications...")
                val fetchedNotifications = authHelper.fetchUserNotificationsForAdmin(
                    this@StudentLogs,
                    supabaseService,
                    studentEmail
                )
                Log.d("StudentLogs", "Fetched ${fetchedNotifications?.size} notifications for email: $studentEmail")
                if (fetchedNotifications != null) {
                    fetchedNotifications.forEach { notification ->
                        Log.d(
                            "StudentLogs",
                            "Notification details: Title='${notification.title}', Body='${notification.body}', SentAt='${notification.sent_at}'"
                        )
                    }
                    notificationsAdapter.updateData(fetchedNotifications)
                    recyclerView.smoothScrollToPosition(0)
                } else {
                    Log.d("StudentLogs", "No notifications fetched from Supabase.")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@StudentLogs, "No notifications found.", Toast.LENGTH_SHORT).show()
                    }
                }
                delay(10_000)
            }
        }
    }
}

class StudNotificationsAdapter(
    private var notifications: List<SupabaseService.NotificationData>
) : RecyclerView.Adapter<StudNotificationsAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.adminNotificationTitle)
        val tvMessage: TextView = itemView.findViewById(R.id.adminNotificationMessage)
        val tvDate: TextView = itemView.findViewById(R.id.adminNotificationDate)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NotificationViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.students_logs_item, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.tvTitle.text = notification.title
        holder.tvMessage.text = notification.body
        holder.tvDate.text = notification.sent_at
    }

    override fun getItemCount(): Int = notifications.size

    @Suppress("NotifyDataSetChanged")
    fun updateData(newNotifications: List<SupabaseService.NotificationData>) {
        Log.d("NotificationsAdapter", "Updating data with ${newNotifications.size} notifications.")
        notifications = newNotifications.sortedByDescending {
            it.sent_at.toLongOrNull() ?: 0L
        }
        notifyDataSetChanged()
    }
}
