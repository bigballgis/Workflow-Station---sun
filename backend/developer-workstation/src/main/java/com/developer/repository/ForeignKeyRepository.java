package com.developer.repository;

import com.developer.entity.ForeignKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 外键仓库
 */
@Repository
public interface ForeignKeyRepository extends JpaRepository<ForeignKey, Long> {

    List<ForeignKey> findByTableDefinitionId(Long tableId);

    List<ForeignKey> findByRefTableDefinitionId(Long refTableId);

    @Query("SELECT f FROM ForeignKey f JOIN f.tableDefinition t WHERE t.functionUnit.id = :functionUnitId")
    List<ForeignKey> findByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);

    /**
     * Bulk-delete all foreign keys of a function unit. Must run BEFORE clearing tableDefinitions on
     * re-import/rollback: a FK's field_id/ref_field_id are NOT NULL, so letting the field/table delete
     * cascade dissociate them first triggers an UPDATE ... field_id=null and violates the constraint.
     */
    @Modifying
    @Query(value = "DELETE FROM dw_foreign_keys WHERE table_id IN ("
            + "SELECT id FROM dw_table_definitions WHERE function_unit_id = :functionUnitId)",
            nativeQuery = true)
    void deleteByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);
}
