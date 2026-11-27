package com.abcell.movil;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationTest {
    private View find(View v,String text){
        if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=find(g.getChildAt(i),text);if(r!=null)return r;}}
        return null;
    }
    private EditText firstEdit(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText e=firstEdit(g.getChildAt(i));if(e!=null)return e;}}return null;}
    @Test public void nativeNavigationAndDraftSurviveRecreation(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"  ABCell Móvil"));find(root,"+  Recibir equipo").performClick();firstEdit(a.getWindow().getDecorView()).setText("Cliente prueba");});
            scenario.recreate();scenario.onActivity(a->assertEquals("Cliente prueba",firstEdit(a.getWindow().getDecorView()).getText().toString()));
        }
    }
    @Test public void moreContainsOnlyWorkshopModules(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();find(root,"Más").performClick();root=a.getWindow().getDecorView();assertNotNull(find(root,"Catálogo de servicios"));assertNotNull(find(root,"Configuración y respaldos"));assertNull(find(root,"Utilidades Android"));});
        }
    }
}
