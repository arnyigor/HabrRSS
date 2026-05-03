package com.arny.habrrss.core.platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform