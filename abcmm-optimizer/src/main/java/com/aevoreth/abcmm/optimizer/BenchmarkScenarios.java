package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The six benchmark variants. Weights use target 2. The infeasible variant is not searched.
 */
public final class BenchmarkScenarios {

    public static final String TOTAL_FIRST = "total-first";
    public static final String SMOOTH_FIRST = "smooth-first";
    public static final String BALANCED = "balanced";
    public static final String PINNED = "pinned";
    public static final String RESTRICTIVE = "restrictive";
    public static final String INFEASIBLE = "infeasible";

    private BenchmarkScenarios() {
    }

    public static List<Scenario> all(OptimizationProblem problem) {
        return List.of(
                scenario(TOTAL_FIRST, problem, rules(0, 1, 0, 0), true, ""),
                scenario(SMOOTH_FIRST, problem, rules(1, 0, 0, 0), true, ""),
                scenario(BALANCED, problem, rules(1, 1, 1, 1), true, ""),
                scenario(PINNED, pinned(problem), rules(1, 1, 1, 1), true, ""),
                restrictive(problem),
                scenario(INFEASIBLE, infeasible(problem), rules(1, 1, 1, 1), false, ""));
    }

    public static Scenario balanced(OptimizationProblem problem) {
        return scenario(BALANCED, problem, rules(1, 1, 1, 1), true, "");
    }

    private static Scenario restrictive(OptimizationProblem problem) {
        List<Performer> reduced = new ArrayList<>();
        boolean changed = false;
        for (Performer performer : problem.performers()) {
            Performer smaller = restrict(problem, performer);
            List<Performer> trial = new ArrayList<>(reduced);
            trial.add(smaller);
            for (int index = reduced.size() + 1; index < problem.performers().size(); index++) {
                trial.add(problem.performers().get(index));
            }
            if (AssignmentValidator.validate(copy(problem, trial, problem.pins()), Assignment.fromBaseline(problem))
                    .isEmpty()) {
                reduced.add(smaller);
                changed = changed || smaller.ownedInstrumentIds().size() != performer.ownedInstrumentIds().size();
            } else {
                reduced.add(performer);
            }
        }
        String note = changed ? "restrictive_ownership=reduced" : "restrictive_ownership=unchanged";
        return scenario(RESTRICTIVE, copy(problem, reduced, problem.pins()), rules(1, 0, 0, 0), true, note);
    }

    private static Performer restrict(OptimizationProblem problem, Performer performer) {
        Set<Long> used = new LinkedHashSet<>();
        for (BaselineAssignment row : problem.baseline()) {
            if (row.playerId() == performer.playerId() && row.instrumentId() != null) {
                used.add(row.instrumentId());
            }
        }
        List<Long> owned = new ArrayList<>(performer.ownedInstrumentIds());
        owned.sort(Comparator.naturalOrder());
        Set<Long> kept = new LinkedHashSet<>();
        Set<Long> covered = new LinkedHashSet<>();
        for (long instrument : owned) {
            for (long required : used) {
                if (!covered.contains(required)
                        && PhysicalInstrumentEquivalence.sameInstrument(
                                instrument, required, problem.equivalentInstrumentIds())) {
                    kept.add(instrument);
                    covered.add(required);
                }
            }
        }
        if (!covered.containsAll(used)) {
            return performer;
        }
        for (long instrument : owned) {
            if (!kept.contains(instrument)) {
                kept.add(instrument);
                break;
            }
        }
        return new Performer(performer.playerId(), kept);
    }

    private static OptimizationProblem pinned(OptimizationProblem problem) {
        List<Pin> pins = new ArrayList<>();
        if (!problem.occurrences().isEmpty()) {
            Pin first = pinFor(problem, problem.occurrences().get(0));
            if (first != null) {
                pins.add(first);
            }
            for (int index = 1; index < problem.occurrences().size(); index++) {
                Pin later = pinFor(problem, problem.occurrences().get(index));
                if (later != null) {
                    pins.add(later);
                    break;
                }
            }
        }
        return copy(problem, problem.performers(), pins);
    }

    private static OptimizationProblem infeasible(OptimizationProblem problem) {
        List<Performer> byId = new ArrayList<>(problem.performers());
        byId.sort(Comparator.comparingLong(Performer::playerId));
        for (SongOccurrence occurrence : problem.occurrences()) {
            List<RequiredPart> parts = new ArrayList<>(occurrence.parts());
            parts.sort(Comparator.comparingInt(RequiredPart::partNumber));
            for (RequiredPart part : parts) {
                if (part.instrumentId() == null) {
                    continue;
                }
                for (Performer performer : byId) {
                    if (!PhysicalInstrumentEquivalence.owns(
                            performer.ownedInstrumentIds(),
                            part.instrumentId(),
                            problem.equivalentInstrumentIds())) {
                        return copy(problem, problem.performers(), List.of(
                                new Pin(occurrence.setlistItemId(), part.partNumber(), performer.playerId())));
                    }
                }
            }
        }
        if (problem.occurrences().isEmpty() || problem.occurrences().get(0).parts().isEmpty() || byId.size() < 2) {
            return problem;
        }
        RequiredPart part = problem.occurrences().get(0).parts().get(0);
        return copy(problem, problem.performers(), List.of(
                new Pin(problem.occurrences().get(0).setlistItemId(), part.partNumber(), byId.get(0).playerId()),
                new Pin(problem.occurrences().get(0).setlistItemId(), part.partNumber(), byId.get(1).playerId())));
    }

    private static Pin pinFor(OptimizationProblem problem, SongOccurrence occurrence) {
        List<RequiredPart> parts = new ArrayList<>(occurrence.parts());
        parts.sort(Comparator.comparingInt(RequiredPart::partNumber));
        RequiredPart preferred = null;
        for (RequiredPart part : parts) {
            if (part.partNumber() == 1) {
                preferred = part;
                break;
            }
        }
        if (preferred != null) {
            Pin pin = pin(problem, occurrence, preferred);
            if (pin != null) {
                return pin;
            }
        }
        for (RequiredPart part : parts) {
            Pin pin = pin(problem, occurrence, part);
            if (pin != null) {
                return pin;
            }
        }
        return null;
    }

    private static Pin pin(OptimizationProblem problem, SongOccurrence occurrence, RequiredPart part) {
        if (part.instrumentId() == null) {
            return null;
        }
        for (BaselineAssignment row : problem.baseline()) {
            if (row.setlistItemId() != occurrence.setlistItemId()
                    || row.partNumber() == null
                    || row.partNumber() != part.partNumber()) {
                continue;
            }
            Performer performer = null;
            for (Performer candidate : problem.performers()) {
                if (candidate.playerId() == row.playerId()) {
                    performer = candidate;
                    break;
                }
            }
            if (performer != null
                    && PhysicalInstrumentEquivalence.owns(
                            performer.ownedInstrumentIds(),
                            part.instrumentId(),
                            problem.equivalentInstrumentIds())) {
                return new Pin(occurrence.setlistItemId(), part.partNumber(), performer.playerId());
            }
        }
        return null;
    }

    private static OptimizationProblem copy(
            OptimizationProblem problem,
            List<Performer> performers,
            List<Pin> pins) {
        return new OptimizationProblem(
                problem.setlistId(),
                problem.locked(),
                problem.bandLayoutId(),
                performers,
                problem.occurrences(),
                problem.baseline(),
                pins,
                problem.equivalentInstrumentIds());
    }

    private static Scenario scenario(
            String name,
            OptimizationProblem problem,
            OptimizationRules rules,
            boolean search,
            String note) {
        return new Scenario(name, problem, rules, search, note);
    }

    private static OptimizationRules rules(double smooth, double total, double role, double keep) {
        return new OptimizationRules(
                smooth,
                total,
                role,
                keep,
                2,
                1.0d,
                java.util.Map.of(),
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of());
    }

    public record Scenario(
            String name,
            OptimizationProblem problem,
            OptimizationRules rules,
            boolean search,
            String note) {
    }
}
