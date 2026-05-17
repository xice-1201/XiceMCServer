package com.xice.xicemc.auditlog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceAuditLogPlugin extends JavaPlugin implements Listener {
    private final Map<String, PendingContainerChange> pendingContainerChanges = new HashMap<>();
    private final Map<UUID, Long> sessionStarts = new HashMap<>();
    private AuditStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storage = createStorage();

        try {
            storage.start();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize audit database.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("XiceAuditLog enabled. Storage: postgresql");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.stop();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!getConfig().getBoolean("logging.block-break", true)) {
            return;
        }
        recordBlock(event.getPlayer(), "BLOCK_BREAK", event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!getConfig().getBoolean("logging.block-place", true)) {
            return;
        }
        recordBlock(event.getPlayer(), "BLOCK_PLACE", event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleContainerDiff(player, event.getView().getTopInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleContainerDiff(player, event.getView().getTopInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("logging.player-session", true)) {
            return;
        }
        Player player = event.getPlayer();
        sessionStarts.put(player.getUniqueId(), System.currentTimeMillis());
        recordPlayerLocation(player, "PLAYER_JOIN", 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!getConfig().getBoolean("logging.player-session", true)) {
            return;
        }
        Player player = event.getPlayer();
        Long startedAt = sessionStarts.remove(player.getUniqueId());
        int playSeconds = 0;
        if (startedAt != null) {
            playSeconds = Math.max(0, (int) ((System.currentTimeMillis() - startedAt) / 1000L));
        }
        recordPlayerLocation(player, "PLAYER_QUIT", playSeconds);
    }

    private void recordBlock(Player player, String action, Block block) {
        Location location = block.getLocation();
        enqueue(new AuditRecord(
                System.currentTimeMillis(),
                action,
                player.getUniqueId().toString(),
                player.getName(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                block.getType().name(),
                null,
                0,
                null));
    }

    private void scheduleContainerDiff(Player player, Inventory inventory) {
        if (!getConfig().getBoolean("logging.container-transfer", true)) {
            return;
        }

        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }

        String key = containerKey(player, location);
        if (pendingContainerChanges.containsKey(key)) {
            return;
        }

        PendingContainerChange pending = new PendingContainerChange(
                player.getUniqueId(),
                player.getName(),
                location.clone(),
                location.getBlock().getType().name(),
                inventory,
                ContainerSnapshot.capture(inventory));
        pendingContainerChanges.put(key, pending);

        getServer().getScheduler().runTask(this, () -> flushContainerDiff(key));
    }

    private void flushContainerDiff(String key) {
        PendingContainerChange pending = pendingContainerChanges.remove(key);
        if (pending == null || pending.location().getWorld() == null) {
            return;
        }

        Map<Material, Integer> after = ContainerSnapshot.capture(pending.inventory());
        Set<Material> materials = new HashSet<>();
        materials.addAll(pending.before().keySet());
        materials.addAll(after.keySet());

        for (Material material : materials) {
            int beforeAmount = pending.before().getOrDefault(material, 0);
            int afterAmount = after.getOrDefault(material, 0);
            int delta = afterAmount - beforeAmount;
            if (delta > 0) {
                recordContainer(pending, "CONTAINER_ADD", material, delta);
            } else if (delta < 0) {
                recordContainer(pending, "CONTAINER_REMOVE", material, -delta);
            }
        }
    }

    private void recordContainer(PendingContainerChange pending, String action, Material material, int amount) {
        Location location = pending.location();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        enqueue(new AuditRecord(
                System.currentTimeMillis(),
                action,
                pending.playerUuid().toString(),
                pending.playerName(),
                world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                pending.containerType(),
                material.name(),
                amount,
                null));
    }

    private void enqueue(AuditRecord record) {
        if (storage != null) {
            storage.enqueue(record);
        }
    }

    private void recordPlayerLocation(Player player, String action, int playSeconds) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        enqueue(new AuditRecord(
                System.currentTimeMillis(),
                action,
                player.getUniqueId().toString(),
                player.getName(),
                world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                "PLAYER",
                null,
                playSeconds,
                null));
    }

    private AuditStorage createStorage() {
        String type = getConfig().getString("storage.type", "postgresql").toLowerCase();
        int batchSize = getConfig().getInt("storage.batch-size", 100);
        int queueCapacity = getConfig().getInt("storage.queue-capacity", 20_000);
        if (!"postgresql".equals(type)) {
            throw new IllegalArgumentException("Unsupported storage type in this version: " + type);
        }

        String host = getConfig().getString("storage.postgresql.host", "127.0.0.1");
        int port = getConfig().getInt("storage.postgresql.port", 5432);
        String database = getConfig().getString("storage.postgresql.database", "xicemc_audit");
        String username = getConfig().getString("storage.postgresql.username", "xicemc_audit");
        String passwordEnv = getConfig().getString("storage.postgresql.password-env", "XICE_AUDIT_DB_PASSWORD");
        String password = System.getenv(passwordEnv);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Missing PostgreSQL password environment variable: " + passwordEnv);
        }

        String jdbcUrl = "jdbc:postgresql://"
                + host
                + ":"
                + port
                + "/"
                + database
                + "?reWriteBatchedInserts=true";
        return new PostgresAuditStorage(getLogger(), jdbcUrl, username, password, batchSize, queueCapacity);
    }

    private String containerKey(Player player, Location location) {
        return player.getUniqueId()
                + ":"
                + location.getWorld().getName()
                + ":"
                + location.getBlockX()
                + ":"
                + location.getBlockY()
                + ":"
                + location.getBlockZ();
    }
}
