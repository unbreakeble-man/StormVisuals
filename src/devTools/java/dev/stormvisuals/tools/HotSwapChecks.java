package dev.stormvisuals.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

/** Integration checks against a real second Java VM, without starting Minecraft. */
public final class HotSwapChecks {
    private static final String TARGET = """
            package dev.stormvisuals;
            public final class StormVisualsClient {
                private static String state = "kept";
                public record Row(String name) {}
                private static Row row = new Row("old");
                public static String value() { return "before-" + state; }
                public static void initializeModules() throws Exception { /* new modules */ }
                public static void main(String[] args) throws Exception {
                    var input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                    System.out.println("READY");
                    while (true) {
                        dev.stormvisuals.dev.LiveReload.tick();
                        if (input.ready()) {
                            String line = input.readLine();
                            if (line == null || line.equals("exit")) return;
                            if (line.equals("module")) {
                                Class<?> module = Class.forName("dev.stormvisuals.NewModule");
                                System.out.println("MODULE=" + module.getField("count").get(null) + ":" + module.getField("thread").get(null));
                            } else if (line.equals("schema")) {
                                System.out.println("SCHEMA=" + StormVisualsClient.class.getMethod("extra").invoke(null));
                            } else if (line.equals("row")) {
                                System.out.println("ROW=" + row.name() + ":" + Row.class.getMethod("count").invoke(row));
                            } else System.out.println("VALUE=" + value());
                        }
                        Thread.sleep(10);
                    }
                }
            }
            """;

    private static final String RELOAD_HOOK = """
            package dev.stormvisuals.dev;
            public final class LiveReload {
                public static volatile boolean requested;
                public static volatile String result;
                private static final java.util.Set<String> initialized = new java.util.HashSet<>();
                public static void initializeOnce(String name) throws Exception {
                    if (initialized.add(name)) Class.forName(name).getMethod("initialize").invoke(null);
                }
                public static void tick() throws Exception {
                    if (!requested) return;
                    try {
                        dev.stormvisuals.StormVisualsClient.initializeModules();
                        result = "Registered " + initialized.size() + " test module(s).";
                    } finally { requested = false; }
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        boolean enhanced = Boolean.getBoolean("stormvisuals.enhancedChecks");
        Path root = Files.createTempDirectory(Path.of(".").toAbsolutePath(), "vm-");
        Path classes = Files.createDirectories(root.resolve("classes"));
        Path resources = Files.createDirectories(root.resolve("resources"));
        Path baseline = Files.createDirectories(root.resolve("applied"));
        Path classFile = classes.resolve("dev/stormvisuals/StormVisualsClient.class");
        Path baselineClass = baseline.resolve("classes/dev/stormvisuals/StormVisualsClient.class");
        Path config = resources.resolve("stormvisuals.mixins.json");
        Files.writeString(config, "{}");
        Files.createDirectories(baseline.resolve("resources"));
        Files.writeString(baseline.resolve("resources/stormvisuals.mixins.json"), "{}");
        compile(root, classes, TARGET);
        Files.createDirectories(baselineClass.getParent());
        Files.copy(classFile, baselineClass);
        Path baselineHook = baseline.resolve("classes/dev/stormvisuals/dev/LiveReload.class");
        Files.createDirectories(baselineHook.getParent());
        Files.copy(classes.resolve("dev/stormvisuals/dev/LiveReload.class"), baselineHook);
        Files.copy(classes.resolve("dev/stormvisuals/StormVisualsClient$Row.class"),
                baseline.resolve("classes/dev/stormvisuals/StormVisualsClient$Row.class"));
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        String targetJava = System.getProperty("stormvisuals.testJava", Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        var command = new java.util.ArrayList<String>();
        command.add(targetJava);
        if (enhanced) command.add("-XX:+AllowEnhancedClassRedefinition");
        command.addAll(java.util.List.of("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:" + port,
                "-cp", classes.toString(), "dev.stormvisuals.StormVisualsClient"));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).start();
        try (var output = new BufferedReader(new InputStreamReader(process.getInputStream()));
             var input = new PrintWriter(process.getOutputStream(), true)) {
            await(output, "READY");
            input.println("value");
            await(output, "VALUE=before-kept");
            require(HotSwap.reload(classes, resources, baseline, port) == 0, "Unchanged classes must be skipped");

            compile(root, classes, TARGET.replace("before-", "after-"));
            require(HotSwap.reload(classes, resources, baseline, port) == 1, "One class must be updated");
            input.println("value");
            await(output, "VALUE=after-kept");
            require(process.isAlive(), "Target VM must keep running");
            require(HotSwap.reload(classes, resources, baseline, port) == 0, "Applied baseline must advance");

            // Standard Java rejects structural changes; JBR must apply them and retain old state.
            compile(root, classes, TARGET.replace("before-", "rejected-")
                    .replace("private static String state", "private static int added = 99; public static int extra() { return added + 42; } private static String state"));
            if (enhanced) {
                require(HotSwap.reload(classes, resources, baseline, port) == 1, "Enhanced HotSwap must add a field and method");
                input.println("schema");
                await(output, "SCHEMA=42"); // Existing classes do not rerun static initializers: added starts at zero.
            } else expectRestart(() -> HotSwap.reload(classes, resources, baseline, port), "JVM rejected");
            input.println("value");
            await(output, enhanced ? "VALUE=rejected-kept" : "VALUE=after-kept");
            compile(root, classes, TARGET.replace("before-", "after-"));
            require(HotSwap.reload(classes, resources, baseline, port) == (enhanced ? 1 : 0), "Schema removal/baseline mismatch");

            // Regression for the reported 'changes to class attribute' error: grow a loaded record.
            compile(root, classes, TARGET.replace("before-", "after-")
                    .replace("record Row(String name)", "record Row(String name, int count)")
                    .replace("new Row(\"old\")", "new Row(\"old\", 7)"));
            if (enhanced) {
                require(HotSwap.reload(classes, resources, baseline, port) == 2, "Enhanced HotSwap must update the record and its caller together");
                input.println("row");
                await(output, "ROW=old:0"); // An existing record is migrated; its old constructor is not replayed.
            } else expectRestart(() -> HotSwap.reload(classes, resources, baseline, port), "JVM rejected");
            compile(root, classes, TARGET.replace("before-", "after-"));
            require(HotSwap.reload(classes, resources, baseline, port) == (enhanced ? 2 : 0), "Record removal/baseline mismatch");

            // A mixin change blocks the whole batch before any ordinary method is updated.
            compile(root, classes, TARGET.replace("before-", "blocked-"));
            Path mixin = classes.resolve("dev/stormvisuals/mixin/Probe.class");
            Files.createDirectories(mixin.getParent());
            Files.write(mixin, new byte[] {1});
            expectRestart(() -> HotSwap.reload(classes, resources, baseline, port), "Changed mixin");
            input.println("value");
            await(output, "VALUE=after-kept");
            Files.delete(mixin);

            Files.writeString(config, "{\"changed\":true}");
            expectRestart(() -> HotSwap.reload(classes, resources, baseline, port), "startup resource");
            Files.writeString(config, "{}");
            Files.delete(classFile);
            expectRestart(() -> HotSwap.reload(classes, resources, baseline, port), "Deleted class");
            compile(root, classes, TARGET.replace("before-", "after-"));

            // A different project on the selected debugger port must never be modified.
            Path otherClasses = Files.createDirectories(root.resolve("other-classes"));
            compile(root, otherClasses, TARGET);
            Path otherBaseline = Files.createDirectories(root.resolve("other-applied/classes/dev/stormvisuals"));
            Files.copy(otherClasses.resolve("dev/stormvisuals/StormVisualsClient.class"),
                    otherBaseline.resolve("StormVisualsClient.class"));
            Files.createDirectories(root.resolve("other-applied/resources"));
            Files.copy(config, root.resolve("other-applied/resources/stormvisuals.mixins.json"));
            try {
                HotSwap.reload(otherClasses, resources, root.resolve("other-applied"), port);
                throw new AssertionError("A foreign VM must be rejected");
            } catch (java.io.IOException expected) {
                require(expected.getMessage().contains("not this StormVisuals"), expected.getMessage());
            }
            input.println("value");
            await(output, "VALUE=after-kept");

            // Add a completely new module after JVM startup, then register it on its main loop.
            Path newModule = root.resolve("NewModule.java");
            Files.writeString(newModule, """
                    package dev.stormvisuals;
                    public final class NewModule {
                        public static int count;
                        public static String thread;
                        public static void initialize() { count++; thread = Thread.currentThread().getName(); }
                    }
                    """);
            require(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "--release", "21", "-d", classes.toString(), newModule.toString()) == 0, "New module compilation failed");
            compile(root, classes, TARGET.replace("before-", "after-").replace("/* new modules */",
                    "dev.stormvisuals.dev.LiveReload.initializeOnce(\"dev.stormvisuals.NewModule\");"));
            require(HotSwap.reload(classes, resources, baseline, port) == 1, "Initializer method must update");
            input.println("module");
            await(output, "MODULE=1:main");
            require(HotSwap.reload(classes, resources, baseline, port) == 0, "Newly registered classes must enter the baseline");
            input.println("module");
            await(output, "MODULE=1:main");
            input.println("exit");
            require(process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0, "Target must exit normally");
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
        System.out.println("HotSwap checks passed (enhanced=" + enhanced + "): live methods, retained state, structural field/method/record changes, mixin/config/deletion/foreign VM guards, new module registration without duplicates.");
    }

    private static void compile(Path root, Path classes, String source) throws Exception {
        Path file = root.resolve("StormVisualsClient.java");
        Files.writeString(file, source);
        Path hook = root.resolve("LiveReload.java");
        Files.writeString(hook, RELOAD_HOOK);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-d", classes.toString(), file.toString(), hook.toString());
        require(result == 0, "Fixture compilation failed");
    }

    private static void await(BufferedReader output, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (output.ready()) {
                String line = output.readLine();
                if (expected.equals(line)) return;
            } else Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for " + expected);
    }

    private static void expectRestart(CheckedAction action, String reason) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected a restart requirement: " + reason);
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains(reason) && expected.getMessage().contains("Restart"), expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }
}
