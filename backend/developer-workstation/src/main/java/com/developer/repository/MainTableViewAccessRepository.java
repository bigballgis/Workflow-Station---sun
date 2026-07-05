package com.developer.repository;

import com.developer.entity.MainTableViewAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MainTableViewAccessRepository extends JpaRepository<MainTableViewAccess, Long> {

    List<MainTableViewAccess> findByViewConfigId(Long viewConfigId);

    /**
     * Must flush before re-inserting access rows on the same view — otherwise lazy
     * {@code config.accessRules} can reload stale rows and hit idx_mtv_access_view_target.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MainTableViewAccess a WHERE a.viewConfig.id = :viewConfigId")
    void deleteByViewConfigId(@Param("viewConfigId") Long viewConfigId);
}
