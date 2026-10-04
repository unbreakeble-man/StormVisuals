package dev.stormvisuals.keybind;

import dev.stormvisuals.config.ConfigManager;
import dev.stormvisuals.config.ConfigState;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    public static final int MAX_COMMANDS = 128;
    private static final List<Command> COMMANDS = new ArrayList<>();
    private static final List<Command> VIEW = Collections.unmodifiableList(COMMANDS);
    // Vanilla retains KeyMappings globally; reuse removed slots instead of leaking mappings.
    private static final List<PressKeyMapping> SLOTS = new ArrayList<>();
    private static List<KeyMapping> functions = List.of();
    private static Map<String, String> savedFunctions = new LinkedHashMap<>();
    private static KeyMapping.Category category;
    private static long revision;
    private static Object previousWorld;

    private Keybinds() { }

    public static final class Command {
        private String command;
        private final PressKeyMapping binding;
        private String label;

        private Command(String command, PressKeyMapping binding) {
            this.command = command;
            this.binding = binding;
            refreshLabel();
        }

        public String command() { return command; }
        public KeyMapping binding() { return binding; }
        public String keyLabel() { return label; }
        private void refreshLabel() {
            label = binding.isUnbound() ? "Назначить" : binding.getTranslatedKeyMessage().getString();
        }
    }

    public static void initialize(List<KeyMapping> functionBindings, KeyMapping.Category keyCategory) {
        functions = List.copyOf(functionBindings);
        category = keyCategory;
        rememberFunctions();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean active = client.player != null && client.level != null
                    && client.screen == null && client.isWindowActive() && previousWorld == client.level;
            previousWorld = client.level;
            for (PressKeyMapping slot : SLOTS) {
                int presses = slot.takePresses();
                if (!active || presses == 0) continue;
                for (Command command : COMMANDS) {
                    if (command.binding == slot) {
                        for (int i = 0; i < presses; i++) client.player.connection.sendCommand(command.command);
                        break;
                    }
                }
            }
            if (ConfigManager.ready()) {
                for (KeyMapping binding : functions) {
                    if (!binding.saveString().equals(savedFunctions.get(binding.getName()))) {
                        changed();
                        break;
                    }
                }
            }
        });
    }

    public static List<Command> commands() { return VIEW; }
    public static String saveError() { return ConfigManager.autosaveError(); }
    public static long revision() { return revision; }

    public static String normalizeCommand(String text) {
        if (text == null) throw new IllegalArgumentException("Введите команду");
        String command = text.strip();
        if (command.startsWith("/")) command = command.substring(1).stripLeading();
        if (command.isEmpty()) throw new IllegalArgumentException("Введите команду");
        if (command.length() > 256) throw new IllegalArgumentException("Команда длиннее 256 символов");
        if (command.codePoints().anyMatch(c -> c < 32 || c == 127 || c == 167))
            throw new IllegalArgumentException("Недопустимые символы в команде");
        return command;
    }

    public static Command add(String text) {
        if (COMMANDS.size() >= MAX_COMMANDS) throw new IllegalArgumentException("Можно создать до 128 команд");
        Command command = create(normalizeCommand(text), InputConstants.UNKNOWN);
        changed();
        return command;
    }

    public static void edit(Command command, String text) {
        command.command = normalizeCommand(text);
        changed();
    }

    public static void bind(Command command, InputConstants.Key key) {
        command.binding.setKey(key);
        command.refreshLabel();
        KeyMapping.resetMapping();
        changed();
    }

    public static void remove(Command command) {
        COMMANDS.remove(command);
        command.binding.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        changed();
    }

    private static Command create(String text, InputConstants.Key key) {
        PressKeyMapping slot = null;
        for (PressKeyMapping candidate : SLOTS) {
            if (COMMANDS.stream().noneMatch(command -> command.binding == candidate)) {
                slot = candidate;
                break;
            }
        }
        if (slot == null) {
            // Fabric registration is init-only. Vanilla's constructor registers this runtime mapping.
            slot = new PressKeyMapping("key.stormvisuals.command." + SLOTS.size(),
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category);
            SLOTS.add(slot);
        }
        slot.setKey(key);
        Command command = new Command(text, slot);
        COMMANDS.add(command);
        KeyMapping.resetMapping();
        return command;
    }

    public static void changed() {
        revision++;
        rememberFunctions();
        ConfigManager.changed();
    }

    private static void rememberFunctions() {
        savedFunctions.clear();
        functions.forEach(binding -> savedFunctions.put(binding.getName(), binding.saveString()));
    }

    public static void capture(ConfigState state) {
        for (Command command : COMMANDS) {
            ConfigState.CommandBind saved = new ConfigState.CommandBind();
            saved.command = command.command;
            saved.key = command.binding.saveString();
            state.commandBinds.add(saved);
        }
    }

    public static void validateConfig(ConfigState state) {
        for (ConfigState.CommandBind command : state.commandBinds) {
            normalizeCommand(command.command);
            validateKey(command.key);
        }
    }

    public static void restore(ConfigState state) {
        COMMANDS.clear();
        SLOTS.forEach(slot -> slot.setKey(InputConstants.UNKNOWN));
        for (ConfigState.CommandBind saved : state.commandBinds)
            create(normalizeCommand(saved.command), InputConstants.getKey(saved.key));
        KeyMapping.resetMapping();
        rememberFunctions();
        revision++;
    }

    private static void validateKey(String name) {
        if (name == null || name.length() > 80) throw new IllegalArgumentException("Некорректная клавиша");
        InputConstants.Key key = InputConstants.getKey(name);
        int value = key.getValue();
        boolean valid = key.equals(InputConstants.UNKNOWN) || switch (key.getType()) {
            case KEYSYM -> value >= GLFW.GLFW_KEY_SPACE && value <= GLFW.GLFW_KEY_LAST;
            case MOUSE -> value >= 0 && value <= GLFW.GLFW_MOUSE_BUTTON_LAST;
            case SCANCODE -> value >= 0 && value <= 512;
        };
        if (!valid) throw new IllegalArgumentException("Некорректная клавиша");
    }
}
