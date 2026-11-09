package com.developer.security;

import com.developer.config.DeveloperWebMvcConfig;
import com.developer.config.FunctionUnitWorkspaceAccessInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route guard: every path variable of every DW endpoint is either the function unit itself (and the interceptor reads
 * it from the path), a sub-resource whose owner {@link WorkspaceRouteOwnership} checks, or listed below with the reason
 * it needs no ownership check. A new endpoint with a new kind of id fails here until someone decides which it is.
 * Signing in is required for every route outside the security chain's public list ({@code SecurityConfig}).
 */
class WorkspaceRouteCoverageTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z]+)(?::[^}]*)?}");

    /** The unit id the interceptor authorizes directly; {@code id} only on unit routes. */
    private static final Set<String> UNIT = Set.of("functionUnitId", "id");
    private static final List<String> UNIT_ROUTES = List.of("/function-units/", "/export-import/function-units/");

    /** Routes whose {@code id} is not a function unit, and why they need no workspace check. */
    private static final Map<String, String> OTHER_ID_ROUTES = Map.of(
            "/icons/", "the icon library is shared by every team");

    /** Sub-resources whose owner is looked up, with a sample value of their id type. */
    private static final Map<String, String> CHECKED = Map.ofEntries(
            Map.entry("formId", "424242"), Map.entry("tableId", "424242"), Map.entry("actionId", "424242"),
            Map.entry("decisionId", "424242"), Map.entry("connectionId", "424242"), Map.entry("ruleId", "424242"),
            Map.entry("templateId", "424242"), Map.entry("viewId", "424242"), Map.entry("versionId", "424242"),
            Map.entry("bindingId", "424242"), Map.entry("sessionId", "00000000-0000-0000-0000-000000424242"),
            Map.entry("deploymentId", "dep-424242"));

    /** Path variables that need no ownership lookup, and why. */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("jobId", "AI Studio proposal jobs are read and cancelled only by the user who started them"),
            Map.entry("messageId", "AI Studio thread messages are read under the path unit and the current user"),
            Map.entry("startEventId", "BPMN element id, looked up together with the path unit"),
            Map.entry("type", "document type, looked up together with the path unit"),
            Map.entry("version", "document version number, looked up together with the path unit"),
            Map.entry("versionNumber", "version number, looked up together with the path unit"),
            Map.entry("componentId", "component inside the path form, whose owner is checked"),
            Map.entry("phase", "AI prompt templates are global, not function-unit data"),
            Map.entry("storedName", "uploaded files, not function-unit design data"),
            Map.entry("filename", "uploaded files, not function-unit design data"),
            Map.entry("businessUnitId", "member directory, not function-unit design data"),
            Map.entry("username", "member directory, not function-unit design data"),
            Map.entry("key", "the caller's own user preferences"));

    @Test
    void everyPathVariableIsClassified() {
        Set<String> unclassified = new TreeSet<>();
        for (String route : routes()) {
            for (String variable : variables(route)) {
                boolean unit = isUnitVariable(route, variable);
                boolean otherId = "id".equals(variable) && OTHER_ID_ROUTES.keySet().stream().anyMatch(route::startsWith);
                if (!unit && !otherId && !CHECKED.containsKey(variable) && !EXEMPT.containsKey(variable)) {
                    unclassified.add(variable + " in " + route);
                }
            }
        }
        assertThat(unclassified).as("classify these path variables in WorkspaceRouteCoverageTest").isEmpty();
    }

    /** A unit id in the path is the one the interceptor authorizes, on a route the interceptor is registered for. */
    @Test
    void theInterceptorReadsEveryUnitIdFromThePath() {
        AntPathMatcher matcher = new AntPathMatcher();
        List<String> missed = new ArrayList<>();
        for (String route : routes()) {
            for (String variable : variables(route)) {
                if (!isUnitVariable(route, variable)) {
                    continue;
                }
                String path = fill(route, variable, "424242");
                boolean registered = Arrays.stream(DeveloperWebMvcConfig.WORKSPACE_PATHS)
                        .anyMatch(pattern -> matcher.match(pattern, path));
                Long read = FunctionUnitWorkspaceAccessInterceptor.unitIdInPath(path);
                if (!registered || !Long.valueOf(424242L).equals(read)) {
                    missed.add(variable + " in " + route + (registered ? " (not read)" : " (not registered)"));
                }
            }
        }
        assertThat(missed).isEmpty();
    }

    private static boolean isUnitVariable(String route, String variable) {
        return "functionUnitId".equals(variable)
                || "id".equals(variable) && UNIT_ROUTES.stream().anyMatch(route::startsWith);
    }

    @Test
    void everyCheckedSubResourceIsLookedUp() {
        List<String> missed = new ArrayList<>();
        for (String route : routes()) {
            for (String variable : variables(route)) {
                if (!CHECKED.containsKey(variable)) {
                    continue;
                }
                String sample = CHECKED.get(variable);
                String path = fill(route, variable, sample);
                boolean lookedUp = WorkspaceRouteOwnership.lookups(path).stream()
                        .anyMatch(lookup -> lookup.id().equals(sample));
                if (!lookedUp) {
                    missed.add(variable + " in " + route);
                }
            }
        }
        assertThat(missed).as("add an owner route to WorkspaceRouteOwnership").isEmpty();
    }

    private static String fill(String route, String variable, String sample) {
        Matcher matcher = VARIABLE.matcher(route);
        StringBuilder path = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            matcher.appendReplacement(path, name.equals(variable) ? sample : "7");
        }
        matcher.appendTail(path);
        return path.toString();
    }

    private static List<String> variables(String route) {
        List<String> names = new ArrayList<>();
        Matcher matcher = VARIABLE.matcher(route);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static List<String> routes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> routes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.developer.controller")) {
            Class<?> type = load(candidate.getBeanClassName());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        routes.add(prefix + path);
                    }
                }
            }
        }
        assertThat(routes).hasSizeGreaterThan(150);
        return routes;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
