package com.example.nammasantheledger

import android.app.Application
import androidx.lifecycle.*
import com.example.nammasantheledger.data.AppDatabase
import com.example.nammasantheledger.data.Transaction
import kotlinx.coroutines.launch
import java.util.*

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()

    val allTransactions: LiveData<List<Transaction>> = dao.getAll()

    private val _dailySales = MediatorLiveData<Double>()
    val dailySales: LiveData<Double> = _dailySales

    private val _totalOutstanding = MediatorLiveData<Double>()
    val totalOutstanding: LiveData<Double> = _totalOutstanding

    init {
        _dailySales.addSource(allTransactions) { list: List<Transaction>? ->
            updateSummaries(list)
        }
        _totalOutstanding.addSource(allTransactions) { /* already updated by dailySales source */ }
    }

    private fun updateSummaries(allTx: List<Transaction>?) {
        if (allTx == null) return
        
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis

        // Group by Name + Phone to maintain separate ledgers for every customer
        val customerGroups = allTx.groupBy { "${it.customerName.trim().uppercase()}|${it.phoneNumber.trim()}" }
        
        var totalSalesToday = 0.0
        var grandTotalDue = 0.0
        
        for (group in customerGroups.values) {
            // CRITICAL: Sort by timestamp ASC to simulate the sequence of transactions correctly
            val sortedTx = group.sortedBy { it.timestamp }
            
            var currentDue = 0.0
            var customerSalesToday = 0.0
            
            for (tx in sortedTx) {
                if (tx.type == "CREDIT") {
                    // CREDIT is always a Sale of goods.
                    if (tx.timestamp >= startOfDay) {
                        customerSalesToday += tx.amount
                    }
                    // It increases what the customer owes.
                    currentDue += tx.amount
                } else { // PAYMENT
                    // PAYMENT clears existing debt first.
                    val debtToClear = maxOf(0.0, currentDue)
                    val usedToClearDebt = minOf(tx.amount, debtToClear)
                    val cashSalePart = tx.amount - usedToClearDebt
                    
                    // Only the portion of payment that is NOT clearing old debt is a "New Sale" (Cash Sale) today.
                    if (tx.timestamp >= startOfDay) {
                        customerSalesToday += cashSalePart
                    }
                    
                    // Payment always reduces current debt.
                    currentDue -= usedToClearDebt
                    // If they pay extra (excess), it doesn't reduce currentDue below 0 here 
                    // (simple ledger logic where credit balances don't offset future udari in this summary)
                }
            }
            totalSalesToday += customerSalesToday
            grandTotalDue += currentDue
        }
        
        _dailySales.value = totalSalesToday
        _totalOutstanding.value = grandTotalDue
    }

    fun addTransaction(name: String, phone: String, amount: Double, type: String) {
        viewModelScope.launch {
            dao.insert(Transaction(customerName = name, phoneNumber = phone, amount = amount, type = type))
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.delete(transaction)
        }
    }
}