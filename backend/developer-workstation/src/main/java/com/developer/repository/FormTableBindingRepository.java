package com.developer.repository;

import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Form-Table Binding Repository
 */
@Repository
public interface FormTableBindingRepository extends JpaRepository<FormTableBinding, Long> {
    
    /**
     * Query by binding ID with JOIN FETCH table + form.
     * Assembling {@link com.developer.dto.FormTableBindingResponse} calls {@code getTable*()} and {@code getFormId()},
     * both of which may touch LAZY associations; outside a transaction or with OSIV off, they must be initialized
     * beforehand, otherwise LazyInitializationException → 500.
     */
    @Query("SELECT b FROM FormTableBinding b LEFT JOIN FETCH b.table LEFT JOIN FETCH b.form WHERE b.id = :id")
    Optional<FormTableBinding> findByIdWithTable(@Param("id") Long id);
    
    /**
     * Query all bindings by form ID, ordered by sort order.
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.form.id = :formId ORDER BY b.sortOrder")
    List<FormTableBinding> findByFormIdOrderBySortOrder(@Param("formId") Long formId);
    
    /**
     * Query all bindings by form ID, loading table and form (consistent with DTO assembly fields).
     */
    @Query("SELECT b FROM FormTableBinding b LEFT JOIN FETCH b.table LEFT JOIN FETCH b.form WHERE b.form.id = :formId ORDER BY b.sortOrder")
    List<FormTableBinding> findByFormIdWithTable(@Param("formId") Long formId);
    
    /**
     * Query by form ID and binding type.
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.form.id = :formId AND b.bindingType = :bindingType")
    Optional<FormTableBinding> findByFormIdAndBindingType(@Param("formId") Long formId, @Param("bindingType") BindingType bindingType);
    
    /**
     * Check if a form has already bound the specified table.
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.form.id = :formId AND b.table.id = :tableId")
    boolean existsByFormIdAndTableId(@Param("formId") Long formId, @Param("tableId") Long tableId);
    
    /**
     * Check if a table is bound by any form.
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.table.id = :tableId")
    boolean existsByTableId(@Param("tableId") Long tableId);
    
    /**
     * Check if a form already has a primary binding.
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.form.id = :formId AND b.bindingType = :bindingType")
    boolean existsByFormIdAndBindingType(@Param("formId") Long formId, @Param("bindingType") BindingType bindingType);
    
    /**
     * Delete all bindings of a form.
     */
    @Modifying
    @Query("DELETE FROM FormTableBinding b WHERE b.form.id = :formId")
    void deleteByFormId(@Param("formId") Long formId);
    
    /**
     * Count bindings for a form.
     */
    @Query("SELECT COUNT(b) FROM FormTableBinding b WHERE b.form.id = :formId")
    long countByFormId(@Param("formId") Long formId);
    
    /**
     * Query all bindings by table ID (for checking if a table is referenced).
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.table.id = :tableId")
    List<FormTableBinding> findByTableId(@Param("tableId") Long tableId);

    /**
     * Check if a form has already bound the specified Relation Table.
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.form.id = :formId AND b.relationTableId = :relationTableId")
    boolean existsByFormIdAndRelationTableId(@Param("formId") Long formId, @Param("relationTableId") Long relationTableId);

    /**
     * Query all bindings by form ID and binding type.
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.form.id = :formId AND b.bindingType = :type ORDER BY b.sortOrder")
    List<FormTableBinding> findByFormIdAndBindingTypeList(@Param("formId") Long formId, @Param("type") BindingType type);
}
