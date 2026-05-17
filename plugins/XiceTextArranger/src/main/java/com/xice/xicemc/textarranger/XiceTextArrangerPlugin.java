package com.xice.xicemc.textarranger;

import java.util.List;
import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceTextArrangerPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("XiceTextArranger enabled.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!getConfig().getBoolean("whitelist-denied.enabled", true)) {
            return;
        }
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST) {
            event.setKickMessage(configuredMessage("whitelist-denied.message", event.getName(), event.getName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!getConfig().getBoolean("whitelist-denied.enabled", true)) {
            return;
        }
        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST) {
            event.disallow(event.getResult(), configuredMessage(
                    "whitelist-denied.message",
                    event.getPlayer().getName(),
                    event.getPlayer().getDisplayName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(arrangeMessage(
                "join",
                event.getJoinMessage(),
                event.getPlayer().getName(),
                event.getPlayer().getDisplayName()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(arrangeMessage(
                "quit",
                event.getQuitMessage(),
                event.getPlayer().getName(),
                event.getPlayer().getDisplayName()));
    }

    private String arrangeMessage(String section, String original, String playerName, String displayName) {
        String mode = getConfig().getString(section + ".mode", "keep").toLowerCase(Locale.ROOT);
        String configured = configuredMessage(section + ".message", playerName, displayName);
        return switch (mode) {
            case "delete" -> null;
            case "rewrite" -> configured;
            case "add" -> appendMessage(original, configured);
            default -> original;
        };
    }

    private String appendMessage(String original, String extra) {
        if (extra.isBlank()) {
            return original;
        }
        if (original == null || original.isBlank()) {
            return extra;
        }
        return original + "\n" + extra;
    }

    private String configuredMessage(String path, String playerName, String displayName) {
        List<String> lines = getConfig().getStringList(path);
        String raw = lines.isEmpty() ? getConfig().getString(path, "") : String.join("\n", lines);
        return color(raw
                .replace("{player}", playerName)
                .replace("{displayName}", displayName));
    }

    private String color(String value) {
        return value.replace('&', '\u00A7');
    }
}
