package com.developer.service;

import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnitAccess;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitAccessRepository;
import com.developer.repository.FunctionUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PermissionService
 * 
 * Requirements: 4.1, 4.3, 4.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService Tests")
class PermissionServiceTest {
    
    @Mock
    private FunctionUnitAccessRepository accessRepository;
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @InjectMocks
    private PermissionService permissionService;
    
    @Captor
    private ArgumentCaptor<List<FunctionUnitAccess>> permissionListCaptor;
    
    private FunctionUnit sourceVersion;
    private FunctionUnit targetVersion;
    private List<FunctionUnitAccess> sourcePermissions;
    
    @BeforeEach
    void setUp() {
        sourceVersion = FunctionUnit.builder()
                .id(1L)
                .name("test-function-unit")
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        targetVersion = FunctionUnit.builder()
                .id(2L)
                .name("test-function-unit")
                .version("1.1.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        sourcePermissions = Arrays.asList(
                FunctionUnitAccess.builder()
                        .id(1L)
                        .functionUnit(sourceVersion)
                        .accessType("USER")
                        .targetType("ROLE")
                        .targetId("role-1")
                        .build(),
                FunctionUnitAccess.builder()
                        .id(2L)
                        .functionUnit(sourceVersion)
                        .accessType("USER")
                        .targetType("ROLE")
                        .targetId("role-2")
                        .build()
        );
    }
    
    @Nested
    @DisplayName("copyPermissions() Tests")
    class CopyPermissionsTests {
        
        @Test
        @DisplayName("Should successfully copy permissions from source to target version")
        void shouldCopyPermissionsSuccessfully() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
            when(accessRepository.findByFunctionUnitId(1L)).thenReturn(sourcePermissions);
            when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            int copiedCount = permissionService.copyPermissions(1L, 2L);
            
            // Then
            assertThat(copiedCount).isEqualTo(2);
            
            verify(accessRepository).saveAll(permissionListCaptor.capture());
            List<FunctionUnitAccess> savedPermissions = permissionListCaptor.getValue();
            
            assertThat(savedPermissions).hasSize(2);
            assertThat(savedPermissions).allMatch(p -> p.getFunctionUnit().equals(targetVersion));
            assertThat(savedPermissions).extracting(FunctionUnitAccess::getAccessType)
                    .containsExactly("USER", "USER");
            assertThat(savedPermissions).extracting(FunctionUnitAccess::getTargetType)
                    .containsExactly("ROLE", "ROLE");
            assertThat(savedPermissions).extracting(FunctionUnitAccess::getTargetId)
                    .containsExactly("role-1", "role-2");
        }
        
        @Test
        @DisplayName("Should handle empty source permissions")
        void shouldHandleEmptySourcePermissions() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
            when(accessRepository.findByFunctionUnitId(1L)).thenReturn(Collections.emptyList());
            when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            int copiedCount = permissionService.copyPermissions(1L, 2L);
            
            // Then
            assertThat(copiedCount).isZero();
            
            verify(accessRepository).saveAll(permissionListCaptor.capture());
            assertThat(permissionListCaptor.getValue()).isEmpty();
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException when source version not found")
        void shouldThrowExceptionWhenSourceVersionNotFound() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.empty());
            
            // When/Then
            assertThatThrownBy(() -> permissionService.copyPermissions(1L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("1");
            
            verify(accessRepository, never()).findByFunctionUnitId(any());
            verify(accessRepository, never()).saveAll(anyList());
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException when target version not found")
        void shouldThrowExceptionWhenTargetVersionNotFound() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.empty());
            
            // When/Then
            assertThatThrownBy(() -> permissionService.copyPermissions(1L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("2");
            
            verify(accessRepository, never()).findByFunctionUnitId(any());
            verify(accessRepository, never()).saveAll(anyList());
        }
        
        @Test
        @DisplayName("Should preserve all permission attributes during copy")
        void shouldPreserveAllPermissionAttributes() {
            // Given
            FunctionUnitAccess complexPermission = FunctionUnitAccess.builder()
                    .id(3L)
                    .functionUnit(sourceVersion)
                    .accessType("DEVELOPER")
                    .targetType("VIRTUAL_GROUP")
                    .targetId("vg-123")
                    .build();
            
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
            when(accessRepository.findByFunctionUnitId(1L))
                    .thenReturn(Collections.singletonList(complexPermission));
            when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            permissionService.copyPermissions(1L, 2L);
            
            // Then
            verify(accessRepository).saveAll(permissionListCaptor.capture());
            FunctionUnitAccess copiedPermission = permissionListCaptor.getValue().get(0);
            
            assertThat(copiedPermission.getFunctionUnit()).isEqualTo(targetVersion);
            assertThat(copiedPermission.getAccessType()).isEqualTo("DEVELOPER");
            assertThat(copiedPermission.getTargetType()).isEqualTo("VIRTUAL_GROUP");
            assertThat(copiedPermission.getTargetId()).isEqualTo("vg-123");
        }
    }
    
    @Nested
    @DisplayName("getPermissions() Tests")
    class GetPermissionsTests {
        
        @Test
        @DisplayName("Should retrieve all permissions for a version")
        void shouldRetrieveAllPermissions() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            when(accessRepository.findByFunctionUnitId(1L)).thenReturn(sourcePermissions);
            
            // When
            List<FunctionUnitAccess> permissions = permissionService.getPermissions(1L);
            
            // Then
            assertThat(permissions).hasSize(2);
            assertThat(permissions).isEqualTo(sourcePermissions);
        }
        
        @Test
        @DisplayName("Should return empty list when version has no permissions")
        void shouldReturnEmptyListWhenNoPermissions() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            when(accessRepository.findByFunctionUnitId(1L)).thenReturn(Collections.emptyList());
            
            // When
            List<FunctionUnitAccess> permissions = permissionService.getPermissions(1L);
            
            // Then
            assertThat(permissions).isEmpty();
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException when version not found")
        void shouldThrowExceptionWhenVersionNotFound() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.empty());
            
            // When/Then
            assertThatThrownBy(() -> permissionService.getPermissions(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("1");
            
            verify(accessRepository, never()).findByFunctionUnitId(any());
        }
    }
    
    @Nested
    @DisplayName("updatePermissions() Tests")
    class UpdatePermissionsTests {
        
        @Test
        @DisplayName("Should update permissions for active version")
        void shouldUpdatePermissionsForActiveVersion() {
            // Given
            List<FunctionUnitAccess> newPermissions = Arrays.asList(
                    FunctionUnitAccess.builder()
                            .accessType("USER")
                            .targetType("ROLE")
                            .targetId("role-3")
                            .build(),
                    FunctionUnitAccess.builder()
                            .accessType("USER")
                            .targetType("ROLE")
                            .targetId("role-4")
                            .build(),
                    FunctionUnitAccess.builder()
                            .accessType("USER")
                            .targetType("ROLE")
                            .targetId("role-5")
                            .build()
            );
            
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
            when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            List<FunctionUnitAccess> updatedPermissions = permissionService.updatePermissions(2L, newPermissions);
            
            // Then
            assertThat(updatedPermissions).hasSize(3);
            assertThat(updatedPermissions).allMatch(p -> p.getFunctionUnit().equals(targetVersion));
            
            verify(accessRepository).deleteByFunctionUnitId(2L);
            verify(accessRepository).saveAll(permissionListCaptor.capture());
            
            List<FunctionUnitAccess> savedPermissions = permissionListCaptor.getValue();
            assertThat(savedPermissions).extracting(FunctionUnitAccess::getTargetId)
                    .containsExactly("role-3", "role-4", "role-5");
        }
        
        @Test
        @DisplayName("Should throw IllegalStateException when updating inactive version")
        void shouldThrowExceptionWhenUpdatingInactiveVersion() {
            // Given
            sourceVersion.setIsActive(false);
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceVersion));
            
            List<FunctionUnitAccess> newPermissions = Collections.singletonList(
                    FunctionUnitAccess.builder()
                            .accessType("USER")
                            .targetType("ROLE")
                            .targetId("role-3")
                            .build()
            );
            
            // When/Then
            assertThatThrownBy(() -> permissionService.updatePermissions(1L, newPermissions))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inactive version");
            
            verify(accessRepository, never()).deleteByFunctionUnitId(any());
            verify(accessRepository, never()).saveAll(anyList());
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException when version not found")
        void shouldThrowExceptionWhenVersionNotFound() {
            // Given
            when(functionUnitRepository.findById(1L)).thenReturn(Optional.empty());
            
            List<FunctionUnitAccess> newPermissions = Collections.emptyList();
            
            // When/Then
            assertThatThrownBy(() -> permissionService.updatePermissions(1L, newPermissions))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("1");
            
            verify(accessRepository, never()).deleteByFunctionUnitId(any());
            verify(accessRepository, never()).saveAll(anyList());
        }
        
        @Test
        @DisplayName("Should handle empty permission list update")
        void shouldHandleEmptyPermissionListUpdate() {
            // Given
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
            when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            List<FunctionUnitAccess> updatedPermissions = permissionService.updatePermissions(2L, Collections.emptyList());
            
            // Then
            assertThat(updatedPermissions).isEmpty();
            
            verify(accessRepository).deleteByFunctionUnitId(2L);
            verify(accessRepository).saveAll(Collections.emptyList());
        }
        
        @Test
        @DisplayName("Should set function unit reference for all new permissions")
        void shouldSetFunctionUnitReferenceForAllPermissions() {
            // Given
            List<FunctionUnitAccess> newPermissions = Arrays.asList(
                    FunctionUnitAccess.builder()
                            .accessType("USER")
                            .targetType("ROLE")
                            .targetId("role-1")
                            .build(),
                    FunctionUnitAccess.builder()
                            .accessType("DEVELOPER")
                            .targetType("USER")
                            .targetId("user-1")
                            .build()
            );
            
            when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
            when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            permissionService.updatePermissions(2L, newPermissions);
            
            // Then
            verify(accessRepository).saveAll(permissionListCaptor.capture());
            List<FunctionUnitAccess> savedPermissions = permissionListCaptor.getValue();
            
            assertThat(savedPermissions).allMatch(p -> p.getFunctionUnit() != null);
            assertThat(savedPermissions).allMatch(p -> p.getFunctionUnit().equals(targetVersion));
        }
    }
}
