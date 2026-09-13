package com.vesit.openattend.entity.enums;

/**
 * Lifecycle of an OD / medical leave request. Rows are never deleted — a decision is a state
 * change that keeps the proof and the approver on the record.
 */
public enum LeaveRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
