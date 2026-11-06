# Changelog

## [Unreleased]

### Changed
- Liquid mode colours come from the artwork's top colours. The background is the top
  colour exactly as it comes - near-black covers included, one rule for everything - the
  ripples are the next top colours pushed apart in lightness until they stand off, and the
  lyrics are a white- or black-tinted shade of the cover's most saturated colour, carried
  by the shadow that is always under them. The black scrim is gone; it fought the colour.
- The palette is remembered per album for the life of the app, so re-delivered artwork
  (a new Bitmap object, a different size) can no longer change the colours mid-song, and
  the same album opens with the same colours every time. The drift on black-dominant
  covers is gone: no branch treats black differently any more.
- Lyrics in all nine Indic scripts - Devanagari, Bangla, Gurmukhi, Gujarati, Odia, Tamil,
  Telugu, Kannada and Malayalam - are set in Anek automatically, detected from the text,
  at real weights through the wght axis. Anek's even proportions across scripts keep
  mixed-language songs coherent. Akshara timing applies to all nine. The two Noto Indic
  fonts are gone.
- Settings is a short list of pages: the current song at the top, then Look, Fonts,
  Playback and motion, Lyrics and storage, Spotify, About and Debug. Every page is its
  own destination: the back arrow and the system back gesture return to Settings, live
  previews included. The long single scroll is gone.

### Fixed
- Songs with lyrics could show "No lyrics" for a day (Lush Life by Zara Larsson). When
  LRCLIB was slow or failed and the other sources had nothing, that counted as a miss and
  was remembered for 24 hours. Now it counts as unavailable and is asked again, and LRCLIB
  gets 15 seconds instead of 9.

## [1.1.4]

### Changed
- Word-timed lyrics now prefer Musixmatch, then NetEase, then Kugou. A faster, less trusted
  source no longer wins the race.
- The Instrumental pill goes away after 5 seconds. Its border runs down as the timer.
- On dark covers ripples are a soft swell of colour instead of hard lines.

### Fixed
- Swiping up to go home or to recents no longer changes the layouts. The song keeps its
  layout seed when the player drops out for a moment, and the stage has no long-press or
  swipe gestures left, only a tap for the controls. Lock a layout under Look in Settings.

## [1.1.3]

### Added
- Fonts in Settings, chosen separately for the lyrics and the app, with a preview. Lyrics:
  Unbounded (default), Bricolage, Big Shoulders, Syne, Darker Grotesque. App: Instrument Sans
  (default), Bricolage, Familjen Grotesk, Schibsted Grotesk, Darker Grotesque.
- Pools and Ripple strength sliders, with a live preview of the background.
- Glass lyrics: the sung words turn to glass, letting the background show through, with light
  on top and a fine rim. A Frost slider makes the glass milky.

### Changed
- Ripples are softer and the colour pools brighter, so the liquid reads again behind the text.
- Background colours are fixed per album: two songs from one album get the same colours.

## [1.1.2]

### Changed
- New typeface: Bricolage Grotesque, a grotesque with ink traps and a slightly hand-made
  feel, for the lyrics (its heaviest display cut) and for the whole app (text cuts for
  small type). Hindi, Bengali and Japanese lyrics keep their Noto fonts. Inter is gone, and
  the APK is smaller for it.

## [1.1.1]

### Fixed
- When notification access is turned off (or Android refuses it), the app shows the
  onboarding screen again instead of a stage that never gets lyrics.
- Spotify sync says why it paused (no Wi-Fi, battery saver, offline). Sync now runs on mobile
  data too.

### Changed
- Shorter wording in Settings and About; the Bluetooth delay has no explanation line.

## [1.1.0]

### New
- Two new layouts. Stamp: each word lands big and tilted, like a rubber stamp, and snaps into
  a tidy line. Spotlight: the whole line waits dimmed while a soft light glides from word to
  word in time. Both join Auto's choice (Stamp for punchy lines, Spotlight for slow ones) and
  the layout pills.
- Link Spotify (Settings) to save lyrics for your Liked Songs and playlists ahead of time:
  once a day on Wi-Fi, or with Sync now. Sign-in is on Spotify's own page; the app gets
  read-only access and keeps the tokens encrypted on the phone.
- About screen: version, licences, credits, links and a privacy note.
- Bluetooth delay (Settings, Lyrics): shifts the lyrics later by up to 0.5 s, only while
  Bluetooth audio is connected.

### Fixed
- Swiping up to switch apps could move a line to a different layout or make it blink. The
  stage is laid out again only once a new screen size has settled, while the app is in front.
- The sync buttons were reversed. Earlier and Later now move the lyrics that way, and the
  offset reads in words ("0.50 s later").
- The player's position is read again every few seconds while the stage is open, in case a
  report from the player was missed.

### Changed
- Settings shows the version under its title.
- In the background the app stops listening to audio, stops the background animation and
  ripples and closes the controls, to save battery. Lyrics keep being fetched and saved
  as songs change, and the stage stays ready in memory, so it is back at once.
- Fewer network requests: LRCLIB's search is sent only when the exact match misses, word
  timing rechecks are remembered for a week, and Kugou starts with the address that worked.
- Ambient ripples wake the phone less often; unused code removed.

## [1.0.11]

### New
- Settings, Look: a Ripples choice. Sung words (as before) makes a ripple for each sung
  word, and beats or a gentle pace when nothing is sung. Beats makes ripples only on the
  beat of the music, for every song, lyrics or not; new lines still sweep their light.
  Beats are heard through the microphone; without it, ripples come at a gentle pace.

## [1.0.10]

### New
- The liquid background keeps moving, with ripples, when there is nothing sung to ripple
  it: songs with no lyrics, instrumentals, unsynced lyrics, the intro and long gaps between
  lines. With audio sync on and the music audible (phone speaker), the ripples follow the
  beat; otherwise they come at a gentle, slightly irregular pace from random spots.

## [1.0.9]

### Fixed
- Ripples were hard to see when the liquid background is dark. They now glow in a light
  shade of the album's main colour, more strongly where the background is darkest.

## [1.0.8]

### Fixed
- The background and highlight colours could stray far from the album art. A cover with
  one or two strong colours had its missing accents filled with unrelated ones; they are
  now neighbours of the cover's own hue. Black-and-white and grey covers keep soft tones
  of their own instead of bright made-up colours. When a song's artwork arrives a moment
  after its title, the previous colours stay for that moment instead of flashing others.

## [1.0.7]

### Changed
- New look for everything around the lyrics: near-black screens, rounded cards, pill buttons
  and one accent colour taken from the playing song's album art. The lyrics, their
  animation and the liquid background are unchanged.
- Settings: a card for the playing song (artwork, lyrics source and timing, sync with the
  offset in seconds, Import), then Look, Motion and battery, Lyrics and Debug. Switches
  fill the whole row and carry a one-line hint; saved lyrics show their size and count.
- Controls: song and layout pills at the top; seek bar and playback buttons in one floating
  card above the gesture area, with a large play button in the accent colour.
- Onboarding: a larger headline, two numbered steps and one main button.
- Pop-ups on the stage have icons (searching, offline, instrumental, audio sync) and an
  Import button in the accent colour.

## [1.0.6]

### Fixed
- The stage could say "Offline" with the internet on, for example after Delete cache. The
  app trusted the last connection report from Android, which can be stale after the app
  was in the background. It now always tries the lookup and only says Offline when that
  fails and a fresh check finds no connection; once back online it retries at once.

## [1.0.5]

### Fixed
- Musixmatch word timing could still run early or late (Copines by Aya Nakamura, 1.5 s
  early). The check against LRCLIB's line timing now waits longer for LRCLIB, matches
  lines that are split differently, and when the lines are off by different amounts it
  corrects each line on its own, keeping the word spacing inside it. Word timing saved by
  1.0.4 or earlier is fetched again once.

## [1.0.4]

### Fixed
- Word-timed lyrics from Musixmatch could run 1.5 to 2.5 seconds off, when they were timed
  to a different release of the song. When LRCLIB has the same song line by line, the word
  timings are now measured against it and shifted into place. Word timings saved by
  earlier versions are fetched again once.

### New
- A fourth source, Kugou, with word-by-word timing for a large catalogue. More songs now
  get real word timing instead of estimates from LRCLIB's line timing.
- Held notes glow properly: a soft light behind the word that follows the voice through
  the note, a smooth change into and out of the glow, and a swell that builds as the note
  is held instead of jumping at its start.

## [1.0.3]

### Fixed
- Musixmatch had stopped giving word timings: its desktop app access was retired in
  September 2026 and now returns the same decoy for every song. LyricStage now uses the
  Android app access, refuses empty or all-zero tokens and keeps session cookies.
- NetEase was asked for lyrics in a way that could skip its word timings. It now always asks
  for the latest lyrics, word timings included.
- Songs cached with line timing are checked again for word timing the first time they play.

### New
- A small pop-up as each song starts names the lyrics source and its timing, for example
  "LRCLIB · line timed".
- Audio sync says when it cannot hear the music. Bluetooth earphones often send audio
  past the part of Android that apps can listen to.

## [1.0.2]

### Fixed
- 1.0.1 could crash on start: it checked the network connection without declaring the
  network-state permission. Connectivity checks now also fail safe.
- Releases now run lint and the app tests before publishing.

## [1.0.1]

### New
- Battery saver mode (Settings): pure black background so OLED pixels stay off, words
  simply fade in, and the screen only redraws when a word changes.
- Lyrics cache shows its size and song count, with a Delete cache button. Imported .lrc
  files are kept.
- Look ahead on Wi-Fi only (Settings, on by default): the next songs in the queue are only
  fetched on Wi-Fi. The playing song still loads on mobile data.

### Battery
- Nothing is redrawn while music is paused or when there are no lyrics; drawing resumes on
  play or seek.
- The moving background runs at 30 fps and holds still while paused, for instrumentals and
  for songs without lyrics.
- The screen may sleep after two minutes of pause.
- Audio sync only listens while line-timed lyrics are on screen and playing.
- Instrumental songs stop all syncing work.

### Network and offline
- Cached lyrics work offline as before; with no connection the app no longer retries every
  minute. Songs not yet cached show "Offline" and load by themselves when the connection
  returns.
- Songs with no lyrics anywhere are remembered for 24 hours, so they are not looked up again
  on every play.
- The Musixmatch token is kept between app starts.

### Other
- Release builds strip unused library code: a smaller APK, without the leftover phone-ID and
  screen-capture references some scanners flagged.

## [1.0.0]

### New
- Two more lyrics sources, NetEase Cloud Music and Musixmatch, asked at the same time as
  LRCLIB. Word-by-word timings win when any of them has them. Songs already cached with
  line timing are checked once for a word-timed version.
- Held notes lift, grow slightly and glow while they are sung, then settle back.
- Highlights go to the words that carry a line: emotionally loaded words, held notes,
  line endings and the song's hooks, instead of random picks.
- Optional audio sync (Settings): nudges estimated word timings towards vocal onsets heard
  in the playing audio. Needs the microphone permission; nothing is recorded or sent.
- Settings opens from a ⋮ button in the top right.

### Changed
- Body text takes a light tint of the album art's main hue; highlight colours are kept
  bright enough to read on the background.
- Better word estimates: line endings are held longer and short filler words go by faster.
- Status messages ("No lyrics found" and the rest) are small pop-ups at the bottom.
- Settings: headings visible again, values shown beside the sliders, a Reset for sync, and
  no explanation text.
- The layout picker sits under the song title, and the controls keep clear of the swipe-up
  gesture area.

### Fixed
- Releases failed to build because a cancelled lyrics lookup kept reading its response
  until it finished. Cancelling now stops the download at once.

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
