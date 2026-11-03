package com.admin.dataview;

import com.admin.repository.RoleRepository;
import com.admin.repository.UserRoleRepository;
import com.admin.service.UserBusinessUnitService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeDataViewAccessTest {
    private final RuntimeDataViewAccess policy=new RuntimeDataViewAccess(mock(UserRoleRepository.class),
            mock(RoleRepository.class),mock(UserBusinessUnitService.class));
    private final RuntimeDataViewAccess.Principal user=new RuntimeDataViewAccess.Principal(false,Set.of("role"),Set.of("bu"));
    private Map<String,Object> definition(String bu,String role) {
        List<Map<String,Object>> rules=new ArrayList<>();
        if(bu!=null) rules.add(Map.of("targetType","BUSINESS_UNIT","targetId",bu));
        if(role!=null) rules.add(Map.of("targetType","ROLE","targetId",role));
        return Map.of("accessRules",rules);
    }
    @Test void emptyAndPartialRulesNeverGrantOrdinaryUsers() {
        assertThat(policy.allows(user,definition(null,null))).isFalse();
        assertThat(policy.allows(user,definition("bu",null))).isFalse();
        assertThat(policy.allows(user,definition(null,"role"))).isFalse();
    }
    @Test void viewGrantWorksWithoutAnyFuRequestGrant() {
        assertThat(policy.allows(user,definition("bu","role"))).isTrue();
    }
    @Test void bothViewDimensionsMustMatch() {
        assertThat(policy.allows(user,definition("bu","role"))).isTrue();
        assertThat(policy.allows(user,definition("other-bu","role"))).isFalse();
        assertThat(policy.allows(user,definition("bu","other-role"))).isFalse();
    }
    @Test void systemAdministratorBypassesViewAndFuGrants() {
        var admin=new RuntimeDataViewAccess.Principal(true,Set.of(),Set.of());
        assertThat(policy.allows(admin,definition(null,null))).isTrue();
    }
    @Test void importedRuleTypeCaseDoesNotChangeItsMeaning() {
        assertThat(policy.allows(user,Map.of("accessRules",List.of(
                Map.of("targetType","business_unit","targetId","bu"),
                Map.of("targetType","role","targetId","role"))))).isTrue();
    }
}
