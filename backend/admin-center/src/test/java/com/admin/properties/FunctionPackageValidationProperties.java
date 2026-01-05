package com.admin.properties;

import com.admin.component.FunctionUnitManagerComponent;
import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.ImportResult;
import com.admin.dto.response.ValidationResult;
import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 功能包验证完整性属性测试
 * 属性 9: 功能包验证完整性
 * 验证需求: 需求 5.1, 5.2
 */
class FunctionPackageValidationProperties {

    private FunctionUnitRepository functionUnitRepository;
    private FunctionUnitDependencyRepository dependencyRepository;
    private FunctionUnitContentRepository contentRepository;
    private FunctionUnitManagerComponent component;

    @BeforeTry
    void setUp() {
        functionUnitRepository = Mockito.mock(FunctionUnitRepository.class);
        dependencyRepository = Mockito.mock(FunctionUnitDependencyRepository.class);
        contentRepository = Mockito.mock(FunctionUnitContentRepository.class);
        component = new FunctionUnitManagerComponent(
                functionUnitRepository, dependencyRepository, contentRepository);
    }

    // ==================== 属性 1: 空文件名验证失败 ====================
    
    @Property(tries = 100)
    void emptyFileNameShouldFailValidation(
            @ForAll("validFileContents") String fileContent) {
        // Given: 空文件名的请求
        FunctionUnitImportRequest request = FunctionUnitImportRequest.builder()
                .fileName("")
                .fileContent(fileContent)
                .build();
        
        // When: 验证功能包
        ValidationResult result = component.validatePackage(request);
        
        // Then: 验证应该失败
        assertThat(result.isFileFormatValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }
    
    @Property(tries = 100)
    void nullFileNameShouldFailValidation(
            @ForAll("validFileContents") String fileContent) {
        // Given: null文件名的请求
        FunctionUnitImportRequest request = FunctionUnitImportRequest.builder()
                .fileName(null)
                .fileContent(fileContent)
                .build();
        
        // When: 验证功能包
        ValidationResult result = component.validatePackage(request);
        
        // Then: 验证应该失败
        assertThat(result.isFileFormatValid()).isFalse();
    }
    
    // ==================== 属性 2: 无效文件扩展名验证失败 ====================
    
    @Property(tries = 100)
    void invalidFileExtensionShouldFailValidation(
            @ForAll("invalidFileExtensions") String fileName,
            @ForAll("validFileContents") String fileContent) {
        // Given: 无效扩展名的请求
        FunctionUnitImportRequest request = FunctionUnitImportRequest.builder()
                .fileName(fileName)
                .fileContent(fileContent)
                .build();
        
        // When: 验证功能包
        ValidationResult result = component.validatePackage(request);
        
        // Then: 验证应该失败
        assertThat(result.isFileFormatValid()).isFalse();
    }
    
    // ==================== 属性 3: 有效文件格式验证通过 ====================
    
    @Property(tries = 100)
    void validFileFormatShouldPassValidation(
            @ForAll("validFileNames") String fileName,
            @ForAll("validFileContents") String fileContent) {
        // Given: 有效格式的请求
        FunctionUnitImportRequest request = FunctionUnitImportRequest.builder()
                .fileName(fileName)
                .fileContent(fileContent)
                .build();
        
        // When: 验证功能包
        ValidationResult result = component.validatePackage(request);
        
        // Then: 文件格式验证应该通过
        assertThat(result.isFileFormatValid()).isTrue();
    }

    // ==================== 属性 4: 空内容验证失败 ====================
    
    @Property(tries = 100)
    void emptyContentShouldFailIntegrityValidation(
            @ForAll("validFileNames") String fileName) {
        // Given: 空内容的请求
        FunctionUnitImportRequest request = FunctionUnitImportRequest.builder()
                .fileName(fileName)
                .fileContent("")
                .build();
        
        // When: 验证功能包
        ValidationResult result = component.validatePackage(request);
        
        // Then: 完整性验证应该失败
        assertThat(result.isIntegrityValid()).isFalse();
    }
    
    // ==================== 属性 5: BPMN语法验证 ====================
    
    @Property(tries = 100)
    void validBpmnShouldPassSyntaxValidation(
            @ForAll("validBpmnContents") String bpmnContent) {
        // Given: 有效的BPMN内容
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .errors(new java.util.ArrayList<>())
                .warnings(new java.util.ArrayList<>())
                .build();
        
        // When: 验证BPMN语法
        boolean isValid = component.validateBpmnSyntax(bpmnContent, result);
        
        // Then: 验证应该通过
        assertThat(isValid).isTrue();
    }
    
    @Property(tries = 100)
    void invalidBpmnShouldFailSyntaxValidation(
            @ForAll("invalidBpmnContents") String bpmnContent) {
        // Given: 无效的BPMN内容
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .errors(new java.util.ArrayList<>())
                .warnings(new java.util.ArrayList<>())
                .build();
        
        // When: 验证BPMN语法
        boolean isValid = component.validateBpmnSyntax(bpmnContent, result);
        
        // Then: 验证应该失败
        assertThat(isValid).isFalse();
    }
    
    // ==================== 属性 6: 数据表结构验证 ====================
    
    @Property(tries = 100)
    void validDataTableShouldPassValidation(
            @ForAll("validDataTableDefinitions") String tableDefinition) {
        // Given: 有效的数据表定义
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .errors(new java.util.ArrayList<>())
                .warnings(new java.util.ArrayList<>())
                .build();
        
        // When: 验证数据表结构
        boolean isValid = component.validateDataTableStructure(tableDefinition, result);
        
        // Then: 验证应该通过
        assertThat(isValid).isTrue();
    }

    // ==================== 属性 7: 表单配置验证 ====================
    
    @Property(tries = 100)
    void validFormConfigShouldPassValidation(
            @ForAll("validFormConfigs") String formConfig) {
        // Given: 有效的表单配置
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .errors(new java.util.ArrayList<>())
                .warnings(new java.util.ArrayList<>())
                .build();
        
        // When: 验证表单配置
        boolean isValid = component.validateFormConfig(formConfig, result);
        
        // Then: 验证应该通过
        assertThat(isValid).isTrue();
    }
    
    @Property(tries = 100)
    void invalidFormConfigShouldFailValidation(
            @ForAll("invalidFormConfigs") String formConfig) {
        // Given: 无效的表单配置
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .errors(new java.util.ArrayList<>())
                .warnings(new java.util.ArrayList<>())
                .build();
        
        // When: 验证表单配置
        boolean isValid = component.validateFormConfig(formConfig, result);
        
        // Then: 验证应该失败
        assertThat(isValid).isFalse();
    }
    
    // ==================== 属性 8: 版本兼容性检查 ====================
    
    @Property(tries = 100)
    void sameVersionShouldBeCompatible(
            @ForAll("validVersions") String version) {
        // Given: 相同版本
        // When: 检查兼容性
        boolean isCompatible = component.isVersionCompatible(version, version);
        
        // Then: 应该兼容
        assertThat(isCompatible).isTrue();
    }
    
    @Property(tries = 100)
    void higherMinorVersionShouldBeCompatible(
            @ForAll @IntRange(min = 1, max = 10) int major,
            @ForAll @IntRange(min = 0, max = 5) int requiredMinor,
            @ForAll @IntRange(min = 0, max = 10) int patch) {
        // Given: 要求的版本和更高次版本号的现有版本
        String requiredVersion = major + "." + requiredMinor + "." + patch;
        String existingVersion = major + "." + (requiredMinor + 1) + ".0";
        
        // When: 检查兼容性
        boolean isCompatible = component.isVersionCompatible(requiredVersion, existingVersion);
        
        // Then: 应该兼容
        assertThat(isCompatible).isTrue();
    }
    
    @Property(tries = 100)
    void differentMajorVersionShouldNotBeCompatible(
            @ForAll @IntRange(min = 1, max = 5) int requiredMajor,
            @ForAll @IntRange(min = 0, max = 10) int minor,
            @ForAll @IntRange(min = 0, max = 10) int patch) {
        // Given: 不同主版本号
        String requiredVersion = requiredMajor + "." + minor + "." + patch;
        String existingVersion = (requiredMajor + 1) + "." + minor + "." + patch;
        
        // When: 检查兼容性
        boolean isCompatible = component.isVersionCompatible(requiredVersion, existingVersion);
        
        // Then: 不应该兼容
        assertThat(isCompatible).isFalse();
    }

    // ==================== 属性 9: 校验和计算一致性 ====================
    
    @Property(tries = 100)
    void checksumShouldBeConsistentForSameContent(
            @ForAll("validFileContents") String content) {
        // Given: 相同内容
        // When: 计算两次校验和
        String checksum1 = component.calculateChecksum(content);
        String checksum2 = component.calculateChecksum(content);
        
        // Then: 校验和应该相同
        assertThat(checksum1).isEqualTo(checksum2);
    }
    
    @Property(tries = 100)
    void differentContentShouldHaveDifferentChecksum(
            @ForAll("validFileContents") String content1,
            @ForAll("validFileContents") String content2) {
        // Given: 不同内容
        Assume.that(!content1.equals(content2));
        
        // When: 计算校验和
        String checksum1 = component.calculateChecksum(content1);
        String checksum2 = component.calculateChecksum(content2);
        
        // Then: 校验和应该不同
        assertThat(checksum1).isNotEqualTo(checksum2);
    }
    
    // ==================== 属性 10: 导入成功后状态为DRAFT ====================
    
    @Property(tries = 100)
    void importedFunctionUnitShouldHaveDraftStatus(
            @ForAll("validFileNames") String fileName,
            @ForAll("validFileContents") String fileContent) {
        // Given: 有效的导入请求
        FunctionUnitImportRequest request = FunctionUnitImportRequest.builder()
                .fileName(fileName)
                .fileContent(fileContent)
                .version("1.0.0")
                .name("Test Function")
                .build();
        
        when(functionUnitRepository.existsByCodeAndVersion(any(), any())).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit unit = invocation.getArgument(0);
            return unit;
        });
        
        // When: 导入功能包
        ImportResult result = component.importFunctionPackage(request, "importer-001");
        
        // Then: 导入成功且状态为DRAFT
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFunctionUnit()).isNotNull();
        assertThat(result.getFunctionUnit().getStatus()).isEqualTo(FunctionUnitStatus.DRAFT);
    }

    // ==================== 数据提供者 ====================
    
    @Provide
    Arbitrary<String> validFileNames() {
        return Arbitraries.of(
                "function-package.zip",
                "my-workflow.fpkg",
                "process-v1.0.0.zip",
                "approval-flow.fpkg",
                "data-sync-module.zip"
        );
    }
    
    @Provide
    Arbitrary<String> invalidFileExtensions() {
        return Arbitraries.of(
                "package.txt",
                "workflow.exe",
                "process.jar",
                "module.tar.gz",
                "function.pdf"
        );
    }
    
    @Provide
    Arbitrary<String> validFileContents() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(10)
                .ofMaxLength(1000);
    }
    
    @Provide
    Arbitrary<String> validBpmnContents() {
        return Arbitraries.of(
                "<definitions><process id=\"p1\"></process></definitions>",
                "<?xml version=\"1.0\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><process id=\"main\"></process></definitions>",
                "<definitions><process id=\"approval\"><startEvent/><endEvent/></process></definitions>"
        );
    }
    
    @Provide
    Arbitrary<String> invalidBpmnContents() {
        return Arbitraries.of(
                "",
                "invalid xml",
                "<root></root>",
                "just some text",
                "<definitions></definitions>"
        );
    }
    
    @Provide
    Arbitrary<String> validDataTableDefinitions() {
        return Arbitraries.of(
                "CREATE TABLE users (id INT PRIMARY KEY)",
                "ALTER TABLE orders ADD COLUMN status VARCHAR(20)",
                "CREATE TABLE products (id SERIAL, name VARCHAR(100))",
                "" // 空字符串也是有效的（可选）
        );
    }
    
    @Provide
    Arbitrary<String> validFormConfigs() {
        return Arbitraries.of(
                "{\"fields\": []}",
                "[{\"type\": \"input\", \"name\": \"username\"}]",
                "{\"title\": \"Form\", \"fields\": [{\"type\": \"text\"}]}",
                "" // 空字符串也是有效的（可选）
        );
    }
    
    @Provide
    Arbitrary<String> invalidFormConfigs() {
        return Arbitraries.of(
                "not json",
                "<xml>invalid</xml>",
                "plain text config",
                "key=value"
        );
    }
    
    @Provide
    Arbitrary<String> validVersions() {
        return Arbitraries.of(
                "1.0.0",
                "2.1.3",
                "0.1.0",
                "10.20.30",
                "1.0.0-beta"
        );
    }
}
