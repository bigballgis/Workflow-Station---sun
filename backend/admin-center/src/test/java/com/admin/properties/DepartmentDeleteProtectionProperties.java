package com.admin.properties;

import com.admin.component.OrganizationManagerComponent;
import com.admin.entity.Department;
import com.admin.exception.DepartmentHasChildrenException;
import com.admin.exception.DepartmentHasMembersException;
import com.admin.repository.DepartmentRepository;
import com.admin.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 属性 4: 部门删除保护有效性
 * 对于任何存在子部门或成员的部门，删除操作应该被阻止并返回相应错误信息
 * 
 * 验证需求: 需求 2.8
 */
public class DepartmentDeleteProtectionProperties {
    
    @Mock
    private DepartmentRepository departmentRepository;
    
    @Mock
    private UserRepository userRepository;
    
    private OrganizationManagerComponent organizationManager;
    
    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        organizationManager = new OrganizationManagerComponent(departmentRepository, userRepository);
    }
    
    /**
     * 功能: admin-center, 属性 4: 部门删除保护有效性
     * 存在子部门的部门不能被删除
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 4: 存在子部门的部门不能被删除")
    void departmentWithChildrenCannotBeDeleted(
            @ForAll("departmentIds") String deptId) {
        
        // Given: 一个有子部门的部门
        Department dept = Department.builder()
                .id(deptId)
                .name("Parent Department")
                .code("PARENT")
                .build();
        
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(departmentRepository.existsByParentId(deptId)).thenReturn(true);
        
        // When & Then: 删除应该被阻止
        assertThatThrownBy(() -> organizationManager.deleteDepartment(deptId))
                .isInstanceOf(DepartmentHasChildrenException.class)
                .hasMessageContaining("存在子部门");
    }
    
    /**
     * 功能: admin-center, 属性 4: 部门删除保护有效性
     * 存在成员的部门不能被删除
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 4: 存在成员的部门不能被删除")
    void departmentWithMembersCannotBeDeleted(
            @ForAll("departmentIds") String deptId,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 100) int memberCount) {
        
        // Given: 一个有成员的部门
        Department dept = Department.builder()
                .id(deptId)
                .name("Department with Members")
                .code("MEMBERS")
                .build();
        
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(departmentRepository.existsByParentId(deptId)).thenReturn(false);
        when(userRepository.countByDepartmentId(deptId)).thenReturn((long) memberCount);
        
        // When & Then: 删除应该被阻止
        assertThatThrownBy(() -> organizationManager.deleteDepartment(deptId))
                .isInstanceOf(DepartmentHasMembersException.class)
                .hasMessageContaining("存在成员");
    }
    
    /**
     * 功能: admin-center, 属性 4: 部门删除保护有效性
     * 检查子部门方法应该正确返回结果
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 4: 检查子部门方法应该正确返回结果")
    void hasChildDepartmentsShouldReturnCorrectResult(
            @ForAll("departmentIds") String deptId,
            @ForAll boolean hasChildren) {
        
        // Given
        when(departmentRepository.existsByParentId(deptId)).thenReturn(hasChildren);
        
        // When
        boolean result = organizationManager.hasChildDepartments(deptId);
        
        // Then
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(hasChildren);
    }
    
    /**
     * 功能: admin-center, 属性 4: 部门删除保护有效性
     * 检查成员方法应该正确返回结果
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 4: 检查成员方法应该正确返回结果")
    void hasDepartmentMembersShouldReturnCorrectResult(
            @ForAll("departmentIds") String deptId,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 100) int memberCount) {
        
        // Given
        when(userRepository.countByDepartmentId(deptId)).thenReturn((long) memberCount);
        
        // When
        boolean result = organizationManager.hasDepartmentMembers(deptId);
        
        // Then
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(memberCount > 0);
    }
    
    @Provide
    Arbitrary<String> departmentIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
}
