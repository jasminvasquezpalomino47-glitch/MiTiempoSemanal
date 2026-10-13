package com.vasquez.mitiemposemanal
import com.vasquez.mitiemposemanal.R
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etCategoriaNombre: EditText
    private lateinit var btnAgregarCategoria: Button
    private lateinit var etTareaNombre: EditText
    private lateinit var spnCategorias: Spinner
    private lateinit var btnAgregarTarea: Button
    private lateinit var lvTareas: ListView
    private lateinit var btnVerCategorias: Button
    private lateinit var btnIrEventos: Button
    private lateinit var btnIrReportes: Button

    private var listaCategorias: List<Categoria> = emptyList()
    private var listaTareas: List<Tarea> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        // HU-07: Cierre automático de semanas terminadas al abrir la app
        dbHelper.generarReportesSemanalesPendientes()

        etCategoriaNombre = findViewById(R.id.etCategoriaNombre)
        btnAgregarCategoria = findViewById(R.id.btnAgregarCategoria)
        etTareaNombre = findViewById(R.id.etTareaNombre)
        spnCategorias = findViewById(R.id.spnCategorias)
        btnAgregarTarea = findViewById(R.id.btnAgregarTarea)
        lvTareas = findViewById(R.id.lvTareas)
        btnVerCategorias = findViewById(R.id.btnVerCategorias)
        btnIrEventos = findViewById(R.id.btnIrEventos)
        btnIrReportes = findViewById(R.id.btnIrReportes)

        cargarCategoriasEnSpinner()
        cargarTareasEnLista()

        // HU-01: Agregar Categoría
        btnAgregarCategoria.setOnClickListener {
            val nombreCat = etCategoriaNombre.text.toString().trim()
            if (nombreCat.isEmpty()) {
                Toast.makeText(this, "Escribe el nombre de la categoría", Toast.LENGTH_SHORT).show()
            } else if (dbHelper.existeCategoria(nombreCat)) {
                Toast.makeText(this, "La categoría ya existe", Toast.LENGTH_SHORT).show()
            } else {
                dbHelper.agregarCategoria(nombreCat)
                etCategoriaNombre.text.clear()
                Toast.makeText(this, "Categoría creada con éxito", Toast.LENGTH_SHORT).show()
                cargarCategoriasEnSpinner()
            }
        }

        // HU-02: Agregar Tarea
        btnAgregarTarea.setOnClickListener {
            val nombreTarea = etTareaNombre.text.toString().trim()
            if (nombreTarea.isEmpty()) {
                Toast.makeText(this, "No se puede guardar una tarea sin nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (spnCategorias.selectedItem == null) {
                Toast.makeText(this, "Selecciona una categoría", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val categoriaSeleccionada = spnCategorias.selectedItem as Categoria

            if (dbHelper.existeTarea(nombreTarea)) {
                Toast.makeText(this, "Ya existe una tarea con este nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dbHelper.agregarTarea(nombreTarea, categoriaSeleccionada.id)
            etTareaNombre.text.clear()
            Toast.makeText(this, "Tarea guardada", Toast.LENGTH_SHORT).show()
            cargarTareasEnLista()
        }

        // Ver y gestionar lista de categorías
        btnVerCategorias.setOnClickListener {
            mostrarListaCategoriasDialog()
        }

        // Navegar hacia la pantalla de Eventos
        btnIrEventos.setOnClickListener {
            val intent = Intent(this, EventosActivity::class.java)
            startActivity(intent)
        }

        // Navegar hacia la pantalla de Reportes Históricos (Sprint 4)
        btnIrReportes.setOnClickListener {
            val intent = Intent(this, ReportesActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cargarCategoriasEnSpinner() {
        listaCategorias = dbHelper.obtenerCategorias()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listaCategorias)
        spnCategorias.adapter = adapter
    }

    private fun cargarTareasEnLista() {
        listaTareas = dbHelper.obtenerTareas()
        val adapter = TareaAdapter(
            this,
            listaTareas,
            onEditClick = { tarea ->
                mostrarDialogEditarTarea(tarea)
            },
            onDeleteClick = { tarea ->
                AlertDialog.Builder(this)
                    .setTitle("Eliminar Tarea")
                    .setMessage("¿Estás seguro de que deseas eliminar la tarea '${tarea.nombre}'?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        dbHelper.eliminarTarea(tarea.id)
                        Toast.makeText(this, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                        cargarTareasEnLista()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
        lvTareas.adapter = adapter
    }

    private fun mostrarDialogEditarTarea(tarea: Tarea) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val inputNombre = EditText(this).apply {
            setText(tarea.nombre)
        }
        val spinnerCat = Spinner(this)
        val adapterCat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listaCategorias)
        spinnerCat.adapter = adapterCat

        val pos = listaCategorias.indexOfFirst { it.id == tarea.categoriaId }
        if (pos >= 0) spinnerCat.setSelection(pos)

        layout.addView(inputNombre)
        layout.addView(spinnerCat)

        AlertDialog.Builder(this)
            .setTitle("Editar Tarea")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = inputNombre.text.toString().trim()
                val catSeleccionada = spinnerCat.selectedItem as Categoria

                if (nuevoNombre.isEmpty()) {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                } else if (dbHelper.existeTarea(nuevoNombre, tarea.id)) {
                    Toast.makeText(this, "Ya existe otra tarea con ese nombre", Toast.LENGTH_SHORT).show()
                } else {
                    dbHelper.actualizarTarea(tarea.id, nuevoNombre, catSeleccionada.id)
                    cargarTareasEnLista()
                    Toast.makeText(this, "Tarea actualizada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarListaCategoriasDialog() {
        val categorias = dbHelper.obtenerCategorias()
        val nombres = categorias.map { it.nombre }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Gestión de Categorías")
            .setItems(nombres) { _, which ->
                val cat = categorias[which]
                mostrarOpcionesCategoriaDialog(cat)
            }
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun mostrarOpcionesCategoriaDialog(categoria: Categoria) {
        val opciones = arrayOf("Editar Nombre", "Eliminar Categoría")
        AlertDialog.Builder(this)
            .setTitle("Categoría: ${categoria.nombre}")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> mostrarDialogEditarCategoria(categoria)
                    1 -> {
                        if (dbHelper.tieneTareasAsociadas(categoria.id)) {
                            AlertDialog.Builder(this)
                                .setTitle("No se puede eliminar")
                                .setMessage("Esta categoría tiene tareas asociadas. Elimina o cambia esas tareas primero.")
                                .setPositiveButton("Aceptar", null)
                                .show()
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Confirmar eliminación")
                                .setMessage("¿Estás seguro de que deseas eliminar la categoría '${categoria.nombre}'?")
                                .setPositiveButton("Eliminar") { _, _ ->
                                    dbHelper.eliminarCategoria(categoria.id)
                                    cargarCategoriasEnSpinner()
                                    Toast.makeText(this, "Categoría eliminada", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    }
                }
            }.show()
    }

    private fun mostrarDialogEditarCategoria(categoria: Categoria) {
        val input = EditText(this).apply {
            setText(categoria.nombre)
        }
        AlertDialog.Builder(this)
            .setTitle("Editar Categoría")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = input.text.toString().trim()
                if (nuevoNombre.isEmpty()) {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                } else if (dbHelper.existeCategoria(nuevoNombre, categoria.id)) {
                    Toast.makeText(this, "Ya existe una categoría con ese nombre", Toast.LENGTH_SHORT).show()
                } else {
                    dbHelper.actualizarCategoria(categoria.id, nuevoNombre)
                    cargarCategoriasEnSpinner()
                    cargarTareasEnLista()
                    Toast.makeText(this, "Categoría actualizada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
