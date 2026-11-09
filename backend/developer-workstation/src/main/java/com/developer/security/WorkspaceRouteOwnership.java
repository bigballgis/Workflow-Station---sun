package com.developer.security;

import com.developer.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The function unit a request reaches through the ids in its path (platform access model §6.1, §6.2).
 *
 * <p>The unit id in a path is not enough on its own: {@code PUT /function-units/A/forms/7} must not edit form 7 of
 * unit B. Every path id that names a form, table, action, … is resolved to its owner from the database. A path whose
 * unit (or form) does not own the object gets 404, the same as a missing object. Routes without a unit id
 * ({@code /actions/7}, {@code /api/forms/7/...}) are authorized against the owner found here.</p>
 */
@Component
@RequiredArgsConstructor
public class WorkspaceRouteOwnership {

    /** What the path puts a sub-resource under: the unit id or the form id earlier in the path, or nothing. */
    enum Parent { FUNCTION_UNIT, FORM, NONE }

    enum IdKind { NUMBER, UUID, TEXT }

    /**
     * A path shape that names a sub-resource. Group 1 is the parent id (unless {@link Parent#NONE}), the last group
     * the sub-resource id; {@code ownerSql} returns the id of what owns it (a unit, or a form for bindings).
     */
    record Route(String resource, Pattern pattern, Parent parent, IdKind idKind, String ownerSql) {
    }

    /** One owner lookup for a request: the route, the id taken from the path, and the parent id the path claims. */
    record Lookup(Route route, String id, Long claimedParent) {
    }

    private static final String BINDING_FORM_SQL = "SELECT form_id FROM dw_form_table_bindings WHERE id = ?";

    static final List<Route> ROUTES = List.of(
            unitChild("Form", "forms", "dw_form_definitions"),
            unitChild("Table", "tables", "dw_table_definitions"),
            unitChild("Action", "actions", "dw_action_definitions"),
            unitChild("Decision", "decisions", "dw_decision_definitions"),
            unitChild("EmailConnection", "connections", "dw_email_connections"),
            unitChild("EmailMonitorRule", "email-monitors", "dw_email_monitor_rules"),
            unitChild("EmailTemplate", "email-templates", "dw_email_templates"),
            unitChild("MainTableView", "main-table-views", "dw_main_table_view_configs"),
            unitChild("Version", "versions", "dw_versions"),
            new Route("FormTableBinding", path("/function-units/\\d+/forms/(\\d+)/bindings/(\\d+)"),
                    Parent.FORM, IdKind.NUMBER, BINDING_FORM_SQL),
            new Route("FormTableBinding",
                    path("/api/forms/(\\d+)/(?:sub-table-views|relation-views|relation-bindings)/(\\d+)"),
                    Parent.FORM, IdKind.NUMBER, BINDING_FORM_SQL),
            new Route("Form", path("/api/forms/(\\d+)"), Parent.NONE, IdKind.NUMBER,
                    "SELECT function_unit_id FROM dw_form_definitions WHERE id = ?"),
            new Route("Action", path("/actions/(\\d+)"), Parent.NONE, IdKind.NUMBER,
                    "SELECT function_unit_id FROM dw_action_definitions WHERE id = ?"),
            new Route("AiSession", path("/ai-generation/sessions/([^/]+)"), Parent.NONE, IdKind.UUID,
                    "SELECT function_unit_id FROM dw_ai_sessions WHERE session_id = ?"),
            new Route("Deployment", path("/function-units/deployments/([^/]+)"), Parent.NONE, IdKind.TEXT,
                    "SELECT function_unit_id FROM dw_deployment_jobs WHERE id = ?"));

    /** A design object that belongs to one function unit, and the query that finds its unit. */
    public enum Owned {
        FORM("Form", "SELECT function_unit_id FROM dw_form_definitions WHERE id = ?"),
        TABLE("Table", "SELECT function_unit_id FROM dw_table_definitions WHERE id = ?"),
        FIELD("Field", "SELECT t.function_unit_id FROM dw_field_definitions f "
                + "JOIN dw_table_definitions t ON t.id = f.table_id WHERE f.id = ?"),
        FORM_BINDING("FormTableBinding", "SELECT d.function_unit_id FROM dw_form_table_bindings b "
                + "JOIN dw_form_definitions d ON d.id = b.form_id WHERE b.id = ?"),
        RELATION_VIEW_CONFIG("RelationViewConfig", "SELECT d.function_unit_id FROM rt_view_configs v "
                + "JOIN dw_form_table_bindings b ON b.id = v.binding_id "
                + "JOIN dw_form_definitions d ON d.id = b.form_id WHERE v.id = ?"),
        ACTION("Action", "SELECT function_unit_id FROM dw_action_definitions WHERE id = ?"),
        EMAIL_CONNECTION("EmailConnection", "SELECT function_unit_id FROM dw_email_connections WHERE id = ?"),
        /** Id is the connection's {@code connection_uid}, which designs reference it by. */
        EMAIL_CONNECTION_UID("EmailConnection",
                "SELECT function_unit_id FROM dw_email_connections WHERE connection_uid = ?"),
        EMAIL_TEMPLATE("EmailTemplate", "SELECT function_unit_id FROM dw_email_templates WHERE id = ?"),
        LINK_FORM_COMPONENT("LinkFormComponent", "SELECT function_unit_id FROM dw_link_form_components WHERE id = ?"),
        /** Id is the session's UUID ({@code session_id}). */
        AI_SESSION("AiSession", "SELECT function_unit_id FROM dw_ai_sessions WHERE session_id = ?");

        private final String resource;
        private final String ownerSql;

        Owned(String resource, String ownerSql) {
            this.resource = resource;
            this.ownerSql = ownerSql;
        }
    }

    private final JdbcTemplate jdbcTemplate;

    /**
     * An id taken from a request body or query must name an object of the unit the request works in (§6.1, §6.2):
     * the path only proves the caller may work in that unit, not that the id they send belongs to it. Nothing to
     * check when the object is not referenced (null id).
     *
     * @throws ResourceNotFoundException when the object does not exist or belongs to another unit
     */
    public void requireInUnit(Owned kind, Object id, Long functionUnitId) {
        if (id == null) {
            return;
        }
        List<Long> owners = jdbcTemplate.queryForList(kind.ownerSql, Long.class, id);
        if (owners.isEmpty() || !owners.get(0).equals(functionUnitId)) {
            throw new ResourceNotFoundException(kind.resource, String.valueOf(id));
        }
    }

    /**
     * The email connection and template a design sends mail with (an action's send-email blocks, a BPMN send task)
     * come from the request body: they must be the unit's own (§6.1, §6.2). {@code connectionId} holds a connection
     * uid, or a legacy numeric connection id; an {@code emailTemplateId} that is not a number names no template and is
     * left to the completeness checks. A blank value references nothing.
     *
     * @throws ResourceNotFoundException when either names an object that does not exist or belongs to another unit
     */
    public void requireEmailReferencesInUnit(Object connectionId, Object emailTemplateId, Long functionUnitId) {
        if (connectionId != null && !connectionId.toString().isBlank()) {
            Long legacyId = toLong(connectionId);
            if (legacyId != null) {
                requireInUnit(Owned.EMAIL_CONNECTION, legacyId, functionUnitId);
            } else {
                requireInUnit(Owned.EMAIL_CONNECTION_UID, connectionId.toString(), functionUnitId);
            }
        }
        requireInUnit(Owned.EMAIL_TEMPLATE, toLong(emailTemplateId), functionUnitId);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return parseLong(value.toString());
    }

    /**
     * A form-table binding id taken from a request body must name a binding of that form; nothing to check for a null
     * id.
     *
     * @throws ResourceNotFoundException when the binding does not exist or belongs to another form
     */
    public void requireBindingOfForm(Long bindingId, Long formId) {
        if (bindingId == null) {
            return;
        }
        List<Long> forms = jdbcTemplate.queryForList(BINDING_FORM_SQL, Long.class, bindingId);
        if (forms.isEmpty() || !forms.get(0).equals(formId)) {
            throw new ResourceNotFoundException("FormTableBinding", String.valueOf(bindingId));
        }
    }

    /** The unit an object belongs to. */
    public Long unitOf(Owned kind, Object id) {
        List<Long> owners = jdbcTemplate.queryForList(kind.ownerSql, Long.class, id);
        if (owners.isEmpty() || owners.get(0) == null) {
            throw new ResourceNotFoundException(kind.resource, String.valueOf(id));
        }
        return owners.get(0);
    }

    /**
     * The unit to authorize when the path reaches it only through a sub-resource; empty when the path names the unit
     * itself, or nothing.
     *
     * @throws ResourceNotFoundException when a path id does not exist, or does not belong to the unit / form the path
     *                                   puts it under
     */
    public Optional<Long> functionUnitOf(String path) {
        Long reached = null;
        for (Lookup lookup : lookups(path)) {
            Long owner = owner(lookup);
            if (lookup.claimedParent() != null && !lookup.claimedParent().equals(owner)) {
                throw notFound(lookup);
            }
            if (lookup.route().parent() == Parent.NONE) {
                reached = owner;
            }
        }
        return Optional.ofNullable(reached);
    }

    /** The owner lookups a path needs, without touching the database. */
    static List<Lookup> lookups(String path) {
        List<Lookup> lookups = new ArrayList<>();
        for (Route route : ROUTES) {
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            Long parent = route.parent() == Parent.NONE ? null : parseLong(matcher.group(1));
            lookups.add(new Lookup(route, matcher.group(matcher.groupCount()), parent));
        }
        return lookups;
    }

    private Long owner(Lookup lookup) {
        Object id = switch (lookup.route().idKind()) {
            case NUMBER -> parseLong(lookup.id());
            case UUID -> parseUuid(lookup.id());
            case TEXT -> lookup.id();
        };
        if (id == null) {
            throw notFound(lookup);
        }
        List<Long> owners = jdbcTemplate.queryForList(lookup.route().ownerSql(), Long.class, id);
        if (owners.isEmpty() || owners.get(0) == null) {
            throw notFound(lookup);
        }
        return owners.get(0);
    }

    private static ResourceNotFoundException notFound(Lookup lookup) {
        return new ResourceNotFoundException(lookup.route().resource(), lookup.id());
    }

    private static Pattern path(String body) {
        return Pattern.compile("^" + body + "(?:/.*)?$");
    }

    private static Route unitChild(String resource, String segment, String table) {
        return new Route(resource, path("/function-units/(\\d+)/" + segment + "/(\\d+)"), Parent.FUNCTION_UNIT,
                IdKind.NUMBER, "SELECT function_unit_id FROM " + table + " WHERE id = ?");
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
