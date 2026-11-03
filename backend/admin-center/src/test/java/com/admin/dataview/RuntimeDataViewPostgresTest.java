package com.admin.dataview;

import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRoleRepository;
import com.admin.service.UserBusinessUnitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the real migration, releases and binding under a role denied every designer table. */
@Testcontainers(disabledWithoutDocker=true)
class RuntimeDataViewPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void deployedDirectoryAndBiSurviveUpgradeWithoutDesignerPrivileges() throws Exception {
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())) {
            var source=new SingleConnectionDataSource(connection,true);
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE sys_function_units(id VARCHAR(64) PRIMARY KEY,code TEXT,name TEXT,version TEXT,status TEXT,enabled BOOLEAN,icon_svg TEXT);
                    CREATE TABLE sys_function_unit_contents(id TEXT PRIMARY KEY,function_unit_id TEXT,content_type TEXT,content_data TEXT);
                    CREATE TABLE sys_roles(id TEXT,code TEXT);
                    CREATE TABLE sys_function_unit_access(function_unit_id TEXT,target_type TEXT,target_id TEXT);
                    CREATE TABLE dw_pk_sequences(table_id BIGINT,field_name TEXT,scope_type TEXT,scope_key TEXT,prefix TEXT,pad_width INTEGER,current_value BIGINT);
                    CREATE TABLE dw_function_units(id BIGINT PRIMARY KEY,code TEXT,name TEXT);
                    CREATE TABLE dw_table_definitions(id BIGINT PRIMARY KEY,function_unit_id BIGINT,table_name TEXT,table_display_name TEXT,table_type TEXT);
                    CREATE TABLE dw_main_table_view_configs(id BIGINT PRIMARY KEY,function_unit_id BIGINT,main_table_id BIGINT,view_name TEXT);
                    CREATE TABLE bi_data_view_assignment(id TEXT PRIMARY KEY,dashboard_id TEXT,function_unit_id BIGINT REFERENCES dw_function_units(id) ON DELETE CASCADE,table_id BIGINT,view_id BIGINT,enabled BOOLEAN,created_at TIMESTAMP,updated_at TIMESTAMP);
                    CREATE TABLE bi_dashboard_registry(id TEXT PRIMARY KEY,dashboard_title TEXT);
                    INSERT INTO dw_pk_sequences VALUES(1,'case_key','perTable','','Case-',6,7);
                    INSERT INTO dw_function_units VALUES(80,'FU','Designer FU');
                    INSERT INTO dw_table_definitions VALUES(90,80,'cases','Cases','MAIN');
                    INSERT INTO dw_main_table_view_configs VALUES(100,80,90,'Old');
                    INSERT INTO bi_dashboard_registry VALUES('dashboard','Dashboard');
                    INSERT INTO bi_data_view_assignment VALUES('binding','dashboard',80,90,100,TRUE,now(),now());
                    INSERT INTO sys_roles VALUES('role','READER');
                    """);
            var migration=Path.of("../../deploy/init-scripts/00-schema/109-admin-runtime-data-views.sql");
            new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
            jdbc.execute(java.nio.file.Files.readString(Path.of("../../deploy/init-scripts/00-schema/110-bi-runtime-target-independence.sql")));
            // The migration is repeatable and must not recreate identities or rewrite already migrated grants.
            new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
            jdbc.execute(java.nio.file.Files.readString(Path.of("../../deploy/init-scripts/00-schema/110-bi-runtime-target-independence.sql")));
            String key=jdbc.queryForObject("SELECT view_key FROM dw_main_table_view_configs WHERE id=100",String.class);
            var mapper=new ObjectMapper();
            var index=new RuntimeDataViewIndex(jdbc,mapper,new RuntimeExternalTableSnapshots(jdbc,mapper));
            insertRelease(jdbc,mapper,"v1",key,"Old",true);
            insertRelease(jdbc,mapper,"v2",key,"Renamed",false);
            index.index("v1");index.index("v2");
            Long id=jdbc.queryForObject("SELECT view_id FROM ac_current_data_views",Long.class);
            assertThat(jdbc.queryForObject("SELECT runtime_target FROM bi_data_view_assignment WHERE id='binding'",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT view_id FROM bi_data_view_assignment WHERE id='binding'",Long.class)).isEqualTo(id);
            jdbc.update("UPDATE sys_function_units SET enabled=(id='v2')");
            assertThat(jdbc.queryForObject("SELECT view_id FROM ac_current_data_views",Long.class)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT view_name FROM ac_current_data_views",String.class)).isEqualTo("Renamed");

            jdbc.execute("CREATE ROLE runtime_data_view NOLOGIN");
            jdbc.execute("GRANT USAGE ON SCHEMA public TO runtime_data_view");
            jdbc.execute("GRANT SELECT ON ac_current_data_views,sys_function_unit_contents,sys_roles,bi_data_view_assignment,bi_dashboard_registry TO runtime_data_view");
            jdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ac_runtime_pk_sequences TO runtime_data_view");
            jdbc.execute("GRANT USAGE ON SEQUENCE ac_runtime_pk_sequences_id_seq TO runtime_data_view");
            jdbc.execute("SET ROLE runtime_data_view");
            var allocator=new com.platform.common.fk.JdbcPrimaryKeyAllocationService(jdbc);
            var config=new com.platform.common.dto.PkGenerationConfig();
            config.setStrategy("prefixedSequence");config.setPrefix("Case-");config.setPadWidth(6);
            assertThat(allocator.allocate(1L,"case_key",config,1,"","ac_runtime_pk_sequences")).containsExactly("Case-000008");
            assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM dw_main_table_view_configs"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM sys_function_unit_access"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            var userRoles=mock(UserRoleRepository.class);var roles=mock(RoleRepository.class);var units=mock(UserBusinessUnitService.class);
            when(userRoles.findEffectiveViewRoleIdsByUserId("reader")).thenReturn(List.of("role"));
            when(roles.findAllById(List.of("role"))).thenReturn(List.of(Role.builder().id("role").code("READER").type("BU_BOUNDED").status("ACTIVE").build()));
            when(units.getUserBusinessUnitIds("reader")).thenReturn(List.of("bu"));
            var snapshots=new CatalogSnapshotReader(jdbc,mapper);
            var catalog=new RuntimeDataViewCatalog(jdbc,snapshots,new RuntimeDataViewAccess(userRoles,roles,units));
            assertThat(catalog.require("reader",id).definition()).containsEntry("viewName","Renamed");
            var metadata=new BiDataViewMetadataRepository(jdbc,snapshots);
            assertThat(metadata.responses(List.of("binding"))).singleElement().satisfies(binding -> {
                assertThat(binding.getViewId()).isEqualTo(id);assertThat(binding.getViewAvailable()).isTrue();
                assertThat(binding.getViewName()).isEqualTo("Renamed");
            });
            jdbc.execute("RESET ROLE");
            jdbc.update("DELETE FROM dw_function_units WHERE id=80");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bi_data_view_assignment WHERE id='binding'",Long.class)).isEqualTo(1L);
            jdbc.update("UPDATE sys_function_units SET enabled=FALSE");
            assertThat(catalog.list("reader",false)).isEmpty();
        }
    }
    private void insertRelease(JdbcTemplate jdbc,ObjectMapper mapper,String catalog,String key,String name,boolean enabled) throws Exception {
        jdbc.update("INSERT INTO sys_function_units VALUES(?, 'FU','Published FU',?,'DEPLOYED',?,NULL)",catalog,catalog,enabled);
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES(?,?,'DATA_TABLE',?)",catalog+"table",catalog,
                mapper.writeValueAsString(Map.of("tableId",1,"tableName","cases","tableType","MAIN","fields",List.of())));
        var view=Map.of("viewKey",key,"mainTableName","cases","viewName",name,"status","PUBLISHED","isDefault",true,
                "fields",List.of(),"accessRules",List.of(Map.of("targetType","BUSINESS_UNIT","targetId","bu"),Map.of("targetType","ROLE","targetId","role")));
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES(?,?,'MAIN_TABLE_VIEW',?)",catalog+"views",catalog,mapper.writeValueAsString(List.of(view)));
    }
}
