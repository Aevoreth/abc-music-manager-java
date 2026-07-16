package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Date field with a month-grid calendar popup (yyyy-MM-dd).
 */
final class CalendarDatePicker extends JPanel {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JButton displayButton = new JButton();
    private final JButton calendarButton = new JButton("\u25BE");
    private LocalDate date = LocalDate.now();
    private JPopupMenu popup;

    CalendarDatePicker() {
        super(new BorderLayout(2, 0));
        displayButton.setHorizontalAlignment(SwingConstants.LEFT);
        displayButton.setFocusPainted(false);
        displayButton.addActionListener(e -> showCalendar());
        calendarButton.setMargin(new Insets(2, 6, 2, 6));
        calendarButton.setToolTipText("Pick date");
        calendarButton.addActionListener(e -> showCalendar());
        add(displayButton, BorderLayout.CENTER);
        add(calendarButton, BorderLayout.EAST);
        syncDisplay();
    }

    LocalDate getDate() {
        return date;
    }

    void setDate(LocalDate value) {
        this.date = value == null ? LocalDate.now() : value;
        syncDisplay();
    }

    String getIsoDate() {
        return date.format(ISO_DATE);
    }

    void setIsoDate(String iso) {
        if (iso == null || iso.isBlank()) {
            setDate(LocalDate.now());
            return;
        }
        try {
            setDate(LocalDate.parse(iso.trim(), ISO_DATE));
        } catch (Exception ex) {
            setDate(LocalDate.now());
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        displayButton.setEnabled(enabled);
        calendarButton.setEnabled(enabled);
    }

    private void syncDisplay() {
        displayButton.setText(date.format(ISO_DATE));
    }

    private void showCalendar() {
        if (!isEnabled()) {
            return;
        }
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));
        popup.add(new MonthPanel(date, selected -> {
            setDate(selected);
            popup.setVisible(false);
        }));
        popup.show(displayButton, 0, displayButton.getHeight());
    }

    private static final class MonthPanel extends JPanel {
        private YearMonth month;
        private final LocalDate selected;
        private final java.util.function.Consumer<LocalDate> onSelect;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));

        MonthPanel(LocalDate selected, java.util.function.Consumer<LocalDate> onSelect) {
            super(new BorderLayout(4, 4));
            this.selected = Objects.requireNonNull(selected, "selected");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.month = YearMonth.from(selected);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setPreferredSize(new Dimension(260, 220));

            JButton prev = new JButton("<");
            JButton next = new JButton(">");
            prev.setMargin(new Insets(2, 8, 2, 8));
            next.setMargin(new Insets(2, 8, 2, 8));
            prev.addActionListener(e -> {
                month = month.minusMonths(1);
                rebuild();
            });
            next.addActionListener(e -> {
                month = month.plusMonths(1);
                rebuild();
            });
            title.setFont(title.getFont().deriveFont(Font.BOLD));

            JPanel header = new JPanel(new BorderLayout());
            header.add(prev, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            JPanel days = new JPanel(new GridLayout(1, 7, 2, 2));
            DayOfWeek[] sundayFirst = {
                    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            };
            for (DayOfWeek day : sundayFirst) {
                JLabel label = new JLabel(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), SwingConstants.CENTER);
                label.setForeground(Color.GRAY);
                days.add(label);
            }
            JPanel body = new JPanel(new BorderLayout(0, 4));
            body.add(days, BorderLayout.NORTH);
            body.add(grid, BorderLayout.CENTER);
            add(body, BorderLayout.CENTER);

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            JButton today = new JButton("Today");
            today.addActionListener(e -> onSelect.accept(LocalDate.now()));
            footer.add(today);
            add(footer, BorderLayout.SOUTH);

            rebuild();
        }

        private void rebuild() {
            title.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                    + " " + month.getYear());
            grid.removeAll();
            LocalDate first = month.atDay(1);
            // Sunday-first: SUN=0, MON=1, ... SAT=6
            int lead = first.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : first.getDayOfWeek().getValue();
            for (int i = 0; i < lead; i++) {
                grid.add(new JLabel(""));
            }
            LocalDate today = LocalDate.now();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate value = month.atDay(day);
                JButton button = new JButton(String.valueOf(day));
                button.setMargin(new Insets(2, 2, 2, 2));
                if (value.equals(selected)) {
                    button.setFont(button.getFont().deriveFont(Font.BOLD));
                }
                if (value.equals(today)) {
                    button.setForeground(UIManager.getColor("Component.focusColor"));
                }
                button.addActionListener(e -> onSelect.accept(value));
                grid.add(button);
            }
            grid.revalidate();
            grid.repaint();
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.pack();
            }
        }
    }
}
