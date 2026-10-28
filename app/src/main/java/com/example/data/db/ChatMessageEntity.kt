package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val sender: String, // "user", "maira", "action"
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionStatus: String? = null
)
