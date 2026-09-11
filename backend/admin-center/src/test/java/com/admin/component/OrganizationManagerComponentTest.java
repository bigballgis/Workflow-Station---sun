package com.admin.component;

import com.admin.dto.response.BusinessUnitTree;
import com.admin.exception.CircularDependencyException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.platform.security.entity.BusinessUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织架构树排序 + 拖拽移动（整棵子树跟随、同级重排、防环）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationManagerComponentTest {

    @Mock
    private BusinessUnitRepository businessUnitRepository;
    @Mock
    private UserRepository userRepository;

    private OrganizationManagerComponent component;

    @BeforeEach
    void setUp() {
        component = new OrganizationManagerComponent(businessUnitRepository, userRepository);
        when(userRepository.countMembersByBusinessUnitId(anyString())).thenReturn(0L);
        when(businessUnitRepository.existsByNameAndParentIdExcluding(anyString(), any(), anyString()))
                .thenReturn(false);
        when(businessUnitRepository.findByParentIdOrderBySortOrder(anyString())).thenReturn(List.of());
    }

    private static BusinessUnit unit(String id, String name, String parentId, int level, String path, int sortOrder) {
        return BusinessUnit.builder()
                .id(id).name(name).code(id.toUpperCase())
                .parentId(parentId).level(level).path(path)
                .sortOrder(sortOrder).status("ACTIVE")
                .build();
    }

    private void stubFind(BusinessUnit... units) {
        for (BusinessUnit u : units) {
            when(businessUnitRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    @Test
    void tree_sortsRootsByNameIgnoringSortOrder_andChildrenBySortOrder() {
        BusinessUnit zeta = unit("zeta", "Zeta", null, 1, "/zeta", 0);
        BusinessUnit alpha = unit("alpha", "alpha", null, 1, "/alpha", 9);
        BusinessUnit beta = unit("beta", "Beta", null, 1, "/beta", 1);
        BusinessUnit a2 = unit("a2", "Second", "alpha", 2, "/alpha/a2", 2);
        BusinessUnit a1 = unit("a1", "First", "alpha", 2, "/alpha/a1", 1);
        when(businessUnitRepository.findAllActive()).thenReturn(List.of(zeta, alpha, beta, a2, a1));

        List<BusinessUnitTree> roots = component.getBusinessUnitTree();

        assertThat(roots).extracting(BusinessUnitTree::getName).containsExactly("alpha", "Beta", "Zeta");
        assertThat(roots.get(0).getChildren()).extracting(BusinessUnitTree::getName)
                .containsExactly("First", "Second");
    }

    @Test
    void move_relocatesWholeSubtreeUnderNewParent() {
        BusinessUnit oldParent = unit("old", "Old", null, 1, "/old", 0);
        BusinessUnit target = unit("tgt", "Target", null, 1, "/tgt", 1);
        BusinessUnit moving = unit("mv", "Moving", "old", 2, "/old/mv", 0);
        BusinessUnit child = unit("ch", "Child", "mv", 3, "/old/mv/ch", 0);
        BusinessUnit grandChild = unit("gc", "GrandChild", "ch", 4, "/old/mv/ch/gc", 0);
        stubFind(oldParent, target, moving, child, grandChild);
        when(businessUnitRepository.findByParentIdOrderBySortOrder("mv")).thenReturn(List.of(child));
        when(businessUnitRepository.findByParentIdOrderBySortOrder("ch")).thenReturn(List.of(grandChild));

        component.moveBusinessUnit("mv", "tgt", null);

        assertThat(moving.getParentId()).isEqualTo("tgt");
        assertThat(moving.getLevel()).isEqualTo(2);
        assertThat(moving.getPath()).isEqualTo("/tgt/mv");
        assertThat(child.getLevel()).isEqualTo(3);
        assertThat(child.getPath()).isEqualTo("/tgt/mv/ch");
        assertThat(grandChild.getLevel()).isEqualTo(4);
        assertThat(grandChild.getPath()).isEqualTo("/tgt/mv/ch/gc");
        verify(businessUnitRepository).save(moving);
        verify(businessUnitRepository).save(child);
        verify(businessUnitRepository).save(grandChild);
        // 没给 sortOrder 就不重排同级
        verify(businessUnitRepository, never()).saveAll(any());
    }

    @Test
    void move_rejectsOwnDescendant_evenWhenSeedPathsUseCodesInsteadOfIds() {
        BusinessUnit moving = unit("bu-e2e-hq", "HQ", null, 1, "/E2E_HQ", 0);
        BusinessUnit child = unit("bu-e2e-it", "IT", "bu-e2e-hq", 2, "/E2E_HQ/E2E_IT", 0);
        stubFind(moving, child);

        assertThatThrownBy(() -> component.moveBusinessUnit("bu-e2e-hq", "bu-e2e-it", null))
                .isInstanceOf(CircularDependencyException.class);
        verify(businessUnitRepository, never()).save(any());
    }

    @Test
    void move_rejectsMovingOntoItself() {
        BusinessUnit moving = unit("a", "A", null, 1, "/a", 0);
        stubFind(moving);

        assertThatThrownBy(() -> component.moveBusinessUnit("a", "a", 0))
                .isInstanceOf(CircularDependencyException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void move_withSortOrder_insertsAmongActiveSiblingsAndRenumbers() {
        BusinessUnit parent = unit("p", "Parent", null, 1, "/p", 0);
        BusinessUnit x = unit("x", "X", "p", 2, "/p/x", 0);
        BusinessUnit y = unit("y", "Y", "p", 2, "/p/y", 1);
        BusinessUnit gone = unit("gone", "Gone", "p", 2, "/p/gone", 2);
        gone.setStatus("INACTIVE");
        BusinessUnit moving = unit("mv", "Moving", null, 1, "/mv", 5);
        stubFind(parent, x, y, gone, moving);
        when(businessUnitRepository.findByParentIdOrderBySortOrder("p")).thenReturn(List.of(x, y, gone));

        component.moveBusinessUnit("mv", "p", 1);

        ArgumentCaptor<List<BusinessUnit>> captor = ArgumentCaptor.forClass(List.class);
        verify(businessUnitRepository).saveAll(captor.capture());
        assertThat(x.getSortOrder()).isEqualTo(0);
        assertThat(moving.getSortOrder()).isEqualTo(1);
        assertThat(y.getSortOrder()).isEqualTo(2);
        assertThat(gone.getSortOrder()).isEqualTo(3);
        assertThat(captor.getValue()).extracting(BusinessUnit::getId).containsExactlyInAnyOrder("mv", "y", "gone");
    }

    @Test
    void move_withSortOrderBeyondEnd_appendsAfterLastActiveSibling() {
        BusinessUnit parent = unit("p", "Parent", null, 1, "/p", 0);
        BusinessUnit x = unit("x", "X", "p", 2, "/p/x", 0);
        BusinessUnit moving = unit("mv", "Moving", null, 1, "/mv", 0);
        stubFind(parent, x, moving);
        when(businessUnitRepository.findByParentIdOrderBySortOrder("p")).thenReturn(List.of(x));

        component.moveBusinessUnit("mv", "p", 99);

        assertThat(x.getSortOrder()).isEqualTo(0);
        assertThat(moving.getSortOrder()).isEqualTo(1);
    }
}
