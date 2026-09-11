# Radio Pack manifest

A pack has one `manifest.json`. Unknown optional fields are ignored. Required identities must be nonempty; world/station IDs must be unique. A station must reference a declared world. One pack is active at a time.

```json
{
  "packId": "my-private-radio",
  "packVersion": "1",
  "worlds": [
    {
      "worldId": "coast-1986",
      "displayName": "Neon Coast",
      "year": "1986",
      "universe": "Custom",
      "accentColor": "#65E9FF",
      "secondaryColor": "#F36BCD",
      "backgroundSource": "pack",
      "backgroundPath": "art/coast.webp"
    }
  ],
  "stations": [
    {
      "stationId": "neon-fm",
      "worldId": "coast-1986",
      "displayName": "Neon FM",
      "frequency": "105.6",
      "genre": ["Pop", "Synth"],
      "audioSource": "pack",
      "audioPath": "audio/neon.wav",
      "logoSource": "pack",
      "logoPath": "art/neon.png",
      "durationMs": 2400000,
      "offsetMs": 12345,
      "epochMs": 0,
      "dailySeedEnabled": true,
      "stationAccentColor": "#65E9FF",
      "stationSecondaryColor": "#F36BCD"
    }
  ]
}
```

Replace example asset paths with real files; remove optional image paths when no image exists. `audioSource` defaults to `pack`. `zip` is an alias for `pack`. Audio requires a path or a remote URL. Local asset paths resolve relative to the manifest's pack root, not the Android working directory. Missing duration is allowed; the player uses the decoded file duration before live seeking.

Each of `audio`, `logo`, and `background` independently accepts these field groups:

| Field suffix | Example | Meaning |
|---|---|---|
| `Source` | `audioSource` | `pack`, `zip`, `url`, or `drive` |
| `Path` | `logoPath` | Relative path for pack assets |
| `Url` | `backgroundUrl` | HTTPS URL for remote assets |

Mixed sources work, for example `audioSource: "drive"`, `audioUrl: "https://..."`, and `logoPath: "art/logo.webp"`. Private URLs belong only in private manifests. Do not commit signed URLs, credentials, or real content packs.

The image loader uses content decoders with PNG/JPEG/WebP and SVG/GIF support, plus ImageDecoder formats supported by the installed Android version. Unsupported or corrupt images retain the native fallback. A remote world background can be specified using the same `backgroundSource`/`backgroundUrl` fields in the world object.

The flattened schema above is the implemented contract. Optional `themeId`, separate theme files, arbitrary nested manifests, custom animations, and checksum fields are not interpreted in this version. World and station colors/background fields supply the current theme. Numeric or string `packVersion` values are accepted.

## Broadcast time

`position = floorMod(nowUTC - epochMs + offsetMs + dailySeed, durationMs)`.

The daily term is a deterministic SHA-256-derived offset for station ID + UTC date. It is applied when tuning/resuming; an uninterrupted broadcast is not reseeked at midnight. Arithmetic is overflow-safe. Archive mode never applies live offsets. The global Daily Seed setting enables/disables station seeds; a station must also opt in with `dailySeedEnabled`.
