package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :convoId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convoId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT DISTINCT conversationId FROM chat_messages ORDER BY timestamp DESC")
    fun getAllConversationIds(): Flow<List<String>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :convoId ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstMessageOfConversation(convoId: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :convoId")
    suspend fun deleteConversation(convoId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
