package com.example.uetraveler.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import com.example.uetraveler.R

class QuizFragment(
    private var responseCallback: (Boolean) -> Unit
) : Fragment() {
    private var answers: List<String>? = null
    private var correctAnswerIndex: Int? = null

    private lateinit var questionView: TextView
    private lateinit var answerButtons: List<RadioButton>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_quiz, container, false)
        questionView = view.findViewById(R.id.questionTextView)
        answerButtons = listOf(
            view.findViewById<RadioButton>(R.id.btnA).also {
                it.setOnClickListener {
                    this.responseCallback(this.correctAnswerIndex?.equals(0) ?: false)
                }
            },
            view.findViewById<RadioButton>(R.id.btnB).also {
                it.setOnClickListener {
                    this.responseCallback(this.correctAnswerIndex?.equals(1) ?: false)
                }
            },
            view.findViewById<RadioButton>(R.id.btnC).also {
                it.setOnClickListener {
                    this.responseCallback(this.correctAnswerIndex?.equals(2) ?: false)
                }
            }
        )
        return view
    }

    fun setQuestion(question: String, answers: List<String>, correctAnswerIndex: Int) {
        questionView.text = question
        this.answers = answers
        this.correctAnswerIndex = correctAnswerIndex

        answerButtons.indices.forEach { i ->
            answerButtons[i].text = this.answers!![i]
        }
    }
}
