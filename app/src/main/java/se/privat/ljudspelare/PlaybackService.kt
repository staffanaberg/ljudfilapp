package se.privat.ljudspelare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.OpenableColumns
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver

class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "ljudspelare_playback"
        const val NOTIFICATION_ID = 1
        const val PREFS = "ljudspelare_prefs"
        const val KEY_PLAYLIST = "playlist_uris"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private var playlist: MutableList<Uri> = mutableListOf()
    private var currentIndex = -1
    private var trackFinished = false
    private lateinit var prefs: SharedPreferences

    var listener: PlaybackListener? = null

    interface PlaybackListener {
        fun onStateChanged(isPlaying: Boolean, index: Int, trackName: String, statusText: String)
        fun onPlaylistChanged(names: List<String>)
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "LjudspelareSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { playOrResume() }
                override fun onPause() { pausePlayback() }
                override fun onSkipToNext() { advanceAndPlay() }
                override fun onSkipToPrevious() { previousAndPlay() }
                override fun onStop() { stopPlayback() }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        restorePlaylist()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun getSessionToken(): MediaSessionCompat.Token = mediaSession.sessionToken

    fun setPlaylist(uris: List<Uri>) {
        playlist = uris.toMutableList()
        currentIndex = -1
        trackFinished = false
        savePlaylist()
        notifyPlaylistChanged()
    }

    fun getPlaylistUris(): List<Uri> = playlist

    fun getCurrentIndex(): Int = currentIndex

    fun loadAndPlay(index: Int) {
        if (index < 0 || index >= playlist.size) return
        currentIndex = index
        trackFinished = false
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            try {
                setDataSource(applicationContext, playlist[index])
                setOnPreparedListener {
                    start()
                    startForeground(NOTIFICATION_ID, buildNotification(true))
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    notifyState(true, "Spelar")
                }
                setOnCompletionListener {
                    trackFinished = true
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    notifyState(false, "Klar — tryck för nästa låt")
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                prepareAsync()
            } catch (e: Exception) {
                notifyState(false, "Kunde inte öppna filen")
            }
        }
        updateMetadata(index)
    }

    fun playOrResume() {
        if (playlist.isEmpty()) return
        if (currentIndex == -1) {
            loadAndPlay(0)
            return
        }
        if (trackFinished) {
            advanceAndPlay()
            return
        }
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startForeground(NOTIFICATION_ID, buildNotification(true))
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                notifyState(true, "Spelar")
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                notifyState(false, "Pausad")
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun advanceAndPlay() {
        if (playlist.isEmpty()) return
        val next = if (currentIndex + 1 < playlist.size) currentIndex + 1 else 0
        loadAndPlay(next)
    }

    fun previousAndPlay() {
        if (playlist.isEmpty()) return
        val prev = if (currentIndex - 1 >= 0) currentIndex - 1 else playlist.size - 1
        loadAndPlay(prev)
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, 0, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMetadata(index: Int) {
        val name = displayNameFor(playlist[index])
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Ljudspelare")
            .build()
        mediaSession.setMetadata(metadata)
    }

    fun displayNameFor(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Okänd fil"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx)
                }
            }
        } catch (_: Exception) {
        }
        return name
    }

    private fun notifyState(isPlaying: Boolean, status: String) {
        val name = if (currentIndex in playlist.indices) displayNameFor(playlist[currentIndex]) else "—"
        listener?.onStateChanged(isPlaying, currentIndex, name, status)
    }

    private fun notifyPlaylistChanged() {
        listener?.onPlaylistChanged(playlist.map { displayNameFor(it) })
    }

    private fun savePlaylist() {
        val strs = playlist.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_PLAYLIST, strs).apply()
    }

    private fun restorePlaylist() {
        val strs = prefs.getStringSet(KEY_PLAYLIST, null) ?: return
        playlist = strs.mapNotNull { Uri.parse(it) }.toMutableList()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Uppspelning", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val name = if (currentIndex in playlist.indices) displayNameFor(playlist[currentIndex]) else "Ljudspelare"

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pausa",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Spela",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
            )
        }
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Nästa",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(name)
            .setContentText("Ljudspelare")
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .build()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaSession.release()
        super.onDestroy()
    }
}
