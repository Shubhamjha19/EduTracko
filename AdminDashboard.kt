package com.example.safeedutrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safeedutrack.databinding.ActivityAdminDashboardBinding
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * AdminDashboard Activity:
 * - Starts the notification polling service.
 * - Sets up the bottom navigation.
 * - Fetches and displays notifications from Supabase.
 */
class AdminDashboard : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdminNotificationsAdapter
    private lateinit var authHelper: AuthHelper

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://yoururl.supabase.co") // Replace with your Supabase base URL.
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val supabaseService = retrofit.create(SupabaseService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("AdminDashboard", "Starting notification polling service.")

        // Start the notification polling service.
        val serviceIntent = Intent(this, AdminNotificationPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("AdminDashboard", "Starting foreground service for polling notifications.")
            startForegroundService(serviceIntent)
        } else {
            Log.d("AdminDashboard", "Starting background service for polling notifications.")
            startService(serviceIntent)
        }

        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the RecyclerView.
        recyclerView = findViewById(R.id.recyclerViewStudents)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminNotificationsAdapter()
        recyclerView.adapter = adapter

        // Initialize AuthHelper.
        authHelper = AuthHelper(this)

        // Fetch and display the latest notifications.
        fetchLatestNotifications()

        // Setup Bottom Navigation.
        bottomNavigationView = findViewById(R.id.adminBottomNavigationView)
        bottomNavigationView.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.home -> {
                    Log.d("AdminDashboard", "BottomNavigation: Home selected.")
                    val intent = Intent(this, AdminDashboard::class.java)
                    startActivity(intent)
                    true
                }
                R.id.alerts -> {
                    Log.d("AdminDashboard", "BottomNavigation: Alerts selected.")
                    val intent = Intent(this, RateUs::class.java)
                    startActivity(intent)
                    true
                }
                R.id.location -> {
                    Log.d("AdminDashboard", "BottomNavigation: Location selected.")
                    val intent = Intent(this, AdminMap::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        val logoutButton = findViewById<Button>(R.id.adminLogout)
        logoutButton.setOnClickListener {
            Log.d("AdminDashboard", "Logout button clicked. Clearing credentials and stopping services.")
            // Clear stored admin credentials.
            val adminPrefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE)
            adminPrefs.edit().clear().apply()

            // Stop the notification polling service.
            stopService(Intent(this, AdminNotificationPollingService::class.java))

            // Redirect the user to the Admin Login screen.
            val loginIntent = Intent(this, WelcomeScreen::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(loginIntent)
            finish()  // Close the dashboard activity.
        }
    }

    private fun fetchLatestNotifications() {
        // Launch a coroutine to fetch notifications off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("AdminDashboard", "Starting fetchLatestNotifications coroutine.")
            try {
                // Retrieve the latest notifications using AuthHelper.
                val notifications = authHelper.fetchLatestUserNotificationsFromSupabase(this@AdminDashboard, supabaseService)
                Log.d("AdminDashboard", "Fetched ${notifications.size} notifications from Supabase.")
                // Filter out duplicate notifications by student email.
                val uniqueNotifications = notifications.distinctBy { it.user_email }
                Log.d("AdminDashboard", "After filtering, found ${uniqueNotifications.size} unique notifications.")

                // Update the adapter on the main thread.
                withContext(Dispatchers.Main) {
                    adapter.setNotifications(uniqueNotifications)
                    Log.d("AdminDashboard", "Adapter updated with new notifications.")
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error fetching notifications", e)
            }
        }
    }
}

/**
 * RecyclerView Adapter for displaying notifications.
 */
class AdminNotificationsAdapter(
    private var notifications: List<SupabaseService.NotificationData> = listOf()
) : RecyclerView.Adapter<AdminNotificationsAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // TextView for the student's email.
        val studentEmail: TextView = itemView.findViewById(R.id.studentName)
        // TextView for the notification title.
        val notificationTitle: TextView = itemView.findViewById(R.id.notificationMessage)
        // TextView for the notification status (timestamp).
        val notificationStatus: TextView = itemView.findViewById(R.id.notificationStatus)
        // "View Details" button.
        val viewDetailsButton: Button = itemView.findViewById(R.id.btnViewDetails)
        // Student profile image.
        val profileImage: ImageView = itemView.findViewById(R.id.studentProfilePic)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.adminitem_notifications, parent, false)
        Log.d("AdminNotificationsAdapter", "Inflated adminitem_notifications layout for new ViewHolder.")
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.studentEmail.text = notification.user_email
        holder.notificationTitle.text = notification.title
        holder.notificationStatus.text = notification.sent_at
        Log.d("AdminNotificationsAdapter", "Binding notification at position $position: Email=${notification.user_email}, Title=${notification.title}")

        // When the "View Details" button is clicked, open StudentLogsActivity.
        holder.viewDetailsButton.setOnClickListener {
            Log.d("AdminNotificationsAdapter", "View Details clicked for notification at position $position")
            val context = holder.itemView.context
            val intent = Intent(context, StudentLogs::class.java)
            // Pass the details via Intent extras.
            intent.putExtra("student_email", notification.user_email)
            intent.putExtra("notification_title", notification.title)
            intent.putExtra("notification_body", notification.body)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = notifications.size

    fun setNotifications(newNotifications: List<SupabaseService.NotificationData>) {
        Log.d("AdminNotificationsAdapter", "Updating adapter with ${newNotifications.size} notifications.")
        notifications = newNotifications.sortedByDescending { it.sent_at.toLongOrNull() ?: 0L }
        notifyDataSetChanged()
    }
}

/**
 * Service that polls Supabase for new notifications and sends high-priority notifications.
 */
class AdminNotificationPollingService : Service() {

    private val TAG = "AdminNotifPollingSvc"
    // Map to track the latest notification timestamp for each email.
    private val lastNotificationTimestamps = mutableMapOf<String, Long>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var supabaseService: SupabaseService
    private lateinit var authHelper: AuthHelper

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "adminPollingNotificaitons"
            val channelName = "Admin Notifications"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        super.onCreate()
        Log.d(TAG, "Service onCreate: Initializing Retrofit and AuthHelper.")
        val retrofit = Retrofit.Builder()
            .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co") // Replace with your Supabase URL.
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        supabaseService = retrofit.create(SupabaseService::class.java)
        authHelper = AuthHelper(this)

        // Start as a foreground service.
        startForeground(1, createForegroundNotification())
        Log.d(TAG, "Service onCreate: Foreground service started.")
    }

    private fun createForegroundNotification() =
        NotificationCompat.Builder(this, "adminPollingNotificaitons")
            .setContentTitle("Edutracko")
            .setContentText("Monitoring for new notifications...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

    /**
     * Parses an ISO 8601 timestamp (with or without fractional seconds)
     * into a Unix timestamp (milliseconds), removing the "T" if present.
     */
    private fun parseSentAt(sentAt: String): Long {
        // Replace "T" with a space.
        val normalizedSentAt = sentAt.replace("T", " ")
        Log.d(TAG, "Normalized timestamp: $normalizedSentAt")
        return try {
            if (normalizedSentAt.contains(".")) {
                val parts = normalizedSentAt.split(".")
                val datePart = parts[0]
                var fractionPart = parts[1].filter { it.isDigit() }
                val msPart = when {
                    fractionPart.length > 3 -> fractionPart.substring(0, 3)
                    fractionPart.length < 3 -> fractionPart.padEnd(3, '0')
                    else -> fractionPart
                }
                val newSentAt = "$datePart.$msPart"
                Log.d(TAG, "Formatted timestamp for parsing: $newSentAt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsedTime = sdf.parse(newSentAt)?.time ?: 0L
                Log.d(TAG, "Parsed timestamp (ms): $parsedTime")
                parsedTime
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsedTime = sdf.parse(normalizedSentAt)?.time ?: 0L
                Log.d(TAG, "Parsed timestamp (ms) without fractional seconds: $parsedTime")
                parsedTime
            }
        } catch (e: ParseException) {
            Log.e(TAG, "Error parsing sentAt: $normalizedSentAt", e)
            0L
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: Starting polling loop.")
        serviceScope.launch {
            while (isActive) {
                Log.d(TAG, "Polling iteration started.")
                Log.d(TAG, "Current lastNotificationTimestamps: $lastNotificationTimestamps")

                // Fetch notifications from Supabase.
                val fetchedNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(
                    this@AdminNotificationPollingService, supabaseService
                )
                Log.d(TAG, "Fetched ${fetchedNotifications.size} notifications from Supabase.")

                if (fetchedNotifications.isNotEmpty()) {
                    // Group notifications by the user's email.
                    val notificationsByEmail = fetchedNotifications.groupBy { it.user_email }

                    notificationsByEmail.forEach { (email, notificationsForEmail) ->
                        // Retrieve the last processed timestamp for this email (or 0 if not processed before).
                        val lastTimestamp = lastNotificationTimestamps[email] ?: 0L

                        // Filter out only the new notifications for this email.
                        val newNotifications = notificationsForEmail.filter {
                            parseSentAt(it.sent_at) > lastTimestamp
                        }

                        if (newNotifications.isNotEmpty()) {
                            Log.d(TAG, "New notifications detected for $email. Processing ${newNotifications.size} notifications.")
                            newNotifications.forEach { newNotification ->
                                Log.d(TAG, "Sending notification: Title='${newNotification.title}', Email='${newNotification.user_email}', Body='${newNotification.body}'")
                                NotificationHelper(this@AdminNotificationPollingService)
                                    .sendHighPriorityNotification(
                                        newNotification.user_email,
                                        newNotification.title,
                                        AdminDashboard::class.java
                                    )
                            }
                            // Update the latest timestamp for this email.
                            val maxTimestamp = newNotifications.maxOf { parseSentAt(it.sent_at) }
                            lastNotificationTimestamps[email] = maxTimestamp
                            Log.d(TAG, "Updated lastNotificationTimestamps for $email to: $maxTimestamp")
                        } else {
                            Log.d(TAG, "No new notifications for $email. Last processed timestamp: $lastTimestamp")
                        }
                    }
                } else {
                    Log.d(TAG, "No notifications fetched from Supabase during this poll iteration.")
                }
                Log.d(TAG, "Polling iteration complete. Waiting for next poll cycle.")
                delay(5_000) // Poll every 5 seconds.
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy: Cancelling polling coroutine and cleaning up.")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
