package com.vasquez.mitiemposemanal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var spnSemanasReporte: Spinner
    private lateinit var tvSinReportes: TextView
    private lateinit var cardResumenReporte: MaterialCardView
    private lateinit var layoutReporteCategorias: LinearLayout
    private lateinit var tvTituloEventosSemana: TextView
    private lateinit var layoutEventosSemana: LinearLayout

    private var semanasList: List<Pair<String, String>> = emptyList()
    private val sqlFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reportes)

        dbHelper = DatabaseHelper(this)

        spnSemanasReporte = findViewById(R.id.spnSemanasReporte)
        tvSinReportes = findViewById(R.id.tvSinReportes)
        cardResumenReporte = findViewById(R.id.cardResumenReporte)
        layoutReporteCategorias = findViewById(R.id.layoutReporteCategorias)
        tvTituloEventosSemana = findViewById(R.id.tvTituloEventosSemana)
        layoutEventosSemana = findViewById(R.id.layoutEventosSemana)

        cargarSemanasEnSpinner()
    }

    private fun cargarSemanasEnSpinner() {
        semanasList = dbHelper.obtenerSemanasConReporte()

        if (semanasList.isEmpty()) {
            tvSinReportes.visibility = View.VISIBLE
            cardResumenReporte.visibility = View.GONE
            tvTituloEventosSemana.visibility = View.GONE
            spnSemanasReporte.isEnabled = false
            return
        }

        tvSinReportes.visibility = View.GONE
        cardResumenReporte.visibility = View.VISIBLE
        tvTituloEventosSemana.visibility = View.VISIBLE
        spnSemanasReporte.isEnabled = true

        val displayNames = semanasList.map { pair ->
            try {
                val start = LocalDateTime.parse(pair.first, sqlFormatter)
                val end = LocalDateTime.parse(pair.second, sqlFormatter)
                "${start.format(displayFormatter)} – ${end.format(displayFormatter)}"
            } catch (ex: Exception) {
                "${pair.first} – ${pair.second}"
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)
        spnSemanasReporte.adapter = adapter

        spnSemanasReporte.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val semanaSeleccionada = semanasList[position]
                mostrarReporteSemana(semanaSeleccionada.first, semanaSeleccionada.second)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun mostrarReporteSemana(inicioStr: String, finStr: String) {
        layoutReporteCategorias.removeAllViews()
        layoutEventosSemana.removeAllViews()

        val categorias = dbHelper.obtenerCategorias()
        val resumenMap = dbHelper.obtenerReporteSemana(inicioStr, finStr)

        val totalMinutosSemana = 10080
        var totalMinutosRegistrados = 0

        val inflater = LayoutInflater.from(this)

        for (cat in categorias) {
            val minutosCat = resumenMap[cat.id] ?: 0
            totalMinutosRegistrados += minutosCat
            val horasCat = minutosCat / 60.0
            val porcentajeCat = (minutosCat.toDouble() / totalMinutosSemana.toDouble()) * 100.0

            val rowView = inflater.inflate(R.layout.item_resumen_categoria, layoutReporteCategorias, false)
            val tvNombre = rowView.findViewById<TextView>(R.id.tvCatNombre)
            val tvHoras = rowView.findViewById<TextView>(R.id.tvCatHoras)
            val tvPorcentaje = rowView.findViewById<TextView>(R.id.tvCatPorcentaje)
            val pbProgreso = rowView.findViewById<ProgressBar>(R.id.pbCatProgreso)

            tvNombre.text = cat.nombre
            tvHoras.text = String.format(Locale.US, "%.1f hrs", horasCat)
            tvPorcentaje.text = String.format(Locale.US, "%.1f%%", porcentajeCat)
            pbProgreso.progress = porcentajeCat.toInt()

            layoutReporteCategorias.addView(rowView)
        }

        val minutosSinRegistrar = maxOf(0, totalMinutosSemana - totalMinutosRegistrados)
        val horasSin = minutosSinRegistrar / 60.0
        val porcentajeSin = (minutosSinRegistrar.toDouble() / totalMinutosSemana.toDouble()) * 100.0

        val rowSinView = inflater.inflate(R.layout.item_resumen_categoria, layoutReporteCategorias, false)
        val tvNombreSin = rowSinView.findViewById<TextView>(R.id.tvCatNombre)
        val tvHorasSin = rowSinView.findViewById<TextView>(R.id.tvCatHoras)
        val tvPorCientoSin = rowSinView.findViewById<TextView>(R.id.tvCatPorcentaje)
        val pbSin = rowSinView.findViewById<ProgressBar>(R.id.pbCatProgreso)

        tvNombreSin.text = "Sin registrar"
        tvHorasSin.text = String.format(Locale.US, "%.1f hrs", horasSin)
        tvPorCientoSin.text = String.format(Locale.US, "%.1f%%", porcentajeSin)
        pbSin.progress = porcentajeSin.toInt()

        layoutReporteCategorias.addView(rowSinView)

        // Cargar eventos de la semana
        val eventosList = dbHelper.obtenerEventosSemana(inicioStr, finStr)
        if (eventosList.isEmpty()) {
            val tvVacio = TextView(this).apply {
                text = "No hay eventos registrados en esta semana."
                textSize = 14f
                setPadding(10, 10, 10, 10)
            }
            layoutEventosSemana.addView(tvVacio)
        } else {
            for (eventoStr in eventosList) {
                val card = MaterialCardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 12) }
                    radius = 12f
                    cardElevation = 1f
                    setCardBackgroundColor(ContextCompat.getColor(this@ReportesActivity, android.R.color.white))
                }

                val tv = TextView(this).apply {
                    text = eventoStr
                    textSize = 14f
                    setPadding(16, 16, 16, 16)
                    setTextColor(ContextCompat.getColor(this@ReportesActivity, android.R.color.black))
                }

                card.addView(tv)
                layoutEventosSemana.addView(card)
            }
        }
    }
}
