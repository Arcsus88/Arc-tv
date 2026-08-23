# Arc TV

An Android TV client for [Real-Debrid](https://real-debrid.com) and
[AllDebrid](https://alldebrid.com): browse the catalogue, your downloads and
torrents from the couch and play them with your favourite video player (VLC,
Just Player, …).

## Features

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
