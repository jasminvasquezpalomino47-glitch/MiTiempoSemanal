package com.vasquez.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mitiemposemanal.db"
        private const val DATABASE_VERSION = 3

        const val TABLE_CATEGORIAS = "categorias"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NOMBRE = "nombre"

        const val TABLE_TAREAS = "tareas"
        const val COL_TAR_ID = "id"
        const val COL_TAR_NOMBRE = "nombre"
        const val COL_TAR_CAT_ID = "categoria_id"

        const val TABLE_EVENTOS = "eventos"
        const val COL_EVT_ID = "id"
        const val COL_EVT_TAREA_ID = "tarea_id"
        const val COL_EVT_INICIO = "inicio"
        const val COL_EVT_FIN = "fin"
        const val COL_EVT_DURACION = "duracion"

        const val TABLE_REPORTES = "reportes_semanales"
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

        val createTableReportes = ("CREATE TABLE " + TABLE_REPORTES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "semana_inicio TEXT NOT NULL, "
                + "semana_fin TEXT NOT NULL, "
                + "categoria_id INTEGER NOT NULL, "
                + "minutos INTEGER NOT NULL, "
                + "FOREIGN KEY(categoria_id) REFERENCES " + TABLE_CATEGORIAS + "(" + COL_CAT_ID + "), "
                + "UNIQUE(semana_inicio, semana_fin, categoria_id))")

        db?.execSQL(createTableCategorias)
        db?.execSQL(createTableTareas)
        db?.execSQL(createTableEventos)
        db?.execSQL(createTableReportes)

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
        if (oldVersion < 3) {
            val createTableReportes = ("CREATE TABLE IF NOT EXISTS $TABLE_REPORTES ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "semana_inicio TEXT NOT NULL, "
                    + "semana_fin TEXT NOT NULL, "
                    + "categoria_id INTEGER NOT NULL, "
                    + "minutos INTEGER NOT NULL, "
                    + "FOREIGN KEY(categoria_id) REFERENCES $TABLE_CATEGORIAS($COL_CAT_ID), "
                    + "UNIQUE(semana_inicio, semana_fin, categoria_id))")
            db?.execSQL(createTableReportes)
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

    fun obtenerResumenSemanal(inicioSemana: String, finSemana: String): Map<Int, Int> {
        val resumen = mutableMapOf<Int, Int>() // categoriaId -> totalMinutos
        val db = readableDatabase
        val query = """
            SELECT t.${COL_TAR_CAT_ID}, SUM((strftime('%s', e.${COL_EVT_FIN}) - strftime('%s', e.${COL_EVT_INICIO})) / 60) as total_min
            FROM $TABLE_EVENTOS e
            INNER JOIN $TABLE_TAREAS t ON e.$COL_EVT_TAREA_ID = t.$COL_TAR_ID
            WHERE e.$COL_EVT_INICIO >= ? AND e.$COL_EVT_INICIO <= ? AND e.$COL_EVT_FIN IS NOT NULL
            GROUP BY t.${COL_TAR_CAT_ID}
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(inicioSemana, finSemana))
        if (cursor.moveToFirst()) {
            do {
                val catId = cursor.getInt(0)
                val totalMin = cursor.getInt(1)
                resumen[catId] = totalMin
            } while (cursor.moveToNext())
        }
        cursor.close()
        return resumen
    }

    // --- REPORTES SEMANALES (SPRINT 4) ---
    fun generarReportesSemanalesPendientes() {
        val db = writableDatabase
        val query = """
            SELECT DISTINCT e.$COL_EVT_INICIO
            FROM $TABLE_EVENTOS e
            WHERE e.$COL_EVT_FIN IS NOT NULL
            ORDER BY e.$COL_EVT_INICIO ASC
        """.trimIndent()
        val cursor = db.rawQuery(query, null)
        val semanasProcesadas = mutableSetOf<Pair<String, String>>()

        val now = LocalDateTime.now()
        val currentWeekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        if (cursor.moveToFirst()) {
            do {
                val inicioStr = cursor.getString(0)
                try {
                    val inicioTime = LocalDateTime.parse(inicioStr, formatter)
                    val startOfWeek = inicioTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay()
                    val endOfWeek = inicioTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toLocalDate().atTime(23, 59, 59)

                    if (endOfWeek.isBefore(currentWeekStart)) {
                        val startStr = startOfWeek.format(formatter)
                        val endStr = endOfWeek.format(formatter)
                        val semanaPair = Pair(startStr, endStr)

                        if (!semanasProcesadas.contains(semanaPair)) {
                            semanasProcesadas.add(semanaPair)

                            val checkCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_REPORTES WHERE semana_inicio = ? AND semana_fin = ?", arrayOf(startStr, endStr))
                            var count = 0
                            if (checkCursor.moveToFirst()) {
                                count = checkCursor.getInt(0)
                            }
                            checkCursor.close()

                            if (count == 0) {
                                val resumenQuery = """
                                    SELECT t.$COL_TAR_CAT_ID, SUM((strftime('%s', e.$COL_EVT_FIN) - strftime('%s', e.$COL_EVT_INICIO)) / 60) as total_min
                                    FROM $TABLE_EVENTOS e
                                    INNER JOIN $TABLE_TAREAS t ON e.$COL_EVT_TAREA_ID = t.$COL_TAR_ID
                                    WHERE e.$COL_EVT_INICIO >= ? AND e.$COL_EVT_INICIO <= ? AND e.$COL_EVT_FIN IS NOT NULL
                                    GROUP BY t.$COL_TAR_CAT_ID
                                """.trimIndent()
                                val resCursor = db.rawQuery(resumenQuery, arrayOf(startStr, endStr))
                                if (resCursor.moveToFirst()) {
                                    do {
                                        val catId = resCursor.getInt(0)
                                        val totalMin = resCursor.getInt(1)

                                        val values = ContentValues().apply {
                                            put("semana_inicio", startStr)
                                            put("semana_fin", endStr)
                                            put("categoria_id", catId)
                                            put("minutos", totalMin)
                                        }
                                        db.insertWithOnConflict(
                                            TABLE_REPORTES,
                                            null,
                                            values,
                                            SQLiteDatabase.CONFLICT_IGNORE
                                        )
                                    } while (resCursor.moveToNext())
                                }
                                resCursor.close()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    fun obtenerSemanasConReporte(): List<Pair<String, String>> {
        val lista = mutableListOf<Pair<String, String>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT DISTINCT semana_inicio, semana_fin FROM $TABLE_REPORTES ORDER BY semana_inicio DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val inicio = cursor.getString(0)
                val fin = cursor.getString(1)
                lista.add(Pair(inicio, fin))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    fun obtenerReporteSemana(semanaInicio: String, semanaFin: String): Map<Int, Int> {
        val resumen = mutableMapOf<Int, Int>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT categoria_id, minutos FROM $TABLE_REPORTES WHERE semana_inicio = ? AND semana_fin = ?", arrayOf(semanaInicio, semanaFin))
        if (cursor.moveToFirst()) {
            do {
                val catId = cursor.getInt(0)
                val minutos = cursor.getInt(1)
                resumen[catId] = minutos
            } while (cursor.moveToNext())
        }
        cursor.close()
        return resumen
    }

    fun obtenerEventosSemana(semanaInicio: String, semanaFin: String): List<String> {
        val lista = mutableListOf<String>()
        val db = readableDatabase
        val query = """
            SELECT t.$COL_TAR_NOMBRE, c.$COL_CAT_NOMBRE, e.$COL_EVT_INICIO, e.$COL_EVT_FIN, e.$COL_EVT_DURACION
            FROM $TABLE_EVENTOS e
            INNER JOIN $TABLE_TAREAS t ON e.$COL_EVT_TAREA_ID = t.$COL_TAR_ID
            INNER JOIN $TABLE_CATEGORIAS c ON t.$COL_TAR_CAT_ID = c.$COL_CAT_ID
            WHERE e.$COL_EVT_INICIO >= ? AND e.$COL_EVT_INICIO <= ?
            ORDER BY e.$COL_EVT_ID DESC
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(semanaInicio, semanaFin))
        if (cursor.moveToFirst()) {
            do {
                val tarea = cursor.getString(0)
                val cat = cursor.getString(1)
                val inicio = cursor.getString(2)
                val fin = cursor.getString(3) ?: "En curso"
                val duracion = cursor.getString(4) ?: "-"
                lista.add("• [$cat] $tarea\nInicio: $inicio\nFin: $fin\nDuración: $duracion")
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }
}
