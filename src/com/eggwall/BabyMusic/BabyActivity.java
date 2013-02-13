package com.eggwall.BabyMusic;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

public class BabyActivity extends Activity {
    private MediaPlayer mPlayer;
    private static final int PLAYING_NOTHING = 0;
    private static final int MUSIC = 1;
    private static final int WHITE_NOISE = 2;
    /** Set to MUSIC or WHITE_NOISE */
    private int mTypePlaying = 0;
    private final Handler mHandler = new Handler();
    private int mWidth;
    private int mHeight;
    // Change clock location every minute
    private static final int DELAY = 1 * 60 * 1000;

    private final Runnable mChangeClockLocation = new Runnable() {
        @Override
        public void run() {
            changeClockLocation();
            postClockChange();
        }
    };

    /**
     * Moves the clock to some random location.
     */
    private final void changeClockLocation() {
        final View v = findViewById(R.id.clock);
        if (mWidth == 0) {
            final View topLevel = findViewById(R.id.toplevel);
            mWidth = topLevel.getRight();
        }
        if (mHeight == 0) {
            final View topLevel = findViewById(R.id.toplevel);
            mHeight = topLevel.getBottom();
        }
        final int x = (int)(Math.random()*(mWidth - v.getWidth()));
        v.setX(x);
        final int y = (int)(Math.random()*(mHeight- v.getHeight()));
        v.setY(y);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        // Go full screen.
        (getActionBar()).hide();
        // Hide the System status bar
        final View topLevel = findViewById(R.id.toplevel);
        topLevel.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        postClockChange();
    }

    private final void postClockChange() {
        mHandler.postDelayed(mChangeClockLocation, DELAY);
    }

    /**
     * Start white noise
     * @param unused the view that got this click event
     */
    public void startWhiteNoise(View unused) {
        startPlayingResource(R.raw.brownian_noise, WHITE_NOISE);
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
        startPlayingResource(R.raw.how_deep_is_the_ocean, MUSIC);
    }

    /**
     * Start playing the resource specified here.
     * @param id A resource like R.raw.music_file
     * @param type Either MUSIC, or WHITE_NOISE. Passing the same ID twice
     *             is a signal to stop playing music altogether.
     */
    private void startPlayingResource(int id, int type) {
        releasePlayer();
        // If the user hits the same button twice, just stop playing anything.
        if (mTypePlaying != type) {
            mTypePlaying = type;
            mPlayer = MediaPlayer.create(this, id);
            mPlayer.start();
        } else {
            mTypePlaying = PLAYING_NOTHING;
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