package com.xice.xicemc.textarranger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceTextArrangerPlugin extends JavaPlugin implements Listener {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("XiceTextArranger enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("xicebroadcast")) {
            return false;
        }
        if (!sender.hasPermission("xicetextarranger.broadcast")) {
            sender.sendMessage(color("&c你没有权限执行该命令。"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(color("&c用法：/xicebroadcast <消息配置名> [占位符=值...]"));
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);
        String basePath = "broadcasts." + key;
        if (!getConfig().getBoolean(basePath + ".enabled", true)) {
            sender.sendMessage(color("&7该广播已禁用：" + key));
            return true;
        }

        Map<String, String> placeholders = parsePlaceholders(args);
        String message = configuredMessage(basePath + ".message", "", "", placeholders);
        if (message.isBlank()) {
            sender.sendMessage(color("&c广播内容为空：" + key));
            return true;
        }

        getServer().broadcastMessage(message);
        sender.sendMessage(color("&7已发送广播：" + key));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        BlacklistEntry blacklistEntry = blacklistEntryFor(event.getName(), event.getUniqueId());
        if (blacklistEntry != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, blacklistMessage(event.getName(), event.getName(), blacklistEntry));
            return;
        }

        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST
                && getConfig().getBoolean("whitelist-denied.enabled", true)) {
            event.setKickMessage(whitelistDeniedMessage(event.getName(), event.getName(), event.getUniqueId()));
            return;
        }

        if (shouldRewriteAuthDenied(event.getKickMessage())) {
            event.setKickMessage(configuredMessage("auth-denied.message", event.getName(), event.getName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        BlacklistEntry blacklistEntry = blacklistEntryFor(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        if (blacklistEntry != null) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, blacklistMessage(
                    event.getPlayer().getName(),
                    event.getPlayer().getDisplayName(),
                    blacklistEntry));
            return;
        }

        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST
                && getConfig().getBoolean("whitelist-denied.enabled", true)) {
            event.disallow(event.getResult(), whitelistDeniedMessage(
                    event.getPlayer().getName(),
                    event.getPlayer().getDisplayName(),
                    event.getPlayer().getUniqueId()));
            return;
        }

        if (shouldRewriteAuthDenied(event.getKickMessage())) {
            event.disallow(event.getResult(), configuredMessage(
                    "auth-denied.message",
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

    private boolean shouldRewriteAuthDenied(String originalMessage) {
        if (!getConfig().getBoolean("auth-denied.enabled", true) || originalMessage == null) {
            return false;
        }

        String normalized = stripColor(originalMessage).toLowerCase(Locale.ROOT);
        for (String fragment : getConfig().getStringList("auth-denied.match-messages")) {
            if (!fragment.isBlank() && normalized.contains(fragment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String arrangeMessage(String section, String original, String playerName, String displayName) {
        String mode = getConfig().getString(section + ".mode", "keep").toLowerCase(Locale.ROOT);
        String configured = configuredMessage(section + ".message", playerName, displayName, Map.of());
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
        return configuredMessage(path, playerName, displayName, Map.of());
    }

    private String configuredMessage(String path, String playerName, String displayName, Map<String, String> extraPlaceholders) {
        List<String> lines = getConfig().getStringList(path);
        String raw = lines.isEmpty() ? getConfig().getString(path, "") : String.join("\n", lines);
        String value = raw
                .replace("{player}", playerName)
                .replace("{displayName}", displayName);
        for (Map.Entry<String, String> entry : extraPlaceholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return color(value);
    }

    private Map<String, String> parsePlaceholders(String[] args) {
        Map<String, String> placeholders = new HashMap<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            int separator = argument.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = argument.substring(0, separator);
            String value = argument.substring(separator + 1);
            if (!key.isBlank()) {
                placeholders.put(key, value);
            }
        }
        return placeholders;
    }

    private String whitelistDeniedMessage(String playerName, String displayName, UUID playerUuid) {
        Map<String, String> placeholders = new HashMap<>();
        if (getConfig().getBoolean("verification-codes.enabled", true)) {
            VerificationCode code = verificationCodeFor(playerName, playerUuid);
            placeholders.put("verificationCode", code.code());
            placeholders.put("verificationExpiresMinutes", String.valueOf(code.expiresMinutes()));
        }
        return configuredMessage("whitelist-denied.message", playerName, displayName, placeholders);
    }

    private String blacklistMessage(String playerName, String displayName, BlacklistEntry entry) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", entry.reason());
        placeholders.put("expiresAt", entry.permanent() ? "永久" : formatExpiry(entry.expiresAt()));
        return configuredMessage("blacklist.message", playerName, displayName, placeholders);
    }

    private synchronized BlacklistEntry blacklistEntryFor(String playerName, UUID playerUuid) {
        if (!getConfig().getBoolean("blacklist.enabled", true)) {
            return null;
        }
        long now = Instant.now().toEpochMilli();
        String key = playerName.toLowerCase(Locale.ROOT);
        String uuid = playerUuid.toString();
        for (BlacklistEntry entry : readBlacklist(blacklistPath(), now)) {
            if (entry.key().equals(key) || entry.uuid().equalsIgnoreCase(uuid)) {
                return entry;
            }
        }
        return null;
    }

    private Path blacklistPath() {
        String configured = getConfig().getString("blacklist.path", "plugins/XiceTextArranger/blacklist.tsv");
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path;
        }
        return getServer().getWorldContainer().toPath().resolve(path).normalize();
    }

    private List<BlacklistEntry> readBlacklist(Path path, long now) {
        List<BlacklistEntry> entries = new ArrayList<>();
        if (!Files.isRegularFile(path)) {
            return entries;
        }

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length != 6) {
                    continue;
                }
                long expiresAt = Long.parseLong(parts[3]);
                boolean permanent = Boolean.parseBoolean(parts[4]);
                if (permanent || expiresAt > now) {
                    entries.add(new BlacklistEntry(parts[0], parts[1], parts[2], expiresAt, permanent, parts[5]));
                }
            }
        } catch (IOException | NumberFormatException exception) {
            getLogger().warning("Failed to read blacklist: " + exception.getMessage());
        }
        return entries;
    }

    private String formatExpiry(long expiresAt) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(expiresAt));
    }

    private synchronized VerificationCode verificationCodeFor(String playerName, UUID playerUuid) {
        long now = Instant.now().toEpochMilli();
        long ttlMillis = Math.max(1, getConfig().getLong("verification-codes.ttl-seconds", 300)) * 1000L;
        Path path = verificationCodesPath();
        List<VerificationCodeEntry> entries = readVerificationCodes(path, now);
        String key = playerName.toLowerCase(Locale.ROOT);

        for (VerificationCodeEntry entry : entries) {
            if (entry.key().equals(key) && entry.expiresAt() > now) {
                writeVerificationCodes(path, entries);
                return new VerificationCode(entry.code(), minutesUntil(entry.expiresAt(), now));
            }
        }

        String code = generateCode();
        long expiresAt = now + ttlMillis;
        entries.add(new VerificationCodeEntry(key, playerUuid.toString(), playerName, code, expiresAt));
        writeVerificationCodes(path, entries);
        return new VerificationCode(code, minutesUntil(expiresAt, now));
    }

    private Path verificationCodesPath() {
        String configured = getConfig().getString("verification-codes.path", "plugins/XiceTextArranger/verification-codes.tsv");
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path;
        }
        return getServer().getWorldContainer().toPath().resolve(path).normalize();
    }

    private List<VerificationCodeEntry> readVerificationCodes(Path path, long now) {
        List<VerificationCodeEntry> entries = new ArrayList<>();
        if (!Files.isRegularFile(path)) {
            return entries;
        }

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length != 5) {
                    continue;
                }
                long expiresAt = Long.parseLong(parts[4]);
                if (expiresAt > now) {
                    entries.add(new VerificationCodeEntry(parts[0], parts[1], parts[2], parts[3], expiresAt));
                }
            }
        } catch (IOException | NumberFormatException exception) {
            getLogger().warning("Failed to read verification codes: " + exception.getMessage());
        }
        return entries;
    }

    private void writeVerificationCodes(Path path, List<VerificationCodeEntry> entries) {
        try {
            Files.createDirectories(path.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# key\tuuid\tplayer\tcode\texpiresAtMillis");
            for (VerificationCodeEntry entry : entries) {
                lines.add(String.join("\t",
                        entry.key(),
                        entry.uuid(),
                        entry.playerName(),
                        entry.code(),
                        String.valueOf(entry.expiresAt())));
            }
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tempPath, lines, StandardCharsets.UTF_8);
            Files.move(tempPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            getLogger().warning("Failed to write verification codes: " + exception.getMessage());
        }
    }

    private String generateCode() {
        String alphabet = getConfig().getString("verification-codes.alphabet", "23456789ABCDEFGHJKLMNPQRSTUVWXYZ");
        int length = Math.max(4, getConfig().getInt("verification-codes.length", 6));
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private long minutesUntil(long expiresAt, long now) {
        return Math.max(1, (long) Math.ceil((expiresAt - now) / 60000.0));
    }

    private String color(String value) {
        return value.replace('&', '\u00A7');
    }

    private String stripColor(String value) {
        return value.replaceAll("(?i)\u00A7[0-9A-FK-OR]", "");
    }

    private record VerificationCode(String code, long expiresMinutes) {
    }

    private record VerificationCodeEntry(String key, String uuid, String playerName, String code, long expiresAt) {
    }

    private record BlacklistEntry(String key, String uuid, String playerName, long expiresAt, boolean permanent, String reason) {
    }
}
