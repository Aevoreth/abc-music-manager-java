package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Date/time (+ optional note) picker for play-log Set… / edit flows.
 */
final class PlayDateTimeDialog extends JDialog {

    private final CalendarDatePicker datePicker = new CalendarDatePicker();
    private final JSpinner hourSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 23, 1));
    private final JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
    private final JTextField noteField = new JTextField(24);
    private boolean accepted;

    PlayDateTimeDialog(Window owner, String title, Instant initial, String initialNote, boolean showNote) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        LocalDateTime local = (initial == null ? Instant.now() : initial)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        datePicker.setDate(local.toLocalDate());
        hourSpinner.setValue(local.getHour());
        minuteSpinner.setValue(local.getMinute());
        noteField.setText(initialNote == null ? "" : initialNote);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Date"), c);
        c.gridx = 1;
        form.add(datePicker, c);
        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("Time"), c);
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timeRow.add(hourSpinner);
        timeRow.add(new JLabel(":"));
        timeRow.add(minuteSpinner);
        c.gridx = 1;
        form.add(timeRow, c);
        if (showNote) {
            c.gridx = 0;
            c.gridy = 2;
            form.add(new JLabel("Note"), c);
            c.gridx = 1;
            form.add(noteField, c);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            accepted = false;
            dispose();
        });
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            accepted = true;
            dispose();
        });
        buttons.add(cancel);
        buttons.add(ok);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    Optional<Result> showDialog() {
        setVisible(true);
        if (!accepted) {
            return Optional.empty();
        }
        LocalDate date = datePicker.getDate();
        if (date == null) {
            date = LocalDate.now();
        }
        LocalTime time = LocalTime.of((Integer) hourSpinner.getValue(), (Integer) minuteSpinner.getValue());
        Instant instant = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant();
        String note = noteField.getText() == null ? "" : noteField.getText().trim();
        return Optional.of(new Result(instant, note.isEmpty() ? null : note));
    }

    record Result(Instant playedAt, String contextNote) {
        String playedAtIso() {
            return playedAt.toString();
        }

        String displayLocal() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(playedAt.atZone(ZoneId.systemDefault()));
        }
    }
}
