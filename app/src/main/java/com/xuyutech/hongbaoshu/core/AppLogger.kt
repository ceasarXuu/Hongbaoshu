package com.xuyutech.hongbaoshu.core

import android.util.Log

object AppLogger {
    fun d(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
    }

    fun i(tag: String, message: String) {
        runCatching { Log.i(tag, message) }
    }

    fun w(tag: String, message: String) {
        runCatching { Log.w(tag, message) }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable == null) {
                Log.e(tag, message)
            } else {
                Log.e(tag, message, throwable)
            }
        }
    }
}
