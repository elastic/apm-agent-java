package co.elastic.apm.util;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class VersionUtils {

    private VersionUtils() {
    }

    @Nullable
    public static String getVersionFromPomProperties(Class clazz, String groupId, String artifactId) {
        final String classpathLocation = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        final Properties pomProperties = getFromClasspath(classpathLocation, clazz.getClassLoader());
        if (pomProperties != null) {
            return pomProperties.getProperty("version");
        }
        return null;
    }

    @Nullable
    private static Properties getFromClasspath(String classpathLocation, ClassLoader classLoader) {
        final Properties props = new Properties();
        InputStream resourceStream = classLoader.getResourceAsStream(classpathLocation);
        if (resourceStream != null) {
            try {
                props.load(resourceStream);
                return props;
            } catch (IOException e) {
                // ignore
            } finally {
                try {
                    resourceStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return null;
    }

}
