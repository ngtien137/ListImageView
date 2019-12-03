package com.luza.pickingimagesbar

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*

const val prefixLog = "FTL_LOG"
fun Any.log(message:String){
    Log.e(fixLogTag(this::class.java.simpleName),message)
}

private fun fixLogTag(tag:String): String {
    var logTag = prefixLog+tag
    if (logTag.length>23){
        logTag = logTag.substring(0,22)
    }
    return logTag
}

fun <T> sync(
    doIn: () -> T,
    doOut: (T) -> Unit = {},
    dispatcherIn: CoroutineDispatcher = Dispatchers.IO,
    dispatcherOut: CoroutineDispatcher = Dispatchers.IO
): Job {
    return GlobalScope.launch(dispatcherIn) {
        val data = doIn()
        withContext(dispatcherOut) {
            doOut(data)
        }
    }
}

