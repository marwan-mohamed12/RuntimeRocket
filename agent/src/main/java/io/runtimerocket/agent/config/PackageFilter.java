package io.runtimerocket.agent.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Include/exclude globs over binary class names. Empty includes means every name; any matching
 * exclude wins.
 */
public final class PackageFilter {

    private static final PackageFilter ALLOW_ALL = new PackageFilter(List.of(), List.of());

    private final List<Pattern> includes;
    private final List<Pattern> excludes;
    private final List<PackageFilter> anyOf;

    private PackageFilter(List<String> includes, List<String> excludes) {
        this.includes = compileAll(includes);
        this.excludes = compileAll(excludes);
        this.anyOf = List.of();
    }

    private PackageFilter(List<PackageFilter> anyOf) {
        this.includes = List.of();
        this.excludes = List.of();
        this.anyOf = List.copyOf(anyOf);
    }

    public static PackageFilter allowAll() {
        return ALLOW_ALL;
    }

    public static PackageFilter of(List<String> includes, List<String> excludes) {
        if ((includes == null || includes.isEmpty()) && (excludes == null || excludes.isEmpty())) {
            return ALLOW_ALL;
        }
        return new PackageFilter(
                includes == null ? List.of() : includes, excludes == null ? List.of() : excludes);
    }

    /**
     * A name is accepted when at least one filter accepts it. An empty list is {@link #allowAll()}.
     */
    public static PackageFilter union(List<PackageFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return ALLOW_ALL;
        }
        List<PackageFilter> compact = new ArrayList<>();
        for (PackageFilter filter : filters) {
            if (filter != null) {
                compact.add(filter);
            }
        }
        if (compact.isEmpty()) {
            return ALLOW_ALL;
        }
        if (compact.size() == 1) {
            return compact.get(0);
        }
        return new PackageFilter(compact);
    }

    public boolean accepts(String binaryName) {
        if (binaryName == null || binaryName.isBlank()) {
            return false;
        }
        if (!anyOf.isEmpty()) {
            for (PackageFilter filter : anyOf) {
                if (filter.accepts(binaryName)) {
                    return true;
                }
            }
            return false;
        }
        if (!includes.isEmpty() && !matchesAny(includes, binaryName)) {
            return false;
        }
        return !matchesAny(excludes, binaryName);
    }

    static boolean matches(String glob, String binaryName) {
        if (glob == null || glob.isBlank() || binaryName == null) {
            return false;
        }
        return compile(glob.trim()).matcher(binaryName).matches();
    }

    private static boolean matchesAny(List<Pattern> patterns, String binaryName) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(binaryName).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compileAll(List<String> globs) {
        if (globs == null || globs.isEmpty()) {
            return List.of();
        }
        List<Pattern> out = new ArrayList<>(globs.size());
        for (String glob : globs) {
            if (glob != null && !glob.isBlank()) {
                out.add(compile(glob.trim()));
            }
        }
        return List.copyOf(out);
    }

    private static Pattern compile(String glob) {
        boolean packageAndBelow = glob.endsWith(".**");
        String body = packageAndBelow ? glob.substring(0, glob.length() - 3) : glob;
        StringBuilder regex = new StringBuilder(body.length() * 2 + 8);
        regex.append('^');
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '*') {
                if (i + 1 < body.length() && body.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^.]*");
                }
            } else if (c == '.') {
                regex.append("\\.");
            } else if ("\\[]{}()+?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        if (packageAndBelow) {
            regex.append("(\\..*)?");
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }
}
