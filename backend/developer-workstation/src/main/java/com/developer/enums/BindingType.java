package com.developer.enums;

/**
 * 表单表绑定类型
 */
public enum BindingType {
    /**
     * 主表 - 表单的主要数据来源，支持完整的增删改查
     */
    PRIMARY,
    
    /**
     * 子表 - 与主表存在一对多关系的从属表
     */
    SUB,
    
    /**
     * 关联表 - 与主表存在多对多或引用关系的表
     */
    RELATED
}
