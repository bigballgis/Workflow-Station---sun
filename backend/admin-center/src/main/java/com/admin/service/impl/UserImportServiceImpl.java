package com.admin.service.impl;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.service.AuditService;
import com.admin.service.UserImportService;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户导入服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportServiceImpl implements UserImportService {
    
    private final UserManagerComponent userManager;
    private final AuditService auditService;
    
    @Override
    public List<UserCreateRequest> parseExcelFile(MultipartFile file) {
        List<UserCreateRequest> users = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // 跳过标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                UserCreateRequest user = parseExcelRow(row);
                if (user != null) {
                    users.add(user);
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse Excel file", e);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage());
        }
        
        return users;
    }
    
    @Override
    public List<UserCreateRequest> parseCsvFile(MultipartFile file) {
        List<UserCreateRequest> users = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            
            // 跳过标题行
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                UserCreateRequest user = parseCsvRow(row);
                if (user != null) {
                    users.add(user);
                }
            }
        } catch (IOException | CsvException e) {
            log.error("Failed to parse CSV file", e);
            throw new RuntimeException("CSV文件解析失败: " + e.getMessage());
        }
        
        return users;
    }
    
    @Override
    @Transactional
    public BatchImportResult importUsers(MultipartFile file) {
        log.info("Starting user import from file: {}", file.getOriginalFilename());
        
        Instant startTime = Instant.now();
        String fileName = file.getOriginalFilename();
        
        try {
            // 根据文件类型解析
            List<UserCreateRequest> users;
            if (fileName != null && fileName.endsWith(".csv")) {
                users = parseCsvFile(file);
            } else {
                users = parseExcelFile(file);
            }
            
            // 验证数据
            List<String> validationErrors = validateImportData(users);
            if (!validationErrors.isEmpty()) {
                return BatchImportResult.builder()
                        .success(false)
                        .fileName(fileName)
                        .totalCount(users.size())
                        .successCount(0)
                        .failureCount(users.size())
                        .errors(String.join("\n", validationErrors))
                        .startTime(startTime)
                        .endTime(Instant.now())
                        .build();
            }
            
            // 执行导入
            int successCount = 0;
            int failureCount = 0;
            StringBuilder errors = new StringBuilder();
            
            for (int i = 0; i < users.size(); i++) {
                UserCreateRequest user = users.get(i);
                try {
                    userManager.createUser(user);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.append(String.format("行 %d (%s): %s\n", 
                            i + 2, user.getUsername(), e.getMessage()));
                }
            }
            
            BatchImportResult result = BatchImportResult.builder()
                    .success(failureCount == 0)
                    .fileName(fileName)
                    .totalCount(users.size())
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .errors(errors.toString())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .build();
            
            auditService.recordBatchImport(result);
            
            log.info("User import completed: {} success, {} failed", successCount, failureCount);
            return result;
            
        } catch (Exception e) {
            log.error("User import failed", e);
            return BatchImportResult.builder()
                    .success(false)
                    .fileName(fileName)
                    .errors("导入失败: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .build();
        }
    }
    
    @Override
    public List<String> validateImportData(List<UserCreateRequest> users) {
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < users.size(); i++) {
            UserCreateRequest user = users.get(i);
            int rowNum = i + 2; // Excel行号从2开始（1是标题）
            
            if (user.getUsername() == null || user.getUsername().isBlank()) {
                errors.add(String.format("行 %d: 用户名不能为空", rowNum));
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                errors.add(String.format("行 %d: 邮箱不能为空", rowNum));
            }
            if (user.getFullName() == null || user.getFullName().isBlank()) {
                errors.add(String.format("行 %d: 姓名不能为空", rowNum));
            }
            if (user.getInitialPassword() == null || user.getInitialPassword().length() < 8) {
                errors.add(String.format("行 %d: 密码长度必须至少8位", rowNum));
            }
        }
        
        return errors;
    }
    
    private UserCreateRequest parseExcelRow(Row row) {
        try {
            return UserCreateRequest.builder()
                    .username(getCellStringValue(row.getCell(0)))
                    .email(getCellStringValue(row.getCell(1)))
                    .fullName(getCellStringValue(row.getCell(2)))
                    .employeeId(getCellStringValue(row.getCell(3)))
                    .departmentId(getCellStringValue(row.getCell(4)))
                    .position(getCellStringValue(row.getCell(5)))
                    .initialPassword(getCellStringValue(row.getCell(6)))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Excel row", e);
            return null;
        }
    }
    
    private UserCreateRequest parseCsvRow(String[] row) {
        try {
            if (row.length < 7) return null;
            
            return UserCreateRequest.builder()
                    .username(row[0].trim())
                    .email(row[1].trim())
                    .fullName(row[2].trim())
                    .employeeId(row.length > 3 ? row[3].trim() : null)
                    .departmentId(row.length > 4 ? row[4].trim() : null)
                    .position(row.length > 5 ? row[5].trim() : null)
                    .initialPassword(row[6].trim())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse CSV row", e);
            return null;
        }
    }
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
    
    @Override
    public byte[] generateImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户导入模板");
            
            // 创建标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // 创建标题行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"用户名*", "邮箱*", "姓名*", "工号", "部门ID", "职位", "初始密码*"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }
            
            // 创建示例数据行
            Row exampleRow = sheet.createRow(1);
            String[] examples = {"zhangsan", "zhangsan@example.com", "张三", "EMP001", "", "工程师", "Password123!"};
            for (int i = 0; i < examples.length; i++) {
                exampleRow.createCell(i).setCellValue(examples[i]);
            }
            
            // 写入字节数组
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            log.error("Failed to generate import template", e);
            throw new RuntimeException("生成导入模板失败: " + e.getMessage());
        }
    }
}
