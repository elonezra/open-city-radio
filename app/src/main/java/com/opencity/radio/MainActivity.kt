package com.opencity.radio

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*

@UnstableApi
class MainActivity : ComponentActivity() {
    private val repo get() = (application as RadioApplication).repository
    private var controller: MediaController? = null
    private var connection: ListenableFuture<MediaController>? = null
    private var page = "radio"
    private var worldFilter = ""
    private var search = ""
    private var favoritesOnly = false
    private var yearFilter = ""
    private var universeFilter = ""
    private var busy = false
    private var busyView: TextView? = null
    private lateinit var root: LinearLayout
    private var playButton: ImageButton? = null
    private val imageJobs = mutableListOf<Job>()
    private var statusView: TextView? = null
    private var artworkJob: Job? = null
    private var carLock = false
    private val cyan = Color.rgb(101,233,255)
    private val pink = Color.rgb(243,107,205)
    private var fg = Color.WHITE
    private var muted = Color.rgb(166,174,194)
    private var bg = Color.rgb(8,11,22)
    private val car: Boolean get() = when(repo.prefs.getString("car","auto")) { "on" -> true; "off" -> false; else -> resources.configuration.screenWidthDp >= 700 }
    private val wide: Boolean get() = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val selected: Station? get() = repo.pack?.stations?.firstOrNull { it.id == controller?.currentMediaItem?.mediaId }
        ?: repo.pack?.stations?.firstOrNull { it.id == repo.prefs.getString("lastStation",null) } ?: repo.pack?.stations?.firstOrNull()
    private val zipPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runTask("Importing pack") { repo.importZip(contentResolver.openInputStream(it) ?: error("Cannot open ZIP")) } } }
    private val jsonPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runTask("Loading manifest") { repo.external(it) } } }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { runTask("Opening folder") { repo.folder(it) } } }
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { if (!busy) render() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayback() }
        override fun onPlaybackStateChanged(playbackState: Int) { updatePlayback() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page") ?: if(repo.prefs.getBoolean("welcomed",false)) "radio" else "welcome"
        worldFilter = repo.prefs.getString("world","") ?: ""
        yearFilter=repo.prefs.getString("year","").orEmpty(); universeFilter=repo.prefs.getString("universe","").orEmpty()
        carLock = savedInstanceState?.getBoolean("lock") ?: false
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.reload() } }.onFailure { page="settings"; message("Cannot load current manifest. Select a valid JSON, ZIP, or built-in library.") }
            render()
        }
        render()
    }
    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(this,SessionToken(this,ComponentName(this,RadioService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if(command.customAction=="error") message(args.getString("message") ?: "Playback error")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).buildAsync()
        connection=future
        future.addListener({ runCatching { future.get() }.onSuccess { controller=it; it.addListener(listener); render() }.onFailure { message("Could not connect to playback service") } },mainExecutor)
    }
    override fun onStop() {
        controller?.removeListener(listener); controller=null
        connection?.let { MediaController.releaseFuture(it) }; connection=null
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page",page); outState.putBoolean("lock",carLock); super.onSaveInstanceState(outState) }
    override fun onDestroy() { repo.progress={}; super.onDestroy() }
    private fun dp(n: Int) = (n*resources.displayMetrics.density).toInt()
    private fun color(s: String, fallback: Int=cyan): Int = runCatching { Color.parseColor(s) }.getOrDefault(fallback)
    private fun text(value: String,size: Float=18f,tint: Int=fg,bold: Boolean=false) = TextView(this).apply {
        text=value; textSize=size; setTextColor(tint); if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
        setPadding(dp(6),dp(8),dp(6),dp(8))
    }
    private fun panel(stroke: Int=Color.rgb(51,63,84),fill: Int=if(bg==Color.rgb(229,237,244)) Color.rgb(246,249,252) else Color.rgb(19,25,42)) = GradientDrawable().apply {
        setColor(fill); cornerRadius=dp(18).toFloat(); setStroke(dp(1),stroke)
    }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text=label; textSize=if(car) 19f else 15f; isAllCaps=false; setTextColor(fg)
        minHeight=dp(if(car)72 else 56); minimumHeight=minHeight
        background=panel(); setPadding(dp(14),dp(8),dp(14),dp(8)); setOnClickListener { action() }
        contentDescription=label
    }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(10),dp(18),dp(10)) }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun LinearLayout.addBlock(view: View,height: Int=ViewGroup.LayoutParams.WRAP_CONTENT) { addView(view,LinearLayout.LayoutParams(-1,height).apply { bottomMargin=dp(12) }) }
    private fun LinearLayout.equal(view: View) { addView(view,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) }) }
    private fun navigate(to: String) { if(busy) return; if(carLock && to!="radio") { message("Unlock car mode while parked to browse or manage content"); return }; page=to; render() }
    private fun command(name: String,id: String="") { controller?.sendCustomCommand(SessionCommand(name,Bundle.EMPTY),Bundle().apply { putString("id",id) }) }
    private fun tune(s: Station) { command("tune",s.id); page="radio"; render() }
    private fun togglePlay() { controller?.let { if(it.playWhenReady) it.pause() else it.play() } ?: message("Connecting to player…") }
    private fun updatePlayback() {
        playButton?.setImageResource(if(controller?.playWhenReady==true) R.drawable.btn_pause else R.drawable.btn_play)
        playButton?.contentDescription=if(controller?.playWhenReady==true) "Pause radio" else "Play radio"
        statusView?.text=when { controller?.playbackState==Player.STATE_BUFFERING -> "◌  TUNING IN"; controller?.isPlaying!=true -> "○  PAUSED"; repo.prefs.getString("mode","live")=="archive" -> "◉  ARCHIVE"; else -> "●  LIVE ON AIR" }
    }
    private fun render() {
        if(isFinishing || isDestroyed) return
        artworkJob?.cancel(); imageJobs.forEach { it.cancel() }; imageJobs.clear(); playButton=null; statusView=null; busyView=null
        val light = repo.prefs.getString("theme","station")=="light"
        fg=if(light) Color.rgb(13,25,43) else Color.WHITE; muted=if(light) Color.rgb(73,83,105) else Color.rgb(166,174,194); bg=if(light) Color.rgb(229,237,244) else Color.rgb(8,11,22)
        root.removeAllViews(); root.setBackgroundColor(bg)
        if(car) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val header=row().apply { setPadding(dp(12),dp(4),dp(12),dp(4)) }
        header.addView(button(if(page=="radio") "◉" else "‹") { navigate(if(page=="radio") "worlds" else "radio") },LinearLayout.LayoutParams(dp(58),dp(56)))
        header.addView(text(if(page=="radio") selected?.let { s -> repo.pack?.worlds?.find { it.id==s.worldId }?.let { "${it.name} · ${it.year}" } } ?: "Open City Radio" else when(page) { "worlds"->"WORLDS & ERAS"; "stations"->"STATIONS"; "welcome"->"OPEN CITY RADIO"; else->"LIBRARY & SETTINGS" },if(car)24f else 20f,fg,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button(if(carLock) "🔒" else "⚙") { if(carLock) AlertDialog.Builder(this).setTitle("Are you parked?").setPositiveButton("Unlock") { _,_->carLock=false; render() }.setNegativeButton("Cancel",null).show() else navigate("settings") },LinearLayout.LayoutParams(dp(60),dp(56)))
        root.addView(header)
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val body=column(); scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(page) { "worlds"->worlds(body); "stations"->stations(body); "settings"->settings(body); "welcome"->welcome(body); else->radio(body) }
        if(page!="welcome") {
            if(page!="radio" && selected!=null) miniPlayer(root)
            val nav=row(); listOf("Worlds" to "worlds","Radio" to "radio","Stations" to "stations","Library" to "settings").forEach { (label,to)->nav.equal(button(label){navigate(to)}) }; root.addView(nav)
        }
        updatePlayback()
    }
    private fun spriteButton(resource: Int, label: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(resource); contentDescription=label; scaleType=ImageView.ScaleType.FIT_CENTER
        background=panel(); setPadding(dp(6),dp(6),dp(6),dp(6)); minimumHeight=dp(if(car)80 else 64)
        setOnClickListener { action() }
    }
    private fun controls(): LinearLayout = row().apply {
        val previous=spriteButton(R.drawable.btn_prev_station,"Previous station") { controller?.seekToPreviousMediaItem() }
        playButton=spriteButton(R.drawable.btn_play,"Play radio") { togglePlay() }
        val next=spriteButton(R.drawable.btn_next_station,"Next station") { controller?.seekToNextMediaItem() }
        listOf(previous,playButton!!,next).forEach { addView(it,LinearLayout.LayoutParams(0,dp(if(car)80 else 70),1f).apply { setMargins(dp(4),0,dp(4),0) }) }
    }
    private fun loadAsset(view: ImageView, asset: Asset) {
        imageJobs += lifecycleScope.launch {
            val value=withContext(Dispatchers.IO) { runCatching { repo.resolve(asset) }.getOrNull() }
            if(value!=null) view.load(value)
        }
    }
    private fun radio(body: LinearLayout) {
        val s=selected
        if(s==null) { body.addBlock(text("Import a Radio Pack to begin")); body.addBlock(button("Open library") { navigate("settings") }); return }
        val theme=repo.prefs.getString("theme","station")
        val world=repo.pack!!.worlds.first { it.id==s.worldId }
        val accent=if(theme=="world") color(world.accent) else color(s.accent)
        val secondary=if(theme=="world") color(world.secondary,pink) else color(s.secondary,pink)
        val card=FrameLayout(this).apply { background=panel(accent); clipToOutline=true }
        val art=RadioArtView(this).apply { this.accent=accent; this.secondary=secondary; title=s.name.uppercase(); frequency=if(s.frequency.isBlank()) "LIVE" else "FM ${s.frequency}"; contentDescription="${s.name} station artwork" }
        card.addView(art,FrameLayout.LayoutParams(-1,-1))
        val backgroundImage=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; alpha=.35f }
        card.addView(backgroundImage,FrameLayout.LayoutParams(-1,-1))
        val logo=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="${s.name} logo"; setPadding(dp(20),dp(20),dp(20),dp(20)) }
        card.addView(logo,FrameLayout.LayoutParams(-1,-1))
        artworkJob=lifecycleScope.launch {
            val bgAsset=if(s.background.path.isNotBlank() || s.background.url.isNotBlank()) s.background else world.background
            val back=withContext(Dispatchers.IO) { runCatching { repo.resolve(bgAsset) }.getOrNull() }
            if(back!=null) backgroundImage.load(back)
            val image=withContext(Dispatchers.IO) { runCatching { repo.resolve(s.logo) }.getOrNull() }
            if(image!=null) logo.load(image) { listener(onSuccess={_,_->art.title=""; art.frequency=""; art.invalidate()}) }
        }
        val info=column()
        statusView=text("○  PAUSED",18f,Color.rgb(255,111,122),true).apply { gravity=Gravity.CENTER; background=panel(Color.rgb(244,79,101),bg) }
        info.addBlock(statusView!!)
        info.addBlock(text(s.name,if(car)32f else 26f,fg,true).apply { gravity=Gravity.CENTER })
        info.addBlock(text("${s.frequency} FM  ·  ${s.genre}",17f,muted).apply { gravity=Gravity.CENTER })
        info.addBlock(DialView(this).apply { this.accent=accent; frequency=s.frequency },dp(70))
        info.addBlock(controls())
        info.addBlock(button(if(repo.favorite(s.id)) "★  Saved to favorites" else "☆  Add to favorites") { repo.toggleFavorite(s.id); render() })
        if(wide) {
            val content=row(); content.addView(card,LinearLayout.LayoutParams(0,dp(300),1.05f)); content.addView(info,LinearLayout.LayoutParams(0,-2,1f)); body.addBlock(content)
        } else { body.addBlock(card,dp(if(car)300 else 280)); body.addBlock(info) }
        if(repo.prefs.getString("mode","live")=="archive") {
            val seek=SeekBar(this).apply { max=1000; progress=((controller?.currentPosition ?: 0)*1000/((controller?.duration ?: 1).coerceAtLeast(1))).toInt().coerceIn(0,1000) }
            seek.contentDescription="Archive playback position"
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(v: SeekBar?,p: Int,user: Boolean) { if(user) controller?.let { if(it.duration>0) it.seekTo(it.duration*p/1000) } }
                override fun onStartTrackingTouch(v: SeekBar?) {}
                override fun onStopTrackingTouch(v: SeekBar?) {}
            }); body.addBlock(seek)
        }
        body.addBlock(text(if(worldFilter.isEmpty()) "ALL WORLDS · Station switching" else "ROAD TRIP · ${world.name} ${world.year}",13f,muted))
        if(car && !carLock) body.addBlock(button("Lock car controls") { carLock=true; render() })
    }
    private fun miniPlayer(body: LinearLayout) {
        val bar=row().apply { background=panel(); setPadding(dp(8),dp(4),dp(8),dp(4)) }
        bar.addView(text(selected?.name ?: "Radio",16f,fg,true).apply { setOnClickListener { navigate("radio") } },LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(button(if(controller?.playWhenReady==true) "Ⅱ" else "▶") { togglePlay(); render() },LinearLayout.LayoutParams(dp(70),dp(56)))
        bar.addView(button("⏭") { controller?.seekToNextMediaItem() },LinearLayout.LayoutParams(dp(70),dp(56))); body.addView(bar)
    }
    private fun worlds(body: LinearLayout) {
        body.addBlock(text("Choose a city. Tune into its time.",17f,muted))
        body.addBlock(button("All worlds · clear Road Trip filter") { worldFilter=""; command("filter",""); navigate("stations") })
        val target=if(wide) row() else body
        repo.pack?.worlds?.forEach { world ->
            val card=column().apply { background=panel(color(world.accent)); setOnClickListener { worldFilter=world.id; command("filter",world.id); navigate("stations") } }
            val cover=FrameLayout(this)
            cover.addView(RadioArtView(this).apply { title=world.name.uppercase(); frequency=world.year; accent=color(world.accent); secondary=color(world.secondary,pink) },FrameLayout.LayoutParams(-1,-1))
            val externalCover=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription="${world.name} artwork" }
            cover.addView(externalCover,FrameLayout.LayoutParams(-1,-1)); loadAsset(externalCover,world.background)
            card.addBlock(cover,dp(if(wide)150 else 165))
            card.addBlock(text("${world.name} ${world.year}",23f,fg,true))
            card.addBlock(text("${world.universe} · ${repo.pack!!.stations.count { it.worldId==world.id }} stations",15f,muted))
            card.addBlock(button("Explore stations →") { worldFilter=world.id; command("filter",world.id); navigate("stations") })
            if(wide) target.equal(card) else target.addBlock(card)
        }
        if(wide) body.addBlock(target)
    }
    private fun stations(body: LinearLayout) {
        val filters=row()
        filters.equal(button(if(favoritesOnly) "★ Favorites" else "All stations") { favoritesOnly=!favoritesOnly; render() })
        filters.equal(button("Search") { input("Station or genre",search) { search=it; render() } }); body.addBlock(filters)
        val time=row()
        time.equal(button(if(yearFilter.isBlank()) "Year: all" else yearFilter) { choose("Year",listOf("")+repo.pack?.worlds.orEmpty().map { it.year }.distinct()) { yearFilter=it; repo.prefs.edit().putString("year",it).apply(); render() } })
        time.equal(button(if(universeFilter.isBlank()) "Universe: all" else universeFilter) { choose("Universe",listOf("")+repo.pack?.worlds.orEmpty().map { it.universe }.distinct()) { universeFilter=it; repo.prefs.edit().putString("universe",it).apply(); render() } }); body.addBlock(time)
        if(search.isNotBlank()) body.addBlock(button("Clear search: $search") { search=""; render() })
        val list=repo.pack?.stations.orEmpty().filter { s ->
            val w=repo.pack!!.worlds.first { it.id==s.worldId }
            (worldFilter.isEmpty() || s.worldId==worldFilter) && (!favoritesOnly || repo.favorite(s.id)) &&
                (yearFilter.isEmpty() || yearFilter==w.year) && (universeFilter.isEmpty() || universeFilter==w.universe) &&
                (search.isBlank() || "${s.name} ${s.genre}".contains(search,true))
        }
        if(list.isEmpty()) body.addBlock(text("No stations match these filters.",18f,muted))
        list.forEach { s ->
            val item=row().apply { background=panel(if(s.id==selected?.id)color(s.accent) else Color.rgb(51,63,84)); setPadding(dp(8),dp(8),dp(8),dp(8)) }
            val badge=ImageView(this).apply { setImageResource(R.drawable.ic_radio); scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="${s.name} logo" }
            item.addView(badge,LinearLayout.LayoutParams(dp(if(car)80 else 54),dp(if(car)80 else 64))); loadAsset(badge,s.logo)
            val detail=listOf(s.frequency.takeIf { it.isNotBlank() }?.let { "$it FM" },s.genre.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
            val label=column().apply { addView(text(s.name,if(car)23f else 19f,fg,true)); if(detail.isNotBlank()) addView(text(detail,15f,muted)); setOnClickListener { tune(s) }; contentDescription="Play ${s.name}" }
            item.addView(label,LinearLayout.LayoutParams(0,-2,1f)); item.addView(button(if(repo.favorite(s.id)) "★" else "☆") { repo.toggleFavorite(s.id); render() },LinearLayout.LayoutParams(dp(58),dp(62)))
            item.addView(button("▶") { tune(s) },LinearLayout.LayoutParams(dp(62),dp(62))); body.addBlock(item)
        }
    }
    private fun welcome(body: LinearLayout) {
        body.addBlock(RadioArtView(this).apply { title="OPEN CITY"; frequency="RADIO" },dp(240))
        body.addBlock(text("Your city. Always on air.",30f,fg,true))
        body.addBlock(text("GTA stations in a phone-friendly native player, plus support for your own Radio Packs.",18f,muted))
        body.addBlock(button("GTA Radio Online") { runTask("Loading GTA Radio") { repo.gtaradio() } })
        importButtons(body)
        body.addBlock(button("Try demo stations") { runTask("Loading demo library") { repo.builtin() } })
    }
    private fun importButtons(body: LinearLayout) {
        body.addBlock(button("Import ZIP") { if(!busy) zipPicker.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream")) })
        body.addBlock(button("Choose content folder") { if(!busy) folderPicker.launch(null) })
        body.addBlock(button("Download pack from link") { if(!busy) input("Direct HTTPS / Drive download link",repo.prefs.getString("downloadUrl","").orEmpty()) { url -> repo.prefs.edit().putString("downloadUrl",url).apply(); runTask("Downloading pack") { repo.importUrl(url) } } })
    }
    private fun settings(body: LinearLayout) {
        body.addBlock(text("CONTENT LIBRARY",15f,cyan,true))
        val sourceLabel=when(repo.activeSource) { "builtin" -> "Built-in demo"; "asset:gtaradio.json" -> "GTA Radio Online"; else -> "External JSON / pack" }
        body.addBlock(text("Active library: $sourceLabel",17f,fg,true))
        body.addBlock(text("${repo.pack?.worlds?.size ?: 0} worlds · ${repo.pack?.stations?.size ?: 0} stations · ${repo.pack?.id ?: "No valid pack"} v${repo.pack?.version ?: "—"}",15f,muted))
        body.addBlock(text("Content location: ${if(repo.prefs.contains("tree")) "Selected document folder" else repo.activeRoot.path}\nFree space: ${repo.home.usableSpace/1048576} MB",13f,muted))
        if(busy) { busyView=text("Working…",18f,cyan); body.addBlock(busyView!!); return }
        importButtons(body)
        body.addBlock(button("Select / change external JSON") { jsonPicker.launch(arrayOf("application/json","text/plain","application/octet-stream")) })
        body.addBlock(button("Reload manifest / rescan") { runTask("Rescanning") { repo.reload() } })
        body.addBlock(button("Use GTA Radio Online") { runTask("Loading GTA Radio") { repo.gtaradio() } })
        body.addBlock(button("Use built-in demo") { runTask("Loading demo library") { repo.builtin() } })
        body.addBlock(text("PLAYBACK & DISPLAY",15f,cyan,true))
        body.addBlock(button("Mode: ${repo.prefs.getString("mode","live")}") { choose("Playback mode",listOf("live","archive")) { command("mode",it); repo.prefs.edit().putString("mode",it).apply(); render() } })
        body.addBlock(button("Daily seed: ${if(repo.prefs.getBoolean("dailySeed",true)) "enabled" else "disabled"}") { repo.prefs.edit().putBoolean("dailySeed",!repo.prefs.getBoolean("dailySeed",true)).apply(); render() })
        body.addBlock(button("Theme: ${repo.prefs.getString("theme","station")}") { choose("Theme",listOf("station","world","dark","light")) { repo.prefs.edit().putString("theme",it).apply(); render() } })
        body.addBlock(button("Car layout: ${repo.prefs.getString("car","auto")}") { choose("Car layout",listOf("auto","on","off")) { repo.prefs.edit().putString("car",it).apply(); render() } })
        body.addBlock(button("Pack status / errors") { AlertDialog.Builder(this).setTitle("Pack status").setMessage(if(repo.errors.isEmpty()) "No missing local audio reported. Remote assets are checked when loaded.\nOpen City Radio 0.2.0" else repo.errors.joinToString("\n")).setPositiveButton("OK",null).show() })
        body.addBlock(button("Clear imported library") {
            AlertDialog.Builder(this).setTitle("Clear imported packs?").setMessage("This deletes app-owned packs and downloaded assets. Your original ZIP and selected external folder are retained.").setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
                AlertDialog.Builder(this).setTitle("Confirm deletion").setPositiveButton("Delete app copies") { _,_->controller?.stop(); runTask("Clearing library") { repo.clear() } }.setNegativeButton("Cancel",null).show()
            }.show()
        })
        body.addBlock(text("GTA Radio Online streams are provided by gtaradio.net. Open City Radio is a fan-made client and is not affiliated with Rockstar Games, Take-Two, or GTA Radio. No account or telemetry is added by this app.",14f,muted))
        body.addBlock(button("Open gtaradio.net") { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://gtaradio.net/"))) })
        body.addBlock(text("Downloaded pack assets are cached locally. Drive sign-in pages must be downloaded manually. Imports and settings are intended for parked use.",14f,muted))
    }
    private fun choose(title: String,values: List<String>,done: (String)->Unit) { AlertDialog.Builder(this).setTitle(title).setItems(values.map { it.ifEmpty { "All" } }.toTypedArray()) { _,i->done(values[i]) }.setNegativeButton("Cancel",null).show() }
    private fun input(title: String,current: String,done: (String)->Unit) {
        val field=EditText(this).apply { setText(current); inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; setPadding(dp(20),dp(16),dp(20),dp(16)) }
        AlertDialog.Builder(this).setTitle(title).setView(field).setPositiveButton("Continue") { _,_->done(field.text.toString().trim()) }.setNegativeButton("Cancel",null).show()
    }
    private fun runTask(label: String,work: ()->Unit) {
        if(busy) return; busy=true; requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED; page="settings"; controller?.pause(); render()
        repo.progress={ value -> runOnUiThread { busyView?.text=value } }; busyView?.text=label
        lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching { work() } }
            busy=false; requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; repo.progress={}
            result.onSuccess { repo.prefs.edit().putBoolean("welcomed",true).apply(); command("reload"); page="worlds" }
                .onFailure { message(it.message ?: "Content operation failed. Retry or choose another file.") }
            render()
        }
    }
    private fun message(value: String) { if(!isFinishing) AlertDialog.Builder(this).setTitle("Open City Radio").setMessage(value).setPositiveButton("OK",null).show() }
}
