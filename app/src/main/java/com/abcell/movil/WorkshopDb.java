package com.abcell.movil;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Android port of database/db_manager.py and services/workshop_service.py. */
public final class WorkshopDb extends SQLiteOpenHelper {
    private final Context context;
    public static final String[] TABLES = {"ordenes", "pagos_orden", "servicios_catalogo", "ubicaciones", "notificaciones_wa", "inventario", "mobile_settings"};
    public static final String[] STATES = {"Recibido", "En diagnóstico", "Esperando refacción", "En reparación", "Listo para entregar", "Entregado", "Cancelado"};
    public static final String[] METHODS = {"Efectivo", "Transferencia", "Tarjeta", "Depósito"};
    public static final String[] CATEGORIES = {"Celulares", "Consolas", "Tablets", "Laptops", "Smartwatches", "Accesorios", "Otros"};

    public WorkshopDb(Context context) { this(context, "abcell_movil.db"); }
    public WorkshopDb(Context context, String name) { super(context, name, null, 1); this.context = context; }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        try {
            for (String sql : asset("schema.sql").split(";")) if (!sql.trim().isEmpty()) db.execSQL(sql);
            db.execSQL("CREATE TABLE mobile_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            db.execSQL("CREATE INDEX idx_orders_state ON ordenes(estatus)");
            db.execSQL("CREATE INDEX idx_payments_order ON pagos_orden(folio)");
            JSONObject seed = new JSONObject(asset("catalogo.json"));
            for (String table : new String[]{"ubicaciones", "servicios_catalogo"}) {
                JSONArray rows = seed.getJSONArray(table);
                for (int i = 0; i < rows.length(); i++) db.insertOrThrow(table, null, values(rows.getJSONObject(i)));
            }
        } catch (Exception e) { throw new IllegalStateException("No se pudo crear la base del taller", e); }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future upgrades must migrate data; never drop workshop tables.
        throw new IllegalStateException("Versión de datos no compatible");
    }
    private String asset(String name) throws Exception {
        try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    public static String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()); }
    public List<JSONObject> rows(String sql, String... args) {
        List<JSONObject> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    Object value;
                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: value = JSONObject.NULL; break;
                        case Cursor.FIELD_TYPE_INTEGER: value = c.getLong(i); break;
                        case Cursor.FIELD_TYPE_FLOAT: value = c.getDouble(i); break;
                        default: value = c.getString(i);
                    }
                    try { row.put(c.getColumnName(i), value); } catch (Exception e) { throw new IllegalStateException(e); }
                }
                result.add(row);
            }
        }
        return result;
    }
    public JSONObject order(long id) {
        List<JSONObject> r = rows("SELECT * FROM ordenes WHERE folio=?", "" + id);
        if (r.isEmpty()) throw new IllegalArgumentException("La orden ya no existe.");
        return r.get(0);
    }
    public static ContentValues values(JSONObject row) {
        ContentValues v = new ContentValues();
        row.keys().forEachRemaining(key -> {
            Object value = row.opt(key);
            if (value == null || value == JSONObject.NULL) v.putNull(key);
            else if (value instanceof Integer || value instanceof Long) v.put(key, ((Number)value).longValue());
            else if (value instanceof Number) v.put(key, ((Number)value).doubleValue());
            else if (value instanceof String) v.put(key, (String)value);
            else throw new IllegalArgumentException("Dato no compatible: " + key);
        });
        return v;
    }
    public static long amount(JSONObject row, String key) { return Money.stored(row.optDouble(key, 0)); }
    private static long validMoney(JSONObject row, String key) {
        Object raw = row.opt(key);
        if (raw == null) return 0;
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Importe no válido: " + key);
        double value = ((Number)raw).doubleValue();
        long cents = Money.stored(value);
        if (!Double.isFinite(value) || value < 0 || cents > 99999999900L || Math.abs(value * 100 - cents) > 0.0001)
            throw new IllegalArgumentException("Importe no válido: " + key);
        return cents;
    }
    private static void validStock(JSONObject row, String key) {
        Object raw = row.opt(key);
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Existencias no válidas");
        double n = ((Number)raw).doubleValue();
        if (!Double.isFinite(n) || n < 0 || n > 10000000 || n != Math.floor(n)) throw new IllegalArgumentException("Existencias no válidas");
    }
    private static void required(JSONObject row, String... keys) {
        for (String key : keys) if (row.optString(key, "").trim().isEmpty()) throw new IllegalArgumentException("Completa los campos obligatorios.");
    }
    public long saveOrder(JSONObject data, long id) {
        required(data, "cliente_nombre", "cliente_tel", "marca", "modelo", "falla");
        long total = validMoney(data, "costo_total");
        validMoney(data, "costo_repuesto");
        long paid = id == 0 ? validMoney(data, "anticipo") : amount(order(id), "anticipo");
        long balance = Money.balance(total, paid);
        ContentValues v = values(data);
        v.remove("folio");
        v.put("anticipo", paid / 100.0); v.put("saldo", balance / 100.0);
        v.put("fecha_actualizacion", now());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (id == 0) {
                v.put("fecha_ingreso", now()); v.put("estatus", STATES[0]);
                id = db.insertOrThrow("ordenes", null, v);
                if (paid > 0) insertPayment(db, id, paid, data.optString("metodo_pago", METHODS[0]));
            } else {
                v.remove("fecha_ingreso"); v.remove("estatus");
                if (db.update("ordenes", v, "folio=?", new String[]{""+id}) != 1) throw new IllegalArgumentException("Orden no encontrada");
            }
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }
    private void insertPayment(SQLiteDatabase db, long id, long cents, String method) {
        if (!Arrays.asList(METHODS).contains(method)) throw new IllegalArgumentException("Método de pago no válido");
        ContentValues v = new ContentValues();
        v.put("folio", id); v.put("monto", cents / 100.0); v.put("metodo_pago", method); v.put("fecha", now());
        db.insertOrThrow("pagos_orden", null, v);
    }
    public void pay(long id, long cents, String method) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject o = order(id);
            if (o.optString("estatus").equals("Cancelado")) throw new IllegalArgumentException("No se pueden abonar órdenes canceladas.");
            long total = amount(o, "costo_total");
            long paid = Money.pay(total, amount(o, "anticipo"), cents);
            insertPayment(db, id, cents, method);
            ContentValues v = new ContentValues(); v.put("anticipo", paid / 100.0);
            v.put("saldo", (total-paid) / 100.0); v.put("metodo_pago", method); v.put("fecha_actualizacion", now());
            db.update("ordenes", v, "folio=?", new String[]{""+id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void status(long id, String state, String location, String notes) {
        if (!Arrays.asList(STATES).contains(state)) throw new IllegalArgumentException("Estado no válido");
        order(id);
        ContentValues v = new ContentValues(); v.put("estatus", state); v.put("ubicacion", location);
        v.put("notas_internas", notes); v.put("fecha_actualizacion", now());
        getWritableDatabase().update("ordenes", v, "folio=?", new String[]{""+id});
    }
    public long saveItem(String table, JSONObject row, long id) {
        if (!Arrays.asList("inventario", "servicios_catalogo", "ubicaciones").contains(table)) throw new IllegalArgumentException("Tabla no válida");
        required(row, "nombre");
        if (table.equals("inventario")) {
            validStock(row, "stock"); validStock(row, "stock_minimo");
            validMoney(row, "costo_compra"); validMoney(row, "precio_venta");
        } else if (table.equals("servicios_catalogo")) validMoney(row, "precio");
        ContentValues v = values(row); v.remove("id");
        if (table.equals("inventario")) v.put("fecha_actualizacion", now());
        if (id == 0) return getWritableDatabase().insertOrThrow(table, null, v);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if (table.equals("ubicaciones")) {
                List<JSONObject> old = rows("SELECT nombre FROM ubicaciones WHERE id=?", ""+id);
                if (!old.isEmpty()) {
                    ContentValues rename = new ContentValues(); rename.put("ubicacion", row.optString("nombre"));
                    db.update("ordenes", rename, "ubicacion=?", new String[]{old.get(0).optString("nombre")});
                    db.update("inventario", rename, "ubicacion=?", new String[]{old.get(0).optString("nombre")});
                }
            }
            db.update(table, v, "id=?", new String[]{""+id}); db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }
    public void delete(String table, long id) {
        if (!Arrays.asList("inventario", "servicios_catalogo", "ubicaciones", "ordenes").contains(table)) throw new IllegalArgumentException("Tabla no válida");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if (table.equals("ordenes")) {
                db.delete("pagos_orden", "folio=?", new String[]{""+id});
                db.delete("notificaciones_wa", "folio=?", new String[]{""+id});
            }
            if (table.equals("ubicaciones")) {
                List<JSONObject> old = rows("SELECT nombre FROM ubicaciones WHERE id=?", ""+id);
                if (!old.isEmpty() && (!rows("SELECT folio FROM ordenes WHERE ubicacion=? LIMIT 1", old.get(0).optString("nombre")).isEmpty()
                        || !rows("SELECT id FROM inventario WHERE ubicacion=? LIMIT 1", old.get(0).optString("nombre")).isEmpty()))
                    throw new IllegalArgumentException("Esta ubicación está en uso. Reasigna los equipos y productos primero.");
            }
            db.delete(table, table.equals("ordenes") ? "folio=?" : "id=?", new String[]{""+id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public String setting(String key, String fallback) {
        List<JSONObject> r = rows("SELECT value FROM mobile_settings WHERE key=?", key);
        return r.isEmpty() ? fallback : r.get(0).optString("value", fallback);
    }
    public void settings(JSONObject settings) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            settings.keys().forEachRemaining(key -> {
                ContentValues v = new ContentValues(); v.put("key", key); v.put("value", settings.optString(key));
                db.insertWithOnConflict("mobile_settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            });
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public JSONObject snapshot() throws Exception {
        JSONObject result = new JSONObject(); result.put("format", "abcell-movil"); result.put("version", 1); result.put("created", now());
        JSONObject tables = new JSONObject();
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (String table : TABLES) tables.put(table, new JSONArray(rows("SELECT * FROM " + table)));
            result.put("tables", tables); db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }
    /** All replacement writes and validation are transactional: invalid backups leave the existing data intact. */
    public void restore(JSONObject backup) throws Exception {
        if (!backup.optString("format").equals("abcell-movil") || backup.optInt("version") != 1)
            throw new IllegalArgumentException("Selecciona un respaldo JSON de ABCell Móvil versión 1.");
        JSONObject tables = backup.getJSONObject("tables");
        if (tables.length() != TABLES.length) throw new IllegalArgumentException("Respaldo incompleto");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (String table : new String[]{"pagos_orden", "notificaciones_wa", "ordenes", "inventario", "ubicaciones", "servicios_catalogo", "mobile_settings"}) db.delete(table, null, null);
            for (String table : TABLES) {
                List<String> columns;
                try (Cursor c = db.rawQuery("SELECT * FROM " + table + " LIMIT 0", null)) { columns = Arrays.asList(c.getColumnNames()); }
                JSONArray records = tables.getJSONArray(table);
                if (records.length() > 100000) throw new IllegalArgumentException("Respaldo demasiado grande");
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    if (record.length() != columns.size()) throw new IllegalArgumentException("Estructura de respaldo no válida: " + table);
                    record.keys().forEachRemaining(k -> { if (!columns.contains(k)) throw new IllegalArgumentException("Columna no válida"); });
                    db.insertOrThrow(table, null, values(record));
                }
            }
            for (JSONObject o : rows("SELECT * FROM ordenes")) {
                required(o, "cliente_nombre", "cliente_tel", "marca", "modelo", "falla", "fecha_ingreso");
                long total = validMoney(o, "costo_total"), paid = validMoney(o, "anticipo");
                validMoney(o, "costo_repuesto"); validMoney(o, "saldo");
                if (Money.balance(total, paid) != amount(o, "saldo") || !Arrays.asList(STATES).contains(o.optString("estatus"))) throw new IllegalArgumentException("Orden inconsistente");
                JSONObject payments = rows("SELECT COALESCE(SUM(monto),0) AS total FROM pagos_orden WHERE folio=?", o.optString("folio")).get(0);
                if (amount(payments, "total") != paid) throw new IllegalArgumentException("Historial de abonos inconsistente");
            }
            for (JSONObject p : rows("SELECT * FROM pagos_orden")) if (validMoney(p, "monto") <= 0) throw new IllegalArgumentException("Abono no válido");
            for (JSONObject item : rows("SELECT * FROM inventario")) {
                required(item, "nombre"); validStock(item, "stock"); validStock(item, "stock_minimo");
                validMoney(item, "costo_compra"); validMoney(item, "precio_venta");
            }
            for (JSONObject service : rows("SELECT * FROM servicios_catalogo")) { required(service, "nombre"); validMoney(service, "precio"); }
            if (!rows("SELECT id FROM pagos_orden WHERE monto<=0").isEmpty() || !rows("SELECT id FROM inventario WHERE stock<0 OR stock_minimo<0 OR costo_compra<0 OR precio_venta<0").isEmpty()) throw new IllegalArgumentException("Importes o existencias no válidos");
            try (Cursor check = db.rawQuery("PRAGMA foreign_key_check", null)) { if (check.moveToFirst()) throw new IllegalArgumentException("Relaciones de respaldo no válidas"); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
