package com.example.nammasantheledger

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.nammasantheledger.data.Transaction
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(context: Context) :
    ArrayAdapter<Transaction>(context, 0) {

    private var items: List<Transaction> = emptyList()

    fun setData(newItems: List<Transaction>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Transaction? = items[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_transaction, parent, false)
        val item = getItem(position)!!
        
        val avatar = view.findViewById<TextView>(R.id.avatarText)
        val nameTv = view.findViewById<TextView>(R.id.customerNameText)
        val amountTv = view.findViewById<TextView>(R.id.amountText)
        val timeTv = view.findViewById<TextView>(R.id.timestampText)
        val phoneTv = view.findViewById<TextView>(R.id.phoneNumberText)

        avatar.text = if (item.customerName.isNotEmpty()) item.customerName.first().uppercaseChar().toString() else "?"
        nameTv.text = item.customerName
        phoneTv.text = if (item.phoneNumber.isNotEmpty()) item.phoneNumber else "No Phone"
        
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        timeTv.text = sdf.format(Date(item.timestamp))

        amountTv.text = "₹${String.format("%.2f", item.amount)}"
        if (item.type == "CREDIT") {
            amountTv.setTextColor(context.resources.getColor(android.R.color.holo_red_dark))
        } else {
            amountTv.setTextColor(context.resources.getColor(android.R.color.holo_green_dark))
        }
        
        return view
    }
}

class MainActivity : AppCompatActivity() {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter
    private lateinit var summaryTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val customerNameInput = findViewById<EditText>(R.id.customerName)
        val phoneInput        = findViewById<EditText>(R.id.phoneNumber)
        val amountInput       = findViewById<EditText>(R.id.amount)
        val addCreditBtn      = findViewById<Button>(R.id.addCreditBtn)
        val addPaymentBtn     = findViewById<Button>(R.id.addPaymentBtn)
        val historyList       = findViewById<ListView>(R.id.historyList)
        summaryTv             = findViewById(R.id.dailySummaryReport)

        val profilePrefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val shopNameTv   = findViewById<TextView>(R.id.shopNameHeader)
        shopNameTv.text  = profilePrefs.getString("shopName", "Namma Santhe Ledger") ?: "Namma Santhe Ledger"

        adapter = TransactionAdapter(this)
        historyList.adapter = adapter

        // Observe Data
        viewModel.allTransactions.observe(this) { list ->
            adapter.setData(list)
        }

        viewModel.dailySales.observe(this) { updateSummaryText() }
        viewModel.totalOutstanding.observe(this) { updateSummaryText() }

        addCreditBtn.setOnClickListener { saveTransaction("CREDIT") }
        addPaymentBtn.setOnClickListener { saveTransaction("PAYMENT") }

        historyList.setOnItemLongClickListener { _, _, position, _ ->
            val t = adapter.getItem(position)!!
            val options = arrayOf("Send WhatsApp Reminder", "Delete Transaction")
            AlertDialog.Builder(this)
                .setTitle(t.customerName)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> sendWhatsAppReminder(t)
                        1 -> viewModel.deleteTransaction(t)
                    }
                }
                .show()
            true
        }
    }

    private fun updateSummaryText() {
        val sales = viewModel.dailySales.value ?: 0.0
        val due = viewModel.totalOutstanding.value ?: 0.0
        summaryTv.text = "Today you sold for ₹${String.format("%.0f", sales)}; Dues pending ₹${String.format("%.0f", due)}"
    }

    private fun saveTransaction(type: String) {
        val nameInput = findViewById<EditText>(R.id.customerName)
        val phoneInput = findViewById<EditText>(R.id.phoneNumber)
        val amountInput = findViewById<EditText>(R.id.amount)

        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val amtStr = amountInput.text.toString().trim()

        if (name.isEmpty() || amtStr.isEmpty()) {
            Toast.makeText(this, "Please enter name and amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amtStr.toDoubleOrNull() ?: 0.0
        viewModel.addTransaction(name, phone, amount, type)

        nameInput.setText("")
        phoneInput.setText("")
        amountInput.setText("")
        Toast.makeText(this, "✅ Transaction Saved", Toast.LENGTH_SHORT).show()
    }

    private fun sendWhatsAppReminder(t: Transaction) {
        if (t.phoneNumber.isEmpty()) {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show()
            return
        }
        val shopName = findViewById<TextView>(R.id.shopNameHeader).text.toString()
        val message = "Namaste ${t.customerName}, this is a reminder from $shopName regarding your pending due of ₹${t.amount}. Please clear it at your earliest. Thank you!"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://api.whatsapp.com/send?phone=${t.phoneNumber}&text=${Uri.encode(message)}")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val profilePrefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        findViewById<TextView>(R.id.shopNameHeader).text =
            profilePrefs.getString("shopName", "Namma Santhe Ledger")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("isLoggedIn", false).apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
