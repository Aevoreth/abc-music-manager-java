package com.aevoreth.abcmm.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.text.ParseException;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
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

    /**
     * Duration spinner in whole minutes, displayed as zero-padded {@code HH:MM}.
     * Model value remains seconds (multiples of 60).
     */
    static JSpinner createHoursMinutes(int valueSeconds, int minimumSeconds, int maximumSeconds) {
        int step = 60;
        int value = Math.max(minimumSeconds, Math.min(maximumSeconds, roundDownToMinute(valueSeconds)));
        int minimum = roundDownToMinute(minimumSeconds);
        int maximum = roundDownToMinute(maximumSeconds);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, step));
        installHoursMinutesEditor(spinner);
        sizeToSample(spinner, "00:00"); // width for HH:MM only
        return spinner;
    }

    /**
     * Integer spinner displayed with at least two zero-padded digits (e.g. {@code 05}).
     */
    static JSpinner createPaddedInt(int value, int minimum, int maximum, int stepSize) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, stepSize));
        installPaddedIntEditor(spinner);
        sizeToSample(spinner, "88"); // width for two digits only
        return spinner;
    }

    static void installPaddedIntEditor(JSpinner spinner) {
        PaddedIntEditor editor = new PaddedIntEditor(spinner);
        spinner.setEditor(editor);
        SpinnerMouseWheel.install(spinner);
    }

    /**
     * Limit spinner <em>width</em> to fit {@code sample} (plus buttons); keep default height.
     */
    static void sizeToSample(JSpinner spinner, String sample) {
        if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)) {
            return;
        }
        JFormattedTextField field = editor.getTextField();
        field.setColumns(0);
        FontMetrics metrics = field.getFontMetrics(field.getFont());
        Insets insets = field.getInsets();
        // Sample text width + breathing room; not the default oversized field.
        int fieldWidth = metrics.stringWidth(sample) + insets.left + insets.right + 18;
        int height = spinner.getPreferredSize().height;

        field.setPreferredSize(new Dimension(fieldWidth, field.getPreferredSize().height));
        field.setMaximumSize(new Dimension(fieldWidth, Integer.MAX_VALUE));

        int width = fieldWidth + 22; // spinner arrow buttons
        Dimension size = new Dimension(width, height);
        spinner.setPreferredSize(size);
        spinner.setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
    }

    static void installMinutesSecondsEditor(JSpinner spinner) {
        MinutesSecondsEditor editor = new MinutesSecondsEditor(spinner);
        spinner.setEditor(editor);
        SpinnerMouseWheel.installNumberSplitByColon(spinner, 60, 1);
    }

    static void installHoursMinutesEditor(JSpinner spinner) {
        HoursMinutesEditor editor = new HoursMinutesEditor(spinner);
        spinner.setEditor(editor);
        SpinnerMouseWheel.installNumberSplitByColon(spinner, 3600, 60);
    }

    private static int roundDownToMinute(int seconds) {
        return Math.max(0, seconds) / 60 * 60;
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

    private static final class HoursMinutesEditor extends JSpinner.DefaultEditor {
        HoursMinutesEditor(JSpinner spinner) {
            super(spinner);
            JFormattedTextField field = getTextField();
            field.setEditable(true);
            field.setColumns(0);
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setFormatterFactory(new DefaultFormatterFactory(new HoursMinutesFormatter()));
            field.setValue(spinner.getValue());
        }
    }

    private static final class HoursMinutesFormatter extends DefaultFormatter {
        HoursMinutesFormatter() {
            setOverwriteMode(false);
            setAllowsInvalid(true);
            setCommitsOnValidEdit(false);
        }

        @Override
        public Object stringToValue(String text) throws ParseException {
            Integer seconds = LibraryDisplayFormats.parseHoursMinutesToSeconds(text);
            if (seconds == null) {
                throw new ParseException("Invalid duration: " + text, 0);
            }
            return seconds;
        }

        @Override
        public String valueToString(Object value) {
            if (value == null) {
                return LibraryDisplayFormats.formatHoursMinutes(0);
            }
            return LibraryDisplayFormats.formatHoursMinutes(((Number) value).intValue());
        }
    }

    private static final class PaddedIntEditor extends JSpinner.DefaultEditor {
        PaddedIntEditor(JSpinner spinner) {
            super(spinner);
            JFormattedTextField field = getTextField();
            field.setEditable(true);
            field.setColumns(0);
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setFormatterFactory(new DefaultFormatterFactory(new PaddedIntFormatter()));
            field.setValue(spinner.getValue());
        }
    }

    private static final class PaddedIntFormatter extends DefaultFormatter {
        PaddedIntFormatter() {
            setOverwriteMode(false);
            setAllowsInvalid(true);
            setCommitsOnValidEdit(false);
        }

        @Override
        public Object stringToValue(String text) throws ParseException {
            if (text == null) {
                throw new ParseException("Invalid number", 0);
            }
            String value = text.trim();
            if (value.isEmpty()) {
                throw new ParseException("Invalid number", 0);
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new ParseException("Invalid number: " + text, 0);
            }
        }

        @Override
        public String valueToString(Object value) {
            int number = value == null ? 0 : ((Number) value).intValue();
            return String.format("%02d", Math.max(0, number));
        }
    }
}
