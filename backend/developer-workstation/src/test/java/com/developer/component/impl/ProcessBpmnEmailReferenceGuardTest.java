package com.developer.component.impl;

import com.developer.exception.DeveloperBusinessException;
import com.developer.security.WorkspaceRouteOwnership;
import com.platform.common.i18n.MessageSourceConfig;
import com.platform.common.i18n.impl.I18nServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A BPMN send task may only mail through its own unit's email connection and template (platform access model §6.1,
 * §6.2): the ids come from the saved XML, i.e. from the request body (out-of-scope list row 37).
 */
class ProcessBpmnEmailReferenceGuardTest {

    private static final long UNIT = 7L;
    private static final long OTHER_UNIT = 8L;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    // The bundles the application loads (spring.messages.basename in application.yml).
    private final ProcessBpmnEmailReferenceGuard guard = new ProcessBpmnEmailReferenceGuard(
            new WorkspaceRouteOwnership(jdbc),
            new I18nServiceImpl(new MessageSourceConfig().messageSource("i18n/messages,i18n/dw-messages", "UTF-8")));

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void ownConnectionAndTemplatePass() {
        owner("conn-own", UNIT);
        owner(31L, UNIT);

        assertThatCode(() -> guard.requireInUnit(UNIT, process(sendTask("Notify", "conn-own", "31"))))
                .doesNotThrowAnyException();
    }

    @Test
    void anotherUnitsConnectionIsRefusedNamingTheTask() {
        owner("conn-foreign", OTHER_UNIT);
        owner(31L, UNIT);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        DeveloperBusinessException ex = refusal(process(sendTask("Notify", "conn-foreign", "31")));

        assertThat(ex.getErrorCode()).isEqualTo("SEND_TASK_EMAIL_REFERENCE_NOT_IN_UNIT");
        assertThat(ex.getMessage()).isEqualTo("Send task Notify uses email connection conn-foreign, which is not one"
                + " of this function unit's connections. Choose one of this unit's connections and save again.");
    }

    @Test
    void aLegacyNumericConnectionIdOfAnotherUnitIsRefused() {
        owner(44L, OTHER_UNIT, "dw_email_connections WHERE id");
        owner(31L, UNIT);

        assertThat(refusal(process(sendTask("Notify", "44", "31"))).getErrorCode())
                .isEqualTo("SEND_TASK_EMAIL_REFERENCE_NOT_IN_UNIT");
    }

    @Test
    void anotherUnitsTemplateIsRefusedInTheUsersLanguage() {
        owner("conn-own", UNIT);
        owner(32L, OTHER_UNIT);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        DeveloperBusinessException ex = refusal(process(sendTask("Notify", "conn-own", "32")));

        assertThat(ex.getMessage()).isEqualTo("发送任务 Notify 使用的邮件模板 32 不属于本功能单元，请改选本功能单元的模板后再保存。");
    }

    @Test
    void aTemplateThatDoesNotExistIsRefused() {
        owner("conn-own", UNIT);

        LocaleContextHolder.setLocale(Locale.TRADITIONAL_CHINESE);
        assertThat(refusal(process(sendTask("Notify", "conn-own", "999"))).getMessage())
                .isEqualTo("傳送任務 Notify 使用的郵件範本 999 不屬於本功能單元，請改選本功能單元的範本後再儲存。");
    }

    @Test
    void everySendTaskIsChecked() {
        owner("conn-own", UNIT);
        owner(31L, UNIT);
        owner("conn-foreign", OTHER_UNIT);

        String xml = process(sendTask("First", "conn-own", "31") + sendTask("Second", "conn-foreign", "31"));

        assertThat(refusal(xml).getMessage()).contains("Second").contains("conn-foreign");
    }

    @Test
    void aSendTaskWithoutConnectionOrTemplateYetReferencesNothing() {
        String xml = process(sendTask("Draft", "", "") + "<bpmn:sendTask id=\"Empty\" name=\"Empty\"/>");

        assertThatCode(() -> guard.requireInUnit(UNIT, xml)).doesNotThrowAnyException();
    }

    // The engine reads the properties with the Flowable parser (BpmnExtensionUtils.getExtensionProperty): any
    // namespace, self-closing or not. Each form below reaches the engine as connectionId = conn-foreign.

    @Test
    void aPropertyWrittenWithAClosingTagIsChecked() {
        owner("conn-foreign", OTHER_UNIT);
        owner(31L, UNIT);
        String xml = process("<bpmn:sendTask id=\"Notify\"><bpmn:extensionElements><custom:properties>"
                + "<custom:property name=\"connectionId\" value=\"conn-foreign\"></custom:property>"
                + "<custom:property name=\"emailTemplateId\" value=\"31\"></custom:property>"
                + "</custom:properties></bpmn:extensionElements></bpmn:sendTask>");

        assertThat(refusal(xml).getMessage()).contains("Notify").contains("conn-foreign");
    }

    @Test
    void aPropertyInADefaultNamespaceIsChecked() {
        owner("conn-foreign", OTHER_UNIT);
        owner(31L, UNIT);
        String xml = process("<bpmn:sendTask id=\"Notify\"><bpmn:extensionElements>"
                + "<properties xmlns=\"http://flowable.org/bpmn\">"
                + "<property name=\"connectionId\" value=\"conn-foreign\"/>"
                + "<property name=\"emailTemplateId\" value=\"31\"/>"
                + "</properties></bpmn:extensionElements></bpmn:sendTask>");

        assertThat(refusal(xml).getMessage()).contains("Notify").contains("conn-foreign");
    }

    @Test
    void aSendTaskInADefaultBpmnNamespaceInsideASubProcessIsChecked() {
        owner("conn-foreign", OTHER_UNIT);
        owner(31L, UNIT);
        String xml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:custom=\"http://custom.bpmn.io/schema\"><process id=\"p\"><subProcess id=\"sp\">"
                + "<sendTask id=\"Nested\"><extensionElements><custom:properties>"
                + "<custom:property name=\"connectionId\" value=\"conn-foreign\"/>"
                + "<custom:property name=\"emailTemplateId\" value=\"31\"/>"
                + "</custom:properties></extensionElements></sendTask></subProcess></process></definitions>";

        assertThat(refusal(xml).getMessage()).contains("Nested").contains("conn-foreign");
    }

    @Test
    void aValuesElementIsReadLikeTheEngineReadsIt() {
        owner("conn-foreign", OTHER_UNIT);
        owner(31L, UNIT);
        String xml = process("<bpmn:sendTask id=\"Notify\"><bpmn:extensionElements><custom:properties>"
                + "<custom:values name=\"connectionId\" value=\"conn-foreign\"/>"
                + "<custom:property name=\"emailTemplateId\" value=\"31\"/>"
                + "</custom:properties></bpmn:extensionElements></bpmn:sendTask>");

        assertThat(refusal(xml).getMessage()).contains("conn-foreign");
    }

    @Test
    void theFirstPropertyOfANameIsTheOneTheEngineUses() {
        owner("conn-foreign", OTHER_UNIT);
        owner("conn-own", UNIT);
        owner(31L, UNIT);
        String xml = process("<bpmn:sendTask id=\"Notify\"><bpmn:extensionElements><custom:properties>"
                + "<custom:property name=\"connectionId\" value=\"conn-foreign\"/>"
                + "<custom:property name=\"connectionId\" value=\"conn-own\"/>"
                + "<custom:property name=\"emailTemplateId\" value=\"31\"/>"
                + "</custom:properties></bpmn:extensionElements></bpmn:sendTask>");

        assertThat(refusal(xml).getMessage()).contains("conn-foreign");
    }

    @Test
    void propertiesOutsideTheSendTasksExtensionElementsAreNotItsProperties() {
        owner(31L, UNIT);
        // A documentation text that looks like a property is not one; nor is a property of another element.
        String xml = process("<bpmn:sendTask id=\"Notify\"><bpmn:documentation>"
                + "&lt;custom:property name=\"connectionId\" value=\"conn-foreign\"/&gt;</bpmn:documentation>"
                + "<bpmn:extensionElements><custom:properties>"
                + "<custom:property name=\"emailTemplateId\" value=\"31\"/>"
                + "</custom:properties></bpmn:extensionElements></bpmn:sendTask>");

        assertThatCode(() -> guard.requireInUnit(UNIT, xml)).doesNotThrowAnyException();
    }

    @Test
    void xmlThatDoesNotParseIsRefused() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        DeveloperBusinessException ex = refusal(process(sendTask("Notify", "conn-own", "31")).replace(
                "</bpmn:process>", ""));

        assertThat(ex.getErrorCode()).isEqualTo("BPMN_XML_NOT_WELL_FORMED");
        assertThat(ex.getMessage()).startsWith("The process cannot be saved: its XML is not well-formed (line 1,");
    }

    @Test
    void aDoctypeIsRefusedSoNoEntityIsEverResolved() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + process(sendTask("Notify", "&x;", "31"));

        assertThat(refusal(xml).getErrorCode()).isEqualTo("BPMN_XML_NOT_WELL_FORMED");
    }

    private DeveloperBusinessException refusal(String xml) {
        return catchThrowableOfType(() -> guard.requireInUnit(UNIT, xml), DeveloperBusinessException.class);
    }

    private void owner(String connectionUid, long unit) {
        when(jdbc.queryForList(eq("SELECT function_unit_id FROM dw_email_connections WHERE connection_uid = ?"),
                eq(Long.class), eq(connectionUid))).thenReturn(List.of(unit));
    }

    private void owner(long templateId, long unit) {
        when(jdbc.queryForList(eq("SELECT function_unit_id FROM dw_email_templates WHERE id = ?"),
                eq(Long.class), eq(templateId))).thenReturn(List.of(unit));
    }

    private void owner(long connectionId, long unit, String tableAndKey) {
        when(jdbc.queryForList(eq("SELECT function_unit_id FROM " + tableAndKey + " = ?"),
                eq(Long.class), eq(connectionId))).thenReturn(List.of(unit));
    }

    private static String sendTask(String id, String connectionId, String templateId) {
        return "<bpmn:sendTask id=\"" + id + "\" name=\"" + id + "\">"
                + "<bpmn:extensionElements><custom:properties>"
                + "<custom:property name=\"sendMode\" value=\"email\" />"
                + "<custom:property name=\"connectionId\" value=\"" + connectionId + "\" />"
                + "<custom:property name=\"emailTo\" value=\"a@example.test\" />"
                + "<custom:property name=\"emailTemplateId\" value=\"" + templateId + "\" />"
                + "</custom:properties></bpmn:extensionElements>"
                + "</bpmn:sendTask>";
    }

    private static String process(String body) {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:custom=\"http://custom.bpmn.io/schema\"><bpmn:process id=\"p\">"
                + body + "</bpmn:process></bpmn:definitions>";
    }
}
