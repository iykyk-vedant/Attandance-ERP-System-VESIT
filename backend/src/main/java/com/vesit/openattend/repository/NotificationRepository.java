package com.vesit.openattend.repository;

import com.vesit.openattend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<Notification> findByStudentIdAndIsReadFalseOrderByCreatedAtDesc(String studentId);
    long countByStudentIdAndIsReadFalse(String studentId);
    boolean existsByStudentIdAndSubjectIdAndTypeAndSyncLogId(String studentId, String subjectId, String type, String syncLogId);
}
