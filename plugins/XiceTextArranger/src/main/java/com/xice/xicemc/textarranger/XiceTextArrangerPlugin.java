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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
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

    private String whitelistDeniedMessage(String playerName, String displayName, UUID playerUuid) {
        Map<String, String> placeholders = new HashMap<>();
        if (getConfig().getBoolean("verification-codes.enabled", true)) {
            VerificationCode code = verificationCodeFor(playerName, playerUuid);
            placeholders.put("verificationCode", code.code());
            placeholders.put("verificationExpiresMinutes", String.valueOf(code.expiresMinutes()));
        }
        return configuredMessage("whitelist-denied.message", playerName, displayName, placeholders);
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
}
