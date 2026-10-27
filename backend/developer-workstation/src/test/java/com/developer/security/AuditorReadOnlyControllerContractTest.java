package com.developer.security;

import com.developer.controller.ExportImportController;
import com.developer.controller.FileUploadController;
import com.developer.controller.IconLibraryController;
import com.developer.controller.MemberController;
import com.developer.controller.SubTableViewController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditorReadOnlyControllerContractTest {

    @Test
    void exportImportWriteRequiresCreateAndUpdate() throws Exception {
        RequireDeveloperPermission annotation = annotationOn(ExportImportController.class, "importFunctionUnit");
        assertEquals(RequireDeveloperPermission.Mode.ALL, annotation.mode());
        assertEquals(Set.of("FUNCTION_UNIT_CREATE", "FUNCTION_UNIT_UPDATE"), Set.of(annotation.value()));
    }

    @Test
    void exportAndValidateRemainView() throws Exception {
        assertEquals(Set.of("FUNCTION_UNIT_VIEW"),
                Set.of(annotationOn(ExportImportController.class, "export").value()));
        assertEquals(Set.of("FUNCTION_UNIT_VIEW"),
                Set.of(annotationOn(ExportImportController.class, "validate").value()));
    }

    /**
     * Upload backs Portal form Attachment fields for every signed-in user (49b02721b), so it carries no developer
     * permission and checks authentication itself; deleting a stored file stays a designer's change.
     */
    @Test
    void fileDeletionRequiresUpdateWhileUploadIsForAnySignedInUser() throws Exception {
        Method upload = Arrays.stream(FileUploadController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("upload")).findFirst().orElseThrow();
        assertNull(upload.getAnnotation(RequireDeveloperPermission.class));
        assertEquals(Set.of("FUNCTION_UNIT_UPDATE"),
                Set.of(annotationOn(FileUploadController.class, "deleteFile").value()));
    }

    @Test
    void iconLibrarySplitsViewAndUpdate() throws Exception {
        assertEquals(Set.of("FUNCTION_UNIT_VIEW"),
                Set.of(annotationOn(IconLibraryController.class, "list").value()));
        assertEquals(Set.of("FUNCTION_UNIT_UPDATE"),
                Set.of(annotationOn(IconLibraryController.class, "upload").value()));
        assertEquals(Set.of("FUNCTION_UNIT_UPDATE"),
                Set.of(annotationOn(IconLibraryController.class, "delete").value()));
    }

    @Test
    void memberMutationsRequireAssignDevGroup() throws Exception {
        assertEquals(Set.of("FUNCTION_UNIT_VIEW"),
                Set.of(annotationOn(MemberController.class, "getMember").value()));
        assertEquals(Set.of("FUNCTION_UNIT_ASSIGN_DEV_GROUP"),
                Set.of(annotationOn(MemberController.class, "createMember").value()));
        assertEquals(Set.of("FUNCTION_UNIT_ASSIGN_DEV_GROUP"),
                Set.of(annotationOn(MemberController.class, "deleteMember").value()));
    }

    @Test
    void subTableViewOrCreateRequiresUpdate() throws Exception {
        assertEquals(Set.of("FUNCTION_UNIT_UPDATE"),
                Set.of(annotationOn(SubTableViewController.class, "getOrCreateViewConfig").value()));
        assertEquals(Set.of("FUNCTION_UNIT_VIEW"),
                Set.of(annotationOn(SubTableViewController.class, "getViewConfig").value()));
    }

    private static RequireDeveloperPermission annotationOn(Class<?> type, String methodName) throws Exception {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + methodName + " on " + type.getName()));
        RequireDeveloperPermission annotation = method.getAnnotation(RequireDeveloperPermission.class);
        assertNotNull(annotation, methodName + " must declare @RequireDeveloperPermission");
        assertTrue(annotation.value().length > 0);
        return annotation;
    }
}
