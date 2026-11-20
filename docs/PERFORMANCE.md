# Desktop memory profile

Aira's desktop build targets a small resident set: the app is a music client,
so the heap holds only tens of megabytes of live objects. Everything above that
is native memory (Skia, VLC, JVM metadata) and has to be measured rather than
guessed.

## What is already capped

`compose.desktop.application.jvmArgs` in `desktopApp/build.gradle.kts`:

| Flag | Why |
| --- | --- |
| `-Xms48m -Xmn32m -Xmx384m` | Live set is ~72 MB. Default ergonomics committed ~490 MB: the initial heap is 1/64 of RAM and the young generation grows to a third of it, and every young-gen page touched between collections counts as resident. |
| `-XX:+UseSerialGC` | A G1 heap this small spends memory and threads on its own structures. |
| `-XX:CICompilerCount=3` | Two fewer JIT compiler threads (stack + code cache). |
| `-XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30` | Shrink the heap back after spikes instead of holding the peak. |

## VLC plugin pruning

VLC ships its full desktop build (~137 MB of plugins for the Windows x64
slice), while Aira only decodes audio and video streams and draws its own UI.
`composeApp/build.gradle.kts` prunes four plugin groups for every platform slice
(`prunedVlcPluginDirs` / `prunedVlcPluginPrefixes`, applied in
`extractWindowsVlcSlice`, `extractLinuxVlcSlice`, `extractMacVlcSlice`):

- `gui` — the Qt interface, skins and tray (~19 MB on Windows);
- `lua` — playlist/interpreter scripts;
- `services_discovery` — UPnP, SAP, SMB, NFS browsing;
- `visualization` — projectm / libvisual visualisers.

Measured on the Windows x64 slice: **137.1 MB → 114.2 MB** (−22.9 MB). The
decode path (`access`, `demux`, `codec`, `packetizer`, `audio_output`,
`video_*`, `spu`, `text_renderer`, `misc`, `stream_filter`,
`stream_extractor`, `meta_engine`, `keystore`, `logger`, `control`) is
untouched, and a live run starts playback with the pruned set.

## Image caches (Coil)

| Platform | Memory cache | Disk cache |
| --- | --- | --- |
| Desktop (`DesktopApp.kt`, `setSingletonImageLoaderFactory`) | 48 MB fixed | 512 MB in the temp dir |
| Android (`SimpMusicApplication.newImageLoader`) | 10 % of the app heap | 512 MB |

Coil defaults to 25 % of the max heap, which on this heap cap would be well over
100 MB of decoded covers; 48 MB holds ~100 covers at the sizes the UI asks for
(160 dp ≈ 320 px ≈ 400 KB each) and the rest comes back from the disk cache.

Skia's own image/picture caches are not exposed through a public API, so they
are not tuned here.

## Measuring the real split

The heap is capped, so any remaining growth is native. Turn on Native Memory
Tracking for a profiling run:

```bash
./gradlew :desktopApp:run -Pnmt          # adds -XX:NativeMemoryTracking=summary
jcmd <pid> VM.native_memory baseline     # once the UI is idle
# ...use the app: play a track, open the player, browse the library...
jcmd <pid> VM.native_memory summary.diff
```

`summary` splits `Java Heap`, `Class`, `Thread`, `Code`, `GC`, `Compiler`,
`Internal`, `Other`, `Symbol`, `Native Memory Tracking` and `Arena Chunk`.
For a packaged build, add `-Pnmt` when running `:desktopApp:packageMsi` and use
`jcmd` on the installed `Aira.exe` process.

NMT is off by default: it reserves extra memory and slows allocation slightly.

## Candidates not taken yet

Still present in the shipped runtime, dropped only if a measurement shows they
matter:

- `video_output`, `video_filter`, `video_chroma`, `video_splitter`, `spu` —
  only needed if the app plays video (music videos); ~10 MB.
- `access_output`, `stream_out`, `mux` — recording/streaming sinks; ~10 MB.
- `codec` (~45 MB) — a curated subset (h264/opus/vorbis/aac/mp3/vp9/av1) would
  cut most of it, but any missing codec breaks playback of some track.
