package com.vasquez.mitiemposemanal

import android.content.ContentValues
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RegistroEventoFragment : Fragment() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var autoCompleteTask: AutoCompleteTextView
    private lateinit var tvCategoriaAsociada: TextView
    private lateinit var tvTiempoEnCurso: TextView
    private lateinit var btnIniciar: Button
    private lateinit var btnFinalizar: Button
    private lateinit var lvEventos: ListView

    private var tareaSeleccionadaId: Int = -1
    private var eventoEnCursoId: Int = -1
    private var inicioEventoActualStr: String? = null
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_registro_evento, container, false)

        dbHelper = DatabaseHelper(requireContext())

        autoCompleteTask = view.findViewById(R.id.autoCompleteTask)
        tvCategoriaAsociada = view.findViewById(R.id.tvCategoriaAsociada)
        tvTiempoEnCurso = view.findViewById(R.id.tvTiempoEnCurso)
        btnIniciar = view.findViewById(R.id.btnIniciarEvento)
        btnFinalizar = view.findViewById(R.id.btnFinalizarEvento)
        lvEventos = view.findViewById(R.id.lvEventos)

        configurarBuscadorTareas()
        verificarEventoEnCurso()
        cargarHistorialEventos()

        btnIniciar.setOnClickListener {
            if (dbHelper.tieneEventoEnCurso() || eventoEnCursoId != -1) {
                Toast.makeText(requireContext(), "Ya hay un evento en curso. Finalízalo antes de iniciar otro.", Toast.LENGTH_SHORT).show()
                actualizarEstadoBotones(enCurso = true)
                return@setOnClickListener
            }

            val nombreInput = autoCompleteTask.text.toString().trim()
            if (nombreInput.isEmpty()) {
                Toast.makeText(requireContext(), "Escribe o selecciona una tarea", Toast.LENGTH_SHORT).show()
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

        return view
    }

    override fun onDestroyView() {
        detenerTimer()
        super.onDestroyView()
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
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tareas.map { it.nombre })
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
            Toast.makeText(requireContext(), "Primero debes crear al menos una categoría", Toast.LENGTH_SHORT).show()
            return
        }

        val nombresCat = categorias.map { it.nombre }.toTypedArray()
        var categoriaSeleccionadaIndex = 0

        AlertDialog.Builder(requireContext())
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
                    Toast.makeText(requireContext(), "Tarea creada y guardada", Toast.LENGTH_SHORT).show()
                    configurarBuscadorTareas()
                    iniciarNuevoEvento(tareaSeleccionadaId)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarNuevoEvento(tareaId: Int) {
        if (dbHelper.tieneEventoEnCurso()) {
            Toast.makeText(requireContext(), "Ya hay un evento en curso. No se puede iniciar otro.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "¡Evento iniciado!", Toast.LENGTH_SHORT).show()
            actualizarEstadoBotones(enCurso = true)
            iniciarTimer(inicioStr)
            cargarHistorialEventos()
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

        Toast.makeText(requireContext(), "Evento finalizado. Duración: $duracionStr", Toast.LENGTH_LONG).show()
        eventoEnCursoId = -1
        inicioEventoActualStr = null
        actualizarEstadoBotones(enCurso = false)
        tvTiempoEnCurso.visibility = View.GONE
        autoCompleteTask.text.clear()
        tvCategoriaAsociada.text = "Categoría: -"
        cargarHistorialEventos()
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
                val fin = cursor.getString(4)
                val duracion = cursor.getString(5)

                val estadoStr = if (fin == null) "🟢 EN CURSO" else "Fin: $fin"
                val duracionStr = if (duracion == null) "En curso (calculando...)" else duracion

                listaEventos.add("• [$cat] $tarea\nInicio: $inicio\nEstado: $estadoStr\nDuración: $duracionStr")
            } while (cursor.moveToNext())
        }
        cursor.close()

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listaEventos)
        lvEventos.adapter = adapter
    }
}
