# Third-Party Notices

ABC Music Manager (Java) is independently developed and licensed under the MIT License.
See [LICENSE](LICENSE).

Copyright (c) 2026 Willow Aevoreth Rowan.

This Java edition is a continuation of the existing ABC Music Manager project
([Aevoreth/abc-music-manager](https://github.com/Aevoreth/abc-music-manager)).

---

## Maestro

ABC Music Manager uses open-source components from **Maestro** for LOTRO ABC
parsing and playback-related implementation.

| Item | Detail |
|------|--------|
| Original project | Created by Digero — https://github.com/digero/maestro |
| Maintained fork | Developed in NikolaiVChr’s repository — https://github.com/NikolaiVChr/maestro |
| Branch used | `java24` |
| License | MIT License (see `third_party/maestro/LICENSE.TXT`) |
| Upstream copyright | Copyright (c) 2013 Ben Howell |

Maestro remains a **separate project** and is the recommended application for
creating and editing LOTRO ABC music (MIDI-to-ABC conversion and advanced editing).

ABC Music Manager is a companion application. Inclusion of Maestro implementation
code does **not** imply endorsement by Digero, NikolaiVChr, or other Maestro
contributors.

ABC Music Manager packages only its own application entry point. It must not
distribute Maestro, ABC Player, or ABC Tools as separate applications, shortcuts,
or file associations, even when shared classes are compiled for playback support.

---

## FlatLaf

Look-and-feel library used by the Swing UI.

- Project: https://github.com/JFormDesigner/FlatLaf
- License: Apache License 2.0

---

## LotroInstruments.sf2 (runtime, not bundled in this prototype)

Maestro’s playback path expects a LOTRO soundfont (`LotroInstruments.sf2`), typically
resolved beside the application or via Maestro’s shared data directory download
flow. Soundfont distribution and licensing are governed by the soundfont’s own
source project (commonly referenced from https://github.com/NikolaiVChr/mver).

This prototype does not bundle the soundfont binary.

---

## Other Maven dependencies

Runtime and test dependencies (JUnit 5, ArchUnit, Apache Commons, ICU4J, and others
mirrored for Maestro compilation) are distributed under their respective open-source
licenses as declared in their Maven POM files.
