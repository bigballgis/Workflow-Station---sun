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
 * 表单表绑定仓库
 */
@Repository
public interface FormTableBindingRepository extends JpaRepository<FormTableBinding, Long> {
    
    /**
     * 按表单ID查询所有绑定，按排序顺序排列
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.form.id = :formId ORDER BY b.sortOrder")
    List<FormTableBinding> findByFormIdOrderBySortOrder(@Param("formId") Long formId);
    
    /**
     * 按表单ID查询所有绑定，同时加载表信息
     */
    @Query("SELECT b FROM FormTableBinding b LEFT JOIN FETCH b.table WHERE b.form.id = :formId ORDER BY b.sortOrder")
    List<FormTableBinding> findByFormIdWithTable(@Param("formId") Long formId);
    
    /**
     * 按表单ID和绑定类型查询
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.form.id = :formId AND b.bindingType = :bindingType")
    Optional<FormTableBinding> findByFormIdAndBindingType(@Param("formId") Long formId, @Param("bindingType") BindingType bindingType);
    
    /**
     * 检查表单是否已绑定指定表
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.form.id = :formId AND b.table.id = :tableId")
    boolean existsByFormIdAndTableId(@Param("formId") Long formId, @Param("tableId") Long tableId);
    
    /**
     * 检查表是否被任何表单绑定
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.table.id = :tableId")
    boolean existsByTableId(@Param("tableId") Long tableId);
    
    /**
     * 检查表单是否已有主表绑定
     */
    @Query("SELECT COUNT(b) > 0 FROM FormTableBinding b WHERE b.form.id = :formId AND b.bindingType = :bindingType")
    boolean existsByFormIdAndBindingType(@Param("formId") Long formId, @Param("bindingType") BindingType bindingType);
    
    /**
     * 删除表单的所有绑定
     */
    @Modifying
    @Query("DELETE FROM FormTableBinding b WHERE b.form.id = :formId")
    void deleteByFormId(@Param("formId") Long formId);
    
    /**
     * 统计表单的绑定数量
     */
    @Query("SELECT COUNT(b) FROM FormTableBinding b WHERE b.form.id = :formId")
    long countByFormId(@Param("formId") Long formId);
    
    /**
     * 按表ID查询所有绑定（用于检查表是否被引用）
     */
    @Query("SELECT b FROM FormTableBinding b WHERE b.table.id = :tableId")
    List<FormTableBinding> findByTableId(@Param("tableId") Long tableId);
}
