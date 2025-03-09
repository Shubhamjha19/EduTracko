package com.example.safeedutrack

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ParentNotifications : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationsAdapter: NotificationsAdapter

    // Initialize Retrofit (adjust the baseUrl as needed)
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co") // Replace with your Supabase project URL
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val supabaseService: SupabaseService = retrofit.create(SupabaseService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_notifications)

        // Initialize the RecyclerView and set its layout manager and adapter
        recyclerView = findViewById(R.id.recyclerViewNotifications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        notificationsAdapter = NotificationsAdapter(emptyList())
        recyclerView.adapter = notificationsAdapter

        val authHelper = AuthHelper(this)


        val sharedPrefs = getSharedPreferences("ParentPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPrefs.getString("childEmail", null)

        // Launch a coroutine to poll for new notifications periodically.
        lifecycleScope.launch {
            while (true) {
                Log.d("ParentNotifications", "Polling for notifications...")
                val fetchedNotifications = authHelper.fetchSpecificUserNotificationsFromSupabase(
                    this@ParentNotifications, supabaseService,userEmail
                )
                if (fetchedNotifications != null) {
                    Log.d("ParentNotifications", "Fetched ${fetchedNotifications.size} notifications from Supabase.")
                    // Log details for each notification
                    fetchedNotifications.forEach { notification ->
                        Log.d(
                            "ParentNotifications",
                            "Notification details: Title='${notification.title}', Body='${notification.body}', SentAt='${notification.sent_at}'"
                        )
                    }
                    // Update the adapter with sorted notifications (newest first)
                    notificationsAdapter.updateData(fetchedNotifications)
                    // Scroll to the top to display new notifications immediately.
                    recyclerView.smoothScrollToPosition(0)
                } else {
                    Log.d("ParentNotifications", "No notifications fetched from Supabase.")
                    Toast.makeText(this@ParentNotifications, "No notifications found.", Toast.LENGTH_SHORT).show()
                }
                // Wait for 10 seconds before checking again.
                delay(10_000)
            }
        }
    }
}

class NotificationsAdapter(
    private var notifications: List<SupabaseService.NotificationData>
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: android.widget.TextView = itemView.findViewById(R.id.parentNotificationTitle)
        val tvMessage: android.widget.TextView = itemView.findViewById(R.id.parentNotificationMessage)
        val tvDate: android.widget.TextView = itemView.findViewById(R.id.parentNotificationDate)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NotificationViewHolder {
        // Inflate the item layout (make sure the layout file is named notification_item.xml)
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.tvTitle.text = notification.title
        holder.tvMessage.text = notification.body
        holder.tvDate.text = notification.sent_at
        // Optional: Log when binding each item (uncomment if needed)
        // Log.d("NotificationsAdapter", "Binding notification at position $position: ${notification.title}")
    }

    override fun getItemCount(): Int = notifications.size

    /**
     * Update the adapter data, sorting notifications so the newest are on top.
     */
    @Suppress("NotifyDataSetChanged")
    fun updateData(newNotifications: List<SupabaseService.NotificationData>) {
        Log.d("NotificationsAdapter", "Updating data with ${newNotifications.size} notifications.")
        notifications = newNotifications.sortedByDescending {
            it.sent_at.toLongOrNull() ?: 0L
        }
        notifyDataSetChanged()
    }
}
