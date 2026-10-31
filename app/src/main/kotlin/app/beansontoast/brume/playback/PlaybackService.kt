package app.beansontoast.brume.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import app.beansontoast.brume.BrumeApp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.ui.MainActivity
import app.beansontoast.brume.ui.soundLabel
import kotlin.concurrent.thread

/**
 * Pumps the engine into an AudioTrack from a foreground mediaPlayback service, so it keeps playing
 * with the screen off. Platform MediaSession for the lock screen and Bluetooth buttons (Media3 is not
 * in the offline build cache; see CLAUDE.md). Pauses on any change of output route and on focus loss.
 */
class PlaybackService : Service() {
    private val player by lazy { (application as BrumeApp).graph.player }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var session: MediaSession
    private var focus: AudioFocusRequest? = null
    @Volatile private var running = false

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .apply {
            // Head-tracked spatial audio would move the sound around; Brume's sound stays put.
            if (Build.VERSION.SDK_INT >= 32) setSpatializationBehavior(AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER)
        }
        .build()

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = player.pause(EndedBy.ROUTE_CHANGE)
    }

    /** Headphones in or out, a speaker connected: the level was set for another route, so pause. */
    private val devices = object : AudioDeviceCallback() {
        private var primed = false
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (primed && added.any { it.isSink }) player.pause(EndedBy.ROUTE_CHANGE)
            primed = true
        }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.isSink }) player.pause(EndedBy.ROUTE_CHANGE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        session = MediaSession(this, "Brume").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = player.play()
                override fun onPause() = player.pause(EndedBy.USER)
                override fun onStop() = player.pause(EndedBy.USER)
            })
            isActive = true
        }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        audio.registerAudioDeviceCallback(devices, main)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause(EndedBy.USER)
        }
        if (player.ui.value.playing) {
            startForeground(NOTIFICATION_ID, notification(playing = true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            publish(playing = true)
            if (requestFocus()) startPump() else player.pause(EndedBy.FOCUS_LOSS)
        }
        return START_NOT_STICKY
    }

    private fun requestFocus(): Boolean {
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    player.pause(EndedBy.FOCUS_LOSS)
                }
            }, main)
            .build().also { focus = it }
        return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun startPump() {
        if (running) return
        running = true
        thread(name = "brume-audio") { pump() }
    }

    private fun pump() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val engine = player.engine
        val frames = engine.rate / 50 // 20 ms blocks
        val buffer = FloatArray(frames * 2)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(engine.rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val min = AudioTrack.getMinBufferSize(engine.rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        // About 200 ms of buffer: rides out a GC pause, still stops promptly.
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min, engine.rate * 2 * 4 / 5))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            .build()
        try {
            track.play()
            while (running) {
                engine.render(buffer, frames)
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                if (engine.state == SoundEngine.State.STOPPED) break
            }
        } finally {
            track.stop()
            track.release()
            running = false
            main.post { onPumpEnded() }
        }
    }

    private fun onPumpEnded() {
        // A Play pressed while the last block drained: keep going rather than drop it.
        if (player.engine.state != SoundEngine.State.STOPPED) { startPump(); return }
        player.onEngineStopped()
        focus?.let { audio.abandonAudioFocusRequest(it) }
        publish(playing = false)
        stopForeground(STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(playing = false))
    }

    private fun publish(playing: Boolean) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, soundLabel(this, player.ui.value))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .build(),
        )
    }

    private fun notification(playing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val toggle = if (playing) {
            PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getForegroundService(this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE)
        }
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            getString(if (playing) R.string.action_pause else R.string.action_play),
            toggle,
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_brume_mark)
            .setContentTitle(soundLabel(this, player.ui.value))
            .setContentText(getString(if (playing) R.string.notification_playing else R.string.notification_paused))
            .setContentIntent(open)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0))
            .build()
    }

    override fun onDestroy() {
        running = false
        unregisterReceiver(noisy)
        audio.unregisterAudioDeviceCallback(devices)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        session.release()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "app.beansontoast.brume.PLAY"
        const val ACTION_PAUSE = "app.beansontoast.brume.PAUSE"
    }
}
