package com.abcell.movil;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class ReceiptRendererTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private ReceiptData sample(boolean access,String logo) throws Exception {
        JSONObject o=ReceiptData.demoOrder().put("fecha_ingreso","2026-11-27 12:30:00").put("estatus","Recibido")
                .put("pin_pass","PIN-SECRETO").put("patron","PATRON-SECRETO").put("notas_internas","NOTA-PRIVADA");
        return new ReceiptData(o,"ABCell · Taller de celulares","Av. Central 120, Tuxtla Gutiérrez, Chiapas","961 123 4567",
                "Garantía de 30 días sobre el servicio realizado. No cubre golpes, humedad ni daño por terceros.","¡Gracias por su preferencia!",logo,access,false);
    }
    private String allText(ReceiptRenderer.Result r){StringBuilder s=new StringBuilder();for(ReceiptRenderer.TextRun t:r.text)s.append(t.text).append('\n');return s.toString();}
    private void inside(ReceiptRenderer.Result r){
        for(ReceiptRenderer.TextRun t:r.text){assertTrue(t.text,t.x>=0);assertTrue(t.text,t.right<=r.bitmap.getWidth()+.1);assertTrue(t.text,t.top>=0);assertTrue(t.text,t.bottom<=r.bitmap.getHeight());}
    }
    private void save(String name,Bitmap image) throws Exception {
        File folder=new File(context.getExternalFilesDir(null),"receipt-previews");assertTrue(folder.isDirectory()||folder.mkdirs());
        try(OutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    private String logo() throws Exception {
        // Synthetic workshop logo, not a real customer's asset.
        Bitmap b=Bitmap.createBitmap(160,110,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(2,132,199));c.drawRoundRect(6,6,154,104,12,12,p);p.setColor(Color.WHITE);p.setTextSize(46);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("ABC",25,74,p);
        ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
    }
    @Test public void desktopHierarchyAndPrivacyDefaults() throws Exception {
        ReceiptRenderer.Result r=ReceiptRenderer.render(sample(false,""),640,false);inside(r);String s=allText(r);
        assertTrue(s.contains("NOTA DE TALLER"));assertTrue(s.contains("FOLIO #0001"));assertTrue(s.contains("27/11/2026 12:30"));assertTrue(s.contains("GARANTÍA Y CONDICIONES"));assertTrue(s.contains("SALDO PENDIENTE"));
        assertFalse(s.contains("PIN-SECRETO"));assertFalse(s.contains("PATRON-SECRETO"));assertFalse(s.contains("NOTA-PRIVADA"));
        assertEquals(Color.WHITE,r.bitmap.getPixel(0,0));boolean blue=false;for(int y=0;y<r.bitmap.getHeight();y++)for(int x=0;x<640;x++)if(r.bitmap.getPixel(x,y)==Color.rgb(2,132,199))blue=true;assertTrue(blue);save("nota-digital-sin-logo-640.png",r.bitmap);r.bitmap.recycle();
    }
    @Test public void accessMustBeExplicitlyEnabledAndInternalNotesNeverPrint() throws Exception {
        ReceiptRenderer.Result r=ReceiptRenderer.render(sample(true,""),640,false);String s=allText(r);assertTrue(s.contains("PIN-SECRETO"));assertTrue(s.contains("PATRON-SECRETO"));assertFalse(s.contains("NOTA-PRIVADA"));r.bitmap.recycle();
    }
    @Test public void exportActualAndroidPreviewsForBothRollWidths() throws Exception {
        ReceiptData data=sample(false,logo());
        ReceiptRenderer.Result digital=ReceiptRenderer.render(data,640,false);inside(digital);save("nota-digital-640.png",digital.bitmap);digital.bitmap.recycle();
        for(PaperProfile profile:PaperProfile.values()){
            ReceiptRenderer.Result thermal=ReceiptRenderer.render(data,profile.dots,true);inside(thermal);assertEquals(profile.dots,thermal.bitmap.getWidth());
            save("nota-termica-"+profile.key+".png",thermal.bitmap);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();Bitmap b=thermal.bitmap;
            EscPosRaster.write(bytes,b.getWidth(),b.getHeight(),(y,row)->b.getPixels(row,0,b.getWidth(),0,y,b.getWidth(),1),false,null);
            assertTrue(bytes.size()>b.getWidth()*b.getHeight()/8);thermal.bitmap.recycle();
        }
    }
    @Test public void longWordsAndLargeAmountsWrapWithoutClippingOrCropping() throws Exception {
        ReceiptData base=sample(false,"");JSONObject o=new JSONObject(base.order.toString()).put("cliente_nombre",String.join("",Collections.nCopies(12,"ClienteSinEspacios")))
                .put("falla",String.join(" ",Collections.nCopies(150,"diagnóstico"))).put("costo_total",999999999).put("anticipo",0).put("saldo",999999999);
        ReceiptData longData=new ReceiptData(o,base.name,base.address,base.phone,base.warranty,base.thanks,"",false,false);
        ReceiptRenderer.Result r=ReceiptRenderer.render(longData,384,true);inside(r);assertTrue(r.bitmap.getHeight()>2000);assertTrue(allText(r).contains("GARANTÍA Y CONDICIONES"));save("nota-termica-58-texto-largo.png",r.bitmap);r.bitmap.recycle();
    }
    @Test public void logoIsPortableThroughExistingBackup() throws Exception {
        context.deleteDatabase("receipt-test.db");WorkshopDb db=new WorkshopDb(context,"receipt-test.db");
        try{
            String logo=logo();db.settings(new JSONObject().put("ticket_logo",logo));JSONObject backup=db.snapshot();
            db.settings(new JSONObject().put("ticket_logo",""));db.restore(backup);assertEquals(logo,db.setting("ticket_logo",""));
            Bitmap bitmap=ReceiptLogo.decode(db.setting("ticket_logo",""));assertNotNull(bitmap);assertTrue(bitmap.getWidth()<=256);bitmap.recycle();
        }finally{db.close();context.deleteDatabase("receipt-test.db");}
    }
    @Test public void invalidLogoDoesNotDecode() {assertNull(ReceiptLogo.decode("esto no es PNG"));}
}
