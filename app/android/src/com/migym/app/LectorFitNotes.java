package com.migym.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lee un respaldo de FitNotes (el archivo .fitnotes es una copia de su base SQLite) y devuelve
 * sus tablas como JSON sin transformar; la página decide cómo convertirlas a los datos de la app.
 *
 * Forma: {categories: [[id, nombre]], exercises: [[id, nombre, categoría, tipo]],
 *         logs: [[ejercicio, fecha, kg, reps, serieDeRutina]], sections: [[rutina, nombre, sección, nombre, orden]],
 *         sectionEx: [[sección, ejercicio]], setSection: [[serieDeRutina, sección]], groups: [[fecha, grupo, ejercicio]]}
 */
final class LectorFitNotes {

    private LectorFitNotes() {}

    static String leer(File archivo) throws Exception {
        if (!esSqlite(archivo)) return error("Ese archivo no es un respaldo de FitNotes. Elige el que termina en .fitnotes.");
        SQLiteDatabase db = SQLiteDatabase.openDatabase(archivo.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            if (!tabla(db, "training_log") || !tabla(db, "exercise")) {
                return error("Ese archivo no es un respaldo de FitNotes. Elige el que termina en .fitnotes.");
            }
            JSONObject o = new JSONObject();
            o.put("categories", tabla(db, "Category") ? filas(db, "SELECT _id, name FROM Category") : new JSONArray());
            o.put("exercises", filas(db, "SELECT _id, name, category_id, "
                    + (columna(db, "exercise", "exercise_type_id") ? "exercise_type_id" : "0") + " FROM exercise"));
            o.put("logs", filas(db, "SELECT exercise_id, date, metric_weight, reps, "
                    + (columna(db, "training_log", "routine_section_exercise_set_id") ? "routine_section_exercise_set_id" : "0")
                    + " FROM training_log ORDER BY date, _id"));
            if (tabla(db, "Routine") && tabla(db, "RoutineSection")) {
                o.put("sections", filas(db, "SELECT r._id, r.name, s._id, s.name, "
                        + (columna(db, "RoutineSection", "sort_order") ? "s.sort_order" : "0")
                        + " FROM Routine r JOIN RoutineSection s ON s.routine_id = r._id ORDER BY r._id, 5, s._id"));
            }
            if (tabla(db, "RoutineSectionExercise")) {
                o.put("sectionEx", filas(db, "SELECT routine_section_id, exercise_id FROM RoutineSectionExercise ORDER BY routine_section_id, "
                        + (columna(db, "RoutineSectionExercise", "sort_order") ? "sort_order, " : "") + "_id"));
                if (tabla(db, "RoutineSectionExerciseSet")) {
                    o.put("setSection", filas(db, "SELECT st._id, rse.routine_section_id FROM RoutineSectionExerciseSet st "
                            + "JOIN RoutineSectionExercise rse ON rse._id = st.routine_section_exercise_id"));
                }
            }
            if (tabla(db, "WorkoutGroupExercise")) {
                o.put("groups", filas(db, "SELECT date, workout_group_id, exercise_id FROM WorkoutGroupExercise ORDER BY date, workout_group_id, _id"));
            }
            return o.toString();
        } finally {
            db.close();
        }
    }

    static String error(String mensaje) {
        try {
            return new JSONObject().put("error", mensaje).toString();
        } catch (Exception e) {
            return "{\"error\":\"No pude leer el respaldo.\"}";
        }
    }

    private static boolean esSqlite(File f) throws IOException {
        byte[] cabecera = new byte[16];
        InputStream in = new FileInputStream(f);
        try {
            if (in.read(cabecera) < 16) return false;
        } finally {
            in.close();
        }
        return new String(cabecera, 0, 15, "US-ASCII").equals("SQLite format 3");
    }

    private static boolean tabla(SQLiteDatabase db, String nombre) {
        Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new String[] { nombre });
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private static boolean columna(SQLiteDatabase db, String tabla, String col) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + tabla + ")", null);
        try {
            int i = c.getColumnIndex("name");
            while (c.moveToNext()) if (col.equals(c.getString(i))) return true;
            return false;
        } finally {
            c.close();
        }
    }

    private static JSONArray filas(SQLiteDatabase db, String sql) throws Exception {
        Cursor c = db.rawQuery(sql, null);
        JSONArray out = new JSONArray();
        try {
            while (c.moveToNext()) {
                JSONArray fila = new JSONArray();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: fila.put(JSONObject.NULL); break;
                        case Cursor.FIELD_TYPE_INTEGER: fila.put(c.getLong(i)); break;
                        case Cursor.FIELD_TYPE_FLOAT: fila.put(c.getDouble(i)); break;
                        default: fila.put(c.getString(i));
                    }
                }
                out.put(fila);
            }
        } finally {
            c.close();
        }
        return out;
    }
}
