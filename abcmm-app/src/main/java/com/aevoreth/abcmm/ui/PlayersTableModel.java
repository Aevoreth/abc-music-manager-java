package com.aevoreth.abcmm.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.table.AbstractTableModel;

import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInfo;

/**
 * Players tab matrix: Name / Level / Class / one Boolean column per instrument.
 * Instrument cells are read-only checkboxes (not editable).
 */
final class PlayersTableModel extends AbstractTableModel {

    static final int COL_NAME = 0;
    static final int COL_LEVEL = 1;
    static final int COL_CLASS = 2;
    static final int FIRST_INSTRUMENT_COL = 3;

    private final List<InstrumentInfo> instruments = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    void setInstruments(List<InstrumentInfo> instruments) {
        this.instruments.clear();
        if (instruments != null) {
            this.instruments.addAll(instruments);
        }
        fireTableStructureChanged();
    }

    void setRows(List<Row> rows) {
        this.rows.clear();
        if (rows != null) {
            this.rows.addAll(rows);
        }
        fireTableDataChanged();
    }

    List<InstrumentInfo> instruments() {
        return List.copyOf(instruments);
    }

    PlayerInfo playerAt(int modelRow) {
        if (modelRow < 0 || modelRow >= rows.size()) {
            return null;
        }
        return rows.get(modelRow).player();
    }

    int instrumentColumnCount() {
        return instruments.size();
    }

    int firstInstrumentColumn() {
        return FIRST_INSTRUMENT_COL;
    }

    int lastInstrumentColumn() {
        return FIRST_INSTRUMENT_COL + instruments.size() - 1;
    }

    boolean isInstrumentColumn(int column) {
        return column >= FIRST_INSTRUMENT_COL && column < FIRST_INSTRUMENT_COL + instruments.size();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return FIRST_INSTRUMENT_COL + instruments.size();
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case COL_NAME -> "Name";
            case COL_LEVEL -> "Level";
            case COL_CLASS -> "Class";
            default -> {
                int index = column - FIRST_INSTRUMENT_COL;
                if (index < 0 || index >= instruments.size()) {
                    yield "";
                }
                yield LotroInstrumentDefaults.uiName(instruments.get(index).name());
            }
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (isInstrumentColumn(columnIndex)) {
            return Boolean.class;
        }
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_NAME -> row.player().name();
            case COL_LEVEL -> row.player().level() == null ? "" : String.valueOf(row.player().level());
            case COL_CLASS -> row.player().characterClass() == null ? "" : row.player().characterClass();
            default -> {
                int index = columnIndex - FIRST_INSTRUMENT_COL;
                if (index < 0 || index >= instruments.size()) {
                    yield Boolean.FALSE;
                }
                yield row.ownedInstrumentIds().contains(instruments.get(index).id());
            }
        };
    }

    record Row(PlayerInfo player, Set<Long> ownedInstrumentIds) {
        Row {
            Objects.requireNonNull(player, "player");
            ownedInstrumentIds = Set.copyOf(ownedInstrumentIds == null ? Set.of() : ownedInstrumentIds);
        }

        static Row of(PlayerInfo player, Iterable<Long> ownedIds) {
            Set<Long> ids = new HashSet<>();
            if (ownedIds != null) {
                for (Long id : ownedIds) {
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            return new Row(player, ids);
        }
    }
}
