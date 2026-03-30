package com.developer.repository;

import com.developer.entity.FormStageBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 表单阶段绑定仓库
 */
@Repository
public interface FormStageBindingRepository extends JpaRepository<FormStageBinding, Long> {

    /**
     * 按表单ID查询所有阶段绑定
     */
    List<FormStageBinding> findByFormId(Long formId);

    /**
     * 按阶段ID查询绑定
     */
    Optional<FormStageBinding> findByStageId(String stageId);

    /**
     * 按多个阶段ID批量查询绑定
     */
    List<FormStageBinding> findByStageIdIn(List<String> stageIds);

    /**
     * 删除表单的所有阶段绑定
     */
    @Modifying
    @Query("DELETE FROM FormStageBinding b WHERE b.form.id = :formId")
    void deleteByFormId(@Param("formId") Long formId);
}
