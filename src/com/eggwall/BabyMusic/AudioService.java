package com.eggwall.BabyMusic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;

/**
 * Runs the music in the background and holds a wake lock during the duration of music playing.
 */
public class AudioService extends Service implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {
    /** For logging */
    private static final String TAG = "AudioService";
    /**
     * The tag used to pass the music type. Can only be MUSIC or WHITE_NOISE
     */
    public static final String TYPE = "type";
    private MediaPlayer mPlayer;
    /** Stop playing any audio. */
    public static final int SILENCE = 0;
    /** Play music from the SD card */
    public static final int MUSIC = 1;
    /** Play standard white noise file (included in the application */
    public static final int WHITE_NOISE = 2;

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        releasePlayer();
        Log.e(TAG, "BabyMusic.AudioService encountered onError");
        // Propagate the error up.
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we don't get an extra (impossible), play white noise.
        final int typeOfResource = intent.getIntExtra("type", WHITE_NOISE);
        Log.d(TAG, "Got resource " + typeOfResource);
        startPlayingResource(typeOfResource);
        return 0;
    }

    /**
     * Start playing the resource specified here.
     *
     * @param type Either MUSIC, or WHITE_NOISE. Passing the same ID twice
     *             is a signal to stop playing music altogether.
     */
    private void startPlayingResource(int type) {
        releasePlayer();
        // If the user hits the same button twice, just stop playing anything.
        if (type == SILENCE) {
            // Nothing to do here. Just quit
            Log.d(TAG, "Stopping the music");
            return;
        }
        mPlayer = new MediaPlayer();
        // Keep the CPU awake while playing music.
        mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mPlayer.setOnPreparedListener(this);
        final int resourceToPlay;
        if (type == WHITE_NOISE) {
            Log.d(TAG, "Playing browninan noise");
            resourceToPlay = R.raw.noise;
        } else {
            // Try to open the SD card and read from there. If nothing is found, play the
            // default music.
            Log.d(TAG, "No SD card music found, playing default music");
            resourceToPlay = R.raw.all_of_me;
        }
        final AssetFileDescriptor d = getApplicationContext().getResources().openRawResourceFd(resourceToPlay);
        if (d == null) {
            Log.wtf(TAG, "Could not open the file to play");
        }
        try {
            mPlayer.setDataSource(d.getFileDescriptor(), d.getStartOffset(), d.getLength());
            d.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mPlayer.prepareAsync();
        mPlayer.setLooping(true);
    }

    /**
     * The idea here is to set the notification so that the service can always run. However, ths is not
     * happening correctly right now.
     */
    private void setForegroundService() {
        final PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), BabyActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification notification = new Notification();
        notification.tickerText = "Baby Music";
        notification.icon = R.drawable.ic_launcher;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.setLatestEventInfo(getApplicationContext(), "BabyMusic", "Playing", pi);
    }

    /**
     * Close the music player, if any, and remove our reference to it.
     */
    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "bye bye");
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        setForegroundService();
        mPlayer.start();
    }
}
