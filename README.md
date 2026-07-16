# ABC Music Manager (Java)

Standalone Java edition of **ABC Music Manager** — a local-first desktop companion for *Lord of the Rings Online* musicians who manage ABC libraries, setlists, bands, and playback.

> **Warning:** This Java edition is an early prototype. It is **not** a replacement for the stable Python release. Use [abc-music-manager](https://github.com/Aevoreth/abc-music-manager) for day-to-day work until Java parity is reached.

## Relationship to the Python application

The existing Python/PySide6 project (`abc-music-manager/`) remains the **stable product** and the authoritative behavior reference. This repository is an independent companion implementation under active development. The Python app is not packaged into or run by the Java build.

## Relationship to Maestro

ABC Music Manager uses open-source ABC parsing and playback implementation from the maintained Maestro project:

- Repository: [NikolaiVChr/maestro](https://github.com/NikolaiVChr/maestro)
- Required branch: `java24`
- Vendored as a read-only Git submodule at `third_party/maestro`

ABC Music Manager is a **companion** to Maestro, not a replacement. Users should continue to use [NikolaiVChr’s Maestro](https://github.com/NikolaiVChr/maestro) for MIDI-to-ABC conversion and advanced ABC editing.

This project must **not** package or expose Maestro, ABC Player, or ABC Tools as application launchers.

## Current status

Bandleader-first library prototype:

- Maven multi-module layout
- Swing + FlatLaf (Flat Dark / Flat Light, Maestro/ABC Player themes) with Library filters/search and Settings dialog
- Read-only open of existing Python SQLite v12 (`~/.abc_music_manager/`)
- Shared `preferences.json` load/save (theme, font size, default filters, LOTRO roots, …)
- Domain playback interfaces and Maestro adapter boundary (stub engine; audio deferred)
- Documentation for Python parity and Maestro integration

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

## Attribution

- ABC Music Manager — Copyright (c) 2026 Willow Aevoreth Rowan — MIT License
- Uses open-source components from Maestro (originally Digero; maintained by NikolaiVChr) — MIT License

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

This project does not claim endorsement by the Maestro maintainers.
