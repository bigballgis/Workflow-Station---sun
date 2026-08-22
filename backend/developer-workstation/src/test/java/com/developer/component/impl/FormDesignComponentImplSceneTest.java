package com.developer.component.impl;

import com.developer.dto.FormDefinitionRequest;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.enums.FormScene;
import com.developer.enums.FormType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.SubTableViewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The designer saves a form without naming a scene — it is not something the form
 * editor changes. An omitted scene must therefore leave the stored one alone; treating
 * it as "TASK" silently moved My Requests designs back into the To Do scene on the next
 * ordinary save, and the only visible symptom was the form reappearing under the wrong
 * tab some time later.
 */
@ExtendWith(MockitoExtension.class)
class FormDesignComponentImplSceneTest {

    @Mock private FormTableBindingRestorer formTableBindingRestorer;
    @Mock private FormConfigJsonTableProvisioner formConfigJsonTableProvisioner;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormTableBindingRepository formTableBindingRepository;
    @Mock private SubTableViewConfigRepository subTableViewConfigRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private I18nService i18nService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SubTableViewService subTableViewService;
    @Mock private OwnerFieldFormReconciler ownerFieldFormReconciler;

    @InjectMocks private FormDesignComponentImpl component;

    private FormDefinition storedForm(FormScene scene, FormType type) {
        FunctionUnit fu = FunctionUnit.builder().id(50005L).build();
        FormDefinition form = FormDefinition.builder()
                .id(50601L)
                .functionUnit(fu)
                .formName("Assign Task (My Request)")
                .formType(type)
                .scene(scene)
                .configJson(new HashMap<>())
                .build();
        // Non-empty so getById does not take its binding-repair detour.
        form.getTableBindings().add(new com.developer.entity.FormTableBinding());
        return form;
    }

    private FormDefinitionRequest requestWithScene(FormScene scene, FormType type) {
        return FormDefinitionRequest.builder()
                .formName("Assign Task (My Request)")
                .formType(type)
                .scene(scene)
                .configJson(new HashMap<>())
                .build();
    }

    private void stubCommon() {
        lenient().when(formDefinitionRepository.existsByFunctionUnitIdAndFormNameAndIdNot(
                anyLong(), anyString(), anyLong())).thenReturn(false);
        lenient().when(formDefinitionRepository.save(any(FormDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void updateWithoutSceneKeepsTheStoredScene() {
        FormDefinition stored = storedForm(FormScene.REQUEST, FormType.TASK);
        when(formDefinitionRepository.findByIdWithBindings(50601L)).thenReturn(Optional.of(stored));
        stubCommon();

        component.update(50601L, requestWithScene(null, FormType.TASK));

        assertThat(stored.getScene()).isEqualTo(FormScene.REQUEST);
    }

    @Test
    void updateWithAnExplicitSceneMovesTheForm() {
        FormDefinition stored = storedForm(FormScene.REQUEST, FormType.TASK);
        when(formDefinitionRepository.findByIdWithBindings(50601L)).thenReturn(Optional.of(stored));
        stubCommon();

        component.update(50601L, requestWithScene(FormScene.TASK, FormType.TASK));

        assertThat(stored.getScene()).isEqualTo(FormScene.TASK);
    }

    /** Forms predating the scene column carry null; those really are To Do designs. */
    @Test
    void updateWithoutSceneOnALegacyFormSettlesOnTask() {
        FormDefinition stored = storedForm(null, FormType.TASK);
        when(formDefinitionRepository.findByIdWithBindings(50601L)).thenReturn(Optional.of(stored));
        stubCommon();

        component.update(50601L, requestWithScene(null, FormType.TASK));

        assertThat(stored.getScene()).isEqualTo(FormScene.TASK);
    }

    // ---------- fieldPermissions persistence ----------
    //
    // The PUT /forms/{id} endpoint silently dropped fieldPermissions until this DTO field and
    // this write were added — the Form Designer's field-permission panel (main-table AND the
    // sub-table groups it now also renders) always sent it correctly, but update() never copied
    // it onto the entity, so a 200 response never actually persisted anything.

    @Test
    void updateWithFieldPermissionsPersistsThem() {
        FormDefinition stored = storedForm(FormScene.TASK, FormType.TASK);
        when(formDefinitionRepository.findByIdWithBindings(50601L)).thenReturn(Optional.of(stored));
        stubCommon();

        FormDefinitionRequest request = requestWithScene(FormScene.TASK, FormType.TASK);
        request.setFieldPermissions(Map.of(
                "id", "READONLY",
                "50544:bu_code", "READONLY",
                "50544:role_code", "READONLY"));

        component.update(50601L, request);

        assertThat(stored.getFieldPermissions()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "READONLY",
                "50544:bu_code", "READONLY",
                "50544:role_code", "READONLY"));
    }

    /** Omitted (null) fieldPermissions means "not sent by this caller" — leave it as-is. */
    @Test
    void updateWithoutFieldPermissionsLeavesTheStoredValueUnchanged() {
        FormDefinition stored = storedForm(FormScene.TASK, FormType.TASK);
        stored.setFieldPermissions(new HashMap<>(Map.of("id", "READONLY")));
        when(formDefinitionRepository.findByIdWithBindings(50601L)).thenReturn(Optional.of(stored));
        stubCommon();

        component.update(50601L, requestWithScene(FormScene.TASK, FormType.TASK));

        assertThat(stored.getFieldPermissions()).containsExactly(Map.entry("id", "READONLY"));
    }

    // ---------- Scene pairing on create ----------

    private FormDefinitionRequest createRequest(String name, FormType type, boolean bothScenes) {
        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>());
        return FormDefinitionRequest.builder()
                .formName(name)
                .formType(type)
                .configJson(configJson)
                .createBothScenes(bothScenes)
                .build();
    }

    private List<FormDefinition> captureCreatedForms() {
        List<FormDefinition> saved = new ArrayList<>();
        lenient().when(functionUnitRepository.findById(50005L))
                .thenReturn(Optional.of(FunctionUnit.builder().id(50005L).build()));
        lenient().when(formDefinitionRepository.existsByFunctionUnitIdAndFormName(anyLong(), anyString()))
                .thenReturn(false);
        lenient().when(formDefinitionRepository.countByFunctionUnitIdAndFormTypeAndScene(
                anyLong(), any(FormType.class), any(FormScene.class))).thenReturn(0L);
        lenient().when(formDefinitionRepository.save(any(FormDefinition.class)))
                .thenAnswer(inv -> {
                    saved.add(inv.getArgument(0));
                    return inv.getArgument(0);
                });
        return saved;
    }

    @Test
    void createBothScenesWritesOneFormPerSceneAndReturnsTheToDoOne() {
        List<FormDefinition> saved = captureCreatedForms();

        FormDefinition returned = component.create(50005L, createRequest("Assign Task", FormType.TASK, true));

        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(FormDefinition::getScene)
                .containsExactly(FormScene.TASK, FormScene.REQUEST);
        assertThat(saved).extracting(FormDefinition::getFormName)
                .containsExactly("Assign Task", "Assign Task (My Request)");
        // The designer lands on the To Do design.
        assertThat(returned.getScene()).isEqualTo(FormScene.TASK);
        assertThat(returned.getFormName()).isEqualTo("Assign Task");
    }

    /** Each scene is designed from scratch, so neither row may inherit the other's layout. */
    @Test
    void createBothScenesStartsBothDesignsEmpty() {
        List<FormDefinition> saved = captureCreatedForms();

        component.create(50005L, createRequest("Assign Task", FormType.TASK, true));

        assertThat(saved).allSatisfy(form ->
                assertThat((List<?>) form.getConfigJson().get("rule")).isEmpty());
    }

    /** ACTION and DETAIL forms exist in one scene only, so the flag must not fan them out. */
    @Test
    void createBothScenesIsIgnoredForNonStepFormTypes() {
        List<FormDefinition> saved = captureCreatedForms();

        component.create(50005L, createRequest("Popup", FormType.ACTION, true));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getScene()).isEqualTo(FormScene.TASK);
    }

    /** A paired PROCESS create needs both slots, so an occupied one must fail the whole call. */
    @Test
    void createBothScenesFailsWhenEitherProcessSlotIsTaken() {
        captureCreatedForms();
        when(formDefinitionRepository.countByFunctionUnitIdAndFormTypeAndScene(
                50005L, FormType.PROCESS, FormScene.REQUEST)).thenReturn(1L);

        assertThatThrownBy(() -> component.create(50005L, createRequest("Main", FormType.PROCESS, true)))
                .isInstanceOf(DeveloperBusinessException.class);
    }

    /** Reported before anything is written, so no half pair is left behind. */
    @Test
    void createBothScenesFailsWhenTheSuffixedNameIsTaken() {
        List<FormDefinition> saved = captureCreatedForms();
        when(formDefinitionRepository.existsByFunctionUnitIdAndFormName(
                50005L, "Assign Task (My Request)")).thenReturn(true);

        assertThatThrownBy(() -> component.create(50005L, createRequest("Assign Task", FormType.TASK, true)))
                .isInstanceOf(DeveloperBusinessException.class);
        assertThat(saved).isEmpty();
    }

    // ---------- ACTION forms are To Do only ----------

    /**
     * A My Requests action form could never be opened: action buttons come from BPMN user tasks and
     * My Requests has none. Rejected loudly rather than saved and silently never rendered.
     */
    @Test
    void createRejectsAnActionFormInTheRequestScene() {
        captureCreatedForms();
        FormDefinitionRequest request = FormDefinitionRequest.builder()
                .formName("Popup")
                .formType(FormType.ACTION)
                .scene(FormScene.REQUEST)
                .configJson(new HashMap<>())
                .build();

        assertThatThrownBy(() -> component.create(50005L, request))
                .isInstanceOf(DeveloperBusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_ACTION_FORM_SCENE");
    }

    @Test
    void updateRejectsMovingAnActionFormIntoTheRequestScene() {
        FormDefinition stored = storedForm(FormScene.TASK, FormType.ACTION);
        when(formDefinitionRepository.findByIdWithBindings(50601L)).thenReturn(Optional.of(stored));
        stubCommon();

        assertThatThrownBy(() -> component.update(50601L, requestWithScene(FormScene.REQUEST, FormType.ACTION)))
                .isInstanceOf(DeveloperBusinessException.class);
        assertThat(stored.getScene()).isEqualTo(FormScene.TASK);
    }

    @Test
    void actionFormsRemainAllowedInTheToDoScene() {
        List<FormDefinition> saved = captureCreatedForms();

        component.create(50005L, FormDefinitionRequest.builder()
                .formName("Popup")
                .formType(FormType.ACTION)
                .scene(FormScene.TASK)
                .configJson(new HashMap<>())
                .build());

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getScene()).isEqualTo(FormScene.TASK);
    }
}
