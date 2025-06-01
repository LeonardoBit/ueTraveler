package com.example.uetraveler

import android.util.Log
import androidx.appcompat.app.AlertDialog

class GameHandler(private var inactivityTimer: InactivityTimer,
                  private val sequenceTriggers: Map<String, List<String>>,
                  private val sequenceStatus: Map<String, String>,
                  private val soundPlayer: SoundPlayer,
                  private val sendStatusUpdate: (String) -> Unit) {

    private var eventHandlers: MutableList<IGameEventHandler> = mutableListOf()

    private var currentStep = 0
    private var isSequenceMode = false
    private var ueLostDone = false
    private var currentSequence: List<String>? = null

    val sequenceFinishedEvent: Map<String, EGameEvent> = mapOf(
        NFCTag.SMC to EGameEvent.ATTACHFINISHED
    )

    fun registerEventHandler(handler: IGameEventHandler) {
        eventHandlers.add(handler)
    }

    fun handleTag(tag: String,isConnected: Boolean) {
        val scannedTag = tag.uppercase()

        if (isConnected) {
            if (isSequenceMode) {
                handleSequenceTag(scannedTag)
                return
            }
        }
        when (scannedTag) {
            NFCTag.START -> {
                //inactivityTimer.resetTimer()
                //inactivityTimer.startTimer()
                sendEvent(EGameEvent.START)
            }

            NFCTag.PAUSE -> {
                //inactivityTimer.stopTimer()
                sendEvent(EGameEvent.PAUSE)
            }

            NFCTag.RESET -> {
                //inactivityTimer.resetTimer()
                //inactivityTimer.startTimer()
                sendEvent(EGameEvent.RESET)
            }

            NFCTag.LOST -> {
                if (!ueLostDone) {
                    sendEvent(EGameEvent.LOST)
                } else {
                    sendEvent(EGameEvent.HANDOVER)
                }
            }

            NFCTag.MSG1 -> {
                //inactivityTimer.setNewTime(30000L)
                //inactivityTimer.startTimer()
                sendEvent(EGameEvent.MSG1)
            }

            NFCTag.CELL2 -> {
                sendEvent(EGameEvent.CELL2)
            }

            NFCTag.CELL3 -> {
                sendEvent(EGameEvent.CELL3)
            }

            NFCTag.CELL4 -> {
                sendEvent(EGameEvent.CELL4)
            }

            NFCTag.MEAS1 -> {
                sendEvent(EGameEvent.MEAS1)
            }

            NFCTag.MEAS2 -> {
                sendEvent(EGameEvent.MEAS2)
            }

            NFCTag.MEAS3 -> {
                sendEvent(EGameEvent.MEAS3)
            }

            NFCTag.PCIFAIL -> {
                sendEvent(EGameEvent.PCIFAIL)
            }

            NFCTag.CA -> {
                sendEvent(EGameEvent.CA)
            }

            NFCTag.CAPS -> {
                sendEvent(EGameEvent.CAPS)
            }

            else -> {
                Log.e("GameHandler", "Unrecognized NFC command: $scannedTag")
            }
        }

        if (isConnected) {
            if (isSequenceMode && currentSequence != null) {
                handleSequenceTag(scannedTag)
                return
            }

            if (sequenceTriggers.containsKey(scannedTag)) {
                currentSequence = sequenceTriggers[scannedTag]
                isSequenceMode = true
                currentStep = 0
                Log.d("GameHandler", "Sequence started with $scannedTag")
                handleSequenceTag(scannedTag)
            }
        }
    }
    private fun handleSequenceTag(scannedTag: String) {
        val sequence = currentSequence ?: return

        if (currentStep >= sequence.size) {
            // Safety check: shouldn't happen
            Log.w("GameHandler", "Current step $currentStep is out of sequence bounds")
            return
        }

        val expectedTag = sequence[currentStep].uppercase()

        if (scannedTag == expectedTag) {
            sequenceStatus[scannedTag]?.let { status ->
                Log.d("GameHandler", "Sending status for $scannedTag: $status")
                sendStatusUpdate(status)  // Send proper step status
            }
            val status123 = sequenceStatus[scannedTag]

            Log.d("GameHandler", " $status123 at step $currentStep")
            Log.d("GameHandler", "Correct tag scanned: $scannedTag at step $currentStep")

            // This is where the status should be sent
            soundPlayer.playSound(R.raw.good_beep)
            currentStep++

            if (currentStep == sequence.size) {
                // Sequence complete!
                Log.d("GameHandler", "Sequence completed successfully")
                isSequenceMode = false
                currentSequence = null
                currentStep = 0
                sequenceFinishedEvent[scannedTag]?.let { sendEvent(it) }
            }
        } else {
            // Wrong tag scanned during sequence
            soundPlayer.playSound(R.raw.bad_beep)
            Log.e("GameHandler", "Wrong tag! Expected $expectedTag but scanned $scannedTag")
            sendStatusUpdate("Wrong tag! Expected: $expectedTag scanned: $scannedTag")
        }
        //inactivityTimer.startTimer()
    }


    fun resetSequenceMode(){
        currentStep = 0
        isSequenceMode = false
        currentSequence = null
    }

    fun setUeLostDone(ueLostStatus: Boolean){
        ueLostDone = ueLostStatus
    }

    fun getUeLostDone(): Boolean {
        return ueLostDone
    }

    private fun sendEvent(event: EGameEvent) {
        eventHandlers.forEach { it.handleGameEvent(event) }
    }
}
