package com.example.pomodoroapp

import android.os.CountDownTimer
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Guarda el estado de la app (tareas, historial y temporizador)
// para que no se pierda ni se reinicie al rotar la pantalla.
class PomodoroViewModel : ViewModel() {

    interface TimerListener {
        fun onTick(timeLeft: Long, totalTime: Long)
        fun onSessionCompleted(taskName: String)
    }

    var listener: TimerListener? = null

    val totalTime = 25 * 60 * 1000L
    var timeLeft: Long = totalTime
        private set
    var isRunning: Boolean = false
        private set
    var sessionsCompleted: Int = 0
        private set

    var endTimeMillis: Long = 0L
    private var countDownTimer: CountDownTimer? = null

    val tasks = mutableListOf<Task>()
    val history = mutableListOf<SessionRecord>()
    var activeTaskName: String? = null
        private set

    fun setActiveTask(name: String?) {
        activeTaskName = if (activeTaskName == name) null else name
    }

    fun addTask(task: Task) {
        tasks.add(task)
    }

    fun removeTask(task: Task) {
        tasks.remove(task)
        if (activeTaskName == task.name) activeTaskName = null
    }

    fun startTimer() {
        if (isRunning) return
        isRunning = true

        endTimeMillis = System.currentTimeMillis() + timeLeft

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                listener?.onTick(timeLeft, totalTime)
            }

            override fun onFinish() {
                timeLeft = 0
                isRunning = false
                val tarea = registrarSesionCompletada()
                listener?.onTick(timeLeft, totalTime)
                listener?.onSessionCompleted(tarea)
            }
        }.start()
    }

    fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
    }

    fun resumeTimer() {
        if (isRunning) return
        startTimer()
    }

    fun resetTimer() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeft = totalTime
        listener?.onTick(timeLeft, totalTime)
    }

    // Registra la sesión completada: suma al contador, la asocia
    // a la tarea activa y la guarda en el historial.
    private fun registrarSesionCompletada(): String {
        sessionsCompleted++
        val nombreTarea = activeTaskName ?: "Sin tarea asignada"
        val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date())
        history.add(0, SessionRecord(nombreTarea, fechaHora))
        return nombreTarea
    }

    fun restoreTime(time: Long) {
        timeLeft = time
    }

    fun restoreSessions(count: Int) {
        sessionsCompleted = count
    }

    fun restoreActiveTask(task: String?) {
        activeTaskName = task
    }

    fun restoreTasks(list: List<Task>) {
        tasks.clear()
        tasks.addAll(list)
    }

    fun restoreHistory(list: List<SessionRecord>) {
        history.clear()
        history.addAll(list)
    }

    // Se libera el temporizador cuando ya no hay ninguna pantalla usándolo.
    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
        countDownTimer = null
    }
}
