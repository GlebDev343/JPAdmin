package by.glebka.jpadmin.scanner;

import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Component responsible for collecting annotations from classes and their fields.
 */
@Component
public class AnnotationCollector {

    /**
     * Collects annotations defined on the given class.
     *
     * @param clazz The class to analyze.
     * @return A map of annotation names to their instances.
     */
    public Map<String, Annotation> collectClassAnnotations(Class<?> clazz) {
        return getAnnotations(clazz.getAnnotations());
    }

    /**
     * Collects annotations defined on the fields of the given class.
     *
     * @param clazz The class whose fields to analyze.
     * @return A map of field names to their respective annotation maps.
     */
    public Map<String, Map<String, Annotation>> collectFieldAnnotations(Class<?> clazz) {
        Map<String, Map<String, Annotation>> fieldAnnotations = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            Map<String, Annotation> fieldAnns = getAnnotations(field.getAnnotations());
            if (!fieldAnns.isEmpty()) {
                fieldAnnotations.put(field.getName(), fieldAnns);
            }
        }
        return fieldAnnotations;
    }

    /**
     * Converts an array of annotations into a map.
     *
     * @param annotations The annotations to process.
     * @return A map of annotation simple names to their instances.
     */
    private Map<String, Annotation> getAnnotations(Annotation[] annotations) {
        Map<String, Annotation> annotationMap = new HashMap<>();
        for (Annotation ann : annotations) {
            annotationMap.put(ann.annotationType().getSimpleName(), ann);
        }
        return annotationMap;
    }
}