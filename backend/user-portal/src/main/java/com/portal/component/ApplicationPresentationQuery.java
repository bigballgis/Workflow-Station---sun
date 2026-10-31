package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListFilterSql;
import com.portal.dto.*;
import com.portal.util.TaskQueryColumnFilters;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Only presentation-dependent searches scan authorized native pages; ordinary pages stay SQL-paged. */
final class ApplicationPresentationQuery {
    private ApplicationPresentationQuery() { }
    static boolean needed(MyApplicationQueryRequest request) {
        return painted(request.sortField()) || request.keyword()!=null || request.filters().stream().anyMatch(f->painted(f.field()));
    }
    private static boolean painted(String field) {
        return "currentAssignee".equals(field) || "currentStepName".equals(field);
    }
    static PortalListPage<ProcessInstanceInfo> query(MyApplicationQueryRequest request,ListFilterSql spec,
            BiFunction<MyApplicationQueryRequest,Boolean,PortalListPage<ProcessInstanceInfo>> nativeQuery) {
        // Validate the original declaration/operators before splitting native and presentation criteria.
        spec.whereClause(request.filters(),new ArrayList<>()); spec.orderBy(request.sortField(),request.sortDirection());
        var nativeFilters=request.filters().stream().filter(f->!painted(f.field())).toList();
        String sort=painted(request.sortField()) ? null : request.sortField();
        String direction=sort==null ? null : request.sortDirection();
        var nativeRequest=new MyApplicationQueryRequest(0,200,request.status(),nativeFilters,sort,direction,null);
        var first=nativeQuery.apply(nativeRequest,true);
        var rows=all(first,nativeRequest,r->nativeQuery.apply(r,true));
        Set<String> nativeKeywordHits=Set.of();
        if (request.keyword()!=null) {
            var searched=new MyApplicationQueryRequest(0,200,request.status(),nativeFilters,sort,direction,request.keyword());
            nativeKeywordHits=new HashSet<>();
            for (var row:all(nativeQuery.apply(searched,false),searched,r->nativeQuery.apply(r,false))) nativeKeywordHits.add(row.getId());
        }
        var hits=nativeKeywordHits;
        var filtered=new ArrayList<>(rows.stream().filter(row->matches(row,request.filters()))
                .filter(row->request.keyword()==null || hits.contains(row.getId()) || keyword(row,request.keyword())).toList());
        if (painted(request.sortField())) {
            Comparator<String> text=String.CASE_INSENSITIVE_ORDER;
            if ("DESC".equalsIgnoreCase(request.sortDirection())) text=text.reversed();
            Comparator<ProcessInstanceInfo> comparator=Comparator.comparing(row->value(row,request.sortField()),Comparator.nullsLast(text));
            filtered.sort(comparator.thenComparing(ProcessInstanceInfo::getId));
        }
        int start=Math.min(filtered.size(),Math.multiplyExact(request.page(),request.size()));
        return new PortalListPage<>(first.columns(),filtered.subList(start,Math.min(start+request.size(),filtered.size())),
                request.page(),request.size(),filtered.size());
    }
    private static List<ProcessInstanceInfo> all(PortalListPage<ProcessInstanceInfo> first,MyApplicationQueryRequest request,
            Function<MyApplicationQueryRequest,PortalListPage<ProcessInstanceInfo>> query) {
        var rows=new LinkedHashMap<String,ProcessInstanceInfo>();
        first.content().forEach(row->rows.put(row.getId(),row));
        for (int page=1;(long)page*request.size()<first.totalElements();page++) {
            var next=query.apply(new MyApplicationQueryRequest(page,request.size(),request.status(),request.filters(),
                    request.sortField(),request.sortDirection(),request.keyword()));
            next.content().forEach(row->rows.put(row.getId(),row));
        }
        return new ArrayList<>(rows.values());
    }
    private static boolean matches(ProcessInstanceInfo row,List<ListColumnFilter> filters) {
        String assignee=row.getCurrentAssigneeDisplay()!=null ? row.getCurrentAssigneeDisplay() : row.getCurrentAssignee();
        var task=TaskInfo.builder().currentStepName(row.getCurrentStepName()).assignee(assignee)
                .assigneeName(assignee).assigneeDisplay(assignee).build();
        var identities=row.getCurrentAssigneeUserIds();
        for (var filter:filters) if ("currentAssignee".equals(filter.field()) && identities!=null
                && ("eq".equalsIgnoreCase(filter.operator()) || "ne".equalsIgnoreCase(filter.operator()))) {
            boolean contains=identities.contains(filter.value());
            if ("eq".equalsIgnoreCase(filter.operator())!=contains) return false;
        }
        var displayFilters=filters.stream().filter(f->painted(f.field()))
                .filter(f->!("currentAssignee".equals(f.field()) && identities!=null
                        && ("eq".equalsIgnoreCase(f.operator()) || "ne".equalsIgnoreCase(f.operator())))).map(f->new ListColumnFilter(
                "currentAssignee".equals(f.field()) ? "assigneeName" : f.field(),f.operator(),f.value(),f.value2())).toList();
        return TaskQueryColumnFilters.matches(task,displayFilters);
    }
    private static boolean keyword(ProcessInstanceInfo row,String keyword) {
        String expected=keyword.toLowerCase(Locale.ROOT);
        return List.of(Objects.toString(row.getCurrentStepName(),""),Objects.toString(row.getCurrentAssigneeDisplay(),""))
                .stream().anyMatch(value->value.toLowerCase(Locale.ROOT).contains(expected));
    }
    private static String value(ProcessInstanceInfo row,String field) {
        String value="currentStepName".equals(field) ? row.getCurrentStepName()
                : row.getCurrentAssigneeDisplay()!=null ? row.getCurrentAssigneeDisplay() : row.getCurrentAssignee();
        return value==null || value.isBlank() ? null : value;
    }
}
