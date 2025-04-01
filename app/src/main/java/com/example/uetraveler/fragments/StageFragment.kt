package com.example.uetraveler.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uetraveler.R

class StageFragment : Fragment() {

    private lateinit var fragmentManager: FragmentManager
    private var currentFragmentIndex = 0
    private val fragments = listOf(Fragment1(), Fragment2(), Fragment3()) // Replace with your fragments

    companion object {
        fun newInstance() = StageFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager = supportFragmentManager
        showFragment(currentFragmentIndex)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_stage, container, false)
    }

    private fun showFragment(index: Int) {
        val transaction: FragmentTransaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragments[index])
        transaction.commit()
    }
}
