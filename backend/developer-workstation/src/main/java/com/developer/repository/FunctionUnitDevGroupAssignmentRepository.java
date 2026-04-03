package com.developer.repository;

import com.developer.entity.FunctionUnitDevGroupAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FunctionUnitDevGroupAssignmentRepository extends JpaRepository<FunctionUnitDevGroupAssignment, Long> {

    List<FunctionUnitDevGroupAssignment> findByFunctionUnitId(Long functionUnitId);

    @Modifying
    @Query("DELETE FROM FunctionUnitDevGroupAssignment a WHERE a.functionUnitId = :functionUnitId")
    void deleteByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);

    @Query("SELECT DISTINCT a.functionUnitId FROM FunctionUnitDevGroupAssignment a WHERE a.virtualGroupId IN :groupIds")
    List<Long> findDistinctFunctionUnitIdsByVirtualGroupIdIn(@Param("groupIds") Collection<String> groupIds);
}
