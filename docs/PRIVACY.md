# Privacy policy — Aira

Aira is a client for YouTube Music. It has no accounts, no telemetry of its own and
no advertising. This page describes everything the application can send over the
network and what leaves your device.

## What is never collected

- No analytics or tracking SDKs.
- No advertising identifiers.
- No account or profile data — Aira does not have accounts.
- No listening history is uploaded anywhere by default.

## What is sent over the network

| Destination | When | What is sent |
| --- | --- | --- |
| YouTube Music (`music.youtube.com`) | Always, when browsing and playing | The same requests a browser makes: search queries, browse IDs, audio stream requests. Requires your YouTube cookies/session for playback. |
| Lyrics providers (e.g. LRCLIB, Google/YouTube lyrics endpoints, Musixmatch-backed services) | When you open lyrics or the app shows synced lyrics | Track title, artist, album and duration, needed to find the lyrics. |
| Last.fm (`ws.audioscrobbler.com`) | Only if you connect Last.fm in Settings | "Now playing" and scrobbles, authenticated with your own Last.fm session key. |
| ListenBrainz (`api.listenbrainz.org`) | Only if you connect ListenBrainz in Settings | "Now playing" and listens, authenticated with your own user token. |
| GitHub (`api.github.com`) | When you check for updates | Current app version. |
| Sentry | Only in builds that have a DSN configured, and only on crashes | Crash stack trace, app version, platform, device model. No file contents, no listening history, no credentials. |

Every third-party integration above is **opt-in** and can be disconnected in
Settings at any time. If you never enable Last.fm or ListenBrainz, no scrobbling
data leaves the device.

Downloads are stored locally on your device and are never uploaded.

## Data stored on your device

Settings, the local database (playback history used for statistics and the
listening heatmap), cached artwork, downloads and lyrics cache. All of it stays on
the device and is removed when you uninstall the app or clear its data.

## Children

The application is not directed at children and does not knowingly collect data
from them.

## Changes

Changes to this policy are published in this file in the repository; the history
of the file is public.

## Contact

Open an issue at <https://github.com/Nicoap/Aira/issues> or write to
lolneknikita@gmail.com.
