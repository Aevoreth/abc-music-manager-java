package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.BorderLayout;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SetPlayGridZoomTest {
    @Test
    void wheelZoomKeepsPointerAnchoredAndHonorsLimits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var grid = new SetPlayBandGridPanel();
                var canvas = (JPanel) ((BorderLayout) grid.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                canvas.setSize(800, 400);
                var transform = SetPlayBandGridPanel.class.getDeclaredMethod("viewToLogical", double.class, double.class);
                transform.setAccessible(true);
                var zoom = SetPlayBandGridPanel.class.getDeclaredField("zoom");
                zoom.setAccessible(true);
                var before = (Point2D.Double) transform.invoke(grid, 150.0, 120.0);
                scroll(canvas, -1);
                assertTrue(zoom.getDouble(grid) > 1);
                var after = (Point2D.Double) transform.invoke(grid, 150.0, 120.0);
                assertEquals(before.x, after.x, 1e-9);
                assertEquals(before.y, after.y, 1e-9);
                scroll(canvas, -1000);
                assertEquals(3.0, zoom.getDouble(grid));
                scroll(canvas, 1000);
                assertEquals(0.35, zoom.getDouble(grid));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private static void scroll(JPanel canvas, int rotation) {
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseWheelEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, 150, 120, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation));
    }
}
