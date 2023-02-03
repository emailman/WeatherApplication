package edu.mailman.weatherapplication.models

data class Sys (
    val type: Int,
    val id: Int,
    val country: String,
    val sunrise: Int,
    val sunset: Int,
    ) : java.io.Serializable