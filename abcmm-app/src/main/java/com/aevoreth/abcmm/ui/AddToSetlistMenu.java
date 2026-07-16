package com.aevoreth.abcmm.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistFolderInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Builds the "Add to setlist" submenu of unlocked setlists.
 */
final class AddToSetlistMenu {

    private AddToSetlistMenu() {
    }

    /**
     * @param excludeSetlistId optional setlist to omit (e.g. the setlist currently open)
     */
    static JMenu build(
            SetlistRepository setlistRepository,
            Long excludeSetlistId,
            Consumer<SetlistInfo> onChoose) {
        Objects.requireNonNull(onChoose, "onChoose");
        JMenu addToSetlist = new JMenu("Add to setlist");
        if (setlistRepository == null) {
            JMenuItem unavailable = new JMenuItem("Unavailable");
            unavailable.setEnabled(false);
            addToSetlist.add(unavailable);
            return addToSetlist;
        }
        try {
            Map<Long, String> folderNames = new HashMap<>();
            for (SetlistFolderInfo folder : setlistRepository.listFolders()) {
                folderNames.put(folder.id(), folder.name());
            }
            int added = 0;
            for (SetlistInfo setlist : setlistRepository.listSetlists()) {
                if (setlist.locked()) {
                    continue;
                }
                if (excludeSetlistId != null && setlist.id() == excludeSetlistId) {
                    continue;
                }
                String folderName = setlist.folderId() == null
                        ? "Unfiled"
                        : folderNames.getOrDefault(setlist.folderId(), "Unfiled");
                String setName = setlist.name() == null || setlist.name().isBlank()
                        ? ("#" + setlist.id())
                        : setlist.name();
                JMenuItem item = new JMenuItem(folderName + " / " + setName);
                item.addActionListener(ev -> onChoose.accept(setlist));
                addToSetlist.add(item);
                added++;
            }
            if (added == 0) {
                JMenuItem empty = new JMenuItem(
                        excludeSetlistId == null ? "No unlocked setlists" : "No other unlocked setlists");
                empty.setEnabled(false);
                addToSetlist.add(empty);
            }
        } catch (LibraryException ex) {
            JMenuItem error = new JMenuItem("Failed to load setlists");
            error.setEnabled(false);
            addToSetlist.add(error);
        }
        return addToSetlist;
    }
}
