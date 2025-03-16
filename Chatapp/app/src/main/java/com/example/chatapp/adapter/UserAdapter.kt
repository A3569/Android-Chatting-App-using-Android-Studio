package com.example.chatapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.databinding.ItemUserBinding
import com.example.chatapp.model.User
import com.example.chatapp.util.ImageUploadUtil

class UserAdapter(
    private val users: List<User>,
    private val onUserClicked: (User) -> Unit,
    private val onUserLongClicked: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // Creates a new ViewHolder when needed by the RecyclerView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    // Binds data to a ViewHolder at the specified position
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    // Returns the total number of items in the data set
    override fun getItemCount() = users.size

    // ViewHolder for user items
    inner class UserViewHolder(private val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Initialize click listeners in the constructor
        init {
            // Set up normal click listener to handle starting a chat
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onUserClicked(users[position])
                }
            }
            
            // Add long press listener for viewing profile
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onUserLongClicked(users[position])
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
        }

        // Binds user data to the views in the ViewHolder
        fun bind(user: User) {
            // Set the username and status text
            binding.tvUsername.text = user.username
            binding.tvStatus.text = user.status
            
            // Load profile image using ImageUploadUtil
            ImageUploadUtil.loadImage(
                binding.root.context,
                user.profileImageUrl,
                binding.ivProfilePic
            )
        }
    }
} 