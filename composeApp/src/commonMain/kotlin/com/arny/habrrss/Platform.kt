package com.arny.habrrss

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform