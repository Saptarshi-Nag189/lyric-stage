# Changelog

## [0.2.0]

### New
- Lyrics are fetched in the background as songs change, plus the next two songs in the
  player's queue when it shares one, so they are usually ready the moment you open the app.
  Runs inside the notification listener Android already keeps alive; switch it off in
  Settings > Lyrics.
- Settings > Lyrics shows how many songs have lyrics saved and can clear downloaded lyrics
  (imported files are kept).

### Faster
- LRCLIB's exact lookup and search now run at the same time instead of one after the other.
- The first lookup starts as soon as the track is known, even while paused. Shorter
  timeouts (5 s to connect, 8 s per request) and fewer retries.
- A cached song is found even when a player reports its length a few seconds differently.
- The stage and the background prefetch share one lookup per song instead of fetching twice.

## [0.1.1]

### Changed
- The controls overlay is simpler: sync nudge and .lrc import moved to a "This song" section
  in Settings, and the lyrics fade back while the controls are open.
- Long runs of hiragana are split into short phrases even when the system word breaker
  returns them as one word.
- Lines now wrap onto whichever row count gives the largest text, so long Japanese or
  Flow lines no longer shrink into one small row.

### Fixed
- A line could be fitted at a size that row balancing then laid out slightly wider than
  the screen. FitCheck caught it for one Japanese line in the Slide layout.

## [0.1.0]

First release.

### New
- Follows whatever is playing on the phone (Spotify, YouTube Music, Apple Music, any player)
  once notification access is granted. Onboarding opens the right settings screen.
- Kinetic, word-synced lyrics: seven layouts (Stack, Slide, Zoom, Letters, Scatter, Graph,
  Flow) picked per line from pace, length and chorus repeats. Words fill left to right as they
  are sung; 1 to 3 words per line glow in colours taken from the album art.
- Lyrics from LRCLIB, cached on the phone and retried every minute while a song plays.
  Enhanced LRC word timings are used when present; otherwise timings are estimated
  (syllables for Latin text, aksharas for Hindi and Bengali).
- Romanised subtitle under Japanese, Hindi or Bengali lines when the lyrics include one.
- Liquid glass background on Android 13 and later, with ripples from each sung word.
  Animated gradient on older phones. Artwork blur or solid colour in settings.
- Tap the stage for controls: seek, play and pause, skip, layout picker, and a sync nudge
  (250 ms steps, saved per song). Long-press to lock the current layout.
- Import an .lrc file for any song. Unsynced lyrics scroll slowly and are clearly marked.
- Reduce motion setting (also automatic when system animations are off): fades only.
- Debug screens: now playing and clock, lyrics list, FitCheck.

### Known limits
- Not yet tested on many devices. Report anything that looks wrong.
