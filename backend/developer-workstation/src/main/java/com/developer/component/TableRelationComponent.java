package com.developer.component;

import com.developer.dto.TableRelationDTO;

import java.util.List;

/**
 * 表关系组件接口
 */
public interface TableRelationComponent {

    /**
     * 获取功能单元的所有表关系
     */
    List<TableRelationDTO> getByFunctionUnitId(Long functionUnitId);

    /**
     * 批量保存表关系（替换现有）
     */
    List<TableRelationDTO> saveAll(Long functionUnitId, List<TableRelationDTO> dtos);

    /**
     * 删除功能单元的所有表关系
     */
    void deleteByFunctionUnitId(Long functionUnitId);
}
