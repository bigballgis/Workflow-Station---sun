package com.portal.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.list.ListColumnMeta;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MainTableViewSelectDisplayTest {

    private static final List<ListColumnMeta.Option> YES_NO = List.of(
            new ListColumnMeta.Option("1", "Y"),
            new ListColumnMeta.Option("2", "N"));

    private static MainTableViewFieldColumn column(String selectDisplay, List<ListColumnMeta.Option> options) {
        return MainTableViewFieldColumn.builder()
                .fieldName("merchant_credit")
                .displayLabel("Merchant Credit")
                .selectDisplay(selectDisplay)
                .selectOptions(options)
                .build();
    }

    @Test
    void labelModeMapsStoredValuesToOptionLabels() {
        MainTableViewFieldColumn col = column("label", YES_NO);
        assertThat(MainTableViewSelectDisplay.display(col, "1")).isEqualTo("Y");
        assertThat(MainTableViewSelectDisplay.display(col, 2)).isEqualTo("N");
        assertThat(MainTableViewSelectDisplay.display(col, List.of("1", "2"))).isEqualTo(List.of("Y", "N"));
    }

    @Test
    void valueOutsideOptionsAndNullStayAsStored() {
        MainTableViewFieldColumn col = column("label", YES_NO);
        assertThat(MainTableViewSelectDisplay.display(col, "9")).isEqualTo("9");
        assertThat(MainTableViewSelectDisplay.display(col, null)).isNull();
    }

    @Test
    void valueModeOrMissingOptionsLeaveTheCellUntouched() {
        assertThat(MainTableViewSelectDisplay.display(column("value", YES_NO), "1")).isEqualTo("1");
        assertThat(MainTableViewSelectDisplay.display(column("label", null), "1")).isEqualTo("1");
        assertThat(MainTableViewSelectDisplay.display(column(null, YES_NO), "1")).isEqualTo("1");
    }

    @Test
    void bindingScopingReadsTopLevelRulesForPrimaryAndSubFormsForSubBindings() throws Exception {
        String config = """
                {"rule":[{"type":"select","field":"status","options":[{"label":"Open","value":"O"}]}],
                 "subForms":{"527":[{"type":"select","field":"status","options":[{"label":"Posted","value":"P"}]},
                                    {"type":"select","field":"case_type","options":[{"label":"CNP","value":"1"}]}]}}
                """;
        JsonNode cfg = new ObjectMapper().readTree(config);

        Map<String, List<ListColumnMeta.Option>> main = new LinkedHashMap<>();
        MainTableViewSelectDisplay.collectOptionsForBinding(cfg, 521L, true, main);
        assertThat(main).containsOnlyKeys("status");
        assertThat(main.get("status")).containsExactly(new ListColumnMeta.Option("O", "Open"));

        Map<String, List<ListColumnMeta.Option>> sub = new LinkedHashMap<>();
        MainTableViewSelectDisplay.collectOptionsForBinding(cfg, 527L, false, sub);
        assertThat(sub).containsOnlyKeys("status", "case_type");
        assertThat(sub.get("status")).containsExactly(new ListColumnMeta.Option("P", "Posted"));

        Map<String, List<ListColumnMeta.Option>> other = new LinkedHashMap<>();
        MainTableViewSelectDisplay.collectOptionsForBinding(cfg, 999L, false, other);
        assertThat(other).isEmpty();
    }

    @Test
    void collectsStaticOptionsPerBoundFieldAndSkipsRuntimeFetchedWidgets() throws Exception {
        String config = """
                {"rule":[
                  {"type":"select","field":"merchant_credit","options":[{"label":"Y","value":"1"},{"label":"N","value":"2"}]},
                  {"type":"radio","field":"channel","options":[{"label":"Web","value":"W"},{"value":"M"}]},
                  {"type":"select","field":"branch","options":[],"effect":{"fetch":"/api/branches"}},
                  {"type":"input","field":"remark"},
                  {"type":"group","children":[
                    {"type":"select","field":"merchant_credit","options":[{"label":"Other","value":"1"}]}
                  ]}
                ]}
                """;
        Map<String, List<ListColumnMeta.Option>> out = new LinkedHashMap<>();
        MainTableViewSelectDisplay.collectStaticOptions(new ObjectMapper().readTree(config), out);

        assertThat(out).containsOnlyKeys("merchant_credit", "channel");
        assertThat(out.get("merchant_credit")).containsExactly(
                new ListColumnMeta.Option("1", "Y"), new ListColumnMeta.Option("2", "N"));
        // A label-less option shows its value; the first declaration of a field wins.
        assertThat(out.get("channel")).containsExactly(
                new ListColumnMeta.Option("W", "Web"), new ListColumnMeta.Option("M", "M"));
    }
}
