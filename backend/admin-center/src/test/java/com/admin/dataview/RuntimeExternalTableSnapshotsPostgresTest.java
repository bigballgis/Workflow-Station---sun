package com.admin.dataview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class RuntimeExternalTableSnapshotsPostgresTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper();
    private RuntimeExternalTableSnapshots snapshots;
    @BeforeEach void database() {
        var source=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        jdbc=new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS ac_external_table_snapshots,sys_function_unit_contents,sys_function_units CASCADE");
        jdbc.execute("CREATE TABLE sys_function_units(id VARCHAR(64) PRIMARY KEY,code TEXT,status TEXT,enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_function_unit_contents(id TEXT PRIMARY KEY,function_unit_id TEXT,content_type TEXT,content_data TEXT)");
        try {
            String script=java.nio.file.Files.readString(java.nio.file.Path.of("../../deploy/init-scripts/00-schema/111-external-table-snapshots.sql"));
            jdbc.execute(script);
            jdbc.execute(script);
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        snapshots=new RuntimeExternalTableSnapshots(jdbc,mapper);
    }
    private void table(String catalog,String code,long id,String name,List<Map<String,Object>> fields,boolean enabled) throws Exception {
        jdbc.update("INSERT INTO sys_function_units VALUES(?,?,'DEPLOYED',?)",catalog,code,enabled);
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES(?,?,'DATA_TABLE',?)",catalog+"table",catalog,
                mapper.writeValueAsString(Map.of("tableId",id,"tableName",name,"tableType","MAIN","fields",fields)));
    }
    private List<Map<String,Object>> refs() {
        return List.of(Map.of("fieldName","customer_ref","isForeignKey",true,"refFunctionUnitCode","customers",
                "refTableName","customers","refPrimaryKeyFields",List.of("customer_key")));
    }
    private Object parentId(String catalog) {
        var form=Map.<String,Object>of("tableBindings",List.of(Map.of("bindingType","PRIMARY","tableName","orders")));
        var bindings=new CatalogSnapshotReader(jdbc,mapper).formBindings(catalog,form);
        return CatalogSnapshotReader.objects(bindings.get(0).get("fieldDefinitions")).get(0).get("refTableId");
    }
    @ParameterizedTest @ValueSource(strings={"native-child","imported-child"})
    void capturedMetadataSurvivesUpgradeDisableAndDeletion(String code) throws Exception {
        table("parent-v1","customers",41,"customers",List.of(),true);
        table("child-v1",code,2,"orders",refs(),true);
        snapshots.capture("child-v1");
        assertThat(parentId("child-v1")).isEqualTo(41);
        jdbc.update("UPDATE sys_function_units SET enabled=FALSE WHERE id='parent-v1'");
        table("parent-v2","customers",99,"customers",List.of(),true);
        snapshots.capture("child-v1");
        assertThat(parentId("child-v1")).isEqualTo(41);
        table("child-v2",code,3,"orders",refs(),true);
        snapshots.capture("child-v2");
        assertThat(parentId("child-v2")).isEqualTo(99);
        jdbc.update("UPDATE sys_function_units SET enabled=FALSE WHERE code='customers'");
        jdbc.update("DELETE FROM sys_function_units WHERE code='customers'");
        assertThat(parentId("child-v1")).isEqualTo(41);
        assertThat(parentId("child-v2")).isEqualTo(99);
        assertThatThrownBy(() -> jdbc.update("UPDATE ac_external_table_snapshots SET definition='{}'::jsonb"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void unavailableDependencyRejectsImportInsteadOfFollowingAnArbitraryRelease() throws Exception {
        table("parent-v1","customers",41,"customers",List.of(),false);
        table("child-v1","orders",2,"orders",refs(),true);
        assertThatThrownBy(() -> snapshots.capture("child-v1"))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ac_external_table_snapshots",Long.class)).isZero();
    }
}
