package com.example.chatapp.adapter

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.BuildConfig
import com.example.chatapp.databinding.ItemChatBinding
import com.example.chatapp.model.Chat
import com.example.chatapp.util.ImageUploadUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatAdapter(
    private val chats: List<Chat>,
    private val onChatClicked: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    // Creates a new ViewHolder when needed by the RecyclerView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    // Binds data to a ViewHolder at the specified position
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    // Returns the total number of items in the data set
    override fun getItemCount() = chats.size

    // ViewHolder for chat items
    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Initialize click listener in the constructor
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onChatClicked(chats[position])
                }
            }
        }

        // Binds chat data to the views in the ViewHolder
        @SuppressLint("SetTextI18n")
        fun bind(chat: Chat) {
            // Get other user's ID
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val otherUserId = chat.participants.find { it != currentUserId }

            // Load user info from Firebase
            otherUserId?.let { uid ->
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            // Extract user information from the database snapshot
                            val username = snapshot.child("username").getValue(String::class.java) ?: "User"
                            val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                            
                            // Update UI with user details
                            binding.tvUsername.text = username
                            
                            // Load profile image using ImageUploadUtil
                            ImageUploadUtil.loadImage(
                                binding.root.context,
                                profileImageUrl,
                                binding.ivProfilePic
                            )
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Get reference to the context for resources
                            val context = binding.root.context
                            
                            // Log the error with all relevant details
                            Log.e("ChatAdapter", "Firebase query cancelled: ${error.message}")
                            Log.e("ChatAdapter", "Error code: ${error.code}, Details: ${error.details}")
                            
                            // Different handling based on error code
                            when (error.code) {
                                DatabaseError.PERMISSION_DENIED -> {
                                    // Permission issue
                                    Log.w("ChatAdapter", "Permission denied for chat data")
                                    binding.tvUsername.text = "Access restricted"
                                }
                                
                                DatabaseError.NETWORK_ERROR -> {
                                    // Network connectivity issues
                                    Log.w("ChatAdapter", "Network error while loading chat data")
                                    binding.tvUsername.text = "User"
                                }
                                
                                DatabaseError.OPERATION_FAILED -> {
                                    // Generic operation failure
                                    Log.w("ChatAdapter", "Operation failed: ${error.message}")
                                    binding.tvUsername.text = "User"
                                }
                                
                                else -> {
                                    // Any other error
                                    Log.w("ChatAdapter", "Unknown error: ${error.code}")
                                    binding.tvUsername.text = "User"
                                }
                            }
                            
                            // Always set a default profile image
                            binding.ivProfilePic.setImageResource(com.example.chatapp.R.drawable.default_profile)
                            
                            // Show toast for critical errors in debug builds
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    context,
                                    "Failed to load chat data: ${error.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    })
            }

            // Display the last message exchanged in this conversation
            binding.tvLastMessage.text = chat.lastMessage
            
            // Format timestamp into a relative time string (e.g., "5 minutes ago")
            val timeFormatted = if (chat.lastMessageTime > 0) {
                DateUtils.getRelativeTimeSpanString(
                    chat.lastMessageTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else {
                ""
            }
            binding.tvTimestamp.text = timeFormatted.toString()
            
            // Set unread message count with proper visibility
            if (chat.unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text = chat.unreadCount.toString()
            } else {
                binding.tvUnreadCount.visibility = View.GONE
            }
        }
    }
} 