package com.example.uetraveler

object NFCTag {
    const val START = "START"
    const val PAUSE = "PAUSE"
    const val RESET = "RESET"
    const val LOST = "LOST"
    const val PCIFAIL = "PCIFAIL"
    const val CA = "CA"
    const val CAPS = "CAPS"
    //Attach procedure
    const val MSG1 = "MSG1"
    const val MSG1BAD = "MSG1BAD"
    const val MSG2 = "MSG2"
    const val MSG3 = "MSG3"
    const val RRCSC = "RRCSC"
    const val SMC = "SMC"
    //Cells for succ handover or failed HO
    const val MEAS1F1 = "MEAS1F1"
    const val MEAS1F3 = "MEAS1F3"
    const val MEAS2 = "MEAS2"
    const val MEAS3 = "MEAS3"

    //Bad cells for attach procedure (MSG1 start attach procedure)
    const val CELL2 = "CELL2"
    const val CELL3 = "CELL3"
    const val CELL4 = "CELL4"


}
