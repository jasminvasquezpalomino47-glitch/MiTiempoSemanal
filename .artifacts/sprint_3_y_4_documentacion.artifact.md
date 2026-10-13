# Documentación Oficial: Sprints 3 y 4 • MiTiempoSemanal
**Curso:** Desarrollo de Aplicaciones Móviles · SENATI
**Estudiante:** Jasmin Vásquez
**Versión de la App:** v1.0.0

---

## 📊 Sprint 3: Porcentaje semanal por categoría

### Objetivo del Sprint
Mostrar en la pantalla de gestión de eventos qué porcentaje de las **168 horas** de la semana (10,080 minutos) dedica el usuario a cada categoría, mediante un panel dinámico que se actualiza automáticamente al finalizar un evento.

### Reglas de Negocio Implementadas
1. **Rango semanal:** La semana va de lunes 00:00 a domingo 23:59.
2. **Total de minutos:** 168 horas × 60 minutos = **10,080 minutos**.
3. **Eventos contabilizados:** Solo se consideran eventos **finalizados** que comenzaron en la semana actual. El evento en curso no suma hasta pulsar *Finalizar*.
4. **Fórmula de porcentaje:**
   $$\text{Porcentaje} = \frac{\text{Minutos de la categoría en la semana}}{10,080} \times 100$$
5. **Fila adicional "Sin registrar":** Muestra las horas y el porcentaje restante de la semana que no tienen eventos asociados.

### Historia de Usuario (HU-06)
* **HU-06: Ver el porcentaje semanal por categoría**
  * *Criterios cumplidos:* Panel superior con el rango de la semana actual, una fila por categoría con horas acumuladas, porcentaje con un decimal (ej. `12.5%`) y barra de progreso (`ProgressBar`). Actualización en tiempo real al finalizar un evento.

### Requisitos Técnicos
* **Consulta SQL optimizada con `SUM` y `GROUP BY`:**
  ```sql
  SELECT t.categoria_id, SUM((strftime('%s', e.fin) - strftime('%s', e.inicio)) / 60) as total_min
  FROM eventos e
  INNER JOIN tareas t ON e.tarea_id = t.id
  WHERE e.inicio >= ? AND e.inicio <= ? AND e.fin IS NOT NULL
  GROUP BY t.categoria_id
  ```
* **Panel dinámico:** Generado por código en Kotlin e integrado en la pantalla de gestión de eventos.

---

## 📈 Sprint 4: Cierre semanal y reportes (Versión 1.0.0)

### Objetivo del Sprint
Guardar automáticamente el reporte de cada semana terminada al abrir la aplicación y permitir consultarlas posteriormente mediante un selector de rangos de fechas (Spinner), alcanzando la versión **1.0.0** de la app.

### Historias de Usuario e Implementación
1. **HU-07: Cerrar la semana automáticamente**
   * Al abrir la app, si hay semanas terminadas sin reporte, se calculan y guardan automáticamente en la base de datos.
   * Los eventos históricos no se borran; se mantienen asociados a su semana correspondiente.
   * Evita duplicados mediante la restricción `UNIQUE (semana_inicio, semana_fin, categoria_id)`.
   * El panel de la semana actual inicia en cero cada lunes.

2. **HU-08: Consultar reportes por rango de fechas**
   * Pantalla **Reportes** (`ReportesActivity`) accesible desde el menú principal.
   * Contiene un `Spinner` dinámico con las semanas cerradas en formato `"06/10/2026 – 12/10/2026"`, ordenadas de la más reciente a la más antigua.
   * Al seleccionar una semana, muestra el desglose de categorías (horas, porcentaje sobre 168h y "Sin registrar") y la lista detallada de eventos de esa semana.
   * Muestra el mensaje `"Aún no hay reportes"` si no existen semanas cerradas.

### Requisitos Técnicos
* **Nueva tabla `reportes_semanales`** en SQLite:
  ```sql
  CREATE TABLE reportes_semanales (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      semana_inicio TEXT NOT NULL,
      semana_fin TEXT NOT NULL,
      categoria_id INTEGER NOT NULL,
      minutos INTEGER NOT NULL,
      FOREIGN KEY(categoria_id) REFERENCES categorias(id),
      UNIQUE(semana_inicio, semana_fin, categoria_id)
  );
  ```
* **Migración de base de datos:** Versión actualizada a **`3`** mediante `onUpgrade`, preservando todos los datos de los sprints anteriores.
