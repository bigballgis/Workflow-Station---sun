package com.portal.repository;

import com.portal.entity.DashboardLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 工作台布局Repository
 */
@Repository
public interface DashboardLayoutRepository extends JpaRepository<DashboardLayout, Long> {

    List<DashboardLayout> findByUserIdOrderByGridYAscGridXAsc(String userId);

    Optional<DashboardLayout> findByUserIdAndComponentId(String userId, String componentId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndComponentId(String userId, String componentId);
}
