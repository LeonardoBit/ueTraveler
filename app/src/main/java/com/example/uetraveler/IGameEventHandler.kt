package com.example.uetraveler

interface IGameEventHandler {
    fun handleGameEvent(event: EGameEvent)
}

enum class EGameEvent {
    INVALID,
    START,
    LOST,
    RESET,
    PAUSE,
    QUIZ,
    MSG1,
    HANDOVER,
    ATTACHFINISHED,
    MEAS1,
    MEAS2,
    MEAS3
}
