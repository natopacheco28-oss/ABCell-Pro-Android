package com.abcell.movil;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared accessible, inset-aware shell for receipt and printer screens. */
public abstract class ScreenActivity extends Activity {
    protected static final int BG=0xff0c111b,CARD=0xff151e2c,TEXT=0xffedf2f7,MUTED=0xff9baabd,LIME=0xffc5f467;
    protected LinearLayout body;
    protected int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    protected LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    protected TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(6),0,dp(6));return t;}
    protected void shell(String title,String subtitle){
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        setContentView(root);root.requestApplyInsets();
        LinearLayout top=new LinearLayout(this);top.setPadding(dp(12),dp(6),dp(12),dp(6));top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("Volver");back.setAllCaps(false);back.setMinHeight(dp(48));back.setOnClickListener(v->onBackPressed());top.addView(back);
        TextView heading=text("  "+title,21,TEXT);heading.setTypeface(null,Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(20),dp(8),dp(20),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(text(subtitle,14,MUTED));
    }
    protected Button button(String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(52));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setTextColor(primary?BG:TEXT);
        GradientDrawable bg=new GradientDrawable();bg.setColor(primary?LIME:CARD);bg.setCornerRadius(dp(14));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);body.addView(b,p);
        b.setOnClickListener(v->{try{action.run();}catch(Exception e){failure(e.getMessage());}});return b;
    }
    protected void failure(String message){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("No se pudo completar").setMessage(message==null?"Revisa los datos e inténtalo de nuevo.":message).setPositiveButton("Entendido",null).show();}
    protected void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
