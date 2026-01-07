package com.developer.repository;

import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingType;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<FormTableBinding> findByFormIdOrderBySortOrder(Long formId);
    
    /**
     * 按表单ID查询所有绑定，同时加载表信息
     */
    @Query("SELECT b FROM FormTableBinding b LEFT JOIN FETCH b.table WHERE b.form.id = :formId ORDER BY b.sortOrder")
    List<FormTableBinding> findByFormIdWithTable(@Param("formId") Long formId);
    
    /**
     * 按表单ID和绑定类型查询
     */
    Optional<FormTableBinding> findByFormIdAndBindingType(Long formId, BindingType bindingType);
    
    /**
     * 检查表单是否已绑定指定表
     */
    boolean existsByFormIdAndTableId(Long formId, Long tableId);
    
    /**
     * 检查表是否被任何表单绑定
     */
    boolean existsByTableId(Long tableId);
    
    /**
     * 检查表单是否已有主表绑定
     */
    boolean existsByFormIdAndBindingType(Long formId, BindingType bindingType);
    
    /**
     * 删除表单的所有绑定
     */
    void deleteByFormId(Long formId);
    
    /**
     * 统计表单的绑定数量
     */
    long countByFormId(Long formId);
    
    /**
     * 按表ID查询所有绑定（用于检查表是否被引用）
     */
    List<FormTableBinding> findByTableId(Long tableId);
}
