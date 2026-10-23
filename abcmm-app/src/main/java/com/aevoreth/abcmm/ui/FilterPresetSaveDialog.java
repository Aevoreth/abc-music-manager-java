package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.aevoreth.abcmm.domain.prefs.LibraryFilterPreset;

/**
 * Choose an existing library filter preset to overwrite, or type a new name.
 */
final class FilterPresetSaveDialog extends JDialog {

    private final JComboBox<String> overwriteCombo;
    private final JTextField newNameField = new JTextField(18);
    private final List<String> existingNames;
    private boolean saved;
    private String targetName;

    FilterPresetSaveDialog(Window owner, List<String> existingNames) {
        super(owner, "Save filter preset", ModalityType.APPLICATION_MODAL);
        this.existingNames = List.copyOf(existingNames);
        overwriteCombo = new JComboBox<>(this.existingNames.toArray(String[]::new));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        form.add(new JLabel("Overwrite"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(overwriteCombo, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("New name"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(newNameField, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        form.add(new JLabel("Leave the new name blank to overwrite the selected preset."), c);

        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> saveChoice());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    boolean wasSaved() {
        return saved;
    }

    String targetName() {
        return targetName;
    }

    private void saveChoice() {
        String typed = LibraryFilterPreset.normalize(newNameField.getText());
        String selected = String.valueOf(overwriteCombo.getSelectedItem());
        if (typed.isEmpty()) {
            if (!confirmOverwrite(selected)) {
                return;
            }
            targetName = selected;
            saved = true;
            dispose();
            return;
        }
        if (LibraryFilterPreset.isReservedName(typed)) {
            JOptionPane.showMessageDialog(
                    this,
                    "That name is reserved. Choose something other than Default, None, or "
                            + LibraryFilterPreset.MODIFIED_LABEL + ".",
                    "Save filter preset",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String existing = findExisting(typed);
        if (existing != null) {
            if (!confirmOverwrite(existing)) {
                return;
            }
            targetName = existing;
        } else {
            targetName = typed;
        }
        saved = true;
        dispose();
    }

    private boolean confirmOverwrite(String name) {
        String message = LibraryFilterPreset.DEFAULT_NAME.equals(name)
                ? "Overwrite the Default preset?"
                : "Overwrite preset \"" + name + "\"?";
        int choice = JOptionPane.showConfirmDialog(
                this,
                message,
                "Save filter preset",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.OK_OPTION;
    }

    private String findExisting(String name) {
        for (String existing : existingNames) {
            if (existing.equalsIgnoreCase(name)) {
                return existing;
            }
        }
        return null;
    }
}
