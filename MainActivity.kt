package com.example.safeedutrack

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://yoururl.supabase.co") // Replace with your Supabase base URL.
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val supabaseService = retrofit.create(SupabaseService::class.java)

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var stateReceiver: StateReceiver
    private lateinit var authHelper: AuthHelper

    // Map and location-related variables
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerStateReceiver()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.materialToolbar)
        navView = findViewById(R.id.nav_view)

        // Initialize AuthHelper instance
        authHelper = AuthHelper(this)

        // Set up bottom navigation.
        // When "location" is clicked, update the integrated map and redirect to MapsActivity.
        bottomNavigationView.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.alerts -> {
                    startActivity(Intent(this, ParentNotifications::class.java))
                    true
                }
                R.id.location -> {
                    updateLocationUI()
                    startActivity(Intent(this, MapsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        setupNavigationDrawer()

        // --- NEW: Update User Info in Navigation Header ---
        updateUserInfoInNavHeader()

        // NEW: Fetch and display current user notification title in studentLocationStatus
        val studentLocationStatus = findViewById<TextView>(R.id.studentLocationStatus)
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "Fetching specific user notifications from Supabase...")
            // Call your method to fetch specific notifications for the current user.
            // Adjust parameters as required by your AuthHelper implementation.
            val notifications = authHelper.fetchSpecificUserNotificationsFromSupabase(this@MainActivity, supabaseService)
            Log.d("MainActivity", "Fetched notifications: $notifications")
            withContext(Dispatchers.Main) {
                if (notifications != null && notifications.isNotEmpty()) {
                    val status = notifications[0].title
                    Log.d("MainActivity", "Displaying notification status: $status")
                    studentLocationStatus.text = "Your Status: $status"
                } else {
                    Log.d("MainActivity", "No notifications fetched")
                    studentLocationStatus.text = "Your Status: Not found"
                }
            }
        }

        val activityLog = findViewById<TextView>(R.id.activityLog)
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "Fetching specific user notifications from Supabase for activityLog...")
            // Fetch specific notifications for the current user.
            val notifications = authHelper.fetchSpecificUserNotificationsFromSupabase(this@MainActivity, supabaseService)
            Log.d("MainActivity", "Fetched notifications for activityLog: $notifications")
            withContext(Dispatchers.Main) {
                if (notifications != null && notifications.isNotEmpty()) {
                    val title = notifications[0].title
                    val date = notifications[0].sent_at  // Ensure your notification object has a 'date' field
                    Log.d("MainActivity", "Displaying last activity: $title, $date")
                    activityLog.text = "Your Last Activity: $title at $date"
                } else {
                    Log.d("MainActivity", "No notifications fetched for activityLog")
                    activityLog.text = "Your Last Activity: Not found"
                }
            }
        }



        // Initialize FusedLocationProviderClient for location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Set up the map fragment (ensure your XML layout has a fragment with id "mapVie")
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapVie) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Check location permissions; if not granted, request them.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
            return
        }
        // Enable the "My Location" layer on the map.
        mMap.isMyLocationEnabled = true
        updateLocationUI()
    }

    private fun updateLocationUI() {
        // Ensure location permissions are granted.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                // Clear any existing markers.
                mMap.clear()
                // Create a marker with your custom icon using the vector-to-bitmap helper.
                val markerOptions = MarkerOptions()
                    .position(currentLatLng)
                    .title("You are here")
                    .icon(bitmapDescriptorFromVector(this, R.drawable.user_moving_icon))
                mMap.addMarker(markerOptions)
                // Animate the camera to the user's current location with a zoom level of 17.
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
            }
        }
    }

    // Helper function to convert vector drawable to BitmapDescriptor
    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
            ?: throw IllegalArgumentException("Resource not found")
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // Handle the permission request result for location permissions.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mMap.isMyLocationEnabled = true
                updateLocationUI()
            }
        }
    }

    /**
     * Updates the user photo and info (name, class, dept, roll no) in the navigation header.
     */
    private fun updateUserInfoInNavHeader() {
        // Get SharedPreferences for Login.
        val sharedPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPrefs.getString("userEmail", null)
        if (userEmail.isNullOrEmpty()) {
            Log.e("MainActivity", "User email not found in SharedPreferences.")
            return
        }
        Log.d("MainActivity", "Updating nav header for user: $userEmail")

        // Find views from layout (in your nav header layout or main layout's user info section).
        val userPhotoImageView = findViewById<android.widget.ImageView>(R.id.userPhoto)
        val userNameTextView = findViewById<android.widget.TextView>(R.id.userName)
        val userClassTextView = findViewById<android.widget.TextView>(R.id.userClass)
        val userDeptTextView = findViewById<android.widget.TextView>(R.id.userDept)
        val userRollNoTextView = findViewById<android.widget.TextView>(R.id.userRollNo)

        // Launch a coroutine to fetch data from Supabase.
        lifecycleScope.launch(Dispatchers.IO) {
            // Fetch the profile image URI.
            val imageUrl = authHelper.fetchProfileImageUri(userEmail)
            withContext(Dispatchers.Main) {
                if (!imageUrl.isNullOrEmpty()) {
                    Log.d("MainActivity", "Fetched profile image URI: $imageUrl")
                    Picasso.get().load(imageUrl).into(userPhotoImageView)
                } else {
                    Log.d("MainActivity", "No profile image URI fetched; retaining default image.")
                }
            }

            // Fetch user details.
            val userDetails = authHelper.retrieveStudentDetails(this@MainActivity, userEmail)
            withContext(Dispatchers.Main) {
                if (userDetails != null) {
                    Log.d("MainActivity", "User details retrieved: $userDetails")
                    userNameTextView.text = "Name: ${userDetails.name}"
                    userClassTextView.text = "Class: ${userDetails.class_name}"
                    userDeptTextView.text = "Dept: ${userDetails.department}"
                    userRollNoTextView.text = "Roll No: ${userDetails.roll_number}"
                } else {
                    Log.d("MainActivity", "No user details found for email: $userEmail")
                }
            }
        }

    }

    private fun registerStateReceiver() {
        stateReceiver = StateReceiver(this)
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(stateReceiver, filter)
    }

    class StateReceiver(private val activity: MainActivity) : BroadcastReceiver() {

        private var lastBatteryNotificationTime: Long = 0 // Timestamp to throttle battery notifications.
        // Mutex to ensure sequential handling of notifications (except GPS, which uses its own mutex).
        private val supabaseLock = Mutex()
        private val gpsLock = Mutex()

        private val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://yoururl.supabase.co") // Replace with your Supabase project URL.
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        private val supabaseService: SupabaseService = retrofit.create(SupabaseService::class.java)

        override fun onReceive(context: Context, intent: Intent) {
            val TAG = "StateReceiver"
            val notificationHelper = NotificationHelper(context)
            // Shared preferences for device notifications.
            val devicePrefs = context.getSharedPreferences("DevicePrefs", Context.MODE_PRIVATE)
            val lastNotificationKey = "lastDeviceNotification"

            when (intent.action) {
                // ----- GPS (Providers Changed) -----
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    val isGpsEnabled = activity.isGpsEnabled()
                    val notificationTitle = if (isGpsEnabled) "GPS Enabled" else "GPS Disabled"
                    val notificationMessage = if (isGpsEnabled) "GPS is now enabled." else "Please enable GPS for proper functionality."

                    val currentDeviceNotification = "$notificationTitle|$notificationMessage"
                    val lastDeviceNotification = devicePrefs.getString(lastNotificationKey, null)
                    if (lastDeviceNotification != null && lastDeviceNotification == currentDeviceNotification) {
                        Log.d(TAG, "Duplicate device notification for '$notificationTitle' detected in DevicePrefs. Skipping sending notification!")
                    } else {
                        notificationHelper.sendHighPriorityNotification(notificationTitle, notificationMessage, MainActivity::class.java)
                        devicePrefs.edit().putString(lastNotificationKey, currentDeviceNotification).apply()
                    }

                    // Store GPS notification in Supabase.
                    CoroutineScope(Dispatchers.IO).launch {
                        if (!gpsLock.tryLock()) {
                            Log.d(TAG, "A GPS notification API call is already in progress. Terminating this one.")
                            return@launch
                        }
                        try {
                            val authHelper = AuthHelper(context)
                            val loginPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            val userEmail = loginPrefs.getString("userEmail", null)
                            if (userEmail.isNullOrEmpty()) {
                                Log.e(TAG, "❌ [GPS] User email not found in LoginPrefs. Skipping GPS notification storage!")
                                return@launch
                            }

                            val latestNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(context, supabaseService)
                            val latestNotification = latestNotifications.firstOrNull { it.user_email == userEmail }
                            if (latestNotification != null &&
                                latestNotification.title == notificationTitle &&
                                latestNotification.body == notificationMessage
                            ) {
                                Log.d(TAG, "⚠️ [GPS] Duplicate notification for '$notificationTitle' found for $userEmail in Supabase. Skipping storage!")
                                return@launch
                            }

                            val isSuccess = authHelper.storeUserNotification(
                                context,
                                supabaseService,
                                notificationTitle,
                                notificationMessage
                            )
                            if (isSuccess) {
                                Log.d(TAG, "✅ [GPS] Notification successfully stored in Supabase for $userEmail.")
                            } else {
                                Log.e(TAG, "❌ [GPS] Failed to store GPS notification in Supabase for $userEmail.")
                            }
                        } finally {
                            gpsLock.unlock()
                        }
                    }
                }

                // ----- Connectivity -----
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val isInternetOrWifiAvailable = activity.isInternetAvailable()
                    val notificationTitle = if (isInternetOrWifiAvailable) "Connected" else "No Internet"
                    val notificationMessage = if (isInternetOrWifiAvailable) "Internet or Wi-Fi is now available." else "Please check your Internet or Wi-Fi connection."

                    val currentDeviceNotification = "$notificationTitle|$notificationMessage"
                    val lastDeviceNotification = devicePrefs.getString(lastNotificationKey, null)
                    if (lastDeviceNotification != null && lastDeviceNotification == currentDeviceNotification) {
                        Log.d(TAG, "Duplicate device notification for '$notificationTitle' detected in DevicePrefs. Skipping sending notification!")
                    } else {
                        notificationHelper.sendHighPriorityNotification(notificationTitle, notificationMessage, MainActivity::class.java)
                        devicePrefs.edit().putString(lastNotificationKey, currentDeviceNotification).apply()
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        supabaseLock.withLock {
                            val authHelper = AuthHelper(context)
                            val loginPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            val userEmail = loginPrefs.getString("userEmail", null)
                            if (userEmail.isNullOrEmpty()) {
                                Log.e(TAG, "❌ [Connectivity] User email not found in LoginPrefs. Skipping connectivity notification storage!")
                                return@withLock
                            }

                            val latestNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(context, supabaseService)
                            val latestNotification = latestNotifications.firstOrNull { it.user_email == userEmail }
                            if (latestNotification != null &&
                                latestNotification.title == notificationTitle &&
                                latestNotification.body == notificationMessage
                            ) {
                                Log.d(TAG, "⚠️ [Connectivity] Duplicate notification for '$notificationTitle' found for $userEmail in Supabase. Skipping storage!")
                                return@withLock
                            }

                            val isSuccess = authHelper.storeUserNotification(
                                context,
                                supabaseService,
                                notificationTitle,
                                notificationMessage
                            )
                            if (isSuccess) {
                                Log.d(TAG, "✅ [Connectivity] Notification successfully stored in Supabase for $userEmail.")
                            } else {
                                Log.e(TAG, "❌ [Connectivity] Failed to store connectivity notification in Supabase for $userEmail.")
                            }
                        }
                    }
                }

                // ----- Battery Changed -----
                Intent.ACTION_BATTERY_CHANGED -> {
                    val batteryLevel = activity.getBatteryLevel(intent)
                    if (batteryLevel < 20) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBatteryNotificationTime >= 1 * 60 * 1000) { // Throttle to once per minute
                            val notificationTitle = "Low Battery"
                            val notificationMessage = "Your device battery is below 20%."

                            val currentDeviceNotification = "$notificationTitle|$notificationMessage"
                            val lastDeviceNotification = devicePrefs.getString(lastNotificationKey, null)
                            if (lastDeviceNotification != null && lastDeviceNotification == currentDeviceNotification) {
                                Log.d(TAG, "Duplicate device notification for '$notificationTitle' detected in DevicePrefs. Skipping sending notification!")
                            } else {
                                notificationHelper.sendHighPriorityNotification(notificationTitle, notificationMessage, MainActivity::class.java)
                                devicePrefs.edit().putString(lastNotificationKey, currentDeviceNotification).apply()
                                lastBatteryNotificationTime = currentTime // Update throttle timer
                            }

                            CoroutineScope(Dispatchers.IO).launch {
                                supabaseLock.withLock {
                                    val authHelper = AuthHelper(context)
                                    val loginPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                                    val userEmail = loginPrefs.getString("userEmail", null)
                                    if (userEmail.isNullOrEmpty()) {
                                        Log.e(TAG, "❌ [Battery] User email not found in LoginPrefs. Skipping battery notification storage!")
                                        return@withLock
                                    }

                                    val latestNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(context, supabaseService)
                                    val latestNotification = latestNotifications.firstOrNull { it.user_email == userEmail }
                                    if (latestNotification != null &&
                                        latestNotification.title == notificationTitle &&
                                        latestNotification.body == notificationMessage
                                    ) {
                                        Log.d(TAG, "⚠️ [Battery] Duplicate battery notification detected for $userEmail in Supabase. Skipping storage!")
                                        return@withLock
                                    }

                                    val isSuccess = authHelper.storeUserNotification(
                                        context,
                                        supabaseService,
                                        notificationTitle,
                                        notificationMessage
                                    )
                                    if (isSuccess) {
                                        Log.d(TAG, "✅ [Battery] Battery notification successfully stored in Supabase for $userEmail.")
                                    } else {
                                        Log.e(TAG, "❌ [Battery] Failed to store battery notification in Supabase for $userEmail.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun getBatteryLevel(intent: Intent): Int {
        return intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.profile -> {
                    startActivity(Intent(this, ProfileScreen::class.java))
                    true
                }
                R.id.notification -> {
                    startActivity(Intent(this, ParentNotifications::class.java))
                    true
                }
                R.id.about -> {
                    startActivity(Intent(this, AboutUs::class.java))
                    true
                }
                R.id.rateUs -> {
                    startActivity(Intent(this, RateUs::class.java))
                    true
                }
                R.id.contact -> {
                    startActivity(Intent(this, ContactUs::class.java))
                    true
                }
                R.id.share -> {
                    startActivity(Intent(this, ShareScreenActivity::class.java))
                    true
                }
                R.id.logoutmenu -> {
                    // Logout: Clear stored credentials and free resources.
                    val loginPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                    loginPrefs.edit().clear().apply()

                    // Optionally, clear other related preferences.
                    val devicePrefs = getSharedPreferences("DevicePrefs", Context.MODE_PRIVATE)
                    devicePrefs.edit().clear().apply()

                    // Unregister the state receiver if registered.
                    unregisterReceiver(stateReceiver)

                    // Redirect the user to the Login Screen.
                    val intent = Intent(this, WelcomeScreen::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.help -> {
                    startActivity(Intent(this, HelpScreen::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
