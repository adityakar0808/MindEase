package com.example.mindease.data.local

import android.util.Log
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_messages")
data class LocalChatMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val peerName: String,
    val peerUid: String,
    val content: String,
    val isFromCurrentUser: Boolean,
    val timestamp: Long
)

@Entity(tableName = "local_conversations")
data class LocalConversation(
    @PrimaryKey val sessionId: String,
    val peerName: String,
    val peerUid: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val totalMessages: Int
)

@Dao
interface LocalChatDao {
    @Query("SELECT * FROM local_conversations ORDER BY lastMessageTime DESC")
    fun getAllConversations(): Flow<List<LocalConversation>>

    @Query("SELECT * FROM local_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<LocalChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LocalChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: LocalConversation)

    @Query("UPDATE local_conversations SET lastMessage = :lastMessage, lastMessageTime = :timestamp, totalMessages = totalMessages + 1 WHERE sessionId = :sessionId")
    suspend fun updateConversation(sessionId: String, lastMessage: String, timestamp: Long): Int

    @Query("DELETE FROM local_conversations WHERE sessionId = :sessionId")
    suspend fun deleteConversation(sessionId: String)

    @Query("DELETE FROM local_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)
}

@Database(
    entities = [LocalChatMessage::class, LocalConversation::class],
    version = 1,
    exportSchema = false
)
abstract class LocalChatDatabase : RoomDatabase() {
    abstract fun chatDao(): LocalChatDao

    companion object {
        @Volatile
        private var INSTANCE: LocalChatDatabase? = null

        fun getDatabase(context: android.content.Context): LocalChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    LocalChatDatabase::class.java,
                    "local_chat_database"
                )
                    .fallbackToDestructiveMigration() // Add this for testing
                    .build()
                INSTANCE = instance
                Log.d("LocalChatDatabase", "Database instance created")
                instance
            }
        }
    }
}
