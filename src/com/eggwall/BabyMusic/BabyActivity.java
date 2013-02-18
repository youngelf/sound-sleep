/**
 Copyright 2013 Vikram Aggarwal

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package com.eggwall.BabyMusic;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Activity that allows playing music or white noise while showing a big clock.
 */
public class BabyActivity extends Activity {
    /** For logging */
    private static final String TAG = "BabyActivity";
    private final Handler mHandler = new Handler();
    private int mWidth;
    private int mHeight;
    // Change clock location every ten minutes
    private static final int DELAY = 10 * 60 * 1000;
    // Initial delay to go to a random position is much shorter
    private static final int INITIAL_DELAY = 500;

    // When this is 10, we are at total alpha decrement, and 0, we are at minimum.
    private int mAlphaDecrement = 1;

    private final int sdk = Build.VERSION.SDK_INT;
    /**
     * Changes the clock and the icon location and posts itself after a delay.
     */
    private final Runnable mChangeClockLocation = new Runnable() {
        @Override
        public void run() {
            changeClockLocation();
            changeIconLocation();
            postClockChange(DELAY);
        }
    };

    /**
     * Moves the clock to some random location.
     */
    private void changeClockLocation() {
        populateTopLevelDimen();
        final View v = findViewById(R.id.clock);
        final int x = (int)(Math.random()*(mWidth - v.getWidth()));
        final int y = (int)(Math.random()*(mHeight- v.getHeight()));
        if (sdk >= 12) {
            v.animate().x(x).y(y);
        } else {
            // Figure out how to do this.
//            v.setX(x);
  //          v.setY(y);
        }
    }

    /**
     * Calculates the dimension of the entire window. Safe if called repeatedly.
     */
    private void populateTopLevelDimen() {
        if (mWidth == 0) {
            final View topLevel = findViewById(R.id.toplevel);
            mWidth = topLevel.getRight();
        }
        if (mHeight == 0) {
            final View topLevel = findViewById(R.id.toplevel);
            mHeight = topLevel.getBottom();
        }
    }

    /**
     * Moves the icons to some random location.
     */
    private void changeIconLocation() {
        if (sdk < 11) {
            // TODO(viki): Figure out how to do this.
            return;
        }
        populateTopLevelDimen();
        setGlobalScreenSettings();
        final View cloud = findViewById(R.id.cloud);
        final View note = findViewById(R.id.note);
        final double locationX = Math.random();
        // The top half of the screen is for the note.
        note.setY(0);
        // The bottom half of the screen is for white noise.
        cloud.setY(mHeight - cloud.getHeight());
        // The cloud and the note mirror each other on opposite sides to
        // increase visual separation.
        cloud.animate().x((int)(locationX * (mWidth - cloud.getWidth()))).alpha((float)(.35-(mAlphaDecrement/100.0)));
        note.animate().x((int)((1-locationX) * (mWidth - note.getWidth()))).alpha((float)(.50-(mAlphaDecrement/100.0)));
        mAlphaDecrement += 5;
        if (mAlphaDecrement > 20) {
            mAlphaDecrement = 20;
        }
    }

    /**
     * Sets the visibility of the icons back to full brightness.
     */
    void resetAlphaDecrement() {
        if (sdk < 11) {
            return;
        }
        mAlphaDecrement = 0;
        final View cloud = findViewById(R.id.cloud);
        final View note = findViewById(R.id.note);
        cloud.animate().alpha((float) .35);
        note.animate().alpha((float) .50);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Go full screen.
        if (sdk >= 11) {
            (getActionBar()).hide();
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        final int fullscreen = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setFlags(fullscreen, fullscreen);
        setContentView(R.layout.main);
        postClockChange(INITIAL_DELAY);
        setGlobalScreenSettings();
    }

    /**
     * Set the full screen view, and also request a wake lock.
     */
    private void setGlobalScreenSettings() {
        final View topLevel = findViewById(R.id.toplevel);
        // Hide the System status bar
        if (sdk >= 11) {
            topLevel.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
        // Keep the screen always on, irrespective of power state.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void postClockChange(int delay) {
        mHandler.postDelayed(mChangeClockLocation, delay);
    }

    /**
     * Start white noise
     * @param unused the view that got this click event
     */
    @SuppressWarnings("unused")
    public void startWhiteNoise(View unused) {
        startPlayingResource(AudioService.WHITE_NOISE);
    }

    /**
     * Start white noise
     * @param unused the view that got this click event
     */
    @SuppressWarnings("unused")
    public void startMusic(View unused) {
        startPlayingResource(AudioService.MUSIC);
    }

    /**
     * Start playing the resource specified here.
     * @param type Either MUSIC, or WHITE_NOISE. Passing the same ID twice
     *             is a signal to stop playing music altogether.
     */
    private void startPlayingResource(int type) {
        // The user has touched the screen, show the icons a bit brighter.
        resetAlphaDecrement();
        // TODO(viki) Bad idea. We should use some resolution mechanism rather than bare name.
        final Intent i = new Intent(this, AudioService.class);
        // Play the music instructed.
        i.putExtra(AudioService.TYPE, type);
        startService(i);
    }
}