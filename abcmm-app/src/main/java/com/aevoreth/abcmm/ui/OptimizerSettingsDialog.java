package com.aevoreth.abcmm.ui;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import javax.swing.*;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.storage.*;

/** Shared settings entry point for Bands and the set optimizer. */
final class OptimizerSettingsDialog {
    private record SetChoice(SetlistInfo set) { public String toString() { return set.name(); } }

    static boolean show(Component parent, Path path, Long requestedBand, Long requestedSet) throws LibraryException {
        return show(parent, path, requestedBand, requestedSet, null, null);
    }

    static boolean show(Component parent, Path path, Long requestedBand, Long requestedSet,
            Long requestedItem, Integer requestedPart) throws LibraryException {
        try (var db = SqliteDatabase.openOrCreate(path)) {
            var bands = new SqliteBandRepository(db);
            var players = new SqlitePlayerRepository(db);
            var profiles = new SqliteOptimizationProfileRepository(db);
            var sets = new SqliteSetlistRepository(db);
            var allSets = sets.listSetlists();
            Long layout = allSets.stream().filter(s -> Objects.equals(s.id(), requestedSet)).map(SetlistInfo::bandLayoutId).filter(Objects::nonNull).findFirst().orElse(null);
            Long bandId = requestedBand;
            if (bandId == null) for (var band : bands.listBands())
                if (bands.listLayouts(band.id()).stream().anyMatch(l -> Objects.equals(l.id(), layout))) { bandId = band.id(); break; }
            if (bandId == null) throw new LibraryException("Choose a band layout for this set before editing optimizer settings.");
            long chosenBand = bandId;
            Set<Long> layouts = new HashSet<>(); bands.listLayouts(chosenBand).forEach(l -> layouts.add(l.id()));
            var form = new BandOptimizerForm(profiles, bands, players, chosenBand);
            var selector = new JComboBox<SetChoice>();
            selector.setToolTipText("Choose the set whose song exceptions you want to edit. Band goals and instrument rules are shared.");
            JPanel exceptions = new JPanel(new BorderLayout(8, 8));
            JPanel cards = new JPanel(new CardLayout());
            Map<Long, SongExceptionsForm> editors = new LinkedHashMap<>();
            Map<Long, String> names = new LinkedHashMap<>(); players.listPlayers(null).forEach(p -> names.put(p.id(), p.name()));
            for (var set : allSets) if (layouts.contains(set.bandLayoutId())) selector.addItem(new SetChoice(set));
            Runnable select = () -> {
                SetChoice choice = (SetChoice) selector.getSelectedItem();
                if (choice == null) return;
                long id = choice.set().id(); String key = Long.toString(id);
                if (!editors.containsKey(id)) {
                    try {
                        var loaded = SetlistOptimizationActions.load(path, id, null);
                        if (loaded.problem() == null) {
                            cards.add(new JLabel("Song exceptions could not be loaded for this set."), key);
                        } else {
                            var editor = new SongExceptionsForm(profiles, loaded.problem(), sets.listItems(id), names);
                            if (Objects.equals(id, requestedSet) && requestedItem != null) editor.selectPart(requestedItem, requestedPart);
                            editors.put(id, editor); cards.add(editor, key);
                        }
                    } catch (LibraryException ex) { cards.add(new JLabel(ex.getMessage()), key); }
                }
                ((CardLayout) cards.getLayout()).show(cards, key);
            };
            selector.addActionListener(e -> select.run());
            for (int i = 0; i < selector.getItemCount(); i++) if (Objects.equals(selector.getItemAt(i).set().id(), requestedSet)) selector.setSelectedIndex(i);
            select.run();
            exceptions.add(selector, BorderLayout.NORTH); exceptions.add(cards, BorderLayout.CENTER);
            if (selector.getItemCount() == 0) exceptions.add(new JLabel("No sets use this band yet."), BorderLayout.SOUTH);
            form.setExceptions(exceptions);
            if (requestedItem != null) form.showExceptions();
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Optimizer settings", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            boolean[] saved = {false};
            JButton save = new JButton("Save"), cancel = new JButton("Cancel"), help = new JButton("Optimizer help…");
            save.setToolTipText("Save band settings and the edited song exceptions together.");
            cancel.setToolTipText("Discard settings edits made in this window.");
            help.setToolTipText("Read the optimizer guide while continuing to use these settings.");
            save.addActionListener(e -> {
                try {
                    for (var editor : editors.values()) editor.validateEdits();
                    db.connection().setAutoCommit(false);
                    try {
                        form.save();
                        for (var editor : editors.values()) editor.save();
                        db.connection().commit();
                    } catch (Exception ex) { db.connection().rollback(); throw ex; }
                    finally { db.connection().setAutoCommit(true); }
                    saved[0] = true; dialog.dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Optimizer settings", JOptionPane.WARNING_MESSAGE);
                }
            });
            cancel.addActionListener(e -> dialog.dispose()); help.addActionListener(e -> OptimizerHelpDialog.show(dialog));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(help); buttons.add(cancel); buttons.add(save);
            JPanel root = new JPanel(new BorderLayout(8, 8)); root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.add(form, BorderLayout.CENTER); root.add(buttons, BorderLayout.SOUTH);
            dialog.setContentPane(root); dialog.setSize(1000, 650); dialog.setLocationRelativeTo(parent); dialog.setVisible(true);
            return saved[0];
        }
    }
}
