package com.civicsense.complaint;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.civicsense.complaint.exception.ComplaintExceptions.InvalidStatusTransitionException;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.Role;

/**
 * Single authority for complaint state transitions (DATABASE_DESIGN.md §5.2).
 * AI-specific transitions remain in the map but no code invokes them until the
 * AI worker exists in Phase 5; Phase 3 uses the manual/system paths only.
 */
@Component
public class StatusTransitionPolicy {

    public enum Actor {
        SYSTEM, CITIZEN, DEPT_ADMIN, SUPER_ADMIN
    }

    private record Rule(Set<Actor> actors, boolean reasonRequired, boolean evidenceRequired) {
    }

    private static final Rule NO_REASON = new Rule(Set.of(), false, false);
    private static final Rule REASON = new Rule(Set.of(), true, false);
    private static final Rule EVIDENCE_AND_REASON = new Rule(Set.of(), true, true);

    private final Map<ComplaintStatus, Map<ComplaintStatus, Rule>> rules = new EnumMap<>(ComplaintStatus.class);

    public StatusTransitionPolicy() {
        add(ComplaintStatus.SUBMITTED, ComplaintStatus.AI_ANALYSIS_PENDING, NO_REASON, Actor.SYSTEM);
        add(ComplaintStatus.SUBMITTED, ComplaintStatus.PENDING_ASSIGNMENT, NO_REASON,
                Actor.SYSTEM, Actor.SUPER_ADMIN);
        add(ComplaintStatus.SUBMITTED, ComplaintStatus.UNASSIGNED, NO_REASON,
                Actor.SYSTEM, Actor.SUPER_ADMIN);
        add(ComplaintStatus.SUBMITTED, ComplaintStatus.ASSIGNED, NO_REASON, Actor.SUPER_ADMIN);

        add(ComplaintStatus.AI_ANALYSIS_PENDING, ComplaintStatus.AI_ANALYSIS_COMPLETED, NO_REASON, Actor.SYSTEM);
        add(ComplaintStatus.AI_ANALYSIS_PENDING, ComplaintStatus.AI_ANALYSIS_FAILED, NO_REASON, Actor.SYSTEM);
        add(ComplaintStatus.AI_ANALYSIS_COMPLETED, ComplaintStatus.ASSIGNED, NO_REASON,
                Actor.SYSTEM, Actor.SUPER_ADMIN);
        add(ComplaintStatus.AI_ANALYSIS_COMPLETED, ComplaintStatus.PENDING_ASSIGNMENT, NO_REASON, Actor.SYSTEM);
        add(ComplaintStatus.AI_ANALYSIS_FAILED, ComplaintStatus.ASSIGNED, NO_REASON,
                Actor.SYSTEM, Actor.SUPER_ADMIN);
        add(ComplaintStatus.AI_ANALYSIS_FAILED, ComplaintStatus.PENDING_ASSIGNMENT, NO_REASON, Actor.SYSTEM);

        add(ComplaintStatus.PENDING_ASSIGNMENT, ComplaintStatus.ASSIGNED, NO_REASON, Actor.SUPER_ADMIN);
        add(ComplaintStatus.PENDING_ASSIGNMENT, ComplaintStatus.UNASSIGNED, NO_REASON,
                Actor.SUPER_ADMIN, Actor.SYSTEM);
        add(ComplaintStatus.PENDING_ASSIGNMENT, ComplaintStatus.REJECTED, REASON, Actor.SUPER_ADMIN);

        add(ComplaintStatus.UNASSIGNED, ComplaintStatus.ASSIGNED, NO_REASON, Actor.SUPER_ADMIN);
        add(ComplaintStatus.UNASSIGNED, ComplaintStatus.REJECTED, REASON, Actor.SUPER_ADMIN);

        add(ComplaintStatus.ASSIGNED, ComplaintStatus.ACKNOWLEDGED, NO_REASON, Actor.DEPT_ADMIN);
        add(ComplaintStatus.ASSIGNED, ComplaintStatus.PENDING_ASSIGNMENT, NO_REASON, Actor.SUPER_ADMIN);
        add(ComplaintStatus.ASSIGNED, ComplaintStatus.UNASSIGNED, NO_REASON, Actor.SUPER_ADMIN);
        add(ComplaintStatus.ASSIGNED, ComplaintStatus.REJECTED, REASON, Actor.DEPT_ADMIN, Actor.SUPER_ADMIN);
        // An approved reassignment changes the owning department without changing the
        // lifecycle state. The reason is mandatory and the event is recorded in history.
        add(ComplaintStatus.ASSIGNED, ComplaintStatus.ASSIGNED, REASON, Actor.SUPER_ADMIN);

        add(ComplaintStatus.ACKNOWLEDGED, ComplaintStatus.IN_PROGRESS, NO_REASON, Actor.DEPT_ADMIN);
        add(ComplaintStatus.ACKNOWLEDGED, ComplaintStatus.ON_HOLD, REASON, Actor.DEPT_ADMIN);

        add(ComplaintStatus.IN_PROGRESS, ComplaintStatus.ON_HOLD, REASON, Actor.DEPT_ADMIN);
        add(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, EVIDENCE_AND_REASON, Actor.DEPT_ADMIN);

        add(ComplaintStatus.ON_HOLD, ComplaintStatus.IN_PROGRESS, NO_REASON, Actor.DEPT_ADMIN);
        add(ComplaintStatus.ON_HOLD, ComplaintStatus.REJECTED, REASON, Actor.DEPT_ADMIN, Actor.SUPER_ADMIN);

        add(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, NO_REASON, Actor.CITIZEN);
        add(ComplaintStatus.RESOLVED, ComplaintStatus.REOPENED, REASON, Actor.CITIZEN);

        add(ComplaintStatus.REOPENED, ComplaintStatus.ASSIGNED, NO_REASON, Actor.SUPER_ADMIN, Actor.SYSTEM);

        add(ComplaintStatus.REJECTED, ComplaintStatus.CLOSED, NO_REASON, Actor.SUPER_ADMIN);
    }

    private void add(ComplaintStatus from, ComplaintStatus to, Rule rule, Actor... actors) {
        rules.computeIfAbsent(from, key -> new EnumMap<>(ComplaintStatus.class))
                .put(to, new Rule(Set.of(actors), rule.reasonRequired(), rule.evidenceRequired()));
    }

    public boolean isAllowed(ComplaintStatus from, ComplaintStatus to, Actor actor) {
        Rule rule = from == null ? null : rules.getOrDefault(from, Map.of()).get(to);
        return rule != null && rule.actors().contains(actor);
    }

    public Set<ComplaintStatus> allowedTargets(ComplaintStatus from, Actor actor) {
        Map<ComplaintStatus, Rule> targets = from == null ? Map.of() : rules.getOrDefault(from, Map.of());
        Set<ComplaintStatus> result = EnumSet.noneOf(ComplaintStatus.class);
        targets.forEach((target, rule) -> {
            if (rule.actors().contains(actor)) {
                result.add(target);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    /** Throws a 409 with the allowed target set when the transition is not permitted. */
    public void validate(ComplaintStatus from, ComplaintStatus to, Actor actor) {
        if (!isAllowed(from, to, actor)) {
            throw new InvalidStatusTransitionException(from, to, allowedTargets(from, actor));
        }
    }

    public boolean requiresReason(ComplaintStatus from, ComplaintStatus to) {
        return rule(from, to).reasonRequired();
    }

    public boolean requiresEvidence(ComplaintStatus from, ComplaintStatus to) {
        return rule(from, to).evidenceRequired();
    }

    private Rule rule(ComplaintStatus from, ComplaintStatus to) {
        Rule rule = from == null ? null : rules.getOrDefault(from, Map.of()).get(to);
        return rule == null ? NO_REASON : rule;
    }

    public static Actor actorFor(Role role) {
        return switch (role) {
            case CITIZEN -> Actor.CITIZEN;
            case DEPARTMENT_ADMIN -> Actor.DEPT_ADMIN;
            case SUPER_ADMIN -> Actor.SUPER_ADMIN;
        };
    }

    /** All statuses known to the policy; useful for validation messages and tests. */
    public static Set<ComplaintStatus> allStatuses() {
        return Collections.unmodifiableSet(EnumSet.allOf(ComplaintStatus.class));
    }

    /** Defensive helper used by tests to assert the policy covers every status. */
    public Set<ComplaintStatus> documentedFromStatuses() {
        return Collections.unmodifiableSet(Arrays.stream(ComplaintStatus.values())
                .filter(rules::containsKey)
                .collect(java.util.stream.Collectors.toSet()));
    }
}
