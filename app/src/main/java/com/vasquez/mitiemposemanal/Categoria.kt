package com.vasquez.mitiemposemanal

data class Categoria(
    val id: Int = 0,
    val nombre: String
) {
    override fun toString(): String {
        return nombre
    }
}