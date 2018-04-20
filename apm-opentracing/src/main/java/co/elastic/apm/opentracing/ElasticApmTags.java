package co.elastic.apm.opentracing;

import io.opentracing.tag.StringTag;

public class ElasticApmTags {

    public static final StringTag TYPE = new StringTag("type");
    public static final StringTag USER_ID = new StringTag("user.id");
    public static final StringTag USER_EMAIL = new StringTag("user.email");
    public static final StringTag USER_USERNAME = new StringTag("user.username");
    public static final StringTag RESULT = new StringTag("result");

}
