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

package com.eggwall.soundsleep;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Activity that allows playing music or white noise while showing a big clock.
 */
public class SleepActivity extends Activity {
    /** Handler associated with the main thread for posting runnables. */
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** The width of our entire window. */
    private int mWidth;
    /** The height of our entire window*/
    private int mHeight;
    /** Delay which adjusts ongoing clock and icon location changes. */
    private static final int DELAY = 10 * 60 * 1000;
    /** Initial delay to change clock and icon location immediately after application startup. */
    private static final int INITIAL_DELAY = 500;
    /** Request code for audio storage runtime permission. */
    private static final int REQUEST_AUDIO_PERMISSION = 1001;

    /** Counts up to 10, to make the icons maximally dark. At 0, icons are at maximal brightness. */
    private int mAlphaDecrement = 1;

    /** The SDK version, stored off because we read it everywhere. */
    private static final int SDK = Build.VERSION.SDK_INT;
    /** The width, height of the cloud icon. */
    private Pair<Integer, Integer> cloudSize = null;
    /** The width, height of the musical note icon. */
    private Pair<Integer, Integer> noteSize = null;

    /** The state the application is currently in. */
    private int mState = AudioService.SILENCE;
    /** Key to store {@link #mState} in a bundle. */
    private static final String STATE_KEY = "state-key";
    /** Whether we have already prompted for audio read permission in this session. */
    private boolean mPermissionRequested = false;

    /** Detector used to identify and ignore upward navigation swipes from the bottom of the screen. */
    private GestureDetector mGestureDetector;
    /** Minimum distance in pixels before a touch can wander before being treated as a scroll/swipe. */
    private int mTouchSlop;
    /** True if the current touch gesture is an upward swipe from the bottom of the screen. */
    private boolean mIgnoreUpSwipe = false;
    /** True once an ongoing touch target has been cancelled for an ignored upward swipe. */
    private boolean mSwipeCancelled = false;
    /** X coordinate where the current touch gesture started. */
    private float mDownX;
    /** Y coordinate where the current touch gesture started. */
    private float mDownY;

    /**
     * Gesture listener that detects and ignores upward swipes starting from the bottom of the
     * screen, which occur when the user swipes up to go Home (up-flick) or to open Recent Apps
     * (slow up-swipe).
     */
    private final GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    mIgnoreUpSwipe = false;
                    mSwipeCancelled = false;
                    if (e != null) {
                        mDownX = e.getX();
                        mDownY = e.getY();
                    }
                    return false;
                }

                @Override
                public boolean onScroll(
                        MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (isUpSwipeFromBottom(e1, e2)) {
                        mIgnoreUpSwipe = true;
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean onFling(
                        MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (isUpSwipeFromBottom(e1, e2) && velocityY < 0) {
                        mIgnoreUpSwipe = true;
                        return true;
                    }
                    return false;
                }
            };

    /**
     * Returns true if the gesture between {@code e1} and {@code e2} started in the bottom half of
     * the screen and moved predominantly upward past the touch slop threshold.
     */
    private boolean isUpSwipeFromBottom(MotionEvent e1, MotionEvent e2) {
        if (e2 == null) {
            return false;
        }
        populateTopLevelDimen();
        final float startX = (e1 != null) ? e1.getX() : mDownX;
        final float startY = (e1 != null) ? e1.getY() : mDownY;
        final View topLevel = findViewById(R.id.toplevel);
        final int screenHeight = (topLevel != null && topLevel.getHeight() > 0)
                ? topLevel.getHeight()
                : mHeight;
        if (screenHeight > 0 && startY < (screenHeight / 2.0f)) {
            return false;
        }
        final float dy = startY - e2.getY();
        final float dx = e2.getX() - startX;
        return dy > mTouchSlop && dy > Math.abs(dx);
    }

    /** Receiver that accepts broadcasts from the service to update the UI. */
    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final Integer state = AudioService.messageToType.get(action);
            if (state != null) {
                mState = state;
                setIconFromState(mState);
            }
        }
    };

    /**
     * Sets the icons from the current AudioService state.
     * @param state an integer: {@link AudioService#MUSIC}, {@link AudioService#WHITE_NOISE}, or
     *              {@link AudioService#SILENCE} which determines what the {@link AudioService} is currently doing.
     */
    private void setIconFromState(int state) {
        final ImageView cloud = findViewById(R.id.cloud);
        final ImageView note = findViewById(R.id.note);
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
        v.animate().x(x).y(y);
    }

    /**
     * Calculates the dimension of the entire window. Safe if called repeatedly.
     */
    private void populateTopLevelDimen() {
        final View topLevel = findViewById(R.id.toplevel);
        if (topLevel != null) {
            final int width = topLevel.getWidth() > 0 ? topLevel.getWidth() : topLevel.getRight();
            final int height = topLevel.getHeight() > 0 ? topLevel.getHeight() : topLevel.getBottom();
            if (width > 0) {
                mWidth = width;
            }
            if (height > 0) {
                mHeight = height;
            }
        }
        if (cloudSize == null) {
            final View cloud = findViewById(R.id.cloud);
            cloudSize = new Pair<>(cloud.getMeasuredWidth(), cloud.getMeasuredHeight());
        }
        if (noteSize == null) {
            final View note = findViewById(R.id.note);
            noteSize = new Pair<>(note.getMeasuredWidth(), note.getMeasuredHeight());
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
        final int cloudY = mHeight - cloudSize.second;
        // The cloud and the note mirror each other on opposite sides to
        // increase visual separation.
        final int cloudX = (int) (locationX * (mWidth - cloudSize.first));
        final float newCloudAlpha = (float)(.35-(mAlphaDecrement/100.0));
        final float newNoteAlpha = (float) (.50 - (mAlphaDecrement / 100.0));
        final int noteX = (int) ((1 - locationX) * (mWidth - noteSize.first));
        mAlphaDecrement += 5;
        if (mAlphaDecrement > 20) {
            mAlphaDecrement = 20;
        }
        final View cloud = findViewById(R.id.cloud);
        final View note = findViewById(R.id.note);
        cloud.setY(cloudY);
        cloud.animate().x(cloudX).alpha(newCloudAlpha);
        note.setY(noteY);
        note.animate().x(noteX).alpha(newNoteAlpha);
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
        mAlphaDecrement = 0;
        final View cloud = findViewById(R.id.cloud);
        final View note = findViewById(R.id.note);
        cloud.animate().alpha((float) .35);
        note.animate().alpha((float) .50);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mChangeClockLocation);
        unregisterReceiver(mMessageReceiver);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Go full screen and remove the title/action bar.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (SDK < Build.VERSION_CODES.R) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.main);
        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        mGestureDetector = new GestureDetector(this, mGestureListener);
        postClockChange(INITIAL_DELAY);
        setGlobalScreenSettings();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioService.MESSAGE_SILENCE);
        filter.addAction(AudioService.MESSAGE_MUSIC);
        filter.addAction(AudioService.MESSAGE_WHITE_NOISE);
        if (SDK >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mMessageReceiver, filter);
        }

        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt(STATE_KEY, AudioService.SILENCE);
        }
        // Ask the service for the current state. It will send a broadcast with the current state.
        sendRequest(AudioService.GET_STATUS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setGlobalScreenSettings();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mIgnoreUpSwipe = false;
            mSwipeCancelled = false;
            mDownX = ev.getX();
            mDownY = ev.getY();
        }
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }
        if (mIgnoreUpSwipe) {
            if (!mSwipeCancelled) {
                mSwipeCancelled = true;
                final MotionEvent cancelEvent = MotionEvent.obtain(ev);
                cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancelEvent);
                cancelEvent.recycle();
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setGlobalScreenSettings();
        }
    }

    /**
     * Set the full screen view (hiding status bar, menu/action bar, and on-screen
     * home/recents navigation buttons), and disable screen timeout while running.
     */
    @SuppressWarnings("deprecation")
    private void setGlobalScreenSettings() {
        final Window window = getWindow();
        // Keep the screen always on, irrespective of power state.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide the System status bar and on-screen navigation bar (home/recents buttons).
        if (SDK >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            final WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            final View topLevel = findViewById(R.id.toplevel);
            if (topLevel != null) {
                topLevel.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        }
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
        sendRequest(AudioService.WHITE_NOISE);
    }

    /**
     * Start/Stop music. This method is called directly from the layout file (onClick=), and thus appears unused.
     * @param unused the view that got this click event
     */
    @SuppressWarnings("unused")
    public void musicPressed(View unused) {
        if (SDK >= Build.VERSION_CODES.M && !mPermissionRequested) {
            final String permission = (SDK >= Build.VERSION_CODES.TIRAMISU)
                    ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                mPermissionRequested = true;
                requestPermissions(new String[] { permission }, REQUEST_AUDIO_PERMISSION);
                return;
            }
        }
        sendRequest(AudioService.MUSIC);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            sendRequest(AudioService.MUSIC);
        }
    }

    /**
     * Send this request to the audio service.
     * @param command Either {@link AudioService#GET_STATUS}, or {@link AudioService#MUSIC} or
     * {@link AudioService#WHITE_NOISE}. Passing {@link AudioService#MUSIC} or {@link AudioService#WHITE_NOISE}
     *             twice is a signal to become silent.
     */
    private void sendRequest(int command) {
        // The user has touched the screen, show the icons a bit brighter.
        resetAlphaDecrement();
        final Intent i = new Intent(this, AudioService.class);
        i.putExtra(AudioService.REQUEST, command);
        startService(i);
    }
}
