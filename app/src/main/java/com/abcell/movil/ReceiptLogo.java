package com.abcell.movil;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.Base64;
import java.io.*;

/** Small, portable PNG stored with mobile_settings, so existing JSON backups include the workshop logo. */
public final class ReceiptLogo {
    private ReceiptLogo() {}
    public static String importImage(Context context, Uri uri) throws IOException {
        try(InputStream in=context.getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null) throw new IllegalArgumentException("No se pudo abrir la imagen.");
            byte[] buffer=new byte[8192]; int n;
            while((n=in.read(buffer))!=-1) {
                if(out.size()+n>4*1024*1024) throw new IllegalArgumentException("Elige un logo PNG/JPG/WebP de hasta 4 MB.");
                out.write(buffer,0,n);
            }
            Bitmap image=decodeBytes(out.toByteArray());
            if(image==null) throw new IllegalArgumentException("La imagen no es válida. El logo anterior se conserva.");
            try(ByteArrayOutputStream png=new ByteArrayOutputStream()) {
                image.compress(Bitmap.CompressFormat.PNG,100,png);
                return Base64.encodeToString(png.toByteArray(),Base64.NO_WRAP);
            } finally { image.recycle(); }
        }
    }
    public static Bitmap decode(String base64) {
        if(base64==null || base64.isEmpty() || base64.length()>1024*1024) return null;
        try { return decodeBytes(Base64.decode(base64,Base64.DEFAULT)); }
        catch(IllegalArgumentException e) { return null; }
    }
    private static Bitmap decodeBytes(byte[] bytes) {
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<=0 || o.outHeight<=0 || o.outWidth>32768 || o.outHeight>32768) return null;
        o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>512) o.inSampleSize*=2;
        o.inJustDecodeBounds=false;
        Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(decoded==null) return null;
        float scale=Math.min(1f,256f/Math.max(decoded.getWidth(),decoded.getHeight()));
        Bitmap image=Bitmap.createBitmap(Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);c.drawColor(Color.WHITE);
        c.drawBitmap(decoded,null,new Rect(0,0,image.getWidth(),image.getHeight()),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        decoded.recycle();return image;
    }
}
