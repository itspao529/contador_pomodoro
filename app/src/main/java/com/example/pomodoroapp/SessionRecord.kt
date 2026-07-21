package com.example.pomodoroapp

// Representa una sesión Pomodoro ya completada (para el historial)
data class SessionRecord(
    val taskName: String,
    val dateTime: String
)
