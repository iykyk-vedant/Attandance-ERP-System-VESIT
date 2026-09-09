package com.vesit.openattend.repository;

import com.vesit.openattend.dto.leave.LeaveProofMetadata;
import com.vesit.openattend.entity.LeaveProofDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveProofDocumentRepository extends JpaRepository<LeaveProofDocument, String> {

    /** Loads the file bytes — only ever called when a proof is actually being downloaded. */
    Optional<LeaveProofDocument> findByLeaveRequestId(String leaveRequestId);

    boolean existsByLeaveRequestId(String leaveRequestId);

    /** Attachment details for a page of requests, without touching the {@code content} column. */
    @Query("""
            SELECT new com.vesit.openattend.dto.leave.LeaveProofMetadata(
                       d.leaveRequest.id, d.fileName, d.contentType, d.sizeBytes, d.sha256)
              FROM LeaveProofDocument d
             WHERE d.leaveRequest.id IN :leaveRequestIds
            """)
    List<LeaveProofMetadata> findMetadataFor(@Param("leaveRequestIds") Collection<String> leaveRequestIds);
}
