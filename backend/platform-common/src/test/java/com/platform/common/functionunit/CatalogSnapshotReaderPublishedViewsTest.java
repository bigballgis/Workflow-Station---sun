package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Main Table Views of a release, named by View name (unique within a function unit). */
class CatalogSnapshotReaderPublishedViewsTest {

    private static final String CATALOG = "cat-views";

    private static CatalogSnapshotReader readerWith(String viewsJson) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(CATALOG), eq("MAIN_TABLE_VIEW")))
                .thenReturn(viewsJson == null ? List.of() : List.of(viewsJson));
        return new CatalogSnapshotReader(jdbc, new ObjectMapper());
    }

    @Test
    void draftsAreLeftOutAndAViewIsFoundByItsName() {
        CatalogSnapshotReader reader = readerWith("""
                [{"viewName":"Open cases","mainTableName":"cases","status":"PUBLISHED"},
                 {"viewName":"Work in progress","mainTableName":"cases","status":"DRAFT"},
                 {"viewName":"Lines","mainTableName":"lines","status":"PUBLISHED"}]""");

        assertThat(reader.publishedViews(CATALOG)).extracting(view -> view.get("viewName"))
                .containsExactly("Open cases", "Lines");
        assertThat(reader.publishedView(CATALOG, "Lines")).get()
                .satisfies(view -> assertThat(view.get("mainTableName")).isEqualTo("lines"));
        assertThat(reader.publishedView(CATALOG, "Work in progress")).isEmpty();
    }

    @Test
    void aReleaseWithoutViewsPublishesNone() {
        assertThat(readerWith(null).publishedViews(CATALOG)).isEmpty();
    }

    @Test
    void twoPublishedViewsOfOneNameMakeTheReleaseInvalid() {
        CatalogSnapshotReader reader = readerWith("""
                [{"viewName":"Open cases","mainTableName":"cases","status":"PUBLISHED"},
                 {"viewName":"Open cases","mainTableName":"lines","status":"PUBLISHED"}]""");

        assertThatThrownBy(() -> reader.publishedView(CATALOG, "Open cases"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test
    void aPublishedViewWithoutANameMakesTheReleaseInvalid() {
        CatalogSnapshotReader reader = readerWith("""
                [{"mainTableName":"cases","status":"PUBLISHED"}]""");

        assertThatThrownBy(() -> reader.publishedViews(CATALOG)).isInstanceOf(FunctionUnitVersionException.class);
    }
}
