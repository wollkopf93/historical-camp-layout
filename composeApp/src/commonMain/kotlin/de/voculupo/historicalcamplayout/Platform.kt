package de.voculupo.historicalcamplayout

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
