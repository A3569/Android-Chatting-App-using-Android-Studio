package com.example.chatapp.model

// Represents a user with his information
data class User(
    val uid: String = "",
    val username: String = "",
    val profileImageUrl: String = "",
    val status: String = "Offline",
    val lastSeen: Long = 0,
    val phoneNumber: String = "",
    val settings: Map<String, Any> = mapOf()
) 