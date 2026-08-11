package com.alfahdtv.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class DownloadsActivity extends Activity {
    private final Handler refreshHandler=new Handler(Looper.getMainLooper());
    private final Runnable refreshTask=new Runnable(){@Override public void run(){load();refreshHandler.postDelayed(this,1200);}};
    private LinearLayout list;

    @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(AppUi.BG);build();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(AppUi.BG);root.setPadding(AppUi.dp(this,16),AppUi.dp(this,14),AppUi.dp(this,16),0);
        TextView head=AppUi.text(this,"‹   التنزيلات",24,Color.WHITE);head.setTypeface(null,1);head.setOnClickListener(v->finish());root.addView(head,new LinearLayout.LayoutParams(-1,AppUi.dp(this,56)));
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void load(){
        list.removeAllViews();List<VideoDownloads.Record> records=VideoDownloads.records(this);
        for(VideoDownloads.Record record:records)addCard(VideoDownloads.snapshot(this,record));
        if(records.isEmpty()){TextView empty=AppUi.text(this,"لا توجد تنزيلات حتى الآن",17,AppUi.MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty,new LinearLayout.LayoutParams(-1,AppUi.dp(this,300)));}
    }

    private void addCard(VideoDownloads.Snapshot item){
        VideoDownloads.Record record=item.record;int status=item.status;
        String state;if(record.cancelled)state="تم إيقاف التحميل";else if(!item.exists)state="التنزيل غير متاح — يمكنك إعادته";else if(status==DownloadManager.STATUS_SUCCESSFUL)state="اكتمل";else if(status==DownloadManager.STATUS_RUNNING)state="جاري التحميل";else if(status==DownloadManager.STATUS_PAUSED)state="متوقف مؤقتًا بواسطة النظام";else if(status==DownloadManager.STATUS_FAILED)state="فشل التحميل";else state="في الانتظار";
        String progress=item.total>0?" • "+(item.downloaded*100/item.total)+"% • "+size(item.downloaded)+" / "+size(item.total):item.downloaded>0?" • "+size(item.downloaded):"";
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(AppUi.dp(this,16),AppUi.dp(this,14),AppUi.dp(this,16),AppUi.dp(this,14));card.setBackground(AppUi.round(AppUi.PANEL,15,this));
        TextView name=AppUi.text(this,record.name,16,Color.WHITE);name.setTypeface(null,1);card.addView(name);
        TextView meta=AppUi.text(this,state+progress,13,(status==DownloadManager.STATUS_FAILED||record.cancelled||!item.exists)?AppUi.RED:AppUi.MUTED);meta.setPadding(0,AppUi.dp(this,7),0,AppUi.dp(this,9));card.addView(meta);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.RIGHT);actions.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        if(item.exists&&status==DownloadManager.STATUS_SUCCESSFUL)actions.addView(button("فتح",v->open(record.id)));
        if(item.exists&&(status==DownloadManager.STATUS_RUNNING||status==DownloadManager.STATUS_PENDING||status==DownloadManager.STATUS_PAUSED))actions.addView(button("إيقاف",v->{VideoDownloads.cancel(this,record);load();}));
        if((!item.exists||status==DownloadManager.STATUS_FAILED||record.cancelled)&&!record.url.isEmpty())actions.addView(button("إعادة التحميل",v->{try{VideoDownloads.retry(this,record);toast("بدأ التحميل من جديد");load();}catch(Exception e){toast("تعذر إعادة التحميل");}}));
        if(!item.exists||status==DownloadManager.STATUS_FAILED||status==DownloadManager.STATUS_SUCCESSFUL||record.cancelled)actions.addView(button("إخفاء من القائمة",v->{VideoDownloads.forget(this,record.id);load();}));
        card.addView(actions,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=AppUi.dp(this,10);list.addView(card,params);
    }

    private Button button(String label,android.view.View.OnClickListener click){Button button=new Button(this);button.setText(label);button.setTextColor(Color.WHITE);button.setTextSize(12);button.setAllCaps(false);button.setBackground(AppUi.round(AppUi.RED,10,this));button.setOnClickListener(click);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,AppUi.dp(this,40));params.setMarginStart(AppUi.dp(this,8));button.setLayoutParams(params);return button;}
    private void open(long id){Uri uri=VideoDownloads.downloadedUri(this,id);if(uri==null){toast("تعذر فتح الملف");return;}Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(intent);}catch(Exception e){toast("لا يوجد مشغل فيديو مناسب");}}
    private String size(long bytes){if(bytes<1024)return bytes+" B";if(bytes<1024*1024)return String.format(Locale.US,"%.1f KB",bytes/1024d);if(bytes<1024L*1024*1024)return String.format(Locale.US,"%.1f MB",bytes/1048576d);return String.format(Locale.US,"%.2f GB",bytes/1073741824d);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();refreshHandler.removeCallbacks(refreshTask);refreshHandler.post(refreshTask);}
    @Override protected void onPause(){refreshHandler.removeCallbacks(refreshTask);super.onPause();}
}
