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
    /**
     * The tag used to pass the music type. Can only be MUSIC or WHITE_NOISE
     */
    public static final String TYPE = "type";
    private MediaPlayer mPlayer;
    public static final int SILENCE = 0;
    public static final int MUSIC = 1;
    public static final int WHITE_NOISE = 2;
    /**
     * Set to MUSIC or WHITE_NOISE
     */
    private int mTypePlaying = 0;

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        releasePlayer();
        Log.e("AudioService", "BabyMusic.AudioService encountered onError");
        // Propagate the error up.
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we don't get an extra (impossible), play white noise.
        final int typeOfResource = intent.getIntExtra("type", WHITE_NOISE);
        Log.d("AudioService", "Got resource " + typeOfResource);
        startPlayingResource(0, typeOfResource);
        return 0;
    }

    /**
     * Start playing the resource specified here.
     *
     * @param id   A resource like R.raw.music_file
     * @param type Either MUSIC, or WHITE_NOISE. Passing the same ID twice
     *             is a signal to stop playing music altogether.
     */
    private void startPlayingResource(int id, int type) {
        releasePlayer();
        // If the user hits the same button twice, just stop playing anything.
        if (mTypePlaying != type && type != SILENCE) {
            mTypePlaying = type;
            mPlayer = new MediaPlayer();
            // Keep the CPU awake while playing music.
            mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mPlayer.setOnPreparedListener(this);
            final int resourceToPlay;
            if (type == WHITE_NOISE) {
                Log.d("AudioService", "Playing browninan noise");
                resourceToPlay = R.raw.brownian_noise;
            } else {
                Log.d("AudioService", "Playing Eric Clapton");
                resourceToPlay = R.raw.how_deep_is_the_ocean;
            }
            final AssetFileDescriptor d = getApplicationContext().getResources().openRawResourceFd(resourceToPlay);
            if (d == null) {
                Log.wtf("AudioService", "Could not open the file to play");
            }
            try {
                mPlayer.setDataSource(d.getFileDescriptor(), d.getStartOffset(), d.getLength());
                d.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPlayer.prepareAsync();
            mPlayer.setLooping(true);
        } else {
            Log.d("AudioService", "Stopping the music");
            mTypePlaying = SILENCE;
        }
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

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        Log.d("AudioService", "bye bye");
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
