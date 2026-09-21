package com.aevoreth.abcmm.ui;

import java.util.Objects;

import javax.swing.JTextField;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * In-field clear (delete) button for filter and search text fields.
 */
final class TextFieldClearButton {

    private TextFieldClearButton() {
    }

    /**
     * Shows FlatLaf's trailing clear button when the field is not empty.
     * Clicking it clears the text and places the caret in the field.
     */
    static void install(JTextField field) {
        Objects.requireNonNull(field, "field");
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK, (Runnable) () -> {
            if (!field.isEnabled() || !field.isEditable()) {
                return;
            }
            field.setText("");
            field.requestFocusInWindow();
            field.setCaretPosition(0);
        });
    }
}
