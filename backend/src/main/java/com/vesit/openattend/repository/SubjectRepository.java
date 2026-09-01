package com.vesit.openattend.repository;

import com.vesit.openattend.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, String> {
    Optional<Subject> findByCode(String code);
    boolean existsByCode(String code);
}
