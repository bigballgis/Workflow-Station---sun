package com.admin.repository;

import com.admin.entity.Alert;
import com.admin.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {
    List<Alert> findByStatus(AlertStatus status);
    Page<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);
    long countByStatus(AlertStatus status);
}
