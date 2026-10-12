package com.vasquez.mitiemposemanal

data class Tarea(
    val id: Int = 0,
    val nombre: String,
    val categoriaId: Int,
    val nombreCategoria: String = ""
)