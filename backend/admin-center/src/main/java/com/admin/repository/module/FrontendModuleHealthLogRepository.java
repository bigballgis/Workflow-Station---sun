package com.admin.repository.module;

import com.admin.entity.module.FrontendModuleHealthLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FrontendModuleHealthLogRepository extends JpaRepository<FrontendModuleHealthLog, Long> {

    List<FrontendModuleHealthLog> findByModuleRegistryIdOrderByCheckedAtDesc(Long moduleRegistryId);
}
