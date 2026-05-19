package com.xice.xicemc.morepotioneffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class XiceMorePotionEffectsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String WARP_SUPPRESSION_ID = "warp_suppression";
    private static final String WARP_SUPPRESSION_NAME = "跃迁抑制";
    private static final String SIDEBAR_OBJECTIVE = "xice_mpe";

    private final Map<UUID, WarpSuppression> suppressions = new HashMap<>();
    private final Map<UUID, Scoreboard> sidebarBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private BukkitTask sidebarTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("morepotioneffects");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        sidebarTask = Bukkit.getScheduler().runTaskTimer(this, this::updateAllSidebars, 20L, 20L);
        getLogger().info("XiceMorePotionEffects enabled.");
    }

    @Override
    public void onDisable() {
        if (sidebarTask != null) {
            sidebarTask.cancel();
            sidebarTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreSidebar(player);
        }
        sidebarBoards.clear();
        previousBoards.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!hasSuppression(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        send(event.getPlayer(), message("teleport-blocked"));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sidebarBoards.remove(uuid);
        previousBoards.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("xicemorepotioneffects.admin")) {
                send(sender, message("no-permission"));
                return true;
            }
            reloadConfig();
            send(sender, message("reload-complete"));
            return true;
        }
        if (args.length < 3) {
            send(sender, message("usage"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            send(sender, message("player-not-found"), "player", args[1]);
            return true;
        }
        CustomEffect effect = effectById(args[2]);
        if (effect == null) {
            send(sender, message("unknown-effect"), "effect", args[2]);
            return true;
        }
        switch (action) {
            case "give", "apply", "set" -> applyEffect(sender, target, effect, args);
            case "clear", "remove" -> clearEffect(sender, target, effect);
            case "check", "info" -> checkEffect(sender, target, effect);
            default -> send(sender, message("usage"));
        }
        return true;
    }

    private void applyEffect(CommandSender sender, Player target, CustomEffect effect, String[] args) {
        if (!canUseAction(sender, "give")) {
            send(sender, message("no-permission"));
            return;
        }
        if (args.length < 4) {
            send(sender, message("usage"));
            return;
        }
        Long durationMillis = parseDurationMillis(args[3]);
        if (durationMillis == null) {
            send(sender, message("invalid-duration"));
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationMillis;
        suppressions.put(target.getUniqueId(), new WarpSuppression(expiresAt));
        String durationText = formatDuration(durationMillis);
        send(sender, message("applied"), "player", target.getName(), "effect", effect.displayName, "duration", durationText);
        send(target, message("received"), "effect", effect.displayName, "duration", durationText);
        updateSidebar(target);
    }

    private void clearEffect(CommandSender sender, Player target, CustomEffect effect) {
        if (!sender.hasPermission("xicemorepotioneffects.admin")) {
            send(sender, message("no-permission"));
            return;
        }
        if (effect == CustomEffect.WARP_SUPPRESSION) {
            suppressions.remove(target.getUniqueId());
        }
        send(sender, message("cleared"), "player", target.getName(), "effect", effect.displayName);
        send(target, message("cleared-target"), "effect", effect.displayName);
        updateSidebar(target);
    }

    private void checkEffect(CommandSender sender, Player target, CustomEffect effect) {
        if (!sender.hasPermission("xicemorepotioneffects.admin")) {
            send(sender, message("no-permission"));
            return;
        }
        WarpSuppression suppression = suppressions.get(target.getUniqueId());
        long now = System.currentTimeMillis();
        if (suppression == null || suppression.expiresAt <= now) {
            suppressions.remove(target.getUniqueId());
            send(sender, message("not-active"), "player", target.getName(), "effect", effect.displayName);
            return;
        }
        send(sender, message("active"), "player", target.getName(), "effect", effect.displayName, "duration", formatDuration(suppression.expiresAt - now));
    }

    private boolean canUseAction(CommandSender sender, String action) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.hasPermission("xicemorepotioneffects.admin")) {
            return true;
        }
        String normalized = action.toLowerCase(Locale.ROOT);
        Set<String> allowed = new HashSet<>();
        for (String value : getConfig().getStringList("access.default-allowed-actions")) {
            allowed.add(value.toLowerCase(Locale.ROOT));
        }
        for (String value : getConfig().getStringList("access.players." + player.getUniqueId() + ".actions")) {
            allowed.add(value.toLowerCase(Locale.ROOT));
        }
        return allowed.contains(normalized);
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

    private void updateAllSidebars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateSidebar(player);
        }
    }

    private void updateSidebar(Player player) {
        List<ActiveEffect> activeEffects = activeEffects(player);
        if (activeEffects.isEmpty()) {
            restoreSidebar(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        Scoreboard board = sidebarBoards.computeIfAbsent(uuid, ignored -> createSidebarBoard());
        if (player.getScoreboard() != board) {
            previousBoards.putIfAbsent(uuid, player.getScoreboard());
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective(SIDEBAR_OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(SIDEBAR_OBJECTIVE, "dummy", color(getConfig().getString("sidebar.title", "&d自定义药水效果")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.setDisplayName(color(getConfig().getString("sidebar.title", "&d自定义药水效果")));

        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        int score = activeEffects.size();
        for (ActiveEffect effect : activeEffects) {
            objective.getScore(color(sidebarLine(effect))).setScore(score--);
        }
    }

    private void restoreSidebar(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard board = sidebarBoards.remove(uuid);
        Scoreboard previous = previousBoards.remove(uuid);
        if (board == null || player.getScoreboard() != board) {
            return;
        }
        if (previous != null) {
            player.setScoreboard(previous);
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private Scoreboard createSidebarBoard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available");
        }
        return manager.getNewScoreboard();
    }

    private List<ActiveEffect> activeEffects(Player player) {
        WarpSuppression suppression = suppressions.get(player.getUniqueId());
        if (suppression == null) {
            return List.of();
        }
        long remainingMillis = suppression.expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            suppressions.remove(player.getUniqueId());
            return List.of();
        }
        return List.of(new ActiveEffect(CustomEffect.WARP_SUPPRESSION, remainingSeconds(remainingMillis)));
    }

    private String sidebarLine(ActiveEffect effect) {
        String template = getConfig().getString("sidebar.line-format", "&5{effect} &f{seconds}s");
        return template
                .replace("{effect}", effect.effect.displayName)
                .replace("{seconds}", Long.toString(effect.seconds));
    }

    private long remainingSeconds(long remainingMillis) {
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
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
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("give", "clear", "check", "reload").stream()
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
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of(WARP_SUPPRESSION_ID, WARP_SUPPRESSION_NAME).stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 4 && List.of("give", "apply", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return List.of("30s", "1m", "5m", "10m").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private record WarpSuppression(long expiresAt) {
    }

    private record ActiveEffect(CustomEffect effect, long seconds) {
    }

    private enum CustomEffect {
        WARP_SUPPRESSION(WARP_SUPPRESSION_ID, WARP_SUPPRESSION_NAME);

        private final String id;
        private final String displayName;

        CustomEffect(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    private CustomEffect effectById(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals(WARP_SUPPRESSION_ID)
                || normalized.equals("warp")
                || normalized.equals("inhibit")
                || raw.trim().equals(WARP_SUPPRESSION_NAME)) {
            return CustomEffect.WARP_SUPPRESSION;
        }
        return null;
    }
}
