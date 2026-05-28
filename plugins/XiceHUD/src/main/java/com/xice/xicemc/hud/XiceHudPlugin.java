package com.xice.xicemc.hud;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class XiceHudPlugin extends JavaPlugin implements HudService, Listener, CommandExecutor, TabCompleter {
    private static final String ECONOMY_ACTION_BAR_OWNER = "xicehud:economy";
    private static final String WORLD_TAB_OWNER = "xicehud:world";
    private static final String SIDEBAR_OBJECTIVE = "xicehud_sidebar";

    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private final Set<UUID> pendingEconomyQueries = new HashSet<>();
    private final Map<UUID, Long> suppressEconomyUntil = new HashMap<>();
    private final Map<UUID, String> cachedEconomyLines = new HashMap<>();
    private final Map<UUID, Map<String, ActionBarEntry>> actionBars = new HashMap<>();
    private final Map<UUID, String> lastActionBarText = new HashMap<>();
    private final Map<UUID, Map<String, SidebarSection>> sidebars = new HashMap<>();
    private final Map<UUID, Scoreboard> sidebarBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private final Map<UUID, Map<String, TabListWorldEntry>> tabListWorlds = new HashMap<>();
    private final Map<UUID, String> lastTabListNames = new HashMap<>();
    private final Set<ManagedHudBossBar> bossBars = new HashSet<>();
    private final Consumer<UUID> economyChangeListener = this::handleEconomyBalanceChanged;
    private BukkitTask hudTask;
    private boolean economyListenerRegistered;
    private boolean enabledByDefault;
    private boolean economyEnabled;
    private String economyPluginName;
    private String economyOffset;
    private String economyFormat;
    private String economyUnavailable;
    private List<String> economyHiddenWorldPrefixes = List.of();
    private boolean tabListEnabled;
    private String tabListFormat;
    private Map<String, String> tabListWorldNames = Map.of();
    private int updateIntervalTicks;
    private int retryAfterErrorTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadTabListSettings();
        Bukkit.getServicesManager().register(HudService.class, this, this, ServicePriority.Normal);
        registerCommand();
        getServer().getPluginManager().registerEvents(this, this);
        registerEconomyListener();
        startHudTask();
        getLogger().info("XiceHUD enabled.");
    }

    @Override
    public void onDisable() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        unregisterEconomyListener();
        Bukkit.getServicesManager().unregister(HudService.class, this);
        for (ManagedHudBossBar bossBar : List.copyOf(bossBars)) {
            bossBar.removeAll();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearAllPlayerHud(player);
        }
        pendingEconomyQueries.clear();
        suppressEconomyUntil.clear();
        cachedEconomyLines.clear();
        actionBars.clear();
        lastActionBarText.clear();
        sidebars.clear();
        sidebarBoards.clear();
        previousBoards.clear();
        tabListWorlds.clear();
        lastTabListNames.clear();
        bossBars.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only");
                    return true;
                }
                if (!player.hasPermission("xicehud.use")) {
                    send(player, "no-permission");
                    return true;
                }
                enabledPlayers.add(player.getUniqueId());
                disabledPlayers.remove(player.getUniqueId());
                send(player, "enabled");
                requestEconomyBalance(player, true);
            }
            case "off" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only");
                    return true;
                }
                if (!player.hasPermission("xicehud.use")) {
                    send(player, "no-permission");
                    return true;
                }
                enabledPlayers.remove(player.getUniqueId());
                disabledPlayers.add(player.getUniqueId());
                clearActionBar(player.getUniqueId(), ECONOMY_ACTION_BAR_OWNER);
                renderActionBar(player);
                send(player, "disabled");
            }
            case "reload" -> {
                if (!sender.hasPermission("xicehud.admin")) {
                    send(sender, "no-permission");
                    return true;
                }
                unregisterEconomyListener();
                reloadConfig();
                loadSettings();
                loadTabListSettings();
                cachedEconomyLines.clear();
                lastTabListNames.clear();
                registerEconomyListener();
                startHudTask();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    updateDefaultTabListWorld(online);
                    renderTabListName(online);
                }
                send(sender, "reload-complete");
            }
            default -> send(sender, "usage");
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return java.util.List.of("on", "off", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return java.util.List.of();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            requestEconomyBalance(player, true);
            updateDefaultTabListWorld(player);
            renderTabListName(player);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, () -> {
            updateDefaultTabListWorld(player);
            renderTabListName(player);
            if (isEconomyHiddenWorld(player)) {
                clearActionBar(player.getUniqueId(), ECONOMY_ACTION_BAR_OWNER);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        pendingEconomyQueries.remove(playerUuid);
        suppressEconomyUntil.remove(playerUuid);
        cachedEconomyLines.remove(playerUuid);
        actionBars.remove(playerUuid);
        lastActionBarText.remove(playerUuid);
        sidebars.remove(playerUuid);
        sidebarBoards.remove(playerUuid);
        previousBoards.remove(playerUuid);
        tabListWorlds.remove(playerUuid);
        lastTabListNames.remove(playerUuid);
    }

    private void loadSettings() {
        enabledByDefault = getConfig().getBoolean("hud.enabled-by-default", true);
        updateIntervalTicks = Math.max(10, getConfig().getInt("hud.update-interval-ticks", 40));
        retryAfterErrorTicks = Math.max(updateIntervalTicks, getConfig().getInt("hud.retry-after-error-ticks", 200));
        economyEnabled = getConfig().getBoolean("modules.economy.enabled", true);
        economyPluginName = getConfig().getString("modules.economy.plugin-name", "XiceEconomy");
        economyOffset = getConfig().getString("modules.economy.offset", "");
        economyFormat = getConfig().getString("modules.economy.format", "{offset}&f{coin} &e{balance}");
        List<String> hiddenPrefixes = getConfig().getStringList("modules.economy.hidden-world-prefixes").stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .toList();
        economyHiddenWorldPrefixes = hiddenPrefixes.isEmpty() ? List.of("xicerpg_instance_") : hiddenPrefixes;
        economyUnavailable = getConfig().getString("modules.economy.unavailable", "&7货币: 暂不可用");
    }

    private void loadTabListSettings() {
        tabListEnabled = getConfig().getBoolean("modules.tab-list.enabled", true);
        tabListFormat = getConfig().getString("modules.tab-list.format", "&7[{world}]&f{player}");
        Map<String, String> configuredWorldNames = new HashMap<>();
        configuredWorldNames.put("main", "主服务器");
        configuredWorldNames.put("main_nether", "主服务器下界");
        configuredWorldNames.put("main_the_end", "主服务器末地");
        if (getConfig().isConfigurationSection("modules.tab-list.worlds")) {
            for (String worldName : Objects.requireNonNull(getConfig().getConfigurationSection("modules.tab-list.worlds")).getKeys(false)) {
                configuredWorldNames.put(worldName, getConfig().getString("modules.tab-list.worlds." + worldName, worldName));
            }
        }
        tabListWorldNames = Map.copyOf(configuredWorldNames);
    }

    private void startHudTask() {
        if (hudTask != null) {
            hudTask.cancel();
        }
        hudTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendCachedHud(player);
                renderActionBar(player);
                renderSidebar(player);
                updateDefaultTabListWorld(player);
                renderTabListName(player);
            }
        }, 20L, 20L);
    }

    private void sendCachedHud(Player player) {
        if (!isHudEnabled(player)) {
            return;
        }
        if (!economyEnabled) {
            return;
        }
        if (isEconomyHiddenWorld(player)) {
            clearActionBar(player.getUniqueId(), ECONOMY_ACTION_BAR_OWNER);
            return;
        }
        String cached = cachedEconomyLines.get(player.getUniqueId());
        if (cached == null) {
            requestEconomyBalance(player, false);
            return;
        }
        setActionBar(player.getUniqueId(), ECONOMY_ACTION_BAR_OWNER, cached, 0, Math.max(40L, updateIntervalTicks + 10L));
    }

    private boolean isHudEnabled(Player player) {
        UUID playerUuid = player.getUniqueId();
        return enabledByDefault ? !disabledPlayers.contains(playerUuid) : enabledPlayers.contains(playerUuid);
    }

    private boolean isEconomyHiddenWorld(Player player) {
        String worldName = player.getWorld().getName();
        return economyHiddenWorldPrefixes.stream().anyMatch(worldName::startsWith);
    }

    @Override
    public void setActionBar(UUID playerUuid, String owner, String legacyText, int priority, long ttlTicks) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(owner, "owner");
        long expiresAt = System.currentTimeMillis() + Math.max(1L, ttlTicks) * 50L;
        actionBars.computeIfAbsent(playerUuid, ignored -> new HashMap<>())
                .put(owner, new ActionBarEntry(owner, legacyText == null ? "" : legacyText, priority, expiresAt));
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            renderActionBar(player);
        }
    }

    @Override
    public void clearActionBar(UUID playerUuid, String owner) {
        Map<String, ActionBarEntry> entries = actionBars.get(playerUuid);
        if (entries != null) {
            entries.remove(owner);
            if (entries.isEmpty()) {
                actionBars.remove(playerUuid);
            }
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            renderActionBar(player);
        }
    }

    @Override
    public void setSidebar(UUID playerUuid, String owner, String legacyTitle, List<String> legacyLines, int priority) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(owner, "owner");
        List<String> lines = legacyLines == null ? List.of() : List.copyOf(legacyLines);
        if (lines.isEmpty()) {
            clearSidebar(playerUuid, owner);
            return;
        }
        sidebars.computeIfAbsent(playerUuid, ignored -> new HashMap<>())
                .put(owner, new SidebarSection(owner, legacyTitle == null ? "" : legacyTitle, lines, priority));
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            renderSidebar(player);
        }
    }

    @Override
    public void clearSidebar(UUID playerUuid, String owner) {
        Map<String, SidebarSection> sections = sidebars.get(playerUuid);
        if (sections != null) {
            sections.remove(owner);
            if (sections.isEmpty()) {
                sidebars.remove(playerUuid);
            }
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            renderSidebar(player);
        }
    }

    @Override
    public HudBossBar createBossBar(String owner, String legacyTitle, BarColor color, BarStyle style) {
        ManagedHudBossBar bossBar = new ManagedHudBossBar(
                owner == null ? "unknown" : owner,
                Bukkit.createBossBar(color(legacyTitle == null ? "" : legacyTitle), color, style));
        bossBars.add(bossBar);
        return bossBar;
    }

    @Override
    public void setTabListWorld(UUID playerUuid, String owner, String legacyWorldName, int priority) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(owner, "owner");
        if (legacyWorldName == null || legacyWorldName.isBlank()) {
            clearTabListWorld(playerUuid, owner);
            return;
        }
        tabListWorlds.computeIfAbsent(playerUuid, ignored -> new HashMap<>())
                .put(owner, new TabListWorldEntry(owner, legacyWorldName, priority));
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            renderTabListName(player);
        }
    }

    @Override
    public void clearTabListWorld(UUID playerUuid, String owner) {
        Map<String, TabListWorldEntry> entries = tabListWorlds.get(playerUuid);
        if (entries != null) {
            entries.remove(owner);
            if (entries.isEmpty()) {
                tabListWorlds.remove(playerUuid);
            }
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            renderTabListName(player);
        }
    }

    private void renderActionBar(Player player) {
        UUID playerUuid = player.getUniqueId();
        Map<String, ActionBarEntry> entries = actionBars.get(playerUuid);
        long now = System.currentTimeMillis();
        ActionBarEntry selected = null;
        if (entries != null) {
            entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            if (entries.isEmpty()) {
                actionBars.remove(playerUuid);
            } else {
                selected = entries.values().stream()
                        .max(Comparator.comparingInt(ActionBarEntry::priority).thenComparing(ActionBarEntry::owner))
                        .orElse(null);
            }
        }
        if (selected == null) {
            if (lastActionBarText.remove(playerUuid) != null) {
                player.sendActionBar(Component.empty());
            }
            return;
        }
        String text = selected.legacyText();
        if (!text.equals(lastActionBarText.get(playerUuid))) {
            sendActionBar(player, text);
            lastActionBarText.put(playerUuid, text);
        } else {
            sendActionBar(player, text);
        }
    }

    private void renderSidebar(Player player) {
        UUID playerUuid = player.getUniqueId();
        Map<String, SidebarSection> sections = sidebars.get(playerUuid);
        if (sections == null || sections.isEmpty()) {
            restoreSidebar(player);
            return;
        }
        Scoreboard board = sidebarBoards.computeIfAbsent(playerUuid, ignored -> createSidebarBoard());
        if (player.getScoreboard() != board) {
            previousBoards.putIfAbsent(playerUuid, player.getScoreboard());
            player.setScoreboard(board);
        }
        List<SidebarSection> ordered = sections.values().stream()
                .sorted(Comparator.comparingInt(SidebarSection::priority).reversed().thenComparing(SidebarSection::owner))
                .toList();
        SidebarSection primary = ordered.getFirst();
        Objective objective = board.getObjective(SIDEBAR_OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(SIDEBAR_OBJECTIVE, "dummy", color(primary.legacyTitle()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.setDisplayName(color(primary.legacyTitle()));
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        List<String> lines = new ArrayList<>();
        for (SidebarSection section : ordered) {
            lines.addAll(section.legacyLines());
        }
        int score = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            objective.getScore(uniqueSidebarEntry(color(lines.get(index)), index)).setScore(score--);
        }
    }

    private void restoreSidebar(Player player) {
        UUID playerUuid = player.getUniqueId();
        Scoreboard board = sidebarBoards.remove(playerUuid);
        Scoreboard previous = previousBoards.remove(playerUuid);
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

    private void updateDefaultTabListWorld(Player player) {
        if (!tabListEnabled) {
            clearTabListWorld(player.getUniqueId(), WORLD_TAB_OWNER);
            return;
        }
        setTabListWorld(player.getUniqueId(), WORLD_TAB_OWNER, displayNameForWorld(player.getWorld().getName()), 0);
    }

    private String displayNameForWorld(String worldName) {
        String configured = tabListWorldNames.get(worldName);
        return configured == null || configured.isBlank() ? worldName : configured;
    }

    private void renderTabListName(Player player) {
        if (!tabListEnabled) {
            restoreTabListName(player);
            return;
        }
        Map<String, TabListWorldEntry> entries = tabListWorlds.get(player.getUniqueId());
        if (entries == null || entries.isEmpty()) {
            restoreTabListName(player);
            return;
        }
        TabListWorldEntry selected = entries.values().stream()
                .max(Comparator.comparingInt(TabListWorldEntry::priority).thenComparing(TabListWorldEntry::owner))
                .orElse(null);
        if (selected == null) {
            restoreTabListName(player);
            return;
        }
        String text = tabListFormat
                .replace("{world}", selected.legacyWorldName())
                .replace("{player}", player.getName());
        if (text.equals(lastTabListNames.get(player.getUniqueId()))) {
            return;
        }
        player.playerListName(LegacyComponentSerializer.legacySection().deserialize(color(text)));
        lastTabListNames.put(player.getUniqueId(), text);
    }

    private void restoreTabListName(Player player) {
        if (lastTabListNames.remove(player.getUniqueId()) != null) {
            player.playerListName(Component.text(player.getName()));
        }
    }

    private Scoreboard createSidebarBoard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available");
        }
        return manager.getNewScoreboard();
    }

    private String uniqueSidebarEntry(String line, int index) {
        ChatColor[] colors = ChatColor.values();
        return line + colors[index % colors.length];
    }

    private void clearAllPlayerHud(Player player) {
        UUID playerUuid = player.getUniqueId();
        actionBars.remove(playerUuid);
        lastActionBarText.remove(playerUuid);
        player.sendActionBar(Component.empty());
        sidebars.remove(playerUuid);
        restoreSidebar(player);
        tabListWorlds.remove(playerUuid);
        restoreTabListName(player);
    }

    private void requestEconomyBalance(Player player, boolean sendImmediately) {
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (isEconomyHiddenWorld(player)) {
            clearActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER);
            return;
        }
        if (pendingEconomyQueries.contains(playerUuid)) {
            return;
        }
        if (suppressEconomyUntil.getOrDefault(playerUuid, 0L) > now) {
            cachedEconomyLines.put(playerUuid, economyUnavailable);
            if (sendImmediately) {
                setActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER, economyUnavailable, 0, Math.max(40L, updateIntervalTicks + 10L));
            }
            return;
        }

        Plugin economyPlugin = getServer().getPluginManager().getPlugin(economyPluginName);
        if (economyPlugin == null || !economyPlugin.isEnabled()) {
            suppressEconomyUntil.put(playerUuid, now + retryAfterErrorTicks * 50L);
            cachedEconomyLines.put(playerUuid, economyUnavailable);
            if (sendImmediately) {
                setActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER, economyUnavailable, 0, Math.max(40L, updateIntervalTicks + 10L));
            }
            return;
        }

        Method method;
        try {
            method = economyPlugin.getClass().getMethod(
                    "requestFormattedBalanceValue",
                    UUID.class,
                    String.class,
                    Consumer.class,
                    Consumer.class);
        } catch (ReflectiveOperationException exception) {
            try {
                method = economyPlugin.getClass().getMethod(
                        "requestFormattedBalance",
                        UUID.class,
                        String.class,
                        Consumer.class,
                        Consumer.class);
            } catch (ReflectiveOperationException fallbackException) {
                suppressEconomyUntil.put(playerUuid, now + retryAfterErrorTicks * 50L);
                cachedEconomyLines.put(playerUuid, economyUnavailable);
                if (sendImmediately) {
                    setActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER, economyUnavailable, 0, Math.max(40L, updateIntervalTicks + 10L));
                }
                getLogger().log(Level.WARNING, "Economy plugin does not expose a supported balance API.", fallbackException);
                return;
            }
        }

        pendingEconomyQueries.add(playerUuid);
        Consumer<String> success = balance -> {
            pendingEconomyQueries.remove(playerUuid);
            Player online = Bukkit.getPlayer(playerUuid);
            if (online != null && online.isOnline() && isHudEnabled(online)) {
                String line = renderEconomyLine(balance);
                cachedEconomyLines.put(playerUuid, line);
                setActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER, line, 0, Math.max(40L, updateIntervalTicks + 10L));
            }
        };
        Consumer<Exception> failure = error -> {
            pendingEconomyQueries.remove(playerUuid);
            suppressEconomyUntil.put(playerUuid, System.currentTimeMillis() + retryAfterErrorTicks * 50L);
            cachedEconomyLines.put(playerUuid, economyUnavailable);
            Player online = Bukkit.getPlayer(playerUuid);
            if (sendImmediately && online != null && online.isOnline() && isHudEnabled(online)) {
                setActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER, economyUnavailable, 0, Math.max(40L, updateIntervalTicks + 10L));
            }
            getLogger().log(Level.WARNING, "Failed to fetch economy balance for HUD: " + player.getName(), error);
        };

        try {
            method.invoke(economyPlugin, playerUuid, player.getName(), success, failure);
        } catch (ReflectiveOperationException exception) {
            pendingEconomyQueries.remove(playerUuid);
            suppressEconomyUntil.put(playerUuid, now + retryAfterErrorTicks * 50L);
            cachedEconomyLines.put(playerUuid, economyUnavailable);
            if (sendImmediately) {
                setActionBar(playerUuid, ECONOMY_ACTION_BAR_OWNER, economyUnavailable, 0, Math.max(40L, updateIntervalTicks + 10L));
            }
            getLogger().log(Level.WARNING, "Failed to call economy balance API.", exception);
        }
    }

    private String renderEconomyLine(String balance) {
        return economyFormat
                .replace("{offset}", economyOffset)
                .replace("{coin}", "\uE000")
                .replace("{balance}", balance);
    }

    private void handleEconomyBalanceChanged(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline() && isHudEnabled(player)) {
            requestEconomyBalance(player, true);
        } else {
            cachedEconomyLines.remove(playerUuid);
        }
    }

    private void registerEconomyListener() {
        if (economyListenerRegistered) {
            return;
        }
        Plugin economyPlugin = getServer().getPluginManager().getPlugin(economyPluginName);
        if (economyPlugin == null || !economyPlugin.isEnabled()) {
            return;
        }
        try {
            Method method = economyPlugin.getClass().getMethod("registerBalanceChangeListener", Consumer.class);
            method.invoke(economyPlugin, economyChangeListener);
            economyListenerRegistered = true;
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Economy plugin does not expose balance listener API.", exception);
        }
    }

    private void unregisterEconomyListener() {
        if (!economyListenerRegistered) {
            return;
        }
        Plugin economyPlugin = getServer().getPluginManager().getPlugin(economyPluginName);
        if (economyPlugin == null) {
            economyListenerRegistered = false;
            return;
        }
        try {
            Method method = economyPlugin.getClass().getMethod("unregisterBalanceChangeListener", Consumer.class);
            method.invoke(economyPlugin, economyChangeListener);
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Failed to unregister economy balance listener.", exception);
        } finally {
            economyListenerRegistered = false;
        }
    }

    private void sendActionBar(Player player, String legacyText) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
        player.sendActionBar(component);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("xicehud");
        if (command == null) {
            throw new IllegalStateException("Missing command declaration: xicehud");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    private void send(CommandSender sender, String key) {
        String text = getConfig().getString("messages." + key, key);
        String prefix = getConfig().getString("messages.prefix", "");
        if (!"prefix".equals(key)) {
            text = prefix + text;
        }
        sender.sendMessage(color(text));
    }

    private record ActionBarEntry(String owner, String legacyText, int priority, long expiresAt) {
    }

    private record SidebarSection(String owner, String legacyTitle, List<String> legacyLines, int priority) {
    }

    private record TabListWorldEntry(String owner, String legacyWorldName, int priority) {
    }

    private final class ManagedHudBossBar implements HudBossBar {
        private final String owner;
        private final BossBar bossBar;

        private ManagedHudBossBar(String owner, BossBar bossBar) {
            this.owner = owner;
            this.bossBar = bossBar;
        }

        @Override
        public void setTitle(String legacyTitle) {
            bossBar.setTitle(color(legacyTitle));
        }

        @Override
        public void setProgress(double progress) {
            bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));
        }

        @Override
        public void setColor(BarColor color) {
            bossBar.setColor(color);
        }

        @Override
        public void setVisible(boolean visible) {
            bossBar.setVisible(visible);
        }

        @Override
        public void addPlayer(Player player) {
            bossBar.addPlayer(player);
        }

        @Override
        public void removePlayer(Player player) {
            bossBar.removePlayer(player);
        }

        @Override
        public void removeAll() {
            bossBar.removeAll();
            bossBars.remove(this);
        }

        @Override
        public List<Player> getPlayers() {
            return bossBar.getPlayers();
        }

        @Override
        public String toString() {
            return owner;
        }
    }
}
