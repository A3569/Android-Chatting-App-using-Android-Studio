package com.example.chatapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.adapter.MessageAdapter
import com.example.chatapp.databinding.ActivityChatBinding
import com.example.chatapp.model.Message
import com.example.chatapp.model.MessageType
import com.example.chatapp.model.User
import com.example.chatapp.util.ImageUploadUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.UUID

// Activity for handling one-on-one chat conversations
class ChatActivity : AppCompatActivity() {
    
    // View binding instance for accessing UI elements without findViewById
    private lateinit var binding: ActivityChatBinding
    
    // Adapter for displaying messages in the RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    
    // List to store all messages in the conversation
    private val messages = mutableListOf<Message>()
    
    // Identifiers for the current chat and recipient
    private lateinit var chatId: String
    private lateinit var receiverId: String
    
    // Uri of the image selected for sending
    private var selectedImageUri: Uri? = null
    
    // Flag to track if user is at the bottom of the message list
    private var isAtBottom = true
    
    // ActivityResultLauncher for handling image selection from gallery
    private val imagePickerLauncher: ActivityResultLauncher<Intent> = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        
            // Check if the result is successful and contains data
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Get the selected image URI from the result
                selectedImageUri = result.data?.data
                
                // Log image selection for debugging
                Log.d("ChatActivity", "Image selected: $selectedImageUri")
                
                // Process the selected image only if URI is not null
                selectedImageUri?.let { uri ->

                    // Show a toast to inform user the upload is in progress
                    Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
                    
                    // Log the MIME type to help with debugging media type issues
                    try {
                        val mimeType = contentResolver.getType(uri)
                        Log.d("ChatActivity", "Selected image MIME type: $mimeType")
                    } catch (e: Exception) {
                        Log.e("ChatActivity", "Failed to get MIME type: ${e.message}")
                    }
                    
                    // Take persistent URI permission if possible
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                        Log.d("ChatActivity", "Took persistable URI permission")
                    } catch (e: Exception) {
                        // Log the error but continue - this permission isn't critical for all URIs
                        Log.e("ChatActivity", "Failed to take persistable URI permission: ${e.message}")
                    }
                    
                    // Start the actual image upload process using the utility
                    ImageUploadUtil.uploadImage(
                        uri,
                        "chat_images/${chatId}",
                        onSuccess = { imageUrl ->
                            Log.d("ChatActivity", "Image upload success, URL: $imageUrl")
                            sendImageMessage(imageUrl)
                        },
                        onFailure = { e ->

                            // Log the error and notify the user
                            Log.e("ChatActivity", "Image upload failed: ${e.message}", e)
                            Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                // Log canceled or failed image selection for debugging
                Log.d("ChatActivity", "Image selection cancelled or failed, resultCode: ${result.resultCode}")
            }
        }
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding for accessing UI elements
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup the toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        
        // Get chat and receiver IDs from the intent extras
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
        
        // Validate that we have enough data to proceed
        if (chatId.isEmpty() && receiverId.isEmpty()) {
            // If both are missing, we can't show a conversation
            Toast.makeText(this, "Error: Missing chat information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize the RecyclerView for displaying messages
        setupRecyclerView()
        
        // Load the receiver's user information for display in the header
        loadReceiverInfo()
        
        // Load existing messages for this conversation
        loadMessages()
        
        // Set up click listeners for send button, attach button, etc.
        setupClickListeners()
        
        // Set up click listeners for the user profile elements
        setupProfileClickListener()
    }
    
    // Sets up click listeners for profile elements in the chat header
    private fun setupProfileClickListener() {
        // Set click listener on profile picture
        binding.ivProfilePic.setOnClickListener {
            openUserProfile()
        }
        
        // Set click listener on username text
        binding.tvUsername.setOnClickListener {
            openUserProfile()
        }
        
        // Set click listener on status text
        binding.tvStatus.setOnClickListener {
            openUserProfile()
        }
    }
    
    // Opens the profile of the user being chatted with
    private fun openUserProfile() {
        // Only proceed if we have a valid receiver ID
        if (receiverId.isNotEmpty()) {
            // Create intent for the profile activity
            val intent = Intent(this, ViewProfileActivity::class.java)
            intent.putExtra("USER_ID", receiverId)
            startActivity(intent)
        }
    }
    
    // Sets up the RecyclerView for displaying chat messages
    private fun setupRecyclerView() {
        // Initialize the adapter with the messages list and current user ID
        messageAdapter = MessageAdapter(messages, FirebaseAuth.getInstance().currentUser?.uid ?: "")
        
        // Configure the RecyclerView with the adapter and layout manager
        binding.rvMessages.apply {
            // Use LinearLayoutManager with stack from end
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
            
            // Add a scroll listener to detect when user is at the bottom of the list
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    
                    // Check scroll position only when scrolling stops
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        
                        // Find the position of the last visible item
                        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                        val itemCount = layoutManager.itemCount
                        
                        // Consider user at bottom if they can see the last or second-to-last item
                        isAtBottom = (lastVisibleItemPosition >= itemCount - 2)
                        Log.d("ChatActivity", "Scrolled to bottom: ${if (isAtBottom) 1 else 0}")
                    }
                }
            })
        }
    }
    
    // Loads the profile information of the user being chatted with
    private fun loadReceiverInfo() {
        // Return early if no receiver ID available
        if (receiverId.isEmpty()) return
        
        // Get a reference to the Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Query the user node for the receiver's information
        database.child("users").child(receiverId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Convert the data snapshot to a User object
                    val user = snapshot.getValue(User::class.java)
                    
                    // Update UI with user information if available
                    user?.let {
                        // Set the username and status in the header
                        binding.tvUsername.text = it.username
                        binding.tvStatus.text = it.status
                        
                        // Load the profile image if available
                        if (it.profileImageUrl.isNotEmpty()) {
                            ImageUploadUtil.loadImage(this@ChatActivity, it.profileImageUrl, binding.ivProfilePic)
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Show error message if user info couldn't be loaded
                    Toast.makeText(this@ChatActivity, "Error loading user info", Toast.LENGTH_SHORT).show()
                }
            })
    }
    
    // Sets up click listeners for interactive elements in the chat UI
    private fun setupClickListeners() {
        // Send button click listener
        binding.btnSend.setOnClickListener {
            // Get the message text and trim whitespace
            val messageText = binding.etMessage.text.toString().trim()
            
            // Only send if there's actual content
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.etMessage.text.clear()
            }
        }
        
        // Attach button click listener
        binding.btnAttach.setOnClickListener {
            openImageChooser()
        }
    }
    
    // Loads existing messages for the current chat conversation
    private fun loadMessages() {
        // Validate we have a chat ID to query
        if (chatId.isEmpty()) {
            Log.e("ChatActivity", "Cannot load messages: chatId is empty")
            return
        }
        
        try {
            // Get reference to Firebase database
            val database = FirebaseDatabase.getInstance().reference
            
            // Set up a real-time listener for messages in this chat
            database.child("messages").child(chatId)
                .addValueEventListener(object : ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Store the current state before updating
                        val hadMessages = messages.isNotEmpty()
                        val oldSize = messages.size
                        
                        // Clear the existing messages to rebuild the list
                        messages.clear()
                        
                        // Process each message in the snapshot
                        for (messageSnapshot in snapshot.children) {
                            // Convert the snapshot to a Message object
                            val message = messageSnapshot.getValue(Message::class.java)
                            
                            // Add valid messages to the list
                            message?.let { messages.add(it) }
                        }
                        
                        // Sort messages chronologically by timestamp
                        messages.sortBy { it.timestamp }
                        
                        // Notify the adapter that all data has changed
                        messageAdapter.notifyDataSetChanged()
                        
                        // Determine if we should scroll to the bottom
                        if (messages.isNotEmpty()) {
                            // Auto-scroll:
                            // 1. This is the first load
                            // 2. New messages arrived
                            // 3. User was already at the bottom
                            if (!hadMessages || messages.size > oldSize || isAtBottom) {
                                // Post to message queue to ensure view is measured before scrolling
                                binding.rvMessages.post {
                                    binding.rvMessages.scrollToPosition(messages.size - 1)
                                    Log.d("ChatActivity", "Scrolled to bottom: ${messages.size - 1}")
                                }
                            }
                        }
                        
                        // Mark incoming messages as read since user is viewing them
                        markMessagesAsRead()
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        // Log and display error message if loading fails
                        Log.e("ChatActivity", "Error loading messages: ${error.message}")
                        Toast.makeText(this@ChatActivity, "Error loading messages", Toast.LENGTH_SHORT).show()
                    }
                })
        } catch (e: Exception) {
            // Handle any unexpected exceptions
            Log.e("ChatActivity", "Exception in loadMessages: ${e.message}")
            Toast.makeText(this, "Failed to load messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Sends a text message to the chat
    private fun sendMessage(text: String) {
        // Get current user ID
        val senderId = FirebaseAuth.getInstance().currentUser?.uid
        
        // Validate user is authenticated before sending
        if (senderId == null) {
            Log.e("ChatActivity", "Cannot send message: User not authenticated")
            Toast.makeText(this, "You need to be logged in to send messages", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // When sending a message, assume user wants to see it
            isAtBottom = true
            
            // Generate a unique ID for this message
            val messageId = UUID.randomUUID().toString()
            
            // Create a message object with all required fields
            val message = Message(
                id = messageId,
                senderId = senderId,
                receiverId = receiverId,
                text = text,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                type = MessageType.TEXT
            )
            
            // Get reference to Firebase database
            val database = FirebaseDatabase.getInstance().reference
            
            // If this is a new chat, create the chat structure
            if (chatId.isEmpty()) {
                // Generate a new chat ID
                chatId = database.child("chats").push().key ?: UUID.randomUUID().toString()
                
                // Create chat entry for sender
                val chat = hashMapOf(
                    "chatId" to chatId,
                    "participants" to listOf(senderId, receiverId),
                    "lastMessage" to text,
                    "lastMessageTime" to System.currentTimeMillis(),
                    "unreadCount" to 0
                )
                
                // Add chat to sender's chat list
                database.child("user-chats").child(senderId).child(chatId).updateChildren(chat as Map<String, Any>)
                
                // Create chat entry for receiver
                val receiverChat = hashMapOf(
                    "chatId" to chatId,
                    "participants" to listOf(senderId, receiverId),
                    "lastMessage" to text,
                    "lastMessageTime" to System.currentTimeMillis(),
                    "unreadCount" to 1
                )
                
                // Add chat to receiver's chat list
                database.child("user-chats").child(receiverId).child(chatId).updateChildren(receiverChat as Map<String, Any>)
            } else {
                // Update sender's chat entry
                database.child("user-chats").child(senderId).child(chatId).child("lastMessage").setValue(text)
                database.child("user-chats").child(senderId).child(chatId).child("lastMessageTime").setValue(System.currentTimeMillis())
                
                // Update receiver's chat entry
                database.child("user-chats").child(receiverId).child(chatId).child("lastMessage").setValue(text)
                database.child("user-chats").child(receiverId).child(chatId).child("lastMessageTime").setValue(System.currentTimeMillis())
                
                // Increment unread count for receiver
                database.child("user-chats").child(receiverId).child(chatId).child("unreadCount")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            // Get current unread count or default to 0
                            val currentCount = snapshot.getValue(Int::class.java) ?: 0
                            
                            // Increment and update the count
                            database.child("user-chats").child(receiverId).child(chatId).child("unreadCount")
                                .setValue(currentCount + 1)
                        }
                        
                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ChatActivity", "Error updating unread count: ${error.message}")
                        }
                    })
            }
            
            // Add the actual message to the messages list
            database.child("messages").child(chatId).child(messageId).setValue(message)
                .addOnFailureListener { e ->
                    // Handle failure to send message
                    Log.e("ChatActivity", "Failed to send message: ${e.message}")
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            // Handle any unexpected exceptions
            Log.e("ChatActivity", "Exception in sendMessage: ${e.message}")
            Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Marks all messages in the current chat as read for the current user
    private fun markMessagesAsRead() {
        // Get current user ID
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Reset unread count to zero for current user
        FirebaseDatabase.getInstance().reference
            .child("user-chats").child(currentUserId).child(chatId).child("unreadCount").setValue(0)
    }
    
    // Opens the image chooser to allow user to select an image to send
    private fun openImageChooser() {
        try {
            // Check if we have the necessary storage permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Uses READ_MEDIA_IMAGES instead of READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    // Request permission if not granted
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 101)
                    return
                }
            } else {
                // Older Android versions use READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    // Request permission if not granted
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
                    return
                }
            }
            
            // Create intent for picking an image
            val intent = Intent().apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Log that we're opening the image chooser
            Log.d("ChatActivity", "Opening image chooser")
            
            // Launch the image picker using our registered launcher
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"))
        } catch (e: Exception) {
            // Log and display any errors that occur
            Log.e("ChatActivity", "Error opening image chooser: ${e.message}", e)
            Toast.makeText(this, "Failed to open image picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Sends an image message after successful upload
    private fun sendImageMessage(imageUrl: String) {
        // Get current user ID (sender)
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // When sending an image, assume user wants to see it
        isAtBottom = true
        
        // Generate a unique ID for this message
        val messageId = UUID.randomUUID().toString()
        
        // Create an image message object
        val message = Message(
            id = messageId,
            senderId = senderId,
            receiverId = receiverId,
            text = "",
            imageUrl = imageUrl,
            type = MessageType.IMAGE,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // If this is a new chat, create the chat structure
        if (chatId.isEmpty()) {
            // Generate a new chat ID
            chatId = database.child("chats").push().key ?: UUID.randomUUID().toString()
            
            // Create chat entry for sender (current user)
            val chat = hashMapOf(
                "chatId" to chatId,
                "participants" to listOf(senderId, receiverId),
                "lastMessage" to "📷 Image",
                "lastMessageTime" to System.currentTimeMillis(),
                "unreadCount" to 0
            )
            
            // Add chat to sender's chat list
            database.child("user-chats").child(senderId).child(chatId).updateChildren(chat as Map<String, Any>)
            
            // Create chat entry for receiver
            val receiverChat = hashMapOf(
                "chatId" to chatId,
                "participants" to listOf(senderId, receiverId),
                "lastMessage" to "📷 Image",
                "lastMessageTime" to System.currentTimeMillis(),
                "unreadCount" to 1
            )
            
            // Add chat to receiver's chat list
            database.child("user-chats").child(receiverId).child(chatId).updateChildren(receiverChat as Map<String, Any>)
        } else {
            // Update existing chat with new image message info
            
            // Update sender's chat entry
            database.child("user-chats").child(senderId).child(chatId).child("lastMessage").setValue("📷 Image")
            database.child("user-chats").child(senderId).child(chatId).child("lastMessageTime").setValue(System.currentTimeMillis())
            
            // Update receiver's chat entry
            database.child("user-chats").child(receiverId).child(chatId).child("lastMessage").setValue("📷 Image")
            database.child("user-chats").child(receiverId).child(chatId).child("lastMessageTime").setValue(System.currentTimeMillis())
            database.child("user-chats").child(receiverId).child(chatId).child("unreadCount").setValue(1)
        }
        
        // Add the actual message to the messages list
        database.child("messages").child(chatId).child(messageId).setValue(message)
    }
    
    // Handles toolbar menu item selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle back button click
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    // Handles permission request results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // Handle storage permission request result
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with opening image chooser
                openImageChooser()
            } else {
                // Permission denied, show explanation
                Toast.makeText(this, "Storage permission is required to send images", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 