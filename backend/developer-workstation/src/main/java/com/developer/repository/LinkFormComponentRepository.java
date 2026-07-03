package com.developer.repository;

import com.developer.entity.LinkFormComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LinkFormComponentRepository extends JpaRepository<LinkFormComponent, Long> {

    List<LinkFormComponent> findByFunctionUnitIdOrderBySortOrderAsc(Long functionUnitId);

    List<LinkFormComponent> findByFunctionUnitIdAndIdIn(Long functionUnitId, List<Long> ids);

    @Modifying
    @Query("DELETE FROM LinkFormComponent c WHERE c.functionUnitId = :functionUnitId")
    void deleteByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);
}
