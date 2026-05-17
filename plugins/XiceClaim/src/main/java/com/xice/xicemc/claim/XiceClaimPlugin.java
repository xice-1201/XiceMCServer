package com.xice.xicemc.claim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceClaimPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final Pattern CLAIM_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,24}$");

    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<String, ClaimRegion> claims = new HashMap<>();
    private File claimsFile;
    private FileConfiguration claimsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadClaims();
        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("claim");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getLogger().info("XiceClaim enabled. Claims: " + claims.size());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("xiceclaim.admin")) {
                send(sender, message("no-permission"));
                return true;
            }
            reloadConfig();
            loadClaims();
            send(sender, message("reload-complete"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, message("player-only"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos1" -> setPosition(player, true);
            case "pos2" -> setPosition(player, false);
            case "create" -> createClaim(player, args);
            case "info" -> showClaimInfo(player);
            case "list" -> listClaims(player);
            case "trust" -> trustPlayer(player, args, true);
            case "untrust" -> trustPlayer(player, args, false);
            case "delete", "remove" -> deleteClaim(player, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void setPosition(Player player, boolean first) {
        Location location = player.getLocation();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (first) {
            selection.pos1 = ClaimPoint.from(location);
            send(player, message("pos1-set"), "world", location.getWorld().getName(), "x", location.getBlockX(), "z", location.getBlockZ());
        } else {
            selection.pos2 = ClaimPoint.from(location);
            send(player, message("pos2-set"), "world", location.getWorld().getName(), "x", location.getBlockX(), "z", location.getBlockZ());
        }
    }

    private void createClaim(Player player, String[] args) {
        if (args.length < 2 || !CLAIM_NAME_PATTERN.matcher(args[1]).matches()) {
            send(player, message("invalid-name"));
            return;
        }
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.pos1 == null || selection.pos2 == null) {
            send(player, message("selection-missing"));
            return;
        }
        if (!selection.pos1.world.equals(selection.pos2.world)) {
            send(player, message("selection-world-mismatch"));
            return;
        }
        int maxClaims = getConfig().getInt("limits.max-claims-per-player", 5);
        long ownedCount = claims.values().stream().filter(claim -> claim.ownerUuid.equals(player.getUniqueId())).count();
        if (!player.hasPermission("xiceclaim.admin") && ownedCount >= maxClaims) {
            send(player, message("too-many-claims"), "limit", maxClaims);
            return;
        }

        ClaimRegion claim = ClaimRegion.fromSelection(
                UUID.randomUUID().toString(),
                args[1],
                player.getUniqueId(),
                player.getName(),
                selection.pos1,
                selection.pos2,
                getConfig().getInt("limits.world-min-y", -64),
                getConfig().getInt("limits.world-max-y", 320));
        int areaLimit = getConfig().getInt("limits.max-area-blocks", 40_000);
        if (!player.hasPermission("xiceclaim.admin") && claim.area() > areaLimit) {
            send(player, message("area-too-large"), "area", claim.area(), "limit", areaLimit);
            return;
        }
        ClaimRegion overlapping = firstOverlappingClaim(claim);
        if (overlapping != null) {
            send(player, message("overlap"), "claim", overlapping.name);
            return;
        }

        claims.put(claim.id, claim);
        saveClaims();
        send(player, message("created"), "claim", claim.name, "area", claim.area());
    }

    private void showClaimInfo(Player player) {
        ClaimRegion claim = claimAt(player.getLocation());
        if (claim == null) {
            send(player, message("not-in-claim"));
            return;
        }
        send(player, message("claim-info"), "claim", claim.name, "owner", claim.ownerName, "members", claim.memberNamesText());
    }

    private void listClaims(Player player) {
        List<ClaimRegion> owned = claims.values().stream()
                .filter(claim -> claim.ownerUuid.equals(player.getUniqueId()) || player.hasPermission("xiceclaim.admin"))
                .sorted(Comparator.comparing(claim -> claim.name.toLowerCase(Locale.ROOT)))
                .toList();
        if (owned.isEmpty()) {
            send(player, message("claim-list-empty"));
            return;
        }
        for (ClaimRegion claim : owned) {
            send(player, message("claim-list-line"),
                    "claim", claim.name,
                    "world", claim.world,
                    "minX", claim.minX,
                    "minZ", claim.minZ,
                    "maxX", claim.maxX,
                    "maxZ", claim.maxZ,
                    "members", claim.memberNamesText());
        }
    }

    private void trustPlayer(Player player, String[] args, boolean trust) {
        if (args.length < 2) {
            sendUsage(player);
            return;
        }
        ClaimRegion claim = claimAt(player.getLocation());
        if (claim == null) {
            send(player, message("not-in-claim"));
            return;
        }
        if (!canManage(player, claim)) {
            send(player, message("no-permission"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline()) {
            send(player, message("player-not-found"), "player", args[1]);
            return;
        }
        String targetName = target.getName() == null ? args[1] : target.getName();
        if (trust) {
            claim.members.add(target.getUniqueId());
            claim.memberNames.put(target.getUniqueId(), targetName);
            send(player, message("trusted"), "player", targetName, "claim", claim.name);
        } else {
            claim.members.remove(target.getUniqueId());
            claim.memberNames.remove(target.getUniqueId());
            send(player, message("untrusted"), "player", targetName, "claim", claim.name);
        }
        saveClaims();
    }

    private void deleteClaim(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player);
            return;
        }
        ClaimRegion claim = claimByOwnerAndName(player, args[1]);
        if (claim == null) {
            send(player, message("claim-not-found"), "claim", args[1]);
            return;
        }
        claims.remove(claim.id);
        saveClaims();
        send(player, message("deleted"), "claim", claim.name);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (getConfig().getBoolean("protection.block-break", true)) {
            protect(event.getPlayer(), event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (getConfig().getBoolean("protection.block-place", true)) {
            protect(event.getPlayer(), event.getBlockPlaced(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!getConfig().getBoolean("protection.block-interact", true) || event.getClickedBlock() == null) {
            return;
        }
        protect(event.getPlayer(), event.getClickedBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!getConfig().getBoolean("protection.container-open", true) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        Location location = inventory.getLocation();
        if (location == null) {
            return;
        }
        ClaimRegion claim = claimAt(location);
        if (claim != null && !claim.canUse(player)) {
            event.setCancelled(true);
            send(player, message("protected"), "claim", claim.name);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (getConfig().getBoolean("protection.entity-interact", true)) {
            protectEntity(event.getPlayer(), event.getRightClicked(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!getConfig().getBoolean("protection.entity-interact", true) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        protectEntity(player, event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (getConfig().getBoolean("protection.entity-interact", true) && event.getPlayer() != null) {
            protectEntity(event.getPlayer(), event.getEntity(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!getConfig().getBoolean("protection.entity-interact", true)) {
            return;
        }
        if (event.getRemover() instanceof Player player) {
            protectEntity(player, event.getEntity(), event);
            return;
        }
        if (claimAt(event.getEntity().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (getConfig().getBoolean("protection.bucket", true)) {
            protect(event.getPlayer(), event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (getConfig().getBoolean("protection.bucket", true)) {
            protect(event.getPlayer(), event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!getConfig().getBoolean("protection.fire", true)) {
            return;
        }
        ClaimRegion claim = claimAt(event.getBlock().getLocation());
        if (claim == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !claim.canUse(player)) {
            event.setCancelled(true);
            if (player != null) {
                send(player, message("protected"), "claim", claim.name);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (getConfig().getBoolean("protection.fire", true) && claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (getConfig().getBoolean("protection.explosion", true)) {
            event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (getConfig().getBoolean("protection.explosion", true)) {
            event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (getConfig().getBoolean("protection.piston", true) && pistonTouchesClaim(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (getConfig().getBoolean("protection.piston", true) && pistonTouchesClaim(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesClaim(Block piston, List<Block> movedBlocks) {
        if (claimAt(piston.getLocation()) != null) {
            return true;
        }
        for (Block block : movedBlocks) {
            if (claimAt(block.getLocation()) != null) {
                return true;
            }
        }
        return false;
    }

    private void protect(Player player, Block block, org.bukkit.event.Cancellable event) {
        ClaimRegion claim = claimAt(block.getLocation());
        if (claim == null || claim.canUse(player)) {
            return;
        }
        event.setCancelled(true);
        send(player, message("protected"), "claim", claim.name);
    }

    private void protectEntity(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        ClaimRegion claim = claimAt(entity.getLocation());
        if (claim == null || claim.canUse(player)) {
            return;
        }
        event.setCancelled(true);
        send(player, message("protected"), "claim", claim.name);
    }

    private ClaimRegion claimAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        for (ClaimRegion claim : claims.values()) {
            if (claim.contains(location)) {
                return claim;
            }
        }
        return null;
    }

    private ClaimRegion claimByOwnerAndName(Player player, String name) {
        for (ClaimRegion claim : claims.values()) {
            if (claim.name.equalsIgnoreCase(name) && (claim.ownerUuid.equals(player.getUniqueId()) || player.hasPermission("xiceclaim.admin"))) {
                return claim;
            }
        }
        return null;
    }

    private ClaimRegion firstOverlappingClaim(ClaimRegion candidate) {
        for (ClaimRegion claim : claims.values()) {
            if (claim.overlaps(candidate)) {
                return claim;
            }
        }
        return null;
    }

    private boolean canManage(Player player, ClaimRegion claim) {
        return claim.ownerUuid.equals(player.getUniqueId()) || player.hasPermission("xiceclaim.admin");
    }

    private void loadClaims() {
        claims.clear();
        claimsFile = new File(getDataFolder(), "claims.yml");
        if (!claimsFile.exists()) {
            getDataFolder().mkdirs();
        }
        claimsConfig = YamlConfiguration.loadConfiguration(claimsFile);
        ConfigurationSection root = claimsConfig.getConfigurationSection("claims");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                ClaimRegion claim = ClaimRegion.fromConfig(id, section);
                claims.put(claim.id, claim);
            } catch (Exception exception) {
                getLogger().warning("Failed to load claim " + id + ": " + exception.getMessage());
            }
        }
    }

    private void saveClaims() {
        claimsConfig.set("claims", null);
        for (ClaimRegion claim : claims.values()) {
            String path = "claims." + claim.id;
            claimsConfig.set(path + ".name", claim.name);
            claimsConfig.set(path + ".owner-uuid", claim.ownerUuid.toString());
            claimsConfig.set(path + ".owner-name", claim.ownerName);
            claimsConfig.set(path + ".world", claim.world);
            claimsConfig.set(path + ".min-x", claim.minX);
            claimsConfig.set(path + ".max-x", claim.maxX);
            claimsConfig.set(path + ".min-y", claim.minY);
            claimsConfig.set(path + ".max-y", claim.maxY);
            claimsConfig.set(path + ".min-z", claim.minZ);
            claimsConfig.set(path + ".max-z", claim.maxZ);
            claimsConfig.set(path + ".created-at", claim.createdAt);
            List<String> members = claim.members.stream().map(UUID::toString).sorted().toList();
            claimsConfig.set(path + ".members", members);
            for (Map.Entry<UUID, String> entry : claim.memberNames.entrySet()) {
                claimsConfig.set(path + ".member-names." + entry.getKey(), entry.getValue());
            }
        }
        try {
            claimsConfig.save(claimsFile);
        } catch (IOException exception) {
            getLogger().warning("Failed to save claims.yml: " + exception.getMessage());
        }
    }

    private void sendUsage(CommandSender sender) {
        for (String line : getConfig().getStringList("messages.usage")) {
            send(sender, line);
        }
    }

    private String message(String key) {
        return getConfig().getString("messages." + key, "");
    }

    private void send(CommandSender sender, String text, Object... replacements) {
        String value = text == null ? "" : text;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            value = value.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
        }
        sender.sendMessage(color(getConfig().getString("messages.prefix", "") + value));
    }

    private String color(String value) {
        return value.replace('&', '\u00A7');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("help", "pos1", "pos2", "create", "info", "list", "trust", "untrust", "delete", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && sender instanceof Player player && ("delete".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return claims.values().stream()
                    .filter(claim -> claim.ownerUuid.equals(player.getUniqueId()) || player.hasPermission("xiceclaim.admin"))
                    .map(claim -> claim.name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && ("trust".equalsIgnoreCase(args[0]) || "untrust".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private static final class Selection {
        private ClaimPoint pos1;
        private ClaimPoint pos2;
    }

    private record ClaimPoint(String world, int x, int z) {
        private static ClaimPoint from(Location location) {
            return new ClaimPoint(Objects.requireNonNull(location.getWorld()).getName(), location.getBlockX(), location.getBlockZ());
        }
    }

    private static final class ClaimRegion {
        private final String id;
        private final String name;
        private final UUID ownerUuid;
        private final String ownerName;
        private final String world;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final long createdAt;
        private final Set<UUID> members;
        private final Map<UUID, String> memberNames;

        private ClaimRegion(
                String id,
                String name,
                UUID ownerUuid,
                String ownerName,
                String world,
                int minX,
                int maxX,
                int minY,
                int maxY,
                int minZ,
                int maxZ,
                long createdAt,
                Set<UUID> members,
                Map<UUID, String> memberNames
        ) {
            this.id = id;
            this.name = name;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.world = world;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.createdAt = createdAt;
            this.members = members;
            this.memberNames = memberNames;
        }

        private static ClaimRegion fromSelection(
                String id,
                String name,
                UUID ownerUuid,
                String ownerName,
                ClaimPoint pos1,
                ClaimPoint pos2,
                int minY,
                int maxY
        ) {
            return new ClaimRegion(
                    id,
                    name,
                    ownerUuid,
                    ownerName,
                    pos1.world,
                    Math.min(pos1.x, pos2.x),
                    Math.max(pos1.x, pos2.x),
                    minY,
                    maxY,
                    Math.min(pos1.z, pos2.z),
                    Math.max(pos1.z, pos2.z),
                    System.currentTimeMillis(),
                    new HashSet<>(),
                    new HashMap<>());
        }

        private static ClaimRegion fromConfig(String id, ConfigurationSection section) {
            Set<UUID> members = new HashSet<>();
            for (String value : section.getStringList("members")) {
                members.add(UUID.fromString(value));
            }
            Map<UUID, String> memberNames = new HashMap<>();
            ConfigurationSection names = section.getConfigurationSection("member-names");
            if (names != null) {
                for (String key : names.getKeys(false)) {
                    memberNames.put(UUID.fromString(key), names.getString(key, key));
                }
            }
            return new ClaimRegion(
                    id,
                    section.getString("name", id),
                    UUID.fromString(section.getString("owner-uuid")),
                    section.getString("owner-name", "unknown"),
                    section.getString("world", "main"),
                    section.getInt("min-x"),
                    section.getInt("max-x"),
                    section.getInt("min-y"),
                    section.getInt("max-y"),
                    section.getInt("min-z"),
                    section.getInt("max-z"),
                    section.getLong("created-at", System.currentTimeMillis()),
                    members,
                    memberNames);
        }

        private boolean contains(Location location) {
            return location.getWorld() != null
                    && world.equals(location.getWorld().getName())
                    && location.getBlockX() >= minX
                    && location.getBlockX() <= maxX
                    && location.getBlockY() >= minY
                    && location.getBlockY() <= maxY
                    && location.getBlockZ() >= minZ
                    && location.getBlockZ() <= maxZ;
        }

        private boolean overlaps(ClaimRegion other) {
            return world.equals(other.world)
                    && minX <= other.maxX
                    && maxX >= other.minX
                    && minY <= other.maxY
                    && maxY >= other.minY
                    && minZ <= other.maxZ
                    && maxZ >= other.minZ;
        }

        private boolean canUse(Player player) {
            return ownerUuid.equals(player.getUniqueId())
                    || members.contains(player.getUniqueId())
                    || player.hasPermission("xiceclaim.admin");
        }

        private int area() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        private String memberNamesText() {
            if (members.isEmpty()) {
                return "无";
            }
            List<String> names = new ArrayList<>();
            for (UUID uuid : members) {
                names.add(memberNames.getOrDefault(uuid, uuid.toString()));
            }
            names.sort(String::compareToIgnoreCase);
            return String.join(", ", names);
        }
    }
}
