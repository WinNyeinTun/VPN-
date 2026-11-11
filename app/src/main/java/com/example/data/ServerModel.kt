package com.example.data

data class ServerModel(
    val id: String,
    val name: String,
    val protocol: String,
    val config: String,
    val location: String = "Global",
    val ping: Int = 0,
    val health: String = "unknown"
)
