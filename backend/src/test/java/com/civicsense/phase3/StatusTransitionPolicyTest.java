package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.civicsense.complaint.StatusTransitionPolicy;
import com.civicsense.complaint.exception.ComplaintExceptions.InvalidStatusTransitionException;
import com.civicsense.domain.ComplaintStatus;

class StatusTransitionPolicyTest {

    private final StatusTransitionPolicy policy = new StatusTransitionPolicy();

    @Test
    void documentedTransitionsAreAllowedForTheCorrectActors() {
        assertThat(policy.isAllowed(ComplaintStatus.SUBMITTED,
                ComplaintStatus.PENDING_ASSIGNMENT, StatusTransitionPolicy.Actor.SYSTEM)).isTrue();
        assertThat(policy.isAllowed(ComplaintStatus.PENDING_ASSIGNMENT,
                ComplaintStatus.ASSIGNED, StatusTransitionPolicy.Actor.SUPER_ADMIN)).isTrue();
        assertThat(policy.isAllowed(ComplaintStatus.ASSIGNED,
                ComplaintStatus.ACKNOWLEDGED, StatusTransitionPolicy.Actor.DEPT_ADMIN)).isTrue();
        assertThat(policy.isAllowed(ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.RESOLVED, StatusTransitionPolicy.Actor.DEPT_ADMIN)).isTrue();
        assertThat(policy.isAllowed(ComplaintStatus.RESOLVED,
                ComplaintStatus.CLOSED, StatusTransitionPolicy.Actor.CITIZEN)).isTrue();
    }

    @Test
    void forbiddenActorsAndTransitionsAreRejected() {
        assertThatThrownBy(() -> policy.validate(ComplaintStatus.SUBMITTED,
                ComplaintStatus.ASSIGNED, StatusTransitionPolicy.Actor.CITIZEN))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> policy.validate(ComplaintStatus.CLOSED,
                ComplaintStatus.REOPENED, StatusTransitionPolicy.Actor.CITIZEN))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> policy.validate(ComplaintStatus.RESOLVED,
                ComplaintStatus.IN_PROGRESS, StatusTransitionPolicy.Actor.DEPT_ADMIN))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatCode(() -> policy.validate(ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.RESOLVED, StatusTransitionPolicy.Actor.DEPT_ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void approvedReassignmentIsARecordedAssignedToAssignedEvent() {
        assertThat(policy.isAllowed(ComplaintStatus.ASSIGNED, ComplaintStatus.ASSIGNED,
                StatusTransitionPolicy.Actor.SUPER_ADMIN)).isTrue();
        assertThat(policy.requiresReason(ComplaintStatus.ASSIGNED, ComplaintStatus.ASSIGNED)).isTrue();
        assertThat(policy.isAllowed(ComplaintStatus.ASSIGNED, ComplaintStatus.ASSIGNED,
                StatusTransitionPolicy.Actor.DEPT_ADMIN)).isFalse();
    }

    @Test
    void resolutionRequiresEvidenceAndReasonWhileHoldRequiresOnlyReason() {
        assertThat(policy.requiresEvidence(ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.RESOLVED)).isTrue();
        assertThat(policy.requiresReason(ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.RESOLVED)).isTrue();
        assertThat(policy.requiresEvidence(ComplaintStatus.ACKNOWLEDGED,
                ComplaintStatus.ON_HOLD)).isFalse();
        assertThat(policy.requiresReason(ComplaintStatus.ACKNOWLEDGED,
                ComplaintStatus.ON_HOLD)).isTrue();
    }

    @Test
    void closedIsTerminalAndCoveredStatusesAreDocumented() {
        assertThat(policy.allowedTargets(ComplaintStatus.CLOSED,
                StatusTransitionPolicy.Actor.SUPER_ADMIN)).isEmpty();
        assertThat(policy.allowedTargets(ComplaintStatus.CLOSED,
                StatusTransitionPolicy.Actor.CITIZEN)).isEmpty();

        Set<ComplaintStatus> covered = policy.documentedFromStatuses();
        assertThat(covered).contains(ComplaintStatus.SUBMITTED, ComplaintStatus.ASSIGNED,
                ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, ComplaintStatus.REOPENED);
        assertThat(covered).doesNotContain(ComplaintStatus.CLOSED);
        assertThat(StatusTransitionPolicy.allStatuses()).containsAll(covered);
    }
}
