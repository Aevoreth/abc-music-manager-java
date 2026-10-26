package com.aevoreth.abcmm.optimizer;

import java.nio.file.Path;
import java.util.Objects;

/**
 * CLI inputs for a read-only load. Seed and budget are recorded for later search
 * and are not used while checking feasibility.
 */
public record LoadRequest(
        Path database,
        long setlistId,
        Long seed,
        Long budgetSeconds,
        Integer players) {

    public LoadRequest {
        Objects.requireNonNull(database, "database");
        if (setlistId <= 0) {
            throw new IllegalArgumentException("setlistId");
        }
        if (seed != null && seed < 0) {
            throw new IllegalArgumentException("seed");
        }
        if (budgetSeconds != null && budgetSeconds < 0) {
            throw new IllegalArgumentException("budgetSeconds");
        }
        if (players != null && players < 0) {
            throw new IllegalArgumentException("players");
        }
    }
}
