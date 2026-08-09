package com.alfahdtv.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

final class AppUi {
    static final int BG=Color.rgb(8,9,13), PANEL=Color.rgb(19,21,28), RED=Color.rgb(239,35,60), MUTED=Color.rgb(160,163,174);
    static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static TextView text(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static TextView action(Context c,String text){TextView v=text(c,text,18,Color.WHITE);v.setGravity(Gravity.CENTER);v.setBackground(round(PANEL,14,c));v.setClickable(true);v.setFocusable(true);v.setPadding(dp(c,12),0,dp(c,12),0);return v;}
    static GradientDrawable round(int color,int radius,Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
    static void divider(View v){v.setBackgroundColor(Color.rgb(37,39,48));}
    private AppUi(){}
}
