package com.vasquez.mitiemposemanal

import android.content.ContentValues
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class EventosActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var autoCompleteTask: AutoCompleteTextView
    private lateinit var tvCategoriaAsociada: TextView
    private lateinit var tvTiempoEnCurso: TextView
    private lateinit var tvSemanaRango: TextView
    private lateinit var layoutResumenCategorias: LinearLayout
    private lateinit var layoutHistorialEventos: LinearLayout
    private lateinit var btnIniciar: Button
    private lateinit var btnFinalizar: Button

    private var tareaSeleccionadaId: Int = -1
    private var eventoEnCursoId: Int = -1
    private var inicioEventoActualStr: String? = null
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eventos)

        dbHelper = DatabaseHelper(this)

        autoCompleteTask = findViewById(R.id.autoCompleteTask)
        tvCategoriaAsociada = findViewById(R.id.tvCategoriaAsociada)
        tvTiempoEnCurso = findViewById(R.id.tvTiempoEnCurso)
        tvSemanaRango = findViewById(R.id.tvSemanaRango)
        layoutResumenCategorias = findViewById(R.id.layoutResumenCategorias)
        layoutHistorialEventos = findViewById(R.id.layoutHistorialEventos)
        btnIniciar = findViewById(R.id.btnIniciarEvento)
        btnFinalizar = findViewById(R.id.btnFinalizarEvento)

        configurarBuscadorTareas()
        verificarEventoEnCurso()
        cargarHistorialEventos()
        cargarResumenSemanal()

        btnIniciar.setOnClickListener {
            if (dbHelper.tieneEventoEnCurso() || eventoEnCursoId != -1) {
                Toast.makeText(this, "Ya hay un evento en curso. Finalízalo antes de iniciar otro.", Toast.LENGTH_SHORT).show()
                actualizarEstadoBotones(enCurso = true)
                return@setOnClickListener
            }

            val nombreInput = autoCompleteTask.text.toString().trim()
            if (nombreInput.isEmpty()) {
                Toast.makeText(this, "Escribe o selecciona una tarea", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tareaExistente = dbHelper.obtenerTareas().find { it.nombre.equals(nombreInput, ignoreCase = true) }

            if (tareaExistente != null) {
                tareaSeleccionadaId = tareaExistente.id
                iniciarNuevoEvento(tareaSeleccionadaId)
            } else {
                mostrarDialogCrearTareaConCategoria(nombreInput)
            }
        }

        btnFinalizar.setOnClickListener {
            finalizarEventoEnCurso()
        }
    }

    override fun onDestroy() {
        detenerTimer()
        super.onDestroy()
    }

    private fun detenerTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun iniciarTimer(inicioStr: String) {
        detenerTimer()
        tvTiempoEnCurso.visibility = View.VISIBLE
        val inicioTime = LocalDateTime.parse(inicioStr, formatter)

        timerRunnable = object : Runnable {
            override fun run() {
                val now = LocalDateTime.now()
                val duracion = Duration.between(inicioTime, now)
                val horas = duracion.toHours()
                val minutos = duracion.toMinutes() % 60
                val segundos = duracion.seconds % 60
                tvTiempoEnCurso.text = String.format(Locale.US, "🟢 EN CURSO • Tiempo: %02d:%02d:%02d", horas, minutos, segundos)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun configurarBuscadorTareas() {
        val tareas = dbHelper.obtenerTareas()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tareas.map { it.nombre })
        autoCompleteTask.setAdapter(adapter)

        autoCompleteTask.setOnItemClickListener { _, _, position, _ ->
            val nombreSeleccionado = adapter.getItem(position)
            val tareaObj = tareas.find { it.nombre == nombreSeleccionado }
            if (tareaObj != null) {
                tareaSeleccionadaId = tareaObj.id
                tvCategoriaAsociada.text = "Categoría: ${tareaObj.nombreCategoria}"
            }
        }
    }

    private fun mostrarDialogCrearTareaConCategoria(nombreNuevaTarea: String) {
        val categorias = dbHelper.obtenerCategorias()
        if (categorias.isEmpty()) {
            Toast.makeText(this, "Primero debes crear al menos una categoría", Toast.LENGTH_SHORT).show()
            return
        }

        val nombresCat = categorias.map { it.nombre }.toTypedArray()
        var categoriaSeleccionadaIndex = 0

        AlertDialog.Builder(this)
            .setTitle("La tarea no existe. Elige una categoría:")
            .setSingleChoiceItems(nombresCat, 0) { _, which ->
                categoriaSeleccionadaIndex = which
            }
            .setPositiveButton("Crear e Iniciar") { _, _ ->
                val catElegida = categorias[categoriaSeleccionadaIndex]
                val nuevaId = dbHelper.agregarTarea(nombreNuevaTarea, catElegida.id)
                if (nuevaId > 0) {
                    tareaSeleccionadaId = nuevaId.toInt()
                    tvCategoriaAsociada.text = "Categoría: ${catElegida.nombre}"
                    Toast.makeText(this, "Tarea creada y guardada", Toast.LENGTH_SHORT).show()
                    configurarBuscadorTareas()
                    iniciarNuevoEvento(tareaSeleccionadaId)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarNuevoEvento(tareaId: Int) {
        if (dbHelper.tieneEventoEnCurso()) {
            Toast.makeText(this, "Ya hay un evento en curso. No se puede iniciar otro.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = dbHelper.writableDatabase
        val inicioStr = LocalDateTime.now().format(formatter)
        inicioEventoActualStr = inicioStr

        val values = ContentValues().apply {
            put(DatabaseHelper.COL_EVT_TAREA_ID, tareaId)
            put(DatabaseHelper.COL_EVT_INICIO, inicioStr)
            put(DatabaseHelper.COL_EVT_FIN, null as String?)
            put(DatabaseHelper.COL_EVT_DURACION, null as String?)
        }

        val id = db.insert(DatabaseHelper.TABLE_EVENTOS, null, values)
        if (id != -1L) {
            eventoEnCursoId = id.toInt()
            Toast.makeText(this, "¡Evento iniciado!", Toast.LENGTH_SHORT).show()
            actualizarEstadoBotones(enCurso = true)
            iniciarTimer(inicioStr)
            cargarHistorialEventos()
            cargarResumenSemanal()
        }
    }

    private fun finalizarEventoEnCurso() {
        if (eventoEnCursoId == -1) return

        detenerTimer()
        val db = dbHelper.writableDatabase
        val finStr = LocalDateTime.now().format(formatter)

        val cursor = db.rawQuery("SELECT ${DatabaseHelper.COL_EVT_INICIO} FROM ${DatabaseHelper.TABLE_EVENTOS} WHERE ${DatabaseHelper.COL_EVT_ID} = ?", arrayOf(eventoEnCursoId.toString()))
        var inicioStr = ""
        if (cursor.moveToFirst()) {
            inicioStr = cursor.getString(0)
        }
        cursor.close()

        val inicioTime = LocalDateTime.parse(inicioStr, formatter)
        val finTime = LocalDateTime.parse(finStr, formatter)
        val duracion = Duration.between(inicioTime, finTime)
        val horas = duracion.toHours()
        val minutos = duracion.toMinutes() % 60
        val segundos = duracion.seconds % 60
        val duracionStr = String.format(Locale.US, "%02d:%02d:%02d", horas, minutos, segundos)

        val values = ContentValues().apply {
            put(DatabaseHelper.COL_EVT_FIN, finStr)
            put(DatabaseHelper.COL_EVT_DURACION, duracionStr)
        }

        db.update(DatabaseHelper.TABLE_EVENTOS, values, "${DatabaseHelper.COL_EVT_ID} = ?", arrayOf(eventoEnCursoId.toString()))

        Toast.makeText(this, "Evento finalizado. Duración: $duracionStr", Toast.LENGTH_LONG).show()
        eventoEnCursoId = -1
        inicioEventoActualStr = null
        actualizarEstadoBotones(enCurso = false)
        tvTiempoEnCurso.visibility = View.GONE
        autoCompleteTask.text.clear()
        tvCategoriaAsociada.text = "Categoría: -"
        cargarHistorialEventos()
        cargarResumenSemanal()
    }

    private fun verificarEventoEnCurso() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT ${DatabaseHelper.COL_EVT_ID}, ${DatabaseHelper.COL_EVT_TAREA_ID}, ${DatabaseHelper.COL_EVT_INICIO} FROM ${DatabaseHelper.TABLE_EVENTOS} WHERE ${DatabaseHelper.COL_EVT_FIN} IS NULL LIMIT 1", null)
        if (cursor.moveToFirst()) {
            eventoEnCursoId = cursor.getInt(0)
            tareaSeleccionadaId = cursor.getInt(1)
            inicioEventoActualStr = cursor.getString(2)
            actualizarEstadoBotones(enCurso = true)
            iniciarTimer(inicioEventoActualStr!!)

            val tareaCursor = db.rawQuery("""
                SELECT t.${DatabaseHelper.COL_TAR_NOMBRE}, c.${DatabaseHelper.COL_CAT_NOMBRE}
                FROM ${DatabaseHelper.TABLE_TAREAS} t
                INNER JOIN ${DatabaseHelper.TABLE_CATEGORIAS} c ON t.${DatabaseHelper.COL_TAR_CAT_ID} = c.${DatabaseHelper.COL_CAT_ID}
                WHERE t.${DatabaseHelper.COL_TAR_ID} = ?
            """, arrayOf(tareaSeleccionadaId.toString()))
            if (tareaCursor.moveToFirst()) {
                autoCompleteTask.setText(tareaCursor.getString(0))
                tvCategoriaAsociada.text = "Categoría: ${tareaCursor.getString(1)}"
            }
            tareaCursor.close()
        } else {
            actualizarEstadoBotones(enCurso = false)
            tvTiempoEnCurso.visibility = View.GONE
        }
        cursor.close()
    }

    private fun actualizarEstadoBotones(enCurso: Boolean) {
        btnIniciar.isEnabled = !enCurso
        btnFinalizar.isEnabled = enCurso
        autoCompleteTask.isEnabled = !enCurso
    }

    private fun cargarResumenSemanal() {
        val now = LocalDateTime.now()
        val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay()
        val endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toLocalDate().atTime(23, 59, 59)

        val formatterSql = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startStr = startOfWeek.format(formatterSql)
        val endStr = endOfWeek.format(formatterSql)

        val formatterDisplay = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        tvSemanaRango.text = "Semana: ${startOfWeek.format(formatterDisplay)} – ${endOfWeek.format(formatterDisplay)}"

        layoutResumenCategorias.removeAllViews()

        val categorias = dbHelper.obtenerCategorias()
        val resumenMap = dbHelper.obtenerResumenSemanal(startStr, endStr)

        val totalMinutosSemana = 10080
        var totalMinutosRegistrados = 0

        val inflater = LayoutInflater.from(this)

        for (cat in categorias) {
            val minutosCat = resumenMap[cat.id] ?: 0
            totalMinutosRegistrados += minutosCat
            val horasCat = minutosCat / 60.0
            val porcentajeCat = (minutosCat.toDouble() / totalMinutosSemana.toDouble()) * 100.0

            val rowView = inflater.inflate(R.layout.item_resumen_categoria, layoutResumenCategorias, false)
            val tvNombre = rowView.findViewById<TextView>(R.id.tvCatNombre)
            val tvHoras = rowView.findViewById<TextView>(R.id.tvCatHoras)
            val tvPorcentaje = rowView.findViewById<TextView>(R.id.tvCatPorcentaje)
            val pbProgreso = rowView.findViewById<ProgressBar>(R.id.pbCatProgreso)

            tvNombre.text = cat.nombre
            tvHoras.text = String.format(Locale.US, "%.1f hrs", horasCat)
            tvPorcentaje.text = String.format(Locale.US, "%.1f%%", porcentajeCat)
            pbProgreso.progress = porcentajeCat.toInt()

            layoutResumenCategorias.addView(rowView)
        }

        val minutosSinRegistrar = maxOf(0, totalMinutosSemana - totalMinutosRegistrados)
        val horasSin = minutosSinRegistrar / 60.0
        val porcentajeSin = (minutosSinRegistrar.toDouble() / totalMinutosSemana.toDouble()) * 100.0

        val rowSinView = inflater.inflate(R.layout.item_resumen_categoria, layoutResumenCategorias, false)
        val tvNombreSin = rowSinView.findViewById<TextView>(R.id.tvCatNombre)
        val tvHorasSin = rowSinView.findViewById<TextView>(R.id.tvCatHoras)
        val tvPorCientoSin = rowSinView.findViewById<TextView>(R.id.tvCatPorcentaje)
        val pbSin = rowSinView.findViewById<ProgressBar>(R.id.pbCatProgreso)

        tvNombreSin.text = "Sin registrar"
        tvHorasSin.text = String.format(Locale.US, "%.1f hrs", horasSin)
        tvPorCientoSin.text = String.format(Locale.US, "%.1f%%", porcentajeSin)
        pbSin.progress = porcentajeSin.toInt()

        layoutResumenCategorias.addView(rowSinView)
    }

    private fun cargarHistorialEventos() {
        val db = dbHelper.readableDatabase
        val actualQuery = """
            SELECT e.${DatabaseHelper.COL_EVT_ID}, t.${DatabaseHelper.COL_TAR_NOMBRE}, c.${DatabaseHelper.COL_CAT_NOMBRE}, 
                   e.${DatabaseHelper.COL_EVT_INICIO}, e.${DatabaseHelper.COL_EVT_FIN}, e.${DatabaseHelper.COL_EVT_DURACION}
            FROM ${DatabaseHelper.TABLE_EVENTOS} e
            INNER JOIN ${DatabaseHelper.TABLE_TAREAS} t ON e.${DatabaseHelper.COL_EVT_TAREA_ID} = t.${DatabaseHelper.COL_TAR_ID}
            INNER JOIN ${DatabaseHelper.TABLE_CATEGORIAS} c ON t.${DatabaseHelper.COL_TAR_CAT_ID} = c.${DatabaseHelper.COL_CAT_ID}
            ORDER BY e.${DatabaseHelper.COL_EVT_ID} DESC
        """.trimIndent()

        val cursor = db.rawQuery(actualQuery, null)
        layoutHistorialEventos.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (cursor.moveToFirst()) {
            do {
                val tarea = cursor.getString(1)
                val cat = cursor.getString(2)
                val inicio = cursor.getString(3)
                val fin = cursor.getString(4)
                val duracion = cursor.getString(5)

                val estadoStr = if (fin == null) "🟢 EN CURSO" else "Fin: $fin"
                val duracionStr = if (duracion == null) "En curso (calculando...)" else duracion

                val itemView = inflater.inflate(android.R.layout.simple_list_item_2, layoutHistorialEventos, false)
                val text1 = itemView.findViewById<TextView>(android.R.id.text1)
                val text2 = itemView.findViewById<TextView>(android.R.id.text2)

                text1.text = "• [$cat] $tarea"
                text2.text = "Inicio: $inicio\nEstado: $estadoStr\nDuración: $duracionStr"

                layoutHistorialEventos.addView(itemView)
            } while (cursor.moveToNext())
        }
        cursor.close()
    }
}
