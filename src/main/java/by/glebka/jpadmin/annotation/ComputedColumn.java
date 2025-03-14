package by.glebka.jpadmin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking methods that define computed columns in a table.
 * Computed columns are dynamically calculated and can be displayed in the admin interface.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ComputedColumn {

    /**
     * The display name of the computed column shown in the admin interface.
     */
    String displayName();
}