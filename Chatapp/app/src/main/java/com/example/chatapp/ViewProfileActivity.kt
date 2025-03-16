package com.example.chatapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.databinding.ActivityViewProfileBinding
import com.example.chatapp.model.User
import com.example.chatapp.util.ImageUploadUtil
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// Activity for viewing another user's profile information
class ViewProfileActivity : AppCompatActivity() {
    
    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityViewProfileBinding
    
    // ID of the user whose profile is being viewed
    private var userId: String = ""
    
    // Tag for logging messages from this activity
    private val tag = "ViewProfileActivity"
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        // Initialize view binding to access UI elements
        binding = ActivityViewProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar with back button and title
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "View Profile"
        
        // Extract user ID from intent extras
        userId = intent.getStringExtra("USER_ID") ?: ""
        
        // Validate user ID is present, otherwise exit the activity
        if (userId.isEmpty()) {
            // Show error message if user ID is missing
            Toast.makeText(this, "Error: User information missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Load user profile data from Firebase
        loadUserProfile()
    }
    
    // Fetches user profile data from Firebase Realtime Database
    private fun loadUserProfile() {
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Query the specific user's data node
        database.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                // Called when user data is successfully retrieved from Firebase
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Convert Firebase data to User object
                    val user = snapshot.getValue(User::class.java)
                    
                    // Use safe call with let to handle non-null User
                    user?.let {
                        // Display user information in UI
                        displayUserInfo(it)
                    } ?: run {
                        // Handle case where user data couldn't be parsed or doesn't exist
                        Toast.makeText(this@ViewProfileActivity, "User not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                
                // Called when database operation is cancelled or fails
                override fun onCancelled(error: DatabaseError) {
                    // Log error for debugging
                    Log.e(tag, "Error loading user profile: ${error.message}")
                    
                    // Show error message to user
                    Toast.makeText(this@ViewProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
    }
    
    // Populates the UI with user information
    @SuppressLint("SetTextI18n")
    private fun displayUserInfo(user: User) {
        // Set username in TextView
        binding.tvUsername.text = user.username
        
        // Set phone number in TextView
        binding.tvPhoneNumber.text = user.phoneNumber
        
        // Set status message in TextView
        binding.tvStatus.text = user.status
        
        // Set last seen time with relative formatting
        if (user.lastSeen > 0) {
            // Convert timestamp to human-readable relative time
            val timeAgo = DateUtils.getRelativeTimeSpanString(
                user.lastSeen,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            binding.tvLastSeen.text = timeAgo
        } else {
            // Handle case where last seen timestamp is not available
            binding.tvLastSeen.text = "Not available"
        }
        
        // Load profile image from URL into ImageView if URL exists
        if (user.profileImageUrl.isNotEmpty()) {
            ImageUploadUtil.loadImage(this, user.profileImageUrl, binding.ivProfilePic)
        }
    }
    
    // Handles action bar item clicks, specifically the back button
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle back button press by finishing activity
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 