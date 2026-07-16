package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Create dialog for setlist metadata.
 */
public final class SetlistDetailsDialog extends JDialog {

    static final int DEFAULT_SWITCH_DELAY_SECONDS = 20;

    private final JTextField nameField = new JTextField(28);
    private final JComboBox<LayoutChoice> layoutCombo = new JComboBox<>();
    private final CalendarDatePicker datePicker = new CalendarDatePicker();
    private final TimeChooser timeChooser = new TimeChooser();
    private final JSpinner targetDurationSpinner = DurationSpinners.createHoursMinutes(0, 0, 24 * 3600);
    private final JSpinner switchDelaySpinner = DurationSpinners.createPaddedInt(
            DEFAULT_SWITCH_DELAY_SECONDS, 0, 300, 1);
    private final JTextArea notesArea = new JTextArea(5, 28);
    private final JCheckBox lockedCheck = new JCheckBox("Locked (songs and order cannot be edited)");

    private Result result;

    private SetlistDetailsDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(420, 420));

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addLabeled(form, gc, row++, "Set Name", nameField);
        addLabeled(form, gc, row++, "Band Layout", layoutCombo);

        JPanel dateTimeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dateTimeRow.add(datePicker);
        dateTimeRow.add(new JLabel("Time"));
        dateTimeRow.add(timeChooser);
        addLabeled(form, gc, row++, "Set Date", dateTimeRow);

        JPanel durationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        durationRow.add(targetDurationSpinner);
        durationRow.add(new JLabel("Switch Delay (s)"));
        durationRow.add(switchDelaySpinner);
        addLabeled(form, gc, row++, "Target Duration", durationRow);

        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Set Notes"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.weighty = 1;
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(280, 100));
        form.add(notesScroll, gc);
        row++;

        gc.gridx = 1;
        gc.gridy = row;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(lockedCheck, gc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("OK");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        ok.addActionListener(e -> accept());
        buttons.add(cancel);
        buttons.add(ok);

        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
    }

    public static Optional<Result> showCreate(Window owner, BandRepository bands) {
        SetlistDetailsDialog dialog = new SetlistDetailsDialog(owner, "New setlist");
        dialog.loadLayouts(bands, null);
        dialog.datePicker.setDate(LocalDate.now());
        dialog.timeChooser.setTime(LocalTime.of(19, 0));
        dialog.targetDurationSpinner.setValue(0);
        dialog.switchDelaySpinner.setValue(DEFAULT_SWITCH_DELAY_SECONDS);
        dialog.lockedCheck.setSelected(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private void loadLayouts(BandRepository bands, Long selectedLayoutId) {
        layoutCombo.removeAllItems();
        layoutCombo.addItem(new LayoutChoice(null, "(none)"));
        if (bands != null) {
            try {
                for (BandInfo band : bands.listBands()) {
                    BandLayoutInfo layout = bands.getOrCreatePrimaryLayout(band.id());
                    layoutCombo.addItem(new LayoutChoice(layout.id(), band.name()));
                }
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Setlist",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        int select = 0;
        for (int i = 0; i < layoutCombo.getItemCount(); i++) {
            LayoutChoice choice = layoutCombo.getItemAt(i);
            if (Objects.equals(choice.id(), selectedLayoutId)) {
                select = i;
                break;
            }
        }
        layoutCombo.setSelectedIndex(select);
    }

    private void accept() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Set Name is required.", "Setlist", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LayoutChoice layout = (LayoutChoice) layoutCombo.getSelectedItem();
        int target = ((Number) targetDurationSpinner.getValue()).intValue();
        int delay = ((Number) switchDelaySpinner.getValue()).intValue();
        String notes = notesArea.getText();
        result = new Result(
                name,
                layout == null ? null : layout.id(),
                datePicker.getIsoDate(),
                timeChooser.getHhMm(),
                target <= 0 ? null : target,
                delay,
                notes == null || notes.isBlank() ? null : notes,
                lockedCheck.isSelected());
        dispose();
    }

    private static void addLabeled(JPanel panel, GridBagConstraints gc, int row, String label, java.awt.Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        panel.add(field, gc);
    }

    public record Result(
            String name,
            Long bandLayoutId,
            String setDate,
            String setTime,
            Integer targetDurationSeconds,
            int switchDelaySeconds,
            String notes,
            boolean locked) {
    }

    private record LayoutChoice(Long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
