package com.abcell.movil;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Classic Bluetooth discovery, Android-managed bonding, profile selection and explicit printing. */
@SuppressLint("MissingPermission") // Every entry to Bluetooth is guarded by connectAllowed/scanAllowed.
public final class BluetoothPrinterActivity extends ScreenActivity {
    private static final int PERMISSIONS=81,ENABLE=82;
    private WorkshopDb db;
    private BluetoothAdapter adapter;
    private TextView status, selected;
    private LinearLayout deviceList;
    private Spinner paper;
    private CheckBox cut,insecure;
    private Button search,printTest,printOrder,cancel;
    private final Map<String,BluetoothDevice> devices=new LinkedHashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable stopScan=()->stopDiscovery("Búsqueda terminada. Si no aparece, activa el modo de vinculación de la impresora y vuelve a buscar.");
    private boolean registered,pendingScan,destroyed;
    private String pairingAddress="";
    private BluetoothPrintJob activeJob;
    private long folio;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!connectAllowed())return;
            String action=intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(d!=null&&d.getType()!=BluetoothDevice.DEVICE_TYPE_LE){devices.put(d.getAddress(),d);showDevices();}
            } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if(adapter!=null&&scanAllowed()&&adapter.isDiscovering())return;
                handler.removeCallbacks(stopScan);search.setEnabled(activeJob==null);status.setText("Búsqueda terminada. Selecciona tu impresora; no todos los dispositivos detectados son impresoras.");
            } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(d!=null&&d.getAddress().equals(pairingAddress)) {
                    int state=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.BOND_NONE);
                    if(state==BluetoothDevice.BOND_BONDED){choose(d);pairingAddress="";}
                    else if(state==BluetoothDevice.BOND_NONE){pairingAddress="";status.setText("Vinculación cancelada o fallida. Consulta el PIN de la impresora en su manual e inténtalo de nuevo.");}
                }
                loadBonded();
            } else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))refresh();
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);db=new WorkshopDb(this);folio=getIntent().getLongExtra("folio",0);
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
        shell("Impresora Bluetooth","Impresoras térmicas Bluetooth clásico SPP compatibles con gráficos ESC/POS. No admite impresoras BLE-only ni protocolos propietarios.");
        selected=text("",16,LIME);body.addView(selected);
        body.addView(text("Papel y ancho imprimible",14,MUTED));
        paper=new Spinner(this);String[] labels=Arrays.stream(PaperProfile.values()).map(p->p.label).toArray(String[]::new);
        paper.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));paper.setMinimumHeight(dp(52));
        paper.setSelection(PaperProfile.from(db.setting("printer_paper","58")).ordinal());body.addView(paper);
        paper.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int i,long id){save("printer_paper",PaperProfile.values()[i].key);}});
        cut=new CheckBox(this);cut.setText("Corte automático (solo si la impresora tiene cortador)");cut.setTextColor(TEXT);cut.setChecked(db.setting("printer_cut","No").equals("Sí"));body.addView(cut);cut.setOnCheckedChangeListener((v,on)->save("printer_cut",on?"Sí":"No"));
        insecure=new CheckBox(this);insecure.setText("Conexión SPP alternativa (insegura, solo si falla la normal)");insecure.setTextColor(TEXT);insecure.setChecked(db.setting("printer_insecure","No").equals("Sí"));body.addView(insecure);insecure.setOnCheckedChangeListener((v,on)->save("printer_insecure",on?"Sí":"No"));
        status=text("",14,MUTED);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        search=button("Buscar impresoras Bluetooth",true,()->ensureBluetooth(true));
        button("Ver impresoras vinculadas",false,()->ensureBluetooth(false));
        button("Abrir ajustes Bluetooth de Android",false,()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        printTest=button("Imprimir nota de prueba",false,()->print(true));
        if(folio>0)printOrder=button("Imprimir orden #"+folio,true,()->print(false));
        cancel=button("Cancelar impresión",false,()->{if(activeJob!=null){activeJob.cancel();status.setText("Cancelando… Revisa si salió parte de la nota antes de repetir.");}});cancel.setVisibility(android.view.View.GONE);
        body.addView(text("DISPOSITIVOS VINCULADOS Y DETECTADOS",13,LIME));deviceList=column();body.addView(deviceList);
        body.addView(text("El ancho no se puede conocer de forma fiable por Bluetooth: elige 58 o 80 mm según el manual. Si una de 80 mm recorta el borde, prueba el perfil de 512 puntos. Los ajustes se guardan al cambiarlos.",14,MUTED));
        if(state!=null){pairingAddress=state.getString("pairing","");if(state.getBoolean("printing"))status.setText("La impresión anterior se interrumpió. Revisa el papel; no se reenvía automáticamente.");}
        IntentFilter f=new IntentFilter();f.addAction(BluetoothDevice.ACTION_FOUND);f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_EXPORTED);else registerReceiver(receiver,f);registered=true;
        refresh();
    }
    private void save(String key,String value){try{db.settings(new JSONObject().put(key,value));}catch(Exception e){failure("No se pudo guardar la preferencia.");}}
    private boolean connectAllowed(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private boolean scanAllowed(){return Build.VERSION.SDK_INT>=31?checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED:checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void refresh(){
        selected.setText("Seleccionada: "+db.setting("printer_name","Ninguna")+"\n"+db.setting("printer_address",""));
        if(adapter==null){status.setText("Este dispositivo no tiene adaptador Bluetooth. Puedes compartir la nota PNG o usar impresión Android/PDF.");search.setEnabled(false);printTest.setEnabled(false);if(printOrder!=null)printOrder.setEnabled(false);return;}
        if(!connectAllowed()){status.setText("Pulsa Buscar o Ver vinculadas para autorizar el acceso Bluetooth.");return;}
        if(!adapter.isEnabled()){status.setText("Bluetooth está apagado. Actívalo para detectar o imprimir.");return;}
        loadBonded();
    }
    private void ensureBluetooth(boolean scan){
        if(activeJob!=null){toast("Espera a que termine la impresión.");return;}
        if(adapter==null){failure("Este teléfono no tiene Bluetooth.");return;}
        List<String> missing=new ArrayList<>();
        if(!connectAllowed())missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(scan&&!scanAllowed())missing.add(Build.VERSION.SDK_INT>=31?Manifest.permission.BLUETOOTH_SCAN:Manifest.permission.ACCESS_FINE_LOCATION);
        if(!missing.isEmpty()){
            pendingScan=scan;
            String rationale=Build.VERSION.SDK_INT>=31?"Se necesita permiso de dispositivos cercanos para encontrar y conectar la impresora.":"Android 8–11 exige permiso de ubicación para descubrir dispositivos Bluetooth. ABCell no obtiene ni guarda tu ubicación. Para imprimir con una ya vinculada no se solicita ese permiso.";
            new AlertDialog.Builder(this).setTitle("Permisos de Bluetooth").setMessage(rationale).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->requestPermissions(missing.toArray(new String[0]),PERMISSIONS)).show();return;
        }
        if(!adapter.isEnabled()){pendingScan=scan;startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE);return;}
        loadBonded();if(scan)discover();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);if(request!=PERMISSIONS)return;
        if(!connectAllowed()||(pendingScan&&!scanAllowed())){
            status.setText("Permiso denegado. Puedes activarlo en Ajustes → Aplicaciones → ABCell Móvil → Permisos. Las notas PNG/PDF siguen disponibles.");
            if(connectAllowed())loadBonded();return;
        }
        ensureBluetooth(pendingScan);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==ENABLE){if(result==RESULT_OK)ensureBluetooth(pendingScan);else status.setText("Bluetooth sigue apagado. No se inició ninguna impresión.");}}
    private void loadBonded(){
        if(adapter==null||!connectAllowed())return;
        for(BluetoothDevice d:adapter.getBondedDevices())if(d.getType()!=BluetoothDevice.DEVICE_TYPE_LE)devices.put(d.getAddress(),d);
        showDevices();
    }
    private void showDevices(){
        deviceList.removeAllViews();
        if(devices.isEmpty()){deviceList.addView(text("Sin dispositivos. Enciende la impresora y activa su modo de vinculación; después pulsa Buscar.",14,MUTED));return;}
        List<BluetoothDevice> sorted=new ArrayList<>(devices.values());sorted.sort(Comparator.comparing((BluetoothDevice d)->d.getBondState()!=BluetoothDevice.BOND_BONDED).thenComparing(BluetoothDevice::getAddress));
        for(BluetoothDevice d:sorted){
            String name=d.getName()==null?"Dispositivo sin nombre":d.getName();
            Button row=new Button(this);row.setAllCaps(false);row.setMinHeight(dp(64));row.setText(name+"\n"+d.getAddress()+" · "+(d.getBondState()==BluetoothDevice.BOND_BONDED?"Vinculada":"Detectada · vincular"));
            row.setEnabled(activeJob==null);deviceList.addView(row,new LinearLayout.LayoutParams(-1,-2));row.setOnClickListener(v->select(d));
        }
    }
    private void select(BluetoothDevice device){
        if(!connectAllowed()||activeJob!=null)return;
        new AlertDialog.Builder(this).setTitle("Usar esta impresora").setMessage((device.getName()==null?"Dispositivo":device.getName())+"\n"+device.getAddress()+"\nDebe ser una impresora ESC/POS. Seleccionarla no envía ninguna nota.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Seleccionar",(d,w)->{
                    stopDiscovery(null);
                    if(device.getBondState()==BluetoothDevice.BOND_BONDED)choose(device);
                    else{
                        pairingAddress=device.getAddress();
                        if(!device.createBond()){pairingAddress="";failure("No se pudo iniciar la vinculación. Inténtalo desde los ajustes Bluetooth de Android.");}
                        else status.setText("Confirma la vinculación en Android. Si pide PIN, consulta el manual de la impresora.");
                    }
                }).show();
    }
    private void choose(BluetoothDevice device){
        try{db.settings(new JSONObject().put("printer_address",device.getAddress()).put("printer_name",device.getName()==null?"Impresora":device.getName()));}
        catch(Exception e){failure("No se pudo guardar la impresora.");return;}
        selected.setText("Seleccionada: "+db.setting("printer_name","")+"\n"+device.getAddress());status.setText("Impresora seleccionada. Elige el ancho y envía una nota de prueba.");
    }
    private void discover(){
        if(!connectAllowed()||!scanAllowed())return;
        if(Build.VERSION.SDK_INT<31){
            LocationManager location=(LocationManager)getSystemService(LOCATION_SERVICE);
            boolean enabled=location!=null&&(location.isProviderEnabled(LocationManager.GPS_PROVIDER)||location.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            if(!enabled){new AlertDialog.Builder(this).setTitle("Android requiere ubicación activa").setMessage("Para buscar Bluetooth en Android 8–11 activa temporalmente Ubicación. No se leen coordenadas. También puedes usar una impresora ya vinculada sin buscar.").setNegativeButton("Cancelar",null).setPositiveButton("Abrir ajustes",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show();return;}
        }
        stopDiscovery(null);devices.clear();loadBonded();
        if(adapter.startDiscovery()){search.setEnabled(false);status.setText("Buscando dispositivos Bluetooth cercanos…");handler.postDelayed(stopScan,20000);}
        else failure("No se pudo iniciar la búsqueda. Revisa los permisos, el Bluetooth y vuelve a intentar.");
    }
    private void stopDiscovery(String message){
        handler.removeCallbacks(stopScan);
        if(adapter!=null&&scanAllowed()&&adapter.isDiscovering())adapter.cancelDiscovery();
        if(search!=null)search.setEnabled(activeJob==null&&adapter!=null);
        if(message!=null&&status!=null)status.setText(message);
    }
    private void print(boolean demo){
        if(activeJob!=null)return;
        if(!connectAllowed()){ensureBluetooth(false);toast("Después de autorizar el permiso, vuelve a pulsar Imprimir.");return;}
        if(adapter==null||!adapter.isEnabled()){ensureBluetooth(false);return;}
        String address=db.setting("printer_address","");
        if(!BluetoothAdapter.checkBluetoothAddress(address))throw new IllegalArgumentException("Primero selecciona una impresora vinculada.");
        BluetoothDevice device=adapter.getRemoteDevice(address);
        if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("La impresora ya no está vinculada. Vincúlala de nuevo.");
        ReceiptData receipt=ReceiptData.load(db,demo?ReceiptData.demoOrder():db.order(folio),demo);
        PaperProfile profile=PaperProfile.values()[paper.getSelectedItemPosition()];boolean doCut=cut.isChecked(),alternate=insecure.isChecked();
        new AlertDialog.Builder(this).setTitle(demo?"Imprimir nota de prueba":"Imprimir orden #"+folio)
                .setMessage(db.setting("printer_name","Impresora")+"\n"+profile.label+"\nSe enviará una copia. Comprueba papel y ancho antes de continuar.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Imprimir",(dialog,w)->{
                    stopDiscovery(null);activeJob=new BluetoothPrintJob();BluetoothPrintJob job=activeJob;setPrinting(true);status.setText("Preparando nota y conectando…");
                    worker.execute(()->{
                        android.graphics.Bitmap bitmap=null;Exception error=null;
                        try{
                            bitmap=ReceiptRenderer.render(receipt,profile.dots,true).bitmap;
                            job.send(device,bitmap,alternate,doCut,(rows,total)->handler.post(()->{if(!destroyed)status.setText("Enviando nota… "+(rows*100/total)+" %");}));
                        }catch(Exception e){error=e;}finally{if(bitmap!=null)bitmap.recycle();}
                        Exception failure=error;
                        handler.post(()->{if(destroyed)return;activeJob=null;setPrinting(false);if(failure!=null){status.setText("La impresión no se confirmó. Revisa si salió parte del recibo antes de repetir.");failure(failure.getMessage());}
                            else status.setText("Datos enviados a la impresora. Comprueba la nota física: Bluetooth no confirma papel, tinta térmica ni final de impresión.");});
                    });
                }).show();
    }
    private void setPrinting(boolean printing){
        search.setEnabled(!printing);printTest.setEnabled(!printing);if(printOrder!=null)printOrder.setEnabled(!printing);
        paper.setEnabled(!printing);cut.setEnabled(!printing);insecure.setEnabled(!printing);cancel.setVisibility(printing?android.view.View.VISIBLE:android.view.View.GONE);showDevices();
    }
    @Override public void onBackPressed(){
        BluetoothPrintJob job=activeJob;
        if(job!=null)new AlertDialog.Builder(this).setTitle("¿Cancelar y salir?").setMessage("La nota podría salir parcialmente. No se reenvía automáticamente.").setNegativeButton("Continuar imprimiendo",null).setPositiveButton("Cancelar y salir",(d,w)->{job.cancel();finish();}).show();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("pairing",pairingAddress);out.putBoolean("printing",activeJob!=null);}
    @Override protected void onStop(){stopDiscovery(null);super.onStop();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(receiver);if(activeJob!=null)activeJob.cancel();worker.shutdownNow();db.close();super.onDestroy();}
}
