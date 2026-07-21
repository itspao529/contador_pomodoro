package com.example.pomodoroapp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Paint

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pomodoroapp.databinding.ActivityMainBinding
import com.example.pomodoroapp.databinding.ItemHistoryBinding
import com.example.pomodoroapp.databinding.ItemTaskBinding

// El estado (tareas, historial, temporizador) vive en el ViewModel.
// Esta Activity solo pinta la UI y reenvía los clics.
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "pomodoro_sessions"
    }

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy {
        getSharedPreferences("pomodoro", MODE_PRIVATE)
    }
    private val viewModel: PomodoroViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restaurarEstado()

        crearCanalDeNotificaciones()
        pedirPermisoNotificacionesSiHaceFalta()

        binding.btnAddTask.setOnClickListener {
            val taskName = binding.etNewTask.text.toString().trim()

            if (taskName.isEmpty()) {
                Toast.makeText(this, "Ingrese una tarea", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (viewModel.tasks.any { it.name.equals(taskName, true) }) {
                Toast.makeText(this, "La tarea ya existe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addTask(Task(taskName))
            binding.etNewTask.text.clear()
            drawTasks()
            guardarEstado()
        }

        binding.btnStart.setOnClickListener { viewModel.startTimer() }
        binding.btnPause.setOnClickListener { viewModel.pauseTimer() }
        binding.btnResume.setOnClickListener { viewModel.resumeTimer() }
        binding.btnReset.setOnClickListener { viewModel.resetTimer() }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        // Nos suscribimos al ViewModel para recibir sus actualizaciones.
        viewModel.listener = object : PomodoroViewModel.TimerListener {
            override fun onTick(timeLeft: Long, totalTime: Long) {
                updateTimerUI(timeLeft, totalTime)
            }

            override fun onSessionCompleted(taskName: String) {
                onSesionCompletada(taskName)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")

        guardarEstado()

        viewModel.listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun drawTasks() {
        binding.containerTasks.removeAllViews()

        viewModel.tasks.forEach { task ->
            val itemBinding = ItemTaskBinding.inflate(LayoutInflater.from(this))

            itemBinding.cbCompletada.isChecked = task.completed

            if (task.completed) {
                itemBinding.tvNombreTarea.paintFlags =
                    itemBinding.tvNombreTarea.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                itemBinding.tvNombreTarea.alpha = 0.5f
            } else {
                itemBinding.tvNombreTarea.paintFlags =
                    itemBinding.tvNombreTarea.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemBinding.tvNombreTarea.alpha = 1f
            }

            val esActiva = task.name == viewModel.activeTaskName
            itemBinding.root.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (esActiva) R.color.task_active_bg else R.color.task_default_bg
            )
            itemBinding.tvNombreTarea.text =
                if (esActiva) "\u25B6 ${task.name} (activa)" else task.name

            // Tocar el nombre marca la tarea como activa para la sesión actual.
            itemBinding.tvNombreTarea.setOnClickListener {
                viewModel.setActiveTask(task.name)
                drawTasks()
                guardarEstado()
            }

            itemBinding.cbCompletada.setOnCheckedChangeListener { _, isChecked ->

                task.completed = isChecked

                if (isChecked) {
                    itemBinding.tvNombreTarea.paintFlags =
                        itemBinding.tvNombreTarea.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    itemBinding.tvNombreTarea.alpha = 0.5f
                } else {
                    itemBinding.tvNombreTarea.paintFlags =
                        itemBinding.tvNombreTarea.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    itemBinding.tvNombreTarea.alpha = 1f
                }

                updateSummary()
                guardarEstado()
            }

            itemBinding.btnEliminar.setOnClickListener {
                viewModel.removeTask(task)
                drawTasks()
                guardarEstado()
            }

            binding.containerTasks.addView(itemBinding.root)
        }

        binding.tvEmptyTasks.visibility =
            if (viewModel.tasks.isEmpty()) View.VISIBLE else View.GONE

        updateSummary()
    }

    private fun updateSummary() {
        val pending = viewModel.tasks.count { !it.completed }
        binding.tvSummary.text =
            "Pendientes: $pending | Sesiones completadas: ${viewModel.sessionsCompleted}"
    }

    private fun drawHistory() {
        binding.containerHistory.removeAllViews()

        viewModel.history.forEach { sesion ->
            val itemBinding = ItemHistoryBinding.inflate(LayoutInflater.from(this))
            itemBinding.tvTareaHistorial.text = sesion.taskName
            itemBinding.tvFechaHora.text = sesion.dateTime
            binding.containerHistory.addView(itemBinding.root)
        }

        binding.tvEmptyHistory.visibility =
            if (viewModel.history.isEmpty()) View.VISIBLE else View.GONE
    }

    // Se llama cuando el temporizador llega a 00:00.
    private fun onSesionCompletada(taskName: String) {
        mostrarNotificacionSesionCompletada(taskName)
        drawHistory()
        updateSummary()
        binding.progressSession.progress = 100

        guardarEstado()
    }

    private fun crearCanalDeNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sesiones Pomodoro",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun pedirPermisoNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val yaConcedido = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!yaConcedido) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun mostrarNotificacionSesionCompletada(taskName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("¡Sesión completada!")
            .setContentText("Terminaste 25 min trabajando en: $taskName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val puedeNotificar = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (puedeNotificar) {
            NotificationManagerCompat.from(this).notify(1, notification)
        }

        Toast.makeText(this, "Sesión completada: $taskName", Toast.LENGTH_LONG).show()
    }

    private fun updateTimerUI(timeLeft: Long, totalTime: Long) {
        val minutes = (timeLeft / 1000) / 60
        val seconds = (timeLeft / 1000) % 60

        binding.tvTimeRemaining.text = String.format("%02d:%02d", minutes, seconds)

        val progress = ((totalTime - timeLeft) * 100 / totalTime).toInt()
        binding.progressSession.progress = progress
    }

    private fun guardarEstado() {

        val gson = Gson()

        prefs.edit()
            .putLong("timeLeft", viewModel.timeLeft)
            .putBoolean("running", viewModel.isRunning)
            .putLong("endTime", viewModel.endTimeMillis)

            .putInt("sessionsCompleted", viewModel.sessionsCompleted)

            .putString("activeTask", viewModel.activeTaskName)

            .putString("tasks", gson.toJson(viewModel.tasks))

            .putString("history", gson.toJson(viewModel.history))

            .apply()
    }

    private fun restaurarEstado() {

        val gson = Gson()

        val running = prefs.getBoolean("running", false)

        val sesiones = prefs.getInt("sessionsCompleted", 0)
        viewModel.restoreSessions(sesiones)

        val tareaActiva = prefs.getString("activeTask", null)
        viewModel.restoreActiveTask(tareaActiva)

        val tasksJson = prefs.getString("tasks", null)

        if (tasksJson != null) {

            val tipo = object : TypeToken<List<Task>>() {}.type

            val lista: List<Task> = gson.fromJson(tasksJson, tipo)

            viewModel.restoreTasks(lista)
        }

        val historyJson = prefs.getString("history", null)

        if (historyJson != null) {

            val tipo = object : TypeToken<List<SessionRecord>>() {}.type

            val lista: List<SessionRecord> = gson.fromJson(historyJson, tipo)

            viewModel.restoreHistory(lista)
        }

        val tiempoGuardado = prefs.getLong("timeLeft", viewModel.totalTime)
        viewModel.restoreTime(tiempoGuardado)
        updateTimerUI(tiempoGuardado, viewModel.totalTime)

        if (running) {

            val endTime = prefs.getLong("endTime", 0)

            val restante = endTime - System.currentTimeMillis()

            if (restante > 0) {

                viewModel.restoreTime(restante)
                updateTimerUI(restante, viewModel.totalTime)
                viewModel.startTimer()

            } else {

                viewModel.resetTimer()

            }
        }

        drawTasks()
        drawHistory()
        updateSummary()
    }
}
