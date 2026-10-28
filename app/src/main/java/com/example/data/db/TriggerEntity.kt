package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "triggers")
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String, // "TIME_REMINDER", "PHRASE_SHORTCUT", "PHONE_EVENT"
    val timeOfDay: String? = null, // e.g. "08:30"
    val daysOfWeek: String? = null, // e.g. "MON,TUE,WED,THU,FRI,SAT,SUN"
    val shortcutPhrase: String? = null, // e.g. "অফিসে যাচ্ছি"
    val actionDetails: String = "", // e.g. JSON or comma separated actions
    val phoneEventType: String? = null, // "BATTERY_LOW", "CHARGER_CONNECTED", "CHARGER_DISCONNECTED"
    val spokenText: String = "",
    val enabled: Boolean = true,
    val lastFiredTime: Long = 0L
)
