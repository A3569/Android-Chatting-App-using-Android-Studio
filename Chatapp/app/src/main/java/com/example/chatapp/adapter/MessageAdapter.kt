package com.example.chatapp.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.databinding.ItemMessageReceivedBinding
import com.example.chatapp.databinding.ItemMessageSentBinding
import com.example.chatapp.databinding.ItemMessageImageReceivedBinding
import com.example.chatapp.databinding.ItemMessageImageSentBinding
import com.example.chatapp.model.Message
import com.example.chatapp.model.MessageType
import com.example.chatapp.util.ImageUploadUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val messages: List<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        // View type constants to differentiate between the four possible message layouts
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_SENT_IMAGE = 3
        private const val VIEW_TYPE_RECEIVED_IMAGE = 4
    }

    // Determines the view type for the message at the specified position
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            // Message from current user (sent message)
            if (message.type == MessageType.TEXT) VIEW_TYPE_SENT_TEXT else VIEW_TYPE_SENT_IMAGE
        } else {
            // Message from other user (received message)
            if (message.type == MessageType.TEXT) VIEW_TYPE_RECEIVED_TEXT else VIEW_TYPE_RECEIVED_IMAGE
        }
    }

    // Creates the appropriate ViewHolder based on the view type
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT_TEXT -> {
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentTextMessageViewHolder(binding)
            }
            VIEW_TYPE_RECEIVED_TEXT -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedTextMessageViewHolder(binding)
            }
            VIEW_TYPE_SENT_IMAGE -> {
                val binding = ItemMessageImageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentImageMessageViewHolder(binding)
            }
            else -> {
                val binding = ItemMessageImageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedImageMessageViewHolder(binding)
            }
        }
    }

    // Binds the appropriate data to the ViewHolder based on its type
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentTextMessageViewHolder -> holder.bind(message)
            is ReceivedTextMessageViewHolder -> holder.bind(message)
            is SentImageMessageViewHolder -> holder.bind(message)
            is ReceivedImageMessageViewHolder -> holder.bind(message)
        }
    }

    // Returns the total number of items in the data set
    override fun getItemCount() = messages.size

    // ViewHolder for text messages sent by the current user
    inner class SentTextMessageViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Binds message data to the sent text message layout
        fun bind(message: Message) {
            binding.tvMessage.text = message.text
            binding.tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    // ViewHolder for text messages received from other users
    inner class ReceivedTextMessageViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Binds message data to the received text message layout
        fun bind(message: Message) {
            binding.tvMessage.text = message.text
            binding.tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    // ViewHolder for image messages sent by the current user
    inner class SentImageMessageViewHolder(private val binding: ItemMessageImageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Binds message data to the sent image message layout
        fun bind(message: Message) {
            // Load image using utility class with proper caching and placeholder handling
            ImageUploadUtil.loadImage(
                binding.root.context,
                message.imageUrl,
                binding.ivMessageImage
            )
            binding.tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    // ViewHolder for image messages received from other users
    inner class ReceivedImageMessageViewHolder(private val binding: ItemMessageImageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Binds message data to the received image message layout
        fun bind(message: Message) {
            // Load image using utility class with proper caching and placeholder handling
            ImageUploadUtil.loadImage(
                binding.root.context,
                message.imageUrl,
                binding.ivMessageImage
            )
            binding.tvTimestamp.text = formatTime(message.timestamp)
        }
    }
    
    // Formats a timestamp into a readable string
    private fun formatTime(timestamp: Long): String {
        return if (DateUtils.isToday(timestamp)) {
            // Format as time if today
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else {
            // Format as date
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
} 