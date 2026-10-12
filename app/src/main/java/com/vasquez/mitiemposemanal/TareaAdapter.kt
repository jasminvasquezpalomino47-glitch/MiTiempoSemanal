package com.vasquez.mitiemposemanal

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView

class TareaAdapter(
    private val context: Context,
    private val tareas: List<Tarea>,
    private val onEditClick: (Tarea) -> Unit,
    private val onDeleteClick: (Tarea) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = tareas.size

    override fun getItem(position: Int): Tarea = tareas[position]

    override fun getItemId(position: Int): Long = tareas[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_tarea, parent, false)
        
        val tarea = getItem(position)
        
        val tvNombre = view.findViewById<TextView>(R.id.tvTareaNombre)
        val tvCategoria = view.findViewById<TextView>(R.id.tvTareaCategoria)
        val btnEditar = view.findViewById<ImageButton>(R.id.btnEditarTarea)
        val btnEliminar = view.findViewById<ImageButton>(R.id.btnEliminarTarea)
        
        tvNombre.text = tarea.nombre
        tvCategoria.text = "📁 ${tarea.nombreCategoria}"
        
        btnEditar.setOnClickListener {
            onEditClick(tarea)
        }
        
        btnEliminar.setOnClickListener {
            onDeleteClick(tarea)
        }
        
        return view
    }
}
