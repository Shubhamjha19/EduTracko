package com.example.safeedutrack

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.safeedutrack.databinding.ActivityParentDashboardBinding
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.squareup.picasso.Picasso
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

// Data class representing a child's location.
data class ChildLocation(val latitude: Double, val longitude: Double)

/**
 * ParentDashboard:
 * - Displays user info and a map.
 * - Continuously updates the child's location on the map.
 * - Handles navigation drawer and bottom navigation.
 */
class ParentDashboard : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityParentDashboardBinding

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var googleMap: GoogleMap
    private lateinit var authHelper: AuthHelper
    private lateinit var supabaseService: SupabaseService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a Retrofit instance for Supabase API calls.
        val retrofit = Retrofit.Builder()
            .baseUrl("https://yoururl.supabase.co")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        supabaseService = retrofit.create(SupabaseService::class.java)
        authHelper = AuthHelper(this)

        // Start the notification polling service.
        Log.d("ParentDashboard", "Starting NotificationPollingService")
        val serviceIntent = Intent(this, NotificationPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        binding = ActivityParentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize views.
        bottomNavigationView = findViewById(R.id.parentBottomNavigationView)
        drawerLayout = findViewById(R.id.parentDrawerLayout)
        toolbar = findViewById(R.id.parentMaterialToolbar)
        navView = findViewById(R.id.parentNavView)

        // Set up Bottom Navigation.
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, ParentDashboard::class.java))
                    true
                }
                R.id.alerts -> {
                    startActivity(Intent(this, ParentNotifications::class.java))
                    true
                }
                R.id.location -> {
                    startActivity(Intent(this, ParentMap::class.java))
                    true
                }
                else -> false
            }
        }

        // Set up the map fragment (child fragment inside parentMapView).
        val mapFragment = supportFragmentManager.findFragmentById(R.id.parentMapImage) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Update notification-related TextViews.
        val parentStudentLocationStatus = findViewById<android.widget.TextView>(R.id.parentStudentLocationStatus)
        val parentActivityLog = findViewById<android.widget.TextView>(R.id.parentActivityLog)
        val sharedPrefs = getSharedPreferences("ParentPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPrefs.getString("childEmail", null)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            Log.d("ParentDashboard", "Fetching specific user notifications for dashboard update...")
            val notifications = authHelper.fetchSpecificUserNotificationsFromSupabase(this@ParentDashboard, supabaseService, userEmail)
            Log.d("ParentDashboard", "Fetched notifications: $notifications")
            withContext(Dispatchers.Main) {
                if (notifications != null && notifications.isNotEmpty()) {
                    val title = notifications[0].title
                    val date = notifications[0].sent_at
                    Log.d("ParentDashboard", "Updating dashboard with: Title='$title', Date='$date'")
                    parentStudentLocationStatus.text = "Child Status: $title"
                    parentActivityLog.text = "Child's Last Activity: $title at $date"
                } else {
                    Log.d("ParentDashboard", "No notifications found for dashboard update.")
                    parentStudentLocationStatus.text = "Child Status: No notifications"
                    parentActivityLog.text = "Child's Last Activity: No notifications"
                }
            }
        }

        // Update user info in the navigation header.
        updateUserInfoInNavHeader()
        // Set up the navigation drawer.
        setupNavigationDrawer()
    }

    /**
     * Called when the map is ready.
     * Launches a coroutine that continuously fetches and updates the child's location.
     */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // Start continuous updates of the child's location.
        lifecycleScope.launch(Dispatchers.IO) {
            val sharedPrefs = getSharedPreferences("ParentPrefs", Context.MODE_PRIVATE)
            val childEmail = sharedPrefs.getString("childEmail", null)
            if (childEmail.isNullOrEmpty()) {
                Log.e("ParentDashboard", "childEmail is null in ParentPrefs.")
                return@launch
            }
            while (isActive) {
                Log.d("ParentDashboard", "Fetching latest child location for email: $childEmail")
                val childLocation: SupabaseService.UserLocation? = authHelper.fetchLatestchildLocationFromSupabase(this@ParentDashboard, supabaseService)
                if (childLocation != null) {
                    Log.d("ParentDashboard", "Fetched child location: $childLocation")
                    withContext(Dispatchers.Main) {
                        val latLng = LatLng(childLocation.latitude, childLocation.longitude)
                        googleMap.clear()
                        googleMap.addMarker(MarkerOptions().position(latLng).title("Child Location"))
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                } else {
                    Log.d("ParentDashboard", "No child location data fetched.")
                }
                delay(5000) // Update every 5 seconds.
            }
        }
    }

    private fun updateUserInfoInNavHeader() {
        val sharedPrefs = getSharedPreferences("ParentPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPrefs.getString("childEmail", null)
        if (userEmail.isNullOrEmpty()) {
            Log.e("ParentDashboard", "User email not found in ParentPrefs.")
            return
        }
        Log.d("ParentDashboard", "Updating nav header for user: $userEmail")
        val userPhotoImageView = findViewById<android.widget.ImageView>(R.id.parentUserPhoto)
        val userNameTextView = findViewById<android.widget.TextView>(R.id.parentUserName)
        val userClassTextView = findViewById<android.widget.TextView>(R.id.parentUserClass)
        val userDeptTextView = findViewById<android.widget.TextView>(R.id.parentUserDept)
        val userRollNoTextView = findViewById<android.widget.TextView>(R.id.parentUserRollNo)
        lifecycleScope.launch(Dispatchers.IO) {
            val imageUrl = authHelper.fetchProfileImageUri(userEmail)
            withContext(Dispatchers.Main) {
                if (!imageUrl.isNullOrEmpty()) {
                    Log.d("ParentDashboard", "Fetched profile image URI: $imageUrl")
                    Picasso.get().load(imageUrl).into(userPhotoImageView)
                } else {
                    Log.d("ParentDashboard", "No profile image URI fetched; retaining default image.")
                }
            }
            val userDetails = authHelper.retrieveStudentDetails(this@ParentDashboard, userEmail)
            withContext(Dispatchers.Main) {
                if (userDetails != null) {
                    Log.d("ParentDashboard", "User details retrieved: $userDetails")
                    userNameTextView.text = "Name: ${userDetails.name}"
                    userClassTextView.text = "Class: ${userDetails.class_name}"
                    userDeptTextView.text = "Dept: ${userDetails.department}"
                    userRollNoTextView.text = "Roll No: ${userDetails.roll_number}"
                } else {
                    Log.d("ParentDashboard", "No user details found for email: $userEmail")
                }
            }
        }
    }

    private fun setupNavigationDrawer() {
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.parentLogoutMenu -> {
                    val parentPrefs = getSharedPreferences("ParentPrefs", MODE_PRIVATE)
                    parentPrefs.edit().clear().apply()
                    stopService(Intent(this, NotificationPollingService::class.java))
                    val intent = Intent(this, WelcomeScreen::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}

/**
 * NotificationPollingService:
 * Polls Supabase for new notifications and sends high-priority notifications.
 */
class NotificationPollingService : Service() {

    private val TAG = "NotificationPollingService"
    // Track the latest notification timestamp to avoid sending duplicate notifications.
    private var lastNotificationTimestamp: Long = 0L

    // Coroutine scope for polling.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var supabaseService: SupabaseService
    private lateinit var authHelper: AuthHelper

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created. Initializing Retrofit and AuthHelper.")
        val retrofit = Retrofit.Builder()
            .baseUrl("https://yoururl.supabase.co")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        supabaseService = retrofit.create(SupabaseService::class.java)
        authHelper = AuthHelper(this)
        startForeground(1, createForegroundNotification())
        Log.d(TAG, "Foreground service started with persistent notification.")
    }

    private fun createForegroundNotification() =
        NotificationCompat.Builder(this, "geofence_notifications")
            .setContentTitle("Edutracko")
            .setContentText("Monitoring for new notifications...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

    /**
     * Parses the ISO 8601 timestamp string into a Unix timestamp (ms).
     * Note: This version expects a "T" between date and time.
     */
    private fun parseSentAt(sentAt: String): Long {
        return try {
            if (sentAt.contains(".")) {
                val parts = sentAt.split(".")
                val datePart = parts[0]
                var fractionPart = parts[1].filter { it.isDigit() }
                val msPart = when {
                    fractionPart.length > 3 -> fractionPart.substring(0, 3)
                    fractionPart.length < 3 -> fractionPart.padEnd(3, '0')
                    else -> fractionPart
                }
                val newSentAt = "$datePart.$msPart"
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(newSentAt)?.time ?: 0L
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(sentAt)?.time ?: 0L
            }
        } catch (e: ParseException) {
            Log.e(TAG, "Error parsing sentAt: $sentAt", e)
            0L
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Starting polling loop.")
        serviceScope.launch {
            while (isActive) {
                val sharedPrefs = getSharedPreferences("ParentPrefs", Context.MODE_PRIVATE)
                val userEmail = sharedPrefs.getString("childEmail", null)
                Log.d(TAG, "Polling for notifications from Supabase...")
                val fetchedNotifications = authHelper.fetchSpecificUserNotificationsFromSupabase(
                    this@NotificationPollingService, supabaseService, userEmail
                )
                if (fetchedNotifications != null && fetchedNotifications.isNotEmpty()) {
                    Log.d(TAG, "Fetched ${fetchedNotifications.size} notifications from Supabase.")
                    val newestTimestamp = parseSentAt(fetchedNotifications[0].sent_at)
                    Log.d(TAG, "Newest timestamp parsed as: $newestTimestamp")
                    if (newestTimestamp > lastNotificationTimestamp) {
                        Log.d(TAG, "New notification detected. LastTimestamp: $lastNotificationTimestamp, NewestTimestamp: $newestTimestamp")
                        fetchedNotifications.filter {
                            parseSentAt(it.sent_at) > lastNotificationTimestamp
                        }.forEach { newNotification ->
                            Log.d(TAG, "Sending notification for: Title='${newNotification.title}', Body='${newNotification.body}'")
                            NotificationHelper(this@NotificationPollingService)
                                .sendHighPriorityNotification(
                                    newNotification.title,
                                    newNotification.body,
                                    ParentNotifications::class.java
                                )
                        }
                        lastNotificationTimestamp = newestTimestamp
                        Log.d(TAG, "Updated lastNotificationTimestamp to: $lastNotificationTimestamp")
                    } else {
                        Log.d(TAG, "No new notifications found. LastNotificationTimestamp remains: $lastNotificationTimestamp")
                    }
                } else {
                    Log.d(TAG, "No notifications fetched from Supabase (null or empty result).")
                }
                delay(5000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service is being destroyed. Cancelling polling coroutine.")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
