package com.developer.component;

import com.developer.dto.CommonTableRequest;
import com.developer.entity.CommonTableDefinition;

import java.util.List;

/**
 * 公共表管理组件接口
 */
public interface CommonTableComponent {

    List<CommonTableDefinition> findAll();

    CommonTableDefinition findById(Long id);

    CommonTableDefinition findByCode(String code);

    CommonTableDefinition create(CommonTableRequest request, String createdBy);

    CommonTableDefinition update(Long id, CommonTableRequest request);

    void delete(Long id);
}
