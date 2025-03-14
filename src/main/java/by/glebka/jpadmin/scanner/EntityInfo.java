package by.glebka.jpadmin.scanner;

import jakarta.persistence.Table;
import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Represents metadata about a JPA entity, including its class details, annotations, and metamodel information.
 */
public class EntityInfo {
    private final String className;
    private final Map<String, Annotation> classAnnotations;
    private final Map<String, Map<String, Annotation>> fieldAnnotations;
    private final MetamodelInfo metamodelInfo;

    public EntityInfo(String className,
                      Map<String, Annotation> classAnnotations,
                      Map<String, Map<String, Annotation>> fieldAnnotations,
                      MetamodelInfo metamodelInfo) {
        this.className = className;
        this.classAnnotations = classAnnotations;
        this.fieldAnnotations = fieldAnnotations;
        this.metamodelInfo = metamodelInfo;
    }

    public EntityInfo(String className, MetamodelInfo metamodelInfo) {
        this(className, null, null, metamodelInfo);
    }

    public String getClassName() { return className; }
    public Map<String, Annotation> getClassAnnotations() { return classAnnotations; }
    public Map<String, Map<String, Annotation>> getFieldAnnotations() { return fieldAnnotations; }
    public MetamodelInfo getMetamodelInfo() { return metamodelInfo; }

    /**
     * Retrieves the table name for the entity, either from the @Table annotation or derived from the class name.
     *
     * @return The table name associated with the entity.
     */
    public String getTableName() {
        if (classAnnotations != null && classAnnotations.containsKey("Table")) {
            return ((Table) classAnnotations.get("Table")).name();
        }
        return metamodelInfo.getEntityName() != null ? metamodelInfo.getEntityName() : className.substring(className.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Prints detailed information about the entity for debugging purposes.
     */
    public void printInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Class/Interface: ").append(className).append("\n\n");

        if (classAnnotations != null) {
            sb.append("Class Annotations:\n{\n");
            for (Map.Entry<String, Annotation> entry : classAnnotations.entrySet()) {
                sb.append("    ").append(entry.getKey()).append(" = ")
                        .append(entry.getValue().toString().replaceAll(",", ",\n        ")).append("\n");
            }
            sb.append("}\n\n");
        }

        if (fieldAnnotations != null) {
            sb.append("Field Annotations:\n{\n");
            for (Map.Entry<String, Map<String, Annotation>> fieldEntry : fieldAnnotations.entrySet()) {
                sb.append("    ").append(fieldEntry.getKey()).append(" = {\n");
                for (Map.Entry<String, Annotation> annEntry : fieldEntry.getValue().entrySet()) {
                    String annotationStr = annEntry.getValue().toString().replaceAll(",", ",\n            ");
                    sb.append("        ").append(annEntry.getKey()).append(" = ").append(annotationStr).append("\n");
                }
                sb.append("    }\n");
            }
            sb.append("}\n\n");
        }

        MetamodelInfo meta = metamodelInfo;
        sb.append("Metamodel Info:\n{\n");
        sb.append("    Metamodel Name: ").append(meta.getMetamodelName()).append("\n");
        sb.append("    Entity Name: ").append(meta.getEntityName()).append("\n");
        sb.append("    Java Type: ").append(meta.getJavaType().getSimpleName()).append("\n");
        sb.append("    ID Type: ").append(meta.getIdType() != null ? meta.getIdType().getSimpleName() : "none").append("\n");
        sb.append("    Has Single ID: ").append(meta.isHasSingleId()).append("\n");
        sb.append("    Has Version: ").append(meta.isHasVersion()).append("\n");

        sb.append("    Attributes:\n    {\n");
        meta.getAttributes().forEach((name, attr) -> {
            sb.append("        ").append(name).append(":\n");
            sb.append("          Type: ").append(attr.getJavaTypeSimpleName()).append("\n");
            sb.append("          Persistent Type: ").append(attr.getPersistentType()).append("\n");
            sb.append("          Is Association: ").append(attr.isAssociation()).append("\n");

            if (attr instanceof SingularAttributeInfo singularAttr) {
                sb.append("          Is ID: ").append(singularAttr.isId()).append("\n");
                sb.append("          Is Version: ").append(singularAttr.isVersion()).append("\n");
                sb.append("          Is Optional: ").append(singularAttr.isOptional()).append("\n");
                sb.append("          Bindable Type: ").append(singularAttr.getBindableType() != null ? singularAttr.getBindableType() : "n/a").append("\n");
                sb.append("          Bindable Java Type: ").append(singularAttr.getBindableJavaType().getSimpleName()).append("\n");
            } else if (attr instanceof PluralAttributeInfo pluralAttr) {
                sb.append("          Collection Type: ").append(pluralAttr.getCollectionType()).append("\n");
                sb.append("          Element Type: ").append(pluralAttr.getElementType().getJavaType().getSimpleName()).append("\n");
            }
        });
        sb.append("    }\n");
        sb.append("}");

        System.out.println(sb.toString());
    }
}