package com.alfahdtv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("settings",0);
        getWindow().setStatusBarColor(AppUi.BG);
        getWindow().setNavigationBarColor(AppUi.BG);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(14),dp(16),0);root.setBackgroundColor(AppUi.BG);
        TextView head=AppUi.text(this,"\u2039   \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0641\u0647\u062f TV",23,Color.WHITE);head.setTypeface(null,1);head.setOnClickListener(v->finish());root.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.addView(section("\u062a\u0641\u0636\u064a\u0644\u0627\u062a \u0627\u0644\u0645\u0634\u0627\u0647\u062f\u0629"));
        body.addView(toggle("\u062a\u0646\u0628\u064a\u0647\u0627\u062a \u0627\u0644\u0645\u062d\u062a\u0648\u0649 \u0627\u0644\u062c\u062f\u064a\u062f","notifications",true));
        body.addView(toggle("\u0635\u0648\u0631\u0629 \u062f\u0627\u062e\u0644 \u0635\u0648\u0631\u0629","pip",true));
        body.addView(toggle("\u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u0641\u064a\u062f\u064a\u0648 \u062a\u0644\u0642\u0627\u0626\u064a\u064b\u0627","autoplay",true));
        body.addView(toggle("\u062d\u0645\u0627\u064a\u0629 \u0627\u0644\u0634\u0627\u0634\u0629 \u0645\u0646 \u0627\u0644\u062a\u0635\u0648\u064a\u0631","secure",true));
        body.addView(section("\u0627\u0644\u062a\u0637\u0628\u064a\u0642"));
        body.addView(action("\u21bb  \u0641\u062d\u0635 \u062a\u062d\u062f\u064a\u062b\u0627\u062a \u0627\u0644\u062a\u0637\u0628\u064a\u0642",v->UpdateChecker.check(this,true)));
        body.addView(action("\u232b  \u0645\u0633\u062d \u0627\u0644\u0645\u0644\u0641\u0627\u062a \u0627\u0644\u0645\u0624\u0642\u062a\u0629",v->confirmClearCache()));
        body.addView(section("\u0627\u0644\u062f\u0639\u0645 \u0648\u0627\u0644\u0645\u0639\u0644\u0648\u0645\u0627\u062a"));
        body.addView(action("\u2709  \u062a\u0648\u0627\u0635\u0644 \u0645\u0639\u0646\u0627",v->contact()));
        body.addView(action("\u21a5  \u0645\u0634\u0627\u0631\u0643\u0629 \u0627\u0644\u062a\u0637\u0628\u064a\u0642",v->share()));
        body.addView(action("\u26a0  \u0625\u062e\u0644\u0627\u0621 \u0627\u0644\u0645\u0633\u0624\u0648\u0644\u064a\u0629",v->disclaimer()));
        TextView version=AppUi.text(this,"\u0627\u0644\u0641\u0647\u062f TV  \u2022  \u0627\u0644\u0625\u0635\u062f\u0627\u0631 "+BuildConfig.VERSION_NAME+"\n\u062a\u0637\u0628\u064a\u0642 \u0623\u0635\u0644\u064a \u0628\u062a\u0635\u0645\u064a\u0645 \u0645\u062e\u0635\u0635 \u0644\u0644\u0645\u0648\u0628\u0627\u064a\u0644",13,AppUi.MUTED);version.setGravity(Gravity.CENTER);version.setPadding(0,dp(30),0,dp(34));body.addView(version);
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private TextView section(String text){TextView v=AppUi.text(this,text,14,AppUi.RED);v.setTypeface(null,1);v.setPadding(dp(8),dp(18),dp(8),dp(8));return v;}
    private TextView action(String text,android.view.View.OnClickListener click){TextView v=AppUi.action(this,text);v.setTextSize(16);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);v.setPadding(dp(18),0,dp(18),0);v.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.bottomMargin=dp(10);v.setLayoutParams(p);return v;}
    private Switch toggle(String title,String key,boolean def){Switch v=new Switch(this);v.setText(title);v.setTextSize(16);v.setTextColor(Color.WHITE);v.setChecked(prefs.getBoolean(key,def));v.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(AppUi.round(AppUi.PANEL,14,this));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(64));p.bottomMargin=dp(10);v.setLayoutParams(p);v.setOnCheckedChangeListener((button,value)->{prefs.edit().putBoolean(key,value).apply();if(key.equals("notifications")){if(value)NotificationScheduler.schedule(this);else NotificationScheduler.cancel(this);}});return v;}
    private void confirmClearCache(){new AlertDialog.Builder(this).setTitle("\u0645\u0633\u062d \u0627\u0644\u0645\u0644\u0641\u0627\u062a \u0627\u0644\u0645\u0624\u0642\u062a\u0629\u061f").setMessage("\u0644\u0646 \u064a\u062a\u0645 \u062d\u0630\u0641 \u0627\u0644\u062a\u0646\u0632\u064a\u0644\u0627\u062a \u0623\u0648 \u0627\u0644\u0645\u0641\u0636\u0644\u0629.").setNegativeButton("\u0625\u0644\u063a\u0627\u0621",null).setPositiveButton("\u0645\u0633\u062d",(d,w)->{WebView web=new WebView(this);web.clearCache(true);web.destroy();WebStorage.getInstance().deleteAllData();CookieManager.getInstance().removeAllCookies(null);}).show();}
    private void contact(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("message/rfc822");i.putExtra(Intent.EXTRA_SUBJECT,"Al Fahd TV support");startActivity(Intent.createChooser(i,"\u062a\u0648\u0627\u0635\u0644 \u0645\u0639\u0646\u0627"));}
    private void share(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"\u0634\u0627\u0647\u062f \u0627\u0644\u0641\u0647\u062f TV: "+BuildConfig.HOME_URL);startActivity(Intent.createChooser(i,"\u0645\u0634\u0627\u0631\u0643\u0629 \u0627\u0644\u062a\u0637\u0628\u064a\u0642"));}
    private void disclaimer(){new AlertDialog.Builder(this).setTitle("\u0625\u062e\u0644\u0627\u0621 \u0627\u0644\u0645\u0633\u0624\u0648\u0644\u064a\u0629").setMessage("\u064a\u0639\u0631\u0636 \u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0645\u062d\u062a\u0648\u0649 \u0627\u0644\u0645\u0635\u0631\u062d \u0628\u0627\u0633\u062a\u062e\u062f\u0627\u0645\u0647 \u0641\u0642\u0637. \u0627\u0644\u0645\u0633\u062a\u062e\u062f\u0645 \u0645\u0633\u0624\u0648\u0644 \u0639\u0646 \u0627\u0644\u0627\u0644\u062a\u0632\u0627\u0645 \u0628\u0627\u0644\u0642\u0648\u0627\u0646\u064a\u0646 \u0627\u0644\u0645\u062d\u0644\u064a\u0629 \u0648\u062d\u0642\u0648\u0642 \u0627\u0644\u0645\u0644\u0643\u064a\u0629 \u0627\u0644\u0641\u0643\u0631\u064a\u0629.").setPositiveButton("\u062d\u0633\u0646\u064b\u0627",null).show();}
    private int dp(int value){return AppUi.dp(this,value);}
}
