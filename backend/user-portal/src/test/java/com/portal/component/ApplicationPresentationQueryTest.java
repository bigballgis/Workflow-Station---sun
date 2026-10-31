package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.portal.dto.*;
import com.portal.util.MyApplicationColumnSpec;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import static org.assertj.core.api.Assertions.*;

class ApplicationPresentationQueryTest {
    private ProcessInstanceInfo row(String id,String holder,String display) {
        return ProcessInstanceInfo.builder().id(id).currentAssignee("multi").currentAssigneeDisplay(display)
                .currentAssigneeUserIds(holder==null ? List.of() : List.of(holder)).currentStepName("multi: Review").build();
    }
    private BiFunction<MyApplicationQueryRequest,Boolean,PortalListPage<ProcessInstanceInfo>> nativePages(
            List<ProcessInstanceInfo> rows,AtomicInteger calls) {
        return (request,enrich)->{
            calls.incrementAndGet();
            var matched=request.keyword()==null ? rows : rows.stream().filter(row->row.getId().contains(request.keyword())).toList();
            int start=Math.min(matched.size(),request.page()*request.size());
            return new PortalListPage<>(MyApplicationColumnSpec.columns(),matched.subList(start,Math.min(start+request.size(),matched.size())),
                    request.page(),request.size(),matched.size());
        };
    }
    private PortalListPage<ProcessInstanceInfo> query(MyApplicationQueryRequest request,List<ProcessInstanceInfo> rows) {
        return ApplicationPresentationQuery.query(request,MyApplicationColumnSpec.sql(),nativePages(rows,new AtomicInteger()));
    }
    @Test void userPickerEqualityAndInequalityUseRealIdentitiesInsteadOfPaintedPeople() {
        var rows=List.of(row("a","person-a","Alice"),row("b","person-b","Bob"));
        for(String op:List.of("eq","ne")) {
            var request=new MyApplicationQueryRequest(0,20,null,List.of(new ListColumnFilter("currentAssignee",op,"person-a",null)),null,null,null);
            assertThat(query(request,rows).content()).extracting(ProcessInstanceInfo::getId).containsExactly(op.equals("eq") ? "a" : "b");
        }
    }
    @Test void presentationFilterAndSortRunBeforeCountingAndPaging() {
        var rows=new ArrayList<ProcessInstanceInfo>();
        for(int i=0;i<501;i++) rows.add(row("p"+i,"u"+i,i<4 ? "Finance + Manager" : "Other"));
        var calls=new AtomicInteger();
        var request=new MyApplicationQueryRequest(1,2,null,List.of(new ListColumnFilter("currentAssignee","contains","Finance",null)),
                "currentAssignee","ASC",null);
        var result=ApplicationPresentationQuery.query(request,MyApplicationColumnSpec.sql(),nativePages(rows,calls));
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.content()).extracting(ProcessInstanceInfo::getId).containsExactly("p2","p3");
        assertThat(calls).hasValue(3);
    }
    @Test void absentKeywordDoesNotAccidentallyMatchEveryNativeRow() {
        var rows=List.of(row("existing","person-a","Alice"));
        var request=new MyApplicationQueryRequest(0,20,null,List.of(),null,null,"never-matches");
        assertThat(query(request,rows).totalElements()).isZero();
    }
    @Test void displayedAssigneeTextFiltersDoNotMatchOldOwnerOrPoolNames() {
        var rows=List.of(row("a","person-a","Alice"));
        for (String op:List.of("contains","notContains")) {
            var request=new MyApplicationQueryRequest(0,20,null,List.of(new ListColumnFilter("currentAssignee",op,"multi",null)),null,null,null);
            assertThat(query(request,rows).totalElements()).isEqualTo(op.equals("contains") ? 0 : 1);
        }
    }
    @Test void keywordCoversNativeColumnsAndNewStageAndBuRoleLabels() {
        var rows=List.of(row("native-match","person-a","Alice"),row("other","person-b","Finance + Manager"));
        assertThat(query(new MyApplicationQueryRequest(0,20,null,List.of(),null,null,"Finance"),rows).content())
                .extracting(ProcessInstanceInfo::getId).containsExactly("other");
        assertThat(query(new MyApplicationQueryRequest(0,20,null,List.of(),null,null,"native-match"),rows).content())
                .extracting(ProcessInstanceInfo::getId).containsExactly("native-match");
        assertThat(query(new MyApplicationQueryRequest(0,20,null,List.of(),null,null,"multi: Review"),rows).totalElements()).isEqualTo(2);
    }
    @Test void noPresentationCriteriaKeepTheExistingSqlPagePath() {
        assertThat(ApplicationPresentationQuery.needed(new MyApplicationQueryRequest(0,20,null,List.of(),"startTime","DESC",null))).isFalse();
    }
    @Test void keywordNativeSecondPassDoesNotRepeatPresentationLookup() {
        var flags=new ArrayList<Boolean>();
        var request=new MyApplicationQueryRequest(0,20,null,List.of(),null,null,"Alice");
        ApplicationPresentationQuery.query(request,MyApplicationColumnSpec.sql(),(input,enrich)->{
            flags.add(enrich);
            return new PortalListPage<>(MyApplicationColumnSpec.columns(),input.keyword()==null ? List.of(row("p","u","Alice")) : List.of(),0,200,input.keyword()==null?1:0);
        });
        assertThat(flags).containsExactly(true,false);
    }
}
