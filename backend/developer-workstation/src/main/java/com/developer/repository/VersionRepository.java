package com.developer.repository;

import com.developer.entity.Version;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 版本仓库
 */
@Repository
public interface VersionRepository extends JpaRepository<Version, Long> {
    
    List<Version> findByFunctionUnitIdOrderByPublishedAtDesc(Long functionUnitId);
    
    Optional<Version> findByFunctionUnitIdAndVersionNumber(Long functionUnitId, String versionNumber);
    
    @Query("SELECT v FROM Version v WHERE v.functionUnit.id = :functionUnitId ORDER BY v.publishedAt DESC LIMIT 1")
    Optional<Version> findLatestByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);
    
    long countByFunctionUnitId(Long functionUnitId);
}
