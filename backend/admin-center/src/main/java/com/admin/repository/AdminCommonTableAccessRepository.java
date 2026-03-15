package com.admin.repository;

import com.admin.entity.AdminCommonTableAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminCommonTableAccessRepository extends JpaRepository<AdminCommonTableAccess, Long> {

    List<AdminCommonTableAccess> findByCommonTableId(Long commonTableId);

    boolean existsByCommonTableIdAndTargetId(Long commonTableId, String targetId);
}
