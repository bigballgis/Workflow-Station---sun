package com.admin.repository;

import com.admin.entity.FunctionUnitAuditAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元审计授权 Repository。
 *
 * <p>与 {@link FunctionUnitAccessRepository} 完全独立 —— 审计权不得与「可发起」权混用。
 */
@Repository
public interface FunctionUnitAuditAccessRepository extends JpaRepository<FunctionUnitAuditAccess, String> {

    List<FunctionUnitAuditAccess> findByFunctionUnitId(String functionUnitId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM FunctionUnitAuditAccess a "
            + "WHERE a.functionUnit.id = :functionUnitId AND a.targetType = 'ROLE' AND a.targetId = :roleId")
    boolean existsByFunctionUnitIdAndRoleId(@Param("functionUnitId") String functionUnitId,
                                            @Param("roleId") String roleId);

    @Query("SELECT a FROM FunctionUnitAuditAccess a "
            + "WHERE a.functionUnit.id = :functionUnitId AND a.targetType = 'ROLE' AND a.targetId = :roleId")
    Optional<FunctionUnitAuditAccess> findByFunctionUnitIdAndRoleId(@Param("functionUnitId") String functionUnitId,
                                                                    @Param("roleId") String roleId);

    void deleteByFunctionUnitId(String functionUnitId);

    @Modifying
    @Query("DELETE FROM FunctionUnitAuditAccess a WHERE a.targetType = 'ROLE' AND a.targetId = :roleId")
    void deleteByRoleId(@Param("roleId") String roleId);

    /** 某角色集合可审计的功能单元 id 列表。 */
    @Query("SELECT DISTINCT a.functionUnit.id FROM FunctionUnitAuditAccess a "
            + "WHERE a.targetType = 'ROLE' AND a.targetId IN :roleIds")
    List<String> findAuditableFunctionUnitIdsByRoles(@Param("roleIds") List<String> roleIds);
}
