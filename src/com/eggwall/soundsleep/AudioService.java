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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Runs the music in the background and holds a wake lock during the duration of music playing.
 */
public class AudioService extends Service implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    /** The namespace of our application, used for qualifying local messages. */
    private static final String NAMESPACE = "com.eggwall.soundsleep";

    /** For logging */
    private static final String TAG = "AudioService";
    /** The tag used to pass the request. Can only be {@link #GET_STATUS}, {@link #MUSIC}, or {@link #WHITE_NOISE}*/
    public static final String REQUEST = "request";

    /** Stop playing any audio. */
    public static final int SILENCE = 0;
    /** Broadcast message that says were were successful in starting silence. */
    public static final String MESSAGE_SILENCE = NAMESPACE + ".message.silence";

    /** Play music from the SD card */
    public static final int MUSIC = 1;
    /** Broadcast message that says were were successful in starting music. */
    public static final String MESSAGE_MUSIC = NAMESPACE + ".message.music";

    /** Play standard white noise file (included in the application */
    public static final int WHITE_NOISE = 2;
    /** Broadcast message that says were were successful in starting white noise. */
    public static final String MESSAGE_WHITE_NOISE = NAMESPACE + ".message.white-noise";

    /** Just return the current status without changing any state. */
    public static final int GET_STATUS = 3;

    /** Map to perform type -> message lookups */
    public static final String[] typeToMessage = {
            MESSAGE_SILENCE, MESSAGE_MUSIC, MESSAGE_WHITE_NOISE
    };
    /** Map to perform message -> type lookups. */
    public static final HashMap<String, Integer> messageToType = new HashMap<>(3);
    static {
        messageToType.put(MESSAGE_SILENCE, SILENCE);
        messageToType.put(MESSAGE_MUSIC, MUSIC);
        messageToType.put(MESSAGE_WHITE_NOISE, WHITE_NOISE);
    }

    /** This represents in invalid position in the list and also an invalid resource. */
    private static final int INVALID_POSITION = -1;
    /** Name of the directory in the main folder containing sleeping music */
    private static final String MUSIC_DIR = "sleeping";

    /** The ID for the global notification we post. */
    private static final int NOTIFICATION_ID = 1;
    /** Notification channel ID for Android O and above. */
    private static final String NOTIFICATION_CHANNEL_ID = "sound_sleep_playback";

    /** Single instance of random number generator */
    private final Random mRandom = new Random();
    /** The SDK version */
    private static final int SDK = Build.VERSION.SDK_INT;

    /** The object that actually plays the music on our behalf. */
    private MediaPlayer mPlayer;
    /** Set to {@link #SILENCE}, {@link #MUSIC}, or {@link #WHITE_NOISE}. */
    private int mTypePlaying = SILENCE;
    /** The actual directory that corresponds to the external SD card. */
    private File mMusicDir;
    /** Names of all the songs in legacy directory mode */
    private String[] mFilenames;
    /** Discovered MediaStore URIs for songs in the sleeping folder */
    private List<Uri> mTrackUris;
    /** The global manager for notifications */
    private NotificationManager mNotificationManager;
    /** System audio manager for handling audio focus */
    private AudioManager mAudioManager;
    /** AudioFocusRequest used on API 26+ */
    private AudioFocusRequest mAudioFocusRequest;

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        releasePlayer();
        Log.e(TAG, "SleepActivity.AudioService encountered onError");
        // Propagate the error up.
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        // If we don't get an extra (impossible), play white noise.
        final int typeOfResource = (intent != null)
                ? intent.getIntExtra(REQUEST, WHITE_NOISE)
                : WHITE_NOISE;
        // If this is a call to get the status, just return right here.
        if (typeOfResource == GET_STATUS) {
            postSuccessMessage(mTypePlaying);
            return START_NOT_STICKY;
        }
        if (mTypePlaying == typeOfResource || typeOfResource == SILENCE) {
            // Pressing the same button twice is an instruction to stop playing this music.
            mTypePlaying = SILENCE;
            stopSelf();
            return START_NOT_STICKY;
        }
        // Switch to the other type of music
        mTypePlaying = typeOfResource;
        releasePlayer();
        play(mTypePlaying);
        return START_NOT_STICKY;
    }

    /**
     * Posts a message back to the activity that it was successful in either playing music, in playing white
     * noise, or becoming silent.
     */
    private void postSuccessMessage(int actionSuccessful) {
        final Intent i = new Intent(typeToMessage[actionSuccessful]);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    /**
     * Start playing the resource specified here.
     *
     * @param type Either MUSIC, or WHITE_NOISE. Passing the same ID twice
     *             is a signal to stop playing music altogether.
     */
    private void play(int type) {
        requestAudioFocus();
        if (type == MUSIC) {
            final MediaPlayer player = tryStartingMusic();
            if (player != null) {
                mPlayer = player;
                mPlayer.prepareAsync();
                // onPrepared will get called when the media player is ready to play.
                return;
            }
        }
        // Either we weren't able to play custom music, or we were asked to play white noise.
        final int resourceToPlay;
        if (type == WHITE_NOISE) {
            Log.v(TAG, "Playing white noise.");
            resourceToPlay = R.raw.noise;
        } else {
            Log.v(TAG, "Playing included jingle.");
            resourceToPlay = R.raw.jingle;
        }
        try {
            final AssetFileDescriptor d = getResources().openRawResourceFd(resourceToPlay);
            if (d == null) {
                Log.wtf(TAG, "Could not open the file to play");
                return;
            }
            final FileDescriptor fd = d.getFileDescriptor();
            mPlayer = getGenericMediaPlayer();
            mPlayer.setDataSource(fd, d.getStartOffset(), d.getLength());
            d.close();
            // White noise or the default song is looped forever.
            mPlayer.setLooping(true);
        } catch (IOException e) {
            Log.e(TAG, "Could not create a media player instance. Full error below.");
            e.printStackTrace();
            return;
        }
        postSuccessMessage(mTypePlaying);
        mPlayer.prepareAsync();
    }

    /**
     * Create a media player with the standard configuration both for white noise and music.
     * @return a generic Media player suitable for this application.
     */
    private MediaPlayer getGenericMediaPlayer() {
        final MediaPlayer player = new MediaPlayer();
        // Keep the CPU awake while playing music.
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        if (SDK >= Build.VERSION_CODES.LOLLIPOP) {
            final AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player.setAudioAttributes(attrs);
        }
        player.setOnPreparedListener(this);
        player.setOnErrorListener(this);
        return player;
    }

    /**
     * Request audio focus from the system AudioManager.
     */
    @SuppressWarnings("deprecation")
    private void requestAudioFocus() {
        if (mAudioManager == null) {
            return;
        }
        if (SDK >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest == null) {
                final AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }
            mAudioManager.requestAudioFocus(mAudioFocusRequest);
        } else {
            mAudioManager.requestAudioFocus(
                    this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    /**
     * Abandon audio focus when playback stops.
     */
    @SuppressWarnings("deprecation")
    private void abandonAudioFocus() {
        if (mAudioManager == null) {
            return;
        }
        if (SDK >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            }
        } else {
            mAudioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (mPlayer == null) {
            return;
        }
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                mTypePlaying = SILENCE;
                stopSelf();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mPlayer.isPlaying()) {
                    mPlayer.pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mPlayer.setVolume(0.2f, 0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                mPlayer.setVolume(1.0f, 1.0f);
                if (!mPlayer.isPlaying()) {
                    mPlayer.start();
                }
                break;
        }
    }

    /**
     * Try getting music from MediaStore (Scoped Storage) or [sdcard]/Music/sleeping.
     * @return a valid media player if we can play from it. Returns null if we didn't find music, or we can't play
     * custom music for any reason.
     */
    private MediaPlayer tryStartingMusic() {
        final MediaPlayer player = getGenericMediaPlayer();
        final Uri nextUri = nextTrackUriFromMediaStore();
        if (nextUri != null) {
            Log.d(TAG, "Now playing MediaStore URI: " + nextUri);
            try {
                player.setDataSource(getApplicationContext(), nextUri);
                player.setOnCompletionListener(this);
                player.setLooping(false);
                return player;
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "Could not play MediaStore URI, falling back.", e);
            }
        }

        // Fallback to direct file path on older devices.
        final int nextPosition = nextTrackFromCard();
        if (nextPosition == INVALID_POSITION) {
            player.release();
            return null;
        }
        final String file = mMusicDir.getAbsolutePath() + File.separator + mFilenames[nextPosition];
        Log.d(TAG, "Now playing " + file);
        try {
            player.setDataSource(file);
        } catch (IOException e) {
            Log.e(TAG, "Could not create a media player instance. Full error below.");
            e.printStackTrace();
            player.release();
            return null;
        }
        player.setOnCompletionListener(this);
        // Play this song, and a different one when done.
        player.setLooping(false);
        return player;
    }

    /**
     * Queries MediaStore for audio files located in a "sleeping" directory and returns a random track URI.
     */
    private Uri nextTrackUriFromMediaStore() {
        if (mTrackUris == null) {
            mTrackUris = querySleepingTracksFromMediaStore();
        }
        if (mTrackUris.isEmpty()) {
            return null;
        }
        return mTrackUris.get(mRandom.nextInt(mTrackUris.size()));
    }

    /**
     * Queries MediaStore.Audio.Media for tracks under the "sleeping" directory.
     */
    @SuppressWarnings("deprecation")
    private List<Uri> querySleepingTracksFromMediaStore() {
        final List<Uri> uris = new ArrayList<>();
        final String[] projection = new String[] { MediaStore.Audio.Media._ID };
        final String selection;
        final String[] selectionArgs = new String[] { "%" + MUSIC_DIR + "/%" };
        if (SDK >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
        } else {
            selection = MediaStore.Audio.Media.DATA + " LIKE ?";
        }
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {
            if (cursor != null) {
                final int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(idColumn);
                    uris.add(ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id));
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Unable to query MediaStore for sleeping music", e);
        }
        return uris;
    }

    /**
     * Returns the position of the next track to play. Returns -1 if nothing could be
     * played.
     */
    private int nextTrackFromCard() {
        if (mFilenames == null || mFilenames.length <= 0) {
            // Fill the filename list and return the first position.
            mFilenames = getMusicList();
            Log.d(TAG, "All filenames: " + Arrays.toString(mFilenames));
            // Still nothing? Go back with an invalid position.
            if (mFilenames.length <= 0) {
                Log.e(TAG, "Music directory has no files.");
                return INVALID_POSITION;
            }
        }
        return mRandom.nextInt(mFilenames.length);
    }

    /**
     * Returns the names of all the music files available to the user.
     * @return list of all the files in the music directory.
     */
    private String[] getMusicList() {
        if (mMusicDir == null) {
            mMusicDir = getMusicDir();
        }
        // What we return when we don't find anything. It is safer to return a zero length array than null.
        final String[] foundNothing = new String[0];
        // Still nothing? We don't have a valid music directory.
        if (mMusicDir == null) {
            return foundNothing;
        }
        final String[] filenames = mMusicDir.list();
        Log.e(TAG, "All filenames: " + Arrays.toString(filenames));
        if (filenames == null || filenames.length <= 0) {
            Log.e(TAG, "Music directory has no files." + mMusicDir);
            return foundNothing;
        }
        return filenames;
    }

    /**
     * Returns the location of the music directory which is
     * [sdcard]/Music/sleeping.
     * @return the file representing the music directory.
     */
    @SuppressWarnings("deprecation")
    private static File getMusicDir() {
        final String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // If we don't have an SD card, cannot do anything here.
            Log.e(TAG, "SD card root directory is not available");
            return null;
        }
        final File rootSdLocation = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (rootSdLocation == null) {
            // Not a directory? Completely unexpected.
            Log.e(TAG, "SD card root directory is NOT a directory: " + rootSdLocation);
            return null;
        }
        // Navigate over to the music directory.
        final File musicDir = new File(rootSdLocation, MUSIC_DIR);
        if (!musicDir.isDirectory()) {
            Log.e(TAG, "Music directory does not exist." + rootSdLocation);
            return null;
        }
        return musicDir;
    }

    /**
     * Promote this service to a foreground service with an ongoing notification while playing audio.
     */
    @SuppressWarnings("deprecation")
    private void setForegroundService() {
        final Intent showClock = new Intent(this, SleepActivity.class);
        final int pendingFlags = (SDK >= Build.VERSION_CODES.M)
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;
        final PendingIntent pending = PendingIntent.getActivity(this, 0, showClock, pendingFlags);
        final String title = getString(R.string.playing_music);
        final Notification.Builder builder;
        if (SDK >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        final Notification notification = builder
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
        if (SDK >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
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
        abandonAudioFocus();
    }

    /**
     * Stop foreground state and remove the persistent notification.
     */
    @SuppressWarnings("deprecation")
    private void removeNotification() {
        if (SDK >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "AudioService: onDestroy() called");
        removeNotification();
        releasePlayer();
        // Indicate that the service is quitting.
        postSuccessMessage(SILENCE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        setForegroundService();
        postSuccessMessage(mTypePlaying);
        mPlayer.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // This method is only called for songs, since white noise is on endless loop, and will never get this event.
        releasePlayer();
        // Play the next song.  Should only be called for mTypePlaying == MUSIC
        if (mTypePlaying == MUSIC) {
            play(mTypePlaying);
        }
    }
}
