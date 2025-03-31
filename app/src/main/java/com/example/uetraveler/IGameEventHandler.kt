package com.example.uetraveler

interface IGameEventHandler {
    fun handleGameEvent(event: EGameEvent)
}

enum class EGameEvent {
    INVALID,
    START,
    UE_LOST,
    RESET,
    PAUSE
}
