package de.voculupo.historicalcamplayout.platform

actual fun platformDebugLog(
    tag: String,
    message: String,
) {
    println("[$tag] $message")
}
