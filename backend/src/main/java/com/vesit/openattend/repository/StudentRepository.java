package com.vesit.openattend.repository;

import com.vesit.openattend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, String> {
    Optional<Student> findByRollNo(String rollNo);
    Optional<Student> findByUserId(String userId);
    Optional<Student> findByUserEmail(String email);
    boolean existsByRollNo(String rollNo);
}
