package com.developer.repository;

import com.developer.entity.FormStageBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
     * 按功能单元编码 + 阶段ID查询绑定。
     * <p>BPMN 节点 id 只在单个流程内唯一，同一 stageId 可合法存在于多个功能单元
     * （表上只有 {@code UNIQUE(form_id, stage_id)}），故解析任务表单必须按功能单元收窄。</p>
     */
    @Query("SELECT b FROM FormStageBinding b WHERE b.stageId = :stageId "
            + "AND b.form.functionUnit.code = :functionUnitCode ORDER BY b.form.id DESC")
    List<FormStageBinding> findByFunctionUnitCodeAndStageId(@Param("functionUnitCode") String functionUnitCode,
                                                            @Param("stageId") String stageId);

    /**
     * 按阶段ID查询绑定（未按功能单元收窄，仅在调用方无法解析功能单元时使用）。
     * <p>返回 List 而非 Optional：跨功能单元的 stageId 冲突是合法数据，
     * 用 Optional 会抛 {@code IncorrectResultSizeDataAccessException}（HTTP 500）。</p>
     */
    @Query("SELECT b FROM FormStageBinding b WHERE b.stageId = :stageId ORDER BY b.form.id DESC")
    List<FormStageBinding> findByStageIdOrderByFormIdDesc(@Param("stageId") String stageId);

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
