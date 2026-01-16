package com.workflow.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminCenterClient 单元测试
 * 测试任务分配相关的 API 调用
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCenterClient Tests")
class AdminCenterClientTest {
    
    @Mock
    private RestTemplate restTemplate;
    
    private AdminCenterClient client;
    
    private static final String ADMIN_CENTER_URL = "http://localhost:8090";
    private static final String USER_ID = "user-001";
    private static final String BU_ID = "bu-001";
    private static final String PARENT_BU_ID = "bu-parent";
    private static final String ROLE_ID = "role-001";
    
    @BeforeEach
    void setUp() {
        client = new AdminCenterClient(restTemplate);
        ReflectionTestUtils.setField(client, "adminCenterUrl", ADMIN_CENTER_URL);
    }
    
    @Nested
    @DisplayName("getUserBusinessUnitId Tests")
    class GetUserBusinessUnitIdTests {
        
        @Test
        @DisplayName("Should return business unit ID when API returns valid response")
        void shouldReturnBusinessUnitId() {
            Map<String, Object> response = new HashMap<>();
            response.put("businessUnitId", BU_ID);
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/users/" + USER_ID + "/business-unit"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
            
            String result = client.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isEqualTo(BU_ID);
        }
        
        @Test
        @DisplayName("Should return null when API returns empty businessUnitId")
        void shouldReturnNullWhenEmpty() {
            Map<String, Object> response = new HashMap<>();
            response.put("businessUnitId", "");
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
            
            String result = client.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("Should return null when API throws exception")
        void shouldReturnNullOnException() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RestClientException("Connection refused"));
            
            String result = client.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isNull();
        }
    }
    
    @Nested
    @DisplayName("getParentBusinessUnitId Tests")
    class GetParentBusinessUnitIdTests {
        
        @Test
        @DisplayName("Should return parent business unit ID")
        void shouldReturnParentBusinessUnitId() {
            Map<String, Object> response = new HashMap<>();
            response.put("parentBusinessUnitId", PARENT_BU_ID);
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/business-units/" + BU_ID + "/parent"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
            
            String result = client.getParentBusinessUnitId(BU_ID);
            
            assertThat(result).isEqualTo(PARENT_BU_ID);
        }
        
        @Test
        @DisplayName("Should return null when no parent")
        void shouldReturnNullWhenNoParent() {
            Map<String, Object> response = new HashMap<>();
            response.put("parentBusinessUnitId", null);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
            
            String result = client.getParentBusinessUnitId(BU_ID);
            
            assertThat(result).isNull();
        }
    }
    
    @Nested
    @DisplayName("getUsersByBusinessUnitAndRole Tests")
    class GetUsersByBusinessUnitAndRoleTests {
        
        @Test
        @DisplayName("Should return user IDs list")
        void shouldReturnUserIdsList() {
            List<String> userIds = Arrays.asList("user-001", "user-002", "user-003");
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/business-units/" + BU_ID + "/roles/" + ROLE_ID + "/users"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(userIds, HttpStatus.OK));
            
            List<String> result = client.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID);
            
            assertThat(result).containsExactly("user-001", "user-002", "user-003");
        }
        
        @Test
        @DisplayName("Should return empty list when API returns null")
        void shouldReturnEmptyListWhenNull() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
            
            List<String> result = client.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID);
            
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("Should return empty list on exception")
        void shouldReturnEmptyListOnException() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RestClientException("Connection refused"));
            
            List<String> result = client.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID);
            
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("getUsersByUnboundedRole Tests")
    class GetUsersByUnboundedRoleTests {
        
        @Test
        @DisplayName("Should return user IDs list")
        void shouldReturnUserIdsList() {
            List<String> userIds = Arrays.asList("user-004", "user-005");
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/roles/" + ROLE_ID + "/users"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(userIds, HttpStatus.OK));
            
            List<String> result = client.getUsersByUnboundedRole(ROLE_ID);
            
            assertThat(result).containsExactly("user-004", "user-005");
        }
        
        @Test
        @DisplayName("Should return empty list on exception")
        void shouldReturnEmptyListOnException() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RestClientException("Connection refused"));
            
            List<String> result = client.getUsersByUnboundedRole(ROLE_ID);
            
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("getEligibleRoleIds Tests")
    class GetEligibleRoleIdsTests {
        
        @Test
        @DisplayName("Should return eligible role IDs")
        void shouldReturnEligibleRoleIds() {
            List<String> roleIds = Arrays.asList("role-001", "role-002");
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/business-units/" + BU_ID + "/eligible-roles"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(roleIds, HttpStatus.OK));
            
            List<String> result = client.getEligibleRoleIds(BU_ID);
            
            assertThat(result).containsExactly("role-001", "role-002");
        }
    }
    
    @Nested
    @DisplayName("isEligibleRole Tests")
    class IsEligibleRoleTests {
        
        @Test
        @DisplayName("Should return true when role is eligible")
        void shouldReturnTrueWhenEligible() {
            Map<String, Object> response = new HashMap<>();
            response.put("eligible", true);
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/business-units/" + BU_ID + "/roles/" + ROLE_ID + "/eligible"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
            
            boolean result = client.isEligibleRole(BU_ID, ROLE_ID);
            
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("Should return false when role is not eligible")
        void shouldReturnFalseWhenNotEligible() {
            Map<String, Object> response = new HashMap<>();
            response.put("eligible", false);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
            
            boolean result = client.isEligibleRole(BU_ID, ROLE_ID);
            
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("Should return false on exception")
        void shouldReturnFalseOnException() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RestClientException("Connection refused"));
            
            boolean result = client.isEligibleRole(BU_ID, ROLE_ID);
            
            assertThat(result).isFalse();
        }
    }
    
    @Nested
    @DisplayName("getBuBoundedRoles Tests")
    class GetBuBoundedRolesTests {
        
        @Test
        @DisplayName("Should return BU bounded roles")
        void shouldReturnBuBoundedRoles() {
            List<Map<String, Object>> roles = new ArrayList<>();
            Map<String, Object> role1 = new HashMap<>();
            role1.put("id", "role-001");
            role1.put("name", "Manager");
            role1.put("type", "BU_BOUNDED");
            roles.add(role1);
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/roles/bu-bounded"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(roles, HttpStatus.OK));
            
            List<Map<String, Object>> result = client.getBuBoundedRoles();
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("id")).isEqualTo("role-001");
        }
        
        @Test
        @DisplayName("Should return empty list on exception")
        void shouldReturnEmptyListOnException() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RestClientException("Connection refused"));
            
            List<Map<String, Object>> result = client.getBuBoundedRoles();
            
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("getBuUnboundedRoles Tests")
    class GetBuUnboundedRolesTests {
        
        @Test
        @DisplayName("Should return BU unbounded roles")
        void shouldReturnBuUnboundedRoles() {
            List<Map<String, Object>> roles = new ArrayList<>();
            Map<String, Object> role1 = new HashMap<>();
            role1.put("id", "role-002");
            role1.put("name", "Auditor");
            role1.put("type", "BU_UNBOUNDED");
            roles.add(role1);
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/task-assignment/roles/bu-unbounded"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(roles, HttpStatus.OK));
            
            List<Map<String, Object>> result = client.getBuUnboundedRoles();
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("id")).isEqualTo("role-002");
        }
    }
    
    @Nested
    @DisplayName("getUserInfo Tests")
    class GetUserInfoTests {
        
        @Test
        @DisplayName("Should return user info with manager IDs")
        void shouldReturnUserInfoWithManagerIds() {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", USER_ID);
            userInfo.put("username", "testuser");
            userInfo.put("functionManagerId", "manager-func-001");
            userInfo.put("entityManagerId", "manager-entity-001");
            
            when(restTemplate.exchange(
                    eq(ADMIN_CENTER_URL + "/api/v1/admin/users/" + USER_ID),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(new ResponseEntity<>(userInfo, HttpStatus.OK));
            
            Map<String, Object> result = client.getUserInfo(USER_ID);
            
            assertThat(result).isNotNull();
            assertThat(result.get("functionManagerId")).isEqualTo("manager-func-001");
            assertThat(result.get("entityManagerId")).isEqualTo("manager-entity-001");
        }
        
        @Test
        @DisplayName("Should return null when user not found")
        void shouldReturnNullWhenUserNotFound() {
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RestClientException("Not found"));
            
            Map<String, Object> result = client.getUserInfo(USER_ID);
            
            assertThat(result).isNull();
        }
    }
}
