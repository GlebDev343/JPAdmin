package by.glebka.jpadmin.config;

import by.glebka.jpadmin.service.EntityTableService;
import by.glebka.jpadmin.service.TimeFormatService;
import by.glebka.jpadmin.service.record.FieldUtils;
import by.glebka.jpadmin.service.record.FieldValueSetter;
import by.glebka.jpadmin.service.record.QueryBuilder;
import by.glebka.jpadmin.service.record.RecordDetailsFetcher;
import by.glebka.jpadmin.service.record.RecordDetailsService;
import by.glebka.jpadmin.service.record.RecordListService;
import by.glebka.jpadmin.service.record.RecordPersister;
import by.glebka.jpadmin.service.record.RecordValidator;
import by.glebka.jpadmin.exception.GlobalExceptionHandler;
import by.glebka.jpadmin.scanner.AnnotationCollector;
import by.glebka.jpadmin.scanner.ClassScanner;
import by.glebka.jpadmin.scanner.MetamodelAnalyzer;
import by.glebka.jpadmin.controller.AdminController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "jpadmin.enabled", havingValue = "true", matchIfMissing = true)
public class JpaAdminAutoConfiguration {

    @Value("${jpadmin.base-package}")
    private String basePackage;

    @Bean
    public ClassScanner classScanner() {
        return new ClassScanner(basePackage);
    }

    @Bean
    public AnnotationCollector annotationCollector() {
        return new AnnotationCollector();
    }

    @Bean
    public MetamodelAnalyzer metamodelAnalyzer() {
        return new MetamodelAnalyzer();
    }

    @Bean
    public FieldUtils fieldUtils() {
        return new FieldUtils();
    }

    @Bean
    public FieldValueSetter fieldValueSetter() {
        return new FieldValueSetter();
    }

    @Bean
    public TimeFormatService timeFormatService() {
        return new TimeFormatService();
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminConfig adminConfig() {
        return new AdminConfig();
    }

    @Bean
    public EntityTableService entityTableService() {
        return new EntityTableService();
    }

    @Bean
    public QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    @Bean
    public RecordPersister recordPersister() {
        return new RecordPersister();
    }

    @Bean
    public RecordDetailsFetcher recordDetailsFetcher() {
        return new RecordDetailsFetcher();
    }

    @Bean
    public RecordValidator recordValidator() {
        return new RecordValidator();
    }

    @Bean
    public RecordListService recordListService() {
        return new RecordListService();
    }

    @Bean
    public RecordDetailsService recordDetailsService() {
        return new RecordDetailsService();
    }

    @Bean
    public AdminController adminController() {
        return new AdminController();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}