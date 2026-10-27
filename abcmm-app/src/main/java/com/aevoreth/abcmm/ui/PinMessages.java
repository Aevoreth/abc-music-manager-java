package com.aevoreth.abcmm.ui;

import java.util.List;

/**
 * Text for hard assignment pins. The marker is letters, so a pin is not shown by color alone.
 */
public final class PinMessages {

    public static final String MARKER = "PIN";

    private PinMessages() {
    }

    public static String pinnedPart(int partNumber) {
        return "Pinned: Part " + partNumber + " is fixed to this player.";
    }

    public static String conflict(String pinnedPlayer, String currentAssignment) {
        return "Pinned to " + pinnedPlayer + ". Current assignment is " + currentAssignment + ".";
    }

    public static String alreadyPinnedToOther(int partNumber, String otherPlayer, String player) {
        return "Part " + partNumber + " is already pinned to " + otherPlayer
                + ". Unpin that assignment before pinning " + player + ".";
    }

    public static String playerPinnedElsewhere(String player, int otherPart, int partNumber) {
        return player + " is already pinned to part " + otherPart
                + " in this song. Unpin that part before pinning part " + partNumber + ".";
    }

    public static String doesNotOwn(String player, String instrument, int partNumber) {
        return player + " does not own " + instrument + ", which part " + partNumber
                + " requires. The pin was not saved.";
    }

    public static String noInstrument(String player, int partNumber) {
        return player + " cannot be pinned to part " + partNumber
                + " because that part has no required instrument. The pin was not saved.";
    }

    public static String tooltip(String history, boolean marked, List<String> lines) {
        StringBuilder text = new StringBuilder();
        if (history != null && !history.isBlank()) {
            text.append(history.strip());
        }
        if (!marked && (lines == null || lines.isEmpty())) {
            return text.toString();
        }
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(MARKER);
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    text.append('\n').append(line);
                }
            }
        }
        return text.toString();
    }
}
