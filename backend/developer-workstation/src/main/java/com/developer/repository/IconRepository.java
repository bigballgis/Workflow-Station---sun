package com.developer.repository;

import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 图标仓库
 */
@Repository
public interface IconRepository extends JpaRepository<Icon, Long>, JpaSpecificationExecutor<Icon> {
    
    List<Icon> findByCategory(IconCategory category);
    
    Page<Icon> findByCategory(IconCategory category, Pageable pageable);
    
    boolean existsByName(String name);

    /**
     * Name is a display label (often the uploaded filename) and is not unique.
     * Callers that match by name take the oldest row when several share it.
     */
    Optional<Icon> findFirstByNameOrderByIdAsc(String name);
}
