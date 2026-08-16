package demo.twomodule.lib;

/** Reloadable type in the lib module of {@code :fixtures:two-module}. */
public final class LibGreeter {

    public int version() {
        return 1;
    }

    public String greet(String name) {
        return "hello " + name;
    }
}
