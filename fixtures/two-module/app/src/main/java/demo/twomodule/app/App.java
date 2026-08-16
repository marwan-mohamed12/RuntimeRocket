package demo.twomodule.app;

import demo.twomodule.lib.LibGreeter;

/** Running app that holds a live {@link LibGreeter} instance. */
public final class App {

    private final LibGreeter greeter = new LibGreeter();

    public int libVersion() {
        return greeter.version();
    }

    public static void main(String[] args) {
        System.out.println(new App().libVersion());
    }
}
