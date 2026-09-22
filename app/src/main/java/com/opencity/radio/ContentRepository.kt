package com.opencity.radio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class ContentRepository(private val context: Context) {
    val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
    val home = File(context.getExternalFilesDir(null) ?: context.filesDir, "RadioPacks").apply { mkdirs() }
    private val cache = File(context.filesDir, "downloaded-assets").apply { mkdirs() }
    val activeSource: String get() = prefs.getString("manifest", "asset:gtaradio.json") ?: "asset:gtaradio.json"
    val activeRoot: File get() = File(prefs.getString("root", home.path) ?: home.path)
    @Volatile var pack: RadioPack? = null; private set
    @Volatile var errors: List<String> = emptyList(); private set
    var progress: (String) -> Unit = {}

    private fun readText(input: InputStream): String = input.use {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer); if (count < 0) break
            require(output.size() + count <= 4 * 1024 * 1024) { "Manifest exceeds 4 MiB" }
            output.write(buffer,0,count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= 4 * 1024 * 1024) { "Manifest exceeds 4 MiB" }
        bytes.toString(Charsets.UTF_8)
    }
    @Synchronized fun reload(): RadioPack {
        val source = activeSource
        val text = when {
            source == "builtin" -> readText(context.assets.open("manifest.json"))
            source == "asset:gtaradio.json" -> readText(context.assets.open("gtaradio.json"))
            source.startsWith("content:") -> readText(context.contentResolver.openInputStream(Uri.parse(source)) ?: error("JSON permission lost; select the file again"))
            else -> readText(File(source).inputStream())
        }
        val parsed = ManifestParser.parse(text)
        pack = parsed
        errors = parsed.stations.mapNotNull { station ->
            if (station.audio.source in setOf("pack","zip")) runCatching { localAsset(station.audio) }
                .exceptionOrNull()?.let { "${station.name}: audio missing; select its content folder or import ZIP" } else null
        }
        return parsed
    }
    @Synchronized fun builtin() { prefs.edit().putString("manifest","builtin").remove("tree").apply(); reload() }
    @Synchronized fun gtaradio() { prefs.edit().putString("manifest","asset:gtaradio.json").remove("tree").apply(); reload() }
    @Synchronized fun external(uri: Uri) {
        val parsed = ManifestParser.parse(readText(context.contentResolver.openInputStream(uri) ?: error("Cannot read JSON")))
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString("manifest",uri.toString()).apply(); pack = parsed; reload()
    }
    @Synchronized fun folder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val dir = DocumentFile.fromTreeUri(context,uri) ?: error("Cannot access folder")
        val manifest = dir.findFile("manifest.json") ?: error("Select folder containing manifest.json")
        val parsed = ManifestParser.parse(readText(context.contentResolver.openInputStream(manifest.uri) ?: error("Cannot read manifest")))
        prefs.edit().putString("tree",uri.toString()).putString("manifest",manifest.uri.toString()).apply()
        pack = parsed; reload()
    }
    @Synchronized fun importZip(input: InputStream): RadioPack {
        val staging = File(home,"import-${UUID.randomUUID()}")
        try {
            SafeZip.extract(input,staging) { progress("Extracting ${it / 1048576} MB") }
            val manifests = staging.walkTopDown().filter { it.isFile && it.name == "manifest.json" }.toList()
            require(manifests.size == 1) { "ZIP must contain exactly one manifest.json" }
            val manifest = manifests.single()
            val parsed = ManifestParser.parse(readText(manifest.inputStream()))
            // Commit only after structural validation. Missing audio is reported, not silently hidden.
            prefs.edit().putString("manifest",manifest.path).putString("root",manifest.parentFile!!.path).remove("tree").apply()
            pack = parsed
            return reload()
        } catch (e: Exception) { staging.deleteRecursively(); throw e }
    }
    fun importUrl(url: String) {
        val file = File(context.cacheDir,"pack-${UUID.randomUUID()}.zip")
        try { download(url,file,SafeZip.MAX_TOTAL); importZip(file.inputStream()) } finally { file.delete() }
    }
    private fun localAsset(asset: Asset): Any {
        require(asset.path.isNotBlank()) { "Asset path missing" }
        SafeZip.resolve(activeRoot,asset.path) // Apply the same traversal rules to SAF paths.
        if (activeSource == "builtin" && asset.path.startsWith("demo/")) {
            context.assets.open(asset.path).close()
            return "file:///android_asset/${asset.path}"
        }
        val tree = prefs.getString("tree",null)
        if (tree != null) {
            var node = DocumentFile.fromTreeUri(context,Uri.parse(tree)) ?: error("Folder permission lost")
            asset.path.split('/').filter { it.isNotEmpty() && it != "." }.forEach { name ->
                require(name != "..") { "Unsafe folder path" }
                node = node.findFile(name) ?: error("Asset not found")
            }
            require(node.isFile) { "Asset is not a file" }; return node.uri
        }
        return SafeZip.resolve(activeRoot,asset.path).also { require(it.isFile) { "Asset not found" } }
    }
    @Synchronized fun resolve(asset: Asset): Any {
        if (asset.source in setOf("pack","zip")) return localAsset(asset)
        if (asset.isStream) return asset.url
        val identity = "${pack?.id}:${pack?.version}:${asset.url}"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(cache,hash)
        if (!file.isFile) download(asset.url,file,SafeZip.MAX_FILE)
        return file
    }
    private fun driveUrl(raw: String): String {
        val uri = Uri.parse(raw)
        if (uri.host != "drive.google.com") return raw
        val parts = uri.pathSegments
        val id = if (parts.size >= 3 && parts[0] == "file" && parts[1] == "d") parts[2] else uri.getQueryParameter("id")
        return if (id != null) "https://drive.google.com/uc?export=download&id=${Uri.encode(id)}" else raw
    }
    private fun download(raw: String, target: File, max: Long) {
        val partial = File(target.parentFile,"${target.name}.part")
        try {
            var address = driveUrl(raw)
            var connection: HttpURLConnection? = null
            for (redirect in 0..5) {
                require(URL(address).protocol == "https" && URL(address).userInfo == null) { "Use an HTTPS download link" }
                val c = URL(address).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
                val status = c.responseCode
                if (status in setOf(301,302,303,307,308)) {
                    val next = c.getHeaderField("Location") ?: error("Download redirect missing")
                    address = URL(URL(address),next).toString(); c.disconnect(); continue
                }
                if (status !in 200..299) { c.disconnect(); error("Download unavailable (HTTP $status). Use a direct link or import locally.") }
                connection = c; break
            }
            val c = connection ?: error("Too many redirects")
            try {
                require(!c.contentType.orEmpty().contains("text/html",true)) { "Link returned a sign-in/confirmation page. Download the pack manually and import ZIP." }
                val expected = c.contentLengthLong
                require(expected <= max) { "Download exceeds size limit" }
                require(target.parentFile!!.usableSpace > (if (expected > 0) expected else 0) + 16L * 1048576) { "Insufficient free storage" }
                c.inputStream.use { input -> partial.outputStream().use { out ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= max && target.parentFile!!.usableSpace > n + 16L * 1048576) { "Download exceeds storage limit" }
                        out.write(buffer,0,n); progress("Downloading ${total / 1048576} MB" + if (expected > 0) " · ${total * 100 / expected}%" else "")
                    }
                    require(total > 0 && (expected < 0 || total == expected)) { "Incomplete download; retry" }
                } }
                check(partial.renameTo(target)) { "Cannot save downloaded file" }
            } finally { c.disconnect() }
        } finally { partial.delete() }
    }
    @Synchronized fun clear() {
        home.deleteRecursively(); home.mkdirs(); cache.deleteRecursively(); cache.mkdirs()
        prefs.edit().remove("manifest").remove("root").remove("tree").remove("lastStation").remove("world").apply()
        gtaradio()
    }
    fun favorite(id: String): Boolean = prefs.getStringSet("favorites",emptySet())!!.contains(id)
    fun toggleFavorite(id: String) {
        val set = prefs.getStringSet("favorites",emptySet())!!.toMutableSet()
        if (!set.add(id)) set.remove(id)
        prefs.edit().putStringSet("favorites",set).apply()
    }
}
