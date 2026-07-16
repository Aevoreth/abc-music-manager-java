# Architecture — ABC Music Manager (Java)

## Why a separate repository

The Java edition is a **standalone companion** with its own entry point, branding,
releases, and packaging. Keeping it in `abc-music-manager-java/` avoids coupling
build systems (Maven vs Python), prevents accidental breakage of the stable product,
and allows independent release cadence.

The intended sibling layout is:

```text
ABC Music Manager Development/
├── abc-music-manager/       # Python — stable product
└── abc-music-manager-java/  # Java — under development
```

## Why the Python edition remains stable

Until Java feature parity and data compatibility are proven, the Python/PySide6
application is the edition users should run. The Java project is a prototype and
must not be marketed as a drop-in replacement.

## Python repository as read-only behavior reference

`abc-music-manager/` is inspected for:

- Product scope and workflows
- Feature terminology
- Library scanning and metadata behavior
- Setlists, ABCP, songbook export
- Band / player / layout / part assignment
- Set Play and relay behavior
- Settings and data-folder conventions
- SQLite schema and migrations
- Edge cases and user-facing error wording where appropriate

It is **not** a build dependency, not a Git submodule of this repo, and must not
be modified by Java development work. Do not mechanically translate Python classes
into Java; reimplement behavior with Java-appropriate designs.

## Maestro as read-only upstream

Maestro (`third_party/maestro`, branch `java24`) provides maintained ABC parsing
and playback implementation. Rules:

- Do not edit files under `third_party/maestro/`
- Do not commit inside the submodule
- Prefer composition, wrappers, and adapters over forking Maestro classes
- Pin the submodule to an exact commit; evaluate updates deliberately

## Module layout

| Module | Responsibility |
|--------|----------------|
| `abcmm-app` | Standalone Swing application entry point and UI shell |
| `abcmm-domain` | Domain models and interfaces owned by ABC Music Manager |
| `abcmm-storage` | Future SQLite persistence and Python DB compatibility |
| `abcmm-maestro-adapter` | Sole module allowed to interact with Maestro / `com.digero.*` |

### Adapter boundary

```text
abcmm-app ──► abcmm-domain ◄── abcmm-storage
     │              ▲
     └──────────────┴── abcmm-maestro-adapter ──► Maestro sources (compile)
```

**Hard rule:** `abcmm-app`, `abcmm-domain`, and `abcmm-storage` must **not**
directly import `com.digero.*`. Only `abcmm-maestro-adapter` may.

Public APIs leaving the adapter use ABC Music Manager types only
(`AbcPlaybackEngine`, `LoadedSong`, `PartInfo`, `PlaybackState`, etc.).

## Inventory and testing of Python behavior

Product capabilities are inventoried in [PYTHON_PARITY.md](PYTHON_PARITY.md).
Future Java features should:

1. Identify the Python workflow and edge cases
2. Add focused Java tests for the intended behavior
3. Update the parity matrix status

Do not run Java tests against the Python repository or its databases in CI for
this prototype phase.

## SQLite compatibility approach

Goal: open existing user data under `~/.abc_music_manager/` (or
`$ABC_MUSIC_MANAGER_DATA`) where practical:

- `abc_music_manager.sqlite`
- `preferences.json`

Authoritative schema is the Python migration chain in
`abc_music_manager/db/schema.py` (`CURRENT_SCHEMA_VERSION = 12` at time of writing).
Note: `SCHEMA.md` in the Python repo may lag the code — prefer the migration list.

`abcmm-storage` opens an existing v12 database read-only and reads/writes
`preferences.json` with Python-compatible keys. It does not create or migrate
the schema yet. Do not invent an incompatible schema without documenting the
incompatibility and migration path.

## Maestro updates

1. Review `java24` changelog / commits
2. Update submodule pointer to a new SHA
3. Rebuild `abcmm-maestro-adapter` and run `mvn verify`
4. Smoke-test ABC load / play when playback is implemented
5. Record the new pin in [MAESTRO_INTEGRATION.md](MAESTRO_INTEGRATION.md)

## Avoiding distribution of Maestro applications

Maestro’s upstream Maven build produces three fat JARs (Maestro, ABC Player,
ABC Tools). ABC Music Manager:

- Does **not** invoke those assembly descriptors
- Does **not** set those Main-Class manifests for distribution
- Compiles needed Maestro sources into the adapter for library use only
- Ships only the ABC Music Manager application entry point

Entry-point classes may exist on the compile classpath because of package coupling
inside Maestro; they must never be launched or advertised as part of ABCMM.

## Java module and synthesizer constraints

Maestro uses internal Java Sound APIs (`com.sun.media.sound`). Required JVM flags:

```text
--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED
--add-exports=java.desktop/sun.awt.shell=ALL-UNNAMED
```

These flags are preserved in compiler, Surefire, `.mvn/jvm.config`, and `exec:exec@run-app` configuration.
Do not remove them to “clean up” the build.

Soundfont loading follows Maestro’s established behavior (`SynthesizerFactory`,
`SoundFontDownloader`, shared `MaestroCommon` data directory). Do not redesign
the synthesizer during early milestones.
