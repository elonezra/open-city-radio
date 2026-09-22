package com.opencity.radio

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ManifestParserTest {
    private fun sample() = JSONObject("""{
        "packId":"test","packVersion":1,
        "worlds":[{"worldId":"city","displayName":"City","year":"1986"}],
        "stations":[{"stationId":"fm","worldId":"city","displayName":"Test FM","audioPath":"audio/test.wav"}]
    }""")
    @Test fun missingDurationIsAllowed() { assertEquals(0L,ManifestParser.parse(sample().toString()).stations.single().durationMs) }
    @Test fun futureFieldsAreIgnored() { assertEquals("test",ManifestParser.parse(sample().put("futureFeature",true).toString()).id) }
    @Test fun numericVersionIsSupported() { assertEquals("1",ManifestParser.parse(sample().toString()).version) }
    @Test(expected=IllegalArgumentException::class) fun missingIdentityFails() { val j=sample();j.remove("packId");ManifestParser.parse(j.toString()) }
    @Test(expected=IllegalArgumentException::class) fun duplicateStationsFail() { val j=sample();j.getJSONArray("stations").put(j.getJSONArray("stations").getJSONObject(0));ManifestParser.parse(j.toString()) }
    @Test(expected=IllegalStateException::class) fun invalidWorldFails() { val j=sample();j.getJSONArray("stations").getJSONObject(0).put("worldId","missing");ManifestParser.parse(j.toString()) }
    @Test(expected=IllegalArgumentException::class) fun invalidRemoteUrlFails() { val j=sample();j.getJSONArray("stations").getJSONObject(0).put("audioSource","url").put("audioUrl","http://example.invalid/a.wav");ManifestParser.parse(j.toString()) }
    @Test fun mixedSourcesWork() { val j=sample();j.getJSONArray("stations").getJSONObject(0).put("audioSource","drive").put("audioUrl","https://example.invalid/a.wav").put("logoPath","logo.webp");val s=ManifestParser.parse(j.toString()).stations.single();assertEquals("drive",s.audio.source);assertEquals("pack",s.logo.source) }
    @Test fun liveStreamSourceWorks() { val j=sample();j.getJSONArray("stations").getJSONObject(0).put("audioSource","stream").put("audioUrl","https://audio.example.invalid/live");val s=ManifestParser.parse(j.toString()).stations.single();assertTrue(s.audio.isStream);assertEquals("https://audio.example.invalid/live",s.audio.url) }
    @Test(expected=IllegalArgumentException::class) fun insecureLiveStreamFails() { val j=sample();j.getJSONArray("stations").getJSONObject(0).put("audioSource","stream").put("audioUrl","http://audio.example.invalid/live");ManifestParser.parse(j.toString()) }
    @Test fun genreArraysWork() { val j=sample();j.getJSONArray("stations").getJSONObject(0).put("genre",org.json.JSONArray(listOf("pop","rock")));assertEquals("pop · rock",ManifestParser.parse(j.toString()).stations.single().genre) }
}
