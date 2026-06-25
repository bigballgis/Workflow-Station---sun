package com.admin.component;

import com.admin.repository.ActionDefinitionRepository;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationTableDefinitionRepository;
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
        RelationTableStructureImporter relationTableStructureImporter = new RelationTableStructureImporter(
                Mockito.mock(RelationTableDefinitionRepository.class), objectMapper);
        FunctionUnitImportComponent importComponent = new FunctionUnitImportComponent(
                functionUnitRepository, dependencyRepository, contentRepository, accessRepository,
                packageParser, actionDefinitionRepository, versionComponent, relationTableStructureImporter,
                objectMapper, i18nService);
        FormTableBindingLoader bindingLoader = new FormTableBindingLoader(jdbcTemplate, objectMapper);
        FunctionUnitContentComponent contentComponent = new FunctionUnitContentComponent(
                contentRepository, jdbcTemplate, lookup, bindingLoader);
        FunctionUnitLifecycleComponent lifecycleComponent = new FunctionUnitLifecycleComponent(
                functionUnitRepository, dependencyRepository, contentRepository, accessRepository,
                validationComponent, versionComponent, lookup, i18nService);
        PortalRuntimePurgeClient purgeClient = new PortalRuntimePurgeClient(restTemplate);

        return new FunctionUnitManagerComponent(
                functionUnitRepository, lookup, importComponent, versionComponent,
                contentComponent, lifecycleComponent, purgeClient);
    }
}
