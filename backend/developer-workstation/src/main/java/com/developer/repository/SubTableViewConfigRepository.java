package com.developer.repository;

import com.developer.entity.SubTableViewConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Sub-Table View Config Repository
 */
@Repository
public interface SubTableViewConfigRepository extends JpaRepository<SubTableViewConfig, Long> {

    /**
     * Find by binding ID
     */
    Optional<SubTableViewConfig> findByBindingId(Long bindingId);

    /**
     * Check if view config exists for binding
     */
    boolean existsByBindingId(Long bindingId);
}
