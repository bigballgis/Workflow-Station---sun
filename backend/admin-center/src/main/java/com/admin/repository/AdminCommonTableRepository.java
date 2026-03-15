package com.admin.repository;

import com.admin.entity.AdminCommonTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AdminCommonTableRepository extends JpaRepository<AdminCommonTable, Long> {

    List<AdminCommonTable> findByStatusOrderByDeployedAtDesc(String status);

    List<AdminCommonTable> findByCodeOrderByDeployedAtDesc(String code);

    @Modifying
    @Transactional
    @Query("UPDATE AdminCommonTable t SET t.enabled = :enabled WHERE t.id = :id")
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Modifying
    @Transactional
    @Query("UPDATE AdminCommonTable t SET t.status = 'ARCHIVED' WHERE t.id = :id")
    int archiveById(@Param("id") Long id);
}
