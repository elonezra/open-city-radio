package com.opencity.radio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*

@UnstableApi
class RadioService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (application as RadioApplication).repository
    private lateinit var engine: ExoPlayer
    private var session: MediaSession? = null
    private var station: Station? = null
    private var stationPackId = ""
    private var tuning: Job? = null
    private var mode = "live"
    private var filter = ""
    private var pendingOffset = false
    private var wantPlay = false
    private var initialized = false
    private fun archiveKey(s: Station) = "archive:$stationPackId:${s.id}"
    private fun isStream(s: Station?) = s?.audio?.isStream == true
    private fun saveArchive() { if (mode == "archive" && !isStream(station)) station?.let { repo.prefs.edit().putLong(archiveKey(it),engine.currentPosition.coerceAtLeast(0)).apply() } }

    override fun onCreate() {
        super.onCreate()
        mode = repo.prefs.getString("mode","live") ?: "live"
        filter = repo.prefs.getString("world","") ?: ""
        engine = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && pendingOffset) {
                        pendingOffset = false
                        station?.let { s ->
                            val duration = engine.duration.takeIf { it > 0 } ?: s.durationMs
                            if (duration <= 0) { wantPlay = false; engine.pause(); report("Cannot determine audio duration"); return }
                            val position = if (mode == "live") livePosition(s,duration) else repo.prefs.getLong(archiveKey(s),0).coerceIn(0,duration-1)
                            engine.seekTo(position)
                            engine.playWhenReady = wantPlay
                        }
                    }
                }
                override fun onPlayerError(error: PlaybackException) { wantPlay = false; report(if (isStream(station)) "Live stream could not be played. Check the network or try the station again." else "Audio could not be played. Check the pack file and supported codec.") }
            })
        }
        val controls = object : ForwardingPlayer(engine) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .remove(Player.COMMAND_SET_MEDIA_ITEM).remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_REPEAT_MODE).remove(Player.COMMAND_SET_SHUFFLE_MODE)
                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION).remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
            override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
            override fun seekToNext() { step(1) }
            override fun seekToNextMediaItem() { step(1) }
            override fun seekToPrevious() { step(-1) }
            override fun seekToPreviousMediaItem() { step(-1) }
            override fun play() { resumeRadio() }
            override fun pause() { wantPlay = false; engine.pause(); saveArchive() }
            override fun setPlayWhenReady(playWhenReady: Boolean) { if (playWhenReady) resumeRadio() else pause() }
            override fun stop() { wantPlay = false; saveArchive(); engine.stop() }
            override fun seekTo(positionMs: Long) { if (mode == "archive" && !isStream(station)) engine.seekTo(positionMs) }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) { if (mode == "archive" && !isStream(station)) engine.seekTo(positionMs) }
            override fun seekBack() { if (mode == "archive" && !isStream(station)) engine.seekBack() }
            override fun seekForward() { if (mode == "archive" && !isStream(station)) engine.seekForward() }
        }
        val launch = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this,controls).setSessionActivity(launch).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val result = super.onConnect(session,controller)
                val commands = result.availableSessionCommands.buildUpon()
                // Private library management is available only to our own UI.
                if (controller.packageName == packageName) listOf("tune","mode","filter","reload").forEach { commands.add(SessionCommand(it,Bundle.EMPTY)) }
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands.build()).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                when(command.customAction) {
                    "tune" -> tune(args.getString("id").orEmpty(),true)
                    "filter" -> { filter = args.getString("id").orEmpty(); repo.prefs.edit().putString("world",filter).apply() }
                    "mode" -> { saveArchive(); mode = if (args.getString("id") == "archive") "archive" else "live"; repo.prefs.edit().putString("mode",mode).apply(); station?.let { tune(it.id,engine.playWhenReady,false) } }
                    "reload" -> { saveArchive(); tuning?.cancel(); engine.stop(); station = null; initialized = false; initialize() }
                    else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
        initialize()
        scope.launch { while (isActive) { delay(5000); saveArchive() } }
    }
    private fun initialize() { scope.launch {
        runCatching { withContext(Dispatchers.IO) { repo.reload() } }.onSuccess { pack ->
            initialized = true
            if (pack.worlds.none { it.id == filter }) filter = ""
            val last = repo.prefs.getString("lastStation",null)
            tune(pack.stations.firstOrNull { it.id == last }?.id ?: pack.stations.first().id,wantPlay)
        }.onFailure { report("Manifest could not be loaded. Open Settings to select a valid pack.") }
    } }
    private fun livePosition(s: Station, duration: Long) = LiveClock.position(System.currentTimeMillis(),duration,s.id,s.offsetMs,
        s.dailySeed && repo.prefs.getBoolean("dailySeed",true),s.epochMs)
    private fun resumeRadio() {
        wantPlay = true
        if (!initialized) return
        val s = station ?: return
        if (engine.playbackState == Player.STATE_IDLE) { tune(s.id,true); return }
        if (isStream(s)) { engine.play(); return }
        if (!pendingOffset && mode == "live" && engine.duration > 0) engine.seekTo(livePosition(s,engine.duration))
        if (!pendingOffset) engine.play()
    }
    private fun step(direction: Int) {
        val list = repo.pack?.stations.orEmpty().filter { filter.isEmpty() || it.worldId == filter }
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.id == station?.id }
        tune(list[Math.floorMod(current + direction,list.size)].id,true)
    }
    private fun tune(id: String, autoplay: Boolean, saveCurrent: Boolean = true) {
        val next = repo.pack?.stations?.firstOrNull { it.id == id } ?: return
        if (saveCurrent) saveArchive(); tuning?.cancel(); engine.pause(); wantPlay = autoplay
        tuning = scope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { repo.resolve(next.audio) }
                ensureActive()
                station = next; stationPackId = repo.pack?.id.orEmpty(); pendingOffset = !next.audio.isStream
                val world = repo.pack!!.worlds.first { it.id == next.worldId }
                repo.prefs.edit().putString("lastStation",next.id).apply()
                val uri = if (resolved is java.io.File) android.net.Uri.fromFile(resolved) else android.net.Uri.parse(resolved.toString())
                val artist = listOf(world.name, world.year.takeIf { it.isNotBlank() }, next.frequency.takeIf { it.isNotBlank() }?.let { "$it FM" })
                    .filterNotNull().joinToString(" · ")
                engine.repeatMode = if (next.audio.isStream) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                engine.setMediaItem(MediaItem.Builder().setMediaId(next.id).setUri(uri).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(next.name).setArtist(artist).setIsPlayable(true).build()).build())
                engine.prepare()
                if (next.audio.isStream) engine.playWhenReady = wantPlay
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { wantPlay = false; report("${next.name}: audio unavailable. Check the content folder or download link.") }
        }
    }
    private fun report(message: String) { session?.broadcastCustomCommand(SessionCommand("error",Bundle.EMPTY),Bundle().apply { putString("message",message) }) }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { saveArchive(); if (!engine.playWhenReady) stopSelf() }
    override fun onDestroy() { saveArchive(); scope.cancel(); session?.release(); engine.release(); super.onDestroy() }
}
