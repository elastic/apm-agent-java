package co.elastic.apm.agent.tracer.configuration;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A matcher for configuration options.
 */
public abstract class Matcher {

    /**
     * Returns {@code true}, if any of the matchers match the provided string.
     *
     * @param matchers the matchers which should be used to match the provided string
     * @param s        the string to match against
     * @return {@code true}, if any of the matchers match the provided string
     */
    public static boolean isAnyMatch(List<? extends Matcher> matchers, @Nullable CharSequence s) {
        return anyMatch(matchers, s) != null;
    }

    /**
     * Returns {@code true}, if none of the matchers match the provided string.
     *
     * @param matchers the matchers which should be used to match the provided string
     * @param s        the string to match against
     * @return {@code true}, if none of the matchers match the provided string
     */
    public static boolean isNoneMatch(List<? extends Matcher> matchers, @Nullable CharSequence s) {
        return !isAnyMatch(matchers, s);
    }

    /**
     * Returns the first {@link Matcher} {@linkplain Matcher#matches(CharSequence) matching} the provided string.
     *
     * @param matchers the matchers which should be used to match the provided string
     * @param s        the string to match against
     * @return the first matching {@link Matcher}, or {@code null} if none match.
     */
    @Nullable
    public static Matcher anyMatch(List<? extends Matcher> matchers, @Nullable CharSequence s) {
        if (s == null || matchers.isEmpty()) {
            return null;
        }
        return anyMatch(matchers, s, null);
    }

    /**
     * Returns the first {@link Matcher} {@linkplain Matcher#matches(CharSequence) matching} the provided partitioned string.
     *
     * @param matchers   the matchers which should be used to match the provided string
     * @param firstPart  The first part of the string to match against.
     * @param secondPart The second part of the string to match against.
     * @return the first matching {@link Matcher}, or {@code null} if none match.
     * @see #matches(CharSequence, CharSequence)
     */
    @Nullable
    public static Matcher anyMatch(List<? extends Matcher> matchers, CharSequence firstPart, @Nullable CharSequence secondPart) {
        for (int i = 0; i < matchers.size(); i++) {
            if (matchers.get(i).matches(firstPart, secondPart)) {
                return matchers.get(i);
            }
        }
        return null;
    }

    /**
     * Checks if the given string matches the given pattern.
     *
     * @param s the String to match
     * @return whether the String matches the given pattern
     */
    public abstract boolean matches(CharSequence s);

    /**
     * This is a different version of {@link #matches(CharSequence)} which has the same semantics as calling
     * {@code matcher.matches(firstPart + secondPart);}.
     * <p>
     * The difference is that this method does not allocate memory.
     * </p>
     *
     * @param firstPart  The first part of the string to match against.
     * @param secondPart The second part of the string to match against.
     * @return {@code true},
     * when the pattern matches the partitioned string,
     * {@code false} otherwise.
     */
    public abstract boolean matches(CharSequence firstPart, @Nullable CharSequence secondPart);
}
