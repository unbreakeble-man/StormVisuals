package dev.stormvisuals.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Personal offline profiles are separate from shareable module presets. */
public final class AccountsState {
    public int version = 1;
    public List<String> accounts = new ArrayList<>();
    public String selected = "";
    public static String nickname(String value) {
        String name = value == null ? "" : value.strip();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Ник: 3–16 латинских букв, цифр или _");
        return name;
    }
    public static UUID uuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname(name)).getBytes(StandardCharsets.UTF_8)); }
    public void validate() {
        if (version != 1 || accounts == null || accounts.size() > 512 || selected == null)
            throw new IllegalArgumentException("Некорректный список аккаунтов");
        var unique = new HashSet<String>();
        for (String name : accounts) if (!nickname(name).equals(name) || !unique.add(name.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Повторяющийся или некорректный аккаунт");
        if (!selected.isEmpty()) nickname(selected);
    }
    public AccountsState copy() {
        AccountsState copy = new AccountsState(); copy.accounts.addAll(accounts); copy.selected = selected;
        return copy;
    }
}
