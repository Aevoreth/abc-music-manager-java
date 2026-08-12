package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/**
 * Help → About: version, MIT license summary, and third-party credits
 * (Python {@code QMessageBox.about} equivalent).
 */
public final class AboutDialog extends JDialog {

    private static final int ICON_SIZE = 64;

    private AboutDialog(Window owner) {
        super(owner, AppInfo.aboutTitle(), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(480, 360));
        setPreferredSize(new Dimension(520, 440));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        Image icon = iconNear(ICON_SIZE);
        if (icon != null) {
            JLabel iconLabel = new JLabel(new ImageIcon(icon));
            iconLabel.setVerticalAlignment(SwingConstants.TOP);
            header.add(iconLabel, BorderLayout.WEST);
        }
        JLabel title = new JLabel("<html><b>" + AppInfo.APP_NAME + "</b><br>Version "
                + AppInfo.displayVersion() + "</html>");
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 14f));
        header.add(title, BorderLayout.CENTER);

        JTextArea body = new JTextArea(AppInfo.aboutDetails());
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFocusable(false);
        body.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        body.setCaretPosition(0);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(close);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);

        JPanel root = new JPanel(new BorderLayout(8, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(header, BorderLayout.NORTH);
        root.add(new JScrollPane(body), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    public static void open(Window owner) {
        AboutDialog dialog = new AboutDialog(owner);
        dialog.setVisible(true);
    }

    private static Image iconNear(int targetSize) {
        List<Image> images = AppIcons.images();
        if (images.isEmpty()) {
            return null;
        }
        Image best = images.get(0);
        int bestDelta = Integer.MAX_VALUE;
        for (Image image : images) {
            int width = image.getWidth(null);
            if (width <= 0) {
                continue;
            }
            int delta = Math.abs(width - targetSize);
            if (delta < bestDelta) {
                best = image;
                bestDelta = delta;
            }
        }
        return best;
    }
}
