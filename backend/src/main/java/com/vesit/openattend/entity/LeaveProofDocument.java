package com.vesit.openattend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * The scanned letter, event invite or medical certificate backing a {@link LeaveRequest}.
 *
 * <p>Held in its own table so listing the approval queue never drags the file bytes along, and
 * stored in the database rather than on disk because the deployment target has an ephemeral
 * filesystem — a proof that vanished on the next restart would put the paper letter back.
 */
@Entity
@Table(name = "leave_proof_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveProofDocument {

    /** Upper bound on a stored proof; also the {@code varbinary} length used for schema generation. */
    public static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;

    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false, unique = true)
    private LeaveRequest leaveRequest;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** Content digest — lets a re-uploaded proof be recognised as the same document. */
    @Column(nullable = false, length = 64)
    private String sha256;

    /**
     * Plain {@code byte[]} (not {@code @Lob}) so Hibernate treats this as VARBINARY and maps it to
     * PostgreSQL {@code bytea} rather than a large-object {@code oid}. The column type is spelled out
     * so it matches the Flyway schema that ddl validation checks against; H2 accepts {@code bytea} as
     * an alias for {@code BINARY VARYING}, so the same mapping generates the test schema too.
     */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
