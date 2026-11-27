package com.abcell.movil;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import static org.junit.Assert.*;

/** Safe emulator coverage: no radio scan, pairing dialog or real print is initiated by these tests. */
public class PrinterScreensTest {
    private View find(View v,String text){
        if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;
    }
    private Spinner spinner(View v){
        if(v instanceof Spinner)return (Spinner)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Spinner found=spinner(g.getChildAt(i));if(found!=null)return found;}}return null;
    }
    @Test public void printerScreenOpensWithoutPermissionsOrHardwareAndRestoresProfile(){
        try(ActivityScenario<BluetoothPrinterActivity> scenario=ActivityScenario.launch(BluetoothPrinterActivity.class)){
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Buscar impresoras Bluetooth"));assertNotNull(find(root,"Ver impresoras vinculadas"));assertNotNull(find(root,"Imprimir nota de prueba"));assertEquals(3,spinner(root).getCount());spinner(root).setSelection(1);});
            scenario.recreate();scenario.onActivity(a->assertEquals(1,spinner(a.getWindow().getDecorView()).getSelectedItemPosition()));
        }
    }
    @Test public void notePreviewOpensAndRestoresThermalMode(){
        try(ActivityScenario<ReceiptActivity> scenario=ActivityScenario.launch(ReceiptActivity.class)){
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Compartir nota digital PNG"));assertNotNull(find(root,"Imprimir / guardar nota PDF"));assertNotNull(find(root,"Imprimir por Bluetooth"));spinner(root).setSelection(1);});
            scenario.recreate();scenario.onActivity(a->assertEquals(1,spinner(a.getWindow().getDecorView()).getSelectedItemPosition()));
        }
    }
}
