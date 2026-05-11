package com.example.nammasantheledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val customerName: String,
    val phoneNumber: String = "",
    val amount: Double,
    val type: String, // "CREDIT" (Udari) or "PAYMENT" (Vasuli)
    val timestamp: Long = System.currentTimeMillis()
)