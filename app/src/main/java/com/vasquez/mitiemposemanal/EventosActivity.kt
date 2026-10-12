package com.vasquez.mitiemposemanal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration

class EventosActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var autoCompleteTask: AutoCompleteTextView
    private lateinit var tvCategoriaAsociada: TextView
    private lateinit var btnIniciar: Button
    private lateinit var btnFinalizar: Button
    private lateinit var lvEventos: ListView
    private lateinit var btnIrEventos: Button // Para MainActivity

    private var tareaSeleccionadaId: Int = -1
    private var eventoEnCursoId: Int = -1
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eventos)

        dbHelper = DatabaseHelper(this)

        autoCompleteTask = findViewById(R.id.autoCompleteTask)
        tvCategoriaAsociada = findViewById(R.id.tvCategoriaAsociada)
        btnIniciar = findViewById(R.id.btnIniciarEvento)
        btnFinalizar = findViewById(R.id.btnFinalizarEvento)
        lvEventos = findViewById(R.id.lvEventos)

        configurarBuscadorTareas()
        verificarEventoEnCurso()
        cargarHistorialEventos()

        btnIniciar.setOnClickListener {
            val nombreInput = autoCompleteTask.text.toString().trim()
            if (nombreInput.isEmpty()) {
                Toast.makeText(this, "Escribe o selecciona una tarea", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Verificar si la tarea existe en la base de datos
            val tareaExistente = dbHelper.obtenerTareas().find { it.nombre.equals(nombreInput, ignoreCase = true) }

            if (tareaExistente != null) {
                tareaSeleccionadaId = tareaExistente.id
                iniciarNuevoEvento(tareaSeleccionadaId)
            } else {
                // HU-04: Crear la tarea si no existe pidiendo categoría
                mostrarDialogCrearTareaConCategoria(nombreInput)
            }
        }

        btnFinalizar.setOnClickListener {
            finalizarEventoEnCurso()
        }
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
        val db = dbHelper.writableDatabase
        val inicioStr = LocalDateTime.now().format(formatter)

        val values = android.content.ContentValues().apply {
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
            cargarHistorialEventos()
        }
    }

    private fun finalizarEventoEnCurso() {
        if (eventoEnCursoId == -1) return

        val db = dbHelper.writableDatabase
        val finStr = LocalDateTime.now().format(formatter)

        // Obtener la hora de inicio para calcular la duración
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
        val duracionStr = String.format("%02d:%02d:%02d", horas, minutos, segundos)

        val values = android.content.ContentValues().apply {
            put(DatabaseHelper.COL_EVT_FIN, finStr)
            put(DatabaseHelper.COL_EVT_DURACION, duracionStr)
        }

        db.update(DatabaseHelper.TABLE_EVENTOS, values, "${DatabaseHelper.COL_EVT_ID} = ?", arrayOf(eventoEnCursoId.toString()))

        Toast.makeText(this, "Evento finalizado. Duración: $duracionStr", Toast.LENGTH_LONG).show()
        eventoEnCursoId = -1
        actualizarEstadoBotones(enCurso = false)
        autoCompleteTask.text.clear()
        tvCategoriaAsociada.text = "Categoría: -"
        cargarHistorialEventos()
    }

    private fun verificarEventoEnCurso() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT ${DatabaseHelper.COL_EVT_ID}, ${DatabaseHelper.COL_EVT_TAREA_ID} FROM ${DatabaseHelper.TABLE_EVENTOS} WHERE ${DatabaseHelper.COL_EVT_FIN} IS NULL LIMIT 1", null)
        if (cursor.moveToFirst()) {
            eventoEnCursoId = cursor.getInt(0)
            tareaSeleccionadaId = cursor.getInt(1)
            actualizarEstadoBotones(enCurso = true)

            // Buscar nombre de tarea para mostrarlo
            val tareaCursor = db.rawQuery("SELECT ${DatabaseHelper.COL_TAR_NOMBRE} FROM ${DatabaseHelper.TABLE_TAREAS} WHERE ${DatabaseHelper.COL_TAR_ID} = ?", arrayOf(tareaSeleccionadaId.toString()))
            if (tareaCursor.moveToFirst()) {
                autoCompleteTask.setText(tareaCursor.getString(0))
            }
            tareaCursor.close()
        } else {
            actualizarEstadoBotones(enCurso = false)
        }
        cursor.close()
    }

    private fun actualizarEstadoBotones(enCurso: Boolean) {
        btnIniciar.isEnabled = !enCurso
        btnFinalizar.isEnabled = enCurso
        autoCompleteTask.isEnabled = !enCurso
    }

    private fun cargarHistorialEventos() {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT e.${DatabaseHelper.COL_EVT_ID}, t.${DatabaseHelper.COL_TAR_NOMBRE}, c.${DatabaseHelper.COL_CAT_NOMBRE}, 
                   e.${DatabaseHelper.COL_EVT_INICIO}, e.${DatabaseHelper.COL_EVT_FIN}, e.${DatabaseHelper.COL_EVT_DURACION}
            FROM ${DatabaseHelper.TABLE_EVENTOS} e
            INNER JOIN ${DatabaseHelper.TABLE_TAREAS} t ON e.${DatabaseHelper.COL_EVT_TAREA_ID} = t.${DatabaseHelper.COL_TAR_ID}
            INNER JOIN ${DatabaseHelper.TABLE_CATEGORIAS} c ON t.${DatabaseHelper.COL_TAR_CAT_ID} = c.${DatabaseHelper.COL_CAT_ID}
            ORDER BY e.${DatabaseHelper.COL_EVT_ID} DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)
        val listaEventos = mutableListOf<String>()

        if (cursor.moveToFirst()) {
            do {
                val tarea = cursor.getString(1)
                val cat = cursor.getString(2)
                val inicio = cursor.getString(3)
                val fin = cursor.getString(4) ?: "En curso..."
                val duracion = cursor.getString(5) ?: "-"

                listaEventos.add("• [$cat] $tarea\nInicio: $inicio\nFin: $fin\nDuración: $duracion")
            } while (cursor.moveToNext())
        }
        cursor.close()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listaEventos)
        lvEventos.adapter = adapter
    }
}