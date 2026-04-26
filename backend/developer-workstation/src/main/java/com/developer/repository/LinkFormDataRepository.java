package com.developer.repository;

import com.developer.entity.LinkFormData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkFormDataRepository extends JpaRepository<LinkFormData, Long> {
    
    Optional<LinkFormData> findByComponentIdAndSubTableRowId(Long componentId, Long subTableRowId);
    
    List<LinkFormData> findBySubTableRowId(Long subTableRowId);
    
    List<LinkFormData> findByComponentId(Long componentId);
    
    void deleteByComponentIdAndSubTableRowId(Long componentId, Long subTableRowId);
}
