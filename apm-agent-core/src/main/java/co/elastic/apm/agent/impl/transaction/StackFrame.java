package co.elastic.apm.agent.impl.transaction;

public class StackFrame {
    private final String className;
    private final String methodName;

    public static StackFrame of(String className, String methodName) {
        return new StackFrame(className, methodName);
    }

    public StackFrame(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void appendSimpleClassName(StringBuilder sb) {
        sb.append(className, className.lastIndexOf('.') + 1, className.length());
    }

    public void appendFileName(StringBuilder replaceBuilder) {
        int fileNameEnd = className.indexOf('$');
        if (fileNameEnd < 0) {
            fileNameEnd = className.length();
        }
        int classNameStart = className.lastIndexOf('.');
        if (classNameStart < fileNameEnd && fileNameEnd <= className.length()) {
            replaceBuilder.append(className, classNameStart + 1, fileNameEnd);
            replaceBuilder.append(".java");
        } else {
            replaceBuilder.append("<Unknown>");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StackFrame that = (StackFrame) o;

        if (!className.equals(that.className)) return false;
        return methodName.equals(that.methodName);
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + methodName.hashCode();
        return result;
    }
}
