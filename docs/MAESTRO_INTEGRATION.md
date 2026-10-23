# Maestro Integration

## Pinned commit

| Item | Value |
|------|--------|
| Repository | https://github.com/NikolaiVChr/maestro |
| Branch | `main` |
| Submodule path | `third_party/maestro` |
| Pinned commit | `7aea828578a113f6c90ec4968f59871554a9e23f` |
| Commit subject | Added comment. |

Update this document whenever the submodule pointer moves.

## Why not consume Maestro’s Maven reactor as-is

Upstream `pom.xml` (artifact `LotroMaestro`):

- Uses nonstandard source roots: `src/` and `resources/` (not `src/main/java`)
- Targets Java 21 with required `--add-exports`
- Runs `maven-assembly-plugin` executions that build **Maestro**, **AbcPlayer**, and **AbcTools** fat JARs
- Disables the default `maven-jar-plugin` jar (`phase` = `none`), so there is **no** clean library artifact

Adding Maestro’s POM as a reactor module of ABC Music Manager would package those three applications — which we must not distribute.

## Chosen integration strategy

`abcmm-maestro-adapter` compiles Maestro sources via `build-helper-maven-plugin`
(additional source/resource roots pointing at `third_party/maestro`) and mirrors
upstream dependencies. Upstream files are never edited.

ABC Music Manager does **not** run Maestro assembly descriptors and does **not**
set Maestro/ABC Player/ABC Tools `Main-Class` manifests for distribution.

## Packages that appear necessary for ABC playback

| Package | Role |
|---------|------|
| `com.digero.common.abctomidi` | ABC → MIDI (`AbcToMidi`, `AbcInfo`, …) |
| `com.digero.common.abc` | LOTRO instruments, ABC fields/constants |
| `com.digero.common.midi` | Sequencer wrappers, synthesizer factory, mute/solo, volume, pan |
| `com.digero.common.util` | Soundfont download/helpers, utilities |
| `com.digero.common.view` / icons | Indirect compile deps from common utilities |

### Coupling caveat (blocker for “common-only” builds)

`com.digero.common` is **not** a standalone library. At the pinned commit:

- `AbcToMidi` / `LotroSequencerWrapper` reference `com.digero.maestro.abc.AbcExporter.ExportTrackInfo`
- `AbcInfo` references `com.digero.abcplayer.AbcPlayer` and `AbcExporter`
- `NoteFilterSequencerWrapper` references `com.digero.maestro.view.ProjectFrame`

Therefore compiling playback requires a **large slice** of the Maestro tree (including
maestro and abcplayer packages), not only `common`.

## Required Java module flags

```text
--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED
--add-exports=java.desktop/sun.awt.shell=ALL-UNNAMED
--patch-module java.desktop=<path>/java.midi.patch.jar
```

`--add-exports` is required to compile and run `SynthesizerFactory`
(`com.sun.media.sound.AudioSynthesizer` / `SoftSynthesizer`): 24-channel LOTRO
playback settings (latency, reverb off, polyphony, and the patched `linear release`
/ interpolation options). **Do not remove these flags.**

`--patch-module` is required at runtime for those synth options to take effect.
`abcmm-maestro-adapter` builds `target/java.midi.patch.jar` from
`third_party/maestro/patches/java.desktop` (same source/target 21 as Maestro's
`CompilePatches` target) during `generate-test-resources`. The JAR is not committed.
Surefire for the adapter and app, and `exec:exec@run-app`, pass
`--patch-module java.desktop=<path>/java.midi.patch.jar`. Windows `jpackage`
copies the JAR to `app/java.midi.patch.jar` and passes
`java.desktop=$APPDIR/java.midi.patch.jar`. `.mvn/jvm.config` stays exports-only:
Maven reads it before the JAR exists. `exec:java` reuses that Maven JVM, so
playback launches should install first (`mvn -pl abcmm-app -am install -DskipTests`)
and then use `mvn -pl abcmm-app exec:exec@run-app`.

## Required resources

From Maestro `resources/` (copied onto adapter classpath):

- `resources/com/digero/common/**` (icons, MIDI kit tables, note durations, …)
- `resources/uitext*.properties`
- Version property files under `resources/com/digero/*/version.properties`

## Soundfont handling

Maestro behavior (reuse; do not redesign):

1. Prefer user-supplied `LotroInstruments.sf2` beside the app / JAR (`SynthesizerFactory`)
2. Otherwise use `SoundFontDownloader.ensureSoundFontExists()` into a shared directory:
   - Windows: `%LOCALAPPDATA%\MaestroCommon`
   - macOS: `~/Library/Application Support/MaestroCommon`
   - Linux: `~/.local/share/maestro-common` (or `$XDG_DATA_HOME/maestro-common`)
3. Download URL and SHA-256 are hardcoded in `SoundFontDownloader` at the pinned commit:
   - URL: `https://github.com/NikolaiVChr/mver/releases/download/v4.6.24/LotroInstruments.sf2`
   - SHA-256: `09c081c183227615937377dd97cca8a2fa1f7b9a60e4909491ebe402caded535`
   - Cached file: `LotroInstruments_09c081c1.sf2` (hash prefix, so an older soundfont file is left in place)

`LotroAbcPlaybackEngine` calls `SoundFontDownloader.ensureSoundFontExists()` and does not hardcode a second URL. This edition does **not** bundle the ~200MB soundfont.

## How ABC Music Manager uses Maestro code

Via `abcmm-maestro-adapter` only:

- Parse/load ABC through Maestro APIs wrapped as `AbcPlaybackEngine`
- Drive play / pause / stop / panic / seek / mute / solo / volume / tempo
- Rely on `LotroSequencerWrapper.injectPatchChanges` for seek / 16+ channel patch+pan restoration

Public types returned to the app are ABCMM domain objects only.

**Playback wiring:** Production factory `MaestroPlaybackEngines.create()` returns
`LotroAbcPlaybackEngine` (falls back to `StubAbcPlaybackEngine` only if MIDI is
unavailable). UI controls live in `PlaybackPanel` and talk to the domain
`PlaybackSession` / `AbcPlaybackEngine` APIs — Digero widgets are not embedded.

## How we avoid packaging Maestro applications

| Action | Policy |
|--------|--------|
| Maestro assembly descriptors | Not executed |
| Main-Class for Maestro/AbcPlayer/AbcTools | Not set for ABCMM distribution |
| Installer shortcuts / file associations | Not created for those apps |
| User guidance | README / notices direct users to NikolaiVChr’s Maestro for editing |

## Unresolved integration questions

1. Can upstream eventually publish a library JAR (no app Main-Class, default jar enabled) to simplify consumption?
2. Can `ExportTrackInfo` / status-feed coupling be reduced upstream so `common` compiles without `maestro`/`abcplayer` UI?
3. Should ABCMM share Maestro’s `MaestroCommon` soundfont cache as-is, or prefer an ABCMM-owned data directory while still calling Maestro loaders?
4. ~~Exact minimal include/exclude set of Maestro sources if full-tree compile proves too heavy or fragile~~ — **Resolved:** full-tree compile of 231 sources on `main` succeeded; revisit excludes only if upstream coupling or build time becomes painful
5. Headless CI strategy for playback tests without an audio device (deferred)
6. ~~When to replace `StubAbcPlaybackEngine` with a real `LotroSequencerWrapper`-backed implementation (next playback milestone)~~ — **Done:** `LotroAbcPlaybackEngine` is the default from `MaestroPlaybackEngines.create()`.

## Evaluating future upstream updates

1. Diff `main` from the pinned SHA
2. Check changes to synthesizer flags, soundfont URL/hash, sequencer seek behavior, and package coupling
3. Update submodule, rebuild, run `mvn verify`, then playback smoke tests
4. Record the new SHA in this document

## First-task adapter status

**Maestro source compilation:** Succeeded via `build-helper-maven-plugin`
(231 upstream `.java` files + resources into `abcmm-maestro-adapter`). Required
`--add-exports` flags are configured on the compiler, Surefire, `.mvn/jvm.config`
(for `exec:java`), and `exec:exec@run-app`. The MIDI patch JAR is built beside
that and passed to Surefire (adapter and app) and `exec:exec@run-app`.

**Playback wiring:** `LotroAbcPlaybackEngine` wraps `AbcToMidi` /
`LotroSequencerWrapper` / `VolumeTransceiver`. It calls `LocaleManager.init()` and
`UIText.init()` before loading ABC, because `AbcInfo` reads `UIText` from its
static initializer. The stub remains available via
`MaestroPlaybackEngines.createStub()` for tests and MIDI-unavailable fallback.

**Packaging:** ABCMM does not run Maestro assembly descriptors. Only
`com.aevoreth.abcmm.AbcMusicManagerMain` is set as Main-Class on `abcmm-app`.
Upstream entry points (`MaestroMain`, `AbcPlayer`, `AbcTools`) may exist as
`.class` files inside the adapter JAR due to compile coupling; they are not
launched or shipped as separate applications.
