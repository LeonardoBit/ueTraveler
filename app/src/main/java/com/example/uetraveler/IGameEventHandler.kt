package com.example.uetraveler

interface IGameEventHandler {
    fun handleGameEvent(event: EGameEvent)
}

enum class EGameEvent {
    START,
    RESET,
    PAUSE,
    QUIZ,
    MSG1,
    HANDOVER,
    CA,
    CAPS,
    LOST,
    PCIFAIL,
    ATTACHFINISHED,
    STARTATTACH,
    MEAS1F1,
    MEAS1F3,
    MEAS2,
    MEAS3,
    CELL2,
    CELL3,
    CELL4,
}
