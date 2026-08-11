package com.alfahdtv.app;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private String mediaUrl;
    private boolean pipEnabled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        mediaUrl=getIntent().getStringExtra("media_url");
        pipEnabled=getSharedPreferences("settings",0).getBoolean("pip",true);
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        setContentView(playerView);
        updatePipParams();
    }

    @Override protected void onStart() {
        super.onStart();
        if(player==null)preparePlayer();
    }

    private void preparePlayer() {
        if(mediaUrl==null||!mediaUrl.startsWith("https://")){finish();return;}
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(mediaUrl));
        player.prepare();
        player.play();
    }

    private void updatePipParams() {
        if(Build.VERSION.SDK_INT<26||!pipEnabled)return;
        PictureInPictureParams.Builder builder=new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9));
        if(Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(true).setSeamlessResizeEnabled(true);
        setPictureInPictureParams(builder.build());
    }

    @Override public void onUserLeaveHint() {
        if(Build.VERSION.SDK_INT>=26&&Build.VERSION.SDK_INT<31&&pipEnabled&&player!=null&&player.isPlaying()){
            try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception ignored){}
        }
        super.onUserLeaveHint();
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
}
