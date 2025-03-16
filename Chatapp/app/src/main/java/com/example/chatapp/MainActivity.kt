package com.example.chatapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.adapter.ChatAdapter
import com.example.chatapp.databinding.ActivityMainBinding
import com.example.chatapp.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.chatapp.util.ErrorHandler
import com.example.chatapp.util.RenderingHelper

// MainActivity serves as the home screen of the app, displaying all user's chat conversations
class MainActivity : AppCompatActivity() {
    
    // Tag for logging messages from this activity
    private val tag = "MainActivity"
    
    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityMainBinding
    
    // Adapter for the RecyclerView that displays chat conversations
    private lateinit var chatAdapter: ChatAdapter
    
    // Firebase Authentication instance for user authentication
    private lateinit var auth: FirebaseAuth
    
    // List to store all user's chat conversations
    private val chatsList = mutableListOf<Chat>()
    
    // Flag to track if a chat deletion is in progress
    private var isDeletingChat = false
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        // Apply rendering optimizations via the helper class
        RenderingHelper.setupOptimizedRendering(this)
        
        try {
            // Log the start of activity creation for debugging
            Log.d(tag, "Starting MainActivity onCreate")
            
            // Initialize view binding to access UI elements
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            try {
                // Set up the toolbar as the action bar
                setSupportActionBar(binding.toolbar)
            } catch (e: Exception) {
                // Log any errors during toolbar setup
                Log.e(tag, "Error setting up toolbar: ${e.message}", e)
            }
            
            try {
                // Initialize Firebase Authentication
                auth = FirebaseAuth.getInstance()
                
                // Check if user is logged in, if not redirect to login screen
                if (auth.currentUser == null) {
                    Log.d(tag, "User not logged in, redirecting to LoginActivity")
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    return
                }
                
                // Set up the RecyclerView for displaying chats
                setupRecyclerView()
                
                // Set up swipe-to-delete functionality for chats
                setupSwipeToDelete()
                
                // Set up listeners for button clicks
                setupClickListeners()
                
                // Set up search functionality
                setupSearchView()
                
                // Load user's chat conversations from Firebase
                loadChats()
                
            } catch (e: Exception) {
                // Log and handle critical errors during initialization
                Log.e(tag, "Critical error in MainActivity setup: ${e.message}", e)
                ErrorHandler.showErrorDialog(this, "Error", 
                    "There was a problem initializing the app. Please restart.")
            }
        } catch (e: Exception) {
            // Catch and handle any fatal errors that prevent normal activity creation
            Log.e(tag, "Fatal error in onCreate: ${e.message}", e)
            
            // Try to create a minimal UI and redirect to login as a fallback
            try {
                setContentView(R.layout.activity_login)
                Toast.makeText(this, "Critical error: ${e.message}", Toast.LENGTH_LONG).show()
                
                // Try to redirect to login after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } catch (e: Exception) {
                        // If we can't even redirect, log the unrecoverable error
                        Log.e(tag, "Cannot recover from error: ${e.message}", e)
                    }
                }, 3000)
            } catch (e2: Exception) {
                Log.e(tag, "Completely unrecoverable error: ${e2.message}", e2)
            }
        }
    }
    
    // Sets up the search functionality for finding chats
    private fun setupSearchView() {
        // Configure the search view with query text listeners
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            // Called when user submits the search query
            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Filter chats based on the submitted query
                filterChats(query)
                return true
            }
            
            // Called when the text in the search view changes
            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextChange(newText: String?): Boolean {
                // Filter chats in real-time as user types
                filterChats(newText)
                return true
            }
        })
        
        // Handle search view close event
        binding.searchView.setOnCloseListener {
            // Reset to showing all chats when search is closed
            if (chatsList.isNotEmpty()) {
                binding.tvEmptyState.visibility = View.GONE
            }
            loadChats()
            false
        }
    }
    
    // Called when the activity becomes visible to the user
    override fun onResume() {
        super.onResume()

        // Configure hardware acceleration for better UI performance
        configureHardwareAcceleration()
    }
    
    // Configures hardware acceleration for better rendering performance
    private fun configureHardwareAcceleration() {
        try {
            // Apply hardware acceleration to improve UI performance
            RenderingHelper.enableHardwareAcceleration(
                window.decorView,
                binding.rvChats
            )
        } catch (e: Exception) {
            // Log any errors in hardware acceleration configuration
            Log.e(tag, "Error configuring hardware acceleration: ${e.message}")
        }
    }
    
    // Sets up the RecyclerView for displaying chat conversations
    private fun setupRecyclerView() {
        // Initialize the chat adapter with click listener for chat selection
        chatAdapter = ChatAdapter(chatsList) { chat ->
            try {
                // Create intent to open the selected chat conversation
                val intent = Intent(this, ChatActivity::class.java).apply {
                    // Pass the chat ID to identify the conversation
                    putExtra("CHAT_ID", chat.chatId)
                    
                    // Find the ID of the other participant in the chat
                    val currentUserId = auth.currentUser?.uid ?: ""
                    val receiverId = chat.participants.find { it != currentUserId } ?: ""
                    putExtra("RECEIVER_ID", receiverId)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Handle errors opening chat screen
                Log.e(tag, "Error starting ChatActivity: ${e.message}")
                Toast.makeText(this, "Error opening chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Configure the RecyclerView with the adapter and layout manager
        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }
    }
    
    // Sets up swipe-to-delete functionality for chat conversations
    private fun setupSwipeToDelete() {
        // Get the delete icon from resources
        val deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)

        // Create a red background for the delete action
        val background = ColorDrawable(Color.RED)
        
        // Create a swipe handler for left swipe deletion
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            // We're not implementing drag and drop, so return false
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }
            
            // Handle swipe action to delete a chat
            @SuppressLint("NotifyDataSetChanged")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                
                // Validate position and check if deletion is already in progress
                if (position != RecyclerView.NO_POSITION && position < chatsList.size && !isDeletingChat) {
                    // Show confirmation dialog before deleting
                    confirmDeleteChat(position)
                } else {
                    // Invalid position or deletion in progress, refresh the item
                    chatAdapter.notifyDataSetChanged()
                }
            }
            
            // Disable swiping if a deletion is already in progress
            override fun isItemViewSwipeEnabled(): Boolean {
                return !isDeletingChat
            }
            
            // Custom drawing for the swipe action
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                
                // Draw the red delete background
                background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                background.draw(c)
                
                // Calculate position for the delete icon
                val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + deleteIcon.intrinsicHeight
                val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                val iconRight = itemView.right - iconMargin
                
                // Draw the delete icon on the canvas
                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                deleteIcon.draw(c)
                
                // Call super to handle the default swiping behavior
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        
        // Attach the swipe handler to the RecyclerView
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.rvChats)
    }
    
    // Shows a confirmation dialog before deleting a chat
    private fun confirmDeleteChat(position: Int) {
        // Build and show an alert dialog to confirm deletion
        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->

                // User confirmed deletion, proceed with deleting the chat
                deleteChat(position)
            }
            .setNegativeButton("Cancel") { _, _ ->

                // User canceled deletion, restore the item in the list
                chatAdapter.notifyItemChanged(position)
            }
            .setCancelable(false)
            .show()
    }
    
    // Deletes a chat conversation from the list and Firebase database
    @SuppressLint("NotifyDataSetChanged")
    private fun deleteChat(position: Int) {
        try {
            // Validate position is within bounds
            if (position < 0 || position >= chatsList.size) {
                Log.e(tag, "Invalid position: $position, list size: ${chatsList.size}")
                Toast.makeText(this, "Error: Invalid chat position", Toast.LENGTH_SHORT).show()
                chatAdapter.notifyDataSetChanged()
                return
            }
            
            // Get the chat at the specified position
            val chat = chatsList[position]
            val currentUserId = auth.currentUser?.uid ?: return
            
            // Set flag to prevent concurrent deletion operations
            isDeletingChat = true
            
            // Store the chat ID for database operations
            val chatIdToDelete = chat.chatId
            
            Log.d(tag, "Deleting chat: $chatIdToDelete at position $position, list size: ${chatsList.size}")
            
            // Create a copy of the chat for potential restoration if deletion fails
            val chatCopy = chat
            
            // Remove the chat from the local list
            try {
                chatsList.removeAt(position)
                chatAdapter.notifyItemRemoved(position)
            } catch (e: Exception) {
                // If local removal fails, log error and refresh adapter
                Log.e(tag, "Error removing chat from list: ${e.message}", e)
                chatAdapter.notifyDataSetChanged()
            }
            
            // Delete the chat from Firebase database
            FirebaseDatabase.getInstance().reference
                .child("user-chats")
                .child(currentUserId)
                .child(chatIdToDelete)
                .removeValue()
                .addOnSuccessListener {
                    // Deletion successful
                    Log.d(tag, "Chat deleted successfully")
                    Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->

                    // Deletion failed, try to restore the chat in the local list
                    Log.e(tag, "Error deleting chat: ${e.message}", e)
                    
                    // Attempt to restore the chat to its original position if possible
                    try {
                        if (position < chatsList.size) {
                            chatsList.add(position, chatCopy)
                        } else {
                            chatsList.add(chatCopy)
                        }
                        chatAdapter.notifyDataSetChanged()
                    } catch (e2: Exception) {
                        Log.e(tag, "Error restoring chat: ${e2.message}", e2)
                    }
                    Toast.makeText(this, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    // Reset deletion flag when operation completes
                    isDeletingChat = false
                }
        } catch (e: Exception) {
            // Handle any unexpected errors in the deletion process
            Log.e(tag, "Error in deleteChat: ${e.message}", e)
            isDeletingChat = false
            Toast.makeText(this, "Error deleting conversation", Toast.LENGTH_SHORT).show()

            // Refresh adapter to ensure UI is in a consistent state
            chatAdapter.notifyDataSetChanged()
        }
    }
    
    // Sets up click listeners for interactive elements in the main UI
    private fun setupClickListeners() {
        // Set up click listener for new chat button
        binding.fabNewChat.setOnClickListener {
            try {
                // Navigate to the NewChatActivity to start a new conversation
                startActivity(Intent(this, NewChatActivity::class.java))
            } catch (e: Exception) {
                // Handle any errors navigating to the new chat screen
                Log.e(tag, "Error starting NewChatActivity: ${e.message}")
                Toast.makeText(this, "Error opening new chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Loads the user's chat conversations from Firebase database
    private fun loadChats() {
        // Get the current user or show error if not authenticated
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(tag, "Cannot load chats: User not authenticated")
            Toast.makeText(this, "You need to be logged in to view chats", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check for internet connectivity and show appropriate message
        if (!isNetworkAvailable()) {
            // Inform user they're offline but try to load cached data
            Toast.makeText(this, "You're offline. Showing cached chats.", Toast.LENGTH_SHORT).show()
        }
        
        try {
            // Get reference to Firebase database
            val database = FirebaseDatabase.getInstance().reference
            
            // Set up a real-time listener for changes to user's chats
            database.child("user-chats").child(currentUser.uid)
                .addValueEventListener(object : ValueEventListener {
                    // Called whenever data at this location changes
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            // Handle special case during chat deletion
                            if (isDeletingChat) {
                                Log.d(tag, "Careful update during deletion in progress")
                                
                                // Create a temporary list to work with
                                val tempList = mutableListOf<Chat>()
                                val existingChatIds = chatsList.map { it.chatId }.toSet()
                                
                                // Process all chats from Firebase
                                for (chatSnapshot in snapshot.children) {
                                    val chat = chatSnapshot.getValue(Chat::class.java)
                                    if (chat != null && chat.chatId.isNotEmpty()) {
                                        tempList.add(chat)
                                    }
                                }
                                
                                // Find any new chats that aren't in our current list
                                val newChats = tempList.filter { it.chatId !in existingChatIds }
                                if (newChats.isNotEmpty()) {
                                    // Add only new chats to avoid interfering with deletion
                                    chatsList.addAll(newChats)
                                    // Sort by most recent message
                                    chatsList.sortByDescending { it.lastMessageTime }
                                    chatAdapter.notifyDataSetChanged()
                                }
                                return
                            }
                            
                            // Normal update when no deletion is in progress
                            chatsList.clear()
                            
                            // Process each chat from the database
                            for (chatSnapshot in snapshot.children) {
                                val chat = chatSnapshot.getValue(Chat::class.java)
                                chat?.let { 
                                    // Only add valid chats with non-empty IDs
                                    if (it.chatId.isNotEmpty()) {
                                        chatsList.add(it) 
                                    }
                                }
                            }
                            
                            // Sort chats by last message time
                            chatsList.sortByDescending { it.lastMessageTime }
                            chatAdapter.notifyDataSetChanged()
                            
                            // Update UI based on whether there are any chats
                            if (chatsList.isEmpty()) {
                                binding.tvEmptyState.visibility = View.VISIBLE
                            } else {
                                binding.tvEmptyState.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            // Handle errors processing the chat data
                            Log.e(tag, "Error processing chat data: ${e.message}")
                            Toast.makeText(this@MainActivity, "Error processing chats", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Called when the database operation is cancelled
                    override fun onCancelled(error: DatabaseError) {
                        // Handle database errors
                        Log.e(tag, "Database error: ${error.message}")
                        ErrorHandler.handleException(this@MainActivity, error.toException(), "Loading chats")
                    }
                })
        } catch (e: Exception) {
            // Handle any unexpected errors during chat loading
            Log.e(tag, "Exception in loadChats: ${e.message}")
            Toast.makeText(this, "Failed to load chats: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Checks if the device has an active network connection
    private fun isNetworkAvailable(): Boolean {
        // Get the connectivity manager service
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // Get the active network info
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        // Check if either WiFi or cellular data is available
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
    
    // Called to create the options menu in the toolbar
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu layout from resources
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    // Filters the chat list based on a search query
    @SuppressLint("NotifyDataSetChanged")
    private fun filterChats(query: String?) {
        try {
            // If query is empty, show all chats
            if (query.isNullOrEmpty()) {
                loadChats()
                return
            }
            
            // Show loading indicator during search
            binding.progressBar.visibility = View.VISIBLE
            
            // Convert query to lowercase for case-insensitive search
            val lowercaseQuery = query.lowercase(java.util.Locale.getDefault())
            val filteredList = mutableListOf<Chat>()
            val processedChats = mutableSetOf<String>()
            
            // Don't filter during deletion to avoid conflicts
            if (isDeletingChat) {
                binding.progressBar.visibility = View.GONE
                return
            }
            
            // If no chats exist, show appropriate empty state
            if (chatsList.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.setText(R.string.no_chats_to_search)
                return
            }
            
            // Create a copy of the original list to avoid modification issues
            val originalChats = ArrayList(chatsList)
            
            // Counter to track async database operations
            var pendingOperations = originalChats.size
            
            // Process each chat to filter based on the query
            for (chat in originalChats) {
                // Get the other user's ID from participants
                val currentUserId = auth.currentUser?.uid ?: ""
                val otherUserId = chat.participants.find { it != currentUserId } ?: ""
                
                // Check if last message contains the query
                val lastMessageMatch = chat.lastMessage.lowercase(java.util.Locale.getDefault())
                    .contains(lowercaseQuery)
                
                // If last message matches, add chat to filtered list immediately
                if (lastMessageMatch && !processedChats.contains(chat.chatId)) {
                    filteredList.add(chat)
                    processedChats.add(chat.chatId)
                }
                
                // If we have a valid receiver ID, check if their username matches
                if (otherUserId.isNotEmpty()) {
                    // Look up the other user's username from Firebase
                    FirebaseDatabase.getInstance().reference
                        .child("users").child(otherUserId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                // Decrement pending operations counter
                                pendingOperations--
                                
                                // Get username from snapshot
                                val username = snapshot.child("username").getValue(String::class.java) ?: ""
                                
                                // Check if username contains the query
                                val usernameMatch = username.lowercase(java.util.Locale.getDefault())
                                    .contains(lowercaseQuery)
                                
                                // If username matches and chat not already added, add it
                                if (usernameMatch && !processedChats.contains(chat.chatId)) {
                                    filteredList.add(chat)
                                    processedChats.add(chat.chatId)
                                    updateFilteredUI(filteredList)
                                }
                                
                                // Hide progress bar if all operations complete
                                if (pendingOperations <= 0) {
                                    binding.progressBar.visibility = View.GONE
                                }
                            }
                            
                            override fun onCancelled(error: DatabaseError) {
                                // Decrement counter even if operation fails
                                pendingOperations--
                                Log.e(tag, "Error searching username: ${error.message}")
                                
                                // Hide progress bar if all operations complete
                                if (pendingOperations <= 0) {
                                    binding.progressBar.visibility = View.GONE
                                }
                            }
                        })
                } else {
                    // No other user ID, decrement counter
                    pendingOperations--
                }
            }
            
            // Update UI with initial results from last message matches
            updateFilteredUI(filteredList)
            
            // Hide progress bar if no pending operations
            if (pendingOperations <= 0) {
                binding.progressBar.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Handle any errors during filtering
            binding.progressBar.visibility = View.GONE
            Log.e(tag, "Error filtering chats: ${e.message}", e)
            Toast.makeText(this, "Error during search: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Updates the UI to display filtered chat results
    @SuppressLint("NotifyDataSetChanged")
    private fun updateFilteredUI(filteredList: List<Chat>) {
        try {
            // Clear current list and add filtered results
            chatsList.clear()
            chatsList.addAll(filteredList)
            
            // Notify adapter of data change
            chatAdapter.notifyDataSetChanged()
            
            // Update UI based on whether there are any matching chats
            if (chatsList.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.setText(R.string.no_matching_chats)
            } else {
                binding.tvEmptyState.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Log any errors updating the UI
            Log.e(tag, "Error updating filtered UI: ${e.message}", e)
        }
    }
    
    // Handles selection of menu items in the toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                // Handle logout action
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            R.id.action_profile -> {
                // Navigate to profile screen
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.action_settings -> {
                // Navigate to settings screen
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}