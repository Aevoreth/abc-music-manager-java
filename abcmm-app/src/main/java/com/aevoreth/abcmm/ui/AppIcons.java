package com.aevoreth.abcmm.ui;

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Application window / taskbar icons from the shared ABC Music Manager logo
 * (same assets as the Python edition under {@code resources/icons}).
 */
public final class AppIcons {

    private static final String[] SIZES = {
            "16", "32", "48", "64", "128", "256", "512"
    };

    private static final List<Image> IMAGES = loadImages();

    private AppIcons() {
    }

    /** Multi-resolution icons for {@link Window#setIconImages(List)}. */
    public static List<Image> images() {
        return IMAGES;
    }

    /** Apply window and (when supported) taskbar / dock icons. */
    public static void applyTo(Window window) {
        if (window == null || IMAGES.isEmpty()) {
            return;
        }
        window.setIconImages(IMAGES);
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(IMAGES.get(IMAGES.size() - 1));
                }
            }
        } catch (Exception ignored) {
            // Platform may reject icon changes; window icons still apply.
        }
    }

    private static List<Image> loadImages() {
        List<Image> loaded = new ArrayList<>();
        for (String size : SIZES) {
            String path = "/com/aevoreth/abcmm/icons/app_" + size + ".png";
            try (InputStream in = AppIcons.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                Image image = ImageIO.read(in);
                if (image != null) {
                    loaded.add(image);
                }
            } catch (IOException ignored) {
                // Skip missing/unreadable sizes; others still apply.
            }
        }
        return Collections.unmodifiableList(loaded);
    }
}
