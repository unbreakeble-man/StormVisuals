package dev.stormvisuals.module;

import java.util.Locale;

/** Exact command names, including namespaced aliases and vanilla execute ... run wrappers. */
public final class CommandRules {
    public static final String[] COMMANDS = {"suicide", "clear", "kill", "ci", "clearinventory", "reset", "wipe"};
    private CommandRules() {}
    public static boolean dangerous(String command, int mask) {
        if (command == null) return false;
        String[] words = command.strip().toLowerCase(Locale.ROOT).split("\\s+");
        for (int start = 0; start < words.length; ) {
            String root = words[start];
            if (root.startsWith("/")) root = root.substring(1);
            root = root.substring(root.lastIndexOf(':') + 1);
            for (int i = 0; i < COMMANDS.length; i++) if ((mask & 1 << i) != 0 && COMMANDS[i].equals(root)) return true;
            if (!root.equals("execute")) return false;
            int run = start + 1;
            while (run < words.length && !words[run].equals("run")) run++;
            start = run + 1;
        }
        return false;
    }
}
