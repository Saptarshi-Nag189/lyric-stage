# LyricStage 1.1.0 plan

Work starts after 1.0.8. Order is the order of work.

## 1. Layout changes after swiping up to recent apps (bug, seen in 1.0.7)

Swiping up to switch apps and coming back can switch the line to a different layout.

Likely cause: the recents gesture shrinks the window, so the stage is measured again at
a new size. The layout chooser and FitEngine then run again for the new size, and the
choice for the current line can come out differently. The app may also be recreating the
stage when it is paused and resumed.

Plan:
- Reproduce in the FitCheck debug screen by changing the stage size mid-line.
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
