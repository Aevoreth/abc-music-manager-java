package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Inputs for a library scan. Roots come from preferences; excludes from {@code FolderRule}.
 *
 * @param lotroRoot        LOTRO install root (Music is resolved as {@code lotroRoot/Music})
 * @param setExportDir     set-export directory (absolute, or relative to Music); may be null/blank
 * @param defaultStatusId  status for newly created songs; null uses first Status by sort order
 */
public record ScanRequest(Path lotroRoot, Path setExportDir, Long defaultStatusId) {

    public ScanRequest {
        Objects.requireNonNull(lotroRoot, "lotroRoot");
    }
}
