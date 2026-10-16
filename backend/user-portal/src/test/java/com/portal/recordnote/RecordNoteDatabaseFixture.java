package com.portal.recordnote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.entity.Notification;
import com.portal.entity.NotificationPreference;
import com.portal.entity.RecordNote;
import com.portal.repository.*;
import com.portal.service.*;
import com.portal.service.impl.RecordNoteServiceImpl;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;
import static org.mockito.Mockito.mock;

/** Minimal real PostgreSQL + Hibernate context, isolated from application/Kafka startup. */
@Configuration
@EnableTransactionManagement(proxyTargetClass=true)
class RecordNoteDatabaseFixture {
    static String schema;
    @Bean DataSource dataSource() {
        String base=System.getenv("NOTE_TEST_JDBC_URL");
        return new DriverManagerDataSource(base+(base.contains("?")?"&":"?")+"currentSchema="+schema,
                System.getenv("NOTE_TEST_JDBC_USER"),System.getenv("NOTE_TEST_JDBC_PASSWORD"));
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
        var factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setManagedTypes(PersistenceManagedTypes.of(RecordNote.class.getName(),Notification.class.getName(),
                NotificationPreference.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","update","hibernate.show_sql","false"));
        return factory;
    }
    @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
    @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    private <T> T repository(EntityManagerFactory factory,Class<T> type) {
        return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(type);
    }
    @Bean RecordNoteRepository noteRepository(EntityManagerFactory f) { return repository(f,RecordNoteRepository.class); }
    @Bean NotificationRepository notificationRepository(EntityManagerFactory f) { return repository(f,NotificationRepository.class); }
    @Bean NotificationPreferenceRepository preferenceRepository(EntityManagerFactory f) { return repository(f,NotificationPreferenceRepository.class); }
    @Bean RecordNoteService noteService(RecordNoteRepository r) { return new RecordNoteServiceImpl(r); }
    @Bean NotificationService notificationService(NotificationRepository r,NotificationPreferenceRepository p) {
        return new NotificationServiceImpl(r,p);
    }
    @Bean RecordNoteSubmissionRepository submissions(JdbcTemplate jdbc,ObjectMapper mapper) { return new RecordNoteSubmissionRepository(jdbc,mapper); }
    @Bean RecordNoteCatalogRepository catalog(JdbcTemplate jdbc,ObjectMapper mapper) { return new RecordNoteCatalogRepository(jdbc,mapper); }
    @Bean RecordNoteSubmissionComponent submit(RecordNoteService n,RecordNoteSubmissionRepository r,RecordNoteCatalogRepository c) {
        return new RecordNoteSubmissionComponent(n,r,c);
    }
    @Bean RecordNoteDeliveryRepository deliveries(JdbcTemplate jdbc,RecordNoteSubmissionRepository s) { return new RecordNoteDeliveryRepository(jdbc,s); }
    @Bean RecordNoteRecipientAccess access() {
        var access=mock(RecordNoteRecipientAccess.class);
        org.mockito.Mockito.when(access.allowed(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anySet()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return access;
    }
    @Bean org.springframework.context.MessageSource messageSource() {
        var source=new org.springframework.context.support.ResourceBundleMessageSource();
        source.setBasename("i18n/messages"); source.setDefaultEncoding("UTF-8"); return source;
    }
    @Bean RecordNoteEngineClient engine() { return mock(RecordNoteEngineClient.class); }
    @Bean RecordNoteDeliveryComponent delivery(RecordNoteSubmissionRepository s,RecordNoteDeliveryRepository d,
            RecordNoteCatalogRepository c,RecordNoteEngineClient e,NotificationService n,JdbcTemplate jdbc,RecordNoteRecipientAccess a,org.springframework.context.MessageSource m) {
        return new RecordNoteDeliveryComponent(s,d,c,e,n,jdbc,a,m);
    }
}
