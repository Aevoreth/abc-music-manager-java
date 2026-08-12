package com.aevoreth.abcmm.ui;

/**
 * Application name, version, and About copy shared by the Help menu and User Guide title.
 */
public final class AppInfo {

    public static final String APP_NAME = "ABC Music Manager";
    public static final String COPYRIGHT = "Copyright (c) 2026 Willow Aevoreth Rowan";

    private AppInfo() {
    }

    /**
     * {@code Implementation-Version} from the package manifest, or {@code null} when
     * running unpackaged (IDE / {@code exec:java}).
     */
    public static String implementationVersion() {
        Package pkg = AppInfo.class.getPackage();
        if (pkg == null) {
            return null;
        }
        String version = pkg.getImplementationVersion();
        if (version == null || version.isBlank()) {
            return null;
        }
        return version.strip();
    }

    /** Version shown in About: packaged manifest value, otherwise {@code development}. */
    public static String displayVersion() {
        String version = implementationVersion();
        return version == null ? "development" : version;
    }

    public static String aboutTitle() {
        return "About " + APP_NAME;
    }

    /** Full About text (title line plus details), suitable for tests and plain message boxes. */
    public static String aboutMessage() {
        return APP_NAME + " — Version " + displayVersion() + "\n\n" + aboutDetails();
    }

    /** About copy below the dialog header (license, credits, disclaimer). */
    public static String aboutDetails() {
        return """
                Local-first desktop app for ABC music library and setlist management.

                %s
                Licensed under the MIT License. See LICENSE.

                Third-party components:
                • FlatLaf — Apache License 2.0 (Swing look and feel)
                • Maestro — ABC parsing and LOTRO playback (Digero / NikolaiVChr); MIT
                • LotroInstruments.sf2 — optional soundfont (NikolaiVChr/mver); not bundled
                • SQLite JDBC (Xerial) — Apache License 2.0
                • Jackson — Apache License 2.0
                • commonmark-java — BSD-2-Clause (in-app User Guide)

                Not affiliated with or endorsed by The Lord of the Rings Online.

                See THIRD_PARTY_NOTICES.md and Help → User Guide → Legal and attribution \
                for full details.
                """.formatted(COPYRIGHT);
    }
}
