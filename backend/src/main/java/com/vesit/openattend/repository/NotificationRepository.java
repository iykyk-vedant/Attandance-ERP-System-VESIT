package com.vesit.openattend.repository;

import com.vesit.openattend.entity.Notification;
import com.vesit.openattend.entity.enums.NotificationEmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<Notification> findByStudentIdAndIsReadFalseOrderByCreatedAtDesc(String studentId);
    long countByStudentIdAndIsReadFalse(String studentId);
    boolean existsByStudentIdAndSubjectIdAndTypeAndSyncLogId(String studentId, String subjectId, String type, String syncLogId);

    /**
     * Alerts of the given types sent to any of {@code studentIds} since {@code since} — the whole
     * cooldown lookup for a roster in one query. The subject is left joined because aggregate
     * alerts carry none.
     */
    @Query("""
            SELECT n FROM Notification n
              LEFT JOIN FETCH n.subject
             WHERE n.student.id IN :studentIds
               AND n.type IN :types
               AND n.createdAt >= :since
            """)
    List<Notification> findRecentAlerts(
            @Param("studentIds") Collection<String> studentIds,
            @Param("types") Collection<String> types,
            @Param("since") LocalDateTime since
    );

    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.emailStatus = :status, n.emailedAt = :emailedAt WHERE n.id IN :ids")
    int markEmailOutcome(
            @Param("ids") Collection<String> ids,
            @Param("status") NotificationEmailStatus status,
            @Param("emailedAt") LocalDateTime emailedAt
    );
}
