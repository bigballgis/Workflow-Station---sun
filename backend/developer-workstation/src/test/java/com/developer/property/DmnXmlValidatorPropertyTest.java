package com.developer.property;

import com.developer.dto.ValidationResult;
import com.developer.validation.DmnXmlValidator;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * DMN XML 验证器属性测试
 * Feature: dmn-decision-table-integration
 * Property 6: DMN XML 验证正确性
 *
 * Validates: Requirements 4.2, 4.3, 4.4, 4.5
 */
public class DmnXmlValidatorPropertyTest {

    private final DmnXmlValidator validator = new DmnXmlValidator();

    // ========== Property 6: DMN XML 验证正确性 ==========

    /**
     * Property 6a: Valid DMN XML passes validation
     * For any well-formed DMN XML with at least one decision table containing
     * input/output columns and a valid hit policy, validation should succeed.
     *
     * Validates: Requirements 4.2, 4.3, 4.4, 4.5
     */
    @Property(tries = 100)
    void validDmnXmlPassesValidation(
            @ForAll("validDmnXml") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid())
                .as("Valid DMN XML should pass validation")
                .isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    /**
     * Property 6b: XML without decision table fails validation
     * For any well-formed XML that does not contain a decisionTable element,
     * validation should return a NO_DECISION_TABLE error.
     *
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    void xmlWithoutDecisionTableFailsValidation(
            @ForAll("xmlWithoutDecisionTable") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("NO_DECISION_TABLE");
    }

    /**
     * Property 6c: Decision table missing input columns fails validation
     * For any DMN XML with a decision table that has no input columns,
     * validation should return a NO_INPUT_COLUMN error.
     *
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    void decisionTableWithoutInputFailsValidation(
            @ForAll("xmlWithoutInputColumn") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("NO_INPUT_COLUMN");
    }

    /**
     * Property 6d: Decision table missing output columns fails validation
     * For any DMN XML with a decision table that has no output columns,
     * validation should return a NO_OUTPUT_COLUMN error.
     *
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    void decisionTableWithoutOutputFailsValidation(
            @ForAll("xmlWithoutOutputColumn") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("NO_OUTPUT_COLUMN");
    }

    /**
     * Property 6e: Invalid hit policy fails validation
     * For any DMN XML with a hit policy not in the allowed set,
     * validation should return an INVALID_HIT_POLICY error.
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 100)
    void invalidHitPolicyFailsValidation(
            @ForAll("xmlWithInvalidHitPolicy") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("INVALID_HIT_POLICY");
    }

    /**
     * Property 6f: Non-XML strings fail validation
     * For any string that is not well-formed XML,
     * validation should return an INVALID_XML_FORMAT error.
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    void nonXmlStringFailsValidation(
            @ForAll("nonXmlStrings") String input) {

        ValidationResult result = validator.validate(input);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("INVALID_XML_FORMAT");
    }

    /**
     * Property 6g: Oversized XML fails validation
     * For any XML content exceeding 1MB, validation should return XML_TOO_LARGE error.
     *
     * Validates: Requirements 4.2 (size limit)
     */
    @Property(tries = 5)
    void oversizedXmlFailsValidation(
            @ForAll("oversizedXml") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("XML_TOO_LARGE");
    }

    /**
     * Property 6h: XXE attack attempts fail validation
     * For any XML containing DOCTYPE declarations (XXE vectors),
     * validation should reject the input.
     *
     * Validates: Requirements 4.2 (security)
     */
    @Property(tries = 100)
    void xxeAttemptsFailValidation(
            @ForAll("xxePayloads") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
    }

    /**
     * Property 6i: Invalid JUEL expressions fail validation
     * For any DMN XML containing input/output entries with unbalanced
     * JUEL expressions, validation should return INVALID_JUEL_EXPRESSION error.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    void invalidJuelExpressionsFailValidation(
            @ForAll("xmlWithInvalidJuelExpression") String dmnXml) {

        ValidationResult result = validator.validate(dmnXml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getCode)
                .contains("INVALID_JUEL_EXPRESSION");
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> validDmnXml() {
        return Combinators.combine(
                validDecisionKeys(),
                validHitPolicies(),
                Arbitraries.integers().between(1, 5),
                Arbitraries.integers().between(1, 3),
                Arbitraries.integers().between(0, 5)
        ).as(DmnXmlValidatorPropertyTest::buildValidDmnXml);
    }

    @Provide
    Arbitrary<String> xmlWithoutDecisionTable() {
        return validDecisionKeys().map(key ->
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"" +
                " id=\"definitions\" name=\"definitions\"" +
                " namespace=\"http://camunda.org/schema/1.0/dmn\">\n" +
                "  <decision id=\"" + key + "\" name=\"test\">\n" +
                "  </decision>\n" +
                "</definitions>");
    }

    @Provide
    Arbitrary<String> xmlWithoutInputColumn() {
        return Combinators.combine(
                validDecisionKeys(),
                validHitPolicies()
        ).as((key, hitPolicy) ->
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"" +
                " id=\"definitions\" name=\"definitions\"" +
                " namespace=\"http://camunda.org/schema/1.0/dmn\">\n" +
                "  <decision id=\"" + key + "\" name=\"test\">\n" +
                "    <decisionTable id=\"dt_1\" hitPolicy=\"" + hitPolicy + "\">\n" +
                "      <output id=\"output_1\" label=\"Result\" name=\"result\" typeRef=\"string\" />\n" +
                "    </decisionTable>\n" +
                "  </decision>\n" +
                "</definitions>");
    }

    @Provide
    Arbitrary<String> xmlWithoutOutputColumn() {
        return Combinators.combine(
                validDecisionKeys(),
                validHitPolicies()
        ).as((key, hitPolicy) ->
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"" +
                " id=\"definitions\" name=\"definitions\"" +
                " namespace=\"http://camunda.org/schema/1.0/dmn\">\n" +
                "  <decision id=\"" + key + "\" name=\"test\">\n" +
                "    <decisionTable id=\"dt_1\" hitPolicy=\"" + hitPolicy + "\">\n" +
                "      <input id=\"input_1\" label=\"Amount\">\n" +
                "        <inputExpression id=\"ie_1\" typeRef=\"double\">\n" +
                "          <text>amount</text>\n" +
                "        </inputExpression>\n" +
                "      </input>\n" +
                "    </decisionTable>\n" +
                "  </decision>\n" +
                "</definitions>");
    }

    @Provide
    Arbitrary<String> xmlWithInvalidHitPolicy() {
        return Combinators.combine(
                validDecisionKeys(),
                invalidHitPolicies()
        ).as((key, hitPolicy) -> buildDmnXmlWithHitPolicy(key, hitPolicy));
    }

    @Provide
    Arbitrary<String> nonXmlStrings() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
                Arbitraries.of(
                        "not xml at all",
                        "<unclosed tag",
                        "{ \"json\": true }",
                        "<root><child></root>",
                        "<<<>>>",
                        "<a><b></a></b>"
                )
        );
    }

    @Provide
    Arbitrary<String> oversizedXml() {
        return Arbitraries.integers().between(1_048_577, 1_200_000).map(size -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>");
            while (sb.length() < size) {
                sb.append("<data>padding content to exceed 1MB limit</data>\n");
            }
            sb.append("</root>");
            return sb.toString();
        });
    }

    @Provide
    Arbitrary<String> xxePayloads() {
        return Arbitraries.of(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\">" +
                "<decision id=\"d1\" name=\"test\">&xxe;</decision></definitions>",

                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY xxe SYSTEM \"http://evil.com/data\">\n" +
                "]>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\">" +
                "<decision id=\"d1\" name=\"test\">&xxe;</decision></definitions>",

                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ELEMENT foo ANY>\n" +
                "  <!ENTITY xxe SYSTEM \"file:///dev/random\">\n" +
                "]>\n" +
                "<root>&xxe;</root>"
        );
    }

    @Provide
    Arbitrary<String> xmlWithInvalidJuelExpression() {
        return Combinators.combine(
                validDecisionKeys(),
                validHitPolicies(),
                invalidJuelExpressions()
        ).as((key, hitPolicy, expr) -> buildDmnXmlWithExpression(key, hitPolicy, expr));
    }

    // ========== Helper Arbitraries ==========

    private Arbitrary<String> validDecisionKeys() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(s -> "dk_" + s);
    }

    private Arbitrary<String> validHitPolicies() {
        return Arbitraries.of("FIRST", "UNIQUE", "ANY", "PRIORITY",
                "COLLECT", "RULE_ORDER", "OUTPUT_ORDER");
    }

    private Arbitrary<String> invalidHitPolicies() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                        .filter(s -> !java.util.Set.of(
                                "FIRST", "UNIQUE", "ANY", "PRIORITY",
                                "COLLECT", "RULE_ORDER", "OUTPUT_ORDER"
                        ).contains(s)),
                Arbitraries.of("INVALID", "NONE", "ALL", "RANDOM", "first", "unique")
        );
    }

    private Arbitrary<String> invalidJuelExpressions() {
        return Arbitraries.of(
                "${unclosed",
                "#{unclosed",
                "${a} ${b",
                "(((",
                "((a + b)",
                "func(a, b",
                "${expr1} #{expr2"
        );
    }

    // ========== XML Builder Helpers ==========

    private static String buildValidDmnXml(String key, String hitPolicy,
                                           int inputCount, int outputCount, int ruleCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"");
        xml.append(" id=\"definitions\" name=\"definitions\"");
        xml.append(" namespace=\"http://camunda.org/schema/1.0/dmn\">\n");
        xml.append("  <decision id=\"").append(key).append("\" name=\"Test Decision\">\n");
        xml.append("    <decisionTable id=\"dt_1\" hitPolicy=\"").append(hitPolicy).append("\">\n");

        for (int i = 1; i <= inputCount; i++) {
            xml.append("      <input id=\"input_").append(i).append("\" label=\"Input ").append(i).append("\">\n");
            xml.append("        <inputExpression id=\"ie_").append(i).append("\" typeRef=\"string\">\n");
            xml.append("          <text>var").append(i).append("</text>\n");
            xml.append("        </inputExpression>\n");
            xml.append("      </input>\n");
        }

        for (int i = 1; i <= outputCount; i++) {
            xml.append("      <output id=\"output_").append(i).append("\"");
            xml.append(" label=\"Output ").append(i).append("\"");
            xml.append(" name=\"out").append(i).append("\"");
            xml.append(" typeRef=\"string\" />\n");
        }

        for (int r = 1; r <= ruleCount; r++) {
            xml.append("      <rule id=\"rule_").append(r).append("\">\n");
            for (int i = 1; i <= inputCount; i++) {
                xml.append("        <inputEntry id=\"ie_").append(r).append("_").append(i).append("\">\n");
                xml.append("          <text>\"value").append(r).append("\"</text>\n");
                xml.append("        </inputEntry>\n");
            }
            for (int o = 1; o <= outputCount; o++) {
                xml.append("        <outputEntry id=\"oe_").append(r).append("_").append(o).append("\">\n");
                xml.append("          <text>\"result").append(r).append("\"</text>\n");
                xml.append("        </outputEntry>\n");
            }
            xml.append("      </rule>\n");
        }

        xml.append("    </decisionTable>\n");
        xml.append("  </decision>\n");
        xml.append("</definitions>");
        return xml.toString();
    }

    private static String buildDmnXmlWithHitPolicy(String key, String hitPolicy) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"" +
                " id=\"definitions\" name=\"definitions\"" +
                " namespace=\"http://camunda.org/schema/1.0/dmn\">\n" +
                "  <decision id=\"" + key + "\" name=\"test\">\n" +
                "    <decisionTable id=\"dt_1\" hitPolicy=\"" + hitPolicy + "\">\n" +
                "      <input id=\"input_1\" label=\"Amount\">\n" +
                "        <inputExpression id=\"ie_1\" typeRef=\"double\">\n" +
                "          <text>amount</text>\n" +
                "        </inputExpression>\n" +
                "      </input>\n" +
                "      <output id=\"output_1\" label=\"Result\" name=\"result\" typeRef=\"string\" />\n" +
                "    </decisionTable>\n" +
                "  </decision>\n" +
                "</definitions>";
    }

    private static String buildDmnXmlWithExpression(String key, String hitPolicy, String expression) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"" +
                " id=\"definitions\" name=\"definitions\"" +
                " namespace=\"http://camunda.org/schema/1.0/dmn\">\n" +
                "  <decision id=\"" + key + "\" name=\"test\">\n" +
                "    <decisionTable id=\"dt_1\" hitPolicy=\"" + hitPolicy + "\">\n" +
                "      <input id=\"input_1\" label=\"Amount\">\n" +
                "        <inputExpression id=\"ie_1\" typeRef=\"double\">\n" +
                "          <text>amount</text>\n" +
                "        </inputExpression>\n" +
                "      </input>\n" +
                "      <output id=\"output_1\" label=\"Result\" name=\"result\" typeRef=\"string\" />\n" +
                "      <rule id=\"rule_1\">\n" +
                "        <inputEntry id=\"ie_1_1\">\n" +
                "          <text>" + escapeXmlContent(expression) + "</text>\n" +
                "        </inputEntry>\n" +
                "        <outputEntry id=\"oe_1_1\">\n" +
                "          <text>\"ok\"</text>\n" +
                "        </outputEntry>\n" +
                "      </rule>\n" +
                "    </decisionTable>\n" +
                "  </decision>\n" +
                "</definitions>";
    }

    private static String escapeXmlContent(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
