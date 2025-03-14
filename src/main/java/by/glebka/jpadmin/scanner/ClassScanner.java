package by.glebka.jpadmin.scanner;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Component responsible for scanning the classpath to find JPA-annotated classes.
 */
@Component
public class ClassScanner {

    private static final Logger logger = LoggerFactory.getLogger(ClassScanner.class);

    private final String basePackage;

    /**
     * Constructs a ClassScanner with the specified base package.
     *
     * @param basePackage The base package to scan, injected from properties.
     */
    public ClassScanner(@Value("${jpadmin.base-package}") String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * Scans the classpath for classes annotated with JPA annotations (e.g., @Entity, @MappedSuperclass).
     *
     * @return A set of classes found with JPA annotations.
     */
    public Set<Class<?>> scanJpaClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Table.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(SecondaryTable.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Inheritance.class));

        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(basePackage);
        Set<Class<?>> classes = new HashSet<>();
        for (BeanDefinition beanDef : beanDefinitions) {
            try {
                classes.add(Class.forName(beanDef.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                logger.error("Failed to load class: {}", beanDef.getBeanClassName(), e);
            }
        }
        return classes;
    }
}