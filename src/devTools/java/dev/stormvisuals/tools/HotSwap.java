package dev.stormvisuals.tools;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.StringReference;
import com.sun.jdi.PathSearchingVirtualMachine;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** External, development-only client for the JDK's built-in HotSwap protocol. */
public final class HotSwap {
    private HotSwap() {}

    public static void main(String[] args) {
        try {
            reload(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Integer.parseInt(args[3]));
        } catch (Exception | LinkageError error) {
            System.err.println("[HotSwap] " + error.getMessage());
            System.exit(1);
        }
    }

    static int reload(Path classes, Path resources, Path baseline, int port) throws Exception {
        Path applied = baseline.resolve("classes");
        if (!Files.isDirectory(applied)) {
            throw new IOException("Start Minecraft with gradlew.bat runHotSwapClient first.");
        }
        Map<String, byte[]> current = readFiles(classes);
        Map<String, byte[]> previous = readFiles(applied);
        Map<String, byte[]> changed = new LinkedHashMap<>();
        for (String name : previous.keySet()) {
            if (!current.containsKey(name)) throw restart("Deleted class: " + name);
        }
        for (var entry : current.entrySet()) {
            String name = entry.getKey();
            if (Arrays.equals(entry.getValue(), previous.get(name))) continue;
            if (!name.startsWith("dev/stormvisuals/") || !name.endsWith(".class")) continue;
            if (name.startsWith("dev/stormvisuals/mixin/")) throw restart("Changed mixin: " + name);
            changed.put(name, entry.getValue());
        }
        Map<String, byte[]> resourceFiles = readFiles(resources);
        Map<String, byte[]> oldResources = readFiles(baseline.resolve("resources"));
        var resourceNames = new java.util.TreeSet<>(resourceFiles.keySet());
        resourceNames.addAll(oldResources.keySet());
        boolean assetsChanged = false;
        for (String name : resourceNames) {
            if (Arrays.equals(resourceFiles.get(name), oldResources.get(name))) continue;
            if (!name.startsWith("assets/")) throw restart("Changed startup resource: " + name);
            assetsChanged = true;
        }

        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(candidate -> candidate.name().equals("com.sun.jdi.SocketAttach"))
                .findFirst().orElseThrow(() -> new IOException("JDK socket connector is unavailable."));
        var arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(Integer.toString(port));
        arguments.get("timeout").setValue("5000");
        VirtualMachine vm;
        try {
            vm = connector.attach(arguments);
        } catch (IOException error) {
            throw new IOException("Cannot connect to the development client. Use runHotSwapClient and wait for the main menu. Close any IDE debugger connection.", error);
        }
        try {
            // Refuse a different project/JVM even if it happens to use this port.
            if (!(vm instanceof PathSearchingVirtualMachine target)
                    || target.classPath().stream().noneMatch(path -> Path.of(path).toAbsolutePath().normalize()
                            .equals(classes.toAbsolutePath().normalize()))
                    || vm.classesByName("dev.stormvisuals.StormVisualsClient").isEmpty()) {
                throw new IOException("The connected VM is not this StormVisuals client, or it is still starting.");
            }
            if (!vm.canRedefineClasses()) throw restart("This Java runtime does not support HotSwap.");
            var reloadHooks = vm.classesByName("dev.stormvisuals.dev.LiveReload");
            if (reloadHooks.isEmpty() && current.containsKey("dev/stormvisuals/dev/LiveReload.class")) {
                throw restart("The new module-registration support is not installed in this running client yet.");
            }
            Map<ReferenceType, byte[]> definitions = new LinkedHashMap<>();
            Map<String, byte[]> accepted = new LinkedHashMap<>();
            for (var entry : changed.entrySet()) {
                String className = entry.getKey().replace('/', '.').replaceFirst("\\.class$", "");
                var types = vm.classesByName(className);
                if (types.isEmpty()) {
                    System.out.println("[HotSwap] Not loaded yet (available on first use): " + className);
                    continue;
                }
                for (ReferenceType type : types) definitions.put(type, entry.getValue());
                accepted.put(entry.getKey(), entry.getValue());
            }
            if (!definitions.isEmpty()) {
                try {
                    // One atomic JVM operation: a rejected schema change applies none of this batch.
                    vm.redefineClasses(definitions);
                } catch (UnsupportedOperationException | LinkageError error) {
                    String detail = error.getMessage();
                    if (detail != null && detail.contains("class attribute")) {
                        detail += " (Record, NestHost, NestMembers or PermittedSubclasses metadata changed)";
                    }
                    throw restart("JVM rejected the class changes. Runtime: " + vm.version()
                            + ". Classes: " + accepted.keySet() + ". " + detail
                            + ". The updated runHotSwapClient uses JBR 21 with enhanced class redefinition; an already running standard Java VM must be restarted once");
                }
                for (var entry : accepted.entrySet()) {
                    Path file = applied.resolve(entry.getKey());
                    Files.createDirectories(file.getParent());
                    Files.write(file, entry.getValue());
                    System.out.println("[HotSwap] Updated: " + entry.getKey());
                }
            }
            System.out.println("[HotSwap] Applied " + accepted.size() + " class(es). Existing state and initializers are unchanged.");
            if (!reloadHooks.isEmpty()) {
                requestRegistration(vm, (ClassType) reloadHooks.getFirst());
                // Newly referenced classes are loaded by the client initializer, not by redefineClasses.
                for (var entry : changed.entrySet()) {
                    String name = entry.getKey().replace('/', '.').replaceFirst("\\.class$", "");
                    if (accepted.containsKey(entry.getKey()) || vm.classesByName(name).isEmpty()) continue;
                    Path file = applied.resolve(entry.getKey());
                    Files.createDirectories(file.getParent());
                    Files.write(file, entry.getValue());
                }
            }
            if (assetsChanged) System.out.println("[HotSwap] Assets changed: press F3+T in Minecraft. Custom cached resources may require a restart.");
            return accepted.size();
        } finally {
            vm.dispose();
        }
    }

    private static void requestRegistration(VirtualMachine vm, ClassType hook) throws Exception {
        var requested = hook.fieldByName("requested");
        var result = hook.fieldByName("result");
        if (requested == null || result == null) throw restart("Module-registration hook is outdated.");
        hook.setValue(result, null);
        hook.setValue(requested, vm.mirrorOf(true));
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (!((com.sun.jdi.BooleanValue) hook.getValue(requested)).value()
                    && hook.getValue(result) instanceof StringReference message) {
                if (message.value().startsWith("ERROR:")) throw new IOException("Code updated, but registration failed: " + message.value());
                System.out.println("[HotSwap] " + message.value());
                return;
            }
            Thread.sleep(25);
        }
        throw new IOException("Code updated, but the client did not acknowledge module registration. Resume the game; registration may still be pending.");
    }

    private static IOException restart(String reason) {
        return new IOException(reason + " Restart the development client to apply these changes.");
    }

    private static Map<String, byte[]> readFiles(Path directory) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        if (!Files.isDirectory(directory)) return result;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(directory.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return result;
    }
}
