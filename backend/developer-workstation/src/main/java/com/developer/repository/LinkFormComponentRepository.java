package com.developer.repository;

import com.developer.entity.LinkFormComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LinkFormComponentRepository extends JpaRepository<LinkFormComponent, Long> {
    
    List<LinkFormComponent> findByFunctionUnitIdOrderBySortOrderAsc(Long functionUnitId);
    
    List<LinkFormComponent> findByFunctionUnitIdAndIdIn(Long functionUnitId, List<Long> ids);
}
