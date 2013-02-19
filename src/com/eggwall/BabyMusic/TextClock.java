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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The implementation of a text clock when the framework doesn't have any.
 */
public class TextClock extends TextView {
    private Calendar mTime;

    public TextClock(Context context) {
        super(context);
    }

    public TextClock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        createTime();
    }

    private final Runnable mTicker = new Runnable() {
        public void run() {
            onTimeChanged();
            final long now = SystemClock.uptimeMillis();
            final long next = now + (1000 - now % 1000);
            getHandler().postAtTime(mTicker, next);
        }
    };

    private void createTime() {
        mTime = Calendar.getInstance();
    }

    private void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setText(DateFormat.format("h:mm", mTime));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onTimeChanged();
    }
}
