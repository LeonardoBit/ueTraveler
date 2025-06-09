package com.example.uetraveler

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.graphics.Color
import android.nfc.FormatException
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.example.uetraveler.fragments.InfoFragment
import com.example.uetraveler.fragments.QuizFragment
import java.io.IOException

class MainActivity : AppCompatActivity(), IGameEventHandler {
    private lateinit var timerTextView: TextView
    private lateinit var procInfoTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var signalQualityTextView: TextView
    private lateinit var scanButton: Button

    private lateinit var inactivityTimer: InactivityTimer
    private lateinit var gameHandler: GameHandler
    private lateinit var soundPlayer: SoundPlayer

    private lateinit var infoFragment: InfoFragment
    private lateinit var quizFragment: QuizFragment

    private var nfcAdapter: NfcAdapter? = null
    private var isScanning = false
    private var isConnected = false
    private var isMsg1Scanned = false
    private var isAttachCompleted = false
    private var isHandoverCompleted = false
    private var lastConnectedPci = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        timerTextView = findViewById(R.id.tvTimer)
        procInfoTextView = findViewById(R.id.procInfoTextView)
        messageTextView = findViewById(R.id.messageTextView)
        signalQualityTextView = findViewById(R.id.signalQualityTextView)
        scanButton = findViewById(R.id.btnScan)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        inactivityTimer = InactivityTimer(30000L)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_LONG).show()
            finish()
        }


        soundPlayer = SoundPlayer(this)
        soundPlayer.loadSound(this, R.raw.good_beep)
        soundPlayer.loadSound(this, R.raw.bad_beep)
        soundPlayer.loadSound(this, R.raw.win)
        soundPlayer.loadSound(this, R.raw.win_end_game)



        val sequenceTriggers: Map<String, List<String>> = mapOf(
            NFCTag.MSG1 to listOf(NFCTag.MSG1, NFCTag.MSG3, NFCTag.RRCSC,NFCTag.SMC)
            // You can define more sequences here dynamically
        )

        //this should be a map with several sequences . To make it possible to handle different
        val sequenceStatus: Map<String, String> = mapOf(
            NFCTag.MSG1 to getString(R.string.attach_proc_msg1),
            NFCTag.MSG3 to getString(R.string.attach_proc_msg3),
            NFCTag.RRCSC to getString(R.string.attach_proc_msg_rrcsc),
            NFCTag.SMC to getString(R.string.attach_proc_msg_smc_attach_complete),
            // Status text for each step
        )

        val sequenceWrongTagScannedMsg: Map<String, String> = mapOf(
            NFCTag.MSG1 to getString(R.string.sequence_bad_msg1),
            NFCTag.MSG3 to getString(R.string.sequence_bad_msg3),
            NFCTag.RRCSC to getString(R.string.sequence_bad_rrcsc),
            NFCTag.SMC to getString(R.string.sequence_bad_smc)
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
            sequenceStatus,
            sequenceWrongTagScannedMsg,
            soundPlayer,
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
        setCellPCI("PCI\nXX",lastConnectedPci)
        setGameMessage("Please reattach to the last connected cell, or initiate the attach procedure if it hasn't been started or finished.")
        isConnected = false
        isMsg1Scanned = false
        gameHandler.resetSequenceMode()
        playSoundBad()
    }

    private fun playSoundGood() {
        soundPlayer.playSound(R.raw.good_beep)
    }

    private fun playSoundBad() {
        soundPlayer.playSound(R.raw.bad_beep)
    }
    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()  // assuming you have a SoundPlayer instance
    }
    private fun enableNfcScanning() {
        scanButton.text = "Stop scanning"
        scanButton.setBackgroundColor(ContextCompat.getColor(this,R.color.Red))
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

    @SuppressLint("ResourceAsColor")
    private fun disableNfcScanning() {
        if (nfcAdapter != null) {
            scanButton.text = "Start scanning"
            scanButton.setBackgroundColor(ContextCompat.getColor(this,R.color.Indigo_accent))
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

        if (ndef == null) {
            Log.e("MainActivity", "NDEF is null, tag is not NDEF formatted.")
            runOnUiThread {
                Toast.makeText(this, "Unsupported NFC tag", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            ndef.connect()

            val message = ndef.ndefMessage
            if (message == null) {
                Log.e("MainActivity", "NDEF Message is null. Tag might not be formatted.")
                runOnUiThread {
                    Toast.makeText(this, "Empty or unsupported NFC tag", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val records = message.records
            if (records.isNotEmpty()) {
                val payload = records[0].payload
                val textEncoding = if (payload[0].toInt() and 128 == 0) "UTF-8" else "UTF-16"
                val languageCodeLength = payload[0].toInt() and 63
                val text = String(
                    payload,
                    languageCodeLength + 1,
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

        } catch (e: IOException) {
            Log.e("MainActivity", "IOException while connecting to or reading tag", e)
            runOnUiThread {
                Toast.makeText(this, "Error reading NFC tag. Please try again.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: FormatException) {
            Log.e("MainActivity", "FormatException while reading tag", e)
            runOnUiThread {
                Toast.makeText(this, "Invalid NFC tag format.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            try {
                ndef.close()
            } catch (e: IOException) {
                Log.w("MainActivity", "Error closing NFC connection", e)
            }
        }
    }

    private fun executeActionBasedOnTag(data: String, isConnected: Boolean) {
        val scannedTag = data.uppercase()

        if (scannedTag == NFCTag.MSG1 && !isMsg1Scanned && !isAttachCompleted) {

            isMsg1Scanned = true
            openAlertDialogWithTwoAnsNEW(
                getString(R.string.lock_unlock_good_cell_attach_start_title),
                getString(R.string.lock_unlock_good_cell_attach_start_message),
            ){
            gameHandler.handleTag(scannedTag, isConnected)
            }
        }else if(scannedTag == NFCTag.MSG1 && isAttachCompleted && !isConnected) {
            playSoundBad()
            openAlertDialog("Procedure failed!","You have already completed attach procedure. To restore connection try to re-attach to the last connected cell.")
        }else {
            gameHandler.handleTag(scannedTag, isConnected)
        }

    }

    private fun openAlertDialogWithTwoAnsNEW(
        title: String,
        message: String,
        onYes: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                onYes()  // Invoke callback when user confirms
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                isMsg1Scanned = false
                dialog.dismiss()
            }
            .show()
    }

    private fun setProcedureStatus(status: String){
        procInfoTextView.text = status
    }

    private fun setGameMessage(status: String){
        messageTextView.text = status
    }

    private fun setCellPCI(status: String, pci: Int){
        signalQualityTextView.text = status
        lastConnectedPci = pci
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
    private fun congratsMessage() {
        openAlertDialogStartMessage(
            getString(R.string.ca_done_congratulation_title),
            getString(R.string.ca_done_congratulation_message)
        ) {
            openAlertDialogStartMessage(
                getString(R.string.ca_done_congratulation_title),
                getString(R.string.ca_done_congratulation_message)
            ) {
                openAlertDialogStartMessage(
                    getString(R.string.ca_done_congratulation_title),
                    getString(R.string.ca_done_congratulation_message)
                ) {
                    openAlertDialogStartMessage(
                        getString(R.string.ca_done_congratulation_title),
                        getString(R.string.ca_done_congratulation_message)
                    ){
                        openAlertDialogStartMessage(
                            getString(R.string.ca_done_congratulation_title),
                            getString(R.string.ca_done_congratulation_message)
                        ){
                            openAlertDialogStartMessage(
                                getString(R.string.ca_done_congratulation_title),
                                getString(R.string.ca_done_congratulation_message)
                            ){
                                openAlertDialogStartMessage(
                                    getString(R.string.ca_done_congratulation_title),
                                    getString(R.string.ca_done_congratulation_message)
                                )
                            }
                        }
                    }
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
        builder.setCancelable(false)
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
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                doSomethingOnPositiveAnswer(titleYes, messageYes)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAlertDialogWithTwoAnsBadCells(title: String,
                                          message: String,
                                          titleYes: String,
                                          messageYes: String
    ){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                doSomethingOnPositiveAnswer(titleYes, messageYes)
                soundPlayer.playSound(R.raw.bad_beep)
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
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                setGameMessage(titleYes+messageYes)
                setProcedureStatus(ProcedureStatus.CONNECTION_LOST)
                inactivityTimer.stopTimer()
                timerTextView.setBackgroundColor(Color.RED)
                setCellPCI("PCI\nXX", lastConnectedPci)
                soundPlayer.playSound(R.raw.bad_beep)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAlertDialogWithTwoAnsHoSucc(title: String,
                                                 message: String,
                                                 titleYes: String,
                                                 messageYes: String
    ){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                setGameMessage(titleYes+messageYes)
                setCellPCI(getString(R.string.good_cell1good_message_new_pci),21)
                soundPlayer.playSound(R.raw.win)
                inactivityTimer.resetTimer()
                inactivityTimer.startTimer()
                isHandoverCompleted = true
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
        if(event != EGameEvent.START && procInfoTextView.text == ProcedureStatus.CONNECTION_LOST ) {
            if (event == EGameEvent.MEAS1 && lastConnectedPci == 21) {
                soundPlayer.playSound(R.raw.good_beep)
                setProcedureStatus(ProcedureStatus.CONNECTED)
                timerTextView.setBackgroundColor(Color.GREEN)
                setGameMessage(getString(R.string.re_attach_message_after_handover))
                setCellPCI("PCI\n21", lastConnectedPci)
                isConnected = true
                inactivityTimer.resetTimer()
                inactivityTimer.startTimer()
            }else if(event == EGameEvent.MSG1 && lastConnectedPci == 0){
                inactivityTimer.startTimer()
                timerTextView.setBackgroundColor(Color.GREEN)
            }else if(event == EGameEvent.ATTACHFINISHED ){
                    soundPlayer.playSound(R.raw.win)
                    setProcedureStatus(ProcedureStatus.CONNECTED)
                    setCellPCI("PCI\n12", 12)
                    isConnected = true
                    //isMsg1Scanned = false
                    isAttachCompleted = true
                    inactivityTimer.resetTimer()
                    inactivityTimer.startTimer()
            }
            else {
                openAlertDialog(
                    "Connection lost ",
                    "Please re-attach to the last connected cell, or initiate the attach procedure if it hasn't been started or finished."
                )

            }
        } else {
            when (event) {
                EGameEvent.START ->{
                    if(lastConnectedPci == 12){
                    playSoundGood()
                    setProcedureStatus(ProcedureStatus.CONNECTED)
                    timerTextView.setBackgroundColor(Color.GREEN)
                    setGameMessage(getString(R.string.re_attach_message),)
                    setCellPCI("PCI\n12",lastConnectedPci)
                    isConnected = true
                    inactivityTimer.resetTimer()
                    inactivityTimer.startTimer()
                    }else{
                        playSoundBad()
                        setGameMessage("Wrong re-attach cell. Please re-attach to the last connected cell.")
                    }
                }

                EGameEvent.PCIFAIL -> {
                    if(isAttachCompleted) {
                        if (!isHandoverCompleted) {
                            openAlertDialogWithTwoAnsPCIFAIL(
                                getString(R.string.cell_found_title),
                                getString(R.string.good_cell2_pcifail_message),
                                getString(R.string.good_cell2_pcifail_title_if_yes),
                                getString(R.string.good_cell2_pcifail_message_if_yes)
                            )
                            isConnected = false
                        } else {
                            openAlertDialog(
                                "Handover already completed!",
                                "You have already connected to a cell with good signal condition." +
                                        "\n No need to handover to another cell"
                            )
                        }
                    }else{
                        openAlertDialog("Handover is not possible!",
                            "Finish attach procedure first. You have to be connected to cell to do a handover")
                    }
                }

                EGameEvent.MSG1 -> {
                        inactivityTimer.startTimer()
                        timerTextView.setBackgroundColor(Color.GREEN)
                }

                EGameEvent.ATTACHFINISHED -> {
                    soundPlayer.playSound(R.raw.win)
                    setProcedureStatus(ProcedureStatus.CONNECTED)
                    setCellPCI("PCI\n12", 12)
                    isConnected = true
                    //isMsg1Scanned = false
                    isAttachCompleted = true
                    inactivityTimer.resetTimer()
                    inactivityTimer.startTimer()
                }

                EGameEvent.CELL2 -> {
                    openAlertDialogWithTwoAnsBadCells(
                            getString(R.string.lock_unlock_cell2_barred_title_DRVI52A),
                            getString(R.string.lock_unlock_cell2_barred_message_DRVI52A),
                            getString(R.string.lock_unlock_cell2_barred_title_if_yes),
                            getString(R.string.lock_unlock_cell3_barred_message_if_yes)
                        )
                }

                EGameEvent.CELL3 -> {
                    openAlertDialogWithTwoAnsBadCells(
                            getString(R.string.lock_unlock_cell3_barred_title_DRVI52D),
                            getString(R.string.lock_unlock_cell3_barred_message_DRVI52D),
                            getString(R.string.lock_unlock_cell3_barred_title_if_yes),
                            getString(R.string.lock_unlock_cell3_barred_message_if_yes)
                        )
                }
                EGameEvent.CELL4 -> {
                    openAlertDialogWithTwoAnsBadCells(
                            getString(R.string.lock_unlock_cell4_bad_caps_title_DRVI52C),
                            getString(R.string.lock_unlock_cell4_bad_caps_message_DRVI52C),
                            getString(R.string.lock_unlock_cell4_bad_caps_title_if_yes),
                            getString(R.string.lock_unlock_cell4_bad_caps_message_if_yes)
                        )
                }

                EGameEvent.MEAS1 -> {
                    if(isAttachCompleted) {
                        if (!isHandoverCompleted) {
                            openAlertDialogWithTwoAnsHoSucc(
                                getString(R.string.cell_found_title),
                                getString(R.string.good_cell1good_message),
                                getString(R.string.good_cell1good_message_if_yes_title),
                                getString(R.string.good_cell1good_message_if_yes)
                            )
                            timerTextView.setBackgroundColor(Color.GREEN)
                        }else{
                            openAlertDialog(
                                "Handover already completed!",
                                "You have already connected to a cell with good signal condition." +
                                        "\n No need to handover to another cell"
                            )
                        }
                    }else{
                        openAlertDialog("Handover is not possible!",
                            "Finish attach procedure first. You have to be connected to cell to do a handover")
                    }
                }

                EGameEvent.MEAS2 -> {
                    if(isAttachCompleted){
                        if(!isHandoverCompleted) {
                            openAlertDialogWithTwoAnsBadCells(
                                getString(R.string.cell_found_title),
                                getString(R.string.cell3bad),
                                getString(R.string.cell_bad_ho_failed_title),
                                getString(R.string.cell3bad_answer_yes)
                            )
                            timerTextView.setBackgroundColor(Color.GREEN)
                        }else{
                            openAlertDialog("Handover already completed!",
                                "You have already connected to a cell with good signal condition." +
                                        "\n No need to handover to another cell")
                        }
                    }else{
                        openAlertDialog("Handover is not possible!",
                            "Finish attach procedure first. You have to be connected to cell to do a handover")
                    }
                }

                EGameEvent.MEAS3 -> {
                    if(isAttachCompleted) {
                        if (!isHandoverCompleted) {
                            openAlertDialogWithTwoAnsBadCells(
                                getString(R.string.cell_found_title),
                                getString(R.string.cell4bad),
                                getString(R.string.cell_bad_ho_failed_title),
                                getString(R.string.cell4bad_answer_yes)
                            )
                            timerTextView.setBackgroundColor(Color.GREEN)
                        } else {
                            openAlertDialog(
                                "Handover already completed!",
                                "You have already connected to a cell with good signal condition.\n No need to handover to another cell"
                            )
                        }
                    }else{
                        openAlertDialog("Handover is not possible!",
                            "Finish attach procedure first. You have to be connected to cell to do a handover")
                    }
                }

                EGameEvent.CA -> {
                    if(lastConnectedPci == 21){
                        soundPlayer.playSound(R.raw.win_end_game)
                        setGameMessage(getString(R.string.ca_done_congratulation_message))
                        congratsMessage()
                        inactivityTimer.stopTimer()
                        timerTextView.setBackgroundColor(Color.GREEN)
                        procInfoTextView.text = "WELL"
                        signalQualityTextView.text = "DONE"
                    }else{
                        soundPlayer.playSound(R.raw.bad_beep)
                        setGameMessage("It looks like you have't finnished HO procedure")
                    }
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
