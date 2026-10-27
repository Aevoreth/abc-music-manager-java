package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.AssignmentPin;
import com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.optimizer.OptimizationProfileRepository;
import com.aevoreth.abcmm.domain.optimizer.PartKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight;

/**
 * Soft occurrence settings and the hard pin for one player on one setlist item.
 */
public final class OccurrenceOptimizerDialog {

    private OccurrenceOptimizerDialog() {
    }

    public static boolean show(
            Component parent,
            OptimizationProfileRepository profiles,
            long setlistId,
            long itemId,
            long playerId,
            String playerName,
            Integer partNumber,
            Long instrumentId,
            String instrumentName,
            boolean ownsInstrument,
            Map<Long, String> playerNames) throws LibraryException {
        return showAndReport(
                parent,
                profiles,
                setlistId,
                itemId,
                playerId,
                playerName,
                partNumber,
                instrumentId,
                instrumentName,
                ownsInstrument,
                playerNames);
    }

    private static boolean showAndReport(
            Component parent,
            OptimizationProfileRepository profiles,
            long setlistId,
            long itemId,
            long playerId,
            String playerName,
            Integer partNumber,
            Long instrumentId,
            String instrumentName,
            boolean ownsInstrument,
            Map<Long, String> playerNames) throws LibraryException {
        OccurrencePreferences preferences = profiles.listOccurrencePreferences(setlistId);
        List<AssignmentPin> pins = profiles.listPins(setlistId);
        String player = blankToName(playerName, playerId);

        JCheckBox prefer = new JCheckBox("Prefer " + player + " for this part");
        JSpinner penalty = new JSpinner(BandOptimizerForm.weightModel(1));
        JLabel penaltyLabel = new JLabel("Preference penalty");
        penaltyLabel.setLabelFor(penalty);
        JTextField itemKeep = new JTextField(8);
        JLabel itemKeepLabel = new JLabel("Song keep multiplier");
        itemKeepLabel.setLabelFor(itemKeep);
        JTextField partKeep = new JTextField(8);
        JLabel partKeepLabel = new JLabel("Part keep multiplier");
        partKeepLabel.setLabelFor(partKeep);
        JCheckBox pin = new JCheckBox("Pin " + player + " to this part");
        JLabel pinNote = new JLabel(" ");
        JLabel preferNote = new JLabel(" ");

        boolean hasPart = partNumber != null && partNumber > 0;
        prefer.setEnabled(hasPart);
        penalty.setEnabled(hasPart);
        partKeep.setEnabled(hasPart);
        pin.setEnabled(hasPart);
        if (!hasPart) {
            pinNote.setText("Pinning needs the part this player is assigned right now.");
        }

        for (ItemKeepWeight weight : preferences.itemKeepWeights()) {
            if (weight.setlistItemId() == itemId) {
                itemKeep.setText(format(weight.multiplier()));
            }
        }
        if (hasPart) {
            for (PartKeepWeight weight : preferences.partKeepWeights()) {
                if (weight.setlistItemId() == itemId && weight.partNumber() == partNumber) {
                    partKeep.setText(format(weight.multiplier()));
                }
            }
            for (PartPreferenceWeight weight : preferences.partPreferences()) {
                if (weight.setlistItemId() == itemId && weight.partNumber() == partNumber) {
                    if (weight.playerId() == playerId) {
                        prefer.setSelected(true);
                        penalty.setValue(weight.penalty());
                    } else {
                        preferNote.setText("This part currently prefers "
                                + name(playerNames, weight.playerId()) + ".");
                    }
                }
            }
            for (AssignmentPin existing : pins) {
                if (existing.setlistItemId() != itemId) {
                    continue;
                }
                if (existing.partNumber() == partNumber && existing.playerId() == playerId) {
                    pin.setSelected(true);
                } else if (existing.partNumber() == partNumber) {
                    pinNote.setText(PinMessages.alreadyPinnedToOther(
                            partNumber, name(playerNames, existing.playerId()), player));
                } else if (existing.playerId() == playerId) {
                    pinNote.setText(PinMessages.playerPinnedElsewhere(
                            player, existing.partNumber(), partNumber));
                }
            }
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        form.add(new JLabel("Blank keep fields mean no override. Existing assignments stay soft unless pinned."), gc);
        gc.gridy = 1;
        form.add(prefer, gc);
        gc.gridy = 2;
        form.add(preferNote, gc);
        gc.gridwidth = 1;
        gc.gridy = 3;
        form.add(penaltyLabel, gc);
        gc.gridx = 1;
        form.add(penalty, gc);
        gc.gridx = 0;
        gc.gridy = 4;
        form.add(itemKeepLabel, gc);
        gc.gridx = 1;
        form.add(itemKeep, gc);
        gc.gridx = 0;
        gc.gridy = 5;
        form.add(partKeepLabel, gc);
        gc.gridx = 1;
        form.add(partKeep, gc);
        gc.gridx = 0;
        gc.gridy = 6;
        gc.gridwidth = 2;
        form.add(pin, gc);
        gc.gridy = 7;
        form.add(pinNote, gc);

        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Optimizer settings for this song", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        cancel.addActionListener(e -> dialog.dispose());
        boolean[] saved = {false};
        save.addActionListener(e -> {
            try {
                save(
                        profiles,
                        setlistId,
                        itemId,
                        playerId,
                        player,
                        partNumber,
                        instrumentId,
                        instrumentName,
                        ownsInstrument,
                        playerNames,
                        preferences,
                        pins,
                        prefer.isSelected(),
                        ((Number) penalty.getValue()).doubleValue(),
                        itemKeep.getText(),
                        partKeep.getText(),
                        pin.isSelected());
                saved[0] = true;
                dialog.dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Optimizer settings",
                        JOptionPane.WARNING_MESSAGE);
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Optimizer settings",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton help = new JButton("Optimizer help…");
        help.addActionListener(event -> OptimizerHelpDialog.show(dialog));
        buttons.add(help);
        buttons.add(cancel);
        buttons.add(save);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return saved[0];
    }

    private static void save(
            OptimizationProfileRepository profiles,
            long setlistId,
            long itemId,
            long playerId,
            String player,
            Integer partNumber,
            Long instrumentId,
            String instrumentName,
            boolean ownsInstrument,
            Map<Long, String> playerNames,
            OccurrencePreferences preferences,
            List<AssignmentPin> pins,
            boolean prefer,
            double penalty,
            String itemKeepText,
            String partKeepText,
            boolean pinned) throws LibraryException {
        boolean hasPart = partNumber != null && partNumber > 0;
        if ((prefer || pinned) && !hasPart) {
            throw new IllegalArgumentException("Choose a part before setting a preference or pin.");
        }
        Double itemKeep = readOptional(itemKeepText, "Song keep multiplier");
        Double partKeep = hasPart ? readOptional(partKeepText, "Part keep multiplier") : null;
        if (pinned) {
            if (instrumentId == null) {
                throw new IllegalArgumentException(PinMessages.noInstrument(player, partNumber));
            }
            if (!ownsInstrument) {
                String instrument = instrumentName == null || instrumentName.isBlank()
                        ? "the required instrument"
                        : instrumentName;
                throw new IllegalArgumentException(PinMessages.doesNotOwn(player, instrument, partNumber));
            }
            for (AssignmentPin existing : pins) {
                if (existing.setlistItemId() != itemId) {
                    continue;
                }
                if (existing.partNumber() == partNumber && existing.playerId() != playerId) {
                    throw new IllegalArgumentException(PinMessages.alreadyPinnedToOther(
                            partNumber, name(playerNames, existing.playerId()), player));
                }
                if (existing.playerId() == playerId && existing.partNumber() != partNumber) {
                    throw new IllegalArgumentException(PinMessages.playerPinnedElsewhere(
                            player, existing.partNumber(), partNumber));
                }
            }
        }

        List<PartPreferenceWeight> partPreferences = new ArrayList<>();
        for (PartPreferenceWeight weight : preferences.partPreferences()) {
            boolean thisPart = hasPart
                    && weight.setlistItemId() == itemId
                    && weight.partNumber() == partNumber;
            boolean replaceThisPart = thisPart && (prefer || weight.playerId() == playerId);
            if (!replaceThisPart) {
                partPreferences.add(weight);
            }
        }
        if (prefer) {
            partPreferences.add(new PartPreferenceWeight(itemId, partNumber, playerId, penalty));
        }
        List<ItemKeepWeight> itemKeeps = new ArrayList<>();
        for (ItemKeepWeight weight : preferences.itemKeepWeights()) {
            if (weight.setlistItemId() != itemId) {
                itemKeeps.add(weight);
            }
        }
        if (itemKeep != null) {
            itemKeeps.add(new ItemKeepWeight(itemId, itemKeep));
        }
        List<PartKeepWeight> partKeeps = new ArrayList<>();
        for (PartKeepWeight weight : preferences.partKeepWeights()) {
            boolean samePart = hasPart
                    && weight.setlistItemId() == itemId
                    && weight.partNumber() == partNumber;
            if (!samePart) {
                partKeeps.add(weight);
            }
        }
        if (partKeep != null) {
            partKeeps.add(new PartKeepWeight(itemId, partNumber, partKeep));
        }
        List<AssignmentPin> nextPins = new ArrayList<>();
        for (AssignmentPin existing : pins) {
            boolean samePart = hasPart
                    && existing.setlistItemId() == itemId
                    && existing.partNumber() == partNumber
                    && existing.playerId() == playerId;
            if (!samePart) {
                nextPins.add(existing);
            }
        }
        if (pinned) {
            nextPins.add(new AssignmentPin(itemId, partNumber, playerId));
        }
        profiles.replaceOccurrencePreferences(
                setlistId, new OccurrencePreferences(partPreferences, itemKeeps, partKeeps));
        profiles.replacePins(setlistId, nextPins);
    }

    private static Double readOptional(String text, String label) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(trimmed);
            if (!Double.isFinite(value) || value < 0.0d) {
                throw new IllegalArgumentException(label + " cannot be negative.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static String name(Map<Long, String> names, long playerId) {
        if (names == null) {
            return "Player " + playerId;
        }
        return blankToName(names.get(playerId), playerId);
    }

    private static String blankToName(String name, long playerId) {
        if (name == null || name.isBlank()) {
            return "Player " + playerId;
        }
        return name.strip();
    }
}
