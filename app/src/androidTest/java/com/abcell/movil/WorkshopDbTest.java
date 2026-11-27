package com.abcell.movil;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;

public class WorkshopDbTest {
    private WorkshopDb db;
    private Context context;
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("test.db");db=new WorkshopDb(context,"test.db");}
    @After public void teardown(){db.close();context.deleteDatabase("test.db");}
    private JSONObject order() throws Exception {return new JSONObject().put("cliente_nombre","Ana").put("cliente_tel","9611234567").put("marca","Samsung").put("modelo","A52").put("falla","Pantalla rota").put("costo_total",100).put("anticipo",20).put("metodo_pago","Efectivo");}
    @Test public void initialCatalogMatchesDesktop(){assertEquals(5,db.rows("SELECT * FROM servicios_catalogo").size());assertEquals(4,db.rows("SELECT * FROM ubicaciones").size());}
    @Test public void orderPaymentAndPersistence() throws Exception {
        long id=db.saveOrder(order(),0);assertEquals(8000,WorkshopDb.amount(db.order(id),"saldo"));
        db.pay(id,3000,"Transferencia");assertEquals(5000,WorkshopDb.amount(db.order(id),"saldo"));
        assertEquals(2,db.rows("SELECT * FROM pagos_orden").size());
        db.close();db=new WorkshopDb(context,"test.db");assertEquals(5000,WorkshopDb.amount(db.order(id),"anticipo"));
    }
    @Test public void invalidPaymentDoesNotWrite() throws Exception {
        long id=db.saveOrder(order(),0);try{db.pay(id,9000,"Efectivo");fail();}catch(IllegalArgumentException expected){}
        assertEquals(1,db.rows("SELECT * FROM pagos_orden").size());assertEquals(8000,WorkshopDb.amount(db.order(id),"saldo"));
    }
    @Test public void editCannotReduceTotalBelowPaid() throws Exception {
        long id=db.saveOrder(order(),0);try{db.saveOrder(order().put("costo_total",10),id);fail();}catch(IllegalArgumentException expected){}
        assertEquals(10000,WorkshopDb.amount(db.order(id),"costo_total"));
    }
    @Test public void deliveryDoesNotInventPayment() throws Exception {
        long id=db.saveOrder(order(),0);db.status(id,"Entregado","Mostrador","");assertEquals(8000,WorkshopDb.amount(db.order(id),"saldo"));assertEquals(1,db.rows("SELECT * FROM pagos_orden").size());
    }
    @Test public void snapshotRestoresDataAndSettings() throws Exception {
        long id=db.saveOrder(order(),0);db.settings(new JSONObject().put("nombre","Taller prueba"));JSONObject backup=db.snapshot();
        db.delete("ordenes",id);db.restore(backup);assertEquals(8000,WorkshopDb.amount(db.order(id),"saldo"));assertEquals("Taller prueba",db.setting("nombre",""));
    }
    @Test public void malformedBackupRollsBackEverything() throws Exception {
        long id=db.saveOrder(order(),0);JSONObject backup=db.snapshot();backup.getJSONObject("tables").getJSONArray("ordenes").getJSONObject(0).put("saldo",-1);
        try{db.restore(backup);fail();}catch(IllegalArgumentException expected){}
        assertEquals(8000,WorkshopDb.amount(db.order(id),"saldo"));assertEquals(1,db.rows("SELECT * FROM pagos_orden").size());
    }
    @Test public void negativeCatalogBackupRollsBack() throws Exception {
        JSONObject backup=db.snapshot();backup.getJSONObject("tables").getJSONArray("servicios_catalogo").getJSONObject(0).put("precio",-1);
        try{db.restore(backup);fail();}catch(IllegalArgumentException expected){}
        assertTrue(db.rows("SELECT id FROM servicios_catalogo WHERE precio<0").isEmpty());
        assertEquals(5,db.rows("SELECT * FROM servicios_catalogo").size());
    }
    @Test public void invalidProductIsRejectedByService() throws Exception {
        JSONObject p=new JSONObject().put("nombre","Pantalla").put("stock",-1).put("stock_minimo",2).put("costo_compra",0).put("precio_venta",0);
        try{db.saveItem("inventario",p,0);fail();}catch(IllegalArgumentException expected){}
        assertTrue(db.rows("SELECT * FROM inventario").isEmpty());
    }
    @Test public void deletionRemovesPaymentHistory() throws Exception {long id=db.saveOrder(order(),0);db.delete("ordenes",id);assertTrue(db.rows("SELECT * FROM pagos_orden").isEmpty());}
    @Test public void locationRenamePropagates() throws Exception {
        long id=db.saveOrder(order().put("ubicacion","Mostrador"),0);
        long loc=db.rows("SELECT id FROM ubicaciones WHERE nombre='Mostrador'").get(0).optLong("id");
        db.saveItem("ubicaciones",new JSONObject().put("nombre","Recepción nueva"),loc);assertEquals("Recepción nueva",db.order(id).optString("ubicacion"));
        try{db.delete("ubicaciones",loc);fail();}catch(IllegalArgumentException expected){}
    }
}
