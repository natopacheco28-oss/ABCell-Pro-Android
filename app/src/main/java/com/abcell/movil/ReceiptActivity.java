package com.abcell.movil;

import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.concurrent.*;

/** One preview and design source for digital PNG, Android printing/PDF and Bluetooth. */
public final class ReceiptActivity extends ScreenActivity {
    private WorkshopDb db;
    private ImageView image;
    private TextView status;
    private Spinner mode;
    private Button share,pdf,bluetooth;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap displayed;
    private ReceiptData receipt;
    private PaperProfile paper;
    private long folio;
    private boolean demo,destroyed,rendering;
    private int generation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);db=new WorkshopDb(this);folio=getIntent().getLongExtra("folio",0);demo=folio<=0;
        shell("Nota de taller","Mismo diseño de la nota digital de ABCell Pro: logo, folio destacado, secciones y recuadro de importes. En térmica se adapta a blanco y negro y al ancho útil.");
        mode=new Spinner(this);mode.setMinimumHeight(dp(52));mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Nota digital · color (PNG)","Vista térmica · ancho de impresora"}));body.addView(mode);
        status=text("Preparando nota…",14,MUTED);body.addView(status);
        share=button("Compartir nota digital PNG",true,this::sharePng);
        pdf=button("Imprimir / guardar nota PDF",false,()->{
            if(rendering)return;
            TicketPrinter.print(this,displayed,paper,"ABCell-"+(demo?"prueba":folio));
        });
        bluetooth=button("Imprimir por Bluetooth",false,()->{Intent i=new Intent(this,BluetoothPrinterActivity.class);if(!demo)i.putExtra("folio",folio);startActivity(i);});
        image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Vista previa de la nota de taller");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(18);body.addView(image,p);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){renderReceipt();}});
        if(state!=null)mode.setSelection(state.getInt("mode",0));
    }
    @Override protected void onResume(){super.onResume();renderReceipt();}
    private void renderReceipt(){
        try{receipt=ReceiptData.load(db,demo?ReceiptData.demoOrder():db.order(folio),demo);paper=PaperProfile.from(db.setting("printer_paper","58"));}
        catch(Exception e){failure(e.getMessage());return;}
        final ReceiptData snapshot=receipt;boolean thermal=mode.getSelectedItemPosition()==1;int width=thermal?paper.dots:ReceiptRenderer.DIGITAL_WIDTH;
        int current=++generation;rendering=true;share.setEnabled(false);pdf.setEnabled(false);status.setText("Generando vista previa…");
        worker.execute(()->{
            Bitmap bitmap=null;Exception error=null;try{bitmap=ReceiptRenderer.render(snapshot,width,thermal).bitmap;}catch(Exception e){error=e;}
            final Bitmap ready=bitmap;final Exception failed=error;
            runOnUiThread(()->{
                if(destroyed||current!=generation){if(ready!=null)ready.recycle();return;}
                rendering=false;
                if(failed!=null){status.setText("No se pudo generar la nota.");failure(failed.getMessage());return;}
                displayed=ready;image.setImageBitmap(ready);share.setEnabled(true);pdf.setEnabled(true);
                status.setText((thermal?paper.label+" · gráfico ESC/POS":"PNG a color · 640 px · diseño de PC")+(snapshot.includeAccess?"\nAtención: esta nota incluye PIN/patrón porque lo activaste en configuración.":"\nPIN, patrón y notas internas ocultos.")+(demo?"\nVista de prueba, no es una orden real.":""));
            });
        });
    }
    private void sharePng(){
        if(rendering)return;share.setEnabled(false);ReceiptData snapshot=receipt;
        worker.execute(()->{
            Bitmap bitmap=null;Exception error=null;Uri uri=null;
            try{
                File folder=new File(getCacheDir(),"receipts");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("No se pudo preparar el archivo compartido.");
                // Keep each export immutable while receiving apps are reading it; age out only stale cache files.
                File[] old=folder.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>7L*24*60*60*1000) { if(!f.delete())f.deleteOnExit(); }
                File file=File.createTempFile("ABCell-"+(demo?"prueba":folio)+"-",".png",folder);
                bitmap=ReceiptRenderer.render(snapshot,ReceiptRenderer.DIGITAL_WIDTH,false).bitmap;
                try(OutputStream out=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("No se pudo guardar la nota PNG.");}
                uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
            }catch(Exception e){error=e;}finally{if(bitmap!=null)bitmap.recycle();}
            final Uri ready=uri;final Exception failed=error;
            runOnUiThread(()->{
                if(destroyed)return;share.setEnabled(true);if(failed!=null){failure(failed.getMessage());return;}
                Intent i=new Intent(Intent.ACTION_SEND);i.setType("image/png");i.putExtra(Intent.EXTRA_STREAM,ready);i.putExtra(Intent.EXTRA_SUBJECT,"Nota de taller #"+folio);
                i.setClipData(ClipData.newUri(getContentResolver(),"Nota de taller",ready));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i,"Compartir nota digital"));
            });
        });
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("mode",mode.getSelectedItemPosition());}
    @Override protected void onDestroy(){destroyed=true;generation++;worker.shutdown();db.close();super.onDestroy();}
}
