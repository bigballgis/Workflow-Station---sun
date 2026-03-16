package com.portal.repository;

import com.platform.security.entity.BusinessUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, String> {
    List<BusinessUnit> findByParentIdAndStatus(String parentId, String status);
}
