# Arc TV

An Android TV client for [Real-Debrid](https://real-debrid.com): browse your
downloads and torrents from the couch and play them with your favourite video
player (VLC, Just Player, …).

## Features

- **Device-code sign-in** — no typing credentials with the remote. The app shows
  a short code; you authorise it at `real-debrid.com/device` from your phone or
  computer.
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
