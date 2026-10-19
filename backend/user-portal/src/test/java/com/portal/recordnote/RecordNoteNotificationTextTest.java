package com.portal.recordnote;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecordNoteNotificationTextTest {
    final ResourceBundleMessageSource messages=new ResourceBundleMessageSource();
    RecordNoteNotificationTextTest() { messages.setBasename("i18n/messages"); messages.setDefaultEncoding("UTF-8"); }
    String content(String name,String scope,Map<String,Object> policy,Locale locale) {
        var request=new RecordNoteCatalogRepository.Request("REQ-001","catalog",name,Map.of(),List.of(),List.of());
        return RecordNoteNotificationText.content(request,Map.of("target_type",scope,"target_id","TX-B","attachment_count",2),policy,locale,messages);
    }
    @Test void englishTransactionNoticeSeparatesRequestLocationAndActualUploadTask() {
        assertEquals("Credit Card Reimbursement (REQ-001)\n2 attachment(s) added to Transaction Notes · TX-B.\nUploaded from: Credit Card Transaction",
                content("Credit Card Reimbursement","RECORD",Map.of("uploadSource",Map.of("kind","TASK","taskName","Credit Card Transaction")),Locale.ENGLISH));
    }
    @Test void sharedCaseNoteUploadedFromTaskDoesNotBecomeATransactionNotice() {
        String text=content("Expense","TABLE",Map.of("uploadSource",Map.of("kind","TASK","taskName","Upload Documents")),Locale.ENGLISH);
        assertTrue(text.contains("Case Notes")); assertFalse(text.contains("TX-B"));
        assertTrue(text.endsWith("Uploaded from: Upload Documents"));
    }
    @Test void chineseAndTraditionalChineseAuditNoticesUseTheRecipientLanguage() {
        String cn=content("信用卡报销申请","RECORD",Map.of("uploadSource",Map.of("kind","AUDIT")),Locale.SIMPLIFIED_CHINESE);
        assertEquals("信用卡报销申请（REQ-001）\nTransaction Notes · TX-B新增 2 个附件。\n上传来源：审核",cn);
        String tw=content("信用卡報銷申請","TABLE",Map.of("uploadSource",Map.of("kind","MY_REQUEST")),Locale.TRADITIONAL_CHINESE);
        assertTrue(tw.startsWith("信用卡報銷申請（REQ-001）\n")); assertTrue(tw.endsWith("上傳來源：我的申請"));
    }
    @Test void legacySubmissionsDoNotGuessSourceFromRecipientNodes() {
        String text=content("Expense","TABLE",Map.of("targetNodeKeys",List.of("recipient-task")),Locale.ENGLISH);
        assertFalse(text.contains("Uploaded from")); assertFalse(text.contains("recipient-task"));
    }
    @Test void titlesArePlainTextAndCannotInjectExtraNotificationLines() {
        String text=content("<img src=x onerror=alert(1)><script>alert(1)</script>Request\nname","TABLE",Map.of(),Locale.ENGLISH);
        assertEquals("Request name (REQ-001)\n2 attachment(s) added to Case Notes.",text);
        assertTrue(content(null,"TABLE",Map.of(),Locale.ENGLISH).startsWith("Request (REQ-001)"));
    }
    @Test void unnamedTasksRemainIdentifiableAndInvalidSourceIsExplicit() {
        assertTrue(content("Expense","TABLE",Map.of("uploadSource",Map.of("kind","TASK","taskId","task-1")),Locale.ENGLISH)
                .endsWith("Uploaded from: Task (task-1)"));
        assertThrows(IllegalStateException.class,()->content("Expense","TABLE",Map.of("uploadSource",Map.of("kind","invented")),Locale.ENGLISH));
    }
}
