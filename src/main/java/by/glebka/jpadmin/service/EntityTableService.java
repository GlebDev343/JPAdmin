package by.glebka.jpadmin.service;

import by.glebka.jpadmin.config.AdminConfig;
import by.glebka.jpadmin.config.TableConfig;
import by.glebka.jpadmin.scanner.*;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Service for scanning and retrieving entity table information.
 */
@Service
public class EntityTableService {

    private static final Logger logger = LoggerFactory.getLogger(EntityTableService.class);
    private static final String DEFAULT_TABLE_NAME_SUFFIX = ".toLowerCase()";

    @Autowired
    private ClassScanner classScanner;

    @Autowired
    private MetamodelAnalyzer metamodelAnalyzer;

    @Autowired(required = false)
    private AdminConfig adminConfig;

    /**
     * Retrieves a list of entity tables based on scanned JPA classes and configuration.
     *
     * @return List of entity information objects.
     */
    public List<EntityInfo> getEntityTables() {
        Set<Class<?>> allClasses = classScanner.scanJpaClasses();
        Map<String, EntityInfo> entityMap = buildEntityMap(allClasses);
        List<EntityInfo> tables = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();

        boolean showAllTables = adminConfig == null || adminConfig.isShowAllTablesByDefault();
        if (showAllTables) {
            processAllEntities(entityMap, tables, processedClasses);
        } else {
            processRegisteredEntities(entityMap, tables, processedClasses);
        }

        logger.debug("Found {} entity tables", tables.size());
        return tables;
    }

    private Map<String, EntityInfo> buildEntityMap(Set<Class<?>> allClasses) {
        Map<String, EntityInfo> entityMap = new HashMap<>();
        for (Class<?> clazz : allClasses) {
            if (clazz.isAnnotationPresent(Entity.class)) {
                entityMap.put(clazz.getName(), metamodelAnalyzer.analyze(clazz));
            }
        }
        return entityMap;
    }

    private void processAllEntities(Map<String, EntityInfo> entityMap, List<EntityInfo> tables, Set<String> processedClasses) {
        for (EntityInfo entity : entityMap.values()) {
            try {
                Class<?> clazz = Class.forName(entity.getClassName());
                processEntity(clazz, entity, entityMap, tables, processedClasses);
            } catch (ClassNotFoundException e) {
                logger.error("Failed to load class {}: {}", entity.getClassName(), e.getMessage(), e);
            }
        }
    }

    private void processRegisteredEntities(Map<String, EntityInfo> entityMap, List<EntityInfo> tables, Set<String> processedClasses) {
        for (Map.Entry<Class<?>, TableConfig> entry : adminConfig.getRegisteredTables().entrySet()) {
            Class<?> clazz = entry.getKey();
            String className = clazz.getName();
            EntityInfo entity = entityMap.get(className);
            if (entity != null) {
                processEntity(clazz, entity, entityMap, tables, processedClasses);
            } else {
                logger.warn("Registered class {} not found in scanned entities", className);
            }
        }
    }

    private void processEntity(Class<?> clazz, EntityInfo entity, Map<String, EntityInfo> entityMap,
                               List<EntityInfo> tables, Set<String> processedClasses) {
        String className = clazz.getName();
        if (processedClasses.contains(className)) return;

        Inheritance inheritance = clazz.getAnnotation(Inheritance.class);
        if (inheritance != null) {
            processInheritance(inheritance, clazz, entity, tables, processedClasses, entityMap);
        } else {
            tables.add(entity);
            processedClasses.add(className);
        }
    }

    private void processInheritance(Inheritance inheritance, Class<?> clazz, EntityInfo entity, List<EntityInfo> tables,
                                    Set<String> processedClasses, Map<String, EntityInfo> entityMap) {
        String className = clazz.getName();
        if (inheritance.strategy() == InheritanceType.JOINED) {
            EntityInfo combinedEntity = handleJoinedInheritance(clazz, entityMap);
            tables.add(combinedEntity);
            processedClasses.addAll(getAllSubclasses(clazz, entityMap.keySet()));
            processedClasses.add(className);
        } else if (inheritance.strategy() == InheritanceType.SINGLE_TABLE) {
            tables.add(entity);
            processedClasses.add(className);
        } else if (inheritance.strategy() == InheritanceType.TABLE_PER_CLASS) {
            tables.addAll(handleTablePerClassInheritance(clazz, entityMap));
            processedClasses.addAll(getAllSubclasses(clazz, entityMap.keySet()));
            processedClasses.add(className);
        }
    }

    private EntityInfo handleJoinedInheritance(Class<?> baseClass, Map<String, EntityInfo> entityMap) {
        EntityInfo baseEntity = entityMap.get(baseClass.getName());
        Map<String, AttributeInfo> combinedAttributes = new HashMap<>(baseEntity.getMetamodelInfo().getAttributes());
        Map<String, Map<String, Annotation>> combinedFieldAnnotations = new HashMap<>(baseEntity.getFieldAnnotations() != null ? baseEntity.getFieldAnnotations() : new HashMap<>());
        Set<String> tableNames = new HashSet<>();
        Table baseTable = baseClass.getAnnotation(Table.class);
        tableNames.add(baseTable != null ? baseTable.name() : baseClass.getSimpleName() + DEFAULT_TABLE_NAME_SUFFIX);

        for (String className : entityMap.keySet()) {
            try {
                Class<?> subClass = Class.forName(className);
                if (baseClass.isAssignableFrom(subClass) && !baseClass.equals(subClass)) {
                    EntityInfo subEntity = entityMap.get(className);
                    combinedAttributes.putAll(subEntity.getMetamodelInfo().getAttributes());
                    if (subEntity.getFieldAnnotations() != null) {
                        combinedFieldAnnotations.putAll(subEntity.getFieldAnnotations());
                    }
                    Table subTable = subClass.getAnnotation(Table.class);
                    if (subTable != null) {
                        tableNames.add(subTable.name());
                    }
                }
            } catch (ClassNotFoundException e) {
                logger.error("Failed to process subclass: {}", e.getMessage(), e);
            }
        }

        return new EntityInfo(
                baseClass.getName(),
                baseEntity.getClassAnnotations(),
                combinedFieldAnnotations,
                new MetamodelInfo(
                        String.join(",", tableNames),
                        baseEntity.getMetamodelInfo().getJavaType(),
                        baseEntity.getMetamodelInfo().getIdType(),
                        baseEntity.getMetamodelInfo().isHasSingleId(),
                        baseEntity.getMetamodelInfo().isHasVersion(),
                        combinedAttributes,
                        baseEntity.getMetamodelInfo().getMetamodelName()
                )
        );
    }

    private List<EntityInfo> handleTablePerClassInheritance(Class<?> baseClass, Map<String, EntityInfo> entityMap) {
        List<EntityInfo> result = new ArrayList<>();
        result.add(entityMap.get(baseClass.getName()));
        for (String className : entityMap.keySet()) {
            try {
                Class<?> cls = Class.forName(className);
                if (baseClass.isAssignableFrom(cls) && !baseClass.equals(cls)) {
                    result.add(entityMap.get(className));
                }
            } catch (ClassNotFoundException e) {
                logger.error("Failed to process subclass: {}", e.getMessage(), e);
            }
        }
        return result;
    }

    private Set<String> getAllSubclasses(Class<?> baseClass, Set<String> allClassNames) {
        Set<String> subclasses = new HashSet<>();
        for (String className : allClassNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (baseClass.isAssignableFrom(clazz) && !baseClass.equals(clazz)) {
                    subclasses.add(className);
                }
            } catch (ClassNotFoundException e) {
                logger.error("Failed to process subclass: {}", e.getMessage(), e);
            }
        }
        return subclasses;
    }
}