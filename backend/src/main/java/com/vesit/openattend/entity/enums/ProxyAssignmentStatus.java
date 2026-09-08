package com.vesit.openattend.entity.enums;

/** Lifecycle of a substitute-lecture delegation. Rows are never deleted — revoking is a state change. */
public enum ProxyAssignmentStatus {
    ACTIVE,
    REVOKED
}
