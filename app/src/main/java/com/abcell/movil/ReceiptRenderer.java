package com.abcell.movil;

import android.graphics.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Port of ABCell Pro integrations/ticket_image.py: same sections, palette and hierarchy.
 * Layout is measured before bitmap allocation, with wrapping and no fixed-height cropping.
 * Thermal output reflows the same design at the actual print-head width, using black ink only.
 */
public final class ReceiptRenderer {
    public static final int DIGITAL_WIDTH=640;
    private static final int BLUE=Color.rgb(2,132,199), DARK=Color.rgb(15,23,42), GRAY=Color.rgb(100,116,139);
    public static final class TextRun {
        public final String text;
        public final float x, top, right, bottom;
        TextRun(String text,float x,float top,float right,float bottom){this.text=text;this.x=x;this.top=top;this.right=right;this.bottom=bottom;}
    }
    public static final class Result {
        public final Bitmap bitmap;
        public final List<TextRun> text;
        Result(Bitmap bitmap,List<TextRun> text){this.bitmap=bitmap;this.text=Collections.unmodifiableList(text);}
    }
    private interface Command { void draw(Canvas c); }
    private final List<Command> commands=new ArrayList<>();
    private final List<TextRun> runs=new ArrayList<>();
    private final int width;
    private final boolean thermal;
    private final float margin, useful, unit;
    private float y;
    private ReceiptRenderer(int width,boolean thermal) {
        if(width<384 || width>640) throw new IllegalArgumentException("Ancho de nota no admitido");
        this.width=width;this.thermal=thermal;
        margin=Math.round(width*36f/640);useful=width-2*margin;
        unit=thermal && width<512?.9f:1f; y=margin;
    }
    public static Result render(ReceiptData data,int width,boolean thermal) {
        ReceiptRenderer r=new ReceiptRenderer(width,thermal);
        Bitmap logo=ReceiptLogo.decode(data.logo);
        try {
            r.compose(data,logo);
            int height=(int)Math.ceil(r.y+r.margin);
            if(height>16000) throw new IllegalArgumentException("La nota es demasiado extensa. Acorta la descripción o las políticas antes de imprimir.");
            Bitmap image=Bitmap.createBitmap(width,Math.max(1,height),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);
            for(Command command:r.commands)command.draw(canvas);
            return new Result(image,r.runs);
        } finally { if(logo!=null)logo.recycle(); }
    }
    private Paint font(float size,boolean bold,int color) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size*unit);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        p.setColor(thermal?Color.BLACK:color);return p;
    }
    private List<String> wrap(String value,Paint p,float maxWidth) {
        List<String> out=new ArrayList<>();
        for(String paragraph:value.replace("\r","").split("\n",-1)) {
            String rest=paragraph.trim();if(rest.isEmpty()){out.add("");continue;}
            while(!rest.isEmpty()) {
                int n=p.breakText(rest,true,maxWidth,null);
                if(n<=0)throw new IllegalArgumentException("No se pudo ajustar el texto de la nota.");
                if(n<rest.length()) {
                    if(Character.isHighSurrogate(rest.charAt(n-1)))n--;
                    int space=rest.lastIndexOf(' ',n);if(space>0)n=space;
                }
                if(n<=0)throw new IllegalArgumentException("Texto no compatible con la nota");
                out.add(rest.substring(0,n));rest=rest.substring(n).trim();
                if(out.size()>1200)throw new IllegalArgumentException("El texto de la nota es demasiado extenso.");
            }
        }
        return out;
    }
    private void line(String s,Paint p,float x,float top) {
        Paint.FontMetrics fm=p.getFontMetrics();float h=fm.descent-fm.ascent;
        runs.add(new TextRun(s,x,top,x+p.measureText(s),top+h));
        commands.add(c->c.drawText(s,x,top-fm.ascent,p));
    }
    private void center(String value,float size,boolean bold,int color,float gap) {
        Paint p=font(size,bold,color);Paint.FontMetrics fm=p.getFontMetrics();
        for(String s:wrap(value,p,useful)){line(s,p,margin+(useful-p.measureText(s))/2,y);y+=fm.descent-fm.ascent+gap*unit;}
    }
    private void separator() {
        y+=14*unit;final float at=y;Paint p=new Paint();p.setColor(thermal?Color.BLACK:Color.rgb(226,232,240));p.setStrokeWidth(thermal?1:2);
        commands.add(c->c.drawLine(margin,at,width-margin,at,p));y+=14*unit;
    }
    private void field(String label,String value) {
        if(value==null || value.trim().isEmpty())return;
        Paint p=font(17,true,GRAY);line(label,p,margin,y);Paint.FontMetrics fm=p.getFontMetrics();y+=fm.descent-fm.ascent+2*unit;
        Paint v=font(20,true,DARK);fm=v.getFontMetrics();
        for(String s:wrap(value,v,useful)){line(s,v,margin,y);y+=fm.descent-fm.ascent+4*unit;}
        y+=6*unit;
    }
    private void amounts(JSONObject o) {
        float boxTop=y;int commandIndex=commands.size();y+=16*unit;
        for(String key:new String[]{"costo_total","anticipo","saldo"}) {
            String title=key.equals("saldo")?"SALDO PENDIENTE":key.equals("anticipo")?"ANTICIPO":"TOTAL";
            String value=Money.format(WorkshopDb.amount(o,key));Paint label=font(18,true,GRAY),valuePaint=font(key.equals("saldo")?28:22,true,DARK);
            float left=margin+20*unit,right=width-margin-20*unit;
            float lh=label.getFontMetrics().descent-label.getFontMetrics().ascent,vh=valuePaint.getFontMetrics().descent-valuePaint.getFontMetrics().ascent;
            if(label.measureText(title)+valuePaint.measureText(value)+12*unit>right-left) {
                line(title,label,left,y);y+=lh+4*unit;
                for(String s:wrap(value,valuePaint,right-left)){line(s,valuePaint,right-valuePaint.measureText(s),y);y+=vh+3*unit;}
                y+=10*unit;
            } else {line(title,label,left,y+Math.max(0,vh-lh)/2);line(value,valuePaint,right-valuePaint.measureText(value),y);y+=Math.max(46*unit,vh+12*unit);}
        }
        y+=8*unit;final float bottom=y;
        commands.add(commandIndex,c->{Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(thermal?Color.WHITE:Color.rgb(224,242,254));RectF box=new RectF(margin,boxTop,width-margin,bottom);c.drawRoundRect(box,14*unit,14*unit,p);if(thermal){p.setColor(Color.BLACK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(box,14*unit,14*unit,p);}});
    }
    private static String date(String input) {
        if(input.isEmpty())return "";
        try {
            String pattern=input.length()>=19?"yyyy-MM-dd HH:mm:ss":input.length()>=16?"yyyy-MM-dd HH:mm":"yyyy-MM-dd";
            Date date=new SimpleDateFormat(pattern,Locale.ROOT).parse(input);
            if(date!=null)return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ROOT).format(date);
        } catch(Exception ignored) { /* Preserve unknown legacy dates, not an invented timestamp. */ }
        return input;
    }
    private void compose(ReceiptData d,Bitmap logo) {
        JSONObject o=d.order;
        if(logo!=null) {
            float max=110*unit,scale=Math.min(max/logo.getWidth(),max/logo.getHeight());
            float w=logo.getWidth()*scale,h=logo.getHeight()*scale;RectF dest=new RectF((width-w)/2,y,(width+w)/2,y+h);
            Paint logoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            if(thermal){ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);logoPaint.setColorFilter(new ColorMatrixColorFilter(gray));}
            commands.add(c->c.drawBitmap(logo,null,dest,logoPaint));y+=max+10*unit;
        }
        center(d.name.toUpperCase(new Locale("es")),30,true,DARK,4);
        center("NOTA DE TALLER",20,true,BLUE,4);
        if(d.demo)center("PRUEBA DE IMPRESIÓN · SIN VALIDEZ",17,true,DARK,4);
        separator();
        center(String.format(Locale.ROOT,"FOLIO #%04d",o.optLong("folio")),38,true,BLUE,4);
        String date=date(o.optString("fecha_ingreso",o.optString("fecha")));
        if(!date.isEmpty())center("Fecha: "+date,18,false,GRAY,2);
        center("Estatus: "+o.optString("estatus","Recibido").toUpperCase(new Locale("es")),20,true,DARK,2);separator();
        field("CLIENTE",o.optString("cliente_nombre"));field("TELÉFONO",o.optString("cliente_tel"));separator();
        field("EQUIPO",(o.optString("marca")+" "+o.optString("modelo")).trim());
        if(!o.optString("color").equalsIgnoreCase("N/A"))field("COLOR",o.optString("color"));
        field("IMEI / SERIE",o.optString("imei"));
        if(d.includeAccess){field("PIN / CONTRASEÑA",o.optString("pin_pass"));if(!o.optString("patron").equals("Sin patrón"))field("PATRÓN",o.optString("patron"));}
        field("UBICACIÓN",o.optString("ubicacion"));field("ESTADO FÍSICO",o.optString("estado_fisico"));
        field("FALLA / SERVICIO",o.optString("falla"));separator();amounts(o);separator();
        if(!d.address.isEmpty())center(d.address,18,false,GRAY,4);
        if(!d.phone.isEmpty())center("Tel / WhatsApp: "+d.phone,18,false,GRAY,4);
        if(!d.warranty.isEmpty()){y+=6*unit;center("GARANTÍA Y CONDICIONES",18,true,DARK,4);center(d.warranty,16,false,GRAY,4);}
        if(!d.thanks.isEmpty()){y+=6*unit;center(d.thanks,20,true,BLUE,4);}
        y+=10*unit;center(String.format(Locale.ROOT,"Folio #%04d · %s",o.optLong("folio"),d.name),15,false,GRAY,2);
    }
}
