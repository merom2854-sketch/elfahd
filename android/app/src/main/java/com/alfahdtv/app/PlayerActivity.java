package com.alfahdtv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.net.Uri;
import android.util.Rational;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONObject;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private PlayerView playerView;
    private String mediaUrl;
    private String fallbackMediaUrl;
    private String mediaTitle;
    private String mediaImage;
    private boolean pipEnabled;
    private boolean autoplay;
    private TextView seekHint;
    private long lastTapAt;
    private float lastTapX;
    private LinearLayout topOverlay;
    private long resumePosition;
    private boolean resumePlaying;
    private boolean pipTransition;
    private boolean fallbackAttempted;
    private boolean controlsLocked;
    private boolean autoNextEnabled;
    private boolean nextResolveInFlight;
    private String nextMediaUrl;
    private String nextFallbackMediaUrl;
    private String nextMediaTitle;
    private String nextResolverUrl;
    private TextView playerTitle;
    private ImageButton unlockButton;
    private LinearLayout nextEpisodeCard;
    private TextView nextEpisodeText;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private Runnable nextEpisodeRunnable;
    private static final String RESOLVER_API="https://elfahd-tv.vercel.app/api/resolve";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        mediaUrl=getIntent().getStringExtra("media_url");
        fallbackMediaUrl=getIntent().getStringExtra("media_fallback_url");
        mediaTitle=getIntent().getStringExtra("media_title");
        mediaImage=getIntent().getStringExtra("media_image");
        nextMediaUrl=getIntent().getStringExtra("next_media_url");
        nextFallbackMediaUrl=getIntent().getStringExtra("next_media_fallback_url");
        nextMediaTitle=getIntent().getStringExtra("next_media_title");
        nextResolverUrl=getIntent().getStringExtra("next_resolver_url");
        SharedPreferences resume=getSharedPreferences("player_resume",0);
        if(mediaUrl!=null&&mediaUrl.equals(resume.getString("url","")))resumePosition=resume.getLong("position",0);
        pipEnabled=getSharedPreferences("settings",0).getBoolean("pip",true);
        autoplay=getSharedPreferences("settings",0).getBoolean("autoplay",true);
        autoNextEnabled=getSharedPreferences("settings",0).getBoolean("auto_next",true);
        // The player follows the device in either direction instead of forcing the
        // viewer to press a fullscreen button. This applies only to this Activity.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        if(getSharedPreferences("settings",0).getBoolean("secure",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        playerView=(PlayerView)getLayoutInflater().inflate(R.layout.player_view,null);
        View videoSurface=playerView.getVideoSurfaceView();
        if(videoSurface!=null){videoSurface.setClickable(true);videoSurface.setFocusable(true);videoSurface.setOnTouchListener((view,event)->{handleOverlayTouch(event);return true;});}
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(4200);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        setContentView(buildPlayerLayout());
        updatePipParams();
    }

    private View buildPlayerLayout(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        seekHint=AppUi.text(this,"",18,Color.WHITE);seekHint.setGravity(Gravity.CENTER);seekHint.setTypeface(null,1);seekHint.setVisibility(View.GONE);seekHint.setBackground(AppUi.round(Color.argb(190,0,0,0),28,this));FrameLayout.LayoutParams hintParams=new FrameLayout.LayoutParams(dp(170),dp(64),Gravity.CENTER);root.addView(seekHint,hintParams);
        LinearLayout top=new LinearLayout(this);topOverlay=top;top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundResource(R.drawable.top_overlay);
        ImageButton back=playerButton(R.drawable.ic_player_back,"رجوع");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        playerTitle=AppUi.text(this,mediaTitle==null||mediaTitle.trim().isEmpty()?"الفهد TV":mediaTitle,16,Color.WHITE);playerTitle.setTypeface(null,1);playerTitle.setSingleLine(true);playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,dp(48),1);titleParams.setMargins(dp(9),0,dp(9),0);top.addView(playerTitle,titleParams);
        ImageButton rotate=playerButton(R.drawable.ic_player_rotate,"تدوير الشاشة تلقائيًا");rotate.setOnClickListener(v->enableAutoRotation());top.addView(rotate,new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageButton quality=playerButton(R.drawable.ic_player_quality,"اختيار جودة الفيديو");quality.setOnClickListener(v->showQualityPicker());top.addView(quality,new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageButton options=playerButton(R.drawable.ic_player_more,"إعدادات المشغّل");options.setOnClickListener(v->showPlayerOptions());top.addView(options,new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(Build.VERSION.SDK_INT>=26&&pipEnabled){ImageButton pip=playerButton(R.drawable.ic_player_pip,"صورة داخل صورة");pip.setOnClickListener(v->enterPip());top.addView(pip,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,dp(72),Gravity.TOP);root.addView(top,topParams);
        unlockButton=playerButton(R.drawable.ic_player_lock,"إلغاء قفل الشاشة");unlockButton.setVisibility(View.GONE);unlockButton.setOnClickListener(v->setControlsLocked(false));root.addView(unlockButton,new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.CENTER));
        nextEpisodeCard=new LinearLayout(this);nextEpisodeCard.setOrientation(LinearLayout.VERTICAL);nextEpisodeCard.setPadding(dp(16),dp(12),dp(16),dp(12));nextEpisodeCard.setBackground(AppUi.round(Color.argb(230,18,21,29),18,this));nextEpisodeCard.setVisibility(View.GONE);
        nextEpisodeText=AppUi.text(this,"",14,Color.WHITE);nextEpisodeText.setTypeface(null,1);nextEpisodeCard.addView(nextEpisodeText,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout nextActions=new LinearLayout(this);nextActions.setGravity(Gravity.CENTER_VERTICAL);nextActions.setPadding(0,dp(8),0,0);
        TextView cancelNext=overlayAction("إلغاء");cancelNext.setOnClickListener(v->cancelNextEpisode());nextActions.addView(cancelNext,new LinearLayout.LayoutParams(0,dp(38),1));
        TextView playNext=overlayAction("تشغيل الآن");playNext.setOnClickListener(v->advanceToNextEpisode());nextActions.addView(playNext,new LinearLayout.LayoutParams(0,dp(38),1));
        nextEpisodeCard.addView(nextActions,new LinearLayout.LayoutParams(-1,-2));FrameLayout.LayoutParams nextParams=new FrameLayout.LayoutParams(dp(288),-2,Gravity.BOTTOM|Gravity.END);nextParams.setMargins(dp(16),dp(16),dp(16),dp(28));root.addView(nextEpisodeCard,nextParams);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{
            if(!controlsLocked)top.setVisibility(View.VISIBLE);
        });
        return root;
    }

    private ImageButton playerButton(int icon,String description){ImageButton button=new ImageButton(this);button.setImageResource(icon);button.setContentDescription(description);button.setBackground(AppUi.round(Color.argb(105,20,21,27),24,this));button.setPadding(dp(10),dp(10),dp(10),dp(10));return button;}
    private TextView overlayAction(String text){TextView action=AppUi.text(this,text,13,Color.WHITE);action.setGravity(Gravity.CENTER);action.setTypeface(null,1);action.setBackground(AppUi.round(Color.argb(170,242,45,70),11,this));return action;}
    private int dp(int value){return AppUi.dp(this,value);}
    private boolean isTrustedMediaUrl(String value){if(value==null)return false;try{Uri uri=Uri.parse(value);String scheme=uri.getScheme(),host=uri.getHost();return "https".equalsIgnoreCase(scheme)||("http".equalsIgnoreCase(scheme)&&host!=null&&host.toLowerCase(java.util.Locale.ROOT).endsWith(".downet.net"));}catch(Exception ignored){return false;}}

    @Override protected void onStart() {
        super.onStart();
        if(player==null)preparePlayer();
    }

    private void preparePlayer() {
        if(!isTrustedMediaUrl(mediaUrl)){finish();return;}
        trackSelector=new DefaultTrackSelector(this);
        player=new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(mediaUrl));
        player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){if(switchToFallback())return;Toast.makeText(PlayerActivity.this,"تعذر تشغيل الفيديو، حاول مرة أخرى",Toast.LENGTH_LONG).show();}@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED){getSharedPreferences("player_resume",0).edit().clear().apply();prepareNextEpisode();}}});
        player.prepare();
        if(resumePosition>0)player.seekTo(resumePosition);
        player.setPlayWhenReady(autoplay||resumePlaying);
        player.setPlaybackParameters(new PlaybackParameters(getSharedPreferences("settings",0).getFloat("playback_speed",1f)));
        updatePipParams();
    }

    /** Uses the dashboard-provided backup once only; it never retries arbitrary URLs. */
    private boolean switchToFallback(){
        if(fallbackAttempted||!isTrustedMediaUrl(fallbackMediaUrl)||player==null)return false;
        fallbackAttempted=true;
        mediaUrl=fallbackMediaUrl;
        fallbackMediaUrl="";
        resumePosition=0;
        selectAutomaticQuality(false);
        player.setMediaItem(MediaItem.fromUri(mediaUrl));
        player.prepare();
        player.setPlayWhenReady(true);
        Toast.makeText(this,"جارٍ التبديل إلى المصدر الاحتياطي…",Toast.LENGTH_SHORT).show();
        return true;
    }

    private void updatePipParams() {
        if(Build.VERSION.SDK_INT<26||!pipEnabled)return;
        PictureInPictureParams.Builder builder=new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9));
        if(Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(true).setSeamlessResizeEnabled(true);
        setPictureInPictureParams(builder.build());
    }

    @Override public void onUserLeaveHint() {
        enterPip();
        super.onUserLeaveHint();
    }

    @Override public void onBackPressed() {
        cancelNextEpisode();
        super.onBackPressed();
    }

    @Override public void onPictureInPictureModeChanged(boolean active, Configuration config) {
        super.onPictureInPictureModeChanged(active,config);
        if(active)pipTransition=false;
        playerView.setUseController(!active);
    }

    @Override protected void onStop() {
        if(Build.VERSION.SDK_INT<26||(!isInPictureInPictureMode()&&!pipTransition))releasePlayer();
        super.onStop();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private void releasePlayer() {
        cancelNextEpisode();
        if(player==null)return;
        saveResumePosition();
        resumePosition=Math.max(0,player.getCurrentPosition());
        resumePlaying=player.isPlaying();
        player.release();
        player=null;
        playerView.setPlayer(null);
    }

    private void saveResumePosition(){
        if(player==null||mediaUrl==null||mediaUrl.trim().isEmpty())return;
        long position=Math.max(0,player.getCurrentPosition());long duration=player.getDuration();
        SharedPreferences.Editor edit=getSharedPreferences("player_resume",0).edit();
        if(duration>0&&position>=duration-15_000L)edit.clear();
        else edit.putString("url",mediaUrl).putString("title",mediaTitle==null?"الفهد TV":mediaTitle).putString("image",mediaImage==null?"":mediaImage).putLong("position",position).putLong("duration",duration);
        edit.apply();
    }

    private void enableAutoRotation(){
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        Toast.makeText(this,"الدوران التلقائي مفعّل",Toast.LENGTH_SHORT).show();
    }

    private void showPlayerOptions(){
        String autoNextLabel=autoNextEnabled?"إيقاف الحلقة التالية تلقائيًا":"تشغيل الحلقة التالية تلقائيًا";
        String lockLabel=controlsLocked?"إلغاء قفل الشاشة":"قفل الشاشة";
        String[] options={"سرعة التشغيل","الترجمة","تخطي المقدمة (+90 ثانية)",lockLabel,autoNextLabel};
        new AlertDialog.Builder(this).setTitle("إعدادات المشغّل").setItems(options,(dialog,which)->{
            if(which==0)showSpeedPicker();
            else if(which==1)showSubtitlePicker();
            else if(which==2)skipIntro();
            else if(which==3)setControlsLocked(!controlsLocked);
            else {autoNextEnabled=!autoNextEnabled;getSharedPreferences("settings",0).edit().putBoolean("auto_next",autoNextEnabled).apply();Toast.makeText(this,autoNextEnabled?"الحلقة التالية ستعمل تلقائيًا":"تم إيقاف التشغيل التلقائي",Toast.LENGTH_SHORT).show();}
        }).setNegativeButton("إلغاء",null).show();
    }

    private void showSpeedPicker(){
        if(player==null)return;
        final float[] speeds={0.75f,1f,1.25f,1.5f,2f};
        String[] labels={"0.75×","عادي 1×","1.25×","1.5×","2×"};
        float selected=player.getPlaybackParameters().speed;int checked=1;
        for(int index=0;index<speeds.length;index++)if(Math.abs(speeds[index]-selected)<.01f){checked=index;break;}
        new AlertDialog.Builder(this).setTitle("سرعة التشغيل").setSingleChoiceItems(labels,checked,(dialog,which)->{
            player.setPlaybackParameters(new PlaybackParameters(speeds[which]));
            getSharedPreferences("settings",0).edit().putFloat("playback_speed",speeds[which]).apply();
            Toast.makeText(this,"السرعة "+labels[which],Toast.LENGTH_SHORT).show();dialog.dismiss();
        }).setNegativeButton("إلغاء",null).show();
    }

    private static final class TrackOption {
        final TrackGroup group;final int trackIndex;final String label;
        TrackOption(TrackGroup group,int trackIndex,String label){this.group=group;this.trackIndex=trackIndex;this.label=label;}
    }

    private void showSubtitlePicker(){
        if(player==null){Toast.makeText(this,"جارٍ تجهيز المشغّل…",Toast.LENGTH_SHORT).show();return;}
        List<TrackOption> subtitles=availableSubtitleOptions();
        if(subtitles.isEmpty()){Toast.makeText(this,"لا توجد ترجمة ضمن هذا المصدر",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[subtitles.size()+2];labels[0]="إيقاف الترجمة";labels[1]="تلقائي";
        for(int index=0;index<subtitles.size();index++)labels[index+2]=subtitles.get(index).label;
        new AlertDialog.Builder(this).setTitle("الترجمة").setSingleChoiceItems(labels,1,(dialog,which)->{
            if(which==0)disableSubtitles();else if(which==1)selectAutomaticSubtitles();else selectSubtitle(subtitles.get(which-2));
            dialog.dismiss();
        }).setNegativeButton("إلغاء",null).show();
    }

    private List<TrackOption> availableSubtitleOptions(){
        ArrayList<TrackOption> result=new ArrayList<>();if(player==null)return result;
        for(Tracks.Group group:player.getCurrentTracks().getGroups()){
            if(group.getType()!=C.TRACK_TYPE_TEXT)continue;TrackGroup mediaGroup=group.getMediaTrackGroup();
            for(int index=0;index<group.length;index++){
                if(!group.isTrackSupported(index))continue;Format format=group.getTrackFormat(index);
                String label=format.label;if(label==null||label.trim().isEmpty())label=format.language;
                if(label==null||label.trim().isEmpty())label="ترجمة "+(result.size()+1);
                result.add(new TrackOption(mediaGroup,index,label));
            }
        }
        return result;
    }

    private void disableSubtitles(){
        if(player==null)return;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());Toast.makeText(this,"تم إيقاف الترجمة",Toast.LENGTH_SHORT).show();
    }
    private void selectAutomaticSubtitles(){
        if(player==null)return;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).build());Toast.makeText(this,"اختيار الترجمة التلقائي مفعّل",Toast.LENGTH_SHORT).show();
    }
    private void selectSubtitle(TrackOption option){
        if(player==null)return;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).clearOverridesOfType(C.TRACK_TYPE_TEXT).addOverride(new TrackSelectionOverride(option.group,option.trackIndex)).build());Toast.makeText(this,"تم اختيار "+option.label,Toast.LENGTH_SHORT).show();
    }

    private void skipIntro(){
        if(player==null)return;long duration=player.getDuration();long target=player.getCurrentPosition()+90_000L;if(duration>0)target=Math.min(duration,target);player.seekTo(target);showSeekHint("تخطي المقدمة +90ث");
    }

    private void setControlsLocked(boolean locked){
        controlsLocked=locked;
        if(locked){playerView.hideController();if(topOverlay!=null)topOverlay.setVisibility(View.GONE);showUnlockButton();Toast.makeText(this,"تم قفل الشاشة",Toast.LENGTH_SHORT).show();}
        else {if(unlockButton!=null)unlockButton.setVisibility(View.GONE);playerView.showController();if(topOverlay!=null)topOverlay.setVisibility(View.VISIBLE);Toast.makeText(this,"تم إلغاء قفل الشاشة",Toast.LENGTH_SHORT).show();}
    }
    private void showUnlockButton(){
        if(unlockButton==null)return;unlockButton.setVisibility(View.VISIBLE);unlockButton.removeCallbacks(hideUnlockButton);unlockButton.postDelayed(hideUnlockButton,1800);
    }
    private final Runnable hideUnlockButton=()->{if(controlsLocked&&unlockButton!=null)unlockButton.setVisibility(View.GONE);};

    private static final class QualityOption {
        final TrackGroup group;
        final int trackIndex;
        final int height;
        final int bitrate;
        final String label;
        QualityOption(TrackGroup group,int trackIndex,int height,int bitrate,String label){this.group=group;this.trackIndex=trackIndex;this.height=height;this.bitrate=bitrate;this.label=label;}
    }

    private void showQualityPicker(){
        if(player==null){Toast.makeText(this,"جارٍ تجهيز المشغّل…",Toast.LENGTH_SHORT).show();return;}
        List<QualityOption> options=availableQualityOptions();
        if(options.isEmpty()){
            Toast.makeText(this,"هذا المصدر يوفّر جودة واحدة فقط",Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels=new String[options.size()+1];
        labels[0]="تلقائي — أفضل جودة حسب الإنترنت";
        for(int i=0;i<options.size();i++)labels[i+1]=options.get(i).label;
        new AlertDialog.Builder(this)
            .setTitle("جودة الفيديو")
            .setSingleChoiceItems(labels,0,(dialog,which)->{
                if(which==0)selectAutomaticQuality(true);else selectQuality(options.get(which-1));
                dialog.dismiss();
            })
            .setNegativeButton("إلغاء",null)
            .show();
    }

    private List<QualityOption> availableQualityOptions(){
        ArrayList<QualityOption> result=new ArrayList<>();
        if(player==null)return result;
        Tracks tracks=player.getCurrentTracks();
        for(Tracks.Group group:tracks.getGroups()){
            if(group.getType()!=C.TRACK_TYPE_VIDEO)continue;
            TrackGroup mediaGroup=group.getMediaTrackGroup();
            for(int index=0;index<group.length;index++){
                if(!group.isTrackSupported(index))continue;
                Format format=group.getTrackFormat(index);
                int height=format.height;
                int bitrate=format.bitrate;
                String label=height>0?height+"p":bitrate>0?Math.max(1,bitrate/1_000_000)+" Mbps":"جودة بديلة";
                boolean duplicate=false;
                for(QualityOption known:result)if(known.label.equals(label)){duplicate=true;break;}
                if(!duplicate)result.add(new QualityOption(mediaGroup,index,height,bitrate,label));
            }
        }
        Collections.sort(result,new Comparator<QualityOption>(){ @Override public int compare(QualityOption first,QualityOption second){int heightOrder=Integer.compare(second.height,first.height);return heightOrder!=0?heightOrder:Integer.compare(second.bitrate,first.bitrate);} });
        return result;
    }

    private void selectQuality(QualityOption option){
        if(player==null)return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,false)
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .addOverride(new TrackSelectionOverride(option.group,option.trackIndex))
            .build());
        Toast.makeText(this,"تم اختيار "+option.label,Toast.LENGTH_SHORT).show();
    }

    private void selectAutomaticQuality(boolean showMessage){
        if(player==null)return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,false)
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build());
        if(showMessage)Toast.makeText(this,"اختيار الجودة التلقائي مفعّل",Toast.LENGTH_SHORT).show();
    }

    private void prepareNextEpisode(){
        if(!autoNextEnabled)return;
        if(isTrustedMediaUrl(nextMediaUrl)){showNextEpisodeCard();return;}
        if(nextResolverUrl==null||nextResolverUrl.trim().isEmpty()||nextResolveInFlight)return;
        final String resolvingLink=nextResolverUrl;
        nextResolveInFlight=true;
        if(nextEpisodeText!=null){nextEpisodeText.setText("جارٍ تحضير الحلقة التالية…");nextEpisodeCard.setVisibility(View.VISIBLE);}
        new Thread(()->{
            String resolved=resolveNextMedia(resolvingLink);
            runOnUiThread(()->{
                nextResolveInFlight=false;
                if(!isFinishing()&&resolvingLink.equals(nextResolverUrl)&&player!=null&&isTrustedMediaUrl(resolved)){nextMediaUrl=resolved;showNextEpisodeCard();}
                else if(nextEpisodeCard!=null)nextEpisodeCard.setVisibility(View.GONE);
            });
        },"alfahd-next-episode").start();
    }

    private String resolveNextMedia(String pageUrl){
        HttpURLConnection connection=null;
        try{
            String endpoint=RESOLVER_API+"?url="+URLEncoder.encode(pageUrl,"UTF-8");
            connection=(HttpURLConnection)new URL(endpoint).openConnection();connection.setConnectTimeout(10_000);connection.setReadTimeout(18_000);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","AlFahdTV/3.0 Android");
            if(connection.getResponseCode()<200||connection.getResponseCode()>299)return "";
            java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()));
            StringBuilder body=new StringBuilder();String line;
            while((line=reader.readLine())!=null)body.append(line);
            reader.close();
            JSONObject payload=new JSONObject(body.toString());return payload.optString("status").equals("success")?payload.optString("media_src"):"";
        }catch(Exception ignored){return "";}finally{if(connection!=null)connection.disconnect();}
    }

    private void showNextEpisodeCard(){
        if(!autoNextEnabled||!isTrustedMediaUrl(nextMediaUrl)||nextEpisodeCard==null)return;
        cancelNextEpisodeTimer();nextEpisodeCard.setVisibility(View.VISIBLE);final int[] seconds={8};
        nextEpisodeRunnable=new Runnable(){@Override public void run(){
            if(nextEpisodeText==null)return;
            String upcoming=nextMediaTitle==null||nextMediaTitle.trim().isEmpty()?"":nextMediaTitle;
            nextEpisodeText.setText("الحلقة التالية "+upcoming+"\nستبدأ خلال "+seconds[0]+" ثوانٍ");
            if(seconds[0]--<=0){advanceToNextEpisode();return;}uiHandler.postDelayed(this,1_000);
        }};nextEpisodeRunnable.run();
    }

    private void cancelNextEpisodeTimer(){if(nextEpisodeRunnable!=null){uiHandler.removeCallbacks(nextEpisodeRunnable);nextEpisodeRunnable=null;}}
    private void cancelNextEpisode(){cancelNextEpisodeTimer();if(nextEpisodeCard!=null)nextEpisodeCard.setVisibility(View.GONE);nextResolverUrl="";}
    private void advanceToNextEpisode(){
        if(!isTrustedMediaUrl(nextMediaUrl)||player==null){cancelNextEpisode();return;}
        cancelNextEpisodeTimer();if(nextEpisodeCard!=null)nextEpisodeCard.setVisibility(View.GONE);
        mediaUrl=nextMediaUrl;fallbackMediaUrl=nextFallbackMediaUrl;mediaTitle=nextMediaTitle==null||nextMediaTitle.trim().isEmpty()?"الحلقة التالية":nextMediaTitle;resumePosition=0;fallbackAttempted=false;nextMediaUrl="";nextFallbackMediaUrl="";nextResolverUrl="";
        if(playerTitle!=null)playerTitle.setText(mediaTitle);selectAutomaticQuality(false);player.setMediaItem(MediaItem.fromUri(mediaUrl));player.prepare();player.setPlayWhenReady(true);Toast.makeText(this,"تشغيل "+mediaTitle,Toast.LENGTH_SHORT).show();
    }

    private void showSeekHint(String text){
        if(seekHint==null)return;
        seekHint.setText(text);seekHint.setVisibility(View.VISIBLE);seekHint.removeCallbacks(hideSeekHint);seekHint.postDelayed(hideSeekHint,650);
    }
    private final Runnable hideSeekHint=()->{if(seekHint!=null)seekHint.setVisibility(View.GONE);};
    private void handleOverlayTouch(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP)return;
        if(controlsLocked){showUnlockButton();return;}
        long now=SystemClock.uptimeMillis();
        boolean doubleTap=lastTapAt>0&&now-lastTapAt<700&&Math.abs(event.getX()-lastTapX)<180;
        if(doubleTap){
            lastTapAt=0;
            if(player!=null){long delta=event.getX()<playerView.getWidth()/2f?-10_000L:10_000L;long duration=player.getDuration();long target=Math.max(0,duration>0?Math.min(duration,player.getCurrentPosition()+delta):player.getCurrentPosition()+delta);player.seekTo(target);showSeekHint(delta<0?"−10 ثواني":"+10 ثواني");}
        }else{
            lastTapAt=now;lastTapX=event.getX();playerView.showController();if(topOverlay!=null)topOverlay.setVisibility(View.VISIBLE);playerView.postDelayed(()->lastTapAt=0,850);
        }
    }

    private boolean enterPip() {
        if(Build.VERSION.SDK_INT<26||!pipEnabled||player==null||!player.isPlaying()||isInPictureInPictureMode())return false;
        pipTransition=true;
        try{boolean entered=enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());if(!entered)pipTransition=false;return entered;}catch(Exception ignored){pipTransition=false;return false;}
    }
}
