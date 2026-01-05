package com.admin.properties;

import com.admin.component.OrganizationManagerComponent;
import com.admin.entity.Department;
import com.admin.exception.CircularDependencyException;
import com.admin.repository.DepartmentRepository;
import com.admin.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 属性 3: 组织架构树形结构完整性
 * 对于任何部门层级调整操作，系统应该检测并阻止循环依赖，保持树形结构的完整性
 * 
 * 验证需求: 需求 2.1, 2.4
 */
public class OrganizationTreeIntegrityProperties {
    
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
     * 功能: admin-center, 属性 3: 组织架构树形结构完整性
     * 移动部门到自己应该被检测为循环依赖
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 3: 移动部门到自己应该被检测为循环依赖")
    void movingDepartmentToItselfShouldBeDetectedAsCycle(
            @ForAll("departmentIds") String deptId) {
        
        // Given: 一个部门
        Department dept = Department.builder()
                .id(deptId)
                .name("Test Department")
                .code("TEST")
                .path("/" + deptId)
                .level(1)
                .build();
        
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        
        // When & Then: 移动到自己应该抛出循环依赖异常
        assertThatThrownBy(() -> organizationManager.moveDepartment(deptId, deptId))
                .isInstanceOf(CircularDependencyException.class);
    }
    
    /**
     * 功能: admin-center, 属性 3: 组织架构树形结构完整性
     * 移动部门到其后代应该被检测为循环依赖
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 3: 移动部门到其后代应该被检测为循环依赖")
    void movingDepartmentToDescendantShouldBeDetectedAsCycle() {
        // Given: 父部门和子部门
        String parentId = UUID.randomUUID().toString();
        String childId = UUID.randomUUID().toString();
        
        Department parent = Department.builder()
                .id(parentId)
                .name("Parent")
                .code("PARENT")
                .path("/" + parentId)
                .level(1)
                .build();
        
        Department child = Department.builder()
                .id(childId)
                .name("Child")
                .code("CHILD")
                .parentId(parentId)
                .path("/" + parentId + "/" + childId)
                .level(2)
                .build();
        
        when(departmentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(departmentRepository.findById(childId)).thenReturn(Optional.of(child));
        
        // When: 检测循环依赖
        boolean wouldCycle = organizationManager.wouldCreateCycle(parentId, childId);
        
        // Then: 应该检测到循环依赖
        assertThat(wouldCycle).isTrue();
    }
    
    /**
     * 功能: admin-center, 属性 3: 组织架构树形结构完整性
     * 移动部门到非后代应该不会造成循环依赖
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 3: 移动部门到非后代应该不会造成循环依赖")
    void movingDepartmentToNonDescendantShouldNotCauseCycle() {
        // Given: 两个独立的部门
        String dept1Id = UUID.randomUUID().toString();
        String dept2Id = UUID.randomUUID().toString();
        
        Department dept1 = Department.builder()
                .id(dept1Id)
                .name("Dept1")
                .code("DEPT1")
                .path("/" + dept1Id)
                .level(1)
                .build();
        
        Department dept2 = Department.builder()
                .id(dept2Id)
                .name("Dept2")
                .code("DEPT2")
                .path("/" + dept2Id)
                .level(1)
                .build();
        
        when(departmentRepository.findById(dept1Id)).thenReturn(Optional.of(dept1));
        when(departmentRepository.findById(dept2Id)).thenReturn(Optional.of(dept2));
        
        // When: 检测循环依赖
        boolean wouldCycle = organizationManager.wouldCreateCycle(dept1Id, dept2Id);
        
        // Then: 不应该检测到循环依赖
        assertThat(wouldCycle).isFalse();
    }
    
    /**
     * 功能: admin-center, 属性 3: 组织架构树形结构完整性
     * 移动部门到根级别不应该造成循环依赖
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 3: 移动部门到根级别不应该造成循环依赖")
    void movingDepartmentToRootShouldNotCauseCycle(
            @ForAll("departmentIds") String deptId) {
        
        // Given: 一个部门
        Department dept = Department.builder()
                .id(deptId)
                .name("Test")
                .code("TEST")
                .parentId("someParent")
                .path("/someParent/" + deptId)
                .level(2)
                .build();
        
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        
        // When: 检测移动到根级别是否会造成循环
        boolean wouldCycle = organizationManager.wouldCreateCycle(deptId, null);
        
        // Then: 不应该造成循环依赖
        assertThat(wouldCycle).isFalse();
    }
    
    @Provide
    Arbitrary<String> departmentIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
}
