package com.admin.service;

import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.response.BatchImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户导入服务接口
 */
public interface UserImportService {
    
    /**
     * 解析Excel文件
     */
    List<UserCreateRequest> parseExcelFile(MultipartFile file);
    
    /**
     * 解析CSV文件
     */
    List<UserCreateRequest> parseCsvFile(MultipartFile file);
    
    /**
     * 批量导入用户
     */
    BatchImportResult importUsers(MultipartFile file);
    
    /**
     * 验证导入数据
     */
    List<String> validateImportData(List<UserCreateRequest> users);
    
    /**
     * 生成导入模板
     */
    byte[] generateImportTemplate();
}
