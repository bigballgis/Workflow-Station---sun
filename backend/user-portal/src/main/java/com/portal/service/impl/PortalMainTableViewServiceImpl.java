package com.portal.service.impl;

import com.platform.common.list.ListColumnMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.SubTableRowIdentity;
import com.platform.common.subtable.SubTableStoreKeys;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.component.OwnerFieldComponent;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.util.MainTableViewColumnSpec;
import com.portal.util.MainTableViewDerivedFilterSql;
import com.portal.util.MainTableViewDesignerFilterSql;
import com.portal.util.SqlFragment;
import com.portal.dto.MainTableViewPortalDtos.FunctionUnitViewMenuItem;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataPage;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataRow;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import com.portal.util.MainTableViewFkDisplaySupport;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewSummary;
import com.portal.entity.ProcessInstance;
import com.portal.service.PortalMainTableViewService;
import com.portal.service.UserDisplayNameResolver;
import com.portal.util.MainTableViewSelectDisplay;
import com.portal.util.PortalMainTableViewCsvUtils;
import com.portal.util.PortalMainTableViewDetailValues;
import com.portal.util.PortalMainTableViewNestedSubTables;
import com.portal.util.PortalMainTableViewNestedSubTables.NestedBinding;
import com.portal.util.PortalMainTableViewRowKeys;
import com.portal.service.impl.ViewDefinition.ViewFieldDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalMainTableViewServiceImpl implements PortalMainTableViewService {

    private static final String PROCESS_INSTANCE_ID_FIELD = "processInstanceId";
    private final com.portal.client.PortalBiClient dataViewClient;
    private final ObjectMapper objectMapper;
    @Autowired
    private com.platform.common.functionunit.CatalogSnapshotReader versionSnapshots;
    private final MainTableViewInvolvementScope involvementScope;
    private final MainTableViewRowQueryComponent rowQueryComponent;
    private final MainTableViewSubRowQueryComponent subRowQueryComponent;
    private final UserDisplayNameResolver userDisplayNameResolver;

    @Override
    @Transactional(readOnly = true)
    public List<FunctionUnitViewMenuItem> listAccessibleFunctionUnits(String userId) {
        Map<String,List<com.platform.common.dataview.RuntimeDataView>> grouped = dataViewClient.getDataViews(userId).stream()
                .collect(Collectors.groupingBy(com.platform.common.dataview.RuntimeDataView::functionUnitCode,
                        LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream().map(views -> {
            var first = views.get(0);
            return FunctionUnitViewMenuItem.builder().functionUnitId(first.functionUnitId().toString())
                    .functionUnitCode(first.functionUnitCode()).functionUnitName(first.functionUnitName())
                    .iconSvg(first.iconSvg()).viewCount(views.size()).build();
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MainTableViewSummary> listPublishedViews(String userId, String functionUnitCode) {
        return dataViewClient.getDataViews(userId).stream().filter(view -> functionUnitCode.equals(view.functionUnitCode()))
                .map(view -> {
                    var definition = view.definition();
                    var filter = asMap(definition.get("filterConfig"));
                    var toolbar = asMap(filter.get("toolbar"));
                    return MainTableViewSummary.builder().id(view.id()).viewName(stringVal(definition.get("viewName")))
                            .isDefault(Boolean.TRUE.equals(definition.get("isDefault"))).tableId(view.tableId())
                            .tableLabel(view.tableLabel()).tableType(view.tableType())
                            .enableExport(toolbarEnable(toolbar,"enableExport",true))
                            .enableImport(false)
                            .detailFormId(numberVal(definition.get("detailFormId"))).build();
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MainTableViewDataPage queryViewData(String userId, Long viewId,
                                               MainTableViewQueryRequest request) {
        ViewDefinition view = loadPublishedView(userId, viewId);

        return isSubView(view)
                ? querySubViewPage(userId, view, request)
                : queryMainViewPage(userId, view, request);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean containsProcess(String userId, Long viewId, String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("Process instance ID is required");
        }
        ViewDefinition view = loadPublishedView(userId, viewId);
        var scope = new MainTableViewQueryRequest(0, 1, null, null, null, null, null);
        return isSubView(view)
                ? subRowQueryComponent.exists(subViewQuery(userId, view, scope, 0, 1), processInstanceId)
                : rowQueryComponent.exists(mainViewQuery(userId, view, scope, 0, 1), processInstanceId);
    }

    /**
     * MAIN views page in the database: one process instance is one row, so the filters, the
     * search, the sort and the row visibility all become one predicate and the total comes from
     * counting it.
     */
    private MainTableViewDataPage queryMainViewPage(String userId, ViewDefinition view,
                                                    MainTableViewQueryRequest request) {
        MainTableViewRowQueryComponent.Page page = rowQueryComponent.query(
                mainViewQuery(userId, view, request, request.page(), request.size()));
        Map<String, FkSourceMeta> fkSource = loadFkSourceMeta(view);
        Map<String, String> ownerCache = ownerDisplayCacheForInstances(page.instances());
        List<MainTableViewDataRow> rows = page.instances().stream()
                .map(pi -> MainTableViewDataRow.builder()
                        .rowKey(pi.getId())
                        .processInstanceId(pi.getId())
                        .detailForm(request.rowKey() == null ? null : releasedDetailForm(pi, view))
                        .functionUnitVersionLabel(pi.getFunctionUnitVersionLabel())
                        .values(stripInternalKeys(projectInstanceRow(pi, view, fkSource, ownerCache)))
                        .build())
                .toList();
        return MainTableViewDataPage.builder()
                .columns(visibleColumns(view))
                .rows(rows)
                .total(page.total())
                .page(request.page())
                .size(request.size())
                .build();
    }

    /**
     * SUB views page in the database too, but on the expansion rather than on the instances: one
     * sub-table row is one row, so the total counts sub-table rows and the page slices them.
     */
    private MainTableViewDataPage querySubViewPage(String userId, ViewDefinition view,
                                                   MainTableViewQueryRequest request) {
        MainTableViewSubRowQueryComponent.Page page = subRowQueryComponent.query(
                subViewQuery(userId, view, request, request.page(), request.size()));
        Map<String, FkSourceMeta> fkSource = loadFkSourceMeta(view);
        Map<String, String> ownerCache = ownerDisplayCacheForSubRows(page.rows());
        boolean detailRow = request.rowKey() != null;
        return MainTableViewDataPage.builder()
                .columns(visibleColumns(view))
                .rows(page.rows().stream()
                        .map(row -> MainTableViewDataRow.builder()
                                .rowKey(row.instance().getId() + "|" + SubTableRowIdentity.identityOf(row.subRow()))
                                .processInstanceId(row.instance().getId())
                                .detailForm(detailRow ? releasedDetailForm(row.instance(), view) : null)
                                .detailAvailable(hasReleasedDetailForm(row.instance(),view))
                                .functionUnitVersionLabel(row.instance().getFunctionUnitVersionLabel())
                                .values(subRowValues(row, view, fkSource, ownerCache, detailRow))
                                .build())
                        .toList())
                .total(page.total())
                .page(request.page())
                .size(request.size())
                .build();
    }

    private MainTableViewRowQueryComponent.Query mainViewQuery(
            String userId, ViewDefinition view, MainTableViewQueryRequest request, int page, int size) {
        List<MainTableViewColumnSpec.FieldSource> fields = queryableFieldSources(view);
        MainTableViewColumnSpec.SqlSource source = MainTableViewColumnSpec.SqlSource.INSTANCE;
        return new MainTableViewRowQueryComponent.Query(
                view.functionUnitCode(),
                MainTableViewColumnSpec.sqlFor(fields, view.sortConfig()),
                designerAndDerived(view, fields, source, request),
                MainTableViewDerivedFilterSql.plainFilters(request.filters(), fields),
                request.sortField(),
                request.sortDirection(),
                request.search(),
                searchableFields(fields, source),
                involvementPredicate(userId, view),
                page,
                size);
    }

    private MainTableViewSubRowQueryComponent.Query subViewQuery(
            String userId, ViewDefinition view, MainTableViewQueryRequest request, int page, int size) {
        List<MainTableViewColumnSpec.FieldSource> fields = queryableFieldSources(view);
        MainTableViewColumnSpec.SqlSource source = MainTableViewColumnSpec.SqlSource.EXPANDED_SUB_ROW;
        return new MainTableViewSubRowQueryComponent.Query(
                view.id(),
                view.functionUnitCode(),
                view.subStoreKey(),
                MainTableViewColumnSpec.sqlFor(fields, view.sortConfig(), source, "pi.id, pi.row_identity"),
                designerAndDerived(view, fields, source, request),
                MainTableViewDerivedFilterSql.plainFilters(request.filters(), fields),
                request.sortField(),
                request.sortDirection(),
                request.search(),
                searchableFields(fields, source),
                involvementPredicate(userId, view),
                page,
                size);
    }

    private SqlFragment designerAndDerived(ViewDefinition view,
                                           List<MainTableViewColumnSpec.FieldSource> fields,
                                           MainTableViewColumnSpec.SqlSource source,
                                           MainTableViewQueryRequest request) {
        return designerFilter(view, fieldSources(view, false), source)
                .and(MainTableViewDerivedFilterSql.whereClause(request.filters(), fields, source))
                .and(PortalMainTableViewRowKeys.exactMatch(
                        request.rowKey(), !source.instanceIsTheRow()));
    }

    /**
     * The designer's filter, compiled once per query. It used to be applied to each projected row
     * in memory; now that the page is drawn in SQL it has to be part of the same predicate, or a
     * view narrowed to a subset would page through everything and drop rows from each page.
     */
    private SqlFragment designerFilter(ViewDefinition view,
                                       List<MainTableViewColumnSpec.FieldSource> fields,
                                       MainTableViewColumnSpec.SqlSource source) {
        List<Object> params = new ArrayList<>();
        String sql = new MainTableViewDesignerFilterSql(
                MainTableViewColumnSpec.designerRefFor(fields, source),
                "View " + view.id() + " '" + view.viewName() + "'")
                .whereClause(view.filterConfig(), params);
        return new SqlFragment(sql, params);
    }

    /**
     * The keyword search covers the columns the query can compare as text — the same ones whose
     * values a user sees, minus display-mapped lookup / FK columns (those convert via
     * {@link MainTableViewDerivedFilterSql}, not ILIKE on the stored key).
     */
    private List<String> searchableFields(List<MainTableViewColumnSpec.FieldSource> fields,
                                          MainTableViewColumnSpec.SqlSource source) {
        return MainTableViewColumnSpec.columnsFor(fields, source).stream()
                .filter(ListColumnMeta::filterable)
                .filter(column -> fields.stream()
                        .noneMatch(f -> f.fieldName().equals(column.field())
                                && MainTableViewColumnSpec.isDisplayMapped(f)))
                .map(ListColumnMeta::field)
                .toList();
    }

    private MainTableViewInvolvementScope.Predicate involvementPredicate(String userId, ViewDefinition view) {
        if (!view.restrictToInvolvedUsers() || view.release().systemAdministrator()) {
            return null;
        }
        return involvementScope.predicateFor(userId, view.functionUnitCode());
    }

    private boolean isSubView(ViewDefinition view) {
        return "SUB".equalsIgnoreCase(view.tableType());
    }

    private MainTableViewColumnSpec.SqlSource sqlSourceOf(ViewDefinition view) {
        return isSubView(view)
                ? MainTableViewColumnSpec.SqlSource.EXPANDED_SUB_ROW
                : MainTableViewColumnSpec.SqlSource.INSTANCE;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportViewCsv(String userId, Long viewId, int maxRows,
                                MainTableViewQueryRequest request) {
        ViewDefinition view = loadPublishedView(userId, viewId);

        List<MainTableViewFieldColumn> columns = visibleColumns(view);
        int limit = Math.min(Math.max(maxRows, 1), 10000);
        List<Map<String, Object>> slice = exportRows(userId, view, request, limit);

        StringBuilder sb = new StringBuilder();
        sb.append(PortalMainTableViewCsvUtils.csvEscape(PROCESS_INSTANCE_ID_FIELD));
        if (!columns.isEmpty()) {
            sb.append(',');
        }
        sb.append(columns.stream().map(c -> PortalMainTableViewCsvUtils.csvEscape(c.displayLabel())).collect(Collectors.joining(",")));
        sb.append('\n');
        for (Map<String, Object> row : slice) {
            Map<String, Object> values = stripInternalKeys(row);
            sb.append(PortalMainTableViewCsvUtils.csvEscape(String.valueOf(row.get("_processInstanceId"))));
            if (!columns.isEmpty()) {
                sb.append(',');
            }
            sb.append(columns.stream()
                    .map(c -> PortalMainTableViewCsvUtils.csvEscape(PortalMainTableViewCsvUtils.formatCell(
                            MainTableViewSelectDisplay.display(c, values.get(c.fieldName())))))
                    .collect(Collectors.joining(",")));
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Export reads through the same path as the list, so a CSV matches what the user just saw. */
    private List<Map<String, Object>> exportRows(String userId, ViewDefinition view,
                                                 MainTableViewQueryRequest request, int limit) {
        Map<String, FkSourceMeta> fkSource = loadFkSourceMeta(view);
        if (isSubView(view)) {
            var page = subRowQueryComponent.query(subViewQuery(userId, view, request, 0, limit));
            Map<String, String> ownerCache = ownerDisplayCacheForSubRows(page.rows());
            return page.rows().stream()
                    .map(row -> projectSubRow(row, view, fkSource, ownerCache))
                    .toList();
        }
        var page = rowQueryComponent.query(mainViewQuery(userId, view, request, 0, limit));
        Map<String, String> ownerCache = ownerDisplayCacheForInstances(page.instances());
        return page.instances().stream()
                .map(pi -> projectInstanceRow(pi, view, fkSource, ownerCache))
                .toList();
    }

    /**
     * One SUB view row: the sub-table row's own members, with the owning instance's variables
     * available for the FK labels that resolve against the parent.
     */
    private Map<String, Object> projectSubRow(MainTableViewSubRowQueryComponent.Row row,
                                              ViewDefinition view,
                                              Map<String, FkSourceMeta> fkSource,
                                              Map<String, String> ownerCache) {
        Map<String, Object> vars = row.instance().getVariables() != null
                ? row.instance().getVariables()
                : Map.<String, Object>of();
        Map<String, Object> mainVars = stripInternalKeys(vars);
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("_processInstanceId", row.instance().getId());
        for (ViewFieldDef field : view.fields()) {
            if (!Boolean.TRUE.equals(field.visible())) {
                continue;
            }
            if (Boolean.TRUE.equals(field.systemField())) {
                projected.put(field.fieldName(), systemFieldValue(row.instance(), field.fieldName()));
            } else {
                projected.put(field.fieldName(),
                        resolveProjectedFieldValue(row.subRow(), field, mainVars, fkSource, ownerCache));
            }
        }
        return projected;
    }

    private Map<String, Object> projectInstanceRow(ProcessInstance pi, ViewDefinition view,
                                                   Map<String, FkSourceMeta> fkSource,
                                                   Map<String, String> ownerCache) {
        Map<String, Object> vars = pi.getVariables() != null ? pi.getVariables() : Map.<String, Object>of();
        Map<String, Object> mainVars = stripInternalKeys(vars);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_processInstanceId", pi.getId());

        for (ViewFieldDef field : view.fields()) {
            if (!Boolean.TRUE.equals(field.visible())) {
                continue;
            }
            if (Boolean.TRUE.equals(field.systemField())) {
                row.put(field.fieldName(), systemFieldValue(pi, field.fieldName()));
            } else {
                row.put(field.fieldName(), resolveProjectedFieldValue(vars, field, mainVars, fkSource, ownerCache));
            }
        }
        return row;
    }

    /**
     * Lookup display columns are not stored in process variables — project the source lookup
     * value (PK or row object) under the synthetic column key so the portal can hydrate labels.
     * FK display columns resolve against same-instance MAIN variables; unmatched keeps the FK scalar.
     */
    private Object resolveProjectedFieldValue(
            Map<String, Object> source,
            ViewFieldDef field,
            Map<String, Object> mainVars,
            Map<String, FkSourceMeta> fkSourceMeta,
            Map<String, String> ownerCache) {
        if ("lookup_display".equalsIgnoreCase(field.columnType())
                && field.lookupSourceField() != null && !field.lookupSourceField().isBlank()) {
            return source.get(field.lookupSourceField());
        }
        if ("fk_display".equalsIgnoreCase(field.columnType())
                && field.lookupSourceField() != null && !field.lookupSourceField().isBlank()) {
            Object fkVal = source.get(field.lookupSourceField());
            FkSourceMeta meta = fkSourceMeta.get(field.lookupSourceField());
            List<String> pkFields = meta != null ? meta.refPrimaryKeyFields() : List.of();
            Object resolved = MainTableViewFkDisplaySupport.resolveAttribute(
                    mainVars, fkVal, pkFields, field.lookupDisplayField());
            // FALLBACK(ux): unmatched FK — show raw scalar rather than inventing a value
            return resolved != null ? resolved : fkVal;
        }
        Object raw = source.get(field.fieldName());
        Object liveUser = liveResolveUserPrefixed(raw, ownerCache);
        if (liveUser != null) {
            return liveUser;
        }
        if (raw instanceof String step && step.startsWith("step:") && step.length() > 5) {
            return step.substring(5);
        }
        return raw;
    }

    private Map<String, String> ownerDisplayCacheForInstances(List<ProcessInstance> instances) {
        Set<String> keys = new LinkedHashSet<>();
        for (ProcessInstance pi : instances) {
            collectOwnerUserKeys(pi, keys);
        }
        return userDisplayNameResolver.resolveBatch(keys);
    }

    private Map<String, String> ownerDisplayCacheForSubRows(
            List<MainTableViewSubRowQueryComponent.Row> rows) {
        Set<String> keys = new LinkedHashSet<>();
        for (MainTableViewSubRowQueryComponent.Row row : rows) {
            collectOwnerUserKeys(row.instance(), keys);
            collectUserPrefixedKeys(row.subRow(), keys);
        }
        return userDisplayNameResolver.resolveBatch(keys);
    }

    private void collectOwnerUserKeys(ProcessInstance pi, Set<String> keys) {
        collectUserPrefixedKeys(pi.getVariables(), keys);
    }

    private static void collectUserPrefixedKeys(Map<String, Object> source, Set<String> keys) {
        if (source == null) {
            return;
        }
        for (Object value : source.values()) {
            if (value instanceof String s) {
                keys.addAll(OwnerFieldComponent.parseStoredUserIds(s));
            }
        }
    }

    private String liveResolveUserPrefixed(Object raw, Map<String, String> ownerCache) {
        if (!(raw instanceof String s)) {
            return null;
        }
        List<String> ids = OwnerFieldComponent.parseStoredUserIds(s);
        if (ids.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            String live = userDisplayNameResolver.resolveCached(id, ownerCache);
            names.add(live != null && !live.isBlank() ? live : id);
        }
        return String.join(UserDisplayNameResolver.MULTI_ASSIGNEE_DISPLAY_SEPARATOR, names);
    }

    private Object systemFieldValue(ProcessInstance pi, String fieldName) {
        return switch (fieldName) {
            case "process_status" -> pi.getStatus();
            case "start_time" -> pi.getStartTime();
            case "initiator" -> pi.getStartUserName() != null ? pi.getStartUserName() : pi.getStartUserId();
            case "current_step" -> pi.getCurrentNode();
            default -> null;
        };
    }

    private List<MainTableViewFieldColumn> visibleColumns(ViewDefinition view) {
        Map<String, FkColumnMeta> resolvedFk = loadFkColumnMeta(view);
        // SUB views: the declared FK back to the owning MAIN table is already a field-level FK above;
        // mark it so the portal opens the row's own request. A view that hides it has no such link.
        if ("SUB".equalsIgnoreCase(view.tableType()) && view.mainTableId() != null) {
            Map<String, FkColumnMeta> merged = new LinkedHashMap<>(resolvedFk);
            loadOwningMainFkField(view).ifPresent(field -> {
                var existing=merged.get(field);
                var declared=tableFields(view).stream().filter(f -> field.equals(f.get("fieldName"))).findFirst().orElseThrow();
                merged.put(field,new FkColumnMeta(existing == null ? null : existing.refViewId(),
                        view.functionUnitCode(),stringList(declared.get("refPrimaryKeyFields")),true));
            });
            resolvedFk = merged;
        }
        final Map<String, FkColumnMeta> fkMeta = resolvedFk;
        final Map<String, FkSourceMeta> fkSourceMeta = loadFkSourceMeta(view);
        // Lookup columns reference a Relation Table (via the form's lookupConfig), not a DW table.
        final Map<String, LookupColumnMeta> lookupMeta = loadLookupColumnMeta(view);
        // Only a column designed to show option labels needs the bound forms' static options.
        boolean anyLabelDisplay = view.fields().stream()
                .anyMatch(f -> MainTableViewSelectDisplay.showsLabel(f.selectDisplay()));
        final Map<String, List<ListColumnMeta.Option>> selectOptions = anyLabelDisplay
                ? loadSelectOptionMeta(view)
                : Map.of();
        ColumnMetaBundle bundle = new ColumnMetaBundle(
                fkMeta, fkSourceMeta, lookupMeta, declaredCapabilities(view), selectOptions);
        return view.fields().stream()
                .filter(f -> Boolean.TRUE.equals(f.visible()))
                .sorted(Comparator.comparingInt(f -> f.sortOrder() != null ? f.sortOrder() : 0))
                .map(f -> toFieldColumn(f, bundle))
                .toList();
    }

    private MainTableViewFieldColumn toFieldColumn(ViewFieldDef f, ColumnMetaBundle meta) {
        String type = f.columnType() != null ? f.columnType() : "";
        boolean lookupDisplay = "lookup_display".equalsIgnoreCase(type);
        boolean fkDisplay = "fk_display".equalsIgnoreCase(type);
        boolean derived = lookupDisplay || fkDisplay;
        String sourceField = derived && f.lookupSourceField() != null ? f.lookupSourceField() : f.fieldName();
        FkColumnMeta fk = derived ? null : meta.fk().get(f.fieldName());
        FkSourceMeta fkSrc = fkDisplay ? meta.fkSource().get(sourceField) : null;
        LookupColumnMeta lookup = lookupDisplay || !derived ? meta.lookup().get(sourceField) : null;
        Long lookupTableId = lookup != null ? lookup.tableId() : null;
        ListColumnMeta capability = meta.capabilities().get(f.fieldName());
        if (capability == null) {
            throw new IllegalStateException("View column was not declared: " + f.fieldName());
        }
        MainTableViewFieldColumn.MainTableViewFieldColumnBuilder column = MainTableViewFieldColumn.builder()
                .fieldName(f.fieldName())
                .displayLabel(f.displayLabel() != null ? f.displayLabel() : f.fieldName())
                .columnWidth(f.columnWidth())
                .systemField(f.systemField())
                .isForeignKey(fk != null)
                .refViewId(fk != null ? fk.refViewId() : null)
                .refFunctionUnitCode(fk != null ? fk.refFunctionUnitCode() : null)
                .refOwningRequest(fk != null && fk.owningRequest())
                .refPrimaryKeyFields(fk != null ? fk.refPrimaryKeyFields()
                        : (fkSrc != null ? fkSrc.refPrimaryKeyFields() : null))
                .isLookup(lookupTableId != null)
                .lookupTableId(lookupTableId)
                .columnType(lookupDisplay ? "lookup_display" : (fkDisplay ? "fk_display" : "field"))
                .lookupSourceField(derived ? f.lookupSourceField() : (lookup != null ? sourceField : null))
                .lookupDisplayField(derived ? f.lookupDisplayField() : null)
                .lookupSelectedDisplayField(lookup != null ? lookup.selectedDisplayField() : null)
                .lookupSearchFields(lookup != null ? lookup.searchFields() : null)
                .fkRefTableId(fkSrc != null ? fkSrc.refTableId() : null)
                .selectDisplay(MainTableViewSelectDisplay.showsLabel(f.selectDisplay())
                        ? MainTableViewSelectDisplay.LABEL : MainTableViewSelectDisplay.VALUE)
                .selectOptions(MainTableViewSelectDisplay.showsLabel(f.selectDisplay())
                        ? meta.selectOptions().get(f.fieldName()) : null);
        return MainTableViewFieldColumn.applyListCapabilities(column, capability).build();
    }

    /** What the header may offer per column, given where this view's rows live. */
    private Map<String, ListColumnMeta> declaredCapabilities(ViewDefinition view) {
        List<MainTableViewColumnSpec.FieldSource> fields = queryableFieldSources(view);
        List<ListColumnMeta> declared = MainTableViewColumnSpec.columnsFor(fields, sqlSourceOf(view));
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        declared.forEach(column -> byField.put(column.field(), column));
        return byField;
    }

    /**
     * The view's visible fields paired with the data type their table declares, which is what
     * decides whether a column can be compared as a number, a date or text.
     */
    private List<MainTableViewColumnSpec.FieldSource> queryableFieldSources(ViewDefinition view) {
        return fieldSources(view, true);
    }

    /** Designer predicates retain hidden fields' system/derived metadata; visibility is presentation. */
    private List<MainTableViewColumnSpec.FieldSource> fieldSources(ViewDefinition view, boolean visibleOnly) {
        Map<String, String> dataTypes = fieldDataTypes(view);
        Map<String, LookupColumnMeta> lookupMeta = loadLookupColumnMeta(view);
        Map<String, FkSourceMeta> fkMeta = loadFkSourceMeta(view);
        return view.fields().stream()
                .filter(f -> !visibleOnly || Boolean.TRUE.equals(f.visible()))
                .sorted(Comparator.comparingInt(f -> f.sortOrder() != null ? f.sortOrder() : 0))
                .map(f -> toFieldSource(f, dataTypes, lookupMeta, fkMeta))
                .toList();
    }

    private MainTableViewColumnSpec.FieldSource toFieldSource(
            ViewFieldDef field,
            Map<String, String> dataTypes,
            Map<String, LookupColumnMeta> lookupMeta,
            Map<String, FkSourceMeta> fkMeta) {
        boolean lookupDisplay = "lookup_display".equalsIgnoreCase(field.columnType());
        boolean fkDisplay = "fk_display".equalsIgnoreCase(field.columnType());
        String sourceField = (lookupDisplay || fkDisplay) ? field.lookupSourceField() : null;
        String displayField = (lookupDisplay || fkDisplay) ? field.lookupDisplayField() : null;
        Long lookupTableId = null;
        List<String> fkPks = List.of();
        if (lookupDisplay && sourceField != null) {
            LookupColumnMeta lookup = lookupMeta.get(sourceField);
            if (lookup != null) {
                lookupTableId = lookup.tableId();
                if (displayField == null || displayField.isBlank()) {
                    displayField = lookup.selectedDisplayField();
                }
            }
        }
        if (fkDisplay && sourceField != null) {
            FkSourceMeta fk = fkMeta.get(sourceField);
            if (fk != null && fk.refPrimaryKeyFields() != null) {
                fkPks = fk.refPrimaryKeyFields();
            }
        }
        return new MainTableViewColumnSpec.FieldSource(
                field.fieldName(),
                field.displayLabel(),
                Boolean.TRUE.equals(field.systemField()),
                field.columnType(),
                dataTypes.get(field.fieldName()),
                sourceField,
                displayField,
                lookupTableId,
                fkPks);
    }

    /**
     * Types for designed view columns. The bound table wins; other tables in the same Function
     * Unit fill names the bound table does not declare so a SUB view can still type a parent MAIN
     * column it shows. A name that exists on none of them is compared as TEXT — we do
     * not invent DATE/USER from the label.
     */
    private Map<String, String> fieldDataTypes(ViewDefinition view) {
        Map<String,String> result = new LinkedHashMap<>();
        for (var field : tableFields(view)) result.put(stringVal(field.get("fieldName")), stringVal(field.get("dataType")));
        view.release().tables().stream().sorted(Comparator.comparingInt(t -> "MAIN".equals(t.get("tableType")) ? 0 : 1))
                .flatMap(t -> objects(t.get("fields")).stream()).forEach(f -> result.putIfAbsent(
                        stringVal(f.get("fieldName")),stringVal(f.get("dataType"))));
        return result;
    }

    private List<Map<String,Object>> tableFields(ViewDefinition view) {
        return objects(viewTable(view).get("fields"));
    }

    private Map<String,Object> viewTable(ViewDefinition view) {
        var tables = view.release().tables().stream().filter(t -> view.release().tableName().equals(t.get("tableName"))).toList();
        if (tables.size()!=1) throw com.platform.common.functionunit.CatalogSnapshotReader.invalid(view.release().catalogId());
        return tables.get(0);
    }

    private record ColumnMetaBundle(
            Map<String, FkColumnMeta> fk,
            Map<String, FkSourceMeta> fkSource,
            Map<String, LookupColumnMeta> lookup,
            Map<String, ListColumnMeta> capabilities,
            Map<String, List<ListColumnMeta.Option>> selectOptions) {
    }

    /**
     * FK source metadata for {@code fk_display} hydration (does not require a published default view).
     */
    private Map<String,FkSourceMeta> loadFkSourceMeta(ViewDefinition view) {
        Map<String,FkSourceMeta> result = new LinkedHashMap<>();
        for (var field : tableFields(view)) {
            if (!Boolean.TRUE.equals(field.get("isForeignKey"))) continue;
            var parent = referencedTable(view,field);
            result.put(stringVal(field.get("fieldName")),new FkSourceMeta(
                    parent.isEmpty() ? null : numberVal(parent.get("tableId")), stringList(field.get("refPrimaryKeyFields"))));
        }
        return result;
    }

    private Map<String,Object> referencedTable(ViewDefinition view, Map<String,Object> field) {
        Object name = field.get("refTableName");
        if (name == null) return Map.of();
        if(field.get("refFunctionUnitCode") instanceof String code && !code.equals(view.functionUnitCode())) {
            return Map.of("tableName",name,"functionUnitCode",code);
        }
        var matches = view.release().tables().stream().filter(t -> name.equals(t.get("tableName"))).toList();
        if (matches.size()!=1) throw com.platform.common.functionunit.CatalogSnapshotReader.invalid(view.release().catalogId());
        return matches.get(0);
    }

    private List<Map<String,Object>> formsForTable(ViewDefinition view) {
        return view.release().forms().stream().filter(form -> objects(form.get("tableBindings")).stream()
                .anyMatch(b -> view.release().tableName().equals(b.get("tableName")))).toList();
    }

    private Map<String,LookupColumnMeta> loadLookupColumnMeta(ViewDefinition view) {
        Map<String,LookupColumnMeta> result = new LinkedHashMap<>();
        for (var form : formsForTable(view)) collectLookupFields(jsonText(form.get("configJson")),result);
        return result;
    }

    private Map<String,List<ListColumnMeta.Option>> loadSelectOptionMeta(ViewDefinition view) {
        Map<String,List<ListColumnMeta.Option>> result = new LinkedHashMap<>();
        for (var form : formsForTable(view)) {
            for (var binding : objects(form.get("tableBindings"))) {
                if (!view.release().tableName().equals(binding.get("tableName"))) continue;
                try {
                    MainTableViewSelectDisplay.collectOptionsForBinding(objectMapper.readTree(jsonText(form.get("configJson"))),
                            numberVal(binding.get("bindingId")),"PRIMARY".equals(binding.get("bindingType")),result);
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    throw new IllegalStateException("Published form is invalid",ex);
                }
            }
        }
        return result;
    }

    /** Walk a form config JSON tree, recording each lookup widget's field → lookup metadata. */
    private void collectLookupFields(String configJson, Map<String, LookupColumnMeta> out) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }
        try {
            walkLookupNodes(objectMapper.readTree(configJson), out);
        } catch (Exception e) {
            log.warn("Failed to parse form config for lookup fields: {}", e.getMessage());
        }
    }

    private void walkLookupNodes(com.fasterxml.jackson.databind.JsonNode node, Map<String, LookupColumnMeta> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            String type = node.path("type").asText(null);
            String field = node.path("field").asText(null);
            if ("lookup".equalsIgnoreCase(type) && field != null && !field.isBlank()) {
                LookupColumnMeta meta = parseLookupConfigMeta(node.path("props").path("lookupConfig").asText(null));
                if (meta != null) {
                    out.putIfAbsent(field, meta);
                }
            }
            node.fields().forEachRemaining(e -> walkLookupNodes(e.getValue(), out));
        } else if (node.isArray()) {
            node.forEach(child -> walkLookupNodes(child, out));
        }
    }

    /** {@code lookupConfig} is a JSON string holding {tableId, searchFields, selectedDisplayField, ...}. */
    private LookupColumnMeta parseLookupConfigMeta(String lookupConfigJson) {
        if (lookupConfigJson == null || lookupConfigJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode cfg = objectMapper.readTree(lookupConfigJson);
            com.fasterxml.jackson.databind.JsonNode tableIdNode = cfg.get("tableId");
            if (tableIdNode == null || !tableIdNode.isNumber()) {
                return null;
            }
            List<String> searchFields = new ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode searchNode = cfg.get("searchFields");
            if (searchNode != null && searchNode.isArray()) {
                searchNode.forEach(n -> {
                    if (n != null && n.isTextual() && !n.asText().isBlank()) {
                        searchFields.add(n.asText());
                    }
                });
            }
            String selected = null;
            if (cfg.hasNonNull("selectedDisplayField") && cfg.get("selectedDisplayField").isTextual()) {
                selected = cfg.get("selectedDisplayField").asText();
            } else if (cfg.hasNonNull("displayField") && cfg.get("displayField").isTextual()) {
                selected = cfg.get("displayField").asText();
            }
            return new LookupColumnMeta(tableIdNode.asLong(), searchFields, selected);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * For each FK field of the view's owning table, resolve the referenced table's PUBLISHED default
     * view (id + FU code) so the portal can render a drill-down link. FK columns whose referenced table
     * has no published default view are omitted from the map (rendered as plain text — graceful degrade).
     */
    private Map<String,FkColumnMeta> loadFkColumnMeta(ViewDefinition view) {
        Map<String,FkColumnMeta> result = new LinkedHashMap<>();
        for (var field : tableFields(view)) {
            if (!Boolean.TRUE.equals(field.get("isForeignKey"))) continue;
            var parent = referencedTable(view,field);
            if (parent.isEmpty()) continue;
            // References are filtered by Admin's same View policy, never by names guessed in Portal.
            var refs = view.release().references().stream().filter(ref -> ref.defaultView()
                    && ref.functionUnitCode().equals(parent.getOrDefault("functionUnitCode",view.functionUnitCode()))
                    && ref.tableName().equals(parent.get("tableName"))).toList();
            if (refs.size()>1) throw new IllegalStateException("Several published default Views target the same table");
            if (!refs.isEmpty()) {
                var ref=refs.get(0);
                result.put(stringVal(field.get("fieldName")), new FkColumnMeta(ref.id(),ref.functionUnitCode(),
                        stringList(field.get("refPrimaryKeyFields")),false));
            }
        }
        return result;
    }

    private Optional<String> loadOwningMainFkField(ViewDefinition view) {
        Set<String> candidates = new LinkedHashSet<>();
        for (var form : formsForTable(view)) {
            var bindings=objects(form.get("tableBindings"));
            for (var sub : bindings) {
                if (!"SUB".equals(sub.get("bindingType")) || !view.release().tableName().equals(sub.get("tableName"))) continue;
                for (var field : tableFields(view)) {
                    if (!Boolean.TRUE.equals(field.get("isForeignKey"))) continue;
                    if (field.get("refFunctionUnitCode") instanceof String code && !code.equals(view.functionUnitCode())) continue;
                    if (sub.get("filterFkFieldName")!=null && !sub.get("filterFkFieldName").equals(field.get("fieldName"))) continue;
                    if (bindings.stream().anyMatch(main -> "PRIMARY".equals(main.get("bindingType"))
                            && Objects.equals(main.get("tableName"),field.get("refTableName")))) {
                        candidates.add(stringVal(field.get("fieldName")));
                    }
                }
            }
        }
        return candidates.size()==1 ? candidates.stream().findFirst() : Optional.empty();
    }

    private List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> stripInternalKeys(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (!k.startsWith("_")) {
                out.put(k, v);
            }
        });
        return out;
    }

    /**
     * Detail (rowKey) needs stored members the list omitted, plus the nested store the DETAIL
     * form's SUB grids read. List pages omit both. {@link #stripInternalKeys} would drop
     * {@code __subTables__}, so that map is attached after.
     */
    private Map<String, Object> subRowValues(
            MainTableViewSubRowQueryComponent.Row row,
            ViewDefinition view,
            Map<String, FkSourceMeta> fkSource,
            Map<String, String> ownerCache,
            boolean detailRow) {
        Map<String, Object> projected = projectSubRow(row, view, fkSource, ownerCache);
        if (detailRow) {
            projected = PortalMainTableViewDetailValues.overlayStoredMembers(projected, row.subRow());
        }
        Map<String, Object> values = stripInternalKeys(projected);
        if (!detailRow) {
            return values;
        }
        var pin = com.portal.component.ProcessVersionContext.of(row.instance());
        var form = versionSnapshots.detailFormForView(pin.catalogId(), view.release().viewKey(), view.release().tableName(), view.viewName());
        if (form.isEmpty()) return values;
        var bindings = com.platform.common.functionunit.CatalogSnapshotReader.objects(form.get("tableBindings"));
        var nestedBindings = bindings.stream().filter(binding -> "SUB".equals(binding.get("bindingType")))
                .map(binding -> new NestedBinding((String) binding.get("tableName"), (String) binding.get("foreignKeyField"))).toList();
        var parentBindings = bindings.stream().filter(binding -> "PRIMARY".equals(binding.get("bindingType"))).toList();
        List<String> parentKeys = parentBindings.isEmpty() ? List.of()
                : ((List<?>) parentBindings.get(0).get("primaryKeyFields")).stream().map(String::valueOf).toList();
        Map<String, Object> nested = PortalMainTableViewNestedSubTables.forParentRow(
                row.subRow(),
                row.instance().getVariables(),
                nestedBindings,
                // The parent's identity columns come from Table Design, not from a list of likely
                // names: this view's parent may be keyed by `correspondence_id`, `case_number`, or
                // anything else the designer chose.
                parentKeys);
        if (!nested.isEmpty()) {
            values.put(PortalMainTableViewNestedSubTables.STORE_KEY, nested);
        }
        return values;
    }

    private boolean hasReleasedDetailForm(ProcessInstance instance, ViewDefinition view) {
        if (view.release().definition().get("detailFormId")==null) return false;
        String catalog=com.portal.component.ProcessVersionContext.of(instance).catalogId();
        return versionSnapshots.hasDetailFormForView(catalog,view.release().viewKey(),view.release().tableName(),view.viewName());
    }

    private Map<String, Object> releasedDetailForm(ProcessInstance instance, ViewDefinition view) {
        String catalog = com.portal.component.ProcessVersionContext.of(instance).catalogId();
        Map<String, Object> form = versionSnapshots.detailFormForView(catalog, view.release().viewKey(), view.release().tableName(), view.viewName());
        if (form.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>(form);
        result.put("data", form.get("configJson"));
        result.put("sourceId", form.get("formId"));
        result.put("peerForms", versionSnapshots.contents(catalog, "FORM").stream().map(peer -> {
            Map<String, Object> dto = new LinkedHashMap<>(peer);
            dto.put("data", peer.get("configJson"));
            dto.put("sourceId", peer.get("formId"));
            dto.put("tableBindings", versionSnapshots.formBindings(catalog, peer));
            return dto;
        }).toList());
        return result;
    }

    private ViewDefinition loadPublishedView(String userId, Long viewId) {
        var release = dataViewClient.getDataView(userId,viewId);
        var definition = release.definition();
        List<ViewFieldDef> fields=objects(definition.get("fields")).stream().map(field -> new ViewFieldDef(
                stringVal(field.get("fieldName")),stringVal(field.get("displayLabel")),integerVal(field.get("columnWidth")),
                integerVal(field.get("sortOrder")),!Boolean.FALSE.equals(field.get("visible")),Boolean.TRUE.equals(field.get("systemField")),
                stringVal(field.get("columnType")),stringVal(field.get("lookupSourceField")),
                stringVal(field.get("lookupDisplayField")),stringVal(field.get("selectDisplay")))).toList();
        return new ViewDefinition(viewId,stringVal(definition.get("viewName")),release.functionUnitCode(),
                definition.get("sortConfig")==null ? List.of() : objects(definition.get("sortConfig")),asMap(definition.get("filterConfig")),fields,release.tableId(),release.tableType(),
                "SUB".equals(release.tableType()) ? SubTableStoreKeys.dwKey(release.tableName()) : null,
                Boolean.TRUE.equals(definition.get("restrictToInvolvedUsers")),release);
    }

    private Map<String,Object> asMap(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?,?>)) throw new IllegalStateException("Published configuration must be an object");
        return objectMapper.convertValue(value,new TypeReference<Map<String,Object>>() { });
    }
    private List<Map<String,Object>> objects(Object value) {
        return com.platform.common.functionunit.CatalogSnapshotReader.objects(value);
    }
    private Long numberVal(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }
    private Integer integerVal(Object value) { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
    private List<String> stringList(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values)) throw new IllegalStateException("Published configuration must be an array");
        return values.stream().map(String::valueOf).toList();
    }
    private String jsonText(Object value) {
        if (value instanceof String text) return text;
        try { return objectMapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Published configuration is invalid",ex); }
    }

    private String stringVal(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private boolean toolbarEnable(Map<String, Object> toolbar, String key, boolean defaultValue) {
        Object val = toolbar.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(val));
    }

    /**
     * @param owningRequest the column links a SUB row to the MAIN row it lives in — that row is the
     *                      row's own process instance, so the portal opens it without a lookup
     */
    private record FkColumnMeta(
            Long refViewId,
            String refFunctionUnitCode,
            List<String> refPrimaryKeyFields,
            boolean owningRequest) {}

    /** Structural FK on the owning table — used to hydrate {@code fk_display} columns. */
    private record FkSourceMeta(Long refTableId, List<String> refPrimaryKeyFields) {}

    private record LookupColumnMeta(
            Long tableId,
            List<String> searchFields,
            String selectedDisplayField) {}

}
