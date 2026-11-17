package com.gikoko.giai

import android.os.Handler
import android.os.Looper

/** Background job events (3D render, termux runs) - main page shows only a small status chip. */
object JobBus {
    data class Event(val type: String, val msg: String, val path: String? = null, val progress: Int = -1)

    interface Listener { fun onEvent(e: Event) }

    private val main = Handler(Looper.getMainLooper())

    @Volatile var listener: Listener? = null
    @Volatile var status: String = ""

    fun post(e: Event) {
        status = if (e.progress in 0..99) e.msg + " " + e.progress + "%" else e.msg
        main.post { listener?.onEvent(e) }
    }
}
