package com.abcell.movil;

import org.json.JSONObject;

/** Immutable inputs shared by PNG, PDF and thermal rendering. No live DB access during printing. */
public final class ReceiptData {
    public final JSONObject order;
    public final String name, address, phone, warranty, thanks, logo;
    public final boolean includeAccess, demo;
    public ReceiptData(JSONObject order, String name, String address, String phone, String warranty,
                       String thanks, String logo, boolean includeAccess, boolean demo) {
        try { this.order=new JSONObject(order.toString()); } catch (Exception e) { throw new IllegalArgumentException(e); }
        this.name=name; this.address=address; this.phone=phone; this.warranty=warranty;
        this.thanks=thanks; this.logo=logo; this.includeAccess=includeAccess; this.demo=demo;
    }
    public static ReceiptData load(WorkshopDb db, JSONObject order, boolean demo) {
        return new ReceiptData(order, db.setting("nombre","Mi taller"), db.setting("direccion",""),
                db.setting("telefono",""), db.setting("garantia","Conserva esta nota para recoger tu equipo."),
                db.setting("ticket_thanks","¡Gracias por su preferencia!"), db.setting("ticket_logo",""),
                db.setting("ticket_access","No").equals("Sí"), demo);
    }
    public static JSONObject demoOrder() {
        try { return new JSONObject().put("folio",1).put("fecha_ingreso",WorkshopDb.now()).put("estatus","PRUEBA")
                .put("cliente_nombre","Cliente de muestra").put("cliente_tel","961 000 0000")
                .put("marca","Samsung").put("modelo","Galaxy A52").put("color","Negro")
                .put("imei","DEMO-123456").put("ubicacion","Mostrador").put("estado_fisico","Equipo con funda; sin golpes visibles.")
                .put("falla","Cambio de pantalla y revisión del centro de carga.").put("costo_total",950)
                .put("anticipo",300).put("saldo",650); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
