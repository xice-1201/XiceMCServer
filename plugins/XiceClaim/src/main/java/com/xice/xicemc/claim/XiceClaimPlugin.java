package com.xice.xicemc.claim;

import com.xice.xicemc.customitem.CustomBlockService;
import com.xice.xicemc.customitem.CustomItemDefinition;
import com.xice.xicemc.customitem.CustomItemService;
import com.xice.xicemc.customitem.CustomMultiBlockDefinition;
import com.xice.xicemc.customitem.CustomMultiBlockPart;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class XiceClaimPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final Pattern CLAIM_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{1,24}$");
    private static final Material TOTEM_ITEM_MATERIAL = Material.QUARTZ_BLOCK;
    private static final Material TOTEM_CORE_ITEM_MATERIAL = Material.NETHER_BRICK;
    private static final Material TOTEM_BOTTOM_MATERIAL = Material.JIGSAW;
    private static final Material TOTEM_TOP_MATERIAL = Material.STRUCTURE_BLOCK;
    private static final int TOTEM_CORE_SLOT = 4;
    private static final int TOTEM_AURA_PERIOD_TICKS = 200;
    private static final int TOTEM_AURA_DURATION_TICKS = 500;
    private static final int TOTEM_CLIENT_VIEW_PERIOD_TICKS = 40;
    private static final long CHAOTIC_WARP_SUPPRESSION_MILLIS = 15L * 60L * 1000L;
    private static final int CHAOTIC_WARP_EFFECT_DURATION_TICKS = 20 * 20;
    private static final int CHAOTIC_WARP_RANDOM_ATTEMPTS = 64;
    private static final int CHAOTIC_WARP_MAX_CONCURRENT_SEARCHES = 2;
    private static final int CHAOTIC_WARP_QUEUE_EXTRA_SIZE = 2;
    private static final int CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS = 2;
    private static final int TEMPORARY_VIEW_DISTANCE = 2;
    private static final long PRE_TELEPORT_VIEW_DISTANCE_LEAD_TICKS = 10L;
    private static final long VIEW_DISTANCE_RAMP_INTERVAL_TICKS = 20L;
    private static final int DEFAULT_SERVER_MAX_WORLD_SIZE = 29_999_984;
    private static final int DEFAULT_CLAIM_WORLD_BORDER = 50_000;
    private static final double TELEPORT_MOVE_CANCEL_DISTANCE_SQUARED = 0.0009D;

    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<String, ClaimRegion> claims = new HashMap<>();
    private final Map<UUID, RingSession> ringSessions = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, PlayerViewDistanceState> temporaryViewDistances = new HashMap<>();
    private final Map<String, ChaoticWarpQueue> chaoticWarpQueues = new HashMap<>();
    private BukkitTask totemAuraTask;
    private BukkitTask totemClientViewTask;
    private BukkitTask ringPreviewTask;
    private int maxOnlineSinceStart;
    private int serverMaxWorldSize = DEFAULT_SERVER_MAX_WORLD_SIZE;
    private int claimWorldBorder = DEFAULT_CLAIM_WORLD_BORDER;
    private File claimsFile;
    private File chaoticWarpQueuesFile;
    private FileConfiguration claimsConfig;
    private CustomItemService customItemService;
    private CustomBlockService customBlockService;
    private NamespacedKey ringKey;
    private NamespacedKey ringClaimIdKey;
    private NamespacedKey ringClaimNameKey;
    private NamespacedKey ringWorldKey;
    private NamespacedKey ringX1Key;
    private NamespacedKey ringY1Key;
    private NamespacedKey ringZ1Key;
    private NamespacedKey ringX2Key;
    private NamespacedKey ringY2Key;
    private NamespacedKey ringZ2Key;
    private NamespacedKey ringRecipeKey;
    private NamespacedKey ringItemModelKey;
    private NamespacedKey totemKey;
    private NamespacedKey totemItemModelKey;
    private NamespacedKey totemRecipeKey;
    private NamespacedKey totemCoreKey;
    private NamespacedKey totemCoreItemModelKey;
    private NamespacedKey totemCoreRecipeKey;
    private NamespacedKey chaoticWarpCoreKey;
    private NamespacedKey chaoticWarpCoreItemModelKey;
    private NamespacedKey chaoticWarpCoreRecipeKey;
    private NamespacedKey totemIdKey;
    private NamespacedKey totemPartKey;
    private NamespacedKey totemFrontKey;
    private NamespacedKey totemClaimIdKey;
    private NamespacedKey totemOwnerUuidKey;
    private NamespacedKey totemOwnerNameKey;
    private NamespacedKey totemPlacedAtKey;
    private NamespacedKey totemDisplayKey;
    private CustomMultiBlockDefinition claimTotemStructureDefinition;

    @Override
    public void onEnable() {
        initializeKeys();
        saveDefaultConfig();
        customItemService = Bukkit.getServicesManager().load(CustomItemService.class);
        if (customItemService == null) {
            getLogger().severe("XiceCustomItem service is not available.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        customBlockService = Bukkit.getServicesManager().load(CustomBlockService.class);
        if (customBlockService == null) {
            getLogger().severe("XiceCustomItem custom block service is not available.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        registerCustomItems();
        registerCustomMultiBlocks();
        registerClaimRingRecipe();
        registerClaimTotemRecipe();
        registerTotemCoreRecipe();
        registerChaoticWarpCoreRecipe();
        loadClaims();
        maxOnlineSinceStart = Bukkit.getOnlinePlayers().size();
        configureChaoticWarpBounds();
        loadChaoticWarpQueues();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::refreshLoadedTotemBlocks);
        totemAuraTask = Bukkit.getScheduler().runTaskTimer(this, this::applyTotemAuras, TOTEM_AURA_PERIOD_TICKS, TOTEM_AURA_PERIOD_TICKS);
        totemClientViewTask = Bukkit.getScheduler().runTaskTimer(this, () -> sendLoadedTotemClientViews(), TOTEM_CLIENT_VIEW_PERIOD_TICKS, TOTEM_CLIENT_VIEW_PERIOD_TICKS);
        int previewIntervalTicks = Math.max(1, getConfig().getInt("visualization.interval-ticks", 10));
        ringPreviewTask = Bukkit.getScheduler().runTaskTimer(this, this::showHeldRingPreviews, 0L, previewIntervalTicks);
        topUpChaoticWarpQueues();
        var command = getCommand("claim");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            synchronizePlayerRingNames(player);
            customItemService.discoverRecipe(player, chaoticWarpCoreRecipeKey);
        }
        getLogger().info("XiceClaim enabled. Claims: " + claims.size());
    }

    @Override
    public void onDisable() {
        for (PendingTeleport pending : pendingTeleports.values()) {
            pending.cancel();
        }
        pendingTeleports.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayerViewDistance(player, "disable");
        }
        if (totemAuraTask != null) {
            totemAuraTask.cancel();
            totemAuraTask = null;
        }
        if (totemClientViewTask != null) {
            totemClientViewTask.cancel();
            totemClientViewTask = null;
        }
        if (ringPreviewTask != null) {
            ringPreviewTask.cancel();
            ringPreviewTask = null;
        }
        if (customItemService != null) {
            customItemService.unregisterRecipe(ringRecipeKey);
            customItemService.unregisterRecipe(totemRecipeKey);
            customItemService.unregisterRecipe(totemCoreRecipeKey);
            customItemService.unregisterRecipe(chaoticWarpCoreRecipeKey);
        }
        if (customBlockService != null) {
            customBlockService.unregisterMultiBlock(totemKey);
        }
        saveChaoticWarpQueues();
        releaseChaoticWarpQueueTickets();
    }

    private void initializeKeys() {
        ringKey = new NamespacedKey(this, "claim_ring");
        ringClaimIdKey = new NamespacedKey(this, "claim_ring_claim_id");
        ringClaimNameKey = new NamespacedKey(this, "claim_ring_claim_name");
        ringWorldKey = new NamespacedKey(this, "claim_ring_world");
        ringX1Key = new NamespacedKey(this, "claim_ring_x1");
        ringY1Key = new NamespacedKey(this, "claim_ring_y1");
        ringZ1Key = new NamespacedKey(this, "claim_ring_z1");
        ringX2Key = new NamespacedKey(this, "claim_ring_x2");
        ringY2Key = new NamespacedKey(this, "claim_ring_y2");
        ringZ2Key = new NamespacedKey(this, "claim_ring_z2");
        ringRecipeKey = new NamespacedKey(this, "claim_ring_recipe");
        ringItemModelKey = new NamespacedKey(this, "claim_ring");
        totemKey = new NamespacedKey(this, "claim_totem");
        totemItemModelKey = new NamespacedKey(this, "claim_totem");
        totemRecipeKey = new NamespacedKey(this, "claim_totem_recipe");
        totemCoreKey = new NamespacedKey(this, "claim_totem_core");
        totemCoreItemModelKey = new NamespacedKey(this, "claim_totem_core");
        totemCoreRecipeKey = new NamespacedKey(this, "claim_totem_core_recipe");
        chaoticWarpCoreKey = new NamespacedKey(this, "chaotic_warp_core");
        chaoticWarpCoreItemModelKey = new NamespacedKey(this, "chaotic_warp_core");
        chaoticWarpCoreRecipeKey = new NamespacedKey(this, "chaotic_warp_core_recipe");
        totemIdKey = new NamespacedKey(this, "claim_totem_id");
        totemPartKey = new NamespacedKey(this, "claim_totem_part");
        totemFrontKey = new NamespacedKey(this, "claim_totem_front");
        totemClaimIdKey = new NamespacedKey(this, "claim_totem_claim_id");
        totemOwnerUuidKey = new NamespacedKey(this, "claim_totem_owner_uuid");
        totemOwnerNameKey = new NamespacedKey(this, "claim_totem_owner_name");
        totemPlacedAtKey = new NamespacedKey(this, "claim_totem_placed_at");
        totemDisplayKey = new NamespacedKey(this, "claim_totem_display");
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
            case "pos1", "pos2", "create", "info", "list", "trust", "untrust", "delete", "remove" ->
                    send(player, message("command-disabled"));
            default -> sendUsage(sender);
        }
        return true;
    }

    private boolean canUseAction(CommandSender sender, String action) {
        if (!(sender instanceof Player player)) {
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

    public boolean canStartEvent(Player player, Location location) {
        ClaimRegion claim = claimAt(location);
        if (claim == null || !claim.blocks(ClaimFeature.START_EVENT, player)) {
            return true;
        }
        sendProtected(player, claim, ClaimFeature.START_EVENT);
        return false;
    }

    private void setPosition(Player player, String[] args, boolean first) {
        ClaimPoint point = parsePoint(player, args);
        if (point == null) {
            send(player, message("invalid-position"));
            return;
        }
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (first) {
            selection.pos1 = point;
            send(player, message("pos1-set"), "world", point.world, "x", point.x, "y", point.y, "z", point.z);
        } else {
            selection.pos2 = point;
            send(player, message("pos2-set"), "world", point.world, "x", point.x, "y", point.y, "z", point.z);
        }
        showSelectionStatus(player, selection);
    }

    private ClaimPoint parsePoint(Player player, String[] args) {
        Location location = player.getLocation();
        String world = Objects.requireNonNull(location.getWorld()).getName();
        if (args.length == 1) {
            return ClaimPoint.from(location);
        }
        if (args.length != 4) {
            return null;
        }
        try {
            return new ClaimPoint(world, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void showSelectionStatus(Player player, Selection selection) {
        if (selection.pos1 == null || selection.pos2 == null) {
            return;
        }
        if (!selection.pos1.world.equals(selection.pos2.world)) {
            send(player, message("selection-world-mismatch"));
            return;
        }
        ClaimRegion preview = ClaimRegion.fromSelection(
                "__preview__",
                "__preview__",
                player.getUniqueId(),
                player.getName(),
                selection.pos1,
                selection.pos2);
        send(player, message("selection-size"),
                "sizeX", preview.sizeX(),
                "sizeY", preview.sizeY(),
                "sizeZ", preview.sizeZ(),
                "volume", preview.volume());
        List<String> errors = validateClaimShape(preview);
        if (errors.isEmpty()) {
            send(player, message("selection-valid"));
        } else {
            send(player, message("selection-invalid"), "reason", String.join("；", errors));
        }
        showClaimParticles(player, preview);
    }

    private void createClaim(Player player, String[] args) {
        if (args.length < 2 || !isValidClaimName(args[1])) {
            send(player, message("invalid-name"));
            return;
        }
        String claimName = args[1];
        if (claimByName(claimName) != null) {
            send(player, message("duplicate-name"), "claim", claimName);
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
        int maxClaims = getConfig().getInt("limits.max-claims-per-player", 3);
        long ownedCount = claims.values().stream().filter(claim -> claim.ownerUuid.equals(player.getUniqueId())).count();
        if (!player.hasPermission("xiceclaim.admin") && ownedCount >= maxClaims) {
            send(player, message("too-many-claims"), "limit", maxClaims);
            return;
        }

        ClaimRegion claim = ClaimRegion.fromSelection(
                UUID.randomUUID().toString(),
                claimName,
                player.getUniqueId(),
                player.getName(),
                selection.pos1,
                selection.pos2);
        List<String> errors = validateClaimShape(claim);
        if (!errors.isEmpty()) {
            send(player, message("invalid-selection"), "reason", String.join("；", errors));
            return;
        }

        claims.put(claim.id, claim);
        bindExistingTotemInsideClaim(claim);
        saveClaims();
        send(player, message("created"),
                "claim", claim.name,
                "sizeX", claim.sizeX(),
                "sizeY", claim.sizeY(),
                "sizeZ", claim.sizeZ(),
                "volume", claim.volume());
        showClaimParticles(player, claim);
    }

    private void showClaimInfo(Player player, String[] args) {
        ClaimRegion claim;
        if (args.length >= 2) {
            claim = claimByName(args[1]);
            if (claim == null) {
                send(player, message("claim-not-found"), "claim", args[1]);
                return;
            }
        } else {
            claim = claimAt(player.getLocation());
            if (claim == null) {
                send(player, message("not-in-claim"));
                return;
            }
        }
        send(player, message("claim-info"),
                "claim", claim.name,
                "owner", claim.ownerName,
                "world", claim.world,
                "minX", claim.minX,
                "minY", claim.minY,
                "minZ", claim.minZ,
                "maxX", claim.maxX,
                "maxY", claim.maxY,
                "maxZ", claim.maxZ,
                "sizeX", claim.sizeX(),
                "sizeY", claim.sizeY(),
                "sizeZ", claim.sizeZ(),
                "volume", claim.volume(),
                "members", claim.memberNamesText());
        showClaimParticles(player, claim);
    }

    private void listClaims(Player player, String[] args) {
        String query = args.length >= 2 ? args[1] : player.getName();
        List<ClaimRegion> result = claims.values().stream()
                .filter(claim -> matchesOwner(claim, query))
                .sorted(Comparator.comparing(claim -> claim.name.toLowerCase(Locale.ROOT)))
                .toList();
        if (result.isEmpty()) {
            send(player, message("claim-list-empty-for"), "player", query);
            return;
        }
        send(player, message("claim-list-header"), "player", query, "count", result.size());
        for (ClaimRegion claim : result) {
            send(player, message("claim-list-line"),
                    "claim", claim.name,
                    "world", claim.world,
                    "minX", claim.minX,
                    "minY", claim.minY,
                    "minZ", claim.minZ,
                    "maxX", claim.maxX,
                    "maxY", claim.maxY,
                    "maxZ", claim.maxZ,
                    "sizeX", claim.sizeX(),
                    "sizeY", claim.sizeY(),
                    "sizeZ", claim.sizeZ(),
                    "members", claim.memberNamesText());
        }
    }

    private boolean matchesOwner(ClaimRegion claim, String query) {
        return claim.ownerName.equalsIgnoreCase(query) || claim.ownerUuid.toString().equalsIgnoreCase(query);
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
        ClaimRegion claim;
        String queryName = args.length >= 2 ? args[1] : "";
        if (args.length >= 2) {
            claim = claimByOwnerAndName(player, queryName);
        } else {
            claim = claimAt(player.getLocation());
            if (claim == null) {
                send(player, message("not-in-claim"));
                return;
            }
            if (!claim.ownerUuid.equals(player.getUniqueId())) {
                send(player, message("no-permission"));
                return;
            }
        }
        if (claim == null) {
            send(player, message("claim-not-found-owned"), "claim", queryName);
            return;
        }
        claims.remove(claim.id);
        saveClaims();
        send(player, message("deleted"), "claim", claim.name);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        BlockState originalState = event.getBlock().getState();
        if (isClaimTotemBlock(event.getBlock())) {
            if (getConfig().getBoolean("protection.block-break", true) && isProtectedFrom(event.getPlayer(), event.getBlock(), ClaimFeature.BLOCK_BREAK)) {
                ClaimRegion claim = claimAt(event.getBlock().getLocation());
                event.setCancelled(true);
                restoreProtectedBlockState(event.getBlock(), originalState);
                sendProtected(event.getPlayer(), claim, ClaimFeature.BLOCK_BREAK);
                return;
            }
            event.setCancelled(true);
            collapseTotem(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE, true);
            return;
        }
        if (getConfig().getBoolean("protection.block-break", true)) {
            protect(event.getPlayer(), event.getBlock(), event, ClaimFeature.BLOCK_BREAK);
            if (event.isCancelled()) {
                restoreProtectedBlockState(event.getBlock(), originalState);
                return;
            }
        }
        Block possibleBottom = event.getBlock().getRelative(BlockFace.UP);
        if (isClaimTotemBottom(possibleBottom)) {
            collapseTotem(possibleBottom, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!isClaimTotemBlock(event.getBlock())) {
            if (getConfig().getBoolean("protection.block-break", true) && isProtectedFrom(event.getPlayer(), event.getBlock(), ClaimFeature.BLOCK_BREAK)) {
                BlockState originalState = event.getBlock().getState();
                ClaimRegion claim = claimAt(event.getBlock().getLocation());
                event.setCancelled(true);
                restoreProtectedBlockState(event.getBlock(), originalState);
                sendProtected(event.getPlayer(), claim, ClaimFeature.BLOCK_BREAK);
            }
            return;
        }
        if (getConfig().getBoolean("protection.block-break", true) && isProtectedFrom(event.getPlayer(), event.getBlock(), ClaimFeature.BLOCK_BREAK)) {
            BlockState originalState = event.getBlock().getState();
            ClaimRegion claim = claimAt(event.getBlock().getLocation());
            event.setCancelled(true);
            restoreProtectedBlockState(event.getBlock(), originalState);
            sendProtected(event.getPlayer(), claim, ClaimFeature.BLOCK_BREAK);
            return;
        }
        event.setCancelled(true);
        collapseTotem(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE, true);
    }

    private void restoreProtectedBlockState(Block block, BlockState originalState) {
        Bukkit.getScheduler().runTask(this, () -> {
            originalState.update(true, false);
            for (Player viewer : block.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(block.getLocation()) <= 64.0D * 64.0D) {
                    viewer.sendBlockChange(block.getLocation(), block.getBlockData());
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isClaimTotemItem(event.getItemInHand()) || isTotemCoreItem(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        if (getConfig().getBoolean("protection.block-place", true)) {
            protect(event.getPlayer(), event.getBlockPlaced(), event, ClaimFeature.BLOCK_PLACE);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && isChaoticWarpCoreItem(event.getItem()) && isRightClick(event.getAction())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.OFF_HAND && isChaoticWarpCoreItem(event.getPlayer().getInventory().getItemInMainHand())) {
                return;
            }
            startChaoticWarpTeleport(event.getPlayer());
            return;
        }
        if (event.getClickedBlock() != null && isClaimTotemBlock(event.getClickedBlock()) && isRightClick(event.getAction())) {
            if (shouldPassTotemInteractionToBlockPlacement(event)) {
                return;
            }
            if (event.getItem() != null && isClaimTotemItem(event.getItem()) && event.getPlayer().isSneaking()) {
                event.setCancelled(true);
                placeClaimTotem(event);
                return;
            }
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                Player player = event.getPlayer();
                Block clickedBlock = event.getClickedBlock();
                Bukkit.getScheduler().runTask(this, () -> openTotemMenu(player, clickedBlock));
            }
            return;
        }
        if (event.getItem() != null && isClaimRing(event.getItem()) && isRightClick(event.getAction())) {
            if (shouldPassRingUseToBlock(event)) {
                if (getConfig().getBoolean("protection.block-interact", true) && event.getClickedBlock() != null) {
                    protect(event.getPlayer(), event.getClickedBlock(), event, ClaimFeature.BLOCK_INTERACT);
                }
                return;
            }
            event.setCancelled(true);
            openRingMenu(event.getPlayer(), event.getItem(), event.getHand());
            return;
        }
        if (event.getItem() != null && isClaimTotemItem(event.getItem()) && isRightClick(event.getAction())) {
            if (!shouldPassTotemUseToBlock(event)) {
                event.setCancelled(true);
                placeClaimTotem(event);
                return;
            }
        }
        if (event.isCancelled()) {
            return;
        }
        if (!getConfig().getBoolean("protection.block-interact", true) || event.getClickedBlock() == null) {
            return;
        }
        protect(event.getPlayer(), event.getClickedBlock(), event, ClaimFeature.BLOCK_INTERACT);
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean shouldPassTotemInteractionToBlockPlacement(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getPlayer().isSneaking()
                && item != null
                && item.getType().isBlock()
                && !isClaimTotemItem(item);
    }

    private boolean shouldPassTotemUseToBlock(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        return event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && !event.getPlayer().isSneaking()
                && isTotemPassThroughInteraction(clicked);
    }

    private boolean shouldPassRingUseToBlock(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        return event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && !event.getPlayer().isSneaking()
                && isTotemPassThroughInteraction(clicked);
    }

    private boolean isTotemPassThroughInteraction(Block block) {
        Material type = block.getType();
        String name = type.name();
        return block.getState() instanceof InventoryHolder
                || name.endsWith("_BUTTON")
                || name.equals("LEVER")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_CHEST")
                || name.endsWith("_BED")
                || name.endsWith("_ANVIL")
                || name.equals("CRAFTING_TABLE")
                || name.equals("CARTOGRAPHY_TABLE")
                || name.equals("SMITHING_TABLE")
                || name.equals("STONECUTTER")
                || name.equals("GRINDSTONE")
                || name.equals("LOOM")
                || name.equals("ENCHANTING_TABLE")
                || name.equals("BREWING_STAND")
                || name.equals("NOTE_BLOCK")
                || name.equals("JUKEBOX")
                || name.equals("REPEATER")
                || name.equals("COMPARATOR");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && positionChanged(from, to)) {
            cancelPendingTeleport(event.getPlayer(), message("teleport-cancelled-moved", "&c传送已取消：你移动了。"));
        }
        if (to == null || sameBlock(from, to)) {
            return;
        }
        ClaimRegion fromClaim = claimAt(from);
        ClaimRegion toClaim = claimAt(to);
        String fromId = fromClaim == null ? "" : fromClaim.id;
        String toId = toClaim == null ? "" : toClaim.id;
        if (fromId.equals(toId)) {
            return;
        }
        if (fromClaim != null) {
            send(event.getPlayer(), message("claim-left"), "claim", fromClaim.name, "owner", fromClaim.ownerName);
        }
        if (toClaim != null) {
            send(event.getPlayer(), message("claim-entered"), "claim", toClaim.name, "owner", toClaim.ownerName);
            showClaimParticles(event.getPlayer(), toClaim);
        }
    }

    private boolean sameBlock(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private boolean positionChanged(Location first, Location second) {
        if (!Objects.equals(first.getWorld(), second.getWorld())) {
            return true;
        }
        return first.distanceSquared(second) > TELEPORT_MOVE_CANCEL_DISTANCE_SQUARED;
    }

    private boolean sameBlock(Block first, Block second) {
        return first != null
                && second != null
                && Objects.equals(first.getWorld(), second.getWorld())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ringSessions.remove(event.getPlayer().getUniqueId());
        cancelPendingTeleport(event.getPlayer(), null);
        restorePlayerViewDistance(event.getPlayer(), "quit");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        maxOnlineSinceStart = Math.max(maxOnlineSinceStart, Bukkit.getOnlinePlayers().size());
        topUpChaoticWarpQueues();
        customItemService.discoverRecipe(event.getPlayer(), ringRecipeKey);
        customItemService.discoverRecipe(event.getPlayer(), chaoticWarpCoreRecipeKey);
        Bukkit.getScheduler().runTask(this, () -> {
            synchronizePlayerRingNames(event.getPlayer());
            if (hasClaimRing(event.getPlayer())) {
                unlockPostRingRecipes(event.getPlayer());
            }
            sendLoadedTotemClientViews(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isClaimRing(event.getItem().getItemStack())) {
            Bukkit.getScheduler().runTask(this, () -> {
                synchronizePlayerRingNames(player);
                unlockPostRingRecipes(player);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        Player player = event.getPlayer();
        ClaimRegion claim = claimAt(event.getRaid().getLocation());
        if (claim == null) {
            claim = claimAt(player.getLocation());
        }
        if (claim != null && claim.blocks(ClaimFeature.START_EVENT, player)) {
            event.setCancelled(true);
            sendProtected(player, claim, ClaimFeature.START_EVENT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (shouldSynchronizeInventory(inventory)) {
            synchronizeInventoryRingNames(inventory, null);
        }
        synchronizePlayerRingNames(player);
        if (!getConfig().getBoolean("protection.container-open", true)) {
            return;
        }
        Location location = inventory.getLocation();
        if (location == null) {
            return;
        }
        ClaimRegion claim = claimAt(location);
        if (claim != null && claim.blocks(ClaimFeature.CONTAINER_OPEN, player)) {
            event.setCancelled(true);
            sendProtected(player, claim, ClaimFeature.CONTAINER_OPEN);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof TotemMenu menu) {
            handleTotemMenuClick(player, menu, event);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof RingMenu menu)) {
            ItemStack anvilResult = isAnvilResultClick(event) && isClaimRing(event.getCurrentItem())
                    ? event.getCurrentItem().clone()
                    : null;
            Bukkit.getScheduler().runTask(this, () -> {
                if (anvilResult != null) {
                    applyAnvilRingRename(player, anvilResult);
                }
                synchronizePlayerRingNames(player);
                if (shouldSynchronizeInventory(event.getInventory())) {
                    synchronizeInventoryRingNames(event.getInventory(), null);
                }
                Inventory clickedInventory = event.getClickedInventory();
                if (clickedInventory != null
                        && clickedInventory != event.getInventory()
                        && shouldSynchronizeInventory(clickedInventory)) {
                    synchronizeInventoryRingNames(clickedInventory, null);
                }
            });
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        handleRingMenuClick(player, menu, event.getSlot());
    }

    private boolean isAnvilResultClick(InventoryClickEvent event) {
        return event.getInventory().getType().name().endsWith("ANVIL")
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getInventory())
                && event.getRawSlot() == 2;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RingMenu || event.getInventory().getHolder() instanceof TotemMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            synchronizePlayerRingNames(player);
            if (hasClaimRing(player)) {
                unlockPostRingRecipes(player);
            }
        });
        if (event.getInventory().getHolder() instanceof TotemMenu menu) {
            saveTotemMenu(menu, event.getInventory());
            return;
        }
        if (!(event.getInventory().getHolder() instanceof RingMenu menu)) {
            if (shouldSynchronizeInventory(event.getInventory())) {
                synchronizeInventoryRingNames(event.getInventory(), null);
            }
            return;
        }
        RingSession session = ringSessions.get(player.getUniqueId());
        if (session == null || session != menu.session || session.discardOnClose) {
            return;
        }
        if (session.mode == RingMenuMode.CREATE) {
            saveDraftToRing(session.ring, session.draft);
            writeRingToHand(player, session);
        }
        ringSessions.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isClaimRing(item) || isClaimTotemItem(item) || isTotemCoreItem(item) || isChaoticWarpCoreItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isClaimRing(item) || isClaimTotemItem(item) || isTotemCoreItem(item) || isChaoticWarpCoreItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player && isClaimRing(event.getCurrentItem())) {
            Bukkit.getScheduler().runTask(this, () -> unlockPostRingRecipes(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        if (isClaimRing(item)) {
            event.setCancelled(true);
            openRingMenu(event.getPlayer(), item, event.getHand());
            return;
        }
        if (isClaimTotemItem(item) || isTotemCoreItem(item) || isChaoticWarpCoreItem(item)) {
            event.setCancelled(true);
            return;
        }
        if (getConfig().getBoolean("protection.entity-interact", true)) {
            protectEntity(event.getPlayer(), event.getRightClicked(), event, ClaimFeature.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = damagingPlayer(event.getDamager());
        if (player == null) {
            return;
        }
        ClaimFeature feature = damageFeature(event.getEntity());
        if (feature == ClaimFeature.PVP) {
            if (!getConfig().getBoolean("protection.pvp", true)) {
                return;
            }
        } else if (!getConfig().getBoolean("protection.entity-interact", true)) {
            return;
        }
        protectEntity(player, event.getEntity(), event, feature);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (getConfig().getBoolean("protection.entity-interact", true) && event.getPlayer() != null) {
            protectEntity(event.getPlayer(), event.getEntity(), event, ClaimFeature.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!getConfig().getBoolean("protection.entity-interact", true)) {
            return;
        }
        if (event.getRemover() instanceof Player player) {
            protectEntity(player, event.getEntity(), event, ClaimFeature.ENTITY_INTERACT);
            return;
        }
        ClaimRegion claim = claimAt(event.getEntity().getLocation());
        if (claim != null && claim.blocksActorless(ClaimFeature.ENTITY_INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (getConfig().getBoolean("protection.bucket", true)) {
            ClaimFeature feature = bucketFeature(event.getBucket());
            if (feature != null) {
                protect(event.getPlayer(), event.getBlock(), event, feature);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (getConfig().getBoolean("protection.bucket", true)) {
            ClaimFeature feature = bucketFeature(event.getBlock().getType());
            if (feature != null) {
                protect(event.getPlayer(), event.getBlock(), event, feature);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        ClaimFeature feature = fluidFlowFeature(event.getBlock().getType());
        if (feature == null) {
            return;
        }
        ClaimRegion claim = claimAt(event.getToBlock().getLocation());
        if (claim != null && claim.blocksActorless(feature)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!getConfig().getBoolean("protection.entity-spawn", true)) {
            return;
        }
        ClaimFeature feature = spawnFeature(event.getEntity());
        if (feature == null) {
            return;
        }
        ClaimRegion claim = claimAt(event.getLocation());
        if (claim != null && claim.blocksActorless(feature)) {
            event.setCancelled(true);
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
        if (claim.blocks(ClaimFeature.FIRE, player)) {
            event.setCancelled(true);
            if (player != null) {
                sendProtected(player, claim, ClaimFeature.FIRE);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        ClaimRegion claim = claimAt(event.getBlock().getLocation());
        if (getConfig().getBoolean("protection.fire", true) && claim != null && claim.blocksActorless(ClaimFeature.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (getConfig().getBoolean("protection.explosion", true)) {
            event.blockList().removeIf(block -> {
                ClaimRegion claim = claimAt(block.getLocation());
                return claim != null && claim.blocksActorless(ClaimFeature.EXPLOSION);
            });
        }
        protectTotemsFromExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (getConfig().getBoolean("protection.explosion", true)) {
            event.blockList().removeIf(block -> {
                ClaimRegion claim = claimAt(block.getLocation());
                return claim != null && claim.blocksActorless(ClaimFeature.EXPLOSION);
            });
        }
        protectTotemsFromExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        scheduleTotemSupportCheck(event.getBlock());
        scheduleTotemSupportCheck(event.getBlock().getRelative(BlockFace.UP));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (getConfig().getBoolean("protection.piston", true)
                && (pistonUseProtected(event.getBlock()) || pistonCrossesClaimBoundary(event.getBlock(), event.getBlocks(), event.getDirection()))) {
            event.setCancelled(true);
            return;
        }
        collapseTotemsAffectedBy(event.getBlocks(), true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (getConfig().getBoolean("protection.piston", true)
                && (pistonUseProtected(event.getBlock()) || pistonCrossesClaimBoundary(event.getBlock(), event.getBlocks(), event.getDirection()))) {
            event.setCancelled(true);
            return;
        }
        collapseTotemsAffectedBy(event.getBlocks(), true);
    }

    private boolean pistonUseProtected(Block piston) {
        ClaimRegion claim = claimAt(piston.getLocation());
        return claim != null && claim.blocksActorless(ClaimFeature.PISTON_USE);
    }

    private boolean pistonCrossesClaimBoundary(Block piston, List<Block> movedBlocks, BlockFace direction) {
        if (claimBoundaryChanges(piston.getLocation(), piston.getRelative(direction).getLocation())) {
            return true;
        }
        for (Block block : movedBlocks) {
            if (claimBoundaryChanges(block.getLocation(), block.getRelative(direction).getLocation())) {
                return true;
            }
        }
        return false;
    }

    private boolean claimBoundaryChanges(Location from, Location to) {
        ClaimRegion fromClaim = claimAt(from);
        ClaimRegion toClaim = claimAt(to);
        String fromId = fromClaim == null ? "" : fromClaim.id;
        String toId = toClaim == null ? "" : toClaim.id;
        if (fromId.equals(toId)) {
            return false;
        }
        return isPistonBoundaryProtected(fromClaim) || isPistonBoundaryProtected(toClaim);
    }

    private boolean isPistonBoundaryProtected(ClaimRegion claim) {
        return claim != null && claim.blocksActorless(ClaimFeature.PISTON);
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private ClaimFeature damageFeature(Entity entity) {
        if (entity instanceof Player) {
            return ClaimFeature.PVP;
        }
        if (entity instanceof Animals) {
            return ClaimFeature.ANIMAL_DAMAGE;
        }
        if (entity instanceof Monster) {
            return ClaimFeature.MONSTER_DAMAGE;
        }
        return ClaimFeature.ENTITY_INTERACT;
    }

    private ClaimFeature spawnFeature(Entity entity) {
        if (entity instanceof Animals) {
            return ClaimFeature.ANIMAL_SPAWN;
        }
        if (entity instanceof Monster) {
            return ClaimFeature.MONSTER_SPAWN;
        }
        return null;
    }

    private ClaimFeature bucketFeature(Material bucket) {
        if (bucket == Material.WATER_BUCKET || bucket == Material.WATER) {
            return ClaimFeature.WATER_BUCKET;
        }
        if (bucket == Material.LAVA_BUCKET || bucket == Material.LAVA) {
            return ClaimFeature.LAVA_BUCKET;
        }
        return null;
    }

    private ClaimFeature fluidFlowFeature(Material material) {
        if (material == Material.WATER) {
            return ClaimFeature.WATER_FLOW;
        }
        if (material == Material.LAVA) {
            return ClaimFeature.LAVA_FLOW;
        }
        return null;
    }

    private void protect(Player player, Block block, org.bukkit.event.Cancellable event, ClaimFeature feature) {
        ClaimRegion claim = claimAt(block.getLocation());
        if (claim == null || !claim.blocks(feature, player)) {
            return;
        }
        event.setCancelled(true);
        sendProtected(player, claim, feature);
    }

    private boolean isProtectedFrom(Player player, Block block, ClaimFeature feature) {
        ClaimRegion claim = claimAt(block.getLocation());
        return claim != null && claim.blocks(feature, player);
    }

    private void protectEntity(Player player, Entity entity, org.bukkit.event.Cancellable event, ClaimFeature feature) {
        ClaimRegion claim = claimAt(entity.getLocation());
        if (claim == null || !claim.blocks(feature, player)) {
            return;
        }
        event.setCancelled(true);
        sendProtected(player, claim, feature);
    }

    private void sendProtected(Player player, ClaimRegion claim, ClaimFeature feature) {
        String text = message("protected");
        if (text == null || text.isBlank()) {
            text = "&c\u8fd9\u91cc\u5c5e\u4e8e {claim}\uff0c\u4f60\u6ca1\u6709 {feature} \u6743\u9650\u3002";
        } else if (!text.contains("{feature}")) {
            text = text + " &7(\u7f3a\u5c11\u6743\u9650\uff1a{feature})";
        }
        send(player, text, "claim", claim == null ? "" : claim.name, "feature", featureDisplayName(feature));
    }

    private String featureDisplayName(ClaimFeature feature) {
        return ChatColor.stripColor(color(feature.displayName));
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

    private ClaimRegion claimByName(String name) {
        for (ClaimRegion claim : claims.values()) {
            if (claim.name.equalsIgnoreCase(name)) {
                return claim;
            }
        }
        return null;
    }

    private ClaimRegion claimByOwnerAndName(Player player, String name) {
        for (ClaimRegion claim : claims.values()) {
            if (claim.name.equalsIgnoreCase(name) && claim.ownerUuid.equals(player.getUniqueId())) {
                return claim;
            }
        }
        return null;
    }

    private ClaimRegion firstOverlappingClaim(ClaimRegion candidate) {
        for (ClaimRegion claim : claims.values()) {
            if (!claim.id.equals(candidate.id) && claim.overlaps(candidate)) {
                return claim;
            }
        }
        return null;
    }

    private boolean canManage(Player player, ClaimRegion claim) {
        return claim.ownerUuid.equals(player.getUniqueId()) || player.hasPermission("xiceclaim.admin");
    }

    private boolean canManage(RingSession session, ClaimRegion claim) {
        return claim.ownerUuid.equals(session.playerUuid) || session.admin;
    }

    private boolean canRenameClaim(Player player, ClaimRegion claim) {
        return claim.ownerUuid.equals(player.getUniqueId());
    }

    private List<String> validateClaimShape(ClaimRegion claim) {
        List<String> errors = new ArrayList<>();
        int minSize = getConfig().getInt("limits.min-size", 3);
        int maxHorizontal = getConfig().getInt("limits.max-horizontal-size", 128);
        int border = getConfig().getInt("limits.world-border", 50_000);
        int minY = getConfig().getInt("limits.world-min-y", -64);
        int maxY = getConfig().getInt("limits.world-max-y", 320);
        if (claim.sizeX() < minSize || claim.sizeY() < minSize || claim.sizeZ() < minSize) {
            errors.add(messageText("invalid-size-small")
                    .replace("{min}", String.valueOf(minSize))
                    .replace("{sizeX}", String.valueOf(claim.sizeX()))
                    .replace("{sizeY}", String.valueOf(claim.sizeY()))
                    .replace("{sizeZ}", String.valueOf(claim.sizeZ())));
        }
        if (claim.sizeX() > maxHorizontal || claim.sizeZ() > maxHorizontal) {
            errors.add(messageText("invalid-size-large")
                    .replace("{max}", String.valueOf(maxHorizontal))
                    .replace("{sizeX}", String.valueOf(claim.sizeX()))
                    .replace("{sizeZ}", String.valueOf(claim.sizeZ())));
        }
        if (claim.minX < -border || claim.maxX > border || claim.minZ < -border || claim.maxZ > border
                || claim.minY < minY || claim.maxY > maxY) {
            errors.add(messageText("out-of-world-boundary")
                    .replace("{border}", String.valueOf(border))
                    .replace("{minY}", String.valueOf(minY))
                    .replace("{maxY}", String.valueOf(maxY)));
        }
        ClaimRegion overlapping = firstOverlappingClaim(claim);
        if (overlapping != null) {
            errors.add(messageText("overlap-reason").replace("{claim}", overlapping.name));
        }
        return errors;
    }

    private List<String> validateClaimResize(ClaimRegion oldClaim, ClaimRegion resized) {
        List<String> errors = new ArrayList<>(validateClaimShape(resized));
        if (!oldClaim.world.equals(resized.world)) {
            errors.add("不能改变领地所在世界");
        }
        if (resized.minX > oldClaim.minX
                || resized.maxX < oldClaim.maxX
                || resized.minY > oldClaim.minY
                || resized.maxY < oldClaim.maxY
                || resized.minZ > oldClaim.minZ
                || resized.maxZ < oldClaim.maxZ) {
            errors.add("新范围必须完整包含旧范围");
        }
        return errors;
    }

    private ItemStack createEmptyRing(ClaimPoint point) {
        ItemStack ring = createEmptyRing();
        RingDraft draft = RingDraft.fromPoint(point);
        saveDraftToRing(ring, draft);
        return ring;
    }

    private ItemStack createEmptyRing() {
        return customItemService.create(ringKey, 1);
    }

    private void registerClaimRingRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(ringRecipeKey, createEmptyRing());
        recipe.shape("IDI", "N N", " S ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('N', Material.IRON_NUGGET);
        recipe.setIngredient('S', Material.STRING);
        customItemService.registerRecipe(recipe);
    }

    private void registerClaimTotemRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(totemRecipeKey, createClaimTotem());
        recipe.shape("Q*Q", "GEG", "SSS");
        recipe.setIngredient('Q', Material.QUARTZ);
        recipe.setIngredient('*', Material.AMETHYST_SHARD);
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('S', Material.QUARTZ_SLAB);
        customItemService.registerRecipe(recipe);
    }

    private void registerTotemCoreRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(totemCoreRecipeKey, createTotemCore());
        recipe.shape("NGN", "G*G", "NGN");
        recipe.setIngredient('N', Material.NETHER_BRICK);
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('*', Material.NETHER_STAR);
        customItemService.registerRecipe(recipe);
    }

    private void registerChaoticWarpCoreRecipe() {
        ShapelessRecipe recipe = new ShapelessRecipe(chaoticWarpCoreRecipeKey, createChaoticWarpCore());
        recipe.addIngredient(Material.ENDER_PEARL);
        recipe.addIngredient(Material.WHEAT_SEEDS);
        recipe.addIngredient(new RecipeChoice.MaterialChoice(
                Material.OAK_PLANKS,
                Material.SPRUCE_PLANKS,
                Material.BIRCH_PLANKS,
                Material.JUNGLE_PLANKS,
                Material.ACACIA_PLANKS,
                Material.DARK_OAK_PLANKS,
                Material.MANGROVE_PLANKS,
                Material.CHERRY_PLANKS,
                Material.PALE_OAK_PLANKS,
                Material.BAMBOO_PLANKS,
                Material.CRIMSON_PLANKS,
                Material.WARPED_PLANKS));
        customItemService.registerRecipe(recipe);
    }

    private void registerCustomItems() {
        customItemService.register(new CustomItemDefinition(
                ringKey,
                Material.FLINT,
                ringKey,
                ringItemModelKey,
                Component.text("领地戒指", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("右键打开领地创建界面。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("可用铁砧改名，戒指名即领地名。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                totemKey,
                TOTEM_ITEM_MATERIAL,
                totemKey,
                totemItemModelKey,
                Component.text("领地图腾", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("右键放置，需要下方有方块支撑。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("放置后占用 1 x 1 x 2 空间。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                false));
        customItemService.register(new CustomItemDefinition(
                totemCoreKey,
                TOTEM_CORE_ITEM_MATERIAL,
                totemCoreKey,
                totemCoreItemModelKey,
                Component.text("图腾核心", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("可放入已绑定的领地图腾。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                false));
        customItemService.register(new CustomItemDefinition(
                chaoticWarpCoreKey,
                Material.FIREWORK_STAR,
                chaoticWarpCoreKey,
                chaoticWarpCoreItemModelKey,
                Component.text("紊乱跃迁晶核", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("右键启动 3 秒跃迁倒计时。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("结束后消耗 1 级经验随机传送至当前维度。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                true));
    }

    private void registerCustomMultiBlocks() {
        Map<BlockFace, String> bottomData = Map.of(
                BlockFace.NORTH, totemBlockData("bottom", BlockFace.NORTH),
                BlockFace.EAST, totemBlockData("bottom", BlockFace.EAST),
                BlockFace.SOUTH, totemBlockData("bottom", BlockFace.SOUTH),
                BlockFace.WEST, totemBlockData("bottom", BlockFace.WEST));
        Map<BlockFace, String> topData = Map.of(
                BlockFace.NORTH, totemBlockData("top", BlockFace.NORTH),
                BlockFace.EAST, totemBlockData("top", BlockFace.EAST),
                BlockFace.SOUTH, totemBlockData("top", BlockFace.SOUTH),
                BlockFace.WEST, totemBlockData("top", BlockFace.WEST));
        claimTotemStructureDefinition = new CustomMultiBlockDefinition(
                totemKey,
                totemDisplayKey,
                totemPartKey,
                Component.text("Claim Totem"),
                List.of(
                        new CustomMultiBlockPart("bottom", 0, 0, 0, bottomData, bottomData, 1.0F, 1.0F),
                        new CustomMultiBlockPart("top", 0, 1, 0, topData, topData, 1.0F, 1.0F)),
                3.5D,
                null,
                null,
                Component.text("Claim Totem"),
                List.of());
        customBlockService.registerMultiBlock(claimTotemStructureDefinition);
    }

    private ItemStack createClaimTotem() {
        return customItemService.create(totemKey, 1);
    }

    private ItemStack createTotemCore() {
        return customItemService.create(totemCoreKey, 1);
    }

    private ItemStack createChaoticWarpCore() {
        return customItemService.create(chaoticWarpCoreKey, 1);
    }

    private boolean isClaimRing(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, ringKey);
    }

    private boolean isClaimTotemItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, totemKey);
    }

    private boolean isTotemCoreItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, totemCoreKey);
    }

    private boolean isChaoticWarpCoreItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, chaoticWarpCoreKey);
    }

    private void unlockPostRingRecipes(Player player) {
        customItemService.discoverRecipes(player, List.of(totemRecipeKey, totemCoreRecipeKey));
    }

    private boolean hasClaimRing(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isClaimRing(item)) {
                return true;
            }
        }
        return false;
    }

    private void placeClaimTotem(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null) {
            return;
        }
        Block bottom = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
        Block top = bottom.getRelative(BlockFace.UP);
        World world = bottom.getWorld();
        if (top.getY() >= world.getMaxHeight()) {
            return;
        }
        if (!customBlockService.canPlaceMultiBlock(bottom, claimTotemStructureDefinition, totemFrontFor(player))
                || !hasTotemSupport(bottom)) {
            return;
        }
        if (getConfig().getBoolean("protection.block-place", true)) {
            ClaimRegion bottomClaim = claimAt(bottom.getLocation());
            if (bottomClaim != null && bottomClaim.blocks(ClaimFeature.BLOCK_PLACE, player)) {
                sendProtected(player, bottomClaim, ClaimFeature.BLOCK_PLACE);
                return;
            }
            ClaimRegion topClaim = claimAt(top.getLocation());
            if (topClaim != null && topClaim.blocks(ClaimFeature.BLOCK_PLACE, player)) {
                sendProtected(player, topClaim, ClaimFeature.BLOCK_PLACE);
                return;
            }
        }

        String totemId = UUID.randomUUID().toString();
        long placedAt = System.currentTimeMillis();
        BlockFace front = totemFrontFor(player);
        customBlockService.setMultiBlockCarrierBlocks(bottom, claimTotemStructureDefinition, front);
        markTotemBlock(bottom, totemId, "bottom", front, player, placedAt);
        markTotemBlock(top, totemId, "top", front, player, placedAt);
        tryBindClaimTotem(bottom, top, totemId);
        refreshTotemBlock(bottom);
        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeOneTotem(player, event.getHand());
        }
    }

    private void consumeOneTotem(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private void markTotemBlock(Block block, String totemId, String part, BlockFace front, Player owner, long placedAt) {
        markTotemBlock(block, totemId, part, front, owner.getUniqueId().toString(), owner.getName(), placedAt);
    }

    private void markTotemBlock(Block block, String totemId, String part, BlockFace front, String ownerUuid, String ownerName, long placedAt) {
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return;
        }
        PersistentDataContainer data = tileState.getPersistentDataContainer();
        data.set(totemKey, PersistentDataType.BYTE, (byte) 1);
        data.set(totemIdKey, PersistentDataType.STRING, totemId);
        data.set(totemPartKey, PersistentDataType.STRING, part);
        data.set(totemFrontKey, PersistentDataType.STRING, front.name().toLowerCase(Locale.ROOT));
        data.remove(totemClaimIdKey);
        data.set(totemOwnerUuidKey, PersistentDataType.STRING, ownerUuid);
        data.set(totemOwnerNameKey, PersistentDataType.STRING, ownerName);
        data.set(totemPlacedAtKey, PersistentDataType.LONG, placedAt);
        tileState.update(true, false);
    }

    private void markTotemClaim(Block bottom, String claimId) {
        markTotemClaimBlock(bottom, claimId);
        Block top = bottom.getRelative(BlockFace.UP);
        if (isClaimTotemBlock(top) && totemId(bottom).equals(totemId(top))) {
            markTotemClaimBlock(top, claimId);
        }
    }

    private void markTotemClaimBlock(Block block, String claimId) {
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return;
        }
        PersistentDataContainer data = tileState.getPersistentDataContainer();
        if (claimId == null || claimId.isBlank()) {
            data.remove(totemClaimIdKey);
        } else {
            data.set(totemClaimIdKey, PersistentDataType.STRING, claimId);
        }
        tileState.update(true, false);
    }

    private void tryBindClaimTotem(Block bottom, Block top, String totemId) {
        ClaimRegion claim = claimContainingTotem(bottom, top);
        if (claim == null || claim.totemBinding != null || !totemString(bottom, totemClaimIdKey).isBlank()) {
            return;
        }
        TotemBinding binding = new TotemBinding(totemId, bottom.getWorld().getName(), bottom.getX(), bottom.getY(), bottom.getZ());
        claim.totemBinding = binding;
        claim.totemCore = false;
        markTotemClaim(bottom, claim.id);
        saveClaims();
        notifyClaimOwner(claim, message("totem-bound", "&a领地图腾已绑定至领地 {claim}。"));
    }

    private ClaimRegion claimContainingTotem(Block bottom, Block top) {
        ClaimRegion bottomClaim = claimAt(bottom.getLocation());
        ClaimRegion topClaim = claimAt(top.getLocation());
        if (bottomClaim == null || topClaim == null || !bottomClaim.id.equals(topClaim.id)) {
            return null;
        }
        return bottomClaim;
    }

    private void removeTotemDisplays(Block bottom, String totemId) {
        customBlockService.removeMultiBlockDisplays(
                bottom.getWorld(),
                bottom.getLocation().add(0.5, 1.0, 0.5),
                claimTotemStructureDefinition,
                totemId,
                2.5D);
        Location center = bottom.getLocation().add(0.5, 1.0, 0.5);
        for (Entity entity : bottom.getWorld().getNearbyEntities(center, 2.0, 2.5, 2.0)) {
            PersistentDataContainer data = entity.getPersistentDataContainer();
            if (data.has(totemDisplayKey, PersistentDataType.BYTE)
                    && totemId.equals(data.get(totemIdKey, PersistentDataType.STRING))) {
                entity.remove();
            }
        }
    }

    private void refreshLoadedTotemBlocks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                refreshTotemBlocks(chunk);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        refreshTotemBlocks(event.getChunk());
    }

    private void refreshTotemBlocks(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            Block block = state.getBlock();
            if (isClaimTotemBottom(block)) {
                refreshTotemBlock(block);
            }
        }
    }

    private void refreshTotemBlock(Block bottom) {
        String id = totemId(bottom);
        if (id.isEmpty()) {
            return;
        }
        Block top = bottom.getRelative(BlockFace.UP);
        if (!isClaimTotemBlock(top) || !id.equals(totemId(top))) {
            return;
        }
        BlockFace front = totemFront(bottom);
        removeTotemDisplays(bottom, id);
        if (!isTotemBlockData(bottom, "bottom", front) || !isTotemBlockData(top, "top", front)) {
            String ownerUuid = totemString(bottom, totemOwnerUuidKey);
            String ownerName = totemString(bottom, totemOwnerNameKey);
            String claimId = totemString(bottom, totemClaimIdKey);
            long placedAt = totemLong(bottom, totemPlacedAtKey);
            customBlockService.setMultiBlockCarrierBlocks(bottom, claimTotemStructureDefinition, front);
            markTotemBlock(bottom, id, "bottom", front, ownerUuid, ownerName, placedAt);
            markTotemBlock(top, id, "top", front, ownerUuid, ownerName, placedAt);
            markTotemClaim(bottom, claimId);
        }
        customBlockService.spawnOrReplaceMultiBlockDisplays(bottom, claimTotemStructureDefinition, front, id);
        sendTotemClientView(bottom);
    }

    private void sendLoadedTotemClientViews() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    Block block = state.getBlock();
                    if (isClaimTotemBottom(block)) {
                        sendTotemClientView(block);
                    }
                }
            }
        }
    }

    private void sendLoadedTotemClientViews(Player player) {
        World world = player.getWorld();
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                Block block = state.getBlock();
                if (isClaimTotemBottom(block)) {
                    sendTotemClientView(player, block);
                }
            }
        }
    }

    private void sendTotemClientView(Block bottom) {
        for (Player player : bottom.getWorld().getPlayers()) {
            sendTotemClientView(player, bottom);
        }
    }

    private void sendTotemClientView(Player player, Block bottom) {
        if (!player.getWorld().equals(bottom.getWorld())) {
            return;
        }
        if (player.getLocation().distanceSquared(bottom.getLocation()) > 128.0D * 128.0D) {
            return;
        }
        Block top = bottom.getRelative(BlockFace.UP);
        BlockData hiddenCarrier = Material.BARRIER.createBlockData();
        player.sendBlockChange(bottom.getLocation(), hiddenCarrier);
        player.sendBlockChange(top.getLocation(), hiddenCarrier);
    }

    private void sendRealBlockView(Block block) {
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(block.getLocation()) <= 128.0D * 128.0D) {
                player.sendBlockChange(block.getLocation(), block.getBlockData());
            }
        }
    }

    private boolean isTotemBlockData(Block block, String part, BlockFace front) {
        Material expectedType = "top".equals(part) ? TOTEM_TOP_MATERIAL : TOTEM_BOTTOM_MATERIAL;
        return block.getType() == expectedType && block.getBlockData().matches(Bukkit.createBlockData(totemBlockData(part, front)));
    }

    private String totemBlockData(String part, BlockFace front) {
        if ("top".equals(part)) {
            return switch (front) {
                case EAST -> "minecraft:structure_block[mode=load]";
                case SOUTH -> "minecraft:structure_block[mode=corner]";
                case WEST -> "minecraft:structure_block[mode=data]";
                default -> "minecraft:structure_block[mode=save]";
            };
        }
        return switch (front) {
            case EAST -> "minecraft:jigsaw[orientation=east_up]";
            case SOUTH -> "minecraft:jigsaw[orientation=south_up]";
            case WEST -> "minecraft:jigsaw[orientation=west_up]";
            default -> "minecraft:jigsaw[orientation=north_up]";
        };
    }

    private BlockFace totemFrontFor(Player player) {
        return switch (player.getFacing()) {
            case NORTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.WEST;
            case SOUTH -> BlockFace.NORTH;
            case WEST -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    private boolean isClaimTotemBlock(Block block) {
        if (block == null) {
            return false;
        }
        BlockState state = block.getState();
        return state instanceof TileState tileState
                && tileState.getPersistentDataContainer().has(totemKey, PersistentDataType.BYTE);
    }

    private boolean isClaimTotemBottom(Block block) {
        return isClaimTotemBlock(block) && "bottom".equals(totemPart(block));
    }

    private String totemPart(Block block) {
        if (block == null || !(block.getState() instanceof TileState tileState)) {
            return "";
        }
        return tileState.getPersistentDataContainer().getOrDefault(totemPartKey, PersistentDataType.STRING, "");
    }

    private String totemId(Block block) {
        if (block == null || !(block.getState() instanceof TileState tileState)) {
            return "";
        }
        return tileState.getPersistentDataContainer().getOrDefault(totemIdKey, PersistentDataType.STRING, "");
    }

    private BlockFace totemFront(Block block) {
        String value = totemString(block, totemFrontKey).toUpperCase(Locale.ROOT);
        try {
            BlockFace face = BlockFace.valueOf(value);
            return switch (face) {
                case NORTH, EAST, SOUTH, WEST -> face;
                default -> BlockFace.NORTH;
            };
        } catch (IllegalArgumentException ignored) {
            return BlockFace.NORTH;
        }
    }

    private String totemString(Block block, NamespacedKey key) {
        if (block == null || !(block.getState() instanceof TileState tileState)) {
            return "";
        }
        return tileState.getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, "");
    }

    private long totemLong(Block block, NamespacedKey key) {
        if (block == null || !(block.getState() instanceof TileState tileState)) {
            return System.currentTimeMillis();
        }
        return tileState.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, System.currentTimeMillis());
    }

    private Block totemBottom(Block block) {
        if (!isClaimTotemBlock(block)) {
            return block;
        }
        if ("top".equals(totemPart(block))) {
            Block bottom = block.getRelative(BlockFace.DOWN);
            if (isClaimTotemBlock(bottom) && totemId(block).equals(totemId(bottom))) {
                return bottom;
            }
        }
        return block;
    }

    private void collapseTotem(Block block, boolean dropItem) {
        collapseTotem(block, dropItem, dropItem);
    }

    private void collapseTotem(Block block, boolean dropItem, boolean dropCoreItem) {
        Block bottom = totemBottom(block);
        String id = totemId(bottom);
        Block top = bottom.getRelative(BlockFace.UP);
        Block above = top.getRelative(BlockFace.UP);
        Location dropLocation = bottom.getLocation().add(0.5, 0.5, 0.5);
        ClaimRegion boundClaim = claimBoundToTotem(bottom, id);
        boolean dropCore = dropCoreItem && boundClaim != null && boundClaim.totemCore;
        unbindClaimTotem(bottom);
        removeTotemDisplays(bottom, id);
        if (isClaimTotemBlock(top) && id.equals(totemId(top))) {
            top.setType(Material.AIR, false);
        }
        if (isClaimTotemBlock(bottom)) {
            bottom.setType(Material.AIR, false);
        }
        sendRealBlockView(bottom);
        sendRealBlockView(top);
        if (dropItem) {
            bottom.getWorld().dropItemNaturally(dropLocation, createClaimTotem());
        }
        if (dropCore) {
            bottom.getWorld().dropItemNaturally(dropLocation, createTotemCore());
        }
        collapseUnsupportedTotemsAbove(above);
    }

    private void unbindClaimTotem(Block bottom) {
        String claimId = totemString(bottom, totemClaimIdKey);
        String totemId = totemId(bottom);
        ClaimRegion claim = claimId.isBlank() ? null : claims.get(claimId);
        if (claim == null) {
            claim = claimBoundToTotem(bottom, totemId);
        }
        if (claim == null || claim.totemBinding == null || !claim.totemBinding.matches(bottom, totemId)) {
            return;
        }
        claim.totemBinding = null;
        claim.totemCore = false;
        saveClaims();
        notifyClaimOwner(claim, message("totem-unbound", "&e领地图腾已与领地 {claim} 解绑。"));
        scheduleClaimTotemRebind(claim, bottom, totemId);
    }

    private ClaimRegion claimBoundToTotem(Block bottom, String totemId) {
        for (ClaimRegion claim : claims.values()) {
            if (claim.totemBinding != null && claim.totemBinding.matches(bottom, totemId)) {
                return claim;
            }
        }
        return null;
    }

    private void notifyClaimOwner(ClaimRegion claim, String text) {
        Player owner = Bukkit.getPlayer(claim.ownerUuid);
        if (owner != null) {
            send(owner, text, "claim", claim.name);
        }
    }

    private void scheduleClaimTotemRebind(ClaimRegion claim, Block removedBottom, String removedTotemId) {
        Bukkit.getScheduler().runTask(this, () -> {
            ClaimRegion current = claims.get(claim.id);
            if (current == null || current.totemBinding != null) {
                return;
            }
            Block candidate = findBindableTotemForClaim(current, removedBottom, removedTotemId);
            if (candidate != null) {
                bindExistingTotemToClaim(current, candidate);
            }
        });
    }

    private Block findBindableTotemForClaim(ClaimRegion claim, Block excludedBottom, String excludedTotemId) {
        World world = Bukkit.getWorld(claim.world);
        if (world == null) {
            return null;
        }
        int minChunkX = claim.minX >> 4;
        int maxChunkX = claim.maxX >> 4;
        int minChunkZ = claim.minZ >> 4;
        int maxChunkZ = claim.maxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (BlockState state : chunk.getTileEntities()) {
                    Block block = state.getBlock();
                    if (isBindableTotemForClaim(claim, block, excludedBottom, excludedTotemId)) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private boolean isBindableTotemForClaim(ClaimRegion claim, Block bottom, Block excludedBottom, String excludedTotemId) {
        if (!isClaimTotemBottom(bottom)) {
            return false;
        }
        String id = totemId(bottom);
        if (id.isBlank() || id.equals(excludedTotemId) || sameBlock(bottom, excludedBottom)) {
            return false;
        }
        Block top = bottom.getRelative(BlockFace.UP);
        if (!isClaimTotemBlock(top) || !id.equals(totemId(top))) {
            return false;
        }
        String boundClaimId = totemString(bottom, totemClaimIdKey);
        return (boundClaimId.isBlank() || boundClaimId.equals(claim.id))
                && claimContainingTotem(bottom, top) == claim;
    }

    private void bindExistingTotemToClaim(ClaimRegion claim, Block bottom) {
        String id = totemId(bottom);
        TotemBinding binding = new TotemBinding(id, bottom.getWorld().getName(), bottom.getX(), bottom.getY(), bottom.getZ());
        claim.totemBinding = binding;
        claim.totemCore = false;
        markTotemClaim(bottom, claim.id);
        saveClaims();
        notifyClaimOwner(claim, message("totem-bound", "&a领地图腾已绑定至领地 {claim}。"));
    }

    private void bindExistingTotemInsideClaim(ClaimRegion claim) {
        if (claim.totemBinding != null) {
            return;
        }
        Block candidate = findBindableTotemForClaim(claim, null, "");
        if (candidate != null) {
            bindExistingTotemToClaim(claim, candidate);
        }
    }

    private void applyTotemAuras() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ClaimRegion claim = claimAt(player.getLocation());
            if (claim != null && hasActiveTotemCore(claim) && !claim.blocks(ClaimFeature.TOTEM_BLESSING, player)) {
                applyTotemAuraEffects(player);
            }
        }
    }

    private boolean hasActiveTotemCore(ClaimRegion claim) {
        if (!claim.totemCore || claim.totemBinding == null) {
            return false;
        }
        World world = Bukkit.getWorld(claim.totemBinding.world);
        if (world == null) {
            return false;
        }
        Block bottom = world.getBlockAt(claim.totemBinding.x, claim.totemBinding.y, claim.totemBinding.z);
        if (!isClaimTotemBottom(bottom)) {
            return false;
        }
        String id = totemId(bottom);
        Block top = bottom.getRelative(BlockFace.UP);
        return claim.totemBinding.matches(bottom, id)
                && isClaimTotemBlock(top)
                && id.equals(totemId(top));
    }

    private void applyTotemAuraEffects(Player player) {
        applyTotemAuraEffect(player, PotionEffectType.NIGHT_VISION, 0);
        applyTotemAuraEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
        applyTotemAuraEffect(player, PotionEffectType.WATER_BREATHING, 0);
        applyTotemAuraEffect(player, PotionEffectType.SPEED, 0);
        applyTotemAuraEffect(player, PotionEffectType.RESISTANCE, 0);
        applyTotemAuraEffect(player, PotionEffectType.REGENERATION, 0);
        applyTotemAuraEffect(player, PotionEffectType.HASTE, 0);
        applyTotemAuraEffect(player, PotionEffectType.STRENGTH, 0);
        applyTotemAuraEffect(player, PotionEffectType.ABSORPTION, 0);
    }

    private void applyTotemAuraEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && (current.getAmplifier() > amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() > TOTEM_AURA_DURATION_TICKS))) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, TOTEM_AURA_DURATION_TICKS, amplifier, true, false, true), true);
    }

    private void collapseUnsupportedTotemsAbove(Block possibleBottom) {
        if (isClaimTotemBottom(possibleBottom) && !hasTotemSupport(possibleBottom)) {
            collapseTotem(possibleBottom, true);
        }
    }

    private void collapseTotemsAffectedBy(List<Block> blocks, boolean dropItem) {
        Set<String> collapsed = new HashSet<>();
        for (Block block : List.copyOf(blocks)) {
            collapseAffectedTotem(block, dropItem, collapsed);
            collapseAffectedTotem(block.getRelative(BlockFace.UP), dropItem, collapsed);
        }
        blocks.removeIf(block -> isClaimTotemBlock(block) || isClaimTotemBlock(block.getRelative(BlockFace.DOWN)));
    }

    private void protectTotemsFromExplosion(List<Block> blocks) {
        List<Block> possibleBottoms = new ArrayList<>();
        for (Block block : List.copyOf(blocks)) {
            if (isClaimTotemBlock(block)) {
                possibleBottoms.add(totemBottom(block));
            }
            Block above = block.getRelative(BlockFace.UP);
            if (isClaimTotemBottom(above)) {
                possibleBottoms.add(above);
            }
        }
        blocks.removeIf(this::isClaimTotemBlock);
        Bukkit.getScheduler().runTask(this, () -> {
            Set<String> checked = new HashSet<>();
            for (Block bottom : possibleBottoms) {
                String key = bottom.getWorld().getName() + ":" + bottom.getX() + ":" + bottom.getY() + ":" + bottom.getZ();
                if (checked.add(key) && isClaimTotemBottom(bottom) && !hasTotemSupport(bottom)) {
                    collapseTotem(bottom, true);
                }
            }
        });
    }

    private void collapseAffectedTotem(Block block, boolean dropItem, Set<String> collapsed) {
        if (!isClaimTotemBlock(block)) {
            return;
        }
        Block bottom = totemBottom(block);
        String id = totemId(bottom);
        String key = bottom.getWorld().getName() + ":" + bottom.getX() + ":" + bottom.getY() + ":" + bottom.getZ() + ":" + id;
        if (collapsed.add(key)) {
            collapseTotem(bottom, dropItem);
        }
    }

    private void scheduleTotemSupportCheck(Block possibleBottom) {
        if (!isClaimTotemBottom(possibleBottom)) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (isClaimTotemBottom(possibleBottom) && !hasTotemSupport(possibleBottom)) {
                collapseTotem(possibleBottom, true);
            }
        });
    }

    private boolean hasTotemSupport(Block bottom) {
        Block support = bottom.getRelative(BlockFace.DOWN);
        return support.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    private boolean isBoundRing(ItemStack item) {
        if (!isClaimRing(item)) {
            return false;
        }
        String claimId = item.getItemMeta().getPersistentDataContainer().get(ringClaimIdKey, PersistentDataType.STRING);
        return claimId != null && !claimId.isBlank();
    }

    private void openRingMenu(Player player, ItemStack ring, EquipmentSlot hand) {
        if (isBoundRing(ring)) {
            openManageRingMenu(player, ring, hand);
        } else {
            openCreateRingMenu(player, ring, hand);
        }
    }

    private void openTotemMenu(Player player, Block clickedBlock) {
        Block bottom = totemBottom(clickedBlock);
        String totemId = totemId(bottom);
        ClaimRegion claim = claimBoundToTotem(bottom, totemId);
        if (claim == null || claim.totemBinding == null) {
            send(player, message("totem-not-bound", "&c该领地图腾尚未绑定领地。"));
            return;
        }
        if (getConfig().getBoolean("protection.container-open", true) && claim.blocks(ClaimFeature.CONTAINER_OPEN, player)) {
            sendProtected(player, claim, ClaimFeature.CONTAINER_OPEN);
            return;
        }

        TotemMenu menu = new TotemMenu(claim.id, claim.totemBinding);
        Inventory inventory = Bukkit.createInventory(menu, 9, "领地图腾");
        menu.inventory = inventory;
        renderTotemMenu(menu, claim);
        player.openInventory(inventory);
    }

    private void renderTotemMenu(TotemMenu menu, ClaimRegion claim) {
        Inventory inventory = menu.inventory;
        inventory.clear();
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != TOTEM_CORE_SLOT) {
                inventory.setItem(slot, filler);
            }
        }
        if (claim.totemCore) {
            inventory.setItem(TOTEM_CORE_SLOT, createTotemCore());
        }
    }

    private void handleTotemMenuClick(Player player, TotemMenu menu, InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        int rawSlot = event.getRawSlot();
        boolean clickedTopInventory = rawSlot >= 0 && rawSlot < inventory.getSize();

        if (!clickedTopInventory) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                moveOneCoreIntoTotemMenu(inventory, event.getClickedInventory(), event.getSlot(), event.getCurrentItem());
                saveTotemMenu(menu, inventory);
            }
            return;
        }
        event.setCancelled(true);
        if (rawSlot != TOTEM_CORE_SLOT) {
            return;
        }
        if (event.getHotbarButton() >= 0) {
            moveOneHotbarCoreIntoTotemMenu(player, inventory, event.getHotbarButton());
            saveTotemMenu(menu, inventory);
            return;
        }

        ItemStack slotItem = inventory.getItem(TOTEM_CORE_SLOT);
        ItemStack cursor = event.getCursor();
        if (isAir(cursor)) {
            if (isTotemCoreItem(slotItem)) {
                player.setItemOnCursor(slotItem.clone());
                inventory.setItem(TOTEM_CORE_SLOT, null);
                saveTotemMenu(menu, inventory);
            }
            return;
        }
        if (!isTotemCoreItem(cursor) || isTotemCoreItem(slotItem)) {
            return;
        }
        ItemStack singleCore = cursor.clone();
        singleCore.setAmount(1);
        inventory.setItem(TOTEM_CORE_SLOT, singleCore);
        removeOneFromCursor(player, cursor);
        saveTotemMenu(menu, inventory);
    }

    private void moveOneCoreIntoTotemMenu(Inventory menuInventory, Inventory clickedInventory, int clickedSlot, ItemStack source) {
        if (clickedInventory == null || !isAir(menuInventory.getItem(TOTEM_CORE_SLOT)) || !isTotemCoreItem(source)) {
            return;
        }
        ItemStack singleCore = source.clone();
        singleCore.setAmount(1);
        menuInventory.setItem(TOTEM_CORE_SLOT, singleCore);
        decrementInventorySlot(clickedInventory, clickedSlot, source);
    }

    private void moveOneHotbarCoreIntoTotemMenu(Player player, Inventory menuInventory, int hotbarSlot) {
        if (!isAir(menuInventory.getItem(TOTEM_CORE_SLOT))) {
            return;
        }
        ItemStack source = player.getInventory().getItem(hotbarSlot);
        if (!isTotemCoreItem(source)) {
            return;
        }
        ItemStack singleCore = source.clone();
        singleCore.setAmount(1);
        menuInventory.setItem(TOTEM_CORE_SLOT, singleCore);
        decrementInventorySlot(player.getInventory(), hotbarSlot, source);
    }

    private void removeOneFromCursor(Player player, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            player.setItemOnCursor(null);
            return;
        }
        ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(remaining);
    }

    private void decrementInventorySlot(Inventory inventory, int slot, ItemStack source) {
        if (source.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return;
        }
        ItemStack remaining = source.clone();
        remaining.setAmount(source.getAmount() - 1);
        inventory.setItem(slot, remaining);
    }

    private void saveTotemMenu(TotemMenu menu, Inventory inventory) {
        ClaimRegion claim = claims.get(menu.claimId);
        if (claim == null || claim.totemBinding == null || !claim.totemBinding.equals(menu.binding)) {
            return;
        }
        claim.totemCore = isTotemCoreItem(inventory.getItem(TOTEM_CORE_SLOT));
        saveClaims();
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private void openCreateRingMenu(Player player, ItemStack ring, EquipmentSlot hand) {
        RingDraft draft = loadDraftFromRing(ring, player);
        RingSession session = new RingSession(RingMenuMode.CREATE, ring, draft, normalizeHand(hand), player.getUniqueId(), player.getName(), player.hasPermission("xiceclaim.admin"));
        ringSessions.put(player.getUniqueId(), session);
        RingMenu menu = new RingMenu(session);
        Inventory inventory = Bukkit.createInventory(menu, 54, "领地戒指 · 创建领地");
        menu.inventory = inventory;
        renderCreateRingMenu(menu);
        player.openInventory(inventory);
    }

    private void openManageRingMenu(Player player, ItemStack ring, EquipmentSlot hand) {
        String claimId = ring.getItemMeta().getPersistentDataContainer().get(ringClaimIdKey, PersistentDataType.STRING);
        ClaimRegion claim = claims.get(claimId);
        if (claim == null) {
            unbindRing(ring);
            writeRingToHand(player, ring, normalizeHand(hand));
            send(player, message("ring-claim-missing"));
            return;
        }
        if (!claim.canUse(player) && claim.blocks(ClaimFeature.TELEPORT, player)) {
            send(player, message("no-permission"));
            return;
        }
        if (canRenameClaim(player, claim)) {
            claim = synchronizeRingName(player, ring, claim);
        } else {
            setBoundRingDisplayName(ring, claim.name);
        }
        writeRingToHand(player, ring, normalizeHand(hand));
        RingSession session = new RingSession(RingMenuMode.MANAGE, ring, null, normalizeHand(hand), player.getUniqueId(), player.getName(), player.hasPermission("xiceclaim.admin"));
        session.claimId = claim.id;
        ringSessions.put(player.getUniqueId(), session);
        RingMenu menu = new RingMenu(session);
        Inventory inventory = Bukkit.createInventory(menu, 54, "领地戒指 · 管理领地");
        menu.inventory = inventory;
        renderManageRingMenu(menu, claim);
        player.openInventory(inventory);
    }

    private void renderCreateRingMenu(RingMenu menu) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        RingDraft draft = menu.session.draft;
        inventory.setItem(4, menuItem(Material.FLINT, "&b领地戒指", List.of(
                "&7名称：&f" + ringDisplayName(menu.session.ring),
                "&7世界：&f" + draft.world,
                "&7关闭界面会自动保存当前坐标草稿。")));
        setFieldItem(menu, 10, DraftField.X1, "坐标1 X", draft.x1);
        setFieldItem(menu, 11, DraftField.Y1, "坐标1 Y", draft.y1);
        setFieldItem(menu, 12, DraftField.Z1, "坐标1 Z", draft.z1);
        setFieldItem(menu, 14, DraftField.X2, "坐标2 X", draft.x2);
        setFieldItem(menu, 15, DraftField.Y2, "坐标2 Y", draft.y2);
        setFieldItem(menu, 16, DraftField.Z2, "坐标2 Z", draft.z2);
        setAction(menu, 20, "use-pos1", menuItem(Material.COMPASS, "&a使用当前位置作为坐标1", List.of("&7把当前位置写入第一个角点。")));
        setAction(menu, 24, "use-pos2", menuItem(Material.COMPASS, "&a使用当前位置作为坐标2", List.of("&7把当前位置写入第二个角点。")));
        setAction(menu, 36, "adjust:-50", adjustItem(false, 50));
        setAction(menu, 37, "adjust:-10", adjustItem(false, 10));
        setAction(menu, 38, "adjust:-1", adjustItem(false, 1));
        inventory.setItem(40, createDraftStatusItem(menu));
        setAction(menu, 42, "adjust:1", adjustItem(true, 1));
        setAction(menu, 43, "adjust:10", adjustItem(true, 10));
        setAction(menu, 44, "adjust:50", adjustItem(true, 50));
        setAction(menu, 45, "bind-menu", menuItem(Material.MAP, "&b绑定至领地", List.of("&7将这枚戒指绑定到自己拥有或被授权的领地。")));
        setAction(menu, 48, "confirm", menuItem(Material.LIME_CONCRETE, "&a确认创建", List.of("&7使用戒指名称和当前坐标创建领地。")));
        setAction(menu, 50, "cancel", menuItem(Material.BARRIER, "&c取消", List.of("&7关闭界面，不保存本次改动。")));
    }

    private void setFieldItem(RingMenu menu, int slot, DraftField field, String label, int value) {
        Material material = menu.session.selectedField == field ? Material.LIME_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        setAction(menu, slot, "field:" + field.name(), menuItem(material, "&e" + label + "：&f" + value, List.of("&7点击选中后使用下方按钮微调。")));
    }

    private ItemStack adjustItem(boolean positive, int amount) {
        String sign = positive ? "+" : "-";
        Material material = positive ? Material.LIME_DYE : Material.RED_DYE;
        String color = positive ? "&a" : "&c";
        return menuItem(material, color + sign + amount, List.of("&7调整当前选中的坐标。"));
    }

    private void renderBindRingMenu(RingMenu menu) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        inventory.setItem(4, menuItem(Material.MAP, "&b绑定至领地", List.of(
                "&7选择一个自己拥有或被授权的领地。",
                "&7绑定后戒指会发光并指向该领地。")));
        List<ClaimRegion> accessibleClaims = claims.values().stream()
                .filter(claim -> claim.canUse(menu.session.playerUuid))
                .sorted(Comparator
                        .comparing((ClaimRegion claim) -> !claim.ownerUuid.equals(menu.session.playerUuid))
                        .thenComparing(claim -> claim.name.toLowerCase(Locale.ROOT)))
                .limit(28)
                .toList();
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        for (int index = 0; index < accessibleClaims.size(); index++) {
            ClaimRegion claim = accessibleClaims.get(index);
            String relation = claim.ownerUuid.equals(menu.session.playerUuid) ? "&a自己拥有" : "&e已被授权";
            setAction(menu, slots[index], "bind:" + claim.id, menuItem(Material.FILLED_MAP, "&b" + claim.name, List.of(
                    "&7关系：" + relation,
                    "&7所有者：&f" + claim.ownerName,
                    "&7大小：&f" + claim.sizeX() + " x " + claim.sizeY() + " x " + claim.sizeZ(),
                    "&7点击绑定这枚领地戒指。")));
        }
        if (accessibleClaims.isEmpty()) {
            inventory.setItem(22, menuItem(Material.BARRIER, "&c没有可绑定的领地", List.of("&7你还没有拥有或被授权的领地。")));
        }
        setAction(menu, 49, "back:create", menuItem(Material.ARROW, "&e返回创建页面", List.of("&7继续使用坐标创建新领地。")));
    }

    private ItemStack createDraftStatusItem(RingMenu menu) {
        RingDraft draft = menu.session.draft;
        ClaimRegion preview = previewClaim(menu.session, ringDisplayName(menu.session.ring));
        List<String> lore = new ArrayList<>();
        lore.add("&7世界：&f" + draft.world);
        lore.add("&7大小：&e" + preview.sizeX() + " x " + preview.sizeY() + " x " + preview.sizeZ());
        lore.add("&7体积：&e" + preview.volume());
        List<String> errors = validateClaimShape(preview);
        if (errors.isEmpty()) {
            lore.add("&a当前框选合规。");
        } else {
            lore.add("&c当前框选不合规：");
            for (String error : errors) {
                lore.add("&c" + error);
            }
        }
        if (!menu.session.statusLines.isEmpty()) {
            lore.add("&8----------------");
            lore.addAll(menu.session.statusLines);
        }
        return menuItem(Material.OAK_SIGN, "&e框选状态", lore);
    }

    private ClaimRegion previewClaim(RingSession session, String claimName) {
        RingDraft draft = session.draft;
        return ClaimRegion.fromSelection(
                "__preview__",
                claimName,
                session.playerUuid,
                session.playerName,
                new ClaimPoint(draft.world, draft.x1, draft.y1, draft.z1),
                new ClaimPoint(draft.world, draft.x2, draft.y2, draft.z2));
    }

    private ClaimRegion previewResizeClaim(RingSession session, ClaimRegion claim) {
        RingDraft draft = resizeDraft(session, claim);
        return ClaimRegion.fromSelection(
                claim.id,
                claim.name,
                claim.ownerUuid,
                claim.ownerName,
                new ClaimPoint(claim.world, draft.x1, draft.y1, draft.z1),
                new ClaimPoint(claim.world, draft.x2, draft.y2, draft.z2))
                .withClaimDataFrom(claim);
    }

    private RingDraft resizeDraft(RingSession session, ClaimRegion claim) {
        if (session.resizeDraft == null || !claim.id.equals(session.resizeClaimId)) {
            session.resizeDraft = RingDraft.fromClaim(claim);
            session.resizeClaimId = claim.id;
            session.selectedField = DraftField.X1;
        }
        return session.resizeDraft;
    }

    private void renderManageRingMenu(RingMenu menu, ClaimRegion claim) {
        switch (menu.session.manageView) {
            case MAIN -> renderManageMainMenu(menu, claim);
            case MEMBERS -> renderManageMembersMenu(menu, claim);
            case FEATURES -> renderManageFeaturesMenu(menu, claim);
            case RESIZE -> renderResizeClaimMenu(menu, claim);
            case DELETE_CONFIRM -> renderDeleteConfirmMenu(menu, claim);
        }
    }

    private void renderManageMainMenu(RingMenu menu, ClaimRegion claim) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        boolean canManage = canManage(menu.session, claim);
        boolean canUse = claim.canUse(menu.session.playerUuid) || menu.session.admin;
        inventory.setItem(4, menuItem(Material.FLINT, "&b" + claim.name, List.of(
                "&7所有者：&f" + claim.ownerName,
                "&7世界：&f" + claim.world,
                "&7坐标：&f" + claim.minX + "," + claim.minY + "," + claim.minZ + " 到 " + claim.maxX + "," + claim.maxY + "," + claim.maxZ)));
        if (canManage) {
            setAction(menu, 20, "view:members", playerHeadItem(claim.ownerUuid, "&a管理授权玩家", List.of("&7添加或移除可以使用该领地的玩家。")));
            setAction(menu, 22, "teleport", menuItem(Material.ENDER_PEARL, "&b传送至领地", List.of("&7传送到领地图腾正前方。")));
            setAction(menu, 24, "view:features", menuItem(Material.COMPARATOR, "&e管理领地功能", List.of("&7切换领地内的保护规则。")));
            setAction(menu, 38, "view:resize", menuItem(Material.LIGHT_BLUE_STAINED_GLASS, "&b扩张领地", List.of("&7调整领地边界，新的范围必须完整包含旧范围。")));
            setAction(menu, 42, "view:delete_confirm", menuItem(Material.TNT, "&c删除领地", List.of("&7进入删除确认页面。")));
        } else {
            inventory.setItem(20, menuItem(Material.OAK_SIGN, canUse ? "&e已授权领地" : "&e公开传送领地", List.of("&7你不能管理该领地。")));
            setAction(menu, 22, "teleport", menuItem(Material.ENDER_PEARL, "&b传送至领地", List.of("&7传送到领地图腾正前方。")));
        }
    }

    private void renderManageMembersMenu(RingMenu menu, ClaimRegion claim) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        inventory.setItem(4, playerHeadItem(claim.ownerUuid, "&a管理授权玩家", List.of(
                "&7领地：&f" + claim.name,
                "&7左侧添加在线玩家，右侧移除已授权玩家。")));
        int addSlot = 10;
        for (Player target : Bukkit.getOnlinePlayers().stream()
                .filter(target -> !target.getUniqueId().equals(claim.ownerUuid))
                .filter(target -> !claim.members.contains(target.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(7)
                .toList()) {
            setAction(menu, addSlot++, "add:" + target.getUniqueId(), menuItem(Material.PLAYER_HEAD, "&a授权 " + target.getName(), List.of("&7点击添加为领地成员。")));
        }
        int removeSlot = 28;
        for (UUID member : claim.members.stream().sorted(Comparator.comparing(uuid -> claim.memberNames.getOrDefault(uuid, uuid.toString()).toLowerCase(Locale.ROOT))).limit(7).toList()) {
            String name = claim.memberNames.getOrDefault(member, member.toString());
            setAction(menu, removeSlot++, "remove:" + member, menuItem(Material.PAPER, "&c移除 " + name, List.of("&7点击移除领地授权。")));
        }
        inventory.setItem(9, menuItem(Material.OAK_SIGN, "&a可授权的在线玩家", List.of("&7只展示当前在线且未授权的玩家。")));
        inventory.setItem(27, menuItem(Material.OAK_SIGN, "&c已授权成员", List.of("&7点击成员可移除授权。")));
        setAction(menu, 49, "view:main", menuItem(Material.ARROW, "&e返回主菜单", List.of("&7回到领地管理菜单。")));
    }

    private void renderManageFeaturesMenu(RingMenu menu, ClaimRegion claim) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        inventory.setItem(4, menuItem(Material.COMPARATOR, "&e管理领地功能", List.of(
                "&7领地：&f" + claim.name,
                "&7发光表示允许所有人，无光表示限制状态。点击可切换。")));
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        ClaimFeature[] features = ClaimFeature.values();
        for (int index = 0; index < features.length && index < slots.length; index++) {
            ClaimFeature feature = features[index];
            PermissionState state = claim.featureState(feature);
            setAction(menu, slots[index], "feature:" + feature.id, featureItem(feature, state));
        }
        setAction(menu, 49, "view:main", menuItem(Material.ARROW, "&e返回主菜单", List.of("&7回到领地管理菜单。")));
    }

    private void renderResizeClaimMenu(RingMenu menu, ClaimRegion claim) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        RingDraft draft = resizeDraft(menu.session, claim);
        inventory.setItem(4, menuItem(Material.LIGHT_BLUE_STAINED_GLASS, "&b扩张领地", List.of(
                "&7领地：&f" + claim.name,
                "&7旧范围：&f" + claim.minX + "," + claim.minY + "," + claim.minZ + " 到 " + claim.maxX + "," + claim.maxY + "," + claim.maxZ,
                "&7新的范围必须完整包含旧范围。")));
        setFieldItem(menu, 10, DraftField.X1, "最小 X", draft.x1);
        setFieldItem(menu, 11, DraftField.Y1, "最小 Y", draft.y1);
        setFieldItem(menu, 12, DraftField.Z1, "最小 Z", draft.z1);
        setFieldItem(menu, 14, DraftField.X2, "最大 X", draft.x2);
        setFieldItem(menu, 15, DraftField.Y2, "最大 Y", draft.y2);
        setFieldItem(menu, 16, DraftField.Z2, "最大 Z", draft.z2);
        setAction(menu, 20, "resize-use-pos1", menuItem(Material.COMPASS, "&a使用当前位置作为最小角", List.of("&7把当前位置写入最小角。")));
        setAction(menu, 24, "resize-use-pos2", menuItem(Material.COMPASS, "&a使用当前位置作为最大角", List.of("&7把当前位置写入最大角。")));
        setAction(menu, 36, "resize-adjust:-50", adjustItem(false, 50));
        setAction(menu, 37, "resize-adjust:-10", adjustItem(false, 10));
        setAction(menu, 38, "resize-adjust:-1", adjustItem(false, 1));
        inventory.setItem(40, createResizeStatusItem(menu, claim));
        setAction(menu, 42, "resize-adjust:1", adjustItem(true, 1));
        setAction(menu, 43, "resize-adjust:10", adjustItem(true, 10));
        setAction(menu, 44, "resize-adjust:50", adjustItem(true, 50));
        setAction(menu, 45, "resize-reset", menuItem(Material.YELLOW_DYE, "&e重置为当前范围", List.of("&7撤销本次界面中的范围调整。")));
        setAction(menu, 48, "resize-confirm", menuItem(Material.LIME_CONCRETE, "&a确认扩张", List.of("&7保存新的领地范围。")));
        setAction(menu, 50, "view:main", menuItem(Material.ARROW, "&e返回主菜单", List.of("&7不保存本次范围调整。")));
    }

    private ItemStack createResizeStatusItem(RingMenu menu, ClaimRegion claim) {
        ClaimRegion preview = previewResizeClaim(menu.session, claim);
        List<String> lore = new ArrayList<>();
        lore.add("&7世界：&f" + preview.world);
        lore.add("&7新大小：&e" + preview.sizeX() + " x " + preview.sizeY() + " x " + preview.sizeZ());
        lore.add("&7新体积：&e" + preview.volume());
        lore.add("&7原体积：&f" + claim.volume());
        List<String> errors = validateClaimResize(claim, preview);
        if (errors.isEmpty()) {
            lore.add("&a当前范围可用于扩张。");
        } else {
            lore.add("&c当前范围不能保存：");
            for (String error : errors) {
                lore.add("&c" + error);
            }
        }
        if (!menu.session.statusLines.isEmpty()) {
            lore.add("&8----------------");
            lore.addAll(menu.session.statusLines);
        }
        return menuItem(Material.OAK_SIGN, "&e扩张状态", lore);
    }

    private void renderDeleteConfirmMenu(RingMenu menu, ClaimRegion claim) {
        menu.actions.clear();
        Inventory inventory = menu.inventory;
        inventory.clear();
        inventory.setItem(4, menuItem(Material.TNT, "&c删除领地", List.of(
                "&7领地：&f" + claim.name,
                "&7删除后该戒指会恢复为空戒指，但保留当前名称。")));
        setAction(menu, 22, "delete-confirm", menuItem(Material.RED_CONCRETE, "&c确认删除", List.of("&7点击后永久删除该领地。")));
        setAction(menu, 49, "view:features", menuItem(Material.ARROW, "&e取消", List.of("&7返回领地功能页面。")));
    }

    private void handleRingMenuClick(Player player, RingMenu menu, int slot) {
        String action = menu.actions.get(slot);
        if (action == null) {
            return;
        }
        if (menu.session.mode == RingMenuMode.CREATE) {
            handleCreateRingMenuClick(player, menu, action);
        } else if (menu.session.mode == RingMenuMode.MANAGE) {
            handleManageRingMenuClick(player, menu, action);
        } else {
            handleBindRingMenuClick(player, menu, action);
        }
    }

    private void handleCreateRingMenuClick(Player player, RingMenu menu, String action) {
        RingDraft draft = menu.session.draft;
        if (action.startsWith("field:")) {
            menu.session.selectedField = DraftField.valueOf(action.substring("field:".length()));
            menu.session.statusLines.clear();
            renderCreateRingMenu(menu);
            return;
        }
        if (action.startsWith("adjust:")) {
            draft.adjust(menu.session.selectedField, Integer.parseInt(action.substring("adjust:".length())));
            menu.session.statusLines.clear();
            renderCreateRingMenu(menu);
            return;
        }
        if ("use-pos1".equals(action) || "use-pos2".equals(action)) {
            ClaimPoint point = ClaimPoint.from(player.getLocation());
            draft.world = point.world;
            if ("use-pos1".equals(action)) {
                draft.x1 = point.x;
                draft.y1 = point.y;
                draft.z1 = point.z;
            } else {
                draft.x2 = point.x;
                draft.y2 = point.y;
                draft.z2 = point.z;
            }
            menu.session.statusLines.clear();
            renderCreateRingMenu(menu);
            return;
        }
        if ("confirm".equals(action)) {
            createClaimFromRing(player, menu);
            return;
        }
        if ("bind-menu".equals(action)) {
            saveDraftToRing(menu.session.ring, draft);
            writeRingToHand(player, menu.session);
            menu.session.mode = RingMenuMode.BIND;
            renderBindRingMenu(menu);
            return;
        }
        if ("cancel".equals(action)) {
            menu.session.discardOnClose = true;
            ringSessions.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    private void handleBindRingMenuClick(Player player, RingMenu menu, String action) {
        if ("back:create".equals(action)) {
            menu.session.mode = RingMenuMode.CREATE;
            renderCreateRingMenu(menu);
            return;
        }
        if (!action.startsWith("bind:")) {
            return;
        }
        ClaimRegion claim = claims.get(action.substring("bind:".length()));
        if (claim == null) {
            renderBindRingMenu(menu);
            return;
        }
        if (!claim.canUse(player)) {
            send(player, message("no-permission"));
            return;
        }
        bindRing(menu.session.ring, claim, RingDraft.fromClaim(claim));
        writeRingToHand(player, menu.session);
        showClaimParticles(player, claim);
        send(player, message("ring-bound", "&a领地戒指已绑定至领地 {claim}。"), "claim", claim.name);
        menu.session.discardOnClose = true;
        ringSessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void startClaimTotemTeleport(Player player, ClaimRegion claim) {
        cancelPendingTeleport(player, null);
        TeleportTarget firstCheck = validateClaimTotemTeleport(player, claim);
        if (!firstCheck.allowed()) {
            send(player, firstCheck.message());
            return;
        }
        send(player, message("teleport-countdown-started", "&e传送将在 3 秒后开始。"));
        String claimId = claim.id;
        PendingTeleport pending = new PendingTeleport(claimId);
        pendingTeleports.put(player.getUniqueId(), pending);
        pending.particleTask = startTeleportParticles(player);
        pending.teleportTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            PendingTeleport currentPending = pendingTeleports.get(player.getUniqueId());
            if (currentPending != pending) {
                return;
            }
            if (!player.isOnline()) {
                finishPendingTeleport(player);
                return;
            }
            ClaimRegion current = claims.get(currentPending.claimId);
            if (current == null) {
                finishPendingTeleport(player);
                send(player, message("ring-claim-missing"));
                return;
            }
            TeleportTarget secondCheck = validateClaimTotemTeleport(player, current);
            if (!secondCheck.allowed()) {
                finishPendingTeleport(player);
                send(player, secondCheck.message());
                return;
            }
            Location departure = player.getLocation();
            Location destination = secondCheck.destination();
            finishPendingTeleport(player);
            if (!player.teleport(destination)) {
                send(player, message("teleport-failed", "&c传送失败，请稍后再试。"));
                return;
            }
            departure.getWorld().playSound(departure, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            destination.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            applyClaimTeleportSuppression(player);
            send(player, message("teleport-success", "&a已传送至领地 {claim}。"), "claim", current.name);
        }, 60L);
    }

    private void applyClaimTeleportSuppression(Player player) {
        applyWarpSuppression(player, 30_000L);
    }

    private void startChaoticWarpTeleport(Player player) {
        cancelPendingTeleport(player, null);
        send(player, message("teleport-countdown-started", "&e传送将在 3 秒后开始。"));
        PendingTeleport pending = new PendingTeleport(null);
        pendingTeleports.put(player.getUniqueId(), pending);
        pending.particleTask = startTeleportParticles(player);
        topUpChaoticWarpQueue(player.getWorld());
        pending.viewDistanceTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            PendingTeleport currentPending = pendingTeleports.get(player.getUniqueId());
            if (currentPending == pending && player.isOnline()) {
                reducePlayerViewDistance(player, "chaotic-warp-pre-teleport");
            }
        }, Math.max(1L, 60L - PRE_TELEPORT_VIEW_DISTANCE_LEAD_TICKS));
        pending.teleportTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            PendingTeleport currentPending = pendingTeleports.get(player.getUniqueId());
            if (currentPending != pending) {
                return;
            }
            if (!player.isOnline()) {
                finishPendingTeleport(player);
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < 1) {
                finishPendingTeleport(player);
                restorePlayerViewDistance(player, "chaotic-warp-no-level");
                send(player, message("chaotic-warp-no-level", "&c经验等级不足，紊乱跃迁失败。"));
                return;
            }
            ConsumedWarpDestination consumed = consumeChaoticWarpDestination(player);
            if (consumed == null) {
                finishPendingTeleport(player);
                restorePlayerViewDistance(player, "chaotic-warp-no-target");
                topUpChaoticWarpQueue(player.getWorld());
                send(player, message("chaotic-warp-no-target", "&d晶核内似乎空空如也……"));
                return;
            }
            Location departure = player.getLocation();
            Location destination = consumed.location();
            finishPendingTeleport(player);
            reducePlayerViewDistanceIfNeeded(player, "chaotic-warp-teleport");
            if (!player.teleport(destination)) {
                releaseChaoticWarpDestination(consumed.destination());
                restorePlayerViewDistance(player, "chaotic-warp-teleport-failed");
                topUpChaoticWarpQueue(player.getWorld());
                send(player, message("teleport-failed", "&c传送失败，请稍后再试。"));
                return;
            }
            rampRestorePlayerViewDistance(player, "chaotic-warp-success");
            releaseChaoticWarpDestination(consumed.destination());
            topUpChaoticWarpQueue(destination.getWorld());
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.setLevel(player.getLevel() - 1);
            }
            departure.getWorld().playSound(departure, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.75F);
            destination.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.75F);
            applyWarpSuppression(player, CHAOTIC_WARP_SUPPRESSION_MILLIS);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, CHAOTIC_WARP_EFFECT_DURATION_TICKS, 4, true, false, true), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, CHAOTIC_WARP_EFFECT_DURATION_TICKS, 0, true, false, true), true);
        }, 60L);
    }

    private void topUpChaoticWarpQueues() {
        for (World world : Bukkit.getWorlds()) {
            topUpChaoticWarpQueue(world);
        }
    }

    private void configureChaoticWarpBounds() {
        serverMaxWorldSize = readServerMaxWorldSize();
        claimWorldBorder = Math.max(1, getConfig().getInt("limits.world-border", DEFAULT_CLAIM_WORLD_BORDER));
        getLogger().info("Chaotic warp bounds configured: serverMaxWorldSize=" + serverMaxWorldSize
                + ", claimWorldBorder=" + claimWorldBorder);
    }

    private int readServerMaxWorldSize() {
        File serverProperties = new File(Bukkit.getWorldContainer(), "server.properties");
        if (!serverProperties.exists()) {
            getLogger().warning("server.properties not found while configuring chaotic warp bounds: " + serverProperties.getAbsolutePath());
            return DEFAULT_SERVER_MAX_WORLD_SIZE;
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(serverProperties)) {
            properties.load(input);
        } catch (IOException exception) {
            getLogger().warning("Failed to read server.properties for chaotic warp bounds: " + exception.getMessage());
            return DEFAULT_SERVER_MAX_WORLD_SIZE;
        }
        String rawValue = properties.getProperty("max-world-size");
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_SERVER_MAX_WORLD_SIZE;
        }
        try {
            return Math.max(1, Integer.parseInt(rawValue.trim()));
        } catch (NumberFormatException exception) {
            getLogger().warning("Invalid max-world-size in server.properties: " + rawValue);
            return DEFAULT_SERVER_MAX_WORLD_SIZE;
        }
    }

    private ChaoticWarpBounds chaoticWarpBounds(World world) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double bukkitRadius = Math.max(1.0D, border.getSize() / 2.0D - 1.0D);
        double minX = Math.max(center.getX() - bukkitRadius, -serverMaxWorldSize);
        double maxX = Math.min(center.getX() + bukkitRadius, serverMaxWorldSize);
        double minZ = Math.max(center.getZ() - bukkitRadius, -serverMaxWorldSize);
        double maxZ = Math.min(center.getZ() + bukkitRadius, serverMaxWorldSize);
        minX = Math.max(minX, -claimWorldBorder);
        maxX = Math.min(maxX, claimWorldBorder);
        minZ = Math.max(minZ, -claimWorldBorder);
        maxZ = Math.min(maxZ, claimWorldBorder);
        if (maxX - minX < 1.0D || maxZ - minZ < 1.0D) {
            getLogger().warning("No usable chaotic warp bounds for world " + world.getName()
                    + ": bukkitCenter=" + center.getBlockX() + "," + center.getBlockZ()
                    + ", bukkitSize=" + border.getSize()
                    + ", serverMaxWorldSize=" + serverMaxWorldSize
                    + ", claimWorldBorder=" + claimWorldBorder);
            return null;
        }
        return new ChaoticWarpBounds(minX, maxX, minZ, maxZ);
    }

    private void topUpChaoticWarpQueue(World world) {
        if (world == null) {
            return;
        }
        ChaoticWarpQueue queue = chaoticWarpQueue(world);
        int targetSize = chaoticWarpQueueTargetSize();
        while (queue.destinations.size() + queue.inFlightSearches < targetSize
                && queue.inFlightSearches < CHAOTIC_WARP_MAX_CONCURRENT_SEARCHES) {
            if (queue.searchAttempts >= CHAOTIC_WARP_RANDOM_ATTEMPTS) {
                scheduleChaoticWarpQueueRetry(world, queue);
                return;
            }
            searchChaoticWarpDestination(world, queue);
        }
    }

    private void searchChaoticWarpDestination(World world, ChaoticWarpQueue queue) {
        if (!isEnabled()) {
            return;
        }
        if (queue.destinations.size() + queue.inFlightSearches >= chaoticWarpQueueTargetSize()
                || queue.inFlightSearches >= CHAOTIC_WARP_MAX_CONCURRENT_SEARCHES) {
            return;
        }
        if (queue.searchAttempts >= CHAOTIC_WARP_RANDOM_ATTEMPTS) {
            scheduleChaoticWarpQueueRetry(world, queue);
            return;
        }
        ChaoticWarpBounds bounds = chaoticWarpBounds(world);
        if (bounds == null) {
            scheduleChaoticWarpQueueRetry(world, queue);
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = (int) Math.floor(random.nextDouble(bounds.minX(), bounds.maxX()));
        int z = (int) Math.floor(random.nextDouble(bounds.minZ(), bounds.maxZ()));
        int attempt = ++queue.searchAttempts;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        queue.inFlightSearches++;
        logChaoticWarpQueue(world, queue, "candidate-start attempt=" + attempt
                + " block=" + x + "," + z
                + " chunk=" + chunkX + "," + chunkZ
                + " bounds=" + bounds.describe());
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, throwable) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!isEnabled() || queue != chaoticWarpQueues.get(world.getName())) {
                return;
            }
            queue.inFlightSearches = Math.max(0, queue.inFlightSearches - 1);
            if (throwable != null || chunk == null || !world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                String reason = throwable == null ? "chunk-not-loaded" : throwable.getClass().getSimpleName();
                logChaoticWarpQueue(world, queue, "candidate-load-failed attempt=" + attempt + " reason=" + reason);
                topUpChaoticWarpQueue(world);
                return;
            }
            SafeRandomDestinationResult result = findSafeRandomDestination(world, x, z, new Location(world, 0.0D, 0.0D, 0.0D));
            Location destination = result.destination();
            if (destination != null && bounds.contains(destination) && world.getWorldBorder().isInside(destination)) {
                WarpDestination warpDestination = new WarpDestination(
                        world.getName(),
                        destination.getBlockX(),
                        destination.getBlockY(),
                        destination.getBlockZ(),
                        chunk.getX(),
                        chunk.getZ());
                retainChaoticWarpDestination(warpDestination);
                warmChaoticWarpDestination(world, queue, warpDestination, attempt);
                return;
            }
            String reason = destination == null
                    ? result.failureReason()
                    : (bounds.contains(destination) ? "outside-bukkit-world-border" : "outside-effective-bounds");
            logChaoticWarpQueue(world, queue, "candidate-rejected attempt=" + attempt
                    + " reason=" + reason
                    + " bounds=" + bounds.describe()
                    + " scanned=" + result.scannedBlocks()
                    + " spaceOk=" + result.spaceOk()
                    + " floorOk=" + result.floorOk()
                    + " deepWater=" + result.deepWater()
                    + " claimBlocked=" + result.claimBlocked()
                    + " underground=" + result.underground()
                    + " feetBlocked=" + result.feetBlocked()
                    + " headBlocked=" + result.headBlocked());
            topUpChaoticWarpQueue(world);
        }));
    }

    private void scheduleChaoticWarpQueueRetry(World world, ChaoticWarpQueue queue) {
        if (queue.retryScheduled) {
            return;
        }
        queue.retryScheduled = true;
        logChaoticWarpQueue(world, queue, "retry-scheduled attempts=" + queue.searchAttempts + " delayTicks=100");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isEnabled() || queue != chaoticWarpQueues.get(world.getName())) {
                return;
            }
            queue.retryScheduled = false;
            queue.searchAttempts = 0;
            logChaoticWarpQueue(world, queue, "retry-start");
            topUpChaoticWarpQueue(world);
        }, 100L);
    }

    private void logChaoticWarpQueue(World world, ChaoticWarpQueue queue, String event) {
        getLogger().info("[ChaoticWarpQueue] world=" + world.getName()
                + " event=" + event
                + " queued=" + queue.destinations.size()
                + " inFlight=" + queue.inFlightSearches
                + " target=" + chaoticWarpQueueTargetSize()
                + " maxConcurrent=" + CHAOTIC_WARP_MAX_CONCURRENT_SEARCHES);
    }

    private void warmChaoticWarpDestination(World world, ChaoticWarpQueue queue, WarpDestination destination, int attempt) {
        long startNanos = System.nanoTime();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dx <= CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dx++) {
            for (int dz = -CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dz <= CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dz++) {
                futures.add(world.getChunkAtAsync(destination.chunkX + dx, destination.chunkZ + dz, true));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!isEnabled() || queue != chaoticWarpQueues.get(world.getName())) {
                releaseChaoticWarpDestination(destination);
                return;
            }
            double warmMillis = (System.nanoTime() - startNanos) / 1_000_000.0D;
            if (throwable != null) {
                releaseChaoticWarpDestination(destination);
                logChaoticWarpQueue(world, queue, "candidate-warm-failed attempt=" + attempt
                        + " destination=" + destination.x + "," + destination.y + "," + destination.z
                        + " radius=" + CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS
                        + " chunks=" + futures.size()
                        + " duration=" + String.format(Locale.ROOT, "%.2fms", warmMillis)
                        + " reason=" + throwable.getClass().getSimpleName());
                topUpChaoticWarpQueue(world);
                return;
            }
            if (queue.destinations.size() >= chaoticWarpQueueTargetSize()) {
                releaseChaoticWarpDestination(destination);
                logChaoticWarpQueue(world, queue, "candidate-warm-discarded-full attempt=" + attempt
                        + " destination=" + destination.x + "," + destination.y + "," + destination.z
                        + " radius=" + CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS
                        + " chunks=" + futures.size()
                        + " duration=" + String.format(Locale.ROOT, "%.2fms", warmMillis));
                return;
            }
            queue.destinations.addLast(destination);
            queue.searchAttempts = 0;
            logChaoticWarpQueue(world, queue, "candidate-accepted attempt=" + attempt
                    + " destination=" + destination.x + "," + destination.y + "," + destination.z
                    + " radius=" + CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS
                    + " chunks=" + futures.size()
                    + " warm=" + String.format(Locale.ROOT, "%.2fms", warmMillis));
            saveChaoticWarpQueues();
            topUpChaoticWarpQueue(world);
        }));
    }

    private ConsumedWarpDestination consumeChaoticWarpDestination(Player player) {
        ChaoticWarpQueue queue = chaoticWarpQueue(player.getWorld());
        boolean changed = false;
        WarpDestination destination;
        while ((destination = queue.destinations.pollFirst()) != null) {
            changed = true;
            Location location = validateQueuedWarpDestination(player, destination);
            if (location != null) {
                logChaoticWarpQueue(player.getWorld(), queue, "destination-consumed"
                        + " destination=" + destination.x + "," + destination.y + "," + destination.z);
                saveChaoticWarpQueues();
                return new ConsumedWarpDestination(destination, location);
            }
            releaseChaoticWarpDestination(destination);
            logChaoticWarpQueue(player.getWorld(), queue, "destination-discarded"
                    + " destination=" + destination.x + "," + destination.y + "," + destination.z);
        }
        logChaoticWarpQueue(player.getWorld(), queue, "queue-empty");
        if (changed) {
            saveChaoticWarpQueues();
        }
        return null;
    }

    private Location validateQueuedWarpDestination(Player player, WarpDestination destination) {
        World world = Bukkit.getWorld(destination.world);
        if (world == null || !world.equals(player.getWorld()) || !world.isChunkLoaded(destination.chunkX, destination.chunkZ)) {
            return null;
        }
        Block feet = world.getBlockAt(destination.x, destination.y, destination.z);
        Location location = feet.getLocation().add(0.5D, 0.0D, 0.5D);
        ChaoticWarpBounds bounds = chaoticWarpBounds(world);
        if (bounds == null
                || !bounds.contains(location)
                || !world.getWorldBorder().isInside(location)
                || !hasTeleportSpace(feet)
                || !hasTeleportFloor(feet)
                || isDeepWaterLanding(feet)
                || isChaoticWarpClaimBlocked(feet)
                || isChaoticWarpUndergroundLanding(feet)) {
            return null;
        }
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        return location;
    }

    private ChaoticWarpQueue chaoticWarpQueue(World world) {
        return chaoticWarpQueues.computeIfAbsent(world.getName(), ignored -> new ChaoticWarpQueue());
    }

    private int chaoticWarpQueueTargetSize() {
        return Math.max(0, maxOnlineSinceStart) + CHAOTIC_WARP_QUEUE_EXTRA_SIZE;
    }

    private void retainChaoticWarpDestination(WarpDestination destination) {
        World world = Bukkit.getWorld(destination.world);
        if (world == null) {
            return;
        }
        ChaoticWarpQueue queue = chaoticWarpQueue(world);
        for (int dx = -CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dx <= CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dx++) {
            for (int dz = -CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dz <= CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dz++) {
                int chunkX = destination.chunkX + dx;
                int chunkZ = destination.chunkZ + dz;
                long key = chunkKey(chunkX, chunkZ);
                int current = queue.chunkTicketCounts.getOrDefault(key, 0);
                if (current == 0) {
                    world.addPluginChunkTicket(chunkX, chunkZ, this);
                }
                queue.chunkTicketCounts.put(key, current + 1);
            }
        }
    }

    private void releaseChaoticWarpDestination(WarpDestination destination) {
        World world = Bukkit.getWorld(destination.world);
        if (world == null) {
            return;
        }
        ChaoticWarpQueue queue = chaoticWarpQueues.get(world.getName());
        if (queue == null) {
            return;
        }
        for (int dx = -CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dx <= CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dx++) {
            for (int dz = -CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dz <= CHAOTIC_WARP_PRELOAD_CHUNK_RADIUS; dz++) {
                int chunkX = destination.chunkX + dx;
                int chunkZ = destination.chunkZ + dz;
                long key = chunkKey(chunkX, chunkZ);
                int current = queue.chunkTicketCounts.getOrDefault(key, 0);
                if (current <= 1) {
                    queue.chunkTicketCounts.remove(key);
                    world.removePluginChunkTicket(chunkX, chunkZ, this);
                } else {
                    queue.chunkTicketCounts.put(key, current - 1);
                }
            }
        }
    }

    private void releaseChaoticWarpQueueTickets() {
        for (Map.Entry<String, ChaoticWarpQueue> entry : chaoticWarpQueues.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            for (long key : entry.getValue().chunkTicketCounts.keySet()) {
                world.removePluginChunkTicket(chunkX(key), chunkZ(key), this);
            }
        }
        chaoticWarpQueues.clear();
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private int chunkX(long key) {
        return (int) (key >> 32);
    }

    private int chunkZ(long key) {
        return (int) key;
    }

    private SafeRandomDestinationResult findSafeRandomDestination(World world, int x, int z, Location current) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int scannedBlocks = 0;
        int spaceOk = 0;
        int floorOk = 0;
        int deepWater = 0;
        int claimBlocked = 0;
        int underground = 0;
        int feetBlocked = 0;
        int headBlocked = 0;
        for (int y = maxY; y >= minY; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = feet.getRelative(BlockFace.UP);
            scannedBlocks++;
            boolean feetPassable = feet.isPassable();
            boolean headPassable = head.isPassable();
            if (!feetPassable) {
                feetBlocked++;
            }
            if (!headPassable) {
                headBlocked++;
            }
            if (!feetPassable || !headPassable) {
                continue;
            }
            spaceOk++;
            if (hasTeleportFloor(feet)) {
                floorOk++;
                if (isDeepWaterLanding(feet)) {
                    deepWater++;
                    continue;
                }
                if (isChaoticWarpClaimBlocked(feet)) {
                    claimBlocked++;
                    continue;
                }
                if (isChaoticWarpUndergroundLanding(feet)) {
                    underground++;
                    continue;
                }
                Location destination = feet.getLocation().add(0.5, 0.0, 0.5);
                destination.setYaw(current.getYaw());
                destination.setPitch(current.getPitch());
                return new SafeRandomDestinationResult(destination, scannedBlocks, spaceOk, floorOk, deepWater, claimBlocked, underground, feetBlocked, headBlocked);
            }
        }
        return new SafeRandomDestinationResult(null, scannedBlocks, spaceOk, floorOk, deepWater, claimBlocked, underground, feetBlocked, headBlocked);
    }

    private boolean isChaoticWarpClaimBlocked(Block feet) {
        return claimAt(feet.getLocation()) != null
                || claimAt(feet.getRelative(BlockFace.UP).getLocation()) != null;
    }

    private boolean isChaoticWarpUndergroundLanding(Block feet) {
        World.Environment environment = feet.getWorld().getEnvironment();
        if (environment == World.Environment.NETHER) {
            return false;
        }
        return feet.getWorld().getHighestBlockYAt(feet.getX(), feet.getZ()) >= feet.getY();
    }

    private void applyWarpSuppression(Player player, long durationMillis) {
        var plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getClass().getMethod("applyWarpSuppression", Player.class, long.class).invoke(plugin, player, durationMillis);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to apply warp suppression: " + exception.getMessage());
        }
    }

    private BukkitTask startTeleportParticles(Player player) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(145, 65, 255), 1.25F);
        return new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!player.isOnline() || !pendingTeleports.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                spawnTeleportParticles(player.getLocation(), elapsedTicks, dust);
                elapsedTicks += 5;
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void spawnTeleportParticles(Location base, int elapsedTicks, Particle.DustOptions dust) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        double progress = Math.min(1.0D, elapsedTicks / 60.0D);
        int count = 8 + (int) Math.round(progress * 28.0D);
        double phase = elapsedTicks * 0.28D;
        for (int index = 0; index < count; index++) {
            double angle = phase + index * Math.PI * 2.0D / count;
            double baseRadius = 0.14D + (index % 4) * 0.035D;
            double outward = 0.26D + progress * 0.42D;
            double upward = 0.42D + progress * 0.36D + (index % 3) * 0.05D;
            Location origin = base.clone().add(Math.cos(angle) * baseRadius, 0.06D, Math.sin(angle) * baseRadius);
            world.spawnParticle(
                    Particle.WITCH,
                    origin,
                    0,
                    Math.cos(angle) * outward,
                    upward,
                    Math.sin(angle) * outward,
                    0.85D);
            if (index % 3 == 0) {
                Location spark = origin.clone().add(Math.cos(angle) * 0.18D, 0.12D + progress * 0.55D, Math.sin(angle) * 0.18D);
                world.spawnParticle(Particle.DUST, spark, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
            }
        }
    }

    private void cancelPendingTeleport(Player player, String message) {
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        pending.cancel();
        restorePlayerViewDistance(player, "teleport-cancel");
        if (message != null && !message.isBlank()) {
            send(player, message);
        }
    }

    private void finishPendingTeleport(Player player) {
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending != null) {
            pending.cancel();
        }
    }

    private void reducePlayerViewDistance(Player player, String reason) {
        restorePlayerViewDistance(player, "replace");
        PlayerViewDistanceState state = new PlayerViewDistanceState(player.getViewDistance(), player.getSendViewDistance());
        temporaryViewDistances.put(player.getUniqueId(), state);
        applyPlayerViewDistance(player, TEMPORARY_VIEW_DISTANCE, TEMPORARY_VIEW_DISTANCE);
        getLogger().info("[Perf] view-distance reduce plugin=XiceClaim reason=" + reason
                + " player=" + player.getName()
                + " originalView=" + state.originalViewDistance
                + " originalSend=" + state.originalSendViewDistance
                + " temporary=" + TEMPORARY_VIEW_DISTANCE);
    }

    private void reducePlayerViewDistanceIfNeeded(Player player, String reason) {
        if (temporaryViewDistances.containsKey(player.getUniqueId())) {
            return;
        }
        reducePlayerViewDistance(player, reason);
    }

    private void rampRestorePlayerViewDistance(Player player, String reason) {
        PlayerViewDistanceState state = temporaryViewDistances.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.restoreTask != null) {
            state.restoreTask.cancel();
        }
        state.restoreTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            PlayerViewDistanceState current = temporaryViewDistances.get(player.getUniqueId());
            if (current != state || !player.isOnline()) {
                if (state.restoreTask != null) {
                    state.restoreTask.cancel();
                }
                return;
            }
            int nextView = nextRestoredDistance(player.getViewDistance(), state.originalViewDistance);
            int nextSend = nextRestoredDistance(player.getSendViewDistance(), state.originalSendViewDistance);
            applyPlayerViewDistance(player, nextView, nextSend);
            if (nextView == state.originalViewDistance && nextSend == state.originalSendViewDistance) {
                if (state.restoreTask != null) {
                    state.restoreTask.cancel();
                    state.restoreTask = null;
                }
                temporaryViewDistances.remove(player.getUniqueId());
                getLogger().info("[Perf] view-distance restored plugin=XiceClaim reason=" + reason
                        + " player=" + player.getName()
                        + " view=" + nextView
                        + " send=" + nextSend);
            }
        }, VIEW_DISTANCE_RAMP_INTERVAL_TICKS, VIEW_DISTANCE_RAMP_INTERVAL_TICKS);
    }

    private void restorePlayerViewDistance(Player player, String reason) {
        PlayerViewDistanceState state = temporaryViewDistances.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.restoreTask != null) {
            state.restoreTask.cancel();
            state.restoreTask = null;
        }
        applyPlayerViewDistance(player, state.originalViewDistance, state.originalSendViewDistance);
        getLogger().info("[Perf] view-distance restore-immediate plugin=XiceClaim reason=" + reason
                + " player=" + player.getName()
                + " view=" + state.originalViewDistance
                + " send=" + state.originalSendViewDistance);
    }

    private int nextRestoredDistance(int current, int target) {
        if (current < target) {
            return Math.min(target, current + 1);
        }
        if (current > target) {
            return Math.max(target, current - 1);
        }
        return target;
    }

    private void applyPlayerViewDistance(Player player, int viewDistance, int sendViewDistance) {
        int safeViewDistance = Math.max(TEMPORARY_VIEW_DISTANCE, viewDistance);
        int safeSendViewDistance = Math.max(TEMPORARY_VIEW_DISTANCE, sendViewDistance);
        try {
            player.setViewDistance(safeViewDistance);
            player.setSendViewDistance(safeSendViewDistance);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("[Perf] view-distance apply-failed plugin=XiceClaim player=" + player.getName()
                    + " view=" + safeViewDistance
                    + " send=" + safeSendViewDistance
                    + " error=" + exception.getMessage());
        }
    }

    private TeleportTarget validateClaimTotemTeleport(Player player, ClaimRegion claim) {
        if (claim.blocks(ClaimFeature.TELEPORT, player)) {
            return TeleportTarget.failure(message("teleport-no-permission", "&c你没有传送至该领地的权限。"));
        }
        if (claim.totemBinding == null) {
            return TeleportTarget.failure(message("teleport-no-totem", "&c该领地尚未绑定领地图腾，无法传送。"));
        }
        World world = Bukkit.getWorld(claim.totemBinding.world);
        if (world == null) {
            return TeleportTarget.failure(message("teleport-world-missing", "&c领地图腾所在世界不可用，无法传送。"));
        }
        Block bottom = world.getBlockAt(claim.totemBinding.x, claim.totemBinding.y, claim.totemBinding.z);
        Block top = bottom.getRelative(BlockFace.UP);
        String boundTotemId = claim.totemBinding.id;
        if (!isClaimTotemBottom(bottom)
                || !isClaimTotemBlock(top)
                || !totemId(bottom).equals(totemId(top))
                || (!boundTotemId.isBlank() && !boundTotemId.equals(totemId(bottom)))) {
            return TeleportTarget.failure(message("teleport-totem-missing", "&c该领地绑定的领地图腾不存在或不完整，无法传送。"));
        }
        BlockFace front = totemFront(bottom);
        Block target = bottom.getRelative(front);
        if (!hasTeleportSpace(target)) {
            return TeleportTarget.failure(message("teleport-target-blocked", "&c领地图腾前方没有足够空间，无法传送。"));
        }
        if (!hasTeleportFloor(target)) {
            return TeleportTarget.failure(message("teleport-target-unsafe", "&c领地图腾前方脚下不安全，无法传送。"));
        }
        Location destination = target.getLocation().add(0.5, 0.0, 0.5);
        Location current = player.getLocation();
        destination.setYaw(current.getYaw());
        destination.setPitch(current.getPitch());
        return TeleportTarget.success(destination);
    }

    private boolean hasTeleportSpace(Block feet) {
        World world = feet.getWorld();
        if (feet.getY() + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block head = feet.getRelative(BlockFace.UP);
        return feet.isPassable()
                && head.isPassable();
    }

    private boolean hasTeleportFloor(Block feet) {
        World world = feet.getWorld();
        if (feet.getY() <= world.getMinHeight()) {
            return false;
        }
        Block floor = feet.getRelative(BlockFace.DOWN);
        return floor.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    private boolean isDeepWaterLanding(Block feet) {
        return isWaterLike(feet) && isWaterLike(feet.getRelative(BlockFace.UP));
    }

    private boolean isWaterLike(Block block) {
        if (block.getType() == Material.WATER) {
            return true;
        }
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return true;
        }
        return switch (block.getType()) {
            case KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> false;
        };
    }

    private void handleManageRingMenuClick(Player player, RingMenu menu, String action) {
        ClaimRegion claim = claims.get(menu.session.claimId);
        if (claim == null) {
            unbindRing(menu.session.ring);
            writeRingToHand(player, menu.session);
            menu.session.discardOnClose = true;
            ringSessions.remove(player.getUniqueId());
            player.closeInventory();
            send(player, message("ring-claim-missing"));
            return;
        }
        if (!claim.canUse(player) && claim.blocks(ClaimFeature.TELEPORT, player)) {
            send(player, message("no-permission"));
            return;
        }
        if (action.startsWith("view:")) {
            ManageView nextView = ManageView.valueOf(action.substring("view:".length()).toUpperCase(Locale.ROOT));
            if (nextView != ManageView.MAIN && !canManage(player, claim)) {
                send(player, message("no-permission"));
                return;
            }
            if (nextView == ManageView.RESIZE) {
                menu.session.resizeDraft = RingDraft.fromClaim(claim);
                menu.session.resizeClaimId = claim.id;
                menu.session.selectedField = DraftField.X1;
            } else if (menu.session.manageView == ManageView.RESIZE) {
                menu.session.resizeDraft = null;
                menu.session.resizeClaimId = "";
            }
            menu.session.statusLines.clear();
            menu.session.manageView = nextView;
            renderManageRingMenu(menu, claim);
            return;
        }
        if (menu.session.manageView == ManageView.RESIZE) {
            handleResizeRingMenuClick(player, menu, claim, action);
            return;
        }
        if ("teleport".equals(action)) {
            startClaimTotemTeleport(player, claim);
            menu.session.discardOnClose = true;
            ringSessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if ("delete-confirm".equals(action)) {
            if (!canManage(player, claim)) {
                send(player, message("no-permission"));
                return;
            }
            claims.remove(claim.id);
            saveClaims();
            unbindRing(menu.session.ring);
            writeRingToHand(player, menu.session);
            menu.session.discardOnClose = true;
            ringSessions.remove(player.getUniqueId());
            player.closeInventory();
            send(player, message("ring-claim-deleted"), "claim", claim.name);
            return;
        }
        if (action.startsWith("feature:")) {
            if (!canManage(player, claim)) {
                send(player, message("no-permission"));
                return;
            }
            ClaimFeature feature = ClaimFeature.fromId(action.substring("feature:".length()));
            if (feature != null) {
                claim.cycleFeature(feature);
                saveClaims();
                renderManageRingMenu(menu, claim);
            }
            return;
        }
        if (action.startsWith("add:")) {
            if (!canManage(player, claim)) {
                send(player, message("no-permission"));
                return;
            }
            UUID targetUuid = UUID.fromString(action.substring("add:".length()));
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                claim.members.add(targetUuid);
                claim.memberNames.put(targetUuid, target.getName());
                saveClaims();
                renderManageRingMenu(menu, claim);
            }
            return;
        }
        if (action.startsWith("remove:")) {
            if (!canManage(player, claim)) {
                send(player, message("no-permission"));
                return;
            }
            UUID targetUuid = UUID.fromString(action.substring("remove:".length()));
            claim.members.remove(targetUuid);
            claim.memberNames.remove(targetUuid);
            saveClaims();
            renderManageRingMenu(menu, claim);
        }
    }

    private void handleResizeRingMenuClick(Player player, RingMenu menu, ClaimRegion claim, String action) {
        if (!canManage(player, claim)) {
            send(player, message("no-permission"));
            return;
        }
        RingDraft draft = resizeDraft(menu.session, claim);
        if (action.startsWith("field:")) {
            menu.session.selectedField = DraftField.valueOf(action.substring("field:".length()));
            menu.session.statusLines.clear();
            renderResizeClaimMenu(menu, claim);
            return;
        }
        if (action.startsWith("resize-adjust:")) {
            draft.adjust(menu.session.selectedField, Integer.parseInt(action.substring("resize-adjust:".length())));
            menu.session.statusLines.clear();
            renderResizeClaimMenu(menu, claim);
            return;
        }
        if ("resize-use-pos1".equals(action) || "resize-use-pos2".equals(action)) {
            ClaimPoint point = ClaimPoint.from(player.getLocation());
            if (!claim.world.equals(point.world)) {
                setResizeStatus(menu, claim, List.of("&c当前位置不在该领地所在世界。"));
                return;
            }
            if ("resize-use-pos1".equals(action)) {
                draft.x1 = point.x;
                draft.y1 = point.y;
                draft.z1 = point.z;
            } else {
                draft.x2 = point.x;
                draft.y2 = point.y;
                draft.z2 = point.z;
            }
            menu.session.statusLines.clear();
            renderResizeClaimMenu(menu, claim);
            return;
        }
        if ("resize-reset".equals(action)) {
            menu.session.resizeDraft = RingDraft.fromClaim(claim);
            menu.session.resizeClaimId = claim.id;
            menu.session.statusLines.clear();
            renderResizeClaimMenu(menu, claim);
            return;
        }
        if ("resize-confirm".equals(action)) {
            resizeClaimFromRing(player, menu, claim);
        }
    }

    private void createClaimFromRing(Player player, RingMenu menu) {
        RingDraft draft = menu.session.draft;
        String claimName = ringDisplayName(menu.session.ring);
        if (!isValidClaimName(claimName)) {
            setCreateStatus(menu, List.of("&c领地名称不合规。", "&7仅允许文字、数字、下划线和短横线，长度 1-24。"));
            return;
        }
        if (claimByName(claimName) != null) {
            setCreateStatus(menu, List.of("&c已存在同名领地：&f" + claimName));
            return;
        }
        int maxClaims = getConfig().getInt("limits.max-claims-per-player", 3);
        long ownedCount = claims.values().stream().filter(claim -> claim.ownerUuid.equals(player.getUniqueId())).count();
        if (!player.hasPermission("xiceclaim.admin") && ownedCount >= maxClaims) {
            setCreateStatus(menu, List.of("&c领地数量已达上限：&f" + maxClaims));
            return;
        }
        ClaimRegion claim = ClaimRegion.fromSelection(
                UUID.randomUUID().toString(),
                claimName,
                player.getUniqueId(),
                player.getName(),
                new ClaimPoint(draft.world, draft.x1, draft.y1, draft.z1),
                new ClaimPoint(draft.world, draft.x2, draft.y2, draft.z2));
        List<String> errors = validateClaimShape(claim);
        if (!errors.isEmpty()) {
            setCreateStatus(menu, errors.stream().map(error -> "&c" + error).toList());
            return;
        }
        claims.put(claim.id, claim);
        bindExistingTotemInsideClaim(claim);
        saveClaims();
        bindRing(menu.session.ring, claim, draft);
        writeRingToHand(player, menu.session);
        showClaimParticles(player, claim);
        send(player, message("ring-created"), "claim", claim.name);
        menu.session.discardOnClose = true;
        ringSessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void resizeClaimFromRing(Player player, RingMenu menu, ClaimRegion claim) {
        ClaimRegion resized = previewResizeClaim(menu.session, claim);
        List<String> errors = validateClaimResize(claim, resized);
        if (!errors.isEmpty()) {
            setResizeStatus(menu, claim, errors.stream().map(error -> "&c" + error).toList());
            return;
        }
        claims.put(resized.id, resized);
        bindExistingTotemInsideClaim(resized);
        saveClaims();
        bindRing(menu.session.ring, resized, RingDraft.fromClaim(resized));
        writeRingToHand(player, menu.session);
        showClaimParticles(player, resized);
        send(player, "&a领地 " + resized.name + " 已扩张为 " + resized.sizeX() + " x " + resized.sizeY() + " x " + resized.sizeZ() + "。");
        menu.session.resizeDraft = null;
        menu.session.resizeClaimId = "";
        menu.session.statusLines.clear();
        menu.session.manageView = ManageView.MAIN;
        renderManageRingMenu(menu, resized);
    }

    private void setCreateStatus(RingMenu menu, List<String> statusLines) {
        menu.session.statusLines.clear();
        menu.session.statusLines.addAll(statusLines);
        renderCreateRingMenu(menu);
    }

    private void setResizeStatus(RingMenu menu, ClaimRegion claim, List<String> statusLines) {
        menu.session.statusLines.clear();
        menu.session.statusLines.addAll(statusLines);
        renderResizeClaimMenu(menu, claim);
    }

    private ClaimRegion synchronizeRingName(Player player, ItemStack ring, ClaimRegion claim) {
        String ringName = ringDisplayName(ring);
        String syncedName = syncedClaimName(ring);
        if (syncedName.isBlank()) {
            setBoundRingDisplayName(ring, claim.name);
            return claim;
        }
        if (ringName.equals(syncedName)) {
            if (!claim.name.equals(syncedName)) {
                setBoundRingDisplayName(ring, claim.name);
            }
            return claim;
        }
        if (ringName.equals(claim.name) || !isValidClaimName(ringName)) {
            setBoundRingDisplayName(ring, claim.name);
            return claim;
        }
        ClaimRegion duplicate = claimByName(ringName);
        if (duplicate != null && !duplicate.id.equals(claim.id)) {
            send(player, message("duplicate-name"), "claim", ringName);
            setBoundRingDisplayName(ring, claim.name);
            return claim;
        }
        ClaimRegion renamed = claim.withName(ringName);
        claims.put(renamed.id, renamed);
        saveClaims();
        synchronizeBoundRingNames(renamed);
        return renamed;
    }

    private void applyAnvilRingRename(Player player, ItemStack ring) {
        if (!isClaimRing(ring)) {
            return;
        }
        String claimId = ring.getItemMeta().getPersistentDataContainer().get(ringClaimIdKey, PersistentDataType.STRING);
        if (claimId == null || claimId.isBlank()) {
            return;
        }
        ClaimRegion claim = claims.get(claimId);
        if (claim == null || !canRenameClaim(player, claim)) {
            return;
        }
        synchronizeRingName(player, ring, claim);
    }

    private void synchronizeBoundRingNames(ClaimRegion claim) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (isRingBoundToClaim(item, claim.id)) {
                    setBoundRingDisplayName(item, claim.name);
                    inventory.setItem(slot, item);
                }
            }
            synchronizeInventoryRingNames(player.getEnderChest(), player.getUniqueId());
            ItemStack cursor = player.getItemOnCursor();
            if (isRingBoundToClaim(cursor, claim.id)) {
                setBoundRingDisplayName(cursor, claim.name);
                player.setItemOnCursor(cursor);
            }
            RingSession session = ringSessions.get(player.getUniqueId());
            if (session != null && isRingBoundToClaim(session.ring, claim.id)) {
                setBoundRingDisplayName(session.ring, claim.name);
            }
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (shouldSynchronizeInventory(openInventory)) {
                synchronizeInventoryRingNames(openInventory, null);
            }
        }
    }

    private boolean isRingBoundToClaim(ItemStack item, String claimId) {
        if (!isClaimRing(item)) {
            return false;
        }
        String boundClaimId = item.getItemMeta().getPersistentDataContainer().get(ringClaimIdKey, PersistentDataType.STRING);
        return claimId.equals(boundClaimId);
    }

    private void synchronizePlayerRingNames(Player player) {
        synchronizeInventoryRingNames(player.getInventory(), player.getUniqueId());
        synchronizeInventoryRingNames(player.getEnderChest(), player.getUniqueId());
        ItemStack cursor = player.getItemOnCursor();
        if (synchronizeRingNamesInItem(cursor, player.getUniqueId())) {
            player.setItemOnCursor(cursor);
        }
    }

    private boolean shouldSynchronizeInventory(Inventory inventory) {
        if (inventory == null || inventory.getHolder() instanceof RingMenu || inventory.getHolder() instanceof TotemMenu) {
            return false;
        }
        String type = inventory.getType().name();
        return !type.endsWith("ANVIL");
    }

    private boolean synchronizeInventoryRingNames(Inventory inventory, UUID holderUuid) {
        if (inventory == null) {
            return false;
        }
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (synchronizeRingNamesInItem(item, holderUuid)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    private boolean synchronizeRingNamesInItem(ItemStack item, UUID holderUuid) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        boolean changed = synchronizeSingleRingName(item, holderUuid);
        if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta blockStateMeta) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof InventoryHolder holder && synchronizeInventoryRingNames(holder.getInventory(), null)) {
                blockStateMeta.setBlockState(blockState);
                item.setItemMeta(blockStateMeta);
                changed = true;
            }
        }
        return changed;
    }

    private boolean synchronizeSingleRingName(ItemStack ring, UUID holderUuid) {
        if (!isClaimRing(ring)) {
            return false;
        }
        PersistentDataContainer data = ring.getItemMeta().getPersistentDataContainer();
        String claimId = data.get(ringClaimIdKey, PersistentDataType.STRING);
        if (claimId == null || claimId.isBlank()) {
            return false;
        }
        ClaimRegion claim = claims.get(claimId);
        if (claim == null) {
            return false;
        }
        String syncedName = data.getOrDefault(ringClaimNameKey, PersistentDataType.STRING, "");
        String displayName = ringDisplayName(ring);
        boolean ownerMayHavePendingRename = holderUuid != null && claim.ownerUuid.equals(holderUuid);
        if (syncedName.isBlank()) {
            setBoundRingDisplayName(ring, claim.name);
            return true;
        }
        if (!ownerMayHavePendingRename || displayName.equals(syncedName)) {
            if (!displayName.equals(claim.name) || !syncedName.equals(claim.name)) {
                setBoundRingDisplayName(ring, claim.name);
                return true;
            }
            return false;
        }
        return false;
    }

    private String syncedClaimName(ItemStack ring) {
        if (!isClaimRing(ring)) {
            return "";
        }
        return ring.getItemMeta().getPersistentDataContainer().getOrDefault(ringClaimNameKey, PersistentDataType.STRING, "");
    }

    private void bindRing(ItemStack ring, ClaimRegion claim, RingDraft draft) {
        ItemMeta meta = ring.getItemMeta();
        meta.setDisplayName(color("&b" + claim.name));
        meta.setLore(List.of(color("&7已绑定领地。"), color("&7右键打开领地管理界面。")));
        meta.setEnchantmentGlintOverride(true);
        applyRingItemModel(meta);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(ringKey, PersistentDataType.BYTE, (byte) 1);
        data.set(ringClaimIdKey, PersistentDataType.STRING, claim.id);
        data.set(ringClaimNameKey, PersistentDataType.STRING, claim.name);
        ring.setItemMeta(meta);
        saveDraftToRing(ring, draft);
    }

    private void unbindRing(ItemStack ring) {
        ItemMeta meta = ring.getItemMeta();
        meta.setLore(List.of(color("&7右键打开领地创建界面。"), color("&7可用铁砧改名，戒指名即领地名。")));
        meta.setEnchantmentGlintOverride(false);
        applyRingItemModel(meta);
        meta.getPersistentDataContainer().remove(ringClaimIdKey);
        meta.getPersistentDataContainer().remove(ringClaimNameKey);
        ring.setItemMeta(meta);
    }

    private void setBoundRingDisplayName(ItemStack ring, String name) {
        ItemMeta meta = ring.getItemMeta();
        meta.setDisplayName(color("&b" + name));
        meta.setEnchantmentGlintOverride(true);
        applyRingItemModel(meta);
        meta.getPersistentDataContainer().set(ringClaimNameKey, PersistentDataType.STRING, name);
        ring.setItemMeta(meta);
    }

    private void setRingDisplayName(ItemStack ring, String name, boolean bound) {
        ItemMeta meta = ring.getItemMeta();
        meta.setDisplayName(color("&b" + name));
        meta.setEnchantmentGlintOverride(bound);
        applyRingItemModel(meta);
        ring.setItemMeta(meta);
    }

    private void applyRingItemModel(ItemMeta meta) {
        meta.setItemModel(ringItemModelKey);
    }

    private void applyTotemItemModel(ItemMeta meta) {
        meta.setItemModel(totemItemModelKey);
    }

    private void applyTotemCoreItemModel(ItemMeta meta) {
        meta.setItemModel(totemCoreItemModelKey);
    }

    private void applyChaoticWarpCoreItemModel(ItemMeta meta) {
        meta.setItemModel(chaoticWarpCoreItemModelKey);
    }

    private EquipmentSlot normalizeHand(EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
    }

    private void writeRingToHand(Player player, RingSession session) {
        writeRingToHand(player, session.ring, session.hand);
    }

    private void writeRingToHand(Player player, ItemStack ring, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(ring);
        } else {
            player.getInventory().setItemInMainHand(ring);
        }
    }

    private RingDraft loadDraftFromRing(ItemStack ring, Player player) {
        ClaimPoint current = ClaimPoint.from(player.getLocation());
        RingDraft draft = RingDraft.fromPoint(current);
        PersistentDataContainer data = ring.getItemMeta().getPersistentDataContainer();
        draft.world = data.getOrDefault(ringWorldKey, PersistentDataType.STRING, current.world);
        draft.x1 = data.getOrDefault(ringX1Key, PersistentDataType.INTEGER, current.x);
        draft.y1 = data.getOrDefault(ringY1Key, PersistentDataType.INTEGER, current.y);
        draft.z1 = data.getOrDefault(ringZ1Key, PersistentDataType.INTEGER, current.z);
        draft.x2 = data.getOrDefault(ringX2Key, PersistentDataType.INTEGER, current.x);
        draft.y2 = data.getOrDefault(ringY2Key, PersistentDataType.INTEGER, current.y);
        draft.z2 = data.getOrDefault(ringZ2Key, PersistentDataType.INTEGER, current.z);
        return draft;
    }

    private void saveDraftToRing(ItemStack ring, RingDraft draft) {
        ItemMeta meta = ring.getItemMeta();
        applyRingItemModel(meta);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(ringKey, PersistentDataType.BYTE, (byte) 1);
        data.set(ringWorldKey, PersistentDataType.STRING, draft.world);
        data.set(ringX1Key, PersistentDataType.INTEGER, draft.x1);
        data.set(ringY1Key, PersistentDataType.INTEGER, draft.y1);
        data.set(ringZ1Key, PersistentDataType.INTEGER, draft.z1);
        data.set(ringX2Key, PersistentDataType.INTEGER, draft.x2);
        data.set(ringY2Key, PersistentDataType.INTEGER, draft.y2);
        data.set(ringZ2Key, PersistentDataType.INTEGER, draft.z2);
        ring.setItemMeta(meta);
    }

    private String ringDisplayName(ItemStack ring) {
        if (ring == null || !ring.hasItemMeta() || !ring.getItemMeta().hasDisplayName()) {
            return "领地戒指";
        }
        return ChatColor.stripColor(ring.getItemMeta().getDisplayName()).trim();
    }

    private boolean isValidClaimName(String name) {
        return name != null && CLAIM_NAME_PATTERN.matcher(name).matches();
    }

    private void setAction(RingMenu menu, int slot, String action, ItemStack item) {
        menu.actions.put(slot, action);
        menu.inventory.setItem(slot, item);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(this::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, boolean glint) {
        ItemStack item = menuItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(glint);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack featureItem(ClaimFeature feature, PermissionState state) {
        return menuItem(feature.material, feature.displayName, List.of(
                "&7当前状态： " + state.displayName,
                "&7点击切换：禁止未授权 / 允许所有人 / 全体禁止。"), state == PermissionState.ALLOW_ALL);
    }

    private ItemStack playerHeadItem(UUID playerUuid, String name, List<String> lore) {
        ItemStack item = menuItem(Material.PLAYER_HEAD, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUuid));
            item.setItemMeta(skullMeta);
        }
        return item;
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
            claimsConfig.set(path + ".totem", null);
            if (claim.totemBinding != null) {
                claimsConfig.set(path + ".totem.id", claim.totemBinding.id);
                claimsConfig.set(path + ".totem.world", claim.totemBinding.world);
                claimsConfig.set(path + ".totem.x", claim.totemBinding.x);
                claimsConfig.set(path + ".totem.y", claim.totemBinding.y);
                claimsConfig.set(path + ".totem.z", claim.totemBinding.z);
                claimsConfig.set(path + ".totem.core", claim.totemCore);
            }
            claimsConfig.set(path + ".allowed-features", null);
            claimsConfig.set(path + ".disabled-features", null);
            claimsConfig.set(path + ".feature-states", null);
            for (Map.Entry<String, String> entry : claim.featureStateIds().entrySet()) {
                claimsConfig.set(path + ".feature-states." + entry.getKey(), entry.getValue());
            }
            List<String> members = claim.members.stream().map(UUID::toString).sorted().toList();
            claimsConfig.set(path + ".members", members);
            claimsConfig.set(path + ".member-names", null);
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

    private void loadChaoticWarpQueues() {
        chaoticWarpQueues.clear();
        chaoticWarpQueuesFile = new File(getDataFolder(), "chaotic-warp-queues.yml");
        if (!chaoticWarpQueuesFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(chaoticWarpQueuesFile);
        ConfigurationSection root = config.getConfigurationSection("queues");
        if (root == null) {
            return;
        }
        int loaded = 0;
        boolean changed = false;
        for (String worldName : root.getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("Skipping chaotic warp queue for missing world: " + worldName);
                changed = true;
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(worldName);
            if (section == null) {
                continue;
            }
            ChaoticWarpQueue queue = chaoticWarpQueue(world);
            List<String> keys = new ArrayList<>(section.getKeys(false));
            keys.sort(Comparator.comparingInt(this::parseQueueIndex));
            for (String key : keys) {
                ConfigurationSection destinationSection = section.getConfigurationSection(key);
                if (destinationSection == null
                        || !destinationSection.isInt("x")
                        || !destinationSection.isInt("y")
                        || !destinationSection.isInt("z")) {
                    continue;
                }
                int x = destinationSection.getInt("x");
                int y = destinationSection.getInt("y");
                int z = destinationSection.getInt("z");
                int chunkX = destinationSection.isInt("chunk-x") ? destinationSection.getInt("chunk-x") : x >> 4;
                int chunkZ = destinationSection.isInt("chunk-z") ? destinationSection.getInt("chunk-z") : z >> 4;
                WarpDestination destination = new WarpDestination(worldName, x, y, z, chunkX, chunkZ);
                ChaoticWarpBounds bounds = chaoticWarpBounds(world);
                if (bounds == null || !bounds.contains(x, z)) {
                    changed = true;
                    continue;
                }
                retainChaoticWarpDestination(destination);
                queue.destinations.addLast(destination);
                loaded++;
            }
            if (!queue.destinations.isEmpty()) {
                logChaoticWarpQueue(world, queue, "loaded-persistent-queue");
            }
        }
        if (loaded > 0) {
            getLogger().info("Loaded " + loaded + " chaotic warp queued destinations from " + chaoticWarpQueuesFile.getName());
        }
        if (changed) {
            saveChaoticWarpQueues();
        }
    }

    private int parseQueueIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private void saveChaoticWarpQueues() {
        if (chaoticWarpQueuesFile == null) {
            chaoticWarpQueuesFile = new File(getDataFolder(), "chaotic-warp-queues.yml");
        }
        getDataFolder().mkdirs();
        FileConfiguration config = new YamlConfiguration();
        config.set("version", 1);
        for (Map.Entry<String, ChaoticWarpQueue> entry : chaoticWarpQueues.entrySet()) {
            int index = 0;
            for (WarpDestination destination : entry.getValue().destinations) {
                String path = "queues." + entry.getKey() + "." + index;
                config.set(path + ".x", destination.x);
                config.set(path + ".y", destination.y);
                config.set(path + ".z", destination.z);
                config.set(path + ".chunk-x", destination.chunkX);
                config.set(path + ".chunk-z", destination.chunkZ);
                index++;
            }
        }
        try {
            config.save(chaoticWarpQueuesFile);
        } catch (IOException exception) {
            getLogger().warning("Failed to save chaotic-warp-queues.yml: " + exception.getMessage());
        }
    }

    private void showHeldRingPreviews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ClaimRegion claim = heldRingPreviewClaim(player);
            if (claim != null) {
                drawClaimParticles(player, claim);
            }
        }
    }

    private ClaimRegion heldRingPreviewClaim(Player player) {
        RingSession session = ringSessions.get(player.getUniqueId());
        if (session != null
                && session.mode == RingMenuMode.CREATE
                && isClaimRing(heldItem(player, session.hand))) {
            return previewClaim(session, ringDisplayName(session.ring));
        }
        if (session != null
                && session.mode == RingMenuMode.MANAGE
                && session.manageView == ManageView.RESIZE
                && isClaimRing(heldItem(player, session.hand))) {
            ClaimRegion claim = claims.get(session.claimId);
            if (claim != null && claim.world.equals(player.getWorld().getName())) {
                return previewResizeClaim(session, claim);
            }
        }
        ItemStack ring = heldClaimRing(player);
        if (ring == null) {
            return null;
        }
        String claimId = ring.getItemMeta().getPersistentDataContainer().get(ringClaimIdKey, PersistentDataType.STRING);
        if (claimId != null && !claimId.isBlank()) {
            ClaimRegion claim = claims.get(claimId);
            return claim != null && claim.world.equals(player.getWorld().getName()) ? claim : null;
        }
        RingDraft draft = loadDraftFromRing(ring, player);
        if (!draft.world.equals(player.getWorld().getName())) {
            return null;
        }
        return ClaimRegion.fromSelection(
                "__preview__",
                ringDisplayName(ring),
                player.getUniqueId(),
                player.getName(),
                new ClaimPoint(draft.world, draft.x1, draft.y1, draft.z1),
                new ClaimPoint(draft.world, draft.x2, draft.y2, draft.z2));
    }

    private ItemStack heldClaimRing(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isClaimRing(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return isClaimRing(offHand) ? offHand : null;
    }

    private ItemStack heldItem(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private void showClaimParticles(Player player, ClaimRegion claim) {
        World world = Bukkit.getWorld(claim.world);
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        int pulses = Math.max(1, getConfig().getInt("visualization.pulses", 10));
        int intervalTicks = Math.max(1, getConfig().getInt("visualization.interval-ticks", 10));
        new BukkitRunnable() {
            private int remaining = pulses;

            @Override
            public void run() {
                if (remaining-- <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }
                drawClaimParticles(player, claim);
            }
        }.runTaskTimer(this, 0L, intervalTicks);
    }

    private void drawClaimParticles(Player player, ClaimRegion claim) {
        Particle.DustOptions edgeDust = new Particle.DustOptions(Color.LIME, 1.2F);
        Particle.DustOptions faceDust = new Particle.DustOptions(Color.RED, 0.8F);
        int maxSize = Math.max(Math.max(claim.sizeX(), claim.sizeY()), claim.sizeZ());
        int edgeStep = Math.max(Math.max(1, getConfig().getInt("visualization.edge-step", 1)), maxSize / 128);
        int gridStep = Math.max(1, maxSize / 16);
        ClaimBounds bounds = ClaimBounds.from(claim);
        drawEdges(player, bounds, edgeStep, edgeDust);
        drawFaceGrid(player, bounds, gridStep, faceDust);
    }

    private void drawEdges(Player player, ClaimBounds bounds, int step, Particle.DustOptions dust) {
        for (int x = bounds.minX; x <= bounds.maxX; x += step) {
            spawnDust(player, x, bounds.minY, bounds.minZ, dust);
            spawnDust(player, x, bounds.minY, bounds.maxZ, dust);
            spawnDust(player, x, bounds.maxY, bounds.minZ, dust);
            spawnDust(player, x, bounds.maxY, bounds.maxZ, dust);
        }
        for (int y = bounds.minY; y <= bounds.maxY; y += step) {
            spawnDust(player, bounds.minX, y, bounds.minZ, dust);
            spawnDust(player, bounds.minX, y, bounds.maxZ, dust);
            spawnDust(player, bounds.maxX, y, bounds.minZ, dust);
            spawnDust(player, bounds.maxX, y, bounds.maxZ, dust);
        }
        for (int z = bounds.minZ; z <= bounds.maxZ; z += step) {
            spawnDust(player, bounds.minX, bounds.minY, z, dust);
            spawnDust(player, bounds.minX, bounds.maxY, z, dust);
            spawnDust(player, bounds.maxX, bounds.minY, z, dust);
            spawnDust(player, bounds.maxX, bounds.maxY, z, dust);
        }
    }

    private void drawFaceGrid(Player player, ClaimBounds bounds, int step, Particle.DustOptions dust) {
        for (int x = bounds.minX; x <= bounds.maxX; x += step) {
            for (int y = bounds.minY; y <= bounds.maxY; y += step) {
                spawnDust(player, x, y, bounds.minZ, dust);
                spawnDust(player, x, y, bounds.maxZ, dust);
            }
        }
        for (int z = bounds.minZ; z <= bounds.maxZ; z += step) {
            for (int y = bounds.minY; y <= bounds.maxY; y += step) {
                spawnDust(player, bounds.minX, y, z, dust);
                spawnDust(player, bounds.maxX, y, z, dust);
            }
        }
        for (int x = bounds.minX; x <= bounds.maxX; x += step) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z += step) {
                spawnDust(player, x, bounds.minY, z, dust);
                spawnDust(player, x, bounds.maxY, z, dust);
            }
        }
    }

    private void spawnDust(Player player, int x, int y, int z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0D, 0D, 0D, 0D, dust);
    }

    private void sendUsage(CommandSender sender) {
        for (String line : getConfig().getStringList("messages.usage")) {
            send(sender, line);
        }
    }

    private String message(String key) {
        return getConfig().getString("messages." + key, "");
    }

    private String message(String key, String fallback) {
        return getConfig().getString("messages." + key, fallback);
    }

    private String messageText(String key) {
        return color(getConfig().getString("messages." + key, key));
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
            return List.of("help", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private enum RingMenuMode {
        CREATE,
        MANAGE,
        BIND
    }

    private enum ManageView {
        MAIN,
        MEMBERS,
        FEATURES,
        RESIZE,
        DELETE_CONFIRM
    }

    private enum ClaimFeature {
        BLOCK_PLACE("block-place", Material.GRASS_BLOCK, "&e放置方块", PermissionState.DENY_UNTRUSTED),
        BLOCK_BREAK("block-break", Material.IRON_PICKAXE, "&e破坏方块", PermissionState.DENY_UNTRUSTED),
        BLOCK_INTERACT("block-interact", Material.OAK_BUTTON, "&e交互方块", PermissionState.DENY_UNTRUSTED),
        CONTAINER_OPEN("container-open", Material.CHEST, "&e打开容器", PermissionState.DENY_UNTRUSTED),
        ENTITY_INTERACT("entity-interact", Material.ARMOR_STAND, "&e交互实体", PermissionState.DENY_UNTRUSTED),
        WATER_BUCKET("water-bucket", Material.WATER_BUCKET, "&e使用水桶", PermissionState.DENY_UNTRUSTED),
        LAVA_BUCKET("lava-bucket", Material.LAVA_BUCKET, "&e使用岩浆桶", PermissionState.DENY_UNTRUSTED),
        WATER_FLOW("water-flow", Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&e水流动", PermissionState.ALLOW_ALL),
        LAVA_FLOW("lava-flow", Material.ORANGE_STAINED_GLASS_PANE, "&e岩浆流动", PermissionState.ALLOW_ALL),
        FIRE("fire", Material.FLINT_AND_STEEL, "&e火焰蔓延和燃烧破坏", PermissionState.DENY_UNTRUSTED),
        EXPLOSION("explosion", Material.TNT, "&e爆炸破坏方块", PermissionState.DENY_UNTRUSTED),
        PISTON_USE("piston-use", Material.PISTON, "&e领地内使用活塞", PermissionState.ALLOW_ALL),
        PISTON("piston", Material.STICKY_PISTON, "&e活塞跨越领地边界", PermissionState.DENY_UNTRUSTED),
        ANIMAL_SPAWN("animal-spawn", Material.WHEAT, "&e领地内刷新动物", PermissionState.ALLOW_ALL),
        ANIMAL_DAMAGE("animal-damage", Material.LEATHER, "&e伤害动物", PermissionState.DENY_UNTRUSTED),
        MONSTER_SPAWN("monster-spawn", Material.ROTTEN_FLESH, "&e领地内刷新怪物", PermissionState.ALLOW_ALL),
        MONSTER_DAMAGE("monster-damage", Material.IRON_SWORD, "&e伤害怪物", PermissionState.DENY_UNTRUSTED),
        PVP("pvp", Material.DIAMOND_SWORD, "&e允许PVP", PermissionState.ALLOW_ALL),
        TOTEM_BLESSING("totem-blessing", Material.TOTEM_OF_UNDYING, "&e图腾祝福", PermissionState.ALLOW_ALL),
        TELEPORT("teleport", Material.ENDER_PEARL, "&e传送权限", PermissionState.DENY_UNTRUSTED),
        START_EVENT("start-event", Material.OMINOUS_BOTTLE, "&e开启事件", PermissionState.DENY_UNTRUSTED);

        private final String id;
        private final Material material;
        private final String displayName;
        private final PermissionState defaultState;

        ClaimFeature(String id, Material material, String displayName, PermissionState defaultState) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.defaultState = defaultState;
        }

        private static ClaimFeature fromId(String id) {
            for (ClaimFeature feature : values()) {
                if (feature.id.equalsIgnoreCase(id)) {
                    return feature;
                }
            }
            return null;
        }

    }

    private enum PermissionState {
        DENY_UNTRUSTED("deny-untrusted", "&c禁止未授权"),
        ALLOW_ALL("allow-all", "&a允许所有人"),
        DENY_ALL("deny-all", "&4全体禁止");

        private final String id;
        private final String displayName;

        PermissionState(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        private PermissionState next() {
            return switch (this) {
                case DENY_UNTRUSTED -> ALLOW_ALL;
                case ALLOW_ALL -> DENY_ALL;
                case DENY_ALL -> DENY_UNTRUSTED;
            };
        }

        private static PermissionState fromId(String id) {
            for (PermissionState state : values()) {
                if (state.id.equalsIgnoreCase(id)) {
                    return state;
                }
            }
            return null;
        }
    }

    private enum DraftField {
        X1,
        Y1,
        Z1,
        X2,
        Y2,
        Z2
    }

    private static final class RingSession {
        private RingMenuMode mode;
        private final ItemStack ring;
        private final RingDraft draft;
        private final EquipmentSlot hand;
        private final UUID playerUuid;
        private final String playerName;
        private final boolean admin;
        private final List<String> statusLines = new ArrayList<>();
        private DraftField selectedField = DraftField.X1;
        private ManageView manageView = ManageView.MAIN;
        private String claimId = "";
        private String resizeClaimId = "";
        private RingDraft resizeDraft;
        private boolean discardOnClose;

        private RingSession(RingMenuMode mode, ItemStack ring, RingDraft draft, EquipmentSlot hand, UUID playerUuid, String playerName, boolean admin) {
            this.mode = mode;
            this.ring = ring;
            this.draft = draft;
            this.hand = hand;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.admin = admin;
        }
    }

    private static final class RingMenu implements InventoryHolder {
        private final RingSession session;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        private RingMenu(RingSession session) {
            this.session = session;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class TotemMenu implements InventoryHolder {
        private final String claimId;
        private final TotemBinding binding;
        private Inventory inventory;

        private TotemMenu(String claimId, TotemBinding binding) {
            this.claimId = claimId;
            this.binding = binding;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class RingDraft {
        private String world;
        private int x1;
        private int y1;
        private int z1;
        private int x2;
        private int y2;
        private int z2;

        private static RingDraft fromPoint(ClaimPoint point) {
            RingDraft draft = new RingDraft();
            draft.world = point.world;
            draft.x1 = point.x;
            draft.y1 = point.y;
            draft.z1 = point.z;
            draft.x2 = point.x;
            draft.y2 = point.y;
            draft.z2 = point.z;
            return draft;
        }

        private static RingDraft fromClaim(ClaimRegion claim) {
            RingDraft draft = new RingDraft();
            draft.world = claim.world;
            draft.x1 = claim.minX;
            draft.y1 = claim.minY;
            draft.z1 = claim.minZ;
            draft.x2 = claim.maxX;
            draft.y2 = claim.maxY;
            draft.z2 = claim.maxZ;
            return draft;
        }

        private void adjust(DraftField field, int amount) {
            switch (field) {
                case X1 -> x1 += amount;
                case Y1 -> y1 += amount;
                case Z1 -> z1 += amount;
                case X2 -> x2 += amount;
                case Y2 -> y2 += amount;
                case Z2 -> z2 += amount;
            }
        }
    }

    private static final class Selection {
        private ClaimPoint pos1;
        private ClaimPoint pos2;
    }

    private record ClaimPoint(String world, int x, int y, int z) {
        private static ClaimPoint from(Location location) {
            return new ClaimPoint(
                    Objects.requireNonNull(location.getWorld()).getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }
    }

    private record ClaimBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static ClaimBounds from(ClaimRegion claim) {
            return new ClaimBounds(
                    claim.minX,
                    claim.maxX + 1,
                    claim.minY,
                    claim.maxY + 1,
                    claim.minZ,
                    claim.maxZ + 1);
        }
    }

    private record TotemBinding(String id, String world, int x, int y, int z) {
        private boolean matches(Block bottom, String totemId) {
            return world.equals(bottom.getWorld().getName())
                    && x == bottom.getX()
                    && y == bottom.getY()
                    && z == bottom.getZ()
                    && (id.isBlank() || totemId.isBlank() || id.equals(totemId));
        }
    }

    private record TeleportTarget(boolean allowed, Location destination, String message) {
        private static TeleportTarget success(Location destination) {
            return new TeleportTarget(true, destination, "");
        }

        private static TeleportTarget failure(String message) {
            return new TeleportTarget(false, null, message);
        }
    }

    private record WarpDestination(String world, int x, int y, int z, int chunkX, int chunkZ) {
    }

    private record ConsumedWarpDestination(WarpDestination destination, Location location) {
    }

    private record SafeRandomDestinationResult(
            Location destination,
            int scannedBlocks,
            int spaceOk,
            int floorOk,
            int deepWater,
            int claimBlocked,
            int underground,
            int feetBlocked,
            int headBlocked) {
        private String failureReason() {
            if (spaceOk <= 0) {
                return "no-passable-space";
            }
            if (floorOk <= 0) {
                return "no-full-support";
            }
            if (deepWater > 0) {
                return "deep-water";
            }
            if (claimBlocked > 0) {
                return "claim-blocked";
            }
            if (underground > 0) {
                return "underground";
            }
            return "no-safe-landing";
        }
    }

    private record ChaoticWarpBounds(double minX, double maxX, double minZ, double maxZ) {
        private boolean contains(Location location) {
            return contains(location.getX(), location.getZ());
        }

        private boolean contains(double x, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        private String describe() {
            return Math.floor(minX) + ".." + Math.ceil(maxX) + "," + Math.floor(minZ) + ".." + Math.ceil(maxZ);
        }
    }

    private static final class ChaoticWarpQueue {
        private final Deque<WarpDestination> destinations = new ArrayDeque<>();
        private final Map<Long, Integer> chunkTicketCounts = new HashMap<>();
        private int inFlightSearches;
        private int searchAttempts;
        private boolean retryScheduled;
    }

    private static final class PendingTeleport {
        private final String claimId;
        private BukkitTask teleportTask;
        private BukkitTask particleTask;
        private BukkitTask viewDistanceTask;

        private PendingTeleport(String claimId) {
            this.claimId = claimId;
        }

        private void cancel() {
            if (teleportTask != null) {
                teleportTask.cancel();
            }
            if (particleTask != null) {
                particleTask.cancel();
            }
            if (viewDistanceTask != null) {
                viewDistanceTask.cancel();
            }
        }
    }

    private static final class PlayerViewDistanceState {
        private final int originalViewDistance;
        private final int originalSendViewDistance;
        private BukkitTask restoreTask;

        private PlayerViewDistanceState(int originalViewDistance, int originalSendViewDistance) {
            this.originalViewDistance = originalViewDistance;
            this.originalSendViewDistance = originalSendViewDistance;
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
        private final Map<String, PermissionState> featureStates;
        private TotemBinding totemBinding;
        private boolean totemCore;

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
                Map<UUID, String> memberNames,
                Map<String, PermissionState> featureStates,
                TotemBinding totemBinding,
                boolean totemCore
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
            this.featureStates = featureStates;
            this.totemBinding = totemBinding;
            this.totemCore = totemBinding != null && totemCore;
        }

        private static ClaimRegion fromSelection(
                String id,
                String name,
                UUID ownerUuid,
                String ownerName,
                ClaimPoint pos1,
                ClaimPoint pos2
        ) {
            return new ClaimRegion(
                    id,
                    name,
                    ownerUuid,
                    ownerName,
                    pos1.world,
                    Math.min(pos1.x, pos2.x),
                    Math.max(pos1.x, pos2.x),
                    Math.min(pos1.y, pos2.y),
                    Math.max(pos1.y, pos2.y),
                    Math.min(pos1.z, pos2.z),
                    Math.max(pos1.z, pos2.z),
                    System.currentTimeMillis(),
                    new HashSet<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    null,
                    false);
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
            Map<String, PermissionState> featureStates = new HashMap<>();
            ConfigurationSection stateSection = section.getConfigurationSection("feature-states");
            if (stateSection != null) {
                for (String featureId : stateSection.getKeys(false)) {
                    ClaimFeature feature = ClaimFeature.fromId(featureId);
                    PermissionState state = PermissionState.fromId(stateSection.getString(featureId, ""));
                    if (feature != null && state != null) {
                        featureStates.put(feature.id, state);
                    }
                }
            }
            for (String featureId : section.getStringList("allowed-features")) {
                if ("bucket".equalsIgnoreCase(featureId)) {
                    featureStates.putIfAbsent(ClaimFeature.WATER_BUCKET.id, PermissionState.ALLOW_ALL);
                    featureStates.putIfAbsent(ClaimFeature.LAVA_BUCKET.id, PermissionState.ALLOW_ALL);
                    continue;
                }
                ClaimFeature feature = ClaimFeature.fromId(featureId);
                if (feature != null) {
                    featureStates.putIfAbsent(feature.id, PermissionState.ALLOW_ALL);
                }
            }
            for (String featureId : section.getStringList("disabled-features")) {
                ClaimFeature feature = ClaimFeature.fromId(featureId);
                if (feature != null) {
                    featureStates.putIfAbsent(feature.id, PermissionState.DENY_UNTRUSTED);
                }
            }
            TotemBinding totemBinding = null;
            boolean totemCore = false;
            ConfigurationSection totemSection = section.getConfigurationSection("totem");
            if (totemSection != null) {
                String totemId = totemSection.getString("id", "");
                String totemWorld = totemSection.getString("world", section.getString("world", "main"));
                totemBinding = new TotemBinding(
                        totemId,
                        totemWorld,
                        totemSection.getInt("x"),
                        totemSection.getInt("y"),
                        totemSection.getInt("z"));
                totemCore = totemSection.getBoolean("core", false);
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
                    memberNames,
                    featureStates,
                    totemBinding,
                    totemCore);
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
            return canUse(player.getUniqueId())
                    || player.hasPermission("xiceclaim.admin");
        }

        private boolean canUse(UUID playerUuid) {
            return ownerUuid.equals(playerUuid) || members.contains(playerUuid);
        }

        private PermissionState featureState(ClaimFeature feature) {
            return featureStates.getOrDefault(feature.id, feature.defaultState);
        }

        private boolean blocks(ClaimFeature feature, Player player) {
            PermissionState state = featureState(feature);
            return state == PermissionState.DENY_ALL
                    || (state == PermissionState.DENY_UNTRUSTED && (player == null || !canUse(player)));
        }

        private boolean blocksActorless(ClaimFeature feature) {
            return featureState(feature) != PermissionState.ALLOW_ALL;
        }

        private void cycleFeature(ClaimFeature feature) {
            featureStates.put(feature.id, featureState(feature).next());
        }

        private Map<String, String> featureStateIds() {
            Map<String, String> states = new java.util.LinkedHashMap<>();
            for (ClaimFeature feature : ClaimFeature.values()) {
                states.put(feature.id, featureState(feature).id);
            }
            return states;
        }

        private ClaimRegion withName(String newName) {
            return new ClaimRegion(
                    id,
                    newName,
                    ownerUuid,
                    ownerName,
                    world,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    createdAt,
                    members,
                    memberNames,
                    featureStates,
                    totemBinding,
                    totemCore);
        }

        private ClaimRegion withClaimDataFrom(ClaimRegion source) {
            return new ClaimRegion(
                    id,
                    name,
                    source.ownerUuid,
                    source.ownerName,
                    world,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    source.createdAt,
                    new HashSet<>(source.members),
                    new HashMap<>(source.memberNames),
                    new HashMap<>(source.featureStates),
                    source.totemBinding,
                    source.totemCore);
        }

        private int sizeX() {
            return maxX - minX + 1;
        }

        private int sizeY() {
            return maxY - minY + 1;
        }

        private int sizeZ() {
            return maxZ - minZ + 1;
        }

        private long volume() {
            return (long) sizeX() * sizeY() * sizeZ();
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
