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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.example.uetraveler.fragments.InfoFragment
import com.example.uetraveler.fragments.QuizFragment

class MainActivity : AppCompatActivity(), IGameEventHandler {
    private lateinit var timerTextView: TextView
    private lateinit var procInfoTextView: TextView
    private lateinit var scanButton: Button

    private lateinit var inactivityTimer: InactivityTimer
    private lateinit var gameHandler: GameHandler

    private lateinit var infoFragment: InfoFragment
    private lateinit var quizFragment: QuizFragment

    private var nfcAdapter: NfcAdapter? = null
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        timerTextView = findViewById(R.id.tvTimer)
        procInfoTextView = findViewById(R.id.procInfoTextView)
        scanButton = findViewById(R.id.btnScan)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        inactivityTimer = InactivityTimer()

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_LONG).show()
            finish()
        }
        updateTimerText(0)

        scanButton.setOnClickListener{
            if (!isScanning) {
                enableNfcScanning()
                inactivityTimer.stopTimer()
                scanButton.text = "Stop scanning"
                Toast.makeText(this, "Place your NFC tag near the device", Toast.LENGTH_SHORT).show()
            } else {
                disableNfcScanning()
                scanButton.text = "Start scanning"
                Toast.makeText(this, "NFC scanning stopped", Toast.LENGTH_SHORT).show()
                inactivityTimer.resumeTimer()
            }
            isScanning = !isScanning
        }

        infoFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as InfoFragment
        quizFragment = QuizFragment({ correct -> quizCallback(correct) })

        inactivityTimer.registerTickCallback { millisLeft ->
            updateTimerText((millisLeft / 1000).toInt())
        }

        inactivityTimer.registerFinishedCallback {
            connectionLost()
        }
        gameHandler = GameHandler(inactivityTimer)
        gameHandler.registerEventHandler(this)
    }

    private fun connectionLost() {
        timerTextView.setBackgroundColor(Color.RED)
        setProcedureStatus(ProcedureStatus.CONNECTION_LOST)
    }

    private fun enableNfcScanning() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("text/plain")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Failed to add MIME type", e)
            }
        }
        val filters = arrayOf(ndefFilter)

        val techList = arrayOf(arrayOf(android.nfc.tech.Ndef::class.java.name))

        Log.d("MainActivity", "Enabling NFC scanning...")
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techList)
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

    private fun quizCallback(correct: Boolean) {
        val message = if (correct) "Correct!" else "Wrong!"
        infoFragment.setInfoText(message)
        showFragment(infoFragment)
    }

    private fun showFragment(frag: Fragment) {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainerView, frag)
        transaction.commit()
    }

    //TO DO Fix timer pause when scanning is ON but not TAG is scanned and button pressed again

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
        gameHandler.handleTag(data)
    }

    private fun setProcedureStatus(status: String){
        procInfoTextView.text = status
    }

    private fun updateTimerText(seconds: Int) {
        timerTextView.text = "$seconds s"
    }

    private fun openAlertDialog(title: String, message: String){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun handleGameEvent(event: EGameEvent) {
        when (event) {
            EGameEvent.START -> {
                setProcedureStatus(ProcedureStatus.CONNECTED)
                timerTextView.setBackgroundColor(Color.GREEN)
                Toast.makeText(this, "Timer started!", Toast.LENGTH_SHORT).show()
                quizFragment.setQuestion(
                    "How much is 2+2?",
                    listOf("3", "4", "2"),
                    1
                )
                showFragment(quizFragment)
            }
            EGameEvent.UE_LOST -> {
                if (procInfoTextView.text == ProcedureStatus.CONNECTION_LOST){
                    inactivityTimer.stopTimer()
                    openAlertDialog("Connection lost ", "Please attach again by scanning initial connection tag")
                }else{
                    timerTextView.setBackgroundColor(Color.RED)
                    setProcedureStatus(ProcedureStatus.CONNECTION_LOST)
                    openAlertDialog("!!!UE lost!!!","ABNORMAL: UE LOST DUE TO PCI CONFLICT. Please reestablish connection (Scan Connetion Start TAG)")
                }
            }
            EGameEvent.RESET -> {
                if (procInfoTextView.text == ProcedureStatus.CONNECTION_LOST){
                    inactivityTimer.stopTimer()
                    openAlertDialog("Connection lost ", "Please attach again by scanning initial connection tag")
                }else {
                    setProcedureStatus(ProcedureStatus.CONNECTED)
                    timerTextView.setBackgroundColor(Color.GREEN)
                    Toast.makeText(this, "Timer reset!", Toast.LENGTH_SHORT).show()
                }
            }
            EGameEvent.PAUSE -> {
                setProcedureStatus("Procedure paused")
            }
            else -> {
                Log.d("MainActivity", "Unhandled game event: $event")
            }
        }
        scanButton.text = "Start scanning"
    }
}
