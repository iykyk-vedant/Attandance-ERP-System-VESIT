package com.vesit.openattend.repository;

import com.vesit.openattend.entity.WorksheetMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorksheetMappingRepository extends JpaRepository<WorksheetMapping, String> {
    Optional<WorksheetMapping> findBySubjectId(String subjectId);
    Optional<WorksheetMapping> findBySubjectCode(String subjectCode);
    List<WorksheetMapping> findByIsActiveTrue();
}
