package com.workflow.assignment;

import com.platform.common.assignment.*;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssignmentReconciliationQueueTest {
    final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    final AssignmentTaskReadContexts tasks=mock(AssignmentTaskReadContexts.class);
    final AssignmentIdentityClient identities=mock(AssignmentIdentityClient.class);
    final AssignmentReconciliationQueue queue=new AssignmentReconciliationQueue(states,tasks,identities);
    @Test void lostActualHolderBeyondTheFirstHundredIsPrioritizedWithOneDeduplicatedRead() {
        var order=new java.util.ArrayList<>(java.util.stream.IntStream.range(0,100).mapToObj(i->"valid-"+i).toList());order.add("lost");
        var current=new LinkedHashMap<String,AssignmentTaskReadContexts.Context>();
        order.forEach(id->current.put(id,context(id,"lost".equals(id)?"inactive":"active")));
        when(states.dueTasks(7,1000)).thenReturn(order);when(tasks.read(order)).thenReturn(current);
        when(identities.resolve(anyList())).thenReturn(snapshot(false));
        var result=queue.due(7);
        assertEquals("lost",result.get(0));
        assertEquals(order.subList(0,100),result.subList(1,101));
        verify(identities).resolve(List.of(new AssignmentIdentitySnapshot.Reference("active",AssignmentIdentitySnapshot.Kind.ID),
                new AssignmentIdentitySnapshot.Reference("inactive",AssignmentIdentitySnapshot.Kind.ID)));
    }
    @Test void activeAndUnheldTasksKeepTheirOriginalFairOrderAndReleasedHolderLosesPriority() {
        var order=List.of("a","b","c");when(states.dueTasks(7,1000)).thenReturn(order);
        when(tasks.read(order)).thenReturn(Map.of("a",context("a","active"),"b",context("b",null),"c",context("c","inactive")));
        when(identities.resolve(anyList())).thenReturn(snapshot(true));assertEquals(order,queue.due(7));
        when(tasks.read(order)).thenReturn(Map.of("a",context("a","active"),"b",context("b",null),"c",context("c",null)));
        when(identities.resolve(anyList())).thenReturn(snapshot(false));assertEquals(order,queue.due(7));
    }
    @Test void missingIsAnExplicitAuthorityResultAndDependencyFailureNeverBecomesMissing() {
        var order=List.of("a","b");when(states.dueTasks(7,1000)).thenReturn(order);
        when(tasks.read(order)).thenReturn(Map.of("a",context("a","active"),"b",context("b","absent")));
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,Map.of("active",new AssignmentIdentitySnapshot.User("active","active",true)),Set.of("absent")));
        assertEquals(List.of("b","a"),queue.due(7));
        when(identities.resolve(anyList())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        assertThrows(AssignmentFailure.class,()->queue.due(7));
        doReturn(new AssignmentIdentitySnapshot(8,Map.of(),Set.of("active","absent"))).when(identities).resolve(anyList());
        assertThrows(AssignmentFailure.class,()->queue.due(7));
    }
    private AssignmentIdentitySnapshot snapshot(boolean active) {
        return new AssignmentIdentitySnapshot(7,Map.of("active",new AssignmentIdentitySnapshot.User("active","active",true),
                "inactive",new AssignmentIdentitySnapshot.User("inactive","inactive",active)),Set.of());
    }
    private AssignmentTaskReadContexts.Context context(String id,String holder) {
        return new AssignmentTaskReadContexts.Context(new AssignmentTaskReadContexts.Subject(id,
                new AssignmentTaskReadContexts.Position("instance","definition","work"),holder,true),List.of());
    }
}
