package com.abcell.movil;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.graphics.pdf.PdfDocument;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Uses Android's print framework (including Save as PDF), not Windows printer drivers. */
public final class TicketPrinter extends PrintDocumentAdapter {
    private final Activity activity;
    private final String text, name;
    private PrintAttributes attributes;
    private final List<String> lines = new ArrayList<>();
    private int linesPerPage, pageCount;
    private static final float LINE_HEIGHT = 17;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TicketPrinter(Activity activity, String text, String name) {
        this.activity=activity; this.text=text; this.name=name;
        paint.setColor(Color.BLACK); paint.setTextSize(11);
    }
    public static void print(Activity activity, String text, String name) {
        PrintManager manager=(PrintManager)activity.getSystemService(Activity.PRINT_SERVICE);
        manager.print(name,new TicketPrinter(activity,text,name),new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5).setMinMargins(new PrintAttributes.Margins(300,300,300,300)).build());
    }
    @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                   CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
        if(cancel.isCanceled()){callback.onLayoutCancelled();return;}
        attributes=newAttributes; lines.clear();
        PrintedPdfDocument document=new PrintedPdfDocument(activity,attributes);
        try {
            float width=document.getPageContentRect().width();
            linesPerPage=Math.max(1,(int)(document.getPageContentRect().height()/LINE_HEIGHT)-1);
            for(String paragraph:text.split("\n",-1)) {
                if(paragraph.isEmpty()){lines.add("");continue;}
                String remaining=paragraph;
                while(!remaining.isEmpty()) {
                    int n=paint.breakText(remaining,true,width,null);
                    if(n<=0)n=1;
                    if(n<remaining.length()) {int space=remaining.lastIndexOf(' ',n);if(space>0)n=space;}
                    lines.add(remaining.substring(0,n));remaining=remaining.substring(n).trim();
                }
            }
            pageCount=Math.max(1,(lines.size()+linesPerPage-1)/linesPerPage);
            callback.onLayoutFinished(new PrintDocumentInfo.Builder(name+".pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pageCount).build(),true);
        } catch(Exception e){callback.onLayoutFailed("No se pudo preparar el ticket.");}
        finally { document.close(); }
    }
    @Override public void onWrite(PageRange[] ranges, ParcelFileDescriptor destination,
                                  CancellationSignal cancel, WriteResultCallback callback) {
        List<PageRange> written=new ArrayList<>();
        PrintedPdfDocument doc=new PrintedPdfDocument(activity,attributes);
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())) {
            for(int p=0;p<pageCount;p++) {
                if(cancel.isCanceled()){callback.onWriteCancelled();return;}
                boolean selected=false;for(PageRange r:ranges)if(p>=r.getStart()&&p<=r.getEnd())selected=true;
                if(!selected)continue;
                PdfDocument.Page page=doc.startPage(p);Canvas canvas=page.getCanvas();
                int start=p*linesPerPage,end=Math.min(lines.size(),start+linesPerPage);
                for(int i=start;i<end;i++)canvas.drawText(lines.get(i),0,(i-start+1)*LINE_HEIGHT,paint);
                doc.finishPage(page);written.add(new PageRange(p,p));
            }
            doc.writeTo(out);callback.onWriteFinished(written.toArray(new PageRange[0]));
        } catch(Exception e){callback.onWriteFailed("No se pudo guardar o imprimir el ticket.");}
        finally { doc.close(); }
    }
}
