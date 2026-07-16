package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;

/**
 * Time chooser displaying and editing {@code HH:mm}.
 */
final class TimeChooser extends JPanel {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final JSpinner spinner;

    TimeChooser() {
        this(LocalTime.of(19, 0));
    }

    TimeChooser(LocalTime initial) {
        super(new BorderLayout());
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, initial == null ? 19 : initial.getHour());
        calendar.set(Calendar.MINUTE, initial == null ? 0 : initial.getMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SpinnerDateModel model = new SpinnerDateModel(calendar.getTime(), null, null, Calendar.MINUTE);
        spinner = new JSpinner(model);
        spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
        SpinnerMouseWheel.installDateTimeSplit(spinner);
        add(spinner, BorderLayout.CENTER);
    }

    LocalTime getTime() {
        Date value = (Date) spinner.getValue();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value);
        return LocalTime.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    void setTime(LocalTime time) {
        LocalTime value = time == null ? LocalTime.of(19, 0) : time;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime((Date) spinner.getValue());
        calendar.set(Calendar.HOUR_OF_DAY, value.getHour());
        calendar.set(Calendar.MINUTE, value.getMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        spinner.setValue(calendar.getTime());
    }

    String getHhMm() {
        return getTime().format(HH_MM);
    }

    void setHhMm(String text) {
        if (text == null || text.isBlank()) {
            setTime(LocalTime.of(19, 0));
            return;
        }
        try {
            setTime(LocalTime.parse(text.trim(), HH_MM));
        } catch (Exception ex) {
            // Also accept H:mm
            try {
                String[] parts = text.trim().split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                setTime(LocalTime.of(hour, minute));
            } catch (Exception ignored) {
                setTime(LocalTime.of(19, 0));
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        spinner.setEnabled(enabled);
    }
}
