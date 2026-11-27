package com.abcell.movil;

import android.app.Activity;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.*;
import android.print.*;
import android.print.pdf.PrintedPdfDocument;
import java.io.FileOutputStream;
import java.util.*;

/** Prints the composed graphic, not a second text-only template. Default media is an actual roll. */
public final class TicketPrinter extends PrintDocumentAdapter {
    private final Activity activity;
    private final Bitmap image;
    private final String name;
    private PrintAttributes attributes;
    private int pages;
    private TicketPrinter(Activity activity,Bitmap image,String name){this.activity=activity;this.image=image;this.name=name;}
    public static void print(Activity activity,Bitmap image,PaperProfile paper,String name){
        if(image==null)throw new IllegalArgumentException("Espera a que se genere la nota.");
        int useful=paper.widthMils()-2*paper.marginMils();
        int height=Math.max(1000,Math.round(useful*(float)image.getHeight()/image.getWidth())+2*paper.marginMils());
        PrintManager manager=(PrintManager)activity.getSystemService(Activity.PRINT_SERVICE);
        manager.print(name,new TicketPrinter(activity,image,name),new PrintAttributes.Builder()
                .setMediaSize(new PrintAttributes.MediaSize("ABCELL_ROLL_"+paper.key,"Rollo "+paper.millimeters+" mm",paper.widthMils(),height))
                .setMinMargins(new PrintAttributes.Margins(paper.marginMils(),paper.marginMils(),paper.marginMils(),paper.marginMils()))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
    }
    @Override public void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal cancel,LayoutResultCallback callback,Bundle extras){
        if(cancel.isCanceled()){callback.onLayoutCancelled();return;}attributes=next;
        PrintedPdfDocument doc=new PrintedPdfDocument(activity,next);
        try{
            Rect area=doc.getPageContentRect();
            if(area.width()<=0||area.height()<=0){callback.onLayoutFailed("El servicio de impresión no ofrece un área imprimible válida.");return;}
            pages=Math.max(1,(int)Math.ceil(image.getHeight()*(double)area.width()/image.getWidth()/area.height()));
            callback.onLayoutFinished(new PrintDocumentInfo.Builder(name+".pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(),true);
        }catch(Exception e){callback.onLayoutFailed("No se pudo preparar la nota gráfica.");}finally{doc.close();}
    }
    @Override public void onWrite(PageRange[] ranges,ParcelFileDescriptor destination,CancellationSignal cancel,WriteResultCallback callback){
        PrintedPdfDocument doc=new PrintedPdfDocument(activity,attributes);List<PageRange> written=new ArrayList<>();
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){
            Rect area=doc.getPageContentRect();float scale=area.width()/(float)image.getWidth();
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            for(int p=0;p<pages;p++){
                if(cancel.isCanceled()){callback.onWriteCancelled();return;}
                boolean included=false;for(PageRange r:ranges)if(p>=r.getStart()&&p<=r.getEnd())included=true;if(!included)continue;
                PdfDocument.Page page=doc.startPage(p);Canvas c=page.getCanvas();
                c.save();c.clipRect(0,0,area.width(),area.height());c.translate(0,-p*area.height());c.scale(scale,scale);c.drawBitmap(image,0,0,paint);c.restore();doc.finishPage(page);written.add(new PageRange(p,p));
            }
            if(cancel.isCanceled()){callback.onWriteCancelled();return;}
            doc.writeTo(out);callback.onWriteFinished(written.toArray(new PageRange[0]));
        }catch(Exception e){callback.onWriteFailed("No se pudo imprimir o guardar el PDF.");}finally{doc.close();}
    }
}
