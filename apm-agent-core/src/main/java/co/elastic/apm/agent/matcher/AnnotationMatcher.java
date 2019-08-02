package co.elastic.apm.agent.matcher;

public class AnnotationMatcher {


    private static final String MATCH_ALL = "";
    private final String annotationName;
    private final boolean metaAnnotation;

    private AnnotationMatcher(String annotationName, boolean metaAnnotation) {
        this.annotationName = annotationName;
        this.metaAnnotation = metaAnnotation;
    }


    public static AnnotationMatcher annotationMatcher(String annotationString) {
        if (annotationString.isEmpty()) {
            return matchAll();
        }
        boolean metaAnnotation = annotationString.startsWith("@@");
        String annotationName = annotationString.substring(metaAnnotation ? 2 : 1);
        return new AnnotationMatcher(annotationName, metaAnnotation);
    }

    public static AnnotationMatcher matchAll() {
        return new AnnotationMatcher(MATCH_ALL, false);
    }

    public String getAnnotationName() {
        return annotationName;
    }

    public boolean isMetaAnnotation() {
        return metaAnnotation;
    }

    public boolean isMatchAll() {
        return MATCH_ALL.equals(annotationName);
    }
}
