package se.privat.ljudspelare

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), PlaybackService.PlaybackListener {

    private var service: PlaybackService? = null
    private var bound = false
    private var mediaController: MediaControllerCompat? = null

    private lateinit var tvTrackIndex: TextView
    private lateinit var tvTrackName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var playlistContainer: LinearLayout

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as PlaybackService.LocalBinder
            service = localBinder.getService()
            service?.listener = this@MainActivity
            mediaController = MediaControllerCompat(this@MainActivity, service!!.getSessionToken())
            bound = true
            refreshPlaylistUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            service?.setPlaylist(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTrackIndex = findViewById(R.id.tvTrackIndex)
        tvTrackName = findViewById(R.id.tvTrackName)
        tvStatus = findViewById(R.id.tvStatus)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        playlistContainer = findViewById(R.id.playlistContainer)

        findViewById<Button>(R.id.btnPickFiles).setOnClickListener {
            filePickerLauncher.launch(arrayOf("audio/*"))
        }

        btnPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            val state = controller.playbackState?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            mediaController?.transportControls?.skipToNext()
        }
        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
        }

        val intent = Intent(this, PlaybackService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (bound) {
            service?.listener = null
            unbindService(connection)
        }
        super.onDestroy()
    }

    override fun onStateChanged(isPlaying: Boolean, index: Int, trackName: String, statusText: String) {
        runOnUiThread {
            val total = service?.getPlaylistUris()?.size ?: 0
            tvTrackIndex.text = if (index >= 0) "Spår ${index + 1} av $total" else "Inget valt"
            tvTrackName.text = trackName
            tvStatus.text = statusText
            btnPlayPause.text = if (isPlaying) "⏸" else "▶"
            highlightActive(index)
        }
    }

    override fun onPlaylistChanged(names: List<String>) {
        runOnUiThread {
            playlistContainer.removeAllViews()
            names.forEachIndexed { i, name ->
                val tv = TextView(this)
                tv.text = "${i + 1}. $name"
                tv.setTextColor(0xFFECEEF1.toInt())
                tv.setPadding(16, 24, 16, 24)
                tv.setOnClickListener {
                    service?.loadAndPlay(i)
                }
                playlistContainer.addView(tv)
            }
        }
    }

    private fun refreshPlaylistUI() {
        val svc = service ?: return
        val names = svc.getPlaylistUris().map { svc.displayNameFor(it) }
        onPlaylistChanged(names)
        val idx = svc.getCurrentIndex()
        if (idx >= 0) {
            val name = svc.displayNameFor(svc.getPlaylistUris()[idx])
            onStateChanged(false, idx, name, "Redo")
        }
    }

    private fun highlightActive(index: Int) {
        for (i in 0 until playlistContainer.childCount) {
            val child = playlistContainer.getChildAt(i) as? TextView ?: continue
            child.setBackgroundColor(if (i == index) 0xFF2C7A72.toInt() else 0x00000000)
        }
    }
}
