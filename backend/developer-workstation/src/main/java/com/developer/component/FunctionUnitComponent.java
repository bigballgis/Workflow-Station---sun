package com.developer.component;

import com.developer.dto.DevGroupAssignmentRequest;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.dto.VersionResponse;
import com.developer.entity.FunctionUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 功能单元组件接口
 */
public interface FunctionUnitComponent {
    
    /**
     * 创建功能单元
     */
    FunctionUnit create(FunctionUnitRequest request);
    
    /**
     * 更新功能单元
     */
    FunctionUnit update(Long id, FunctionUnitRequest request);
    
    /**
     * 删除功能单元
     */
    void delete(Long id);
    
    /**
     * 根据ID获取功能单元
     */
    FunctionUnit getById(Long id);
    
    /**
     * 根据ID获取功能单元响应DTO
     */
    FunctionUnitResponse getByIdAsResponse(Long id);
    
    /**
     * 分页查询功能单元
     */
    Page<FunctionUnitResponse> list(String name, String status, Pageable pageable);
    
    /**
     * 发布功能单元
     */
    FunctionUnit publish(Long id, String changeLog);
    
    /**
     * 克隆功能单元
     */
    FunctionUnit clone(Long id, String newName);
    
    /**
     * 验证功能单元完整性
     */
    ValidationResult validate(Long id);
    
    /**
     * 获取版本历史
     */
    List<VersionResponse> getVersionHistory(Long functionUnitId);
    
    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 检查名称是否存在（排除指定ID）
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * 替换虚拟开发组分配（仅 Team Lead 创建者或 Technical Lead）
     */
    void replaceDevGroupAssignments(Long functionUnitId, DevGroupAssignmentRequest request);

    /**
     * 当前功能单元已分配的 sys_virtual_groups.id 列表
     */
    java.util.List<String> getDevGroupAssignments(Long functionUnitId);
}
