package de.voculupo.historicalcamplayout.platform

import android.util.Log

actual fun platformDebugLog(
    tag: String,
    message: String,
) {
    Log.d(tag, message)
}
