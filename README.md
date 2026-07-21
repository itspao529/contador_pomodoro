# Contador Pomodoro 🍅

Aplicación de productividad para Android que combina un **temporizador Pomodoro** con **gestión de tareas**, desarrollada en **Kotlin + XML** sobre Android Studio, como trabajo de investigación #01 de la materia Desarrollo de Software para Móviles — Universidad Don Bosco.

## Video de exposición

📹 [Ver video explicativo](https://youtu.be/VImYJ3pJouU)

---

## Descripción

La aplicación permite al usuario crear tareas, seleccionar una como "tarea activa", y trabajar en ella usando la técnica Pomodoro: sesiones de 25 minutos de concentración, con controles para iniciar, pausar, reanudar y reiniciar la cuenta regresiva. Al completarse una sesión, se notifica al usuario y queda registrada en un historial asociada a la tarea sobre la que se trabajó.

## Características principales

- ➕ Agregar, seleccionar, completar y eliminar tareas
- ⏱️ Temporizador Pomodoro (25 min por defecto) con indicador de progreso visual
- 🔔 Notificación al completar una sesión
- 📜 Historial de sesiones completadas, con fecha y tarea asociada
- 📊 Resumen en tiempo real de tareas pendientes y sesiones completadas
- 💾 Persistencia de datos: las tareas y el historial se conservan al cerrar la app
- 🔄 Manejo robusto del ciclo de vida: nada se pierde ni se duplica al rotar la pantalla, minimizar la app, o si el sistema destruye el proceso en segundo plano

## Tecnologías

- **Kotlin** + **XML** (Vistas nativas de Android)
- **View Binding** — acceso seguro a vistas sin `findViewById`
- **ViewModel** (Android Jetpack) — retención de estado ante cambios de configuración
- **SharedPreferences** + **Gson** — persistencia de datos en formato JSON
- **CountDownTimer** — lógica del temporizador
- **NotificationCompat** — notificaciones al completar sesiones

---

## Arquitectura y decisiones de diseño

### Generación dinámica de vistas

Las tareas y el historial no tienen una cantidad fija de elementos, por lo que sus vistas se generan dinámicamente en tiempo de ejecución. Se definieron dos layouts reutilizables (`item_task.xml` e `item_history.xml`), que se inflan mediante View Binding (`ItemTaskBinding.inflate(...)`) por cada elemento de las listas, y se agregan a sus respectivos contenedores. Antes de cada redibujado se limpia el contenedor con `removeAllViews()` para evitar duplicados.

### Ciclo de vida de la Activity

| Callback | Qué hace la app | Por qué ahí |
|---|---|---|
| `onCreate` | Infla el binding, restaura el estado guardado en `SharedPreferences`, configura los listeners de botones | Es el único punto garantizado de inicialización de la Activity |
| `onStart` | Se suscribe al `ViewModel` (asigna el `listener` del temporizador) | Se reactiva cada vez que la pantalla vuelve a ser visible, no solo la primera vez |
| `onStop` | Guarda el estado en `SharedPreferences` y libera el `listener` (`= null`) | Es el último punto seguro antes de que el sistema pueda destruir el proceso; libera lo que se activó en `onStart`, evitando fugas de memoria |
| `onDestroy` | No libera recursos del temporizador directamente | El `CountDownTimer` vive en el `ViewModel`, que se encarga de cancelarlo en su propio `onCleared()` cuando el ViewModel deja de estar asociado a ninguna pantalla |

Se mantiene simetría entre lo que se activa y lo que se libera: el listener que se asigna en `onStart` se limpia en `onStop`.

### Retención de estado ante rotación (ViewModel)

Todo el estado —tareas, historial, y datos del temporizador— vive en `PomodoroViewModel`, obtenido con el delegado `by viewModels()`. Esta instancia sobrevive a la recreación de la Activity durante una rotación de pantalla, por lo que el `CountDownTimer` sigue corriendo sin detenerse. Para evitar que se dispare un segundo temporizador en paralelo tras la recreación, `startTimer()` valida `if (isRunning) return` antes de crear una nueva instancia.

### Tiempo real transcurrido en segundo plano

Guardar únicamente los segundos restantes es insuficiente: si el usuario cierra la app con "10 minutos restantes" y regresa 20 minutos después, ese valor guardado ya no refleja la realidad — el temporizador debió haber terminado hace 10 minutos.

Para resolverlo, en lugar de guardar cuánto tiempo falta, se guarda el **momento exacto en el que el temporizador debe finalizar**:

```kotlin
endTimeMillis = System.currentTimeMillis() + timeLeft
```

Al restaurar el estado, se recalcula el tiempo real restante comparando esa marca contra el momento actual:

```kotlin
val restante = endTime - System.currentTimeMillis()
```

- Si `restante > 0`, el temporizador se retoma con el tiempo real que falta.
- Si `restante <= 0`, significa que la sesión se completó mientras la app no estaba visible (incluso si el proceso fue destruido por el sistema operativo). En ese caso, la sesión se registra en el historial antes de reiniciar el temporizador, para no perder el progreso del usuario.

---

## Persistencia de datos: análisis comparativo

Se investigaron tres técnicas de persistencia disponibles en Android:

| Técnica | Ventajas | Desventajas |
|---|---|---|
| **SharedPreferences** | Simple, síncrona, ideal para pares clave-valor pequeños, no requiere corrutinas | No está pensada para estructuras de datos complejas ni grandes volúmenes; el acceso síncrono puede bloquear el hilo principal si el volumen crece |
| **DataStore** | Alternativa moderna recomendada por Google, asíncrona por diseño (usa `Flow`/corrutinas), más segura ante corrupción de datos | Requiere manejar corrutinas y `Flow`, lo que añade complejidad para un proyecto de este tamaño |
| **Archivos con serialización JSON manual** | Control total sobre el formato y la ubicación de los datos | Hay que gestionar manualmente la lectura/escritura de archivos, manejo de errores de I/O, y no ofrece ninguna ventaja adicional frente a las otras dos opciones para este caso de uso |

**Técnica elegida: `SharedPreferences` + serialización JSON con Gson.**

**Justificación:** el volumen de datos de esta aplicación es pequeño — una lista de tareas y un historial de sesiones de un único usuario, sin necesidad de consultas complejas ni de un volumen de datos que justifique una base de datos completa. `SharedPreferences` permite guardar y restaurar el estado de forma síncrona y sencilla en los puntos clave del ciclo de vida (`onStop` / `onCreate`), sin necesidad de introducir corrutinas o `Flow`, lo cual habría añadido complejidad innecesaria para el alcance del proyecto. Para las listas de tareas y el historial, que son estructuras más complejas que un simple par clave-valor, se usa la librería **Gson** para serializarlas a una cadena JSON antes de guardarlas, y deserializarlas al restaurar el estado:

```kotlin
// Guardar
.putString("tasks", gson.toJson(viewModel.tasks))

// Restaurar
val tipo = object : TypeToken<List<Task>>() {}.type
val lista: List<Task> = gson.fromJson(tasksJson, tipo)
```

---

## Pruebas realizadas

Se validó el correcto funcionamiento de la aplicación ante los siguientes escenarios (evidencia disponible en el video de exposición):

1. **Rotación de pantalla con el temporizador en marcha** — el tiempo restante y el progreso continúan sin reiniciarse ni duplicarse.
2. **Minimizar la app durante varios minutos** — al regresar, el tiempo restante refleja el tiempo real transcurrido.
3. **Opción de desarrollador "No conservar actividades"** (simula la destrucción del proceso) — se verificó que, si el temporizador se completa mientras la app está en segundo plano y el proceso es destruido, la sesión se registra correctamente en el historial al regresar.

---

## Cómo ejecutar el proyecto

1. Clonar el repositorio:
```bash
   git clone https://github.com/itspao529/contador_pomodoro.git
```
2. Abrir el proyecto en **Android Studio**.
3. Sincronizar dependencias de Gradle (`File > Sync Project with Gradle Files`).
4. Ejecutar en un emulador o dispositivo físico con **API 23 o superior**.

---

**Universidad Don Bosco** — Facultad de Ingeniería — Escuela de Computación
Desarrollo de Software para Móviles — Trabajo de investigación #01
