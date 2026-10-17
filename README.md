# Arc TV

An Android TV client for [Real-Debrid](https://real-debrid.com) and
[AllDebrid](https://alldebrid.com): browse the catalogue, your downloads and
torrents from the couch and play them with your favourite video player (VLC,
Just Player, …).

## Features

- **Netflix-style TV interface** — the menu runs across the top (Netflix's
  2025 TV layout), Home opens on a billboard with Play / More info / My List,
  and a details area at the top describes whatever has focus while the rows
  scroll beneath it, the focused row and tile always in the same place. A
  Top 10 row with outlined rank numerals, Continue Watching and My List
  (hearted titles). Back takes you up to the menu, and from there Home.
  Movies and Shows each have a search box beside their genre and sort
  pills; the magnifier in the menu searches films and shows together.
  Styled with the web app's dark theme: near-black ground,
  hairline borders, the Arc blue accent, Netflix's white focus.
- **Disney, Sky Sports, Netflix and Prime Video banners** — the web app's
  home banners, side by side: LEFT/RIGHT moves between them, OK opens Disney
  (every Disney and Pixar feature, pre-searched so Play starts at once), the
  guide on the UK sports channels, Netflix (the films and series on Netflix
  UK) or Prime Video (the films and series included with Prime Video UK).
  Each collection has its own section with search, sort and (Netflix, Prime
  Video) All / Films / Series.
- **TV guide** — as on the web: pick your channel groups, then each channel
  shows its logo over its Sky channel number, a still of what's on with a
  progress bar, and now and next. Listings, numbers, logos and stills come
  from Sky's own EPG where Sky carries the channel, else the Xtream panel's.
- **Code-based sign-in** — no typing credentials with the remote. Pick
  Real-Debrid or AllDebrid; the app shows a short code you authorise at
  `real-debrid.com/device` or `alldebrid.com/pin` from your phone or computer.
  Playback resolves TorBox-cached first, then Real-Debrid, then AllDebrid
  (resolved on the device — AllDebrid does not allow server IPs).
- **Live TV** — save multiple playlists (plain M3U links or Xtream logins) in
  the Settings tab and browse them by group with search. Xtream loads live
  channels via `player_api.php`, far faster than a full M3U export.
- **Settings tab** — connect/disconnect both debrid providers, set a TorBox
  token, and manage Live TV playlists.
- **Downloads grid** — your Real-Debrid downloads, newest first, with D-pad
  navigation, file size, host icon and date. Toggle to filter to streamable
  video files (mkv/mp4/avi).
- **External playback** — selecting an item hands the stream URL to any
  installed video player. Long-press to copy the link instead.
- **Torrents tab** — see torrent status/progress; for finished torrents pick a
  file, unrestrict it, and play.
- **Self-updating** — checks GitHub Releases on launch and offers to download
  and install new versions.

## Design captures

`RedesignScreens` renders the main screens on the JVM with real artwork and
today's Sky listings (no debrid account needed) and writes PNGs to
`.design-loop/redesign/`:

```sh
ARC_REDESIGN_SHOTS=1 ./gradlew testDebugUnitTest -Proborazzi.test.record=true \
  --tests 'com.arcsus.arctv.RedesignScreens'
```

## Building

```sh
./gradlew assembleDebug
```

Release builds are signed with a keystore configured via `keystore.properties`
(not committed — see `RELEASE_SIGNING.md`). Tagged pushes (`v*`) build and
publish a signed APK to GitHub Releases via GitHub Actions.

## Install on a TV

Sideload the APK (e.g. with *Send files to TV* or `adb install`). The app
appears on the Android TV home row.
