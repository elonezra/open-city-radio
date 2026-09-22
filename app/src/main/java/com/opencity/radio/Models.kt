package com.opencity.radio

import org.json.JSONObject

data class Asset(val source: String = "pack", val path: String = "", val url: String = "") {
    val isStream: Boolean get() = source == "stream"
}
data class World(val id: String, val name: String, val year: String, val universe: String,
                 val accent: String = "#65E9FF", val secondary: String = "#F36BCD", val background: Asset = Asset())
data class Station(val id: String, val worldId: String, val name: String, val frequency: String,
    val genre: String, val audio: Asset, val logo: Asset, val background: Asset,
    val durationMs: Long, val offsetMs: Long, val epochMs: Long, val dailySeed: Boolean,
    val accent: String, val secondary: String)
data class RadioPack(val id: String, val version: String, val worlds: List<World>, val stations: List<Station>)

object ManifestParser {
    fun parse(text: String): RadioPack {
        val root = JSONObject(text)
        fun required(o: JSONObject, key: String): String = o.optString(key).trim().also {
            require(it.isNotEmpty() && it != "null") { "Missing $key" }
        }
        fun asset(o: JSONObject, prefix: String): Asset {
            val source = o.optString("${prefix}Source", "pack").lowercase()
            require(source in setOf("pack", "zip", "url", "drive", "stream")) { "Unknown $prefix source" }
            val a = Asset(source, o.optString("${prefix}Path"), o.optString("${prefix}Url"))
            if (source in setOf("url", "drive", "stream")) {
                require(a.url.startsWith("https://")) { "$prefix requires an HTTPS URL" }
            }
            return a
        }
        val worlds = root.getJSONArray("worlds").let { list -> (0 until list.length()).map { i ->
            val w = list.getJSONObject(i)
            World(required(w,"worldId"), required(w,"displayName"), w.optString("year"),
                w.optString("universe", "Custom"), w.optString("accentColor", "#65E9FF"),
                w.optString("secondaryColor", "#F36BCD"), asset(w,"background"))
        } }
        require(worlds.isNotEmpty() && worlds.map { it.id }.distinct().size == worlds.size) { "World IDs must be nonempty and unique" }
        val stations = root.getJSONArray("stations").let { list -> (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            val world = worlds.firstOrNull { it.id == s.optString("worldId") } ?: error("Station references missing world")
            val audio = asset(s,"audio")
            require(audio.path.isNotBlank() || audio.url.isNotBlank()) { "Station needs audioPath or audioUrl" }
            Station(required(s,"stationId"), world.id, required(s,"displayName"), s.optString("frequency"),
                s.opt("genre").let { if (it is org.json.JSONArray) (0 until it.length()).joinToString(" · ") { n -> it.getString(n) } else s.optString("genre") },
                audio, asset(s,"logo"), asset(s,"background"), s.optLong("durationMs",0).coerceAtLeast(0),
                s.optLong("offsetMs",0), s.optLong("epochMs",0), s.optBoolean("dailySeedEnabled",false),
                s.optString("stationAccentColor",world.accent), s.optString("stationSecondaryColor",world.secondary))
        } }
        require(stations.isNotEmpty() && stations.map { it.id }.distinct().size == stations.size) { "Station IDs must be nonempty and unique" }
        return RadioPack(required(root,"packId"), required(root,"packVersion"),worlds,stations)
    }
}
