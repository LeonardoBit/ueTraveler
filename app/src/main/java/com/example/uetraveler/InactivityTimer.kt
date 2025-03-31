package com.example.uetraveler

import android.os.CountDownTimer

class InactivityTimer {
    private val timeLeftInMilliSeconds = 10000L

    private var countDownTimer: CountDownTimer? = null
    var isTimerRunning = false
        private set

    private var timerFinishedCallbacks: MutableList<() -> Unit> = mutableListOf()
    private var timerTickCallbacks: MutableList<(millisUntilFinished: Long) -> Unit> = mutableListOf()

    fun registerTickCallback(callback: (millisUntilFinished: Long) -> Unit) {
        timerTickCallbacks.add(callback)
    }

    fun registerFinishedCallback(callback: () -> Unit) {
        timerFinishedCallbacks.add(callback)
    }

    fun startTimer() {
        if(!isTimerRunning){
            countDownTimer = object: CountDownTimer(timeLeftInMilliSeconds,1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timerTickCallbacks.forEach { it(millisUntilFinished) }
                }

                override fun onFinish() {
                    timerFinishedCallbacks.forEach { it() }
                    stopTimer()
                }
            }.start()
            isTimerRunning = true
        }

    }

    fun stopTimer(){
        countDownTimer?.cancel()
        isTimerRunning = false
    }
}