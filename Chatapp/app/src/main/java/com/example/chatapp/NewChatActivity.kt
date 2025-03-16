package com.example.chatapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatapp.adapter.UserAdapter
import com.example.chatapp.databinding.ActivityNewChatBinding
import com.example.chatapp.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import java.util.Locale

// Activity for starting a new chat with another user
class NewChatActivity : AppCompatActivity() {
    
    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityNewChatBinding
    
    // Adapter for the RecyclerView that displays users
    private lateinit var userAdapter: UserAdapter
    
    // List to store all users from the database
    private val usersList = mutableListOf<User>()
    
    // List to store filtered users
    private val filteredList = mutableListOf<User>()
    
    // Tag for logging messages from this activity
    private val tag = "NewChatActivity"
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        // Initialize view binding to access UI elements
        binding = ActivityNewChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up the toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Set up RecyclerView with click and long press actions
        userAdapter = UserAdapter(
            filteredList,
            onUserClicked = { user ->

                // When user is clicked, check if a chat exists and navigate
                checkExistingChatAndNavigate(user)
            },
            onUserLongClicked = { user ->

                // When user is long-pressed, view their profile
                viewUserProfile(user)
            }
        )
        
        // Configure the RecyclerView with the adapter and layout manager
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@NewChatActivity)
            adapter = userAdapter
        }
        
        // Set up device contacts button to find friends
        binding.btnDeviceContacts.setOnClickListener {
            startActivity(Intent(this, DeviceContactsActivity::class.java))
        }
        
        // Set up search functionality with query listener
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Handle search submission
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            
            // Handle text changes in search view, filtering results in real-time
            override fun onQueryTextChange(newText: String?): Boolean {
                filterUsers(newText)
                return true
            }
        })
        
        // Load all app users from Firebase
        loadUsers()
    }
    
    // Navigates to the user profile screen when a user is long-pressed
    private fun viewUserProfile(user: User) {
        // Create intent for the profile activity
        val intent = Intent(this, ViewProfileActivity::class.java)
        intent.putExtra("USER_ID", user.uid)
        startActivity(intent)
    }
    
    // Checks if a chat already exists with the selected user before navigating
    private fun checkExistingChatAndNavigate(user: User) {
        // Get current user or return if not authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance().reference
        
        // Show loading indicator during the check
        binding.progressBar.visibility = View.VISIBLE
        
        // Get the current user's phone number to compare
        database.child("users").child(currentUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(currentUserSnapshot: DataSnapshot) {
                    // Get the current user data
                    val currentUserData = currentUserSnapshot.getValue(User::class.java)
                    
                    // Check if user is trying to chat with themselves by comparing phone numbers
                    if (currentUserData != null && 
                        user.phoneNumber.isNotEmpty() && 
                        currentUserData.phoneNumber.isNotEmpty() && 
                        user.phoneNumber == currentUserData.phoneNumber) {
                        
                        // Hide loading indicator
                        binding.progressBar.visibility = View.GONE
                        
                        // Show error message - can't chat with yourself
                        Toast.makeText(
                            this@NewChatActivity,
                            "Failed to create a new chat: Cannot chat with yourself",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    
                    // Continue with existing flow to check for an existing chat
                    checkForExistingChat(currentUser.uid, user)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Hide loading indicator
                    binding.progressBar.visibility = View.GONE
                    
                    // Log and show error message
                    Log.e(tag, "Error fetching current user data: ${error.message}")
                    Toast.makeText(
                        this@NewChatActivity,
                        "Failed to create a new chat: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
    
    // Checks if a chat already exists between the current user and selected user
    private fun checkForExistingChat(currentUserId: String, user: User) {
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Ensure loading indicator is visible
        if (binding.progressBar.visibility != View.VISIBLE) {
            binding.progressBar.visibility = View.VISIBLE
        }
        
        // Log the check for debugging
        Log.d(tag, "Checking for existing chat between current user $currentUserId and ${user.uid}")
        
        // Query the database for existing chats of the current user
        database.child("user-chats").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Variable to store existing chat ID if found
                    var existingChatId: String? = null
                    
                    // Iterate through all chats to find one with this user
                    for (chatSnapshot in snapshot.children) {
                        // Get participants list for this chat
                        val participants = chatSnapshot.child("participants").getValue(object : GenericTypeIndicator<List<String>>() {})
                        
                        // If participants include selected user, we found a match
                        if (participants != null && participants.contains(user.uid)) {
                            existingChatId = chatSnapshot.child("chatId").getValue(String::class.java)
                            Log.d(tag, "Found existing chat with ID: $existingChatId")
                            break
                        }
                    }
                    
                    // Hide loading indicator
                    binding.progressBar.visibility = View.GONE
                    
                    if (existingChatId != null) {
                        // Chat exists, navigate to it
                        Log.d(tag, "Redirecting to existing chat with ${user.username}")
                        Toast.makeText(this@NewChatActivity, "Opening existing chat with ${user.username}", Toast.LENGTH_SHORT).show()
                        
                        // Create intent with chat ID and receiver information
                        val intent = Intent(this@NewChatActivity, ChatActivity::class.java).apply {
                            putExtra("CHAT_ID", existingChatId)
                            putExtra("RECEIVER_ID", user.uid)
                            putExtra("RECEIVER_NAME", user.username)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // No existing chat, create a new one
                        Log.d(tag, "Creating new chat with ${user.username}")
                        
                        // Create intent with just receiver information (ChatActivity will create chat)
                        val intent = Intent(this@NewChatActivity, ChatActivity::class.java).apply {
                            putExtra("RECEIVER_ID", user.uid)
                            putExtra("RECEIVER_NAME", user.username)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Hide loading indicator
                    binding.progressBar.visibility = View.GONE
                    
                    // Log and show error
                    Log.e(tag, "Error checking for existing chat: ${error.message}")
                    Toast.makeText(
                        this@NewChatActivity,
                        "Failed to create a new chat: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
    
    // Loads all app users from Firebase database
    private fun loadUsers() {
        // Get current user or return if not authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        Log.d(tag, "Loading users, current user ID: ${currentUser.uid}")
        
        // Show loading state
        binding.rvUsers.visibility = View.GONE
        binding.tvNoUsers.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Query the users node for all users
        database.child("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Clear the existing users list
                    usersList.clear()
                    
                    Log.d(tag, "Users snapshot received, children count: ${snapshot.childrenCount}")
                    
                    // Show UI elements
                    binding.rvUsers.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    
                    // If no users found, show empty message
                    if (snapshot.childrenCount == 0L) {
                        showNoUsersMessage()
                        return
                    }
                    
                    // Process each user from the database
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)
                        Log.d(tag, "Processing user: ${userSnapshot.key}, value: $user")
                        
                        user?.let {
                            // Don't add current user to the list
                            if (it.uid != currentUser.uid) {
                                usersList.add(it)
                                Log.d(tag, "Added user to list: ${it.username}")
                            }
                        }
                    }
                    
                    // Initialize filtered list with all users
                    filteredList.clear()
                    filteredList.addAll(usersList)
                    userAdapter.notifyDataSetChanged()
                    
                    Log.d(tag, "Final users list size: ${usersList.size}")
                    
                    // Show empty message if no users found (excluding current user)
                    if (usersList.isEmpty()) {
                        showNoUsersMessage()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Log and show error message
                    Log.e(tag, "Error loading users: ${error.message}")
                    Toast.makeText(
                        this@NewChatActivity,
                        "Failed to load contacts: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Show UI with error
                    binding.rvUsers.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    showNoUsersMessage()
                }
            })
    }
    
    // Shows a message when no users are found
    @SuppressLint("SetTextI18n")
    private fun showNoUsersMessage() {
        // Make the no users message visible
        binding.tvNoUsers.visibility = View.VISIBLE
        binding.tvNoUsers.text = "No contacts found"
    }
    
    // Filters the users list based on a search query
    @SuppressLint("NotifyDataSetChanged")
    private fun filterUsers(query: String?) {
        // Clear the current filtered list
        filteredList.clear()
        
        if (query.isNullOrEmpty()) {
            // If no search query, show all users
            filteredList.addAll(usersList)
        } else {
            // Convert query to lowercase for case-insensitive search
            val searchQuery = query.lowercase(Locale.getDefault())
            
            // Filter users whose username contains the query
            usersList.filter {
                it.username.lowercase(Locale.getDefault()).contains(searchQuery)
            }.forEach { filteredList.add(it) }
        }
        
        // Notify adapter that data has changed
        userAdapter.notifyDataSetChanged()
        
        // Update UI based on whether any matching users were found
        if (filteredList.isEmpty()) {
            binding.tvNoUsers.visibility = View.VISIBLE
            binding.tvNoUsers.text = if (query.isNullOrEmpty()) "No contacts found" else "No contacts found matching \"$query\""
        } else {
            binding.tvNoUsers.visibility = View.GONE
        }
    }
    
    // Handles selection of menu items in the toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle back button press
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 