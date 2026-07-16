package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Mouse-wheel support for {@link JSpinner} controls across the app.
 */
final class SpinnerMouseWheel {

    private SpinnerMouseWheel() {
    }

    /** Step by the spinner model's next/previous value. */
    static void install(JSpinner spinner) {
        Objects.requireNonNull(spinner, "spinner");
        attach(spinner, e -> {
            if (!spinner.isEnabled() || e.getWheelRotation() == 0) {
                return;
            }
            Object next = e.getWheelRotation() < 0 ? spinner.getNextValue() : spinner.getPreviousValue();
            if (next != null) {
                spinner.setValue(next);
            }
            e.consume();
        });
    }

    /**
     * For number spinners whose text contains a colon (e.g. {@code m:ss}, {@code HH:MM}):
     * wheel left of the colon adjusts by {@code leftStep}, right by {@code rightStep}.
     */
    static void installNumberSplitByColon(JSpinner spinner, int leftStep, int rightStep) {
        Objects.requireNonNull(spinner, "spinner");
        JFormattedTextField field = textField(spinner);
        attach(spinner, e -> {
            if (!spinner.isEnabled() || e.getWheelRotation() == 0) {
                return;
            }
            int direction = e.getWheelRotation() < 0 ? 1 : -1;
            boolean left = field != null && isPointerLeftOfColon(field, e);
            int delta = direction * (left ? leftStep : rightStep);
            adjustNumber(spinner, delta);
            e.consume();
        });
    }

    /**
     * For {@link SpinnerDateModel} time editors ({@code HH:mm}): wheel left of colon
     * steps hours, right of colon steps minutes.
     */
    static void installDateTimeSplit(JSpinner spinner) {
        Objects.requireNonNull(spinner, "spinner");
        JFormattedTextField field = textField(spinner);
        attach(spinner, e -> {
            if (!spinner.isEnabled() || e.getWheelRotation() == 0) {
                return;
            }
            if (!(spinner.getModel() instanceof SpinnerDateModel)) {
                return;
            }
            int direction = e.getWheelRotation() < 0 ? 1 : -1;
            boolean left = field != null && isPointerLeftOfColon(field, e);
            adjustDateField(spinner, left ? Calendar.HOUR_OF_DAY : Calendar.MINUTE, direction);
            e.consume();
        });
    }

    private static void attach(JSpinner spinner, MouseWheelListener listener) {
        spinner.addMouseWheelListener(listener);
        for (Component child : spinner.getComponents()) {
            child.addMouseWheelListener(listener);
        }
        JComponent editor = spinner.getEditor();
        editor.addMouseWheelListener(listener);
        for (Component child : editor.getComponents()) {
            child.addMouseWheelListener(listener);
        }
    }

    private static JFormattedTextField textField(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            return editor.getTextField();
        }
        return null;
    }

    private static void adjustNumber(JSpinner spinner, int delta) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            // Fall back to the last committed model value.
        }
        if (!(spinner.getModel() instanceof SpinnerNumberModel model)) {
            return;
        }
        Number currentNumber = (Number) model.getValue();
        Number minimumNumber = (Number) model.getMinimum();
        Number maximumNumber = (Number) model.getMaximum();
        if (currentNumber instanceof Double || minimumNumber instanceof Double || maximumNumber instanceof Double) {
            double current = currentNumber.doubleValue();
            double minimum = minimumNumber.doubleValue();
            double maximum = maximumNumber.doubleValue();
            double next = Math.max(minimum, Math.min(maximum, current + delta));
            if (next != current) {
                spinner.setValue(next);
            }
            return;
        }
        int current = currentNumber.intValue();
        int minimum = minimumNumber.intValue();
        int maximum = maximumNumber.intValue();
        int next = Math.max(minimum, Math.min(maximum, current + delta));
        if (next != current) {
            spinner.setValue(next);
        }
    }

    private static void adjustDateField(JSpinner spinner, int calendarField, int direction) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            // Fall back to the last committed model value.
        }
        Object value = spinner.getValue();
        if (!(value instanceof Date date)) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(calendarField, direction);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        spinner.setValue(calendar.getTime());
    }

    static boolean isPointerLeftOfColon(JFormattedTextField field, MouseWheelEvent e) {
        Point inField = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), field);
        if (inField.x < 0 || inField.x >= field.getWidth()
                || inField.y < 0 || inField.y >= field.getHeight()) {
            // Outside the text field (e.g. spinner buttons): treat as the right side.
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
}
