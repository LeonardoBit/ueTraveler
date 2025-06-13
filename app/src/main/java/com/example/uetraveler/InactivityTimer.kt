package com.example.uetraveler

import android.os.CountDownTimer

class InactivityTimer(private var totalTimeInMillis: Long = 10000L) {
    private var timeLeftInMillis = totalTimeInMillis


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
        if (!isTimerRunning) {
            countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
                    timerTickCallbacks.forEach { it(millisUntilFinished) }
                }
                override fun onFinish() {
                    isTimerRunning = false
                    timeLeftInMillis = totalTimeInMillis // Reset after finishing
                    timerFinishedCallbacks.forEach { it() }
                }
            }.start()
            isTimerRunning = true
        }
    }

    fun stopTimer(){
        countDownTimer?.cancel()
        isTimerRunning = false
    }

    fun resetTimer() {
        stopTimer()
        timeLeftInMillis = totalTimeInMillis
    }

    fun resumeTimer() {
        if (!isTimerRunning && timeLeftInMillis > 0) {
            startTimer()
        }
    }
    fun setNewTime(newTimeInMillis: Long) {
        stopTimer()
        totalTimeInMillis = newTimeInMillis
        timeLeftInMillis = newTimeInMillis
    }

    fun decreaseTimer(decreaseNumOfSecondsInMillis: Long){
        stopTimer()
        timeLeftInMillis -= decreaseNumOfSecondsInMillis
        startTimer()
    }


}