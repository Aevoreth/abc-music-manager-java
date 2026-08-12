# ABC Music Manager (Java)

Standalone Java edition of **ABC Music Manager** — a local-first desktop companion for *Lord of the Rings Online* musicians who manage ABC libraries, setlists, bands, and playback.

This is the **active edition** (beta). It is a full-function port of the former Python app and shares the same data folder. The last Python release is [abc-music-manager](https://github.com/Aevoreth/abc-music-manager) v0.2.9b (behavior and schema reference).

## Relationship to the Python application

The Python/PySide6 project remains the **behavior and schema reference**. This repository is an independent implementation with its own UI (Swing + FlatLaf), playback engine (Maestro Java), and Windows packaging. The Python app is not packaged into or run by the Java build. Both editions read and write `~/.abc_music_manager/` (SQLite v12 + `preferences.json`).

Capability inventory and remaining gaps: [docs/PYTHON_PARITY.md](docs/PYTHON_PARITY.md).

## Relationship to Maestro

ABC Music Manager uses open-source ABC parsing and playback implementation from the maintained Maestro project:

- Repository: [NikolaiVChr/maestro](https://github.com/NikolaiVChr/maestro)
- Required branch: `java24`
- Vendored as a read-only Git submodule at `third_party/maestro`

ABC Music Manager is a **companion** to Maestro, not a replacement. Users should continue to use [NikolaiVChr’s Maestro](https://github.com/NikolaiVChr/maestro) for MIDI-to-ABC conversion and advanced ABC editing.

This project must **not** package or expose Maestro, ABC Player, or ABC Tools as application launchers.

## Current status

Bandleader library and live-set edition (see [CHANGELOG.md](CHANGELOG.md)):

- Maven multi-module layout; Swing + FlatLaf (Flat Dark / Flat Light, Maestro/ABC Player themes)
- SQLite v12 create/migrate and shared `preferences.json` (read-write, interchangeable with Python)
- Library scan, filters/search, song detail (including ABC metadata write-back), duplicate review
- Setlists, set export, ABCP, PluginData / Songbook
- Players, bands, layout grids, setlist part assignments
- Maestro ABC audition (`LotroAbcPlaybackEngine`) with mute/solo, tempo, stereo, volume
- Set Play + Cloudflare relay + Band Assistant (`--assistant` or browser `/playback`)
- In-app User Guide (**Help → User Guide**)
- Windows zip + MSI via tag-push GitHub Actions

## Prerequisites

- **JDK 21** (compilation target)
- **Apache Maven 3.9+**
- Git (for submodule checkout)

## Workspace layout

```text
ABC Music Manager Development/
├── abc-music-manager/          # Python edition (sibling, read-only reference)
└── abc-music-manager-java/     # This repository
    ├── abcmm-app/
    ├── abcmm-domain/
    ├── abcmm-storage/
    ├── abcmm-maestro-adapter/
    ├── docs/
    └── third_party/maestro/    # Maestro java24 submodule
```

## Initialize the Maestro submodule

```bash
git submodule update --init --recursive
```

The submodule tracks branch `java24` and is pinned to a specific commit. See [docs/MAESTRO_INTEGRATION.md](docs/MAESTRO_INTEGRATION.md).

## Build

```bash
mvn verify
```

## Run

```bash
# Build modules, then launch (exports come from .mvn/jvm.config)
mvn -pl abcmm-app -am package -DskipTests
mvn -pl abcmm-app exec:java
```

Alternatively, spawn a dedicated JVM:

```bash
mvn -pl abcmm-app exec:exec@run-app
```

Required JVM module exports (configured in `.mvn/jvm.config`, Surefire, and `exec:exec@run-app`):

```text
--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED
--add-exports=java.desktop/sun.awt.shell=ALL-UNNAMED
```

Do not remove these flags; Maestro’s synthesizer uses internal Java Sound APIs.

## Releases

Pushing a version tag (`vX.Y.Z`) runs GitHub Actions packaging on Windows and publishes a GitHub Release with:

- `ABC-Music-Manager-<version>.zip` — portable self-contained app (bundled custom JRE)
- `ABC-Music-Manager-<version>.msi` — installer

End users do **not** need to install a JDK. Artifacts are currently unsigned (Windows SmartScreen may warn).

In-app help: **Help → User Guide** (also **Open User Guide** on an empty Library). Source pages live in [`docs/user/`](docs/user/index.md).

Local packaging (Windows, JDK 21 + [WiX 3.11](https://github.com/wixtoolset/wix3/releases) on `PATH`):

```powershell
mvn -pl abcmm-app -am package -DskipTests
.\distribute\package-windows.ps1 -Version 0.1.0 -Jar abcmm-app\target\abc-music-manager.jar
```

## Attribution

- ABC Music Manager — Copyright (c) 2026 Willow Aevoreth Rowan — MIT License
- Uses open-source components from Maestro (originally Digero; maintained by NikolaiVChr) — MIT License

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

This project does not claim endorsement by the Maestro maintainers.
