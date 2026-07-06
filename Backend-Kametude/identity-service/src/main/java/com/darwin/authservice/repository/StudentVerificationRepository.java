package com.darwin.authservice.repository;

import com.darwin.authservice.entity.StudentVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentVerificationRepository extends JpaRepository<StudentVerification, UUID> {
    List<StudentVerification> findByStudentIdOrderBySubmittedAtDesc(UUID studentId);
    List<StudentVerification> findAllByOrderBySubmittedAtDesc();
}
