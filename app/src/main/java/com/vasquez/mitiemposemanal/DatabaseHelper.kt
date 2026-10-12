package com.vasquez.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mitiemposemanal.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_CATEGORIAS = "categorias"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NOMBRE = "nombre"

        const val TABLE_TAREAS = "tareas"
        const val COL_TAR_ID = "id"
        const val COL_TAR_NOMBRE = "nombre"
        const val COL_TAR_CAT_ID = "categoria_id"

        // Nuevas constantes para Sprint 2 (Eventos)
        const val TABLE_EVENTOS = "eventos"
        const val COL_EVT_ID = "id"
        const val COL_EVT_TAREA_ID = "tarea_id"
        const val COL_EVT_INICIO = "inicio"
        const val COL_EVT_FIN = "fin"
        const val COL_EVT_DURACION = "duracion"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTableCategorias = ("CREATE TABLE " + TABLE_CATEGORIAS + " ("
                + COL_CAT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CAT_NOMBRE + " TEXT UNIQUE NOT NULL)")

        val createTableTareas = ("CREATE TABLE " + TABLE_TAREAS + " ("
                + COL_TAR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TAR_NOMBRE + " TEXT UNIQUE NOT NULL, "
                + COL_TAR_CAT_ID + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + COL_TAR_CAT_ID + ") REFERENCES " + TABLE_CATEGORIAS + "(" + COL_CAT_ID + "))")

        val createTableEventos = ("CREATE TABLE " + TABLE_EVENTOS + " ("
                + COL_EVT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_EVT_TAREA_ID + " INTEGER NOT NULL, "
                + COL_EVT_INICIO + " TEXT NOT NULL, "
                + COL_EVT_FIN + " TEXT, "
                + COL_EVT_DURACION + " TEXT, "
                + "FOREIGN KEY(" + COL_EVT_TAREA_ID + ") REFERENCES " + TABLE_TAREAS + "(" + COL_TAR_ID + "))")

        db?.execSQL(createTableCategorias)
        db?.execSQL(createTableTareas)
        db?.execSQL(createTableEventos)

        // Insertar las 4 categorías por defecto (HU-01)
        db?.execSQL("INSERT INTO $TABLE_CATEGORIAS ($COL_CAT_NOMBRE) VALUES ('Estudio')")
        db?.execSQL("INSERT INTO $TABLE_CATEGORIAS ($COL_CAT_NOMBRE) VALUES ('Trabajo')")
        db?.execSQL("INSERT INTO $TABLE_CATEGORIAS ($COL_CAT_NOMBRE) VALUES ('Descanso')")
        db?.execSQL("INSERT INTO $TABLE_CATEGORIAS ($COL_CAT_NOMBRE) VALUES ('Ocio')")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createTableEventos = ("CREATE TABLE IF NOT EXISTS $TABLE_EVENTOS ("
                    + "$COL_EVT_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "$COL_EVT_TAREA_ID INTEGER NOT NULL, "
                    + "$COL_EVT_INICIO TEXT NOT NULL, "
                    + "$COL_EVT_FIN TEXT, "
                    + "$COL_EVT_DURACION TEXT, "
                    + "FOREIGN KEY($COL_EVT_TAREA_ID) REFERENCES $TABLE_TAREAS($COL_TAR_ID))")
            db?.execSQL(createTableEventos)
        }
    }

    // --- CATEGORÍAS ---

    fun obtenerCategorias(): List<Categoria> {
        val lista = mutableListOf<Categoria>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CATEGORIAS ORDER BY $COL_CAT_NOMBRE ASC", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_ID))
                val nombre = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_NOMBRE))
                lista.add(Categoria(id, nombre))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    fun existeCategoria(nombre: String, excludeId: Int = -1): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_CATEGORIAS WHERE LOWER($COL_CAT_NOMBRE) = LOWER(?) AND $COL_CAT_ID != ?",
            arrayOf(nombre.trim(), excludeId.toString())
        )
        val existe = cursor.count > 0
        cursor.close()
        return existe
    }

    fun agregarCategoria(nombre: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CAT_NOMBRE, nombre.trim())
        }
        return db.insert(TABLE_CATEGORIAS, null, values)
    }

    fun actualizarCategoria(id: Int, nuevoNombre: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CAT_NOMBRE, nuevoNombre.trim())
        }
        return db.update(TABLE_CATEGORIAS, values, "$COL_CAT_ID = ?", arrayOf(id.toString()))
    }

    fun tieneTareasAsociadas(categoriaId: Int): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TAREAS WHERE $COL_TAR_CAT_ID = ?", arrayOf(categoriaId.toString()))
        val tiene = cursor.count > 0
        cursor.close()
        return tiene
    }

    fun eliminarCategoria(id: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_CATEGORIAS, "$COL_CAT_ID = ?", arrayOf(id.toString()))
    }

    // --- TAREAS ---

    fun obtenerTareas(): List<Tarea> {
        val lista = mutableListOf<Tarea>()
        val db = readableDatabase
        val query = """
            SELECT t.$COL_TAR_ID, t.$COL_TAR_NOMBRE, t.$COL_TAR_CAT_ID, c.$COL_CAT_NOMBRE AS cat_nombre
            FROM $TABLE_TAREAS t
            INNER JOIN $TABLE_CATEGORIAS c ON t.$COL_TAR_CAT_ID = c.$COL_CAT_ID
            ORDER BY t.$COL_TAR_ID DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TAR_ID))
                val nombre = cursor.getString(cursor.getColumnIndexOrThrow(COL_TAR_NOMBRE))
                val catId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TAR_CAT_ID))
                val catNombre = cursor.getString(cursor.getColumnIndexOrThrow("cat_nombre"))
                lista.add(Tarea(id, nombre, catId, catNombre))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    fun existeTarea(nombre: String, excludeId: Int = -1): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_TAREAS WHERE LOWER($COL_TAR_NOMBRE) = LOWER(?) AND $COL_TAR_ID != ?",
            arrayOf(nombre.trim(), excludeId.toString())
        )
        val existe = cursor.count > 0
        cursor.close()
        return existe
    }

    fun agregarTarea(nombre: String, categoriaId: Int): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TAR_NOMBRE, nombre.trim())
            put(COL_TAR_CAT_ID, categoriaId)
        }
        return db.insert(TABLE_TAREAS, null, values)
    }

    fun actualizarTarea(id: Int, nuevoNombre: String, nuevaCategoriaId: Int): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TAR_NOMBRE, nuevoNombre.trim())
            put(COL_TAR_CAT_ID, nuevaCategoriaId)
        }
        return db.update(TABLE_TAREAS, values, "$COL_TAR_ID = ?", arrayOf(id.toString()))
    }

    fun eliminarTarea(id: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_TAREAS, "$COL_TAR_ID = ?", arrayOf(id.toString()))
    }

    // --- EVENTOS ---
    fun tieneEventoEnCurso(): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_EVENTOS WHERE $COL_EVT_FIN IS NULL", null)
        val tiene = cursor.count > 0
        cursor.close()
        return tiene
    }
}