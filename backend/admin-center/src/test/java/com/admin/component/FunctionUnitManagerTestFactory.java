package com.admin.component;

import com.admin.repository.ActionDefinitionRepository;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.service.FunctionUnitAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Test factory wiring the {@link FunctionUnitManagerComponent} facade with its real
 * collaborator components from the same low-level dependencies (typically mocks).
 * Keeps the parameter list of the pre-split 11-arg constructor so existing
 * property tests only swap the constructor call.
 */
public final class FunctionUnitManagerTestFactory {

    private FunctionUnitManagerTestFactory() {
    }

    public static FunctionUnitManagerComponent createManager(
            FunctionUnitRepository functionUnitRepository,
            FunctionUnitDependencyRepository dependencyRepository,
            FunctionUnitContentRepository contentRepository,
            FunctionUnitAccessRepository accessRepository,
            FunctionUnitValidationComponent validationComponent,
            FunctionUnitPackageParser packageParser,
            ActionDefinitionRepository actionDefinitionRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            I18nService i18nService) {

        FunctionUnitLookup lookup = new FunctionUnitLookup(functionUnitRepository, i18nService);
        FunctionUnitVersionComponent versionComponent = new FunctionUnitVersionComponent(
                functionUnitRepository, dependencyRepository, contentRepository, lookup, i18nService);
        org.springframework.test.util.ReflectionTestUtils.setField(versionComponent, "versionActions", actionDefinitionRepository);
        RelationTableDefinitionRepository relationTableDefinitionRepository =
                Mockito.mock(RelationTableDefinitionRepository.class);
        RelationTableStructureImporter relationTableStructureImporter = new RelationTableStructureImporter(
                relationTableDefinitionRepository,
                new RelationTableFieldMapper(relationTableDefinitionRepository),
                objectMapper);
        ImportViewAccessValidator viewValidator = Mockito.mock(ImportViewAccessValidator.class);
        Mockito.lenient().when(viewValidator.remapAndValidate(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        FunctionUnitImportComponent importComponent = new FunctionUnitImportComponent(
                functionUnitRepository, dependencyRepository, contentRepository, accessRepository,
                Mockito.mock(FunctionUnitAccessService.class),
                Mockito.mock(com.admin.service.FunctionUnitAuditAccessService.class),
                packageParser, actionDefinitionRepository, versionComponent, relationTableStructureImporter,
                objectMapper, i18nService,
                Mockito.mock(EmailConnectionSyncComponent.class),
                Mockito.mock(EmailMonitorSyncComponent.class),
                Mockito.mock(ImportBpmnStructureValidator.class),
                viewValidator);
        FormTableBindingLoader bindingLoader = new FormTableBindingLoader(jdbcTemplate, objectMapper);
        FunctionUnitContentComponent contentComponent = new FunctionUnitContentComponent(
                contentRepository, jdbcTemplate, lookup, bindingLoader, objectMapper);
        FunctionUnitLifecycleComponent lifecycleComponent = new FunctionUnitLifecycleComponent(
                functionUnitRepository, dependencyRepository, contentRepository, accessRepository,
                Mockito.mock(com.admin.repository.FunctionUnitAuditAccessRepository.class),
                validationComponent, versionComponent, lookup, i18nService);
        var snapshots = Mockito.mock(com.platform.common.functionunit.CatalogSnapshotReader.class);
        org.springframework.test.util.ReflectionTestUtils.setField(contentComponent, "catalogSnapshots", snapshots);
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycleComponent, "versionJdbc", jdbcTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycleComponent, "versionSnapshots", snapshots);
        // Existing lifecycle properties model fully deployed releases without historical instances.
        // Readiness/refusal boundaries are exercised separately by runtime version verification.
        Mockito.lenient().when(jdbcTemplate.queryForObject(
                Mockito.startsWith("SELECT COUNT(*) FROM sys_function_unit_contents"), Mockito.eq(Long.class), Mockito.anyString()))
                .thenReturn(1L);
        Mockito.lenient().when(jdbcTemplate.queryForObject(
                Mockito.startsWith("SELECT COUNT(*) FROM up_process_instance"), Mockito.eq(Long.class), Mockito.anyString()))
                .thenReturn(0L);
        PortalRuntimePurgeClient purgeClient = new PortalRuntimePurgeClient(restTemplate);

        return new FunctionUnitManagerComponent(
                functionUnitRepository, lookup, importComponent, versionComponent,
                contentComponent, lifecycleComponent, purgeClient);
    }
}
