package com.xice.xicemc.warpsuppression;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceWarpSuppressionPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, WarpSuppression> suppressions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("warpsuppression");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getLogger().info("XiceWarpSuppression enabled.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!hasSuppression(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        send(event.getPlayer(), message("teleport-blocked"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xicewarpsuppression.admin")) {
            send(sender, message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            send(sender, message("usage"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            send(sender, message("player-not-found"), "player", args[1]);
            return true;
        }
        switch (action) {
            case "give", "apply", "set" -> applySuppression(sender, target, args);
            case "clear", "remove" -> clearSuppression(sender, target);
            case "check", "info" -> checkSuppression(sender, target);
            default -> send(sender, message("usage"));
        }
        return true;
    }

    private void applySuppression(CommandSender sender, Player target, String[] args) {
        if (args.length < 3) {
            send(sender, message("usage"));
            return;
        }
        Long durationMillis = parseDurationMillis(args[2]);
        if (durationMillis == null) {
            send(sender, message("invalid-duration"));
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationMillis;
        suppressions.put(target.getUniqueId(), new WarpSuppression(expiresAt));
        String durationText = formatDuration(durationMillis);
        send(sender, message("applied"), "player", target.getName(), "duration", durationText);
        send(target, message("received"), "duration", durationText);
    }

    private void clearSuppression(CommandSender sender, Player target) {
        suppressions.remove(target.getUniqueId());
        send(sender, message("cleared"), "player", target.getName());
        send(target, message("cleared-target"));
    }

    private void checkSuppression(CommandSender sender, Player target) {
        WarpSuppression suppression = suppressions.get(target.getUniqueId());
        long now = System.currentTimeMillis();
        if (suppression == null || suppression.expiresAt <= now) {
            suppressions.remove(target.getUniqueId());
            send(sender, message("not-active"), "player", target.getName());
            return;
        }
        send(sender, message("active"), "player", target.getName(), "duration", formatDuration(suppression.expiresAt - now));
    }

    private boolean hasSuppression(Player player) {
        WarpSuppression suppression = suppressions.get(player.getUniqueId());
        if (suppression == null) {
            return false;
        }
        if (suppression.expiresAt <= System.currentTimeMillis()) {
            suppressions.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private Long parseDurationMillis(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        long multiplier = 1000L;
        String number = normalized;
        char suffix = normalized.charAt(normalized.length() - 1);
        if (suffix == 's' || suffix == 'm' || suffix == 'h') {
            number = normalized.substring(0, normalized.length() - 1);
            multiplier = switch (suffix) {
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                default -> 1000L;
            };
        }
        try {
            long amount = Long.parseLong(number);
            if (amount <= 0L || amount > Long.MAX_VALUE / multiplier) {
                return null;
            }
            return amount * multiplier;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = Math.max(1L, durationMillis / 1000L);
        if (totalSeconds % 3600L == 0L) {
            return (totalSeconds / 3600L) + " 小时";
        }
        if (totalSeconds % 60L == 0L) {
            return (totalSeconds / 60L) + " 分钟";
        }
        return totalSeconds + " 秒";
    }

    private String message(String key) {
        return getConfig().getString("messages." + key, key);
    }

    private void send(CommandSender sender, String text, Object... replacements) {
        String value = text == null ? "" : text;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            value = value.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
        }
        sender.sendMessage(color(getConfig().getString("messages.prefix", "") + value));
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("xicewarpsuppression.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("give", "clear", "check").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 3 && List.of("give", "apply", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("30s", "1m", "5m", "10m").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private record WarpSuppression(long expiresAt) {
    }
}
