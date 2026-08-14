package com.alfahdtv.app;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private String mediaUrl;
    private String mediaTitle;
    private boolean pipEnabled;
    private boolean autoplay;
    private boolean landscape;
    private TextView seekHint;
    private long lastTapAt;
    private float lastTapX;
    private LinearLayout topOverlay;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        mediaUrl=getIntent().getStringExtra("media_url");
        mediaTitle=getIntent().getStringExtra("media_title");
        pipEnabled=getSharedPreferences("settings",0).getBoolean("pip",true);
        autoplay=getSharedPreferences("settings",0).getBoolean("autoplay",true);
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        if(getSharedPreferences("settings",0).getBoolean("secure",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        playerView=(PlayerView)getLayoutInflater().inflate(R.layout.player_view,null);
        View videoSurface=playerView.getVideoSurfaceView();
        if(videoSurface!=null){videoSurface.setClickable(true);videoSurface.setFocusable(true);videoSurface.setOnTouchListener((view,event)->{handleOverlayTouch(event);return true;});}
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(3500);
        playerView.setControllerAutoShow(true);
        setContentView(buildPlayerLayout());
        updatePipParams();
    }

    private View buildPlayerLayout(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        seekHint=AppUi.text(this,"",18,Color.WHITE);seekHint.setGravity(Gravity.CENTER);seekHint.setTypeface(null,1);seekHint.setVisibility(View.GONE);seekHint.setBackground(AppUi.round(Color.argb(190,0,0,0),28,this));FrameLayout.LayoutParams hintParams=new FrameLayout.LayoutParams(dp(170),dp(64),Gravity.CENTER);root.addView(seekHint,hintParams);
        LinearLayout top=new LinearLayout(this);topOverlay=top;top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundResource(R.drawable.top_overlay);
        ImageButton back=playerButton(R.drawable.ic_player_back,"رجوع");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=AppUi.text(this,mediaTitle==null||mediaTitle.trim().isEmpty()?"الفهد TV":mediaTitle,16,Color.WHITE);title.setTypeface(null,1);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,dp(48),1);titleParams.setMargins(dp(9),0,dp(9),0);top.addView(title,titleParams);
        ImageButton rotate=playerButton(R.drawable.ic_player_rotate,landscape?"تصغير الشاشة":"تدوير وتكبير الشاشة");rotate.setOnClickListener(v->toggleOrientation());top.addView(rotate,new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(Build.VERSION.SDK_INT>=26&&pipEnabled){ImageButton pip=playerButton(R.drawable.ic_player_pip,"صورة داخل صورة");pip.setOnClickListener(v->enterPip());top.addView(pip,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,dp(72),Gravity.TOP);root.addView(top,topParams);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{
            top.setVisibility(View.VISIBLE);
        });
        return root;
    }

    private ImageButton playerButton(int icon,String description){ImageButton button=new ImageButton(this);button.setImageResource(icon);button.setContentDescription(description);button.setBackground(AppUi.round(Color.argb(105,20,21,27),24,this));button.setPadding(dp(10),dp(10),dp(10),dp(10));return button;}
    private int dp(int value){return AppUi.dp(this,value);}
    private boolean isTrustedMediaUrl(String value){if(value==null)return false;try{Uri uri=Uri.parse(value);String scheme=uri.getScheme(),host=uri.getHost();return "https".equalsIgnoreCase(scheme)||("http".equalsIgnoreCase(scheme)&&host!=null&&host.toLowerCase(java.util.Locale.ROOT).endsWith(".downet.net"));}catch(Exception ignored){return false;}}

    @Override protected void onStart() {
        super.onStart();
        if(player==null)preparePlayer();
    }

    private void preparePlayer() {
        if(!isTrustedMediaUrl(mediaUrl)){finish();return;}
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(mediaUrl));
        player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){Toast.makeText(PlayerActivity.this,"تعذر تشغيل الفيديو، حاول مرة أخرى",Toast.LENGTH_LONG).show();}});
        player.prepare();
        player.setPlayWhenReady(autoplay);
        updatePipParams();
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
        super.onBackPressed();
    }

    @Override public void onPictureInPictureModeChanged(boolean active, Configuration config) {
        super.onPictureInPictureModeChanged(active,config);
        playerView.setUseController(!active);
    }

    @Override protected void onStop() {
        if(Build.VERSION.SDK_INT<26||!isInPictureInPictureMode())releasePlayer();
        super.onStop();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private void releasePlayer() {
        if(player==null)return;
        player.release();
        player=null;
        playerView.setPlayer(null);
    }

    private void toggleOrientation(){
        landscape=!landscape;
        setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void showSeekHint(String text){
        if(seekHint==null)return;
        seekHint.setText(text);seekHint.setVisibility(View.VISIBLE);seekHint.removeCallbacks(hideSeekHint);seekHint.postDelayed(hideSeekHint,650);
    }
    private final Runnable hideSeekHint=()->{if(seekHint!=null)seekHint.setVisibility(View.GONE);};
    private void handleOverlayTouch(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP)return;
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
        try{return enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception ignored){return false;}
    }
}
