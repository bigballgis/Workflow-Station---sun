package com.developer.repository;

import com.developer.entity.SubTableViewConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Sub-Table View Config Repository
 */
@Repository
public interface SubTableViewConfigRepository extends JpaRepository<SubTableViewConfig, Long> {

    /**
     * Find by binding ID
     */
    Optional<SubTableViewConfig> findByBindingId(Long bindingId);

    /**
     * Check if view config exists for binding
     */
    boolean existsByBindingId(Long bindingId);

    /**
     * Every sub-table list view whose binding targets {@code tableId}, with its columns fetched.
     *
     * <p>Used by Table Design field-rename propagation: a renamed field must be rewritten in the
     * sub-table list view columns too, otherwise the column keeps pointing at a field name that no
     * longer exists and the Portal renders "-" for it (the row data carries the NEW name).
     */
    @Query("SELECT DISTINCT c FROM SubTableViewConfig c "
            + "LEFT JOIN FETCH c.viewFields "
            + "WHERE c.binding.table.id = :tableId")
    List<SubTableViewConfig> findByBindingTableIdWithFields(@Param("tableId") Long tableId);

    /**
     * Delete the view fields of all configs belonging to a function unit's form-table bindings.
     * These tables have no FK/cascade, so cleanup on re-import/delete must be explicit
     * (otherwise dangling configs collide with the unique binding_id index on the next import).
     * Must run before {@link #deleteConfigsByFunctionUnitId(Long)} (fields reference configs).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM dw_sub_table_view_fields WHERE view_config_id IN ("
            + "SELECT c.id FROM dw_sub_table_view_configs c "
            + "JOIN dw_form_table_bindings b ON b.id = c.binding_id "
            + "JOIN dw_form_definitions f ON f.id = b.form_id "
            + "WHERE f.function_unit_id = :functionUnitId)", nativeQuery = true)
    void deleteViewFieldsByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);

    /**
     * Delete all sub-table view configs belonging to a function unit's form-table bindings.
     * Call {@link #deleteViewFieldsByFunctionUnitId(Long)} first.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM dw_sub_table_view_configs WHERE binding_id IN ("
            + "SELECT b.id FROM dw_form_table_bindings b "
            + "JOIN dw_form_definitions f ON f.id = b.form_id "
            + "WHERE f.function_unit_id = :functionUnitId)", nativeQuery = true)
    void deleteConfigsByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);
}
