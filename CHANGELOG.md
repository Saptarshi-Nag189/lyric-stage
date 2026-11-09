# Changelog

## [2.6.1] - 2026-11-09

### Fixed
- The queue plays in Spotify and YouTube Music, not only in Apple Music. A row used to be
  switched off unless the player advertised queue jumping - Apple Music does, the others
  don't - so the songs sat there and could not be tapped. A tap now sends the jump
  whatever the player says about itself, waits to see whether the player answered, and
  one that didn't is asked again by the song's own media id before the app gives up and
  says so, naming the player.

### Changed
- Beat ripples, redone. The listener no longer averages itself out of hearing a steady
  drum: the threshold is the median of the last few seconds plus its spread, so a beat
  that repeats keeps clearing it, and a flat drone is refused instead of triggering it.
  Onsets carry their loudness, so a heavy beat lands harder than a light one and every
  fourth beat is accented. They rise from alternating sides of the lower stage, in time
  with the music rather than from random spots, and when the beats hold a tempo one the
  microphone missed still lands softly on the grid. In the glass a beat now reads as one
  broad pulse - quicker to spread and fade, bending the field less and tinting it more -
  where a sung word keeps its tighter, busier ring.
- The queue list is plain: no numbers in front of the songs. The one playing keeps its
  note marker.
- The liquid glass is closer to the material it imitates, within the same budget. The
  panels are lit from the top left rather than straight above, carry a bright hairline
  at their edge, and keep more of the field's colour under their darkening and milk.
  Frosted lyric letters sample the field six around one instead of four on the diagonal,
  and reach further, so frost reads as blur rather than a pale wash. The rim refraction,
  the dispersion and the single shared field are kept as they were: the reference
  implementation pays for its look with three full displacement passes per frame, which
  a phone GPU filling the whole stage is not being asked to do.
- Settings says less. The explanations under the layout lock, Auto rotation, glass
  controls and the unsynced switch are gone; what stays is a handful of words where they
  carry something worth knowing, and the unsynced switch is called "Show unsynced
  lyrics" now, so it can drop its sentence.

## [2.5.0] - 2026-11-09

### Added
- Glass lyrics drawn the way Apple draws a liquid glass material. On the liquid background
  (Android 13 and later) a shader now looks at the whole lyric layer from outside: the rim
  of every letter is a bevel that bends the field behind it - refraction with a whisper of
  dispersion, blue bent more than red - while the middle is clear, a light from above
  catches the top of the rim and the underside sits in shade, and frost blurs and milks the
  body. Because the field is the background's own shader, evaluated live, the glass shows
  exactly what is behind each letter, ripples included. The drawing-based glass stays where
  there is no shader to look through (older Android, artwork and solid backgrounds).
- The line turns to glass while the controls are open, instead of fading back. The text
  seen behind the overlay is the glass font now - the unsung words too, etched at their dim
  opacity - and it melts in and out over a quarter second as the controls come and go.
- Glass controls, a toggle in Look, with Frost as a shared slider. The transport card and
  the queue become panes of the liquid field: the same moving colours, sampled at the
  panel's own rectangle so a ripple crossing the glass continues into the background,
  frosted by the slider, the rim bending what is behind it and lit from above. Off, on
  battery saver, or without the liquid background, they are the quiet dark card as before.
- Auto rotation, in Look: pick the layouts Auto chooses from. The chooser keeps its
  judgement of pace, length and chorus within whatever is left on; the last one stays on.

### Changed
- One line hands over to the next quietly: the band of light that used to sweep the glass
  when a line began is gone, and a leaving line fades away where it is instead of lifting
  and sliding out. Nothing slides and nothing flashes between lines; the per-word
  entrances are untouched.
- Settings, rethought. The layout lock sits at the top of Settings itself, the same pills
  as on the stage; Fonts is folded into Look as its Typeface section; and Look is grouped
  into Auto rotation, Background, Glass, Typeface and Size, in that order.
- The session tracker keeps following the player while the app is in the background or the
  screen is locked, so a song switched to away from LyricStage is already on the stage at
  return - its lyrics, palette and cover looked up while away. Nothing renders with the
  window gone: the stage's frame loop now stops unless the app is in front, and starts
  again the moment it is.
- Album art: the last eight covers are kept (was one), and covers that arrive as a file
  rather than a bitmap are decoded on their own thread, so a local player's metadata no
  longer stalls the main thread.

## [2.0.0] - 2026-11-09

### Reverted
- The background is the liquid glass of 1.1.7 again. The 1.1.8 rewrite traded it for a
  flat colour wash, and with it everything that made it read as glass and water: the
  undulating slab, the refraction, the rings that bent the glass as they travelled -
  ripples became nearly invisible - and the light band that swept with a new line became a
  dull flash. All of it is back exactly as it was, mockups included.

### Changed
- Glass lyrics look like glass now, not like a white border. A uniform bright outline was
  drawn around every letter; what glass actually shows is light with a direction: a bright
  edge along the top of the letters, a dark edge underneath, a translucent body the
  background shows through, and a soft sheen on the upper face. No outline anywhere.

### Fixed
- The Look preview follows the Glass lyrics slider both ways now. Going from solid to
  glass and back left the preview stuck on the last look, because the drawing did not
  refresh on every change of the slider; the preview now reads the settings as state that
  always redraws it.

### Added
- A queue, like a music player. The controls now have shuffle and repeat buttons flanking
  the transport, and a queue button next to Settings that opens the player's own queue:
  the song playing is marked, and tapping a song jumps the player to it. Reordering or
  removing songs stays in the player's own app - the media session does not offer it. The
  queue button appears only when the player publishes a queue (Spotify, YouTube Music and
  most local players do); shuffle and repeat work wherever the player honours them, and
  the buttons keep their own state, as most sessions do not report it back.
- Tablet layouts. On wide screens the controls and their card sit in a centred column
  instead of stretching edge to edge, the queue opens as a panel standing at the right of
  the stage rather than a sheet from the bottom, and Settings, About and onboarding keep
  their text in a readable column, not stretched across the screen. The stage itself has
  always laid out to the screen it is given.

## [1.1.8] - 2026-11-07

### Changed
- The lyrics lookup is a chain in a fixed order instead of a race: Musixmatch is asked
  first, then NetEase; Kugou is asked only when both of them came back with nothing, a
  miss or an error alike, so a less trusted source can never win by being faster. LRCLIB
  is the last resort for the lyrics, but it starts at the same moment as Musixmatch:
  its line timings are what word timings made for a different release are corrected
  against, so the cross-check is kept without LRCLIB ever being waited for. The playing
  song and the background cache follow the same order, and fewer requests go out: when
  Musixmatch has the song, NetEase and Kugou are not asked at all.
- The liquid glass is redrawn the way Apple draws a background: the artwork's own
  colours in a soft, slowly drifting wash - as smooth as a blur, with no visible edge
  anywhere - a fine ripple in its own colour from each sung word, and a soft band of
  light when a new line begins. What is gone is everything that read as tricks: the
  undulating slab and its refraction, the dispersion fringes at the edges of the light,
  the specular glints and the saturation push. The colour is exactly what the cover has,
  so a grey cover stays grey with no special case, the wash can never blow a light cover
  out to white, and the filmic tail that the light lyric shade is measured against is
  untouched. The README mockups and the pre-Android-13 gradient follow the same look.
- One typeface for the app's own screens: Bricolage Grotesque, at real weights through
  its axes. The app font setting is gone, and with it Instrument Sans, Familjen Grotesk
  and Schibsted Grotesk - three font files out of the APK. The lyrics font stays a
  setting, with Bricolage as the new default and Unbounded, Big Shoulders, Syne and
  Darker Grotesque to pick instead on a Fonts page with a live sample. Anek stays for
  the nine Indic scripts, applied automatically from the text; Japanese and Korean keep
  the phone's own CJK typeface.
- Word estimates for line-timed lyrics are anchored at both ends of the line's slot:
  they start with the line and are stretched to end with it (up to 1.35x a natural
  pace; a longer gap is an instrumental break), instead of stopping at 1.1x and leaving
  the line's tail wordless. The fill now reads through the whole line.
- The Debug page keeps what diagnoses real problems - the FPS counter, the Now playing
  screen with the raw and smoothed clock, and the Fetched lyrics list - and drops
  FitCheck, a layout QA tool whose checks already run in the project's tests.

### Fixed
- Every notice pill goes away by itself after five seconds, its border running down as
  the timer, the way the Instrumental pill always has: No lyrics found, service
  unreachable, Offline and the new No synced lyrics. The bottom of the stage is never
  occupied for a whole song.
- Unsynced lyrics no longer take over the stage as a wall of text. A song whose lyrics
  have no timings now shows a short notice instead; a new Unsynced lyrics switch in
  Settings (off by default) scrolls the whole text with the song the way it used to.

## [1.1.7] - 2026-11-06

### Fixed
- A black-and-white cover stays black-and-white. The ripple colours carried a flat
  saturation floor, and a grey's hue is red by convention in HSL - so the greys picked up
  a pink tint - and the glass shader laid blue and amber dispersion fringes over
  everything. A grey now keeps its own (zero) saturation through the palette - the floor
  ramps in only past near-grey, so coloured covers are unchanged - and when every palette
  colour is a grey the shader works in luminance alone: no saturation push, dispersion
  folded onto plain luminance, ripple rings in grey. Light still bends; no colour appears
  that the cover never had. The ±1/255 chroma noise a JPEG greyscale carries is inside
  the same tolerance.
- The lyrics are always a light shade of the cover's own colour. The previous rule shaded
  by cover luminance and set a black-added tint on light covers - but the stage draws
  every background through one filmic tone map, which turns a light cover dark: yellow
  shows as olive, green as dark green, light grey as grey. Against those mid tones the
  black lyrics read as mud. Now: yellow lyrics on olive, light grey on grey, light greens
  on dark green, and the white shades that already read well (red, blue, dark covers) are
  unchanged. The highlight glint is light on every cover too, and the shade is floored at
  3:1 - the large-type bar - against the background as that tone map leaves it. Ripples
  still pick their side by WCAG luminance (red and blue lighter, green and yellow
  darker), so the waves stand off on every hue.
- The liquid glass shader is rebuilt. Its pools of colour were added straight onto the
  background, so a bright cover blew out to white and the type had nothing to sit on; and
  the dispersion term could take the colour below zero into pow(), which is NaN - garbage
  pixels on some GPUs. Pools and ripple rings now mix toward the palette's own colours -
  bounded, so no cover can wash out: light covers get darker olive/grey waves, dark
  covers lighter ones - and nothing reaches the tone map below zero. Ripple rings carry
  their own colour instead of white light, so they read on light glass too.
- The solid colour and animated gradient backgrounds now show the background through the
  same filmic tone map as the glass, so the lyric shade's 3:1 bar holds in every mode and
  a yellow cover is olive whichever background is set.
- The background clock no longer jumps when its speed changes (reduce motion turning on
  or off): it accumulates scaled time instead of scaling elapsed time after the fact, so
  ripples in flight keep their ages and the noise field stays continuous.
- The README mockups apply the shader's saturation push, tone map and dispersion fringes
  (folded to grey for a grey palette), so they show the colour the app draws; ripple
  rings carry the ripple colour as the shader now mixes them. The proof sheet's two rows
  compare the luminance rule's black lyrics on light covers with the always-light rule
  - yellow on olive, light grey on grey, light greens on dark green - across the
  black-and-white cover (light- and dark-dominant) and pure red, green, blue and yellow,
  the four colours that all sit at HSL lightness 0.5.

### Changed
- Japanese lyrics render in the phone's own CJK typeface (as Korean already does)
  instead of a bundled 9.2 MB font. The APK shrinks by roughly half, and the first
  Japanese line no longer waits for a 9 MB font to parse before it can draw.
- Library locale resources beyond English are stripped.

## [1.1.6] - 2026-11-06

### Fixed
- The stage no longer stutters when a song changes. Preparing a song's layouts (every
  word twice - once to fit, once at final size - plus every glyph for per-glyph templates)
  was one long single-threaded burst of text shaping while the stage waited blank; it now
  runs across up to four workers, and repeated words ("the", "you", ...) are laid out once
  and shared. The result is identical - only the time to it changed.
- Album artwork is no longer copied at full resolution to read its colours: a 3000×3000
  cover used to allocate ~36 MB per song change (and a hardware copy on the main thread
  in Artwork mode). Both the palette and the blurred background now draw the cover into a
  ~112×112 (or 24×24) software canvas, off the main thread - kilobytes instead of
  megabytes, milliseconds instead of a stall.

## [1.1.5] - 2026-11-06

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
