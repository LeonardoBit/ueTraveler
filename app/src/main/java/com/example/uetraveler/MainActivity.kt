package com.example.uetraveler

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import android.util.Log
import androidx.appcompat.app.AlertDialog



class MainActivity : AppCompatActivity() {
    private lateinit var  timerTextView: TextView
    private lateinit var  procInfoTextView: TextView
    private lateinit var  scanButton: Button

    private var nfcAdapter: NfcAdapter? = null
    private var countDownTimer: CountDownTimer? = null

    private var timeLeftInMilliSeconds = 10000L
    private var isTimerRunning: Boolean = false
    private var isScanning = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        timerTextView = findViewById(R.id.tvTimer)
        procInfoTextView = findViewById(R.id.procInfoTextView)
        scanButton = findViewById(R.id.btnScan)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_LONG).show()
            finish()
        }

        updateTimerText()

        scanButton.setOnClickListener{
            if(!isScanning) {
                enableNfcScanning()
                pauseTimer()
                Toast.makeText(this, "Place your NFC tag near the device", Toast.LENGTH_SHORT).show()
            }else{
                disableNfcScanning()
                startTimer()

                Toast.makeText(this, "NFC scanning stopped", Toast.LENGTH_SHORT).show()
            }
            isScanning = !isScanning
        }
    }

    private fun enableNfcScanning() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) // Ensures the same instance is used
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )
        setProcedureStatus("Scanning")
        Log.d("MainActivity", "Enabling NFC scanning...") // Debugging Log
       nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, null)
    }

    private fun disableNfcScanning() {
        if (nfcAdapter != null) {
            try {
                nfcAdapter?.disableForegroundDispatch(this)
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Attempted to disable NFC dispatch when activity was not running", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        disableNfcScanning()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent triggered with action: ${intent.action}")
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                Log.d("MainActivity", "Tag detected, reading data...")
                readFromTag(it)
            }
        }
    }

    private fun readFromTag(tag: Tag) {
        Log.d("MainActivity", "Scanning tag...")
        val ndef = Ndef.get(tag)

        ndef?.let {
            it.connect()
            val message = it.ndefMessage

            if (message == null) {
                Log.e("MainActivity", "NDEF Message is null. Tag might not be formatted.")
                runOnUiThread {
                    Toast.makeText(this, "Empty or unsupported NFC tag", Toast.LENGTH_SHORT).show()
                }
                it.close()
                return
            }

            val records = message.records
            if (records.isNotEmpty()) {
                val payload = records[0].payload
                val textEncoding = if (payload[0].toInt() and 128 == 0) "UTF-8" else "UTF-16"
                val languageCodeLength = payload[0].toInt() and 63
                val text = String(
                    payload, languageCodeLength + 1,
                    payload.size - languageCodeLength - 1,
                    Charset.forName(textEncoding)
                ).trim()

                runOnUiThread {
                    setProcedureStatus("NFC Scanned: $text")
                    executeActionBasedOnTag(text)

                    isScanning = false
                }
            } else {
                Log.e("MainActivity", "NDEF Records are empty")
                runOnUiThread {
                    Toast.makeText(this, "NFC tag is empty", Toast.LENGTH_SHORT).show()
                }
            }
            it.close()
        } ?: run {
            Log.e("MainActivity", "NDEF is null, tag is not NDEF formatted.")
            runOnUiThread {
                Toast.makeText(this, "Unsupported NFC tag", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeActionBasedOnTag(data: String) {
        when (data.uppercase()) {
            "START" -> {
                timeLeftInMilliSeconds = 10000
                startTimer()
                Toast.makeText(this, "Timer started!", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "START command executed")
            }
            "PAUSE" -> {
                pauseTimer()
                setProcedureStatus("Procedure paused")
            }
            "RESET" -> {
                resetTimer()
                startTimer()
                Toast.makeText(this, "Timer reset!", Toast.LENGTH_SHORT).show()
            }
            "LOST"-> {
                ueLost()
            }
            else -> {
                Toast.makeText(this, "Unknown NFC tag: $data", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Unrecognized NFC command: $data")
            }
        }
    }

    private fun ueLost() {
        openAlertDialog("!!!UE lost!!!","ABNORMAL: UE LOST DUE TO PCI CONFLICT. Please reestablish connection (Scan Connetion Start TAG)")
        connectionLost()
        timeLeftInMilliSeconds = 10000
    }


    private fun openAlertDialog(title:String, message: String){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun connectionLost() {
        timerTextView.setBackgroundColor(Color.RED)
        setProcedureStatus("Connection lost")
        scanButton.text = "Start"
        isTimerRunning = false
    }

    private fun startTimer() {
        if(!isTimerRunning){
            countDownTimer = object:CountDownTimer(timeLeftInMilliSeconds,1000){
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMilliSeconds = millisUntilFinished
                    timerTextView.text = (millisUntilFinished/1000).toString()
                    updateTimerText()
                }

                override fun onFinish() {
                    connectionLost()
                    resetTimer()
                }

            }.start()
            isTimerRunning = true
        }
        scanButton.text = "Scan TAG"
        timerTextView.setBackgroundColor(Color.GREEN)
        setProcedureStatus("CONNECTED")
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        scanButton.text = "Start"
        isTimerRunning = false
    }

    private fun resetTimer(){
        countDownTimer?.cancel()
        timeLeftInMilliSeconds = 10000 // Reset to 10 seconds
        updateTimerText()
        isTimerRunning = false
    }

    private fun setProcedureStatus(status: String){
        procInfoTextView.text = status
    }

    private fun updateTimerText() {
        val seconds = (timeLeftInMilliSeconds / 1000).toInt()
        timerTextView.text = "$seconds s"
    }

}



//..c