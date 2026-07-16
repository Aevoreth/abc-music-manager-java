package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.ParseException;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

/**
 * Duration spinner helpers: values stay in seconds, display/edit as {@code m:ss}.
 * Mouse-wheel over the minutes side steps by 1 minute; over the seconds side by 1 second.
 */
final class DurationSpinners {

    private DurationSpinners() {
    }

    static JSpinner create(int value, int minimum, int maximum, int stepSize) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, stepSize));
        installMinutesSecondsEditor(spinner);
        return spinner;
    }

    static void installMinutesSecondsEditor(JSpinner spinner) {
        MinutesSecondsEditor editor = new MinutesSecondsEditor(spinner);
        spinner.setEditor(editor);
        installSplitMouseWheel(spinner, editor.getTextField());
    }

    private static void installSplitMouseWheel(JSpinner spinner, JFormattedTextField field) {
        MouseWheelListener listener = e -> {
            if (!spinner.isEnabled() || e.getWheelRotation() == 0) {
                return;
            }
            int direction = e.getWheelRotation() < 0 ? 1 : -1;
            boolean overMinutes = isPointerOverMinutes(field, e);
            int deltaSeconds = overMinutes ? direction * 60 : direction;
            adjustSeconds(spinner, deltaSeconds);
            e.consume();
        };
        spinner.addMouseWheelListener(listener);
        for (Component child : spinner.getComponents()) {
            child.addMouseWheelListener(listener);
        }
        field.addMouseWheelListener(listener);
        for (Component child : field.getComponents()) {
            child.addMouseWheelListener(listener);
        }
    }

    private static boolean isPointerOverMinutes(JFormattedTextField field, MouseWheelEvent e) {
        Point inField = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), field);
        if (inField.x < 0 || inField.x >= field.getWidth()
                || inField.y < 0 || inField.y >= field.getHeight()) {
            // Outside the text field (e.g. spinner buttons): treat as seconds.
            return false;
        }

        String text = field.getText();
        if (text == null) {
            text = "";
        }
        int colon = text.indexOf(':');
        if (colon < 0) {
            return true;
        }

        FontMetrics metrics = field.getFontMetrics(field.getFont());
        Insets insets = field.getInsets();
        int textWidth = metrics.stringWidth(text);
        int available = field.getWidth() - insets.left - insets.right;
        int textStartX = switch (field.getHorizontalAlignment()) {
            case JTextField.LEFT, JTextField.LEADING -> insets.left;
            case JTextField.CENTER -> insets.left + Math.max(0, (available - textWidth) / 2);
            default -> insets.left + Math.max(0, available - textWidth);
        };
        int colonCenterX = textStartX
                + metrics.stringWidth(text.substring(0, colon))
                + Math.max(1, metrics.charWidth(':') / 2);
        return inField.x < colonCenterX;
    }

    private static void adjustSeconds(JSpinner spinner, int deltaSeconds) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            // Fall back to the last committed model value.
        }
        if (!(spinner.getModel() instanceof SpinnerNumberModel model)) {
            return;
        }
        int current = ((Number) model.getValue()).intValue();
        int minimum = ((Number) model.getMinimum()).intValue();
        int maximum = ((Number) model.getMaximum()).intValue();
        int next = Math.max(minimum, Math.min(maximum, current + deltaSeconds));
        if (next != current) {
            spinner.setValue(next);
        }
    }

    private static final class MinutesSecondsEditor extends JSpinner.DefaultEditor {
        MinutesSecondsEditor(JSpinner spinner) {
            super(spinner);
            JFormattedTextField field = getTextField();
            field.setEditable(true);
            field.setColumns(5);
            field.setHorizontalAlignment(JTextField.TRAILING);
            field.setFormatterFactory(new DefaultFormatterFactory(new MinutesSecondsFormatter()));
            field.setValue(spinner.getValue());
        }
    }

    private static final class MinutesSecondsFormatter extends DefaultFormatter {
        MinutesSecondsFormatter() {
            setOverwriteMode(false);
            setAllowsInvalid(true);
            setCommitsOnValidEdit(false);
        }

        @Override
        public Object stringToValue(String text) throws ParseException {
            Integer seconds = LibraryDisplayFormats.parseDurationToSeconds(text);
            if (seconds == null) {
                throw new ParseException("Invalid duration: " + text, 0);
            }
            return seconds;
        }

        @Override
        public String valueToString(Object value) {
            if (value == null) {
                return LibraryDisplayFormats.formatDuration(0);
            }
            return LibraryDisplayFormats.formatDuration(((Number) value).intValue());
        }
    }
}
