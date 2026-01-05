package com.developer.component.impl;

import com.developer.component.IconLibraryComponent;
import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 图标库组件实现
 */
@Component
@Slf4j
public class IconLibraryComponentImpl implements IconLibraryComponent {
    
    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList("svg", "png", "ico");
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    
    private final IconRepository iconRepository;
    private final FunctionUnitRepository functionUnitRepository;
    
    /**
     * 简化构造函数，用于测试
     */
    public IconLibraryComponentImpl(IconRepository iconRepository) {
        this(iconRepository, null);
    }
    
    /**
     * 完整构造函数
     */
    public IconLibraryComponentImpl(
            IconRepository iconRepository,
            FunctionUnitRepository functionUnitRepository) {
        this.iconRepository = iconRepository;
        this.functionUnitRepository = functionUnitRepository;
    }
    
    @Override
    @Transactional
    public Icon upload(MultipartFile file, String name, IconCategory category, String tags) {
        validateFile(file);
        
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        
        byte[] fileData;
        try {
            fileData = file.getBytes();
            
            // 如果是SVG，进行优化
            if ("svg".equalsIgnoreCase(fileType)) {
                fileData = optimizeSvg(fileData);
            }
        } catch (IOException e) {
            throw new BusinessException("SYS_FILE_READ_ERROR", "读取文件失败");
        }
        
        Icon icon = Icon.builder()
                .name(name)
                .category(category)
                .fileType(fileType)
                .fileData(fileData)
                .fileSize(fileData.length)
                .tags(tags)
                .build();
        
        return iconRepository.save(icon);
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        Icon icon = getById(id);
        
        if (isIconInUse(id)) {
            throw new BusinessException("BIZ_ICON_IN_USE", 
                    "图标正在被使用，无法删除",
                    "请先解除图标的使用");
        }
        
        iconRepository.delete(icon);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Icon getById(Long id) {
        return iconRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Icon", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Icon> search(String keyword, IconCategory category, Pageable pageable) {
        return iconRepository.search(keyword, category, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public byte[] getIconData(Long id) {
        Icon icon = getById(id);
        return icon.getFileData();
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isIconInUse(Long id) {
        return functionUnitRepository.findAll().stream()
                .anyMatch(fu -> fu.getIcon() != null && fu.getIcon().getId().equals(id));
    }
    
    @Override
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("VAL_FILE_EMPTY", "文件不能为空");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("VAL_FILE_TOO_LARGE", 
                    "文件大小超过限制（最大2MB）",
                    "请上传小于2MB的文件");
        }
        
        String fileType = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_FILE_TYPES.contains(fileType.toLowerCase())) {
            throw new BusinessException("VAL_INVALID_FILE_TYPE", 
                    "不支持的文件格式: " + fileType,
                    "支持的格式: SVG, PNG, ICO");
        }
    }
    
    @Override
    public byte[] optimizeSvg(byte[] svgData) {
        String svg = new String(svgData);
        
        // 移除注释
        svg = svg.replaceAll("<!--[\\s\\S]*?-->", "");
        
        // 移除多余空白
        svg = svg.replaceAll("\\s+", " ");
        svg = svg.replaceAll("> <", "><");
        
        // 移除空属性
        svg = svg.replaceAll("\\s+[a-zA-Z-]+=\"\"", "");
        
        return svg.trim().getBytes();
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
