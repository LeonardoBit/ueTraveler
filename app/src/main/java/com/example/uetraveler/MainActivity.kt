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
import android.os.Handler
import android.os.Looper
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
    private lateinit var messageTextView: TextView
    private lateinit var signalQualityTextView: TextView
    private lateinit var scanButton: Button

    private lateinit var inactivityTimer: InactivityTimer
    private lateinit var gameHandler: GameHandler

    private lateinit var infoFragment: InfoFragment
    private lateinit var quizFragment: QuizFragment

    private var nfcAdapter: NfcAdapter? = null
    private var isScanning = false
    private var isConnected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        timerTextView = findViewById(R.id.tvTimer)
        procInfoTextView = findViewById(R.id.procInfoTextView)
        messageTextView = findViewById(R.id.messageTextView)
        signalQualityTextView = findViewById(R.id.signalQualityTextView)
        scanButton = findViewById(R.id.btnScan)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        inactivityTimer = InactivityTimer(600000L)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_LONG).show()
            finish()
        }

        val sequenceTriggers: Map<String, List<String>> = mapOf(
            NFCTag.MSG1 to listOf(NFCTag.MSG1, NFCTag.MSG3, NFCTag.RRCSC,NFCTag.SMC)
            // You can define more sequences here dynamically
        )

        //this should be a map with several sequences . To make it possible to handle different
        val sequenceStatus: Map<String, String> = mapOf(
            NFCTag.MSG1 to "Message 1 successfully sent, GNB responded with MSG2",
            NFCTag.MSG3 to "Message 3 successfully sent, GNB using PUSCH responded with RRCSetup message",
            NFCTag.RRCSC to "RRC setup complete sent, Gnb responded with Security mode command",
            NFCTag.SMC to "Security mode complete sent. Congratulations attach successfully complete"
            // Status text for each step
        )

        startMessage()

        updateTimerText(0)



        scanButton.setOnClickListener{
            if (!isScanning) {
                enableNfcScanning()
                //inactivityTimer.stopTimer()
                Toast.makeText(this, "Place your NFC tag near the device", Toast.LENGTH_SHORT).show()
            } else {
                disableNfcScanning()
                Toast.makeText(this, "NFC scanning stopped", Toast.LENGTH_SHORT).show()
                //inactivityTimer.resumeTimer()
            }
            isScanning = !isScanning
        }

        //infoFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as InfoFragment
        //quizFragment = QuizFragment({ correct -> quizCallback(correct) })

        inactivityTimer.registerTickCallback { millisLeft ->
            updateTimerText((millisLeft / 1000).toInt())
        }

        inactivityTimer.registerFinishedCallback {
            connectionLost()
        }
        gameHandler = GameHandler(
            inactivityTimer,
            sequenceTriggers,
            sequenceStatus
        ) { statusMessage ->
            runOnUiThread {
                setGameMessage(statusMessage)
            }
        }
        gameHandler.registerEventHandler(this)
    }

    private fun connectionLost() {
        timerTextView.setBackgroundColor(Color.RED)
        setProcedureStatus(ProcedureStatus.CONNECTION_LOST)
        gameHandler.resetSequenceMode()
    }

    private fun enableNfcScanning() {
        scanButton.text = "Stop scanning"
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
            scanButton.text = "Start scanning"
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

    /*private fun quizCallback(correct: Boolean) {
        val message = if (correct) "Correct!" else "Wrong!"
        infoFragment.setInfoText(message)
        showFragment(infoFragment)
    }

    private fun showFragment(frag: Fragment) {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainerView, frag)
        transaction.commit()
    }*/

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
                    executeActionBasedOnTag(text, isConnected)
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

    private fun executeActionBasedOnTag(data: String,isConnected: Boolean) {
        val scannedTag = data.uppercase()
        gameHandler.handleTag(scannedTag,isConnected)
    }

    private fun setProcedureStatus(status: String){
        procInfoTextView.text = status
    }

    private fun setGameMessage(status: String){
        messageTextView.text = status
    }

    private fun setSignalQualityStatus(status: String){
        signalQualityTextView.text = status
    }

    private fun updateTimerText(seconds: Int) {
        timerTextView.text = "$seconds s"
    }

    private fun startMessage() {
        openAlertDialogStartMessage(
            getString(R.string.welcome_message_title),
            getString(R.string.welcome_message1)
        ) {
            openAlertDialogStartMessage(
                getString(R.string.welcome_message2_title),
                getString(R.string.welcome_message2)
            ) {
                openAlertDialogStartMessage(
                    getString(R.string.welcome_message3_title),
                    getString(R.string.welcome_message3)
                ) {
                    openAlertDialogStartMessage(
                        getString(R.string.welcome_message4_title),
                        getString(R.string.welcome_message4)
                    )
                }
            }
        }
    }
    private fun openAlertDialog(title: String, message: String){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun openAlertDialogStartMessage(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Ok") { dialog, _ ->
            dialog.dismiss()
            onDismiss?.invoke()  // Run callback after dismissal
        }

        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun openAlertDialogWithTwoAns(title: String,
                                          message: String,
                                          titleYes: String,
                                          messageYes: String
                                          ){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { dialog, _ ->
                doSomethingOnPositiveAnswer(titleYes, messageYes)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAlertDialogWithTwoAnsPCIFAIL(title: String,
                                          message: String,
                                          titleYes: String,
                                          messageYes: String
    ){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { dialog, _ ->
                setGameMessage(titleYes+messageYes)
                setProcedureStatus(ProcedureStatus.CONNECTION_LOST)
                timerTextView.setBackgroundColor(Color.RED)
                setSignalQualityStatus("RSRP X\n RSRQ X")
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun doSomethingOnPositiveAnswer(title: String,
                                          message: String) {
        setGameMessage(title+message)
    }

    override fun handleGameEvent(event: EGameEvent) {
        if(event != EGameEvent.START && procInfoTextView.text == ProcedureStatus.CONNECTION_LOST) {
            if (procInfoTextView.text == ProcedureStatus.CONNECTION_LOST) {
                openAlertDialog(
                    "Connection lost ",
                    "Please attach again by scanning initial connection tag"
                )
            }
        }else {
            when (event) {
                EGameEvent.START ->{
                    setProcedureStatus(ProcedureStatus.CONNECTED)
                    timerTextView.setBackgroundColor(Color.GREEN)
                    setGameMessage("\"Well done you have connected again\"")
                    setSignalQualityStatus("RSRP -90dB\n RSRQ -50dB")
                    isConnected = true
                }

                EGameEvent.PCIFAIL -> {
                        openAlertDialogWithTwoAnsPCIFAIL(
                            getString(R.string.cell_found_title),
                            getString(R.string.good_cell2_pcifail_message),
                            getString(R.string.good_cell2_pcifail_title_if_yes),
                            getString(R.string.good_cell2_pcifail_message_if_yes)
                        )
                    isConnected = false
                }

                EGameEvent.RESET -> {
                        setProcedureStatus(ProcedureStatus.CONNECTED)
                        timerTextView.setBackgroundColor(Color.GREEN)
                        Toast.makeText(this, "Timer reset!", Toast.LENGTH_SHORT).show()
                }

                EGameEvent.PAUSE -> {
                    setProcedureStatus("Procedure paused")
                }

                EGameEvent.MSG1 -> {
                        setGameMessage("Attach procedure started")
                        inactivityTimer.startTimer()
                        timerTextView.setBackgroundColor(Color.GREEN)
                }

                EGameEvent.ATTACHFINISHED -> {
                    setGameMessage("Procedure DONE awesome.\"Attach complete\", \"Look under the table to for additional information. \"")
                    setProcedureStatus(ProcedureStatus.CONNECTED)
                    setSignalQualityStatus("RSRP -90dB\n RSRQ -50dB")
                    isConnected = true
                    inactivityTimer.resetTimer()
                    inactivityTimer.startTimer()
                }

                EGameEvent.CELL2 -> {
                        openAlertDialogWithTwoAns(
                            "Cell configuration",
                            "11101001001. Chose for attach?",
                            "Attach not started",
                            "Unfortunately capabilities is not enough"
                        )
                }

                EGameEvent.CELL3 -> {
                        openAlertDialogWithTwoAns(
                            "Cell configuration",
                            "Cell configuration 22222222",
                            "Attach not started",
                            "bad caps"
                        )
                }

                EGameEvent.MEAS1 -> {
                    openAlertDialogWithTwoAns(
                        getString(R.string.cell_found_title),
                        getString(R.string.good_cell1good_message),
                        getString(R.string.good_cell1good_message_if_yes_title),
                        getString(R.string.good_cell1good_message_if_yes)
                    )
                    //setProcedureStatus("Handover completed. Time to do some CA !!!!")
                    inactivityTimer.startTimer()
                    timerTextView.setBackgroundColor(Color.GREEN)
                }

                EGameEvent.MEAS2 -> {
                    openAlertDialogWithTwoAns(
                        getString(R.string.cell_found_title),
                        getString(R.string.cell3bad),
                        getString(R.string.cell_bad_ho_failed_title),
                        getString(R.string.cell3bad_answer_yes)
                    )
                    timerTextView.setBackgroundColor(Color.GREEN)
                }

                EGameEvent.MEAS3 -> {
                    openAlertDialogWithTwoAns(
                        getString(R.string.cell_found_title),
                        getString(R.string.cell4bad),
                        getString(R.string.cell_bad_ho_failed_title),
                        getString(R.string.cell4bad_answer_yes)
                    )
                    timerTextView.setBackgroundColor(Color.GREEN)
                }

                EGameEvent.CA -> {
                    setGameMessage(getString(R.string.ca_done_congratulation_message))
                    timerTextView.setBackgroundColor(Color.GREEN)
                }

                EGameEvent.CAPS -> {
                    openAlertDialog("Capabilities", getString(R.string.Capabilities))
                }
                else -> {
                    Log.d("MainActivity", "Unhandled game event: $event")
                }
            }
        }
        scanButton.text = "Start scanning"
    }
}
