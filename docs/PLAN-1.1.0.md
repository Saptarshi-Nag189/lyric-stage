# LyricStage 1.1.0 plan

Work starts after 1.0.8. Order is the order of work.

## 1. Layout changes after swiping up to recent apps (bug, seen in 1.0.7)

Swiping up to switch apps and coming back can switch the line to a different layout.

Cause found in the code: the stage's layouts are prepared for a given screen size
(`LyricStage.kt`, `produceState(... size ...)`). The recents gesture shrinks the window, so
the stage is prepared again; while that runs the prepared stage is dropped (back to
null), so the line vanishes and replays its entrance, and the layout choice for the new
size can differ.

Plan:
- Keep showing the current prepared stage while a new one is prepared; swap without
  replaying entrances.
- Keep the chosen layout per line for the whole play of a song (keyed by line index and
  play seed), independent of the stage size; only the fitting (font size, rows) is redone.
- Ignore size changes while the app is not in the foreground; re-measure once on return.
- Test: choosing for the same lines at two sizes gives the same layout sequence.

## 2. Spotify link and library lyrics

Spotify Web API only, with PKCE (no client secret is needed or stored).

- Client ID `f64735d0c8694a96832a16bc4c4ba45a`, redirect `lyricstage://spotify-callback`.
- Scopes: `user-library-read`, `playlist-read-private`.
- Settings: "Link Spotify" card, showing the account name when linked, and Unlink.
- Library sync: reads Liked Songs and saved playlists, then fetches lyrics for them in the
  background, Wi-Fi only by default and a few songs at a time, so lyrics are ready before
  a song plays, offline too. Progress and a stop button in Settings.
- Tokens kept in encrypted prefs, excluded from backups; refresh token rotation handled.
- README privacy section updated with what is sent to Spotify.

## 3. A new lyrics layout

An eighth layout, unlike the current seven, and catchy. The current seven and their
animation stay as they are.

- First a mockup of two or three ideas to pick from, as moving previews.
- Ideas to mock:
  - Ribbon: the line rides a gently curving band that sweeps across the screen; each word
    rises off the band as it is sung.
  - Stamp: each word lands big and slightly rotated, like a stamp, then settles into a
    tidy line; held notes keep their stamp size until released.
  - Spotlight: the line is spread across the screen, dim; a soft light moves from word to
    word in time, and the lit word is drawn sharp and full colour.
- The chosen one goes into the core layout engine with FitCheck coverage, like the
  others, and into the layout chooser and the layout pills.

## 4. Smaller items carried over

- Bluetooth delay: one setting (for example +200 ms) applied only when Bluetooth audio
  is connected, for all songs.
- Clock check: re-read the player's position every few seconds and correct drift, in case
  a player (Spotify with crossfade) reports a stale position.
- README: redraw the stage mockups if the new layout is added.

## 5. Battery, network, speed and dead code

Found by reading the code; each item keeps every feature as it is. Nothing here changes
how the lyrics look or move.

Already done (in the next release): in the background the app stops audio listening,
the background animation, ripples and the controls' position updates. Lyrics are still
fetched and saved, and the prepared stage stays in memory. The lyrics drawing loop
already pauses by itself when the screen is not visible.

Battery
- Re-preparing the stage (fitting every line in every layout) runs on each window size
  change, also in the background and during the recents gesture. Skip it while in the
  background (same fix as item 1); this is the heaviest work the app does.
- Ambient ripples check every 60 ms even when no ripple is due. Sleep until the next
  gentle ripple is due, and wake on a detected beat instead of polling.

Network
- LRCLIB: every lookup sends the exact match request and a search request at the same
  time. Send the search only when the exact match finds nothing (most songs hit the
  exact match), saving about one request per song.
- Word-timing upgrade: every app start re-asks all four sources once for each song cached
  with line timing only, each time it plays. Remember the attempt for seven days.
- Kugou: when HTTPS fails it retries the same lookup over HTTP every time. Remember for
  the session which one works.

Speed
- With item 1's fix, returning to the app or rotating shows the stage at once instead of
  a blank moment while it is prepared again.
- The app creates every lyrics client at start. Create them on first use, for a slightly
  faster cold start.

Dead code (unused, to remove)
- String `settings_this_song` (the old Settings heading).
- `Colour.withAlpha`, `RowLayout.totalWidth`, `Pose.isIdentity`,
  `LineTiming.wordEnterAtMs`, `Spring.Soft`, `LyricsRepository.SOURCE_LRCLIB`.
- `ActiveSessionTracker.accessDenied` is set but never shown: either remove it, or use it
  to show onboarding again when notification access is turned off.

Leave as is
- Background lyrics fetching and queue look-ahead: this is the cache that makes lyrics
  instant, and it is already limited (Wi-Fi only for the queue, misses remembered 24 h).
- The palette is worked out per song in the background: cheap, and keeps colours ready.

