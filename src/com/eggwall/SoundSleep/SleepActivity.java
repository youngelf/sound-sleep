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

package com.eggwall.SoundSleep;

import android.app.Activity;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Activity that allows playing music or white noise while showing a big clock.
 */
public class SleepActivity extends Activity {
    /** Handler associated with the main thread for posting runnables. */
    private final Handler mHandler = new Handler();
    /** The width of our entire window. */
    private int mWidth;
    /** The height of our entire window*/
    private int mHeight;
    /** Delay which adjusts ongoing clock and icon location changes. */
    private static final int DELAY = 10 * 60 * 1000;
    /** Initial delay to change clock and icon location immediately after application startup. */
    private static final int INITIAL_DELAY = 500;

    /** Counts up to 10, to make the icons maximally dark. At 0, icons are at maximal brightness. */
    private int mAlphaDecrement = 1;

    /** The SDK version, stored off because we read it everywhere. */
    private final static int SDK = Build.VERSION.SDK_INT;
    /** The width, height of the cloud icon. */
    private Pair cloudSize = null;
    /** The width, height of the musical note icon. */
    private Pair noteSize = null;

    /** The state the application is currently in. */
    private int mState = AudioService.SILENCE;
    /** Key to store {@link #mState} in a bundle. */
    private static String STATE_KEY = "state-key";

    /** Receiver that accepts local broadcasts from the service to update the UI. */
    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            mState = AudioService.messageToType.get(action);
            setIconFromState(mState);
        }
    };

    /**
     * Sets the icons from the current AudioService state.
     * @param state an integer: {@link AudioService#MUSIC}, {@link AudioService#WHITE_NOISE}, or
     *              {@link AudioService#SILENCE} which determines what the {@link AudioService} is currently doing.
     */
    private void setIconFromState(int state) {
        final ImageView cloud = (ImageView) findViewById(R.id.cloud);
        final ImageView note = (ImageView) findViewById(R.id.note);
        switch (state) {
            case AudioService.MUSIC:
                cloud.setImageResource(R.drawable.rain);
                note.setImageResource(R.drawable.pause);
                break;
            case AudioService.WHITE_NOISE:
                note.setImageResource(R.drawable.music);
                cloud.setImageResource(R.drawable.pause);
                break;
            case AudioService.SILENCE:
                cloud.setImageResource(R.drawable.rain);
                note.setImageResource(R.drawable.music);
                break;
        }
    }

    /**
     * Changes the clock and the icon location and posts itself after a delay set to {@value #DELAY} milliseconds.
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
        final int x = (int)(Math.random()*(mWidth - v.getMeasuredWidth()));
        final int y = (int)(Math.random()*(mHeight- v.getMeasuredHeight()));
        if (SDK >= 12) {
            v.animate().x(x).y(y);
        } else {
            v.setPadding(x, y, 0, 0);
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
        if (cloudSize == null) {
            final View cloud = findViewById(R.id.cloud);
            cloudSize = new Pair(cloud.getMeasuredWidth(), cloud.getMeasuredHeight());
        }
        if (noteSize == null) {
            final View note = findViewById(R.id.note);
            noteSize = new Pair(note.getMeasuredWidth(), note.getMeasuredHeight());
        }
    }

    /**
     * Moves the icons to some random location.
     */
    private void changeIconLocation() {
        populateTopLevelDimen();
        setGlobalScreenSettings();
        if (cloudSize == null || noteSize == null) {
            return;
        }
        final double locationX = Math.random();
        // The top half of the screen is for the note.
        final int noteY = 0;
        // The bottom half of the screen is for white noise.
        final int cloudY = mHeight - cloudSize.mSecond;
        // The cloud and the note mirror each other on opposite sides to
        // increase visual separation.
        final int cloudX = (int) (locationX * (mWidth - cloudSize.mFirst));
        final float newCloudAlpha = (float)(.35-(mAlphaDecrement/100.0));
        final float newNoteAlpha = (float) (.50 - (mAlphaDecrement / 100.0));
        final int noteX = (int) ((1 - locationX) * (mWidth - noteSize.mFirst));
        mAlphaDecrement += 5;
        if (mAlphaDecrement > 20) {
            mAlphaDecrement = 20;
        }
        final View cloud = findViewById(R.id.cloud);
        final View note = findViewById(R.id.note);
        if (SDK >= 11) {
            cloud.setY(cloudY);
            cloud.animate().x(cloudX).alpha(newCloudAlpha);
            note.setY(noteY);
            note.animate().x(noteX).alpha(newNoteAlpha);
        } else {
            cloud.setPadding(cloudX, cloudY, 0, 0);
            note.setPadding(noteX, noteY, 0, 0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_KEY, mState);
        super.onSaveInstanceState(outState);
    }

    /**
     * Sets the visibility of the icons back to full brightness.
     */
    void resetAlphaDecrement() {
        if (SDK < 11) {
            return;
        }
        mAlphaDecrement = 0;
        final View cloud = findViewById(R.id.cloud);
        final View note = findViewById(R.id.note);
        cloud.animate().alpha((float) .35);
        note.animate().alpha((float) .50);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final LocalBroadcastManager m = LocalBroadcastManager.getInstance(this);
        m.unregisterReceiver(mMessageReceiver);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Go full screen.
        if (SDK >= 11) {
            (getActionBar()).hide();
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        final int fullscreen = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setFlags(fullscreen, fullscreen);
        setContentView(R.layout.main);
        postClockChange(INITIAL_DELAY);
        setGlobalScreenSettings();
        final LocalBroadcastManager m = LocalBroadcastManager.getInstance(this);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioService.MESSAGE_SILENCE);
        filter.addAction(AudioService.MESSAGE_MUSIC);
        filter.addAction(AudioService.MESSAGE_WHITE_NOISE);
        m.registerReceiver(mMessageReceiver, filter);

        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt(STATE_KEY, AudioService.SILENCE);
        }
        // Ask the service for the current state. It will send a broadcast with the current state.
        startPlayingResource(AudioService.GET_STATUS);
        setIconFromState(mState);
    }
    // Null diff

    /**
     * Set the full screen view, and also request a wake lock.
     */
    private void setGlobalScreenSettings() {
        final View topLevel = findViewById(R.id.toplevel);
        // Hide the System status bar
        if (SDK >= 11) {
            topLevel.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
        // Keep the screen always on, irrespective of power state.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void postClockChange(int delay) {
        mHandler.postDelayed(mChangeClockLocation, delay);
    }

    /**
     * Start/Stop white noise. This method is called directly from the layout file (onClick=), and thus appears unused.
     * @param unused the view that got this click event
     */
    @SuppressWarnings("unused")
    public void whiteNoisePressed(View unused) {
        startPlayingResource(AudioService.WHITE_NOISE);
    }

    /**
     * Start/Stop music. This method is called directly from the layout file (onClick=), and thus appears unused.
     * @param unused the view that got this click event
     */
    @SuppressWarnings("unused")
    public void musicPressed(View unused) {
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