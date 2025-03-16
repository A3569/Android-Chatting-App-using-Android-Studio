package com.example.chatapp.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.databinding.ItemContactBinding
import com.example.chatapp.model.Contact
import com.squareup.picasso.Picasso

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onContactClicked: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    // Tag for logging purposes
    private val tag = "ContactAdapter"

    // Creates a new ViewHolder when needed by the RecyclerView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    // Binds data to a ViewHolder at the specified position
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    // Returns the total number of items in the data set
    override fun getItemCount() = contacts.size

    // ViewHolder for contact items
    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Initialize click listener in the constructor
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onContactClicked(contacts[position])
                }
            }
        }

        // Binds contact data to the views in the ViewHolder
        fun bind(contact: Contact) {
            try {
                // Set contact information text
                binding.tvContactName.text = contact.name
                binding.tvContactPhone.text = contact.phoneNumber
                binding.tvContactStatus.text = contact.status
                
                // Load profile image if available
                if (contact.profileImageUrl.isNotEmpty()) {
                    try {
                        // Use Picasso for image loading with placeholder and error handling
                        Picasso.get()
                            .load(contact.profileImageUrl)
                            .placeholder(com.example.chatapp.R.drawable.ic_launcher_foreground)
                            .error(com.example.chatapp.R.drawable.ic_launcher_foreground)
                            .into(binding.ivContactProfile)
                    } catch (e: Exception) {
                        // Log error and fallback to default image if Picasso fails
                        Log.e(tag, "Error loading image with Picasso: ${e.message}", e)

                        // Fallback to default image
                        binding.ivContactProfile.setImageResource(com.example.chatapp.R.drawable.ic_launcher_foreground)
                    }
                } else {
                    // Set placeholder image for contacts without a profile picture
                    binding.ivContactProfile.setImageResource(com.example.chatapp.R.drawable.ic_launcher_foreground)
                }
            } catch (e: Exception) {
                // Log any errors during the binding process
                Log.e(tag, "Error binding contact data: ${e.message}", e)
            }
        }
    }
} 