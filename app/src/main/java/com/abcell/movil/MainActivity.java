package com.abcell.movil;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, offline-first workshop UI. No WebView, desktop runtime, ADB or network permission. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff0c111b, CARD = 0xff151e2c, LINE = 0xff2a3849;
    private static final int TEXT = 0xffedf2f7, MUTED = 0xff9baabd, LIME = 0xffc5f467, RED = 0xffff9595;
    private static final int EXPORT = 41, IMPORT = 42, LOGO = 43;
    private WorkshopDb db;
    private LinearLayout root, body, content;
    private String screen = "home", search = "", filter = "Todos", itemTable = "inventario";
    private long selectedId;
    private final Map<String, View> fields = new LinkedHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean busy;
    private ImageView logoPreview;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new WorkshopDb(this);
        if (state != null) {
            screen = state.getString("screen", "home"); selectedId = state.getLong("id");
            itemTable = state.getString("table", "inventario"); search = state.getString("search", ""); filter = state.getString("filter", "Todos");
        }
        try { render(); } catch (Exception e) { screen = "home"; render(); error(e); }
        if (state != null && state.containsKey("draft")) {
            Bundle draft = state.getBundle("draft");
            if (draft != null) for (String key : fields.keySet()) if (draft.containsKey(key)) setField(key, draft.getString(key));
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("screen", screen); out.putLong("id", selectedId); out.putString("table", itemTable);
        out.putString("search", search); out.putString("filter", filter);
        Bundle draft = new Bundle(); for (String key : fields.keySet()) draft.putString(key, value(key)); out.putBundle("draft", draft);
    }
    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density * v + .5f); }
    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(16));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(5), 0, dp(5)); t.setTextIsSelectable(false); return t;
    }
    private void label(LinearLayout parent, String title) {
        TextView t = text(title, 13, MUTED); t.setTypeface(null, Typeface.BOLD); t.setPadding(0, dp(18), 0, dp(8)); parent.addView(t);
    }
    private Button button(LinearLayout parent, String title, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? BG : TEXT); b.setTypeface(null, Typeface.BOLD);
        b.setBackground(shape(primary ? LIME : CARD, primary ? 0 : LINE)); b.setMinHeight(dp(52));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(10); parent.addView(b, p);
        b.setOnClickListener(v -> { if (!busy) safe(action); }); return b;
    }
    private void safe(Runnable action) { try { action.run(); } catch (Exception e) { error(e); } }
    private void error(Exception e) {
        String msg = e instanceof IllegalArgumentException ? e.getMessage() : "No se pudo completar la operación. Los cambios no confirmados no se guardaron.";
        new AlertDialog.Builder(this).setTitle("Revisa los datos").setMessage(msg).setPositiveButton("Entendido", null).show();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private boolean form() { return screen.equals("orderForm") || screen.equals("itemForm") || screen.equals("settings"); }
    private void navigate(String next) {
        if (form()) {
            new AlertDialog.Builder(this).setTitle("¿Salir del formulario?").setMessage("Los cambios sin guardar se perderán.")
                    .setNegativeButton("Seguir editando", null).setPositiveButton("Salir", (d,w) -> go(next)).show();
        } else go(next);
    }
    private void go(String next) { screen = next; search = ""; filter = "Todos"; render(); }
    @Override public void onBackPressed() {
        if (busy) return;
        if (screen.equals("home")) { super.onBackPressed(); return; }
        if (screen.equals("orderForm") && selectedId > 0) navigate("detail");
        else if (screen.equals("detail") || screen.equals("orderForm")) navigate("orders");
        else if (screen.equals("itemForm")) navigate(itemTable);
        else navigate("home");
    }
    private void render() {
        fields.clear(); content = null;
        root = column(); root.setBackgroundColor(BG); root.setFitsSystemWindows(true);
        // Android 15 enforces edge-to-edge. Apply actual system/IME insets to all controls.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20), dp(10), dp(20), dp(10));
        ImageView logo = new ImageView(this); logo.setImageResource(com.abcell.movil.R.drawable.brand_emblem); logo.setContentDescription("Emblema ABCell");
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView brand = text("  ABCell Móvil", 21, TEXT); brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = text("● LOCAL", 10, LIME); header.addView(local); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(20), dp(4), dp(20), dp(28)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        switch (screen) {
            case "orders": orders(); break;
            case "detail": detail(); break;
            case "orderForm": orderForm(); break;
            case "inventario": case "servicios_catalogo": case "ubicaciones": items(); break;
            case "itemForm": itemForm(); break;
            case "reports": reports(); break;
            case "settings": settings(); break;
            case "more": more(); break;
            default: home();
        }
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(8), dp(8), dp(8), dp(10)); nav.setBackgroundColor(CARD);
        String[] destinations = {"home", "orders", "inventario", "more"};
        String[] titles = {"Inicio", "Órdenes", "Inventario", "Más"};
        for (int i=0; i<destinations.length; i++) {
            final String target = destinations[i];
            boolean active = screen.equals(target) || (target.equals("orders") && (screen.equals("detail") || screen.equals("orderForm"))) || (target.equals("more") && Arrays.asList("settings","reports","ubicaciones","servicios_catalogo").contains(screen));
            Button b = new Button(this); b.setText(titles[i]); b.setAllCaps(false); b.setTextSize(12); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(2),dp(8),dp(2),dp(8)); b.setMinHeight(dp(48));
            b.setTextColor(active ? BG : MUTED); b.setBackground(shape(active ? LIME : CARD,0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(2),0,dp(2),0); nav.addView(b,p);
            b.setOnClickListener(v -> { if (!busy && !screen.equals(target)) navigate(target); });
        }
        root.addView(nav);
    }
    private void title(String eyebrow, String title, String subtitle) {
        TextView e = text(eyebrow.toUpperCase(new Locale("es")), 11, LIME); e.setLetterSpacing(.13f); body.addView(e);
        TextView t = text(title, 30, TEXT); t.setTypeface(null, Typeface.BOLD); body.addView(t);
        if (!subtitle.isEmpty()) body.addView(text(subtitle,14,MUTED));
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(16),dp(14),dp(16),dp(14)); c.setBackground(shape(CARD,LINE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(12); parent.addView(c,p); return c;
    }
    private void metric(String name, String value, String hint) {
        LinearLayout c=card(body); c.addView(text(name,13,MUTED)); TextView v=text(value,28,LIME);v.setTypeface(null,Typeface.BOLD);c.addView(v);
        if (!hint.isEmpty()) c.addView(text(hint,12,MUTED));
    }
    private JSONObject scalar(String sql) { return db.rows(sql).get(0); }
    private String money(JSONObject r,String key) { return Money.format(WorkshopDb.amount(r,key)); }
    private void home() {
        title("Tu taller, en movimiento", db.setting("nombre","Mi taller"), "Gestiona tus reparaciones desde aquí. Sin conexión y sin complicaciones.");
        button(body,"+  Recibir equipo",true,()->{selectedId=0;go("orderForm");});
        JSONObject r=scalar("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN estatus NOT IN ('Entregado','Cancelado') THEN 1 ELSE 0 END),0) AS abiertas, COALESCE(SUM(CASE WHEN estatus='Listo para entregar' THEN 1 ELSE 0 END),0) AS listas, COALESCE(SUM(CASE WHEN estatus!='Cancelado' THEN saldo ELSE 0 END),0) AS saldo FROM ordenes");
        metric("Órdenes en proceso",r.optString("abiertas"),r.optString("listas")+" listas para entregar · "+r.optString("total")+" registradas");
        metric("Por cobrar",money(r,"saldo"),"Saldo de órdenes no canceladas");
        JSONObject stock=scalar("SELECT COUNT(*) AS total FROM inventario WHERE stock<=stock_minimo");
        if (stock.optInt("total")>0) button(body,"Inventario: "+stock.optInt("total")+" productos por reponer",false,()->go("inventario"));
        label(body,"ACTIVIDAD RECIENTE");
        List<JSONObject> recent=db.rows("SELECT * FROM ordenes ORDER BY folio DESC LIMIT 5");
        if(recent.isEmpty()) empty(body,"Tu primera reparación empieza aquí","Recibe un equipo para crear su orden, registrar un anticipo y seguir su reparación.");
        for(JSONObject o:recent) orderCard(body,o);
    }
    private void empty(LinearLayout parent,String title,String detail) { LinearLayout c=card(parent);c.addView(text(title,18,TEXT));c.addView(text(detail,14,MUTED)); }
    private EditText searchBox(String hint,Runnable changed) {
        EditText input = new EditText(this); input.setSingleLine(); input.setTextSize(15);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setHint(hint);
        input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setBackground(shape(CARD,LINE));input.setText(search);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);body.addView(input,p);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){search=s.toString();safe(changed);}public void afterTextChanged(Editable e){}});return input;
    }
    private Spinner spinner(LinearLayout parent,String[] options,String selected) {
        Spinner s=new Spinner(this);s.setMinimumHeight(dp(52));s.setPadding(dp(8),0,dp(8),0);s.setBackground(shape(CARD,LINE));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);s.setAdapter(adapter);
        int index=Arrays.asList(options).indexOf(selected);if(index>=0)s.setSelection(index);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);parent.addView(s,p);return s;
    }
    private void orders() {
        title("Seguimiento","Órdenes","Clientes, equipos y reparaciones en un solo lugar.");
        button(body,"+  Nueva orden",true,()->{selectedId=0;go("orderForm");});
        searchBox("Buscar folio, cliente, teléfono o equipo…",this::loadOrders);
        List<String> options=new ArrayList<>();options.add("Todos");options.addAll(Arrays.asList(WorkshopDb.STATES));
        Spinner status=spinner(body,options.toArray(new String[0]),filter);
        content=column();body.addView(content);loadOrders();
        status.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){filter=options.get(pos);loadOrders();}});
    }
    private void loadOrders() {
        if(content==null)return;content.removeAllViews();String q="%"+search.trim()+"%";
        List<JSONObject> rows=db.rows("SELECT * FROM ordenes WHERE (?='Todos' OR estatus=?) AND (CAST(folio AS TEXT) LIKE ? OR cliente_nombre LIKE ? OR cliente_tel LIKE ? OR marca LIKE ? OR modelo LIKE ? OR falla LIKE ? OR ubicacion LIKE ?) ORDER BY folio DESC LIMIT 201",filter,filter,q,q,q,q,q,q,q);
        if(rows.isEmpty())empty(content,"Sin órdenes para mostrar","Crea una orden o cambia los filtros de búsqueda.");
        for(int i=0;i<Math.min(200,rows.size());i++)orderCard(content,rows.get(i));
        if(rows.size()>200)content.addView(text("Mostrando 200 resultados. Refina la búsqueda para encontrar otros.",13,MUTED));
    }
    private void orderCard(LinearLayout parent,JSONObject o) {
        LinearLayout c=card(parent);c.addView(text(String.format(Locale.ROOT,"#%04d",o.optLong("folio"))+"   ·   "+o.optString("estatus"),12,LIME));
        TextView client=text(o.optString("cliente_nombre"),18,TEXT);client.setTypeface(null,Typeface.BOLD);c.addView(client);
        c.addView(text(o.optString("marca")+" "+o.optString("modelo"),14,MUTED));
        c.addView(text("Saldo "+money(o,"saldo")+"    ·    "+o.optString("ubicacion","Sin asignar"),13,MUTED));
        button(c,"Abrir orden →",false,()->{selectedId=o.optLong("folio");go("detail");});
    }
    private EditText field(LinearLayout parent,String key,String label,String initial,int type) {
        label(parent,label);EditText input=new EditText(this);input.setTextSize(16);input.setTextColor(TEXT);input.setHintTextColor(MUTED);
        input.setInputType(type);input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setBackground(shape(BG,LINE));input.setMinHeight(dp(52));input.setText(initial);
        if((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0){input.setMinLines(3);input.setGravity(Gravity.TOP);}
        else input.setSingleLine();
        if(key.equals("pin_pass"))input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(input,new LinearLayout.LayoutParams(-1,-2));fields.put(key,input);return input;
    }
    private void field(String key,String label,String initial) { field(body,key,label,initial,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); }
    private void multiline(String key,String label,String initial) { field(body,key,label,initial,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); }
    private void amountField(String key,String label,JSONObject row) { field(body,key,label,Money.edit(WorkshopDb.amount(row,key)),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); }
    private void choice(String key,String label,String[] options,String initial) { label(body,label);fields.put(key,spinner(body,options,initial)); }
    private String value(String key) { View v=fields.get(key);return v instanceof EditText?((EditText)v).getText().toString().trim():v instanceof Spinner?String.valueOf(((Spinner)v).getSelectedItem()):""; }
    private void setField(String key,String value) {
        View v=fields.get(key);if(v instanceof EditText)((EditText)v).setText(value);
        else if(v instanceof Spinner){Spinner s=(Spinner)v;for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(value))s.setSelection(i);}
    }
    private JSONObject fieldData() {
        JSONObject data=new JSONObject();for(String key:fields.keySet())put(data,key,value(key));return data;
    }
    private static void put(JSONObject o,String key,Object value) { try{o.put(key,value);}catch(Exception e){throw new IllegalArgumentException("Dato no válido",e);} }
    private void amounts(JSONObject data,String... keys) { for(String key:keys)put(data,key,Money.cents(value(key))/100.0); }
    private String[] locations(String current) {
        List<String> options=new ArrayList<>();options.add("Sin asignar");
        for(JSONObject r:db.rows("SELECT nombre FROM ubicaciones WHERE activo=1 ORDER BY nombre"))options.add(r.optString("nombre"));
        if(current!=null&&!current.isEmpty()&&!options.contains(current))options.add(current);return options.toArray(new String[0]);
    }
    private void orderForm() {
        JSONObject o=selectedId==0?new JSONObject():db.order(selectedId);
        title("Recepción",selectedId==0?"Nueva orden":"Editar #"+selectedId,"Los campos con * son obligatorios. Importes en MXN.");
        label(body,"01  /  CLIENTE");
        field("cliente_nombre","Nombre del cliente *",o.optString("cliente_nombre"));
        field(body,"cliente_tel","Teléfono / WhatsApp *",o.optString("cliente_tel"),InputType.TYPE_CLASS_PHONE);
        label(body,"02  /  EQUIPO");
        choice("categoria","Categoría",WorkshopDb.CATEGORIES,o.optString("categoria","Celulares"));
        field("marca","Marca *",o.optString("marca"));field("modelo","Modelo *",o.optString("modelo"));field("color","Color",o.optString("color"));
        field("imei","IMEI / número de serie",o.optString("imei"));
        field(body,"pin_pass","PIN / contraseña (opcional)",o.optString("pin_pass"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field("patron","Patrón (opcional)",o.optString("patron","Sin patrón"));
        multiline("estado_fisico","Estado físico y accesorios",o.optString("estado_fisico"));multiline("falla","Falla reportada *",o.optString("falla"));
        label(body,"03  /  SERVICIO Y COSTOS");
        List<JSONObject> services=db.rows("SELECT * FROM servicios_catalogo WHERE activo=1 ORDER BY nombre");
        if(!services.isEmpty())button(body,"Elegir servicio del catálogo",false,()->{
            String[] options=new String[services.size()];for(int i=0;i<options.length;i++)options[i]=services.get(i).optString("nombre")+" · "+money(services.get(i),"precio");
            new AlertDialog.Builder(this).setTitle("Aplicar servicio y precio").setItems(options,(d,w)->{
                setField("servicio_nombre",services.get(w).optString("nombre"));setField("costo_total",Money.edit(WorkshopDb.amount(services.get(w),"precio")));
            }).setNegativeButton("Cancelar",null).show();
        });
        field("servicio_nombre","Servicio / reparación",o.optString("servicio_nombre"));
        choice("ubicacion","Ubicación",locations(o.optString("ubicacion")),o.optString("ubicacion","Sin asignar"));
        amountField("costo_repuesto","Costo de refacciones",o);amountField("costo_total","Total al cliente",o);
        if(selectedId==0){amountField("anticipo","Anticipo",o);choice("metodo_pago","Método del anticipo",WorkshopDb.METHODS,WorkshopDb.METHODS[0]);}
        else body.addView(text("Abonado: "+money(o,"anticipo")+". Registra nuevos abonos desde el detalle de la orden.",14,MUTED));
        multiline("notas_internas","Notas internas (no se comparten)",o.optString("notas_internas"));
        button(body,"Guardar orden",true,()->{
            JSONObject data=fieldData();amounts(data,"costo_repuesto","costo_total");if(selectedId==0)amounts(data,"anticipo");
            selectedId=db.saveOrder(data,selectedId);toast("Orden guardada");go("detail");
        });
        button(body,"Cancelar",false,()->navigate(selectedId==0?"orders":"detail"));
    }
    private void detail() {
        JSONObject o=db.order(selectedId);
        title("Orden #"+String.format(Locale.ROOT,"%04d",selectedId),o.optString("cliente_nombre"),o.optString("marca")+" "+o.optString("modelo"));
        LinearLayout c=card(body);c.addView(text(o.optString("estatus"),17,LIME));c.addView(text("Ingreso: "+o.optString("fecha_ingreso"),12,MUTED));
        c.addView(text("Teléfono: "+o.optString("cliente_tel"),14,TEXT));
        c.addView(text("Falla: "+o.optString("falla"),16,TEXT));
        if(!o.optString("servicio_nombre").isEmpty())c.addView(text("Servicio: "+o.optString("servicio_nombre"),14,MUTED));
        for(String key:new String[]{"categoria","color","imei","estado_fisico","ubicacion"})if(!o.optString(key).isEmpty())c.addView(text(detailLabel(key)+": "+o.optString(key),14,MUTED));
        if(!o.optString("notas_internas").isEmpty())c.addView(text("Notas internas: "+o.optString("notas_internas"),14,MUTED));
        metric("Saldo pendiente",money(o,"saldo"),"Total "+money(o,"costo_total")+"  ·  Abonado "+money(o,"anticipo"));
        if(WorkshopDb.amount(o,"saldo")>0&&!o.optString("estatus").equals("Cancelado"))button(body,"+  Registrar abono",true,()->payment(o));
        button(body,"Cambiar estado / ubicación",false,()->statusDialog(o));
        button(body,"Editar datos de la orden",false,()->go("orderForm"));
        button(body,"Ver / compartir nota digital",true,()->startActivity(new Intent(this,ReceiptActivity.class).putExtra("folio",selectedId)));
        button(body,"Imprimir nota por Bluetooth",false,()->startActivity(new Intent(this,BluetoothPrinterActivity.class).putExtra("folio",selectedId)));
        button(body,"Enviar estado por WhatsApp",false,()->whatsapp(o));
        label(body,"HISTORIAL DE ABONOS");
        List<JSONObject> payments=db.rows("SELECT * FROM pagos_orden WHERE folio=? ORDER BY id DESC",""+selectedId);
        if(payments.isEmpty())body.addView(text("Sin abonos registrados.",14,MUTED));
        for(JSONObject p:payments){LinearLayout row=card(body);row.addView(text(money(p,"monto")+" · "+p.optString("metodo_pago"),16,TEXT));row.addView(text(p.optString("fecha"),12,MUTED));}
        Button delete=button(body,"Eliminar orden",false,()->confirm("¿Eliminar orden #"+selectedId+"?","También se eliminará su historial de abonos. Esta acción no se puede deshacer.",()->{db.delete("ordenes",selectedId);go("orders");}));delete.setTextColor(RED);
    }
    private String detailLabel(String key) {
        switch(key){case "categoria":return "Categoría";case "imei":return "IMEI / serie";case "estado_fisico":return "Estado físico";case "ubicacion":return "Ubicación";default:return "Color";}
    }
    private void payment(JSONObject o) {
        LinearLayout box=dialogBox();box.addView(text("Saldo: "+money(o,"saldo"),18,LIME));
        EditText amount=new EditText(this);amount.setHint("Importe MXN");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(amount);
        Spinner method=spinner(box,WorkshopDb.METHODS,WorkshopDb.METHODS[0]);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Registrar abono").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->safe(()->{
            db.pay(selectedId,Money.cents(amount.getText().toString()),String.valueOf(method.getSelectedItem()));dialog.dismiss();render();toast("Abono registrado");
        })));dialog.show();
    }
    private LinearLayout dialogBox(){LinearLayout b=column();b.setPadding(dp(22),dp(10),dp(22),dp(14));return b;}
    private void statusDialog(JSONObject o) {
        LinearLayout box=dialogBox();box.addView(text("Estado de reparación",14,MUTED));Spinner state=spinner(box,WorkshopDb.STATES,o.optString("estatus"));
        box.addView(text("Ubicación",14,MUTED));Spinner location=spinner(box,locations(o.optString("ubicacion")),o.optString("ubicacion"));
        box.addView(text("Entregar no cobra automáticamente. Registra y confirma el pago antes de entregar.",13,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Actualizar orden").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->safe(()->{
            String next=String.valueOf(state.getSelectedItem());
            if(next.equals("Entregado")&&WorkshopDb.amount(o,"saldo")>0){confirm("Entrega con saldo pendiente","Quedarán "+money(o,"saldo")+" por cobrar. ¿Confirmas la entrega sin liquidar?",()->{db.status(selectedId,next,String.valueOf(location.getSelectedItem()),o.optString("notas_internas"));dialog.dismiss();render();});}
            else{db.status(selectedId,next,String.valueOf(location.getSelectedItem()),o.optString("notas_internas"));dialog.dismiss();render();}
        })));dialog.show();
    }
    private void confirm(String title,String message,Runnable action){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->safe(action)).show();}
    private void whatsapp(JSONObject o) {
        String phone=o.optString("cliente_tel").replaceAll("[^0-9]","");if(phone.length()==10)phone="52"+phone;
        if(phone.length()<11||phone.length()>15)throw new IllegalArgumentException("Revisa el teléfono e incluye el código de país (por ejemplo +52).");
        String message="Hola "+o.optString("cliente_nombre")+", te contactamos de "+db.setting("nombre","Mi taller")+". Tu orden #"+o.optLong("folio")+" ("+o.optString("marca")+" "+o.optString("modelo")+") está en estado: "+o.optString("estatus")+". Saldo pendiente: "+money(o,"saldo")+".";
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+phone+"?text="+Uri.encode(message))));}
        catch(ActivityNotFoundException e){toast("Instala WhatsApp o un navegador para abrir este enlace.");}
    }
    private String itemTitle(String table){return table.equals("inventario")?"Inventario":table.equals("ubicaciones")?"Ubicaciones":"Catálogo de servicios";}
    private void items() {
        itemTable=screen;title("Organización",itemTitle(itemTable),itemTable.equals("inventario")?"Refacciones y productos, siempre bajo control.":"Administra los datos de tu taller.");
        button(body,"+  Agregar "+(itemTable.equals("inventario")?"producto":itemTable.equals("ubicaciones")?"ubicación":"servicio"),true,()->{selectedId=0;go("itemForm");});
        searchBox("Buscar por nombre…",this::loadItems);
        if(itemTable.equals("inventario")) {
            Spinner stock=spinner(body,new String[]{"Todos","Stock bajo","Agotado"},filter);
            stock.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){filter=String.valueOf(p.getItemAtPosition(pos));loadItems();}});
        }
        content=column();body.addView(content);loadItems();
    }
    private void loadItems() {
        if(content==null)return;content.removeAllViews();
        String extra=itemTable.equals("inventario")?(filter.equals("Stock bajo")?" AND stock>0 AND stock<=stock_minimo":filter.equals("Agotado")?" AND stock=0":""):"";
        List<JSONObject> rows=db.rows("SELECT * FROM "+itemTable+" WHERE nombre LIKE ?"+extra+" ORDER BY nombre COLLATE NOCASE LIMIT 201","%"+search.trim()+"%");
        if(rows.isEmpty())empty(content,"Sin registros","Agrega un registro o cambia la búsqueda.");
        for(int i=0;i<Math.min(200,rows.size());i++){
            JSONObject r=rows.get(i);LinearLayout c=card(content);c.addView(text(r.optString("nombre"),18,TEXT));
            if(itemTable.equals("inventario")){
                c.addView(text(r.optInt("stock")+" unidades · Mínimo "+r.optInt("stock_minimo"),14,r.optInt("stock")<=r.optInt("stock_minimo")?LIME:MUTED));
                c.addView(text("Venta "+money(r,"precio_venta")+" · "+r.optString("marca_modelo"),14,MUTED));
                c.addView(text(r.optString("codigo")+" · "+r.optString("ubicacion"),13,MUTED));
            }else if(itemTable.equals("servicios_catalogo")){c.addView(text(money(r,"precio")+" · "+r.optString("categoria"),14,LIME));c.addView(text(r.optString("marca")+" "+r.optString("modelo"),14,MUTED));}
            else c.addView(text(r.optString("tipo")+" · "+r.optString("descripcion"),14,MUTED));
            button(c,"Editar",false,()->{selectedId=r.optLong("id");go("itemForm");});
        }
        if(rows.size()>200)content.addView(text("Mostrando 200 registros. Refina tu búsqueda.",13,MUTED));
    }
    private int integer(String key){try{int n=Integer.parseInt(value(key));if(n<0||n>10000000)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException("Las existencias deben ser números enteros entre 0 y 10,000,000.");}}
    private void itemForm() {
        JSONObject r=new JSONObject();if(selectedId>0){List<JSONObject> rows=db.rows("SELECT * FROM "+itemTable+" WHERE id=?",""+selectedId);if(rows.isEmpty())throw new IllegalArgumentException("El registro no existe");r=rows.get(0);}
        title(itemTitle(itemTable),selectedId==0?"Nuevo registro":"Editar registro","Los campos con * son obligatorios. Importes en MXN.");
        field("nombre","Nombre *",r.optString("nombre"));
        if(itemTable.equals("inventario")){
            field("codigo","Código / SKU",r.optString("codigo"));field("categoria","Categoría",r.optString("categoria","Refacciones"));field("marca_modelo","Marca / modelo compatible",r.optString("marca_modelo"));
            field(body,"stock","Existencias",r.optString("stock","0"),InputType.TYPE_CLASS_NUMBER);field(body,"stock_minimo","Stock mínimo",r.optString("stock_minimo","2"),InputType.TYPE_CLASS_NUMBER);
            amountField("costo_compra","Costo de compra",r);amountField("precio_venta","Precio de venta",r);
            choice("ubicacion","Ubicación",locations(r.optString("ubicacion")),r.optString("ubicacion","Sin asignar"));multiline("notas","Notas",r.optString("notas"));
        }else if(itemTable.equals("servicios_catalogo")){
            choice("categoria","Categoría",WorkshopDb.CATEGORIES,r.optString("categoria","Celulares"));field("marca","Marca",r.optString("marca"));field("modelo","Modelo",r.optString("modelo"));amountField("precio","Precio",r);
        }else{field("tipo","Tipo",r.optString("tipo","General"));multiline("descripcion","Descripción",r.optString("descripcion"));}
        button(body,"Guardar",true,()->{
            JSONObject data=fieldData();
            if(itemTable.equals("inventario")){put(data,"stock",integer("stock"));put(data,"stock_minimo",integer("stock_minimo"));amounts(data,"costo_compra","precio_venta");}
            else if(itemTable.equals("servicios_catalogo")){amounts(data,"precio");put(data,"activo",1);}else put(data,"activo",1);
            db.saveItem(itemTable,data,selectedId);toast("Registro guardado");go(itemTable);
        });
        if(selectedId>0){Button b=button(body,"Eliminar registro",false,()->confirm("¿Eliminar registro?","Esta acción no se puede deshacer.",()->{db.delete(itemTable,selectedId);go(itemTable);}));b.setTextColor(RED);}
        button(body,"Cancelar",false,()->navigate(itemTable));
    }
    private void reports() {
        title("Visión general","Reportes","Resumen global del taller. Todos los importes en MXN.");
        JSONObject r=scalar("SELECT COALESCE(SUM(monto),0) AS total, COALESCE(SUM(CASE WHEN substr(fecha,1,10)=date('now','localtime') THEN monto ELSE 0 END),0) AS hoy FROM pagos_orden");
        metric("Cobrado hoy",money(r,"hoy"),"Abonos registrados en la fecha local del dispositivo");metric("Ingresos cobrados",money(r,"total"),"Suma de todos los abonos, incluidos los de órdenes canceladas");
        JSONObject orders=scalar("SELECT COALESCE(SUM(CASE WHEN estatus!='Cancelado' THEN costo_total ELSE 0 END),0) AS total, COALESCE(SUM(CASE WHEN estatus!='Cancelado' THEN saldo ELSE 0 END),0) AS saldo, COALESCE(SUM(CASE WHEN estatus='Entregado' THEN costo_total-costo_repuesto ELSE 0 END),0) AS margen FROM ordenes");
        metric("Total presupuestado",money(orders,"total"),"Excluye órdenes canceladas");metric("Por cobrar",money(orders,"saldo"),"Excluye órdenes canceladas");
        metric("Margen estimado de entregas",money(orders,"margen"),"Total menos refacciones. No descuenta gastos ni equivale a utilidad neta.");
        label(body,"ÓRDENES POR ESTADO");for(JSONObject s:db.rows("SELECT estatus, COUNT(*) AS n FROM ordenes GROUP BY estatus")){LinearLayout c=card(body);c.addView(text(s.optString("estatus")+"   ·   "+s.optString("n"),16,TEXT));}
        JSONObject stock=scalar("SELECT COALESCE(SUM(stock),0) AS unidades, COALESCE(SUM(stock*costo_compra),0) AS costo, COALESCE(SUM(stock*precio_venta),0) AS venta FROM inventario");
        metric("Valor del inventario a costo",money(stock,"costo"),stock.optString("unidades")+" unidades · Valor a venta "+money(stock,"venta"));
    }
    private void more() {
        title("Herramientas del taller","Más","Todo lo necesario para organizar tu negocio.");
        button(body,"Catálogo de servicios",false,()->go("servicios_catalogo"));button(body,"Ubicaciones",false,()->go("ubicaciones"));button(body,"Reportes",false,()->go("reports"));button(body,"Configuración y respaldos",false,()->go("settings"));
        button(body,"Impresora Bluetooth · 58 / 80 mm",false,()->startActivity(new Intent(this,BluetoothPrinterActivity.class)));
        button(body,"Vista previa de nota de prueba",false,()->startActivity(new Intent(this,ReceiptActivity.class)));
        LinearLayout c=card(body);c.addView(text("ABCell Móvil · 0.2.0",18,TEXT));c.addView(text("Adaptación nativa de ABCell Pro\nAutoría original: ABCell Profecional",14,MUTED));
        c.addView(text("Tus datos se guardan en este dispositivo. No hay sincronización automática con la versión de escritorio. Exporta respaldos periódicamente.",14,MUTED));
    }
    private void settings() {
        title("Personaliza tu espacio","Mi taller","Estos datos aparecerán en los próximos tickets.");
        field("nombre","Nombre del taller *",db.setting("nombre","Mi taller"));field(body,"telefono","Teléfono / WhatsApp",db.setting("telefono",""),InputType.TYPE_CLASS_PHONE);
        multiline("direccion","Dirección",db.setting("direccion",""));multiline("garantia","Políticas y garantía",db.setting("garantia","Conserva esta nota para recoger tu equipo."));
        field("ticket_thanks","Texto de agradecimiento",db.setting("ticket_thanks","¡Gracias por su preferencia!"));
        choice("ticket_access","¿Incluir PIN y patrón en las notas compartidas e impresas?",new String[]{"No","Sí"},db.setting("ticket_access","No"));
        body.addView(text("Por privacidad se ocultan por defecto. Si activas Sí, saldrán tanto en el PNG como en la impresión. Las notas internas nunca salen.",13,MUTED));
        label(body,"LOGO DEL TALLER EN LA NOTA");
        logoPreview=new ImageView(this);logoPreview.setAdjustViewBounds(true);logoPreview.setMaxHeight(dp(110));logoPreview.setContentDescription("Logo personalizado del taller");logoPreview.setImageBitmap(ReceiptLogo.decode(db.setting("ticket_logo","")));body.addView(logoPreview,new LinearLayout.LayoutParams(-1,dp(110)));
        button(body,"Elegir logo PNG / JPG / WebP",false,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,LOGO);});
        button(body,"Quitar logo de las notas",false,()->{JSONObject data=new JSONObject();put(data,"ticket_logo","");db.settings(data);logoPreview.setImageDrawable(null);toast("Logo retirado");});
        body.addView(text("El logo se guarda al elegirlo y se incluye en los respaldos. Es independiente del icono de ABCell Móvil; no se usa el icono como sustituto del logo de tu taller.",13,MUTED));
        button(body,"Guardar configuración",true,()->{if(value("nombre").isEmpty())throw new IllegalArgumentException("Escribe el nombre del taller.");db.settings(fieldData());toast("Configuración guardada");go("more");});
        label(body,"RESPALDOS LOCALES");body.addView(text("El respaldo incluye clientes, equipos, PIN, abonos y configuración. Guárdalo en un lugar privado. No está cifrado. Desinstalar la aplicación elimina sus datos locales.",14,MUTED));
        button(body,"Exportar respaldo JSON",false,()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"ABCell-Movil-"+WorkshopDb.now().replace(':','-').replace(' ','_')+".json");startActivityForResult(i,EXPORT);
        });
        button(body,"Restaurar respaldo JSON",false,()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,IMPORT);
        });
        body.addView(text("El formato de respaldo de Móvil es propio. No selecciones directamente una base .db de escritorio. Guarda la configuración antes de exportar.",13,MUTED));
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==LOGO){
            busy=true;setEnabled(root,false);toast("Preparando logo…");
            io.execute(()->{
                Exception failure=null;
                try{String encoded=ReceiptLogo.importImage(this,uri);JSONObject setting=new JSONObject();put(setting,"ticket_logo",encoded);db.settings(setting);}catch(Exception e){failure=e;}
                final Exception failed=failure;
                runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;setEnabled(root,true);if(failed!=null)error(failed);else{if(logoPreview!=null)logoPreview.setImageBitmap(ReceiptLogo.decode(db.setting("ticket_logo","")));toast("Logo guardado. Guarda los demás datos del taller antes de generar la nota.");}});
            });return;
        }
        if(request==EXPORT)backupWork(()->{
            byte[] bytes=db.snapshot().toString(2).getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IllegalArgumentException("No se pudo abrir el archivo de destino.");out.write(bytes);}
        },"Respaldo exportado");
        if(request==IMPORT)confirm("¿Reemplazar los datos del taller?","Se reemplazarán TODAS las órdenes, abonos, inventario y configuración con el respaldo seleccionado. Exporta un respaldo actual antes de continuar.",()->backupWork(()->{
            try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(in==null)throw new IllegalArgumentException("No se pudo abrir el respaldo.");byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>20*1024*1024)throw new IllegalArgumentException("El respaldo supera el límite de 20 MB.");out.write(buffer,0,n);}
                db.restore(new JSONObject(out.toString(StandardCharsets.UTF_8.name())));
            }
        },"Respaldo restaurado"));
    }
    private interface Work {void run() throws Exception;}
    private void backupWork(Work work,String success) {
        busy=true;setEnabled(root,false);toast("Procesando respaldo…");io.execute(()->{
            Exception failure=null;try{work.run();}catch(Exception e){failure=e;}final Exception error=failure;
            runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;setEnabled(root,true);if(error==null){toast(success);go("more");}else error(error);});
        });
    }
    private void setEnabled(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)setEnabled(g.getChildAt(i),enabled);}}
    @Override protected void onDestroy(){super.onDestroy();io.execute(db::close);io.shutdown();}
}
