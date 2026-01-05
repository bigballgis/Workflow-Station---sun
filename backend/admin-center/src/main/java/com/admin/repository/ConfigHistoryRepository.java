package com.admin.repository;

import com.admin.entity.ConfigHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigHistoryRepository extends JpaRepository<ConfigHistory, String> {
    
    Page<ConfigHistory> findByConfigIdOrderByChangedAtDesc(String configId, Pageable pageable);
    
    List<ConfigHistory> findByConfigKeyOrderByChangedAtDesc(String configKey);
    
    Optional<ConfigHistory> findByConfigIdAndNewVersion(String configId, Integer version);
}
