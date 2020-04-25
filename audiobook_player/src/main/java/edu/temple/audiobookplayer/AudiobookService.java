package edu.temple.audiobookplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
//import android.support.v4.app.NotificationCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class AudiobookService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    private final MediaControlBinder binder = new MediaControlBinder();
    private static final String TAG = "Audiobook Service";
    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private Notification notification;
    private Handler progressHandler;
    private Thread progressThread;
    private PlayingState playingState = PlayingState.STOPPED; // 0 - stopped, 1 - playing, 2 - paused
    private int startPosition;
    private int currentBookId = -1;
    private Uri currentBookUri;

    private final String NOTIFICATION_CHANNEL_ID = "media_player_control";

    public AudiobookService() {}

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);

        String NOTIFICATION_PLAYING_TITLE = getString(R.string.notification_playing_title);
        String NOTIFICATION_PLAYING_DESCRIPTION = getString(R.string.notification_playing_description);

        notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(NOTIFICATION_PLAYING_TITLE)
                .setContentText(NOTIFICATION_PLAYING_DESCRIPTION)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();


    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Player bound");
        return binder;
    }

    private void setHandler (Handler handler) {
        Log.i(TAG, "Handler set");
        progressHandler = handler;
    }

    private void play(int id) {
        try {
            currentBookId = id;
            currentBookUri = null;
            mediaPlayer.reset();
            String BOOK_DOWNLOAD_URL = "https://kamorris.com/lab/audlib/download.php?id=";
            mediaPlayer.setDataSource(BOOK_DOWNLOAD_URL + id);
            playingState = PlayingState.STOPPED;
            mediaPlayer.prepareAsync();
            Log.i(TAG, "Audiobook preparing");
            int FOREGROUND_CODE = 1;
            startForeground(FOREGROUND_CODE, notification);
            Log.i(TAG, "Foreground notification started");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void play(int id, int position) {
        startPosition = position;
        play(id);
    }

    private void play(File file) {
        try {
            currentBookUri = Uri.fromFile(file);
            currentBookId = -1;
            mediaPlayer.reset();
            mediaPlayer.setDataSource((new FileInputStream(file)).getFD());
            playingState = PlayingState.STOPPED;
            mediaPlayer.prepareAsync();
            Log.i(TAG, "Audiobook preparing");
            int FOREGROUND_CODE = 1;
            startForeground(FOREGROUND_CODE, notification);
            Log.i(TAG, "Foreground notification started");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void play(File file, int position) {
        startPosition = position;
        play(file);
    }

    private void pause () {
        if (playingState == PlayingState.PLAYING) {
            playingState = PlayingState.PAUSED;
            mediaPlayer.pause();
            if (progressThread != null) {
                progressThread.interrupt();
                progressThread = null;
            }
            Log.i(TAG, "Player paused");
        } else if (playingState == PlayingState.PAUSED) {
            playingState = PlayingState.PLAYING;
            mediaPlayer.start();
            progressThread = new Thread(new NotifyProgress());
            progressThread.start();
            Log.i(TAG, "Player started");
        }
    }

    private void stop() {
        mediaPlayer.stop();
        playingState = PlayingState.STOPPED;
        stopForeground(true);
        if (progressThread != null) {
            progressThread.interrupt();
            progressThread = null;
        }
        Log.i(TAG, "Player stopped");
    }

    private void seekTo(int position) {
        position = position * 1000;
        if (position <= mediaPlayer.getDuration()) {
            mediaPlayer.seekTo((position));
            Log.i(TAG, "Audiobook position changed");
        }
    }

    private boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    @SuppressWarnings("unused")
    public class MediaControlBinder extends Binder {

        public void play(int id) {
            AudiobookService.this.play(id);
        }

        public void play(int id, int startPosition) {
            AudiobookService.this.play(id, startPosition);
        }

        public void play(File file) {
            AudiobookService.this.play(file);
        }

        public void play(File file, int startPosition) {
            AudiobookService.this.play(file, startPosition);
        }

        public void pause() {
            AudiobookService.this.pause();
        }

        public void stop() {
            AudiobookService.this.stop();
        }

        public void setProgressHandler(Handler handler) {
            AudiobookService.this.setHandler(handler);
        }

        public void seekTo(int position) {
            AudiobookService.this.seekTo(position);
        }

        public boolean isPlaying() {
            return AudiobookService.this.isPlaying();
        }

    }

    @Override
    public boolean onUnbind(Intent intent) {
        progressHandler = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel);
            String description = getString(R.string.notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.i(TAG, "Audiobook prepared");
        playingState = PlayingState.PLAYING;
        if (startPosition > 0) {
            new Thread(new SeekDelay(500)).start();
        }
        mediaPlayer.start();
        progressThread = new Thread(new NotifyProgress());
        progressThread.start();
        Log.i(TAG, "Audiobook started");

    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mp.reset();
        progressThread = null;
        stopSelf();
    }

    class NotifyProgress implements Runnable {

        @Override
        public void run() {
            Message message;
            while (playingState == PlayingState.PLAYING) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.i(TAG, "Progress update stopped");
                }
                if (progressHandler != null) {
                    if (playingState == PlayingState.PLAYING) {
                        message = Message.obtain();
                        if (currentBookId >= 0)
                            message.obj = new BookProgress(currentBookId, mediaPlayer.getCurrentPosition() / 1000);
                        else
                            message.obj = new BookProgress(currentBookUri, mediaPlayer.getCurrentPosition() / 1000);
                        progressHandler.sendMessage(message);
                    } else if (playingState == PlayingState.PAUSED)
                        progressHandler.sendEmptyMessage(0);
                }
            }
        }

    }

    class SeekDelay implements Runnable {

        int delay;

        SeekDelay(int delay) {
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mediaPlayer.seekTo(1000 * startPosition);
            startPosition = 0;
        }
    }

    enum PlayingState {
        STOPPED, PLAYING, PAUSED
    }

    public class BookProgress {
        private int bookId = -1;
        private Uri bookUri;
        private int progress;

        public BookProgress(int bookId, int progress) {
            this.bookId = bookId;
            this.progress = progress;
        }

        public BookProgress(Uri bookUri, int progress) {
            this.bookUri = bookUri;
            this.progress = progress;
        }

        public int getBookId() {
            return bookId;
        }

        public Uri getBookUri() {
            return bookUri;
        }

        public int getProgress() {
            return progress;
        }
    }
}