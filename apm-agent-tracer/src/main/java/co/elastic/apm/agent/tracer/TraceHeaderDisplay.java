package co.elastic.apm.agent.tracer;

import java.util.regex.Pattern;

public enum TraceHeaderDisplay {
    REGULAR {
        @Override
        public String format(String header) {
            return header;
        }
    },
    BINARY {
        @Override
        public String format(String header) {
            return PATTERN.matcher(header).replaceAll("");
        }
    },
    QUEUE {
        @Override
        public String format(String header) {
            return header.replace('-', '_');
        }
    };

    static final Pattern PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    public abstract String format(String header);
}
