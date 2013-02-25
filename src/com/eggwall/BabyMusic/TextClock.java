/**
 * Copyright 2013 Vikram Aggarwal
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eggwall.BabyMusic;

import android.content.*;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The implementation of a text clock when the framework doesn't have any.
 */
public class TextClock extends TextView {
    /** True if this view is currently attached to the window. */
    private boolean mAttached = false;
    /** The current time, as displayed. */
    private Calendar mTime;
    /** The current timezone. */
    private String mTimeZone;

    /** Show hours : minutes only */
    private static final String FORMAT="h:mm";

    public TextClock(Context context) {
        super(context);
    }

    public TextClock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        createTime(null);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mTimeZone == null && Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                final String timeZone = intent.getStringExtra("time-zone");
                createTime(timeZone);
            }
            onTimeChanged();
        }
    };

    private final Runnable mTicker = new Runnable() {
        public void run() {
            onTimeChanged();
            final long now = SystemClock.uptimeMillis();
            final long next = now + (1000 - now % 1000);
            getHandler().postAtTime(mTicker, next);
        }
    };


    private void createTime(String timeZone) {
        if (timeZone != null) {
            mTime = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        } else {
            mTime = Calendar.getInstance();
        }
    }

    private void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setText(DateFormat.format(FORMAT, mTime));
    }

    /**
     * Sets the specified time zone to use in this clock. When the time zone
     * is set through this method, system time zone changes (when the user
     * sets the time zone in settings for instance) will be ignored.
     *
     * @param timeZone The desired time zone's ID as specified in {@link TimeZone}
     *                 or null to user the time zone specified by the user
     *                 (system time zone)
     *
     * @see java.util.TimeZone#getAvailableIDs()
     * @see TimeZone#getTimeZone(String)
     *
     * @attr ref android.R.styleable#TextClock_timeZone
     */
    public void setTimeZone(String timeZone) {
        mTimeZone = timeZone;
        createTime(timeZone);
        onTimeChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            registerReceiver();
        }
        onTimeChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            unregisterReceiver();
            getHandler().removeCallbacks(mTicker);
            mAttached = false;
        }
    }

    private void registerReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
    }

    private void unregisterReceiver() {
        getContext().unregisterReceiver(mIntentReceiver);
    }
}