package com.eggwall.BabyMusic;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class BabyActivity extends Activity {
    private MediaPlayer mPlayer;
    private static final int MUSIC = 1;
    private static final int WHITE_NOISE = 2;
    /** Set to MUSIC or WHITE_NOISE */
    private int mTypePlaying = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    /**
     * Start white noise
     * @param unused the view that got this click event
     */
    public void startWhiteNoise(View unused) {
        Log.d("viki", "Starting white noise");
        startResource(R.raw.brownian_noise, WHITE_NOISE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    /**
     * Start white noise
     * @param unused the view that got this click event
     */
    public void startMusic(View unused) {
        Log.d("viki", "Starting music");
        startResource(R.raw.how_deep_is_the_ocean, MUSIC);
    }

    private void startResource(int id, int type) {
        releasePlayer();
        // If the user hits the same button twice, just stop playing anything.
        if (mTypePlaying != type) {
            mTypePlaying = type;
            mPlayer = MediaPlayer.create(this, id);
            mPlayer.start();
        }
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}