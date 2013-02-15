package com.eggwall.BabyMusic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Created with IntelliJ IDEA.
 * User: viki
 * Date: 2/14/13
 * Time: 11:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class AudioService extends Service implements MediaPlayer.OnErrorListener {
    MediaPlayer mPlayer;

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        Log.e("AudioService", "BabyMusic.AudioService encountered onError");
        // Propagate the error up.
        return false;
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        // Keep the CPU awake while playing music.
        mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        setForegroundService();
    }

    private void setForegroundService() {
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), BabyActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification();
        notification.tickerText = "Baby Music";
        notification.icon = R.drawable.ic_launcher;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.setLatestEventInfo(getApplicationContext(), "BabyMusic", "Playing", pi);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
