package com.example.uetraveler

import android.util.Log

class GameHandler(private var inactivityTimer: InactivityTimer) {

    private var eventHandlers: MutableList<IGameEventHandler> = mutableListOf()

    fun registerEventHandler(handler: IGameEventHandler) {
        eventHandlers.add(handler)
    }

    fun handleTag(tag: String) {
        when (tag.uppercase()) {
            NFCTag.START -> {
                inactivityTimer.resetTimer()
                inactivityTimer.startTimer()
                sendEvent(EGameEvent.START)
            }
            NFCTag.PAUSE -> {
                inactivityTimer.stopTimer()
                sendEvent(EGameEvent.PAUSE)
            }
            NFCTag.RESET -> {
                inactivityTimer.resetTimer()
                inactivityTimer.startTimer()
                sendEvent(EGameEvent.RESET)
            }
            NFCTag.LOST -> {
                sendEvent(EGameEvent.UE_LOST)
            }
            else -> {
                Log.e("GameHandler", "Unrecognized NFC command: $tag")
            }
        }
    }

    private fun sendEvent(event: EGameEvent) {
        eventHandlers.forEach { it.handleGameEvent(event) }
    }
}
