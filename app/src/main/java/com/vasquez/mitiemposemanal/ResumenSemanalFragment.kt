package com.vasquez.mitiemposemanal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class ResumenSemanalFragment : Fragment() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tvSemanaRango: TextView
    private lateinit var layoutResumenCategorias: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_resumen_semanal, container, false)

        dbHelper = DatabaseHelper(requireContext())
        tvSemanaRango = view.findViewById(R.id.tvSemanaRango)
        layoutResumenCategorias = view.findViewById(R.id.layoutResumenCategorias)

        cargarResumenSemanal()

        return view
    }

    override fun onResume() {
        super.onResume()
        cargarResumenSemanal()
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

        val inflater = LayoutInflater.from(requireContext())

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
}
