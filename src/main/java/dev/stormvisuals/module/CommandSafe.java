package dev.stormvisuals.module;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.stormvisuals.config.ConfigState;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

public final class CommandSafe {
    public static final String[] COMMANDS = CommandRules.COMMANDS;
    private static boolean enabled, announce;
    private static int mask = 127;
    private static String pending, approved, token;
    private static ClientPacketListener connection;
    private CommandSafe() {}
    public static void setEnabled(boolean on) { enabled = on; if (!on) clear(); }
    public static boolean protects(int index) { return (mask & 1 << index) != 0; }
    public static void protect(int index, boolean on) { mask = on ? mask | 1 << index : mask & ~(1 << index); }
    public static void capture(ConfigState.ModuleState s) { s.protectedCommands = mask; }
    public static void restore(ConfigState.ModuleState s) { mask = s.protectedCommands; }
    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommandManager.literal("stormvisuals-safe")
                    .then(ClientCommandManager.literal("confirm").then(ClientCommandManager.argument("token", StringArgumentType.word())
                            .executes(context -> resolve(StringArgumentType.getString(context, "token"), true))))
                    .then(ClientCommandManager.literal("cancel").then(ClientCommandManager.argument("token", StringArgumentType.word())
                            .executes(context -> resolve(StringArgumentType.getString(context, "token"), false)))));
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (command.equals(approved)) { approved = null; return true; }
            if (!enabled || !CommandRules.dangerous(command, mask)) return true;
            Minecraft client = Minecraft.getInstance();
            pending = command;
            token = UUID.randomUUID().toString();
            connection = client.getConnection();
            announce = true;
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pending == null) return;
            if (client.player == null || connection == null || connection != client.getConnection()) { clear(); return; }
            if (!announce) return;
            announce = false;
            client.gui.getChat().addMessage(Component.literal("Command Safe: /").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(pending).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"))
                    .append(action("[Confirm]", "confirm", ChatFormatting.GREEN))
                    .append(Component.literal("  "))
                    .append(action("[Cancel]", "cancel", ChatFormatting.RED)));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { clear(); approved = null; });
    }
    private static Component action(String label, String action, ChatFormatting color) {
        return Component.literal(label).withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand("/stormvisuals-safe " + action + " " + token)));
    }
    private static int resolve(String supplied, boolean confirm) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || pending == null || !supplied.equals(token) || client.player == null || connection != client.getConnection()) {
            client.gui.getChat().addMessage(Component.literal("Command Safe: подтверждение больше не действует.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        String command = pending;
        ClientPacketListener original = connection;
        clear();
        if (confirm) {
            approved = command;
            try { original.sendCommand(command); }
            finally { approved = null; }
        } else client.gui.getChat().addMessage(Component.literal("Command Safe: команда отменена.").withStyle(ChatFormatting.GRAY));
        return 1;
    }
    private static void clear() { pending = token = null; connection = null; announce = false; }
}
