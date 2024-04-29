package co.elastic.apm.agent.universalprofiling;

public interface Clock {

    Clock SYSTEM_NANOTIME = new Clock() {
        @Override
        public long getNanos() {
            return System.nanoTime();
        }
    };

    long getNanos();
}
