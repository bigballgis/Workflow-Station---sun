package com.developer.component;

import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图标库组件接口
 */
public interface IconLibraryComponent {
    
    /**
     * 上传图标
     */
    Icon upload(MultipartFile file, String name, IconCategory category, String tags);
    
    /**
     * 删除图标
     */
    void delete(Long id);
    
    /**
     * 根据ID获取图标
     */
    Icon getById(Long id);
    
    /**
     * 搜索图标
     */
    Page<Icon> search(String keyword, IconCategory category, Pageable pageable);
    
    /**
     * 获取图标数据
     */
    byte[] getIconData(Long id);
    
    /**
     * 检查图标是否被使用
     */
    boolean isIconInUse(Long id);
    
    /**
     * 验证文件格式和大小
     */
    void validateFile(MultipartFile file);
    
    /**
     * 优化SVG代码
     */
    byte[] optimizeSvg(byte[] svgData);
}
