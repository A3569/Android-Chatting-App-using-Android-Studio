package com.example.chatapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.example.chatapp.ChatActivity
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Firebase Cloud Messaging Service for handling push notifications
class ChatFirebaseMessagingService : FirebaseMessagingService() {
    
    // Called when a message is received from Firebase Cloud Messaging
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.notification?.let {
            sendNotification(it.title ?: "New Message", it.body ?: "", remoteMessage.data)
        }
    }
    
    // Called when the FCM token is updated
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        updateFCMToken(token)
    }
    
    // Creates and displays a notification with the given message details
    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        val chatId = data["chatId"]
        val senderId = data["senderId"]
        
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("CHAT_ID", chatId)
            putExtra("RECEIVER_ID", senderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Channel human readable title",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(0, notificationBuilder.build())
    }
    
    // Updates the user's FCM token in the Firebase database
    private fun updateFCMToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        FirebaseDatabase.getInstance().reference
            .child("users").child(user.uid).child("fcmToken")
            .setValue(token)
    }
} 