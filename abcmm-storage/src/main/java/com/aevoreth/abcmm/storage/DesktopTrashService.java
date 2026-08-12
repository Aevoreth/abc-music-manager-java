package com.aevoreth.abcmm.storage;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.aevoreth.abcmm.domain.scan.TrashService;

/**
 * Recycle Bin / Trash via {@link Desktop#moveToTrash(File)}.
 */
public final class DesktopTrashService implements TrashService {

    @Override
    public boolean moveToTrash(Path path) throws Exception {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            throw new UnsupportedOperationException("Recycle Bin / Trash is not supported on this platform");
        }
        return desktop.moveToTrash(path.toFile());
    }
}
