package com.example.chatapp.model

// Represents a contact from the user's device
data class Contact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val username: String = "",
    val profileImageUrl: String = "",
    val status: String = "Available",
    val isRegistered: Boolean = false
) 