package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.service.impl.AiValidationServiceImpl;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiValidationService SVG security validation.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 5: SVG 安全校验")
class AiSvgValidationProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    /**
     * Property 5a: SVGs containing script tags should produce SVG_VALIDATION errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void svgWithScriptTagShouldError(
            @ForAll("safeShapeContent") String shapeContent) {

        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + shapeContent
                + "<script>alert('xss')</script></svg>";

        AiGeneratedData data = buildIconData(svg);
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "SVG_VALIDATION".equals(e.getErrorType())
                        && e.getDescription().contains("script")))
                .isTrue();
    }

    /**
     * Property 5b: SVGs containing on* event attributes should produce SVG_VALIDATION errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void svgWithEventAttributeShouldError(
            @ForAll("eventAttributeName") String eventAttr) {

        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<rect " + eventAttr + "=\"alert('xss')\" />"
                + "</svg>";

        AiGeneratedData data = buildIconData(svg);
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "SVG_VALIDATION".equals(e.getErrorType())
                        && e.getDescription().contains("事件属性")))
                .isTrue();
    }

    /**
     * Property 5c: SVGs containing javascript: protocol should produce SVG_VALIDATION errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void svgWithJavascriptProtocolShouldError(
            @ForAll("safeShapeContent") String shapeContent) {

        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<a href=\"javascript:alert('xss')\">" + shapeContent + "</a>"
                + "</svg>";

        AiGeneratedData data = buildIconData(svg);
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "SVG_VALIDATION".equals(e.getErrorType())
                        && e.getDescription().contains("javascript:")))
                .isTrue();
    }

    /**
     * Property 5d: SVGs exceeding 10KB should produce SVG_VALIDATION errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void svgExceeding10KBShouldError(
            @ForAll("oversizedPadding") String padding) {

        // Build an SVG that exceeds 10KB
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><!-- " + padding + " --><rect/></svg>";

        // Ensure it's actually over 10KB
        Assume.that(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 10240);

        AiGeneratedData data = buildIconData(svg);
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "SVG_VALIDATION".equals(e.getErrorType())
                        && e.getDescription().contains("10KB")))
                .isTrue();
    }

    /**
     * Property 5e: Non-svg root element should produce SVG_VALIDATION errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void nonSvgRootElementShouldError(
            @ForAll("nonSvgRootTag") String rootTag) {

        String xml = "<" + rootTag + "><rect/></" + rootTag + ">";

        AiGeneratedData data = buildIconData(xml);
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "SVG_VALIDATION".equals(e.getErrorType())))
                .isTrue();
    }

    /**
     * Property 5f: Valid safe SVGs should produce no SVG_VALIDATION errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void validSafeSvgShouldPass(
            @ForAll("safeShapeContent") String shapeContent) {

        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">"
                + shapeContent
                + "</svg>";

        AiGeneratedData data = buildIconData(svg);
        AiValidationResult result = validationService.validate(data);

        long svgErrors = result.getErrors().stream()
                .filter(e -> "SVG_VALIDATION".equals(e.getErrorType()))
                .count();
        assertThat(svgErrors).isZero();
    }

    // --- Helpers ---

    private AiGeneratedData buildIconData(String svgContent) {
        return AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "test-icon",
                        "category", "GENERAL",
                        "svgContent", svgContent
                ))
                .build();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> safeShapeContent() {
        return Arbitraries.of(
                "<rect width=\"10\" height=\"10\"/>",
                "<circle cx=\"12\" cy=\"12\" r=\"5\"/>",
                "<line x1=\"0\" y1=\"0\" x2=\"24\" y2=\"24\"/>",
                "<path d=\"M12 2L2 22h20z\"/>",
                "<polyline points=\"1,1 5,5 9,1\"/>",
                "<ellipse cx=\"12\" cy=\"12\" rx=\"8\" ry=\"5\"/>"
        );
    }

    @Provide
    Arbitrary<String> eventAttributeName() {
        return Arbitraries.of("onclick", "onload", "onmouseover", "onfocus", "onerror", "onanimationend");
    }

    @Provide
    Arbitrary<String> oversizedPadding() {
        // Generate a string that when embedded in SVG will exceed 10KB
        return Arbitraries.strings().alpha().ofMinLength(10241).ofMaxLength(15000);
    }

    @Provide
    Arbitrary<String> nonSvgRootTag() {
        return Arbitraries.of("div", "html", "body", "span", "p", "xml", "root", "document");
    }
}
