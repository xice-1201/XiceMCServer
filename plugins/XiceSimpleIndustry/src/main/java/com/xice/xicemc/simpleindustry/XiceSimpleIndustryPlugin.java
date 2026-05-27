package com.xice.xicemc.simpleindustry;

import com.xice.xicemc.customitem.CustomBlockDefinition;
import com.xice.xicemc.customitem.CustomBlockService;
import com.xice.xicemc.customitem.CustomItemDefinition;
import com.xice.xicemc.customitem.CustomItemService;
import com.xice.xicemc.hud.HudBossBar;
import com.xice.xicemc.hud.HudService;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class XiceSimpleIndustryPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final Material MACHINE_CARRIER = Material.BARRIER;
    private static final Material MACHINE_ITEM = Material.DROPPER;
    private static final int MENU_SIZE = 27;
    private static final int INPUT_SLOT = 11;
    private static final int CONFIRM_SLOT = 12;
    private static final int BREEDER_SWITCH_SLOT = 11;
    private static final int BREEDER_OUTPUT_SLOT = 13;
    private static final int BREEDER_STATUS_SLOT = 15;
    private static final int TRADING_STATION_JOB_INPUT_SLOT = 11;
    private static final int TRADING_STATION_JOB_CONFIRM_SLOT = 15;
    private static final int TRADING_STATION_OPEN_TRADE_SLOT = 49;
    private static final int TRADING_STATION_CLEAR_SLOT = 53;
    private static final List<Integer> TRADING_STATION_LEVEL_SLOTS = List.of(10, 11, 12, 13, 14);
    private static final List<Integer> TRADING_STATION_CANDIDATE_SLOTS = List.of(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
    private static final int WATER_LIMIT = 16;
    private static final int LAVA_LIMIT = 64;
    private static final int VILLAGER_BREEDER_INTERVAL_TICKS = 5 * 60 * 20;
    private static final double DROPPER_HARDNESS = 3.5D;
    private static final double MACHINE_BREAK_REACH_SQUARED = 36.0D;
    private static final int UNDEAD_TIDE_REQUIRED_PROGRESS = 25;
    private static final int UNDEAD_TIDE_MAX_ZOMBIES = 5;
    private static final int UNDEAD_TIDE_MIN_SPAWN_TICKS = 20;
    private static final int UNDEAD_TIDE_MAX_SPAWN_TICKS = 100;
    private static final int UNDEAD_TIDE_FAILURE_TICKS = 60 * 60 * 20;
    private static final int UNDEAD_TIDE_VICTORY_DISPLAY_TICKS = 10 * 20;
    private static final int UNDEAD_TIDE_MAX_CONSECUTIVE_SPAWN_FAILURES = 3;
    private static final double UNDEAD_TIDE_BOSS_BAR_RANGE_SQUARED = 96.0D * 96.0D;
    private static final double UNDEAD_TIDE_SPAWN_MIN_RADIUS = 10.0D;
    private static final double UNDEAD_TIDE_SPAWN_MAX_RADIUS = 30.0D;
    private static final int UNDEAD_TIDE_SPAWN_MAX_Y_DELTA = 20;
    private static final double UNDEAD_TIDE_PARTICLE_MAX_RADIUS = 24.0D;
    private static final double UNDEAD_TIDE_LEASH_RADIUS_SQUARED = 20.0D * 20.0D;
    private static final double UNDEAD_TIDE_HARD_LEASH_RADIUS_SQUARED = 42.0D * 42.0D;
    private static final double UNDEAD_TIDE_LEADER_CHANCE = 0.75D;
    private static final double UNDEAD_TIDE_LEADER_REINFORCEMENT_MIN = 0.50D;
    private static final double UNDEAD_TIDE_LEADER_REINFORCEMENT_MAX = 0.75D;
    private static final double UNDEAD_TIDE_LEADER_HEALTH_BONUS_MIN = 8.0D;
    private static final double UNDEAD_TIDE_LEADER_HEALTH_BONUS_MAX = 16.0D;
    private static final List<Material> GLASS_RECIPE_MATERIALS = List.of(
            Material.GLASS,
            Material.TINTED_GLASS,
            Material.WHITE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS
    );
    private static final List<Material> BED_RECIPE_MATERIALS = materialsOf(
            "WHITE_BED",
            "ORANGE_BED",
            "MAGENTA_BED",
            "LIGHT_BLUE_BED",
            "YELLOW_BED",
            "LIME_BED",
            "PINK_BED",
            "GRAY_BED",
            "LIGHT_GRAY_BED",
            "CYAN_BED",
            "PURPLE_BED",
            "BLUE_BED",
            "BROWN_BED",
            "GREEN_BED",
            "RED_BED",
            "BLACK_BED"
    );
    private static final List<Material> VILLAGER_FOOD_RECIPE_MATERIALS = List.of(
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.WHEAT
    );
    private static final List<Material> VILLAGER_BREEDER_BASE_RECIPE_MATERIALS = List.of(
            Material.DIRT
    );
    private static final List<Material> PLANK_RECIPE_MATERIALS = materialsOf(
            "OAK_PLANKS",
            "SPRUCE_PLANKS",
            "BIRCH_PLANKS",
            "JUNGLE_PLANKS",
            "ACACIA_PLANKS",
            "DARK_OAK_PLANKS",
            "MANGROVE_PLANKS",
            "CHERRY_PLANKS",
            "BAMBOO_PLANKS",
            "CRIMSON_PLANKS",
            "WARPED_PLANKS"
    );
    private static final List<Material> FENCE_RECIPE_MATERIALS = materialsOf(
            "OAK_FENCE",
            "SPRUCE_FENCE",
            "BIRCH_FENCE",
            "JUNGLE_FENCE",
            "ACACIA_FENCE",
            "DARK_OAK_FENCE",
            "MANGROVE_FENCE",
            "CHERRY_FENCE",
            "BAMBOO_FENCE",
            "CRIMSON_FENCE",
            "WARPED_FENCE"
    );
    private static final Map<Material, Villager.Profession> WORKSTATION_PROFESSIONS = Map.ofEntries(
            Map.entry(Material.BLAST_FURNACE, Villager.Profession.ARMORER),
            Map.entry(Material.SMOKER, Villager.Profession.BUTCHER),
            Map.entry(Material.CARTOGRAPHY_TABLE, Villager.Profession.CARTOGRAPHER),
            Map.entry(Material.BREWING_STAND, Villager.Profession.CLERIC),
            Map.entry(Material.COMPOSTER, Villager.Profession.FARMER),
            Map.entry(Material.BARREL, Villager.Profession.FISHERMAN),
            Map.entry(Material.FLETCHING_TABLE, Villager.Profession.FLETCHER),
            Map.entry(Material.CAULDRON, Villager.Profession.LEATHERWORKER),
            Map.entry(Material.LECTERN, Villager.Profession.LIBRARIAN),
            Map.entry(Material.STONECUTTER, Villager.Profession.MASON),
            Map.entry(Material.LOOM, Villager.Profession.SHEPHERD),
            Map.entry(Material.SMITHING_TABLE, Villager.Profession.TOOLSMITH),
            Map.entry(Material.GRINDSTONE, Villager.Profession.WEAPONSMITH)
    );
    private static final List<BlockFace> MACHINE_FACES = List.of(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    );
    private static final List<Material> UNDEAD_TIDE_HELMETS = materialsOf(
            "LEATHER_HELMET",
            "CHAINMAIL_HELMET",
            "IRON_HELMET",
            "GOLDEN_HELMET",
            "COPPER_HELMET"
    );
    private static final List<Material> UNDEAD_TIDE_SWORDS = materialsOf(
            "WOODEN_SWORD",
            "STONE_SWORD",
            "IRON_SWORD",
            "GOLDEN_SWORD"
    );
    private static final List<Material> UNDEAD_TIDE_SPEARS = materialsOf(
            "WOODEN_SHOVEL",
            "STONE_SHOVEL",
            "IRON_SHOVEL",
            "GOLDEN_SHOVEL"
    );
    private static final List<Material> UNDEAD_TIDE_AXES = materialsOf(
            "WOODEN_AXE",
            "STONE_AXE",
            "IRON_AXE",
            "GOLDEN_AXE"
    );
    private static final Map<Villager.Profession, List<TradeSpec>> VANILLA_TRADE_CATALOG = buildVanillaTradeCatalog();

    private final Map<String, MachineState> machines = new HashMap<>();
    private final Map<String, Long> lastRedstonePulseTicks = new HashMap<>();
    private final Map<UUID, MachineBreakSession> breakSessions = new HashMap<>();
    private final Map<UUID, UndeadTideState> undeadTides = new HashMap<>();
    private final Set<UUID> failedUndeadTideMobs = new HashSet<>();

    private CustomItemService customItemService;
    private NamespacedKey generatorItemKey;
    private NamespacedKey generatorItemModelKey;
    private NamespacedKey generatorRecipeKey;
    private NamespacedKey villagerBreederItemKey;
    private NamespacedKey villagerBreederItemModelKey;
    private NamespacedKey villagerBreederRecipeKey;
    private NamespacedKey villagerEggKnowledgeKey;
    private NamespacedKey villagerTradingStationItemKey;
    private NamespacedKey villagerTradingStationItemModelKey;
    private NamespacedKey villagerTradingStationRecipeKey;
    private NamespacedKey villagerTradingStationProfessionKey;
    private NamespacedKey villagerTradingStationTradesKey;
    private NamespacedKey zombieVillagerEggRecipeKey;
    private NamespacedKey undeadDustKey;
    private NamespacedKey undeadDustItemModelKey;
    private NamespacedKey undeadDustKnowledgeKey;
    private NamespacedKey undeadCoreKey;
    private NamespacedKey undeadCoreItemModelKey;
    private NamespacedKey undeadCoreRecipeKey;
    private NamespacedKey undeadTideMobKey;
    private NamespacedKey failedUndeadTideMobKey;
    private NamespacedKey xiceRpgMonsterTypeKey;
    private NamespacedKey displayKey;
    private CustomBlockDefinition generatorBlockDefinition;
    private CustomBlockDefinition villagerBreederBlockDefinition;
    private CustomBlockDefinition villagerTradingStationBlockDefinition;
    private File machinesFile;
    private YamlConfiguration machinesConfig;
    private int outputIntervalTicks;
    private int outputTaskId = -1;
    private int hopperTaskId = -1;
    private int breakTaskId = -1;
    private int undeadTideTaskId = -1;
    private int villagerBreederTaskId = -1;
    private HudService hudService;
    private CustomBlockService customBlockService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        generatorItemKey = new NamespacedKey(this, "simple_cobblestone_generator_item");
        generatorItemModelKey = new NamespacedKey(this, "simple_cobblestone_generator");
        generatorRecipeKey = new NamespacedKey(this, "simple_cobblestone_generator");
        villagerBreederItemKey = new NamespacedKey(this, "simple_villager_breeder_item");
        villagerBreederItemModelKey = NamespacedKey.minecraft("composter");
        villagerBreederRecipeKey = new NamespacedKey(this, "simple_villager_breeder");
        villagerEggKnowledgeKey = new NamespacedKey(this, "knows_villager_spawn_egg");
        villagerTradingStationItemKey = new NamespacedKey(this, "simple_villager_trading_station_item");
        villagerTradingStationItemModelKey = NamespacedKey.minecraft("lectern");
        villagerTradingStationRecipeKey = new NamespacedKey(this, "simple_villager_trading_station");
        villagerTradingStationProfessionKey = new NamespacedKey(this, "simple_villager_trading_station_profession");
        villagerTradingStationTradesKey = new NamespacedKey(this, "simple_villager_trading_station_trades");
        zombieVillagerEggRecipeKey = new NamespacedKey(this, "zombie_villager_spawn_egg");
        undeadDustKey = new NamespacedKey(this, "undead_dust");
        undeadDustItemModelKey = new NamespacedKey(this, "undead_dust");
        undeadDustKnowledgeKey = new NamespacedKey(this, "knows_undead_dust");
        undeadCoreKey = new NamespacedKey(this, "undead_core");
        undeadCoreItemModelKey = new NamespacedKey(this, "undead_core");
        undeadCoreRecipeKey = new NamespacedKey(this, "undead_core");
        undeadTideMobKey = new NamespacedKey(this, "undead_tide_mob");
        failedUndeadTideMobKey = new NamespacedKey(this, "failed_undead_tide_mob");
        xiceRpgMonsterTypeKey = new NamespacedKey("xicerpg", "monster_type");
        displayKey = new NamespacedKey(this, "simple_cobblestone_generator_display");
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
        hudService = Bukkit.getServicesManager().load(HudService.class);
        if (hudService == null) {
            getLogger().severe("XiceHUD service is not available.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        registerCustomItems();
        registerCustomBlocks();
        outputIntervalTicks = Math.max(1, getConfig().getInt("simple-cobblestone-generator.output-interval-ticks", 30));

        machinesFile = new File(getDataFolder(), "machines.yml");
        loadMachines();

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("simpleindustry")).setExecutor(this);
        Objects.requireNonNull(getCommand("simpleindustry")).setTabCompleter(this);
        registerGeneratorRecipe();
        registerVillagerBreederRecipe();
        registerVillagerTradingStationRecipe();
        registerZombieVillagerEggRecipe();
        registerUndeadCoreRecipe();
        customItemService.allowCustomIngredientRecipe(zombieVillagerEggRecipeKey);

        refreshLoadedMachines();
        outputTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickMachines, outputIntervalTicks, outputIntervalTicks).getTaskId();
        hopperTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickHoppers, 8L, 8L).getTaskId();
        breakTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickMachineBreaking, 1L, 1L).getTaskId();
        undeadTideTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickUndeadTides, 1L, 1L).getTaskId();
        villagerBreederTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickVillagerBreeders, 20L, 20L).getTaskId();
        getLogger().info("XiceSimpleIndustry enabled. Machines loaded: " + machines.size());
    }

    @Override
    public void onDisable() {
        if (outputTaskId != -1) {
            Bukkit.getScheduler().cancelTask(outputTaskId);
        }
        if (hopperTaskId != -1) {
            Bukkit.getScheduler().cancelTask(hopperTaskId);
        }
        if (breakTaskId != -1) {
            Bukkit.getScheduler().cancelTask(breakTaskId);
        }
        if (undeadTideTaskId != -1) {
            Bukkit.getScheduler().cancelTask(undeadTideTaskId);
        }
        if (villagerBreederTaskId != -1) {
            Bukkit.getScheduler().cancelTask(villagerBreederTaskId);
        }
        for (UndeadTideState tide : new ArrayList<>(undeadTides.values())) {
            endUndeadTide(tide, false);
        }
        if (customItemService != null) {
            customItemService.unregisterRecipe(generatorRecipeKey);
            customItemService.unregisterRecipe(villagerBreederRecipeKey);
            customItemService.unregisterRecipe(villagerTradingStationRecipeKey);
            customItemService.unregisterRecipe(zombieVillagerEggRecipeKey);
            customItemService.unregisterRecipe(undeadCoreRecipeKey);
        } else {
            Bukkit.removeRecipe(generatorRecipeKey);
            Bukkit.removeRecipe(villagerBreederRecipeKey);
            Bukkit.removeRecipe(villagerTradingStationRecipeKey);
            Bukkit.removeRecipe(zombieVillagerEggRecipeKey);
            Bukkit.removeRecipe(undeadCoreRecipeKey);
        }
        if (customBlockService != null) {
            customBlockService.unregisterBlock(generatorItemKey);
            customBlockService.unregisterBlock(villagerBreederItemKey);
            customBlockService.unregisterBlock(villagerTradingStationItemKey);
        }
        saveMachines();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("用法: /" + label + " reload");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xicesimpleindustry.admin")) {
                sender.sendMessage("你没有权限使用该命令。");
                return true;
            }
            reloadConfig();
            outputIntervalTicks = Math.max(1, getConfig().getInt("simple-cobblestone-generator.output-interval-ticks", 30));
            sender.sendMessage("XiceSimpleIndustry 配置已重载。输出周期会在下次重启插件后完全应用。");
            return true;
        }
        sender.sendMessage("未知子命令。用法: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        MachineType type = machineTypeForItem(event.getItemInHand());
        if (type == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        String key = blockKey(block);
        BlockFace front = customBlockService.frontFor(event.getPlayer());
        block.setType(MACHINE_CARRIER, false);

        MachineState state = new MachineState(type, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), front);
        restoreMachineDataFromItem(state, event.getItemInHand());
        machines.put(key, state);
        saveMachines();
        refreshMachineDisplay(state);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        MachineState state = machines.get(blockKey(event.getBlock()));
        if (state == null) {
            return;
        }
        event.setDropItems(false);
        breakMachine(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE || !machines.containsKey(blockKey(event.getBlock()))) {
            return;
        }
        event.setCancelled(true);
        startMachineBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        stopMachineBreak(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopMachineBreak(event.getPlayer());
    }

    private void breakMachine(Block block, boolean dropMachineItem) {
        MachineState state = machines.remove(blockKey(block));
        if (state == null) {
            return;
        }
        clearBreakSessionsFor(block);
        removeMachineDisplays(block, state);
        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
        if (dropMachineItem) {
            block.getWorld().dropItemNaturally(dropLocation, createMachineDropItem(state));
        }
        if (!isAir(state.input)) {
            block.getWorld().dropItemNaturally(dropLocation, state.input.clone());
        }
        if (!isAir(state.breederOutput)) {
            block.getWorld().dropItemNaturally(dropLocation, state.breederOutput.clone());
        }
        block.setType(Material.AIR, false);
        saveMachines();
    }

    private void startMachineBreak(Player player, Block block) {
        String key = blockKey(block);
        MachineBreakSession existing = breakSessions.get(player.getUniqueId());
        if (existing != null && existing.blockKey.equals(key)) {
            return;
        }
        stopMachineBreak(player);
        breakSessions.put(player.getUniqueId(), new MachineBreakSession(key, block.getLocation()));
    }

    private void stopMachineBreak(Player player) {
        MachineBreakSession session = breakSessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendBlockDamage(session.location, 0.0f);
        }
    }

    private void clearBreakSessionsFor(Block block) {
        String key = blockKey(block);
        for (Player player : Bukkit.getOnlinePlayers()) {
            MachineBreakSession session = breakSessions.get(player.getUniqueId());
            if (session != null && session.blockKey.equals(key)) {
                stopMachineBreak(player);
            }
        }
    }

    private void tickMachineBreaking() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            MachineBreakSession session = breakSessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }
            Block block = session.location.getBlock();
            Block target = player.getTargetBlockExact(6);
            if (!machines.containsKey(session.blockKey)
                    || block.getType() != MACHINE_CARRIER
                    || player.getGameMode() != GameMode.SURVIVAL
                    || !player.getWorld().equals(block.getWorld())
                    || target == null
                    || !target.equals(block)
                    || player.getEyeLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) > MACHINE_BREAK_REACH_SQUARED) {
                stopMachineBreak(player);
                continue;
            }
            MachineState state = machines.get(session.blockKey);
            session.progress += customBlockService.blockBreakProgressPerTick(player, blockDefinitionFor(state.type));
            player.sendBlockDamage(session.location, (float) Math.min(1.0D, session.progress));
            if (session.progress >= 1.0D) {
                stopMachineBreak(player);
                breakMachine(block, true);
            }
        }
    }

    private double dropperBreakProgressPerTick(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPickaxe(tool)) {
            return 1.0D / DROPPER_HARDNESS / 100.0D;
        }
        double speed = pickaxeSpeed(tool.getType());
        int efficiency = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiency > 0) {
            speed += efficiency * efficiency + 1.0D;
        }
        return speed / DROPPER_HARDNESS / 30.0D;
    }

    private boolean isPickaxe(ItemStack item) {
        return item != null && switch (item.getType()) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE, GOLDEN_PICKAXE -> true;
            default -> false;
        };
    }

    private double pickaxeSpeed(Material material) {
        return switch (material) {
            case WOODEN_PICKAXE -> 2.0D;
            case STONE_PICKAXE -> 4.0D;
            case IRON_PICKAXE -> 6.0D;
            case DIAMOND_PICKAXE -> 8.0D;
            case NETHERITE_PICKAXE -> 9.0D;
            case GOLDEN_PICKAXE -> 12.0D;
            default -> 1.0D;
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.SPAWNER
                && event.getPlayer().getGameMode() != GameMode.CREATIVE
                && isSpawnEgg(event.getItem())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("非创造模式无法用刷怪蛋修改刷怪笼。");
            return;
        }
        if (event.getHand() != null
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isUndeadCore(event.getItem())) {
            event.setCancelled(true);
            if (!canStartClaimEvent(event.getPlayer(), event.getPlayer().getLocation())) {
                return;
            }
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                consumeHandItem(event.getPlayer(), event.getHand());
            }
            startUndeadTide(event.getPlayer().getLocation());
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        MachineState state = machines.get(blockKey(event.getClickedBlock()));
        if (state == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
            event.setCancelled(true);
            startMachineBreak(event.getPlayer(), event.getClickedBlock());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            Bukkit.getScheduler().runTask(this, () -> openMachineMenu(event.getPlayer(), state));
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (event.getOldCurrent() <= 0 && event.getNewCurrent() > 0) {
            triggerPulsedMachinesNear(event.getBlock());
            Bukkit.getScheduler().runTask(this, () -> triggerPulsedMachinesNear(event.getBlock()));
        }
        Bukkit.getScheduler().runTask(this, () -> refreshOpenMenusNear(event.getBlock()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> {
            unlockGeneratorRecipeIfHasRedstone(event.getPlayer());
            unlockVillagerBreederRecipeIfKnowsEgg(event.getPlayer());
            unlockUndeadCoreRecipeIfKnowsDust(event.getPlayer());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.REDSTONE) {
            Bukkit.getScheduler().runTask(this, () -> unlockGeneratorRecipeIfHasRedstone(player));
        }
        if (item.getType() == Material.VILLAGER_SPAWN_EGG) {
            Bukkit.getScheduler().runTask(this, () -> rememberVillagerEggAndUnlockBreederRecipe(player));
        }
        if (isUndeadDust(item)) {
            Bukkit.getScheduler().runTask(this, () -> rememberUndeadDustAndUnlockCoreRecipe(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        String tideId = event.getEntity().getPersistentDataContainer().get(undeadTideMobKey, PersistentDataType.STRING);
        if (tideId != null) {
            event.getDrops().clear();
            event.setDroppedExp(event.getDroppedExp() * 5);
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                recordUndeadTideMobContributor(event.getEntity().getUniqueId(), tideId, killer.getUniqueId());
            }
            handleUndeadTideMobDeath(event.getEntity().getUniqueId(), tideId);
            return;
        }
        EntityType type = event.getEntityType();
        if (type != EntityType.ZOMBIE && type != EntityType.ZOMBIE_VILLAGER) {
            return;
        }
        if ("rotten_guard".equals(event.getEntity().getPersistentDataContainer().get(xiceRpgMonsterTypeKey, PersistentDataType.STRING))) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        int looting = Math.max(0, killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING));
        double chance = 0.20D + looting * 0.05D;
        if (type == EntityType.ZOMBIE_VILLAGER) {
            chance += 0.20D;
        }
        if (event.getEntity() instanceof Zombie zombie && zombie.isBaby()) {
            chance += 0.20D;
        }
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            event.getDrops().add(createUndeadDust(1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        String tideId = event.getEntity().getPersistentDataContainer().get(undeadTideMobKey, PersistentDataType.STRING);
        if (tideId == null) {
            return;
        }
        Player player = damagingPlayer(event.getDamager());
        if (player != null) {
            recordUndeadTideMobContributor(event.getEntity().getUniqueId(), tideId, player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        String tideId = event.getEntity().getPersistentDataContainer().get(undeadTideMobKey, PersistentDataType.STRING);
        if (tideId == null || event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            event.setCancelled(true);
            removeUndeadTideMobWithoutKill(event.getEntity().getUniqueId(), tideId, event.getEntity());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (craftingMatrixContainsBlockedCustomItem(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (craftingMatrixContainsBlockedCustomItem(event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && clickMayGrantRedstone(event)) {
            Bukkit.getScheduler().runTask(this, () -> unlockGeneratorRecipeIfHasRedstone(player));
        }
        if (event.getWhoClicked() instanceof Player player && clickMayGrantVillagerSpawnEgg(event)) {
            Bukkit.getScheduler().runTask(this, () -> rememberVillagerEggAndUnlockBreederRecipe(player));
        }
        if (event.getWhoClicked() instanceof Player player && clickMayGrantUndeadDust(event)) {
            Bukkit.getScheduler().runTask(this, () -> rememberUndeadDustAndUnlockCoreRecipe(player));
        }
        if (event.getView().getTopInventory().getHolder() instanceof TradingStationJobMenu menu) {
            handleTradingStationJobInventoryClick(event, menu);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof TradingStationTradeMenu menu) {
            handleTradingStationTradeInventoryClick(event, menu);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof BreederMenu menu) {
            handleBreederInventoryClick(event, menu);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof GeneratorMenu menu)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean topClick = rawSlot >= 0 && rawSlot < top.getSize();

        if (topClick && rawSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            processConfirm(menu, top);
            return;
        }
        if (topClick && rawSlot == INPUT_SLOT) {
            if (!isAllowedInputClick(event)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> saveInputFromMenu(menu, top));
            return;
        }
        if (topClick) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            moveAllowedItemIntoInput(top, event.getClickedInventory(), event.getSlot(), event.getCurrentItem());
            saveInputFromMenu(menu, top);
            renderMenu(menu, top);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRedstoneItem(event.getOldCursor())) {
            Bukkit.getScheduler().runTask(this, () -> unlockGeneratorRecipeIfHasRedstone(player));
        }
        if (event.getWhoClicked() instanceof Player player && isVillagerSpawnEgg(event.getOldCursor())) {
            Bukkit.getScheduler().runTask(this, () -> rememberVillagerEggAndUnlockBreederRecipe(player));
        }
        if (event.getWhoClicked() instanceof Player player && isUndeadDust(event.getOldCursor())) {
            Bukkit.getScheduler().runTask(this, () -> rememberUndeadDustAndUnlockCoreRecipe(player));
        }
        if (event.getView().getTopInventory().getHolder() instanceof TradingStationJobMenu) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize && rawSlot != TRADING_STATION_JOB_INPUT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!isAir(event.getOldCursor())
                    && (!isTradingStationWorkstation(event.getOldCursor()) || event.getOldCursor().getAmount() != 1)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof TradingStationTradeMenu) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof BreederMenu) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof GeneratorMenu menu)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                touchesTop = true;
                if (rawSlot != INPUT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (!touchesTop) {
            return;
        }
        if (!isAir(event.getOldCursor()) && !isAllowedInput(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> saveInputFromMenu(menu, event.getView().getTopInventory()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GeneratorMenu menu) {
            saveInputFromMenu(menu, event.getInventory());
        }
        if (event.getInventory().getHolder() instanceof BreederMenu menu) {
            saveBreederOutputFromMenu(menu, event.getInventory());
        }
        if (event.getInventory().getHolder() instanceof TradingStationJobMenu menu) {
            returnTradingStationJobInput(menu, event.getInventory(), event.getPlayer() instanceof Player player ? player : null);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        refreshMachineDisplays(event.getChunk());
        cleanupOrphanMachineDisplays(event.getChunk());
        cleanupFailedUndeadTideMobs(event.getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeExplodedMachines(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeExplodedMachines(event.blockList());
    }

    private void removeExplodedMachines(List<Block> blocks) {
        boolean changed = false;
        for (Block block : blocks) {
            MachineState state = machines.remove(blockKey(block));
            if (state != null) {
                removeMachineDisplays(block, state);
                if (!isAir(state.input)) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), state.input.clone());
                }
                if (!isAir(state.breederOutput)) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), state.breederOutput.clone());
                }
                changed = true;
            }
        }
        if (changed) {
            saveMachines();
        }
    }

    private void openMachineMenu(Player player, MachineState state) {
        if (state.type == MachineType.VILLAGER_TRADING_STATION) {
            openTradingStationMenu(player, state);
            return;
        }
        if (state.type == MachineType.VILLAGER_BREEDER) {
            openBreederMenu(player, state);
            return;
        }
        openGeneratorMenu(player, state);
    }

    private void openGeneratorMenu(Player player, MachineState state) {
        if (!machines.containsKey(state.key())) {
            return;
        }
        GeneratorMenu menu = new GeneratorMenu(state.key());
        Inventory inventory = Bukkit.createInventory(menu, MENU_SIZE, "简易刷石机");
        menu.inventory = inventory;
        renderMenu(menu, inventory, false);
        player.openInventory(inventory);
    }

    private void openTradingStationMenu(Player player, MachineState state) {
        if (state.tradingProfession == null) {
            openTradingStationJobMenu(player, state);
            return;
        }
        openTradingStationTradeMenu(player, state, 1);
    }

    private void openTradingStationJobMenu(Player player, MachineState state) {
        if (!machines.containsKey(state.key())) {
            return;
        }
        TradingStationJobMenu menu = new TradingStationJobMenu(state.key());
        Inventory inventory = Bukkit.createInventory(menu, MENU_SIZE, "简易村民交易站 - 求职");
        menu.inventory = inventory;
        renderTradingStationJobMenu(menu, inventory);
        player.openInventory(inventory);
    }

    private void openTradingStationTradeMenu(Player player, MachineState state, int level) {
        if (!machines.containsKey(state.key())) {
            return;
        }
        TradingStationTradeMenu menu = new TradingStationTradeMenu(state.key(), clamp(level, 1, 5));
        Inventory inventory = Bukkit.createInventory(menu, 54, "简易村民交易站 - 交易配置");
        menu.inventory = inventory;
        renderTradingStationTradeMenu(menu, inventory);
        player.openInventory(inventory);
    }

    private void renderTradingStationJobMenu(TradingStationJobMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            inventory.clear();
            return;
        }
        ItemStack input = inventory.getItem(TRADING_STATION_JOB_INPUT_SLOT);
        inventory.clear();
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(4, menuItem(Material.LECTERN, "简易村民交易站", List.of("放入 1 个村民工作方块来决定职业。")));
        inventory.setItem(TRADING_STATION_JOB_INPUT_SLOT, sanitizeTradingStationJobInput(input));
        inventory.setItem(TRADING_STATION_JOB_CONFIRM_SLOT, menuItem(Material.LIME_CONCRETE, "确认职业", List.of("职业确定后会随方块掉落保存。")));
    }

    private void renderTradingStationTradeMenu(TradingStationTradeMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null || state.tradingProfession == null) {
            inventory.clear();
            return;
        }
        inventory.clear();
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(4, menuItem(Material.LECTERN, professionDisplayName(state.tradingProfession), List.of("选择原版可能出现的交易项。")));
        for (int i = 0; i < TRADING_STATION_LEVEL_SLOTS.size(); i++) {
            int level = i + 1;
            inventory.setItem(TRADING_STATION_LEVEL_SLOTS.get(i), menuItem(
                    level == menu.level ? Material.EMERALD_BLOCK : Material.EMERALD,
                    villagerLevelName(level),
                    List.of("点击查看该等级交易。")));
        }
        List<TradeSpec> candidates = tradeCandidates(state.tradingProfession, menu.level);
        for (int i = 0; i < TRADING_STATION_CANDIDATE_SLOTS.size() && i < candidates.size(); i++) {
            TradeSpec trade = candidates.get(i);
            boolean selected = state.selectedTradeIds.contains(trade.id());
            inventory.setItem(TRADING_STATION_CANDIDATE_SLOTS.get(i), tradeMenuItem(trade, selected));
        }
        inventory.setItem(TRADING_STATION_OPEN_TRADE_SLOT, menuItem(Material.EMERALD, "打开交易", List.of("使用已选交易创建临时商人界面。")));
        inventory.setItem(TRADING_STATION_CLEAR_SLOT, menuItem(Material.BARRIER, "清空当前等级", List.of("只清空当前等级已选交易。")));
    }

    private void openBreederMenu(Player player, MachineState state) {
        if (!machines.containsKey(state.key())) {
            return;
        }
        BreederMenu menu = new BreederMenu(state.key());
        Inventory inventory = Bukkit.createInventory(menu, MENU_SIZE, "简易村民繁殖机");
        menu.inventory = inventory;
        renderBreederMenu(menu, inventory);
        player.openInventory(inventory);
    }

    private void renderBreederMenu(BreederMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            inventory.clear();
            return;
        }
        ItemStack output = isAir(inventory.getItem(BREEDER_OUTPUT_SLOT))
                ? (state.breederOutput == null ? null : state.breederOutput.clone())
                : inventory.getItem(BREEDER_OUTPUT_SLOT);
        inventory.clear();
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(4, menuItem(Material.COMPOSTER, "简易村民繁殖机", List.of(
                "每 5 分钟准备 1 个村民刷怪蛋。",
                "关机时计时会继续累积到上限。"
        )));
        inventory.setItem(BREEDER_SWITCH_SLOT, menuItem(
                state.breederEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                state.breederEnabled ? "开关: 开机" : "开关: 关机",
                List.of("点击切换机器开关。")));
        inventory.setItem(BREEDER_STATUS_SLOT, breederStatusItem(state));
        inventory.setItem(BREEDER_OUTPUT_SLOT, output);
    }

    private ItemStack breederStatusItem(MachineState state) {
        int remainingTicks = Math.max(0, VILLAGER_BREEDER_INTERVAL_TICKS - state.breederTicks);
        int remainingSeconds = (remainingTicks + 19) / 20;
        if (state.breederEnabled) {
            return menuItem(Material.LIME_DYE, "状态: 开机", List.of(
                    "计时: " + state.breederTicks / 20 + " / " + VILLAGER_BREEDER_INTERVAL_TICKS / 20 + " 秒",
                    remainingSeconds <= 0 ? "已准备输出。" : "距离下次输出: " + remainingSeconds + " 秒"));
        }
        return menuItem(Material.GRAY_DYE, "状态: 关机", List.of(
                "计时: " + state.breederTicks / 20 + " / " + VILLAGER_BREEDER_INTERVAL_TICKS / 20 + " 秒",
                remainingSeconds <= 0 ? "下次开机会立刻尝试输出。" : "计时仍会继续到上限。"));
    }

    private void renderMenu(GeneratorMenu menu, Inventory inventory) {
        renderMenu(menu, inventory, true);
    }

    private void renderMenu(GeneratorMenu menu, Inventory inventory, boolean preserveInputSlot) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            inventory.clear();
            return;
        }
        ItemStack input = preserveInputSlot ? inventory.getItem(INPUT_SLOT) : null;
        if (input == null) {
            input = state.input == null ? null : state.input.clone();
        }

        inventory.clear();
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        Block block = blockFor(state);
        boolean redstonePowered = block != null && isRedstonePowered(block);
        inventory.setItem(4, menuItem(Material.OAK_SIGN, "简易刷石机", List.of(
                "正面: " + displayFace(state.front),
                "未被红石充能时每 " + outputIntervalTicks + " tick 吐出圆石"
        )));
        inventory.setItem(10, indicatorItem(Material.WATER_BUCKET, "水量", state.water, WATER_LIMIT));
        inventory.setItem(14, indicatorItem(Material.LAVA_BUCKET, "熔岩", state.lava, LAVA_LIMIT));
        inventory.setItem(15, indicatorItem(Material.COBBLESTONE, "效率", state.efficiency(), Math.max(0, state.water * 4)));
        inventory.setItem(16, statusItem(state, block, redstonePowered));
        inventory.setItem(CONFIRM_SLOT, menuItem(Material.LIME_CONCRETE, "确认注入", List.of(
                "水桶: 水量 +1，并变为空桶",
                "熔岩桶: 熔岩 +1，并变为空桶",
                "空桶: 无反应"
        )));
        inventory.setItem(INPUT_SLOT, sanitizeInput(input));
    }

    private ItemStack indicatorItem(Material material, String name, int value, int max) {
        return menuItem(material, name + ": " + value + "/" + max, List.of(progressBar(value, Math.max(1, max))));
    }

    private ItemStack statusItem(MachineState state, Block block, boolean redstonePowered) {
        if (isRecentPulse(state, block)) {
            return menuItem(Material.REDSTONE, "状态: 收到红石脉冲", List.of("已尝试执行一次确认注入。"));
        }
        if (redstonePowered) {
            return menuItem(Material.REDSTONE_TORCH, "状态: 红石暂停", List.of("当前接收到红石信号，自动产出暂停。"));
        }
        return menuItem(Material.LIME_DYE, "状态: 工作中", List.of("当前未接收到红石信号，自动产出开启。"));
    }

    private boolean isRecentPulse(MachineState state, Block block) {
        if (block == null) {
            return false;
        }
        Long lastTick = lastRedstonePulseTicks.get(state.key());
        return lastTick != null && block.getWorld().getGameTime() - lastTick <= 20;
    }

    private String progressBar(int value, int max) {
        int filled = Math.min(10, Math.max(0, (int) Math.round(value * 10.0 / max)));
        return "■".repeat(filled) + "□".repeat(10 - filled);
    }

    private void processConfirm(GeneratorMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            return;
        }
        state.input = sanitizeInput(inventory.getItem(INPUT_SLOT));
        injectInput(state);
        inventory.setItem(INPUT_SLOT, state.input == null ? null : state.input.clone());
        renderMenu(menu, inventory, false);
    }

    private void handleBreederInventoryClick(InventoryClickEvent event, BreederMenu menu) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean topClick = rawSlot >= 0 && rawSlot < top.getSize();
        if (topClick && rawSlot == BREEDER_SWITCH_SLOT) {
            event.setCancelled(true);
            toggleBreeder(menu, top);
            return;
        }
        if (topClick && rawSlot == BREEDER_OUTPUT_SLOT) {
            if (event.getClick().isKeyboardClick()) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                Bukkit.getScheduler().runTask(this, () -> saveBreederOutputFromMenu(menu, top));
                return;
            }
            if (!isAir(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> saveBreederOutputFromMenu(menu, top));
            return;
        }
        if (topClick) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    private void handleTradingStationJobInventoryClick(InventoryClickEvent event, TradingStationJobMenu menu) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean topClick = rawSlot >= 0 && rawSlot < top.getSize();
        if (topClick && rawSlot == TRADING_STATION_JOB_CONFIRM_SLOT) {
            event.setCancelled(true);
            confirmTradingStationProfession(event, menu, top);
            return;
        }
        if (topClick && rawSlot == TRADING_STATION_JOB_INPUT_SLOT) {
            if (!isTradingStationJobInputClick(event)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> renderTradingStationJobMenu(menu, top));
            return;
        }
        if (topClick) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            moveWorkstationIntoTradingStationJobInput(top, event.getClickedInventory(), event.getSlot(), event.getCurrentItem());
            renderTradingStationJobMenu(menu, top);
        }
    }

    private void handleTradingStationTradeInventoryClick(InventoryClickEvent event, TradingStationTradeMenu menu) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean topClick = rawSlot >= 0 && rawSlot < top.getSize();
        if (!topClick) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        MachineState state = machines.get(menu.machineKey);
        if (state == null || state.tradingProfession == null) {
            return;
        }
        int levelIndex = TRADING_STATION_LEVEL_SLOTS.indexOf(rawSlot);
        if (levelIndex >= 0) {
            openTradingStationTradeMenu((Player) event.getWhoClicked(), state, levelIndex + 1);
            return;
        }
        if (rawSlot == TRADING_STATION_OPEN_TRADE_SLOT) {
            openTradingStationMerchant((Player) event.getWhoClicked(), state);
            return;
        }
        if (rawSlot == TRADING_STATION_CLEAR_SLOT) {
            clearTradesForLevel(state, menu.level);
            saveMachines();
            renderTradingStationTradeMenu(menu, top);
            return;
        }
        int candidateIndex = TRADING_STATION_CANDIDATE_SLOTS.indexOf(rawSlot);
        if (candidateIndex < 0) {
            return;
        }
        List<TradeSpec> candidates = tradeCandidates(state.tradingProfession, menu.level);
        if (candidateIndex >= candidates.size()) {
            return;
        }
        TradeSpec trade = candidates.get(candidateIndex);
        if (!state.selectedTradeIds.add(trade.id())) {
            state.selectedTradeIds.remove(trade.id());
        }
        saveMachines();
        renderTradingStationTradeMenu(menu, top);
    }

    private void confirmTradingStationProfession(InventoryClickEvent event, TradingStationJobMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            return;
        }
        ItemStack input = sanitizeTradingStationJobInput(inventory.getItem(TRADING_STATION_JOB_INPUT_SLOT));
        if (isAir(input)) {
            event.getWhoClicked().sendMessage("请先放入一个村民工作方块。");
            return;
        }
        Villager.Profession profession = WORKSTATION_PROFESSIONS.get(input.getType());
        if (profession == null) {
            event.getWhoClicked().sendMessage("该方块不能决定村民职业。");
            return;
        }
        state.tradingProfession = profession;
        state.selectedTradeIds.clear();
        saveMachines();
        inventory.setItem(TRADING_STATION_JOB_INPUT_SLOT, null);
        openTradingStationTradeMenu((Player) event.getWhoClicked(), state, 1);
    }

    private boolean isTradingStationJobInputClick(InventoryClickEvent event) {
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            return isAir(hotbar) || (isTradingStationWorkstation(hotbar) && hotbar.getAmount() == 1);
        }
        ItemStack cursor = event.getCursor();
        return isAir(cursor) || (isTradingStationWorkstation(cursor) && cursor.getAmount() == 1);
    }

    private void moveWorkstationIntoTradingStationJobInput(Inventory top, Inventory sourceInventory, int sourceSlot, ItemStack source) {
        if (sourceInventory == null || isAir(source) || !isTradingStationWorkstation(source) || !isAir(top.getItem(TRADING_STATION_JOB_INPUT_SLOT))) {
            return;
        }
        ItemStack moved = source.clone();
        moved.setAmount(1);
        top.setItem(TRADING_STATION_JOB_INPUT_SLOT, moved);
        source.setAmount(source.getAmount() - 1);
        sourceInventory.setItem(sourceSlot, source.getAmount() <= 0 ? null : source);
    }

    private void returnTradingStationJobInput(TradingStationJobMenu menu, Inventory inventory, Player player) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null || state.tradingProfession != null) {
            return;
        }
        ItemStack input = sanitizeTradingStationJobInput(inventory.getItem(TRADING_STATION_JOB_INPUT_SLOT));
        if (isAir(input)) {
            return;
        }
        inventory.setItem(TRADING_STATION_JOB_INPUT_SLOT, null);
        Map<Integer, ItemStack> leftovers = player == null ? Map.of(0, input) : player.getInventory().addItem(input);
        if (!leftovers.isEmpty()) {
            Block block = blockFor(state);
            Location drop = block == null ? state.centerLocation() : block.getLocation().add(0.5D, 0.5D, 0.5D);
            if (drop == null || drop.getWorld() == null) {
                return;
            }
            for (ItemStack leftover : leftovers.values()) {
                drop.getWorld().dropItemNaturally(drop, leftover);
            }
        }
    }

    private void toggleBreeder(BreederMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            return;
        }
        saveBreederOutputFromMenu(menu, inventory);
        state.breederEnabled = !state.breederEnabled;
        if (state.breederEnabled && state.breederTicks >= VILLAGER_BREEDER_INTERVAL_TICKS) {
            completeBreederCycle(state);
        }
        saveMachines();
        renderBreederMenu(menu, inventory);
    }

    private void saveInputFromMenu(GeneratorMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            return;
        }
        state.input = sanitizeInput(inventory.getItem(INPUT_SLOT));
        saveMachines();
    }

    private void saveBreederOutputFromMenu(BreederMenu menu, Inventory inventory) {
        MachineState state = machines.get(menu.machineKey);
        if (state == null) {
            return;
        }
        ItemStack output = inventory.getItem(BREEDER_OUTPUT_SLOT);
        state.breederOutput = isVillagerSpawnEgg(output) ? output.clone() : null;
        saveMachines();
    }

    private boolean isAllowedInputClick(InventoryClickEvent event) {
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            return isAir(hotbar) || isAllowedInput(hotbar);
        }
        ItemStack cursor = event.getCursor();
        return isAir(cursor) || isAllowedInput(cursor);
    }

    private void moveAllowedItemIntoInput(Inventory top, Inventory sourceInventory, int sourceSlot, ItemStack source) {
        if (sourceInventory == null || isAir(source) || !isAllowedInput(source)) {
            return;
        }
        ItemStack input = top.getItem(INPUT_SLOT);
        if (!isAir(input) && input.getType() != source.getType()) {
            return;
        }
        int maxStack = source.getMaxStackSize();
        int currentAmount = isAir(input) ? 0 : input.getAmount();
        int movable = Math.min(source.getAmount(), maxStack - currentAmount);
        if (movable <= 0) {
            return;
        }
        ItemStack moved = source.clone();
        moved.setAmount(currentAmount + movable);
        top.setItem(INPUT_SLOT, moved);
        source.setAmount(source.getAmount() - movable);
        sourceInventory.setItem(sourceSlot, source.getAmount() <= 0 ? null : source);
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

    private ItemStack sanitizeInput(ItemStack item) {
        if (isAir(item) || !isAllowedInput(item)) {
            return null;
        }
        return item.clone();
    }

    private boolean isAllowedInput(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType() == Material.WATER_BUCKET
                || item.getType() == Material.LAVA_BUCKET
                || item.getType() == Material.BUCKET;
    }

    private boolean injectInput(MachineState state) {
        ItemStack input = sanitizeInput(state.input);
        if (input == null) {
            state.input = null;
            return false;
        }
        boolean changed = false;
        Material injectedMaterial = input.getType();
        if (input.getType() == Material.WATER_BUCKET && state.water < WATER_LIMIT) {
            state.water += 1;
            input = new ItemStack(Material.BUCKET);
            changed = true;
        } else if (input.getType() == Material.LAVA_BUCKET && state.lava < LAVA_LIMIT) {
            state.lava += 1;
            input = new ItemStack(Material.BUCKET);
            changed = true;
        }
        state.input = input.clone();
        if (changed) {
            playInjectionSound(state, injectedMaterial);
            saveMachines();
        }
        return changed;
    }

    private void playInjectionSound(MachineState state, Material injectedMaterial) {
        Block block = blockFor(state);
        if (block == null) {
            return;
        }
        String sound = injectedMaterial == Material.LAVA_BUCKET
                ? "minecraft:item.bucket.empty_lava"
                : "minecraft:item.bucket.empty";
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 0.9f, 1.0f);
    }

    private void tickMachines() {
        for (MachineState state : new ArrayList<>(machines.values())) {
            if (state.type != MachineType.GENERATOR) {
                continue;
            }
            World world = Bukkit.getWorld(state.world);
            if (world == null || !world.isChunkLoaded(state.x >> 4, state.z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(state.x, state.y, state.z);
            if (block.getType() != MACHINE_CARRIER) {
                continue;
            }
            int amount = state.efficiency();
            if (amount <= 0 || isRedstonePowered(block)) {
                continue;
            }
            spitCobblestone(block, state.front, amount);
        }
    }

    private void tickVillagerBreeders() {
        boolean changed = false;
        for (MachineState state : new ArrayList<>(machines.values())) {
            if (state.type != MachineType.VILLAGER_BREEDER) {
                continue;
            }
            Block block = blockFor(state);
            if (block == null || block.getType() != MACHINE_CARRIER) {
                continue;
            }
            if (state.breederTicks < VILLAGER_BREEDER_INTERVAL_TICKS) {
                state.breederTicks = Math.min(VILLAGER_BREEDER_INTERVAL_TICKS, state.breederTicks + 20);
                changed = true;
            }
            if (state.breederEnabled && state.breederTicks >= VILLAGER_BREEDER_INTERVAL_TICKS) {
                completeBreederCycle(state);
                changed = true;
            }
            refreshOpenMenus(state);
        }
        if (changed) {
            saveMachines();
        }
    }

    private void completeBreederCycle(MachineState state) {
        if (isAir(state.breederOutput)) {
            state.breederOutput = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        } else if (state.breederOutput.getType() == Material.VILLAGER_SPAWN_EGG
                && state.breederOutput.getAmount() < state.breederOutput.getMaxStackSize()) {
            state.breederOutput.setAmount(state.breederOutput.getAmount() + 1);
        }
        state.breederTicks = 0;
    }

    private void tickHoppers() {
        boolean changed = false;
        for (MachineState state : new ArrayList<>(machines.values())) {
            if (state.type != MachineType.GENERATOR) {
                continue;
            }
            Block block = blockFor(state);
            if (block == null || block.getType() != MACHINE_CARRIER) {
                continue;
            }
            boolean machineChanged = pullFromHoppersIntoMachine(block, state);
            machineChanged |= pushFromMachineIntoHopper(block, state);
            if (machineChanged) {
                refreshOpenMenus(state);
            }
            changed |= machineChanged;
        }
        if (changed) {
            saveMachines();
        }
    }

    private boolean pullFromHoppersIntoMachine(Block block, MachineState state) {
        for (BlockFace face : MACHINE_FACES) {
            Block hopperBlock = block.getRelative(face);
            if (!isHopperPushingInto(hopperBlock, block) || !(hopperBlock.getState() instanceof Hopper hopper)) {
                continue;
            }
            Inventory inventory = hopper.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack source = inventory.getItem(slot);
                if (moveOneItemIntoMachineInput(state, source)) {
                    decrementInventorySlot(inventory, slot, source);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pushFromMachineIntoHopper(Block block, MachineState state) {
        Block hopperBlock = block.getRelative(BlockFace.DOWN);
        if (hopperBlock.getType() != Material.HOPPER || !(hopperBlock.getState() instanceof Hopper hopper)) {
            return false;
        }
        ItemStack input = sanitizeInput(state.input);
        if (input == null) {
            state.input = null;
            return false;
        }
        ItemStack single = input.clone();
        single.setAmount(1);
        if (!hopper.getInventory().addItem(single).isEmpty()) {
            return false;
        }
        input.setAmount(input.getAmount() - 1);
        state.input = input.getAmount() <= 0 ? null : input;
        return true;
    }

    private boolean moveOneItemIntoMachineInput(MachineState state, ItemStack source) {
        if (isAir(source) || !isAllowedInput(source)) {
            return false;
        }
        ItemStack input = sanitizeInput(state.input);
        if (input != null && input.getType() != source.getType()) {
            return false;
        }
        int currentAmount = input == null ? 0 : input.getAmount();
        int maxStack = source.getMaxStackSize();
        if (currentAmount >= maxStack) {
            return false;
        }
        ItemStack updated = source.clone();
        updated.setAmount(currentAmount + 1);
        state.input = updated;
        return true;
    }

    private boolean isHopperPushingInto(Block hopperBlock, Block target) {
        if (hopperBlock.getType() != Material.HOPPER || !(hopperBlock.getBlockData() instanceof Directional directional)) {
            return false;
        }
        return hopperBlock.getRelative(directional.getFacing()).equals(target);
    }

    private void triggerPulsedMachinesNear(Block signalBlock) {
        for (MachineState state : new ArrayList<>(machines.values())) {
            if (state.type != MachineType.GENERATOR) {
                continue;
            }
            Block machineBlock = blockFor(state);
            if (machineBlock != null && isPulseTarget(signalBlock, machineBlock)) {
                triggerPulsedMachine(machineBlock, state);
            }
        }
    }

    private boolean isPulseTarget(Block signalBlock, Block machineBlock) {
        if (!signalBlock.getWorld().equals(machineBlock.getWorld()) || machineBlock.getType() != MACHINE_CARRIER) {
            return false;
        }
        int dx = Math.abs(signalBlock.getX() - machineBlock.getX());
        int dy = Math.abs(signalBlock.getY() - machineBlock.getY());
        int dz = Math.abs(signalBlock.getZ() - machineBlock.getZ());
        int manhattan = dx + dy + dz;
        return manhattan == 0 || manhattan == 1 || (manhattan <= 2 && isRedstonePowered(machineBlock));
    }

    private void triggerPulsedMachine(Block block, MachineState state) {
        if (state == null || block.getType() != MACHINE_CARRIER) {
            return;
        }
        long tick = block.getWorld().getGameTime();
        Long lastTick = lastRedstonePulseTicks.get(state.key());
        if (lastTick != null && tick - lastTick <= 1) {
            return;
        }
        lastRedstonePulseTicks.put(state.key(), tick);
        syncOpenMenusToState(state.key());
        injectInput(state);
        refreshOpenMenus(state);
        Bukkit.getScheduler().runTaskLater(this, () -> refreshOpenMenus(state), 20L);
    }

    private boolean isRedstonePowered(Block block) {
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    private Block blockFor(MachineState state) {
        World world = Bukkit.getWorld(state.world);
        if (world == null || !world.isChunkLoaded(state.x >> 4, state.z >> 4)) {
            return null;
        }
        return world.getBlockAt(state.x, state.y, state.z);
    }

    private void syncOpenMenusToState(String machineKey) {
        MachineState state = machines.get(machineKey);
        if (state == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory.getHolder() instanceof GeneratorMenu menu && machineKey.equals(menu.machineKey)) {
                state.input = sanitizeInput(inventory.getItem(INPUT_SLOT));
            }
            if (inventory.getHolder() instanceof BreederMenu menu && machineKey.equals(menu.machineKey)) {
                state.breederOutput = isVillagerSpawnEgg(inventory.getItem(BREEDER_OUTPUT_SLOT))
                        ? inventory.getItem(BREEDER_OUTPUT_SLOT).clone()
                        : null;
            }
            if (inventory.getHolder() instanceof TradingStationJobMenu menu && machineKey.equals(menu.machineKey) && state.tradingProfession == null) {
                returnTradingStationJobInput(menu, inventory, player);
            }
        }
    }

    private void refreshOpenMenus(MachineState state) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory.getHolder() instanceof GeneratorMenu menu && state.key().equals(menu.machineKey)) {
                inventory.setItem(INPUT_SLOT, state.input == null ? null : state.input.clone());
                renderMenu(menu, inventory, false);
            }
            if (inventory.getHolder() instanceof BreederMenu menu && state.key().equals(menu.machineKey)) {
                renderBreederMenu(menu, inventory);
            }
            if (inventory.getHolder() instanceof TradingStationTradeMenu menu && state.key().equals(menu.machineKey)) {
                renderTradingStationTradeMenu(menu, inventory);
            }
        }
    }

    private void refreshOpenMenusNear(Block signalBlock) {
        for (MachineState state : machines.values()) {
            Block machineBlock = blockFor(state);
            if (machineBlock != null && isPulseTarget(signalBlock, machineBlock)) {
                refreshOpenMenus(state);
            }
        }
    }

    private void spitCobblestone(Block block, BlockFace front, int amount) {
        ItemStack output = new ItemStack(Material.COBBLESTONE, Math.min(64, amount));
        Block outputBlock = block.getRelative(front);
        if (outputBlock.getState() instanceof InventoryHolder holder) {
            Map<Integer, ItemStack> leftover = holder.getInventory().addItem(output);
            if (leftover.isEmpty()) {
                return;
            }
            output = leftover.values().iterator().next();
        }
        spitCobblestoneItem(block, front, output);
    }

    private void spitCobblestoneItem(Block block, BlockFace front, ItemStack output) {
        Vector direction = front.getDirection();
        Location spawn = block.getLocation().add(0.5 + direction.getX() * 0.72, 0.5 + direction.getY() * 0.72, 0.5 + direction.getZ() * 0.72);
        Item item = block.getWorld().dropItem(spawn, output);
        Vector velocity = direction.clone().multiply(0.28);
        if (front.getModY() == 0) {
            velocity.setY(0.08);
        }
        item.setVelocity(velocity);
    }

    private void registerGeneratorRecipe() {
        customItemService.unregisterRecipe(generatorRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(generatorRecipeKey, createGeneratorItem(1));
        recipe.setCategory(CraftingBookCategory.REDSTONE);
        recipe.shape("GTG", "BOB", "IRI");
        recipe.setIngredient('G', new RecipeChoice.MaterialChoice(GLASS_RECIPE_MATERIALS));
        recipe.setIngredient('T', Material.TNT);
        recipe.setIngredient('B', Material.BUCKET);
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE);
        customItemService.registerRecipe(recipe);
    }

    private void registerVillagerBreederRecipe() {
        customItemService.unregisterRecipe(villagerBreederRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(villagerBreederRecipeKey, createVillagerBreederItem(1));
        recipe.setCategory(CraftingBookCategory.MISC);
        recipe.shape("BBB", "EFE", "DWD");
        recipe.setIngredient('B', new RecipeChoice.MaterialChoice(BED_RECIPE_MATERIALS));
        recipe.setIngredient('E', Material.VILLAGER_SPAWN_EGG);
        recipe.setIngredient('F', new RecipeChoice.MaterialChoice(VILLAGER_FOOD_RECIPE_MATERIALS));
        recipe.setIngredient('D', new RecipeChoice.MaterialChoice(VILLAGER_BREEDER_BASE_RECIPE_MATERIALS));
        recipe.setIngredient('W', Material.WATER_BUCKET);
        customItemService.registerRecipe(recipe);
    }

    private void registerVillagerTradingStationRecipe() {
        customItemService.unregisterRecipe(villagerTradingStationRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(villagerTradingStationRecipeKey, createVillagerTradingStationItem(1));
        recipe.setCategory(CraftingBookCategory.MISC);
        recipe.shape("PPP", "PV ", "PMF");
        recipe.setIngredient('P', new RecipeChoice.MaterialChoice(PLANK_RECIPE_MATERIALS));
        recipe.setIngredient('V', Material.VILLAGER_SPAWN_EGG);
        recipe.setIngredient('M', Material.MINECART);
        recipe.setIngredient('F', new RecipeChoice.MaterialChoice(FENCE_RECIPE_MATERIALS));
        customItemService.registerRecipe(recipe);
    }

    private void registerZombieVillagerEggRecipe() {
        customItemService.unregisterRecipe(zombieVillagerEggRecipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(
                zombieVillagerEggRecipeKey,
                new ItemStack(Material.ZOMBIE_VILLAGER_SPAWN_EGG));
        recipe.setCategory(CraftingBookCategory.MISC);
        recipe.addIngredient(Material.VILLAGER_SPAWN_EGG);
        recipe.addIngredient(new RecipeChoice.ExactChoice(createUndeadDust(1)));
        customItemService.registerRecipe(recipe);
    }

    private void registerUndeadCoreRecipe() {
        customItemService.unregisterRecipe(undeadCoreRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(undeadCoreRecipeKey, createUndeadCore(1));
        recipe.shape("RRR", "RDR", "RRR");
        recipe.setIngredient('R', Material.ROTTEN_FLESH);
        recipe.setIngredient('D', new RecipeChoice.ExactChoice(createUndeadDust(1)));
        customItemService.registerRecipe(recipe);
    }

    private boolean canStartClaimEvent(Player player, Location location) {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceClaim");
        if (plugin == null || !plugin.isEnabled()) {
            return true;
        }
        try {
            Object allowed = plugin.getClass().getMethod("canStartEvent", Player.class, Location.class).invoke(plugin, player, location);
            return !(allowed instanceof Boolean result) || result;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to check claim event permission: " + exception.getMessage());
            return true;
        }
    }

    private void consumeHandItem(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (isAir(item)) {
            return;
        }
        int remaining = item.getAmount() - 1;
        if (remaining <= 0) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        item.setAmount(remaining);
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void startUndeadTide(Location center) {
        Location eventCenter = center.clone();
        eventCenter.setX(eventCenter.getBlockX() + 0.5D);
        eventCenter.setY(eventCenter.getY());
        eventCenter.setZ(eventCenter.getBlockZ() + 0.5D);
        UndeadTideState tide = new UndeadTideState(hudService, UUID.randomUUID(), eventCenter);
        tide.nextSpawnTicks = randomUndeadTideSpawnDelay();
        undeadTides.put(tide.id, tide);
        updateUndeadTideBossBar(tide);
        eventCenter.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eventCenter.clone().add(0.0D, 1.0D, 0.0D), 30, 0.6D, 0.5D, 0.6D, 0.02D);
    }

    private void tickUndeadTides() {
        for (UndeadTideState tide : new ArrayList<>(undeadTides.values())) {
            tickUndeadTide(tide);
        }
        cleanupFailedUndeadTideMobs();
    }

    private void tickUndeadTide(UndeadTideState tide) {
        tide.ageTicks++;
        if (tide.victoryTicksRemaining > 0) {
            tickUndeadTideVictory(tide);
            return;
        }
        if (tide.ageTicks >= UNDEAD_TIDE_FAILURE_TICKS) {
            failUndeadTide(tide);
            return;
        }
        cleanupUndeadTideMobs(tide);
        guideUndeadTideMobs(tide);
        tickUndeadTideParticles(tide);
        if (tide.ageTicks % 20 == 0) {
            updateUndeadTideBossBar(tide);
        }
        if (tide.zombieIds.size() >= UNDEAD_TIDE_MAX_ZOMBIES) {
            return;
        }
        tide.nextSpawnTicks--;
        if (tide.nextSpawnTicks > 0) {
            return;
        }
        if (!spawnUndeadTideZombie(tide)) {
            tide.consecutiveSpawnFailures++;
            playUndeadTideSpawnFailure(tide.center);
            if (tide.consecutiveSpawnFailures >= UNDEAD_TIDE_MAX_CONSECUTIVE_SPAWN_FAILURES) {
                failUndeadTide(tide);
                return;
            }
        } else {
            tide.consecutiveSpawnFailures = 0;
        }
        tide.nextSpawnTicks = randomUndeadTideSpawnDelay();
    }

    private void tickUndeadTideVictory(UndeadTideState tide) {
        tide.victoryTicksRemaining--;
        tickUndeadTideParticles(tide);
        tickUndeadTideVictoryParticles(tide);
        if (tide.ageTicks % 20 == 0) {
            updateUndeadTideBossBar(tide);
        }
        if (tide.victoryTicksRemaining <= 0) {
            finishUndeadTideVictory(tide);
        }
    }

    private void cleanupUndeadTideMobs(UndeadTideState tide) {
        tide.zombieIds.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Zombie zombie) || zombie.isDead() || !zombie.isValid()) {
                tide.zombieContributors.remove(id);
                return true;
            }
            if (!zombie.getWorld().equals(tide.center.getWorld())) {
                zombie.remove();
                tide.zombieContributors.remove(id);
                return true;
            }
            return false;
        });
    }

    private void guideUndeadTideMobs(UndeadTideState tide) {
        if (tide.ageTicks % 20 != 0) {
            return;
        }
        for (UUID zombieId : tide.zombieIds) {
            Entity entity = Bukkit.getEntity(zombieId);
            if (!(entity instanceof Zombie zombie) || zombie.isDead() || !zombie.isValid()) {
                continue;
            }
            double distanceSquared = zombie.getLocation().distanceSquared(tide.center);
            if (distanceSquared > UNDEAD_TIDE_HARD_LEASH_RADIUS_SQUARED) {
                Location fallback = findUndeadTideCenterReturnLocation(tide.center);
                if (fallback != null) {
                    zombie.teleport(fallback);
                }
                zombie.setTarget(null);
                continue;
            }
            if (distanceSquared > UNDEAD_TIDE_LEASH_RADIUS_SQUARED) {
                zombie.setTarget(null);
                zombie.getPathfinder().moveTo(randomUndeadTideCenterTarget(tide.center), 1.1D);
            } else if (zombie.getTarget() instanceof Player target
                    && target.getLocation().distanceSquared(tide.center) > UNDEAD_TIDE_LEASH_RADIUS_SQUARED) {
                zombie.setTarget(null);
            }
        }
    }

    private void tickUndeadTideVictoryParticles(UndeadTideState tide) {
        World world = tide.center.getWorld();
        if (world == null) {
            return;
        }
        int elapsedTicks = UNDEAD_TIDE_VICTORY_DISPLAY_TICKS - tide.victoryTicksRemaining;
        double progress = Math.min(1.0D, elapsedTicks / (double) UNDEAD_TIDE_VICTORY_DISPLAY_TICKS);
        int count = 10 + (int) Math.round(progress * 30.0D);
        double phase = elapsedTicks * 0.22D;
        Particle.DustOptions greenDust = new Particle.DustOptions(Color.fromRGB(70, 255, 90), 1.25F);
        for (int index = 0; index < count; index++) {
            double angle = phase + index * Math.PI * 2.0D / count;
            double radius = 0.35D + progress * 2.0D + (index % 4) * 0.08D;
            double upward = 0.2D + progress * 1.2D + (index % 3) * 0.12D;
            Location origin = tide.center.clone().add(Math.cos(angle) * radius, 0.25D, Math.sin(angle) * radius);
            world.spawnParticle(Particle.WITCH, origin, 0, Math.cos(angle) * 0.18D, upward, Math.sin(angle) * 0.18D, 0.75D);
            if (index % 3 == 0) {
                Location spark = origin.clone().add(0.0D, 0.35D + progress * 0.7D, 0.0D);
                world.spawnParticle(Particle.DUST, spark, 1, 0.02D, 0.02D, 0.02D, 0.0D, greenDust);
            }
        }
    }

    private void tickUndeadTideParticles(UndeadTideState tide) {
        World world = tide.center.getWorld();
        if (world == null) {
            return;
        }
        double radius = 1.0D + (tide.ageTicks % 60) / 59.0D * UNDEAD_TIDE_PARTICLE_MAX_RADIUS;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.35F);
        int points = 20;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i / points) + tide.ageTicks * 0.04D;
            Location particle = tide.center.clone().add(Math.cos(angle) * radius, 0.15D + (i % 3) * 0.25D, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, particle, 1, 0.04D, 0.03D, 0.04D, 0.0D, dust);
        }
    }

    private void updateUndeadTideBossBar(UndeadTideState tide) {
        double progress = (double) tide.progress / UNDEAD_TIDE_REQUIRED_PROGRESS;
        tide.bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));
        if (tide.victoryTicksRemaining > 0) {
            tide.bossBar.setTitle("亡灵潮 胜利");
        } else {
            tide.bossBar.setTitle("亡灵潮 " + tide.progress + "/" + UNDEAD_TIDE_REQUIRED_PROGRESS);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean inRange = player.getWorld().equals(tide.center.getWorld())
                    && player.getLocation().distanceSquared(tide.center) <= UNDEAD_TIDE_BOSS_BAR_RANGE_SQUARED;
            if (inRange) {
                tide.bossBar.addPlayer(player);
            } else {
                tide.bossBar.removePlayer(player);
            }
        }
    }

    private boolean spawnUndeadTideZombie(UndeadTideState tide) {
        World world = tide.center.getWorld();
        if (world == null) {
            return false;
        }
        Location spawn = findUndeadTideSpawnLocation(tide.center);
        if (spawn == null) {
            return false;
        }
        Zombie zombie = world.spawn(spawn, Zombie.class, entity -> {
            entity.setAdult();
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);
            entity.setShouldBurnInDay(false);
            entity.setGlowing(true);
            entity.getPersistentDataContainer().set(undeadTideMobKey, PersistentDataType.STRING, tide.id.toString());
            equipUndeadTideZombie(entity);
            maybeApplyVanillaZombieLeader(entity);
        });
        tide.zombieIds.add(zombie.getUniqueId());
        return true;
    }

    private Location findUndeadTideSpawnLocation(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            double radius = random.nextDouble(UNDEAD_TIDE_SPAWN_MIN_RADIUS, UNDEAD_TIDE_SPAWN_MAX_RADIUS);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            double distanceSquared = Math.pow(x + 0.5D - center.getX(), 2.0D) + Math.pow(z + 0.5D - center.getZ(), 2.0D);
            if (distanceSquared < UNDEAD_TIDE_SPAWN_MIN_RADIUS * UNDEAD_TIDE_SPAWN_MIN_RADIUS
                    || distanceSquared > UNDEAD_TIDE_SPAWN_MAX_RADIUS * UNDEAD_TIDE_SPAWN_MAX_RADIUS) {
                continue;
            }
            Location candidate = findClosestSpawnY(world, x, z, center.getBlockY());
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private Location findClosestSpawnY(World world, int x, int z, int centerY) {
        for (int offset = 0; offset <= UNDEAD_TIDE_SPAWN_MAX_Y_DELTA; offset++) {
            Location upper = spawnLocationIfValid(world, x, centerY + offset, z);
            if (upper != null) {
                return upper;
            }
            if (offset > 0) {
                Location lower = spawnLocationIfValid(world, x, centerY - offset, z);
                if (lower != null) {
                    return lower;
                }
            }
        }
        return null;
    }

    private Location findUndeadTideCenterReturnLocation(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            double radius = random.nextDouble(0.0D, 5.0D);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location candidate = findClosestSpawnY(world, x, z, center.getBlockY());
            if (candidate != null && candidate.distanceSquared(center) <= 5.5D * 5.5D) {
                return candidate;
            }
        }
        return randomUndeadTideCenterTarget(center);
    }

    private Location randomUndeadTideCenterTarget(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2.0D);
        double radius = random.nextDouble(0.0D, 5.0D);
        return center.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
    }

    private Location spawnLocationIfValid(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return null;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        if (feet.isPassable() && head.isPassable() && !ground.isPassable()) {
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    private void playUndeadTideSpawnFailure(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location effectLocation = center.clone().add(0.0D, 1.0D, 0.0D);
        world.playSound(effectLocation, "minecraft:entity.generic.explode", SoundCategory.HOSTILE, 1.0F, 0.8F);
        world.spawnParticle(Particle.EXPLOSION, effectLocation, 8, 0.6D, 0.4D, 0.6D, 0.0D);
    }

    private void equipUndeadTideZombie(Zombie zombie) {
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(new ItemStack(randomFrom(UNDEAD_TIDE_HELMETS)));
        equipment.setItemInMainHand(new ItemStack(randomUndeadTideWeapon()));
        equipment.setHelmetDropChance(0.0F);
        equipment.setItemInMainHandDropChance(0.0F);
    }

    private void maybeApplyVanillaZombieLeader(Zombie zombie) {
        if (ThreadLocalRandom.current().nextDouble() >= UNDEAD_TIDE_LEADER_CHANCE) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        AttributeInstance reinforcement = zombie.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
        if (reinforcement != null) {
            reinforcement.setBaseValue(random.nextDouble(
                    UNDEAD_TIDE_LEADER_REINFORCEMENT_MIN,
                    UNDEAD_TIDE_LEADER_REINFORCEMENT_MAX));
        }
        AttributeInstance maxHealth = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double newMaxHealth = maxHealth.getBaseValue()
                    + random.nextDouble(UNDEAD_TIDE_LEADER_HEALTH_BONUS_MIN, UNDEAD_TIDE_LEADER_HEALTH_BONUS_MAX);
            maxHealth.setBaseValue(newMaxHealth);
            zombie.setHealth(newMaxHealth);
        }
    }

    private Material randomUndeadTideWeapon() {
        int type = ThreadLocalRandom.current().nextInt(3);
        if (type == 0) {
            return randomFrom(UNDEAD_TIDE_SWORDS);
        }
        if (type == 1) {
            return randomFrom(UNDEAD_TIDE_SPEARS);
        }
        return randomFrom(UNDEAD_TIDE_AXES);
    }

    private int randomUndeadTideSpawnDelay() {
        return ThreadLocalRandom.current().nextInt(UNDEAD_TIDE_MIN_SPAWN_TICKS, UNDEAD_TIDE_MAX_SPAWN_TICKS + 1);
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void recordUndeadTideMobContributor(UUID mobId, String tideId, UUID playerId) {
        UndeadTideState tide;
        try {
            tide = undeadTides.get(UUID.fromString(tideId));
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (tide == null || !tide.zombieIds.contains(mobId)) {
            return;
        }
        tide.zombieContributors.computeIfAbsent(mobId, ignored -> new HashSet<>()).add(playerId);
    }

    private void handleUndeadTideMobDeath(UUID mobId, String tideId) {
        UndeadTideState tide;
        try {
            tide = undeadTides.get(UUID.fromString(tideId));
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (tide == null) {
            return;
        }
        tide.zombieIds.remove(mobId);
        Set<UUID> contributors = tide.zombieContributors.remove(mobId);
        if (contributors != null) {
            tide.rewardPlayerIds.addAll(contributors);
        }
        tide.progress++;
        if (tide.progress >= UNDEAD_TIDE_REQUIRED_PROGRESS) {
            beginUndeadTideVictory(tide);
            return;
        }
        updateUndeadTideBossBar(tide);
    }

    private void removeUndeadTideMobWithoutKill(UUID mobId, String tideId, Entity entity) {
        try {
            UndeadTideState tide = undeadTides.get(UUID.fromString(tideId));
            if (tide != null) {
                tide.zombieIds.remove(mobId);
                tide.zombieContributors.remove(mobId);
            }
        } catch (IllegalArgumentException ignored) {
        }
        failedUndeadTideMobs.remove(mobId);
        entity.remove();
    }

    private void beginUndeadTideVictory(UndeadTideState tide) {
        tide.progress = UNDEAD_TIDE_REQUIRED_PROGRESS;
        tide.victoryTicksRemaining = UNDEAD_TIDE_VICTORY_DISPLAY_TICKS;
        for (UUID zombieId : tide.zombieIds) {
            Entity entity = Bukkit.getEntity(zombieId);
            if (entity != null) {
                entity.remove();
            }
        }
        tide.zombieIds.clear();
        tide.zombieContributors.clear();
        playUndeadTideVictory(tide);
        updateUndeadTideBossBar(tide);
    }

    private void playUndeadTideVictory(UndeadTideState tide) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(tide.center.getWorld())
                    && player.getLocation().distanceSquared(tide.center) <= UNDEAD_TIDE_BOSS_BAR_RANGE_SQUARED) {
                player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 1.0F, 1.0F);
            }
        }
    }

    private void finishUndeadTideVictory(UndeadTideState tide) {
        undeadTides.remove(tide.id);
        tide.bossBar.removeAll();
        giveUndeadTideRewards(tide);
    }

    private void endUndeadTide(UndeadTideState tide, boolean success) {
        undeadTides.remove(tide.id);
        tide.bossBar.removeAll();
        for (UUID zombieId : tide.zombieIds) {
            Entity entity = Bukkit.getEntity(zombieId);
            if (entity != null) {
                entity.remove();
            }
        }
        tide.zombieIds.clear();
        tide.zombieContributors.clear();
        if (success) {
            giveUndeadTideRewards(tide);
        }
    }

    private void failUndeadTide(UndeadTideState tide) {
        undeadTides.remove(tide.id);
        tide.bossBar.removeAll();
        for (UUID zombieId : tide.zombieIds) {
            Entity entity = Bukkit.getEntity(zombieId);
            if (entity != null) {
                entity.getPersistentDataContainer().set(failedUndeadTideMobKey, PersistentDataType.BYTE, (byte) 1);
                failedUndeadTideMobs.add(zombieId);
            }
        }
        tide.zombieIds.clear();
        tide.zombieContributors.clear();
        cleanupFailedUndeadTideMobs();
    }

    private void giveUndeadTideRewards(UndeadTideState tide) {
        for (UUID playerId : tide.rewardPlayerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            ItemStack reward = new ItemStack(Material.VILLAGER_SPAWN_EGG);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            rememberVillagerEggAndUnlockBreederRecipe(player);
        }
    }

    private void cleanupFailedUndeadTideMobs() {
        if (failedUndeadTideMobs.isEmpty()) {
            return;
        }
        failedUndeadTideMobs.removeIf(mobId -> {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity == null) {
                return false;
            }
            if (entity.isDead() || !entity.isValid()) {
                return true;
            }
            if (!isWithinAnyPlayerLoadingRange(entity)) {
                entity.remove();
                return true;
            }
            return false;
        });
    }

    private void cleanupFailedUndeadTideMobs(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity.getPersistentDataContainer().has(failedUndeadTideMobKey, PersistentDataType.BYTE)) {
                entity.remove();
                failedUndeadTideMobs.remove(entity.getUniqueId());
            }
        }
    }

    private boolean isWithinAnyPlayerLoadingRange(Entity entity) {
        double range = (Math.max(1, getServer().getViewDistance()) + 1) * 16.0D;
        double rangeSquared = range * range;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(entity.getWorld())
                    && player.getLocation().distanceSquared(entity.getLocation()) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean clickMayGrantRedstone(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = null;
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }
        return isRedstoneItem(current) || isRedstoneItem(cursor) || isRedstoneItem(hotbar);
    }

    private boolean clickMayGrantUndeadDust(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = null;
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }
        return isUndeadDust(current) || isUndeadDust(cursor) || isUndeadDust(hotbar);
    }

    private boolean clickMayGrantVillagerSpawnEgg(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = null;
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }
        return isVillagerSpawnEgg(current) || isVillagerSpawnEgg(cursor) || isVillagerSpawnEgg(hotbar);
    }

    private boolean isRedstoneItem(ItemStack item) {
        return item != null && item.getType() == Material.REDSTONE && item.getAmount() > 0;
    }

    private boolean isSpawnEgg(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SPAWN_EGG") && item.getAmount() > 0;
    }

    private boolean isVillagerSpawnEgg(ItemStack item) {
        return item != null && item.getType() == Material.VILLAGER_SPAWN_EGG && item.getAmount() > 0;
    }

    private void unlockGeneratorRecipeIfHasRedstone(Player player) {
        if (player.getInventory().contains(Material.REDSTONE)) {
            customItemService.discoverRecipe(player, generatorRecipeKey);
        }
    }

    private void unlockVillagerBreederRecipeIfKnowsEgg(Player player) {
        if (customItemService.hasRecipeKnowledge(player, villagerEggKnowledgeKey)
                || player.getInventory().contains(Material.VILLAGER_SPAWN_EGG)) {
            rememberVillagerEggAndUnlockBreederRecipe(player);
        }
    }

    private void rememberVillagerEggAndUnlockBreederRecipe(Player player) {
        customItemService.rememberRecipeKnowledge(player, villagerEggKnowledgeKey);
        customItemService.discoverRecipes(player, List.of(villagerBreederRecipeKey, villagerTradingStationRecipeKey, zombieVillagerEggRecipeKey));
    }

    private void unlockUndeadCoreRecipeIfKnowsDust(Player player) {
        if (customItemService.hasRecipeKnowledge(player, undeadDustKnowledgeKey)
                || inventoryContainsUndeadDust(player)) {
            rememberUndeadDustAndUnlockCoreRecipe(player);
        }
    }

    private void rememberUndeadDustAndUnlockCoreRecipe(Player player) {
        customItemService.rememberAndDiscoverRecipe(player, undeadDustKnowledgeKey, undeadCoreRecipeKey);
    }

    private boolean inventoryContainsUndeadDust(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isUndeadDust(item)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack sanitizeTradingStationJobInput(ItemStack item) {
        if (isAir(item) || !isTradingStationWorkstation(item)) {
            return null;
        }
        ItemStack result = item.clone();
        result.setAmount(1);
        return result;
    }

    private boolean isTradingStationWorkstation(ItemStack item) {
        return item != null
                && item.getAmount() > 0
                && !isVillagerTradingStationItem(item)
                && WORKSTATION_PROFESSIONS.containsKey(item.getType());
    }

    private List<TradeSpec> tradeCandidates(Villager.Profession profession, int level) {
        return VANILLA_TRADE_CATALOG.getOrDefault(profession, List.of()).stream()
                .filter(trade -> trade.level() == level)
                .toList();
    }

    private TradeSpec tradeById(String id) {
        for (List<TradeSpec> trades : VANILLA_TRADE_CATALOG.values()) {
            for (TradeSpec trade : trades) {
                if (trade.id().equals(id)) {
                    return trade;
                }
            }
        }
        return null;
    }

    private List<TradeSpec> selectedTradesFor(MachineState state) {
        if (state.tradingProfession == null) {
            return List.of();
        }
        return VANILLA_TRADE_CATALOG.getOrDefault(state.tradingProfession, List.of()).stream()
                .filter(trade -> state.selectedTradeIds.contains(trade.id()))
                .toList();
    }

    private List<String> selectedTradeIdsForStorage(MachineState state) {
        return selectedTradesFor(state).stream()
                .map(TradeSpec::id)
                .toList();
    }

    private ItemStack tradeMenuItem(TradeSpec trade, boolean selected) {
        ItemStack item = trade.result().clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((selected ? "已选择 " : "") + trade.label(), selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("等级: " + villagerLevelName(trade.level()), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("花费: " + ingredientText(trade.firstIngredient())
                + (trade.secondIngredient() == null ? "" : " + " + ingredientText(trade.secondIngredient())), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(selected ? "点击移除" : "点击加入", selected ? NamedTextColor.RED : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String ingredientText(ItemStack item) {
        return item.getAmount() + "x " + item.getType().translationKey();
    }

    private String villagerLevelName(int level) {
        return switch (level) {
            case 1 -> "新手";
            case 2 -> "学徒";
            case 3 -> "老手";
            case 4 -> "专家";
            case 5 -> "大师";
            default -> "等级 " + level;
        };
    }

    private String professionDisplayName(Villager.Profession profession) {
        if (Villager.Profession.ARMORER.equals(profession)) {
            return "盔甲匠";
        }
        if (Villager.Profession.BUTCHER.equals(profession)) {
            return "屠夫";
        }
        if (Villager.Profession.CARTOGRAPHER.equals(profession)) {
            return "制图师";
        }
        if (Villager.Profession.CLERIC.equals(profession)) {
            return "牧师";
        }
        if (Villager.Profession.FARMER.equals(profession)) {
            return "农民";
        }
        if (Villager.Profession.FISHERMAN.equals(profession)) {
            return "渔夫";
        }
        if (Villager.Profession.FLETCHER.equals(profession)) {
            return "制箭师";
        }
        if (Villager.Profession.LEATHERWORKER.equals(profession)) {
            return "皮匠";
        }
        if (Villager.Profession.LIBRARIAN.equals(profession)) {
            return "图书管理员";
        }
        if (Villager.Profession.MASON.equals(profession)) {
            return "石匠";
        }
        if (Villager.Profession.SHEPHERD.equals(profession)) {
            return "牧羊人";
        }
        if (Villager.Profession.TOOLSMITH.equals(profession)) {
            return "工具匠";
        }
        if (Villager.Profession.WEAPONSMITH.equals(profession)) {
            return "武器匠";
        }
        return profession == null ? "无职业" : profession.toString();
    }

    private void clearTradesForLevel(MachineState state, int level) {
        state.selectedTradeIds.removeIf(id -> {
            TradeSpec trade = tradeById(id);
            return trade == null || trade.level() == level;
        });
    }

    private void openTradingStationMerchant(Player player, MachineState state) {
        if (state.tradingProfession == null || state.selectedTradeIds.isEmpty()) {
            player.sendMessage("请先为交易站选择职业和交易项。");
            return;
        }
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (TradeSpec trade : selectedTradesFor(state)) {
            recipes.add(trade.toRecipe());
        }
        if (recipes.isEmpty()) {
            player.sendMessage("当前职业没有可用交易项。");
            return;
        }
        Merchant merchant = Bukkit.createMerchant(Component.text("简易村民交易站 - " + professionDisplayName(state.tradingProfession)));
        merchant.setRecipes(recipes);
        player.openMerchant(merchant, true);
    }

    private void writeTradingStationDataToItem(ItemStack item, MachineState state) {
        if (!item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (state.tradingProfession != null) {
            String professionId = professionId(state.tradingProfession);
            if (professionId != null) {
                data.set(villagerTradingStationProfessionKey, PersistentDataType.STRING, professionId);
            }
        }
        if (!state.selectedTradeIds.isEmpty()) {
            data.set(villagerTradingStationTradesKey, PersistentDataType.STRING, String.join(",", selectedTradeIdsForStorage(state)));
        }
        item.setItemMeta(meta);
    }

    private void restoreMachineDataFromItem(MachineState state, ItemStack item) {
        if (state.type != MachineType.VILLAGER_TRADING_STATION || item == null || !item.hasItemMeta()) {
            return;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        String professionName = data.get(villagerTradingStationProfessionKey, PersistentDataType.STRING);
        state.tradingProfession = parseProfession(professionName);
        String trades = data.get(villagerTradingStationTradesKey, PersistentDataType.STRING);
        if (trades != null && state.tradingProfession != null) {
            for (String id : trades.split(",")) {
                TradeSpec trade = tradeById(id);
                if (trade != null && trade.profession().equals(state.tradingProfession)) {
                    state.selectedTradeIds.add(id);
                }
            }
        }
    }

    private Villager.Profession parseProfession(String professionName) {
        if (professionName == null || professionName.isBlank()) {
            return null;
        }
        return switch (professionName.toUpperCase(Locale.ROOT)) {
            case "ARMORER" -> Villager.Profession.ARMORER;
            case "BUTCHER" -> Villager.Profession.BUTCHER;
            case "CARTOGRAPHER" -> Villager.Profession.CARTOGRAPHER;
            case "CLERIC" -> Villager.Profession.CLERIC;
            case "FARMER" -> Villager.Profession.FARMER;
            case "FISHERMAN" -> Villager.Profession.FISHERMAN;
            case "FLETCHER" -> Villager.Profession.FLETCHER;
            case "LEATHERWORKER" -> Villager.Profession.LEATHERWORKER;
            case "LIBRARIAN" -> Villager.Profession.LIBRARIAN;
            case "MASON" -> Villager.Profession.MASON;
            case "SHEPHERD" -> Villager.Profession.SHEPHERD;
            case "TOOLSMITH" -> Villager.Profession.TOOLSMITH;
            case "WEAPONSMITH" -> Villager.Profession.WEAPONSMITH;
            default -> null;
        };
    }

    private String professionId(Villager.Profession profession) {
        if (Villager.Profession.ARMORER.equals(profession)) {
            return "ARMORER";
        }
        if (Villager.Profession.BUTCHER.equals(profession)) {
            return "BUTCHER";
        }
        if (Villager.Profession.CARTOGRAPHER.equals(profession)) {
            return "CARTOGRAPHER";
        }
        if (Villager.Profession.CLERIC.equals(profession)) {
            return "CLERIC";
        }
        if (Villager.Profession.FARMER.equals(profession)) {
            return "FARMER";
        }
        if (Villager.Profession.FISHERMAN.equals(profession)) {
            return "FISHERMAN";
        }
        if (Villager.Profession.FLETCHER.equals(profession)) {
            return "FLETCHER";
        }
        if (Villager.Profession.LEATHERWORKER.equals(profession)) {
            return "LEATHERWORKER";
        }
        if (Villager.Profession.LIBRARIAN.equals(profession)) {
            return "LIBRARIAN";
        }
        if (Villager.Profession.MASON.equals(profession)) {
            return "MASON";
        }
        if (Villager.Profession.SHEPHERD.equals(profession)) {
            return "SHEPHERD";
        }
        if (Villager.Profession.TOOLSMITH.equals(profession)) {
            return "TOOLSMITH";
        }
        if (Villager.Profession.WEAPONSMITH.equals(profession)) {
            return "WEAPONSMITH";
        }
        return null;
    }

    private void refreshLoadedMachines() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                refreshMachineDisplays(chunk);
            }
        }
    }

    private void refreshMachineDisplays(Chunk chunk) {
        for (MachineState state : machines.values()) {
            World world = Bukkit.getWorld(state.world);
            if (world == null || !world.equals(chunk.getWorld())) {
                continue;
            }
            if ((state.x >> 4) == chunk.getX() && (state.z >> 4) == chunk.getZ()) {
                refreshMachineDisplay(state);
            }
        }
    }

    private void refreshMachineDisplay(MachineState state) {
        World world = Bukkit.getWorld(state.world);
        if (world == null || !world.isChunkLoaded(state.x >> 4, state.z >> 4)) {
            return;
        }
        Block block = world.getBlockAt(state.x, state.y, state.z);
        if (block.getType() != MACHINE_CARRIER) {
            return;
        }
        customBlockService.spawnOrReplaceDisplay(block, blockDefinitionFor(state.type), state.front);
    }

    private void removeMachineDisplays(Block block, MachineState state) {
        String displayId = customBlockService.blockId(block);
        customBlockService.removeDisplays(
                block.getWorld(),
                block.getLocation().add(0.5, 0.5, 0.5),
                blockDefinitionFor(state.type),
                displayId,
                1.2D);
        customBlockService.removeDisplays(
                block.getWorld(),
                block.getLocation().add(0.5, 0.5, 0.5),
                generatorBlockDefinition,
                displayId,
                1.2D);
        customBlockService.removeDisplays(
                block.getWorld(),
                block.getLocation().add(0.5, 0.5, 0.5),
                villagerBreederBlockDefinition,
                displayId,
                1.2D);
        customBlockService.removeDisplays(
                block.getWorld(),
                block.getLocation().add(0.5, 0.5, 0.5),
                villagerTradingStationBlockDefinition,
                displayId,
                1.2D);
    }

    private void cleanupOrphanMachineDisplays(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ItemDisplay)) {
                continue;
            }
            PersistentDataContainer data = entity.getPersistentDataContainer();
            if (!data.has(displayKey, PersistentDataType.STRING)) {
                continue;
            }
            String displayId = data.get(displayKey, PersistentDataType.STRING);
            String machineKey = machineKeyFromDisplayId(displayId);
            if (!isLiveMachineDisplay(machineKey)) {
                entity.remove();
            }
        }
    }

    private boolean isLiveMachineDisplay(String machineKey) {
        if (machineKey == null) {
            return false;
        }
        MachineState state = machines.get(machineKey);
        if (state == null) {
            return false;
        }
        Block block = blockFor(state);
        return block != null && block.getType() == MACHINE_CARRIER;
    }

    private String machineKeyFromDisplayId(String displayId) {
        if (displayId == null) {
            return null;
        }
        String[] parts = displayId.split("\\|", -1);
        if (parts.length != 4) {
            return null;
        }
        return parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3];
    }

    private CustomBlockDefinition blockDefinitionFor(MachineType type) {
        return switch (type) {
            case VILLAGER_BREEDER -> villagerBreederBlockDefinition;
            case VILLAGER_TRADING_STATION -> villagerTradingStationBlockDefinition;
            case GENERATOR -> generatorBlockDefinition;
        };
    }

    private void registerCustomBlocks() {
        generatorBlockDefinition = new CustomBlockDefinition(
                generatorItemKey,
                MACHINE_CARRIER,
                MACHINE_ITEM,
                generatorItemModelKey,
                displayKey,
                Component.text("Simple Cobblestone Generator"),
                1.0F,
                1.0F,
                DROPPER_HARDNESS);
        villagerBreederBlockDefinition = new CustomBlockDefinition(
                villagerBreederItemKey,
                MACHINE_CARRIER,
                Material.COMPOSTER,
                villagerBreederItemModelKey,
                displayKey,
                Component.text("Simple Villager Breeder"),
                1.0F,
                1.0F,
                DROPPER_HARDNESS);
        villagerTradingStationBlockDefinition = new CustomBlockDefinition(
                villagerTradingStationItemKey,
                MACHINE_CARRIER,
                Material.LECTERN,
                villagerTradingStationItemModelKey,
                displayKey,
                Component.text("Simple Villager Trading Station"),
                1.0F,
                1.0F,
                DROPPER_HARDNESS);
        customBlockService.registerBlock(generatorBlockDefinition);
        customBlockService.registerBlock(villagerBreederBlockDefinition);
        customBlockService.registerBlock(villagerTradingStationBlockDefinition);
    }

    private void registerCustomItems() {
        customItemService.register(new CustomItemDefinition(
                generatorItemKey,
                MACHINE_ITEM,
                generatorItemKey,
                generatorItemModelKey,
                Component.text("简易刷石机", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("右键放置后打开机器界面。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("注入水和熔岩后会自动吐出圆石。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                villagerBreederItemKey,
                Material.COMPOSTER,
                villagerBreederItemKey,
                villagerBreederItemModelKey,
                Component.text("简易村民繁殖机", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("右键放置后打开机器界面。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("开机后会定期产出村民刷怪蛋。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                villagerTradingStationItemKey,
                Material.LECTERN,
                villagerTradingStationItemKey,
                villagerTradingStationItemModelKey,
                Component.text("简易村民交易站", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("设置职业后选择原版候选交易。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("职业与交易配置会随方块掉落保存。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                undeadDustKey,
                Material.GUNPOWDER,
                undeadDustKey,
                undeadDustItemModelKey,
                Component.text("亡灵粉尘", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("从亡灵身上剥落的阴冷粉末。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                undeadCoreKey,
                Material.FIREWORK_STAR,
                undeadCoreKey,
                undeadCoreItemModelKey,
                Component.text("亡灵核心", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("由腐败残骸与亡灵粉尘凝结成的幽冷核心。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("捏碎它，或许会唤醒徘徊的亡潮。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("熬过试炼者，终会迎来生者的回赠。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
    }

    private ItemStack createGeneratorItem(int amount) {
        return customItemService.create(generatorItemKey, amount);
    }

    private ItemStack createVillagerBreederItem(int amount) {
        return customItemService.create(villagerBreederItemKey, amount);
    }

    private ItemStack createVillagerTradingStationItem(int amount) {
        return customItemService.create(villagerTradingStationItemKey, amount);
    }

    private ItemStack createMachineItem(MachineType type, int amount) {
        return switch (type) {
            case VILLAGER_BREEDER -> createVillagerBreederItem(amount);
            case VILLAGER_TRADING_STATION -> createVillagerTradingStationItem(amount);
            case GENERATOR -> createGeneratorItem(amount);
        };
    }

    private ItemStack createMachineDropItem(MachineState state) {
        ItemStack item = createMachineItem(state.type, 1);
        if (state.type == MachineType.VILLAGER_TRADING_STATION) {
            writeTradingStationDataToItem(item, state);
        }
        return item;
    }

    private boolean isGeneratorItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, generatorItemKey);
    }

    private boolean isVillagerBreederItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, villagerBreederItemKey);
    }

    private boolean isVillagerTradingStationItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, villagerTradingStationItemKey);
    }

    private MachineType machineTypeForItem(ItemStack item) {
        if (isGeneratorItem(item)) {
            return MachineType.GENERATOR;
        }
        if (isVillagerBreederItem(item)) {
            return MachineType.VILLAGER_BREEDER;
        }
        if (isVillagerTradingStationItem(item)) {
            return MachineType.VILLAGER_TRADING_STATION;
        }
        return null;
    }

    private ItemStack createUndeadDust(int amount) {
        return customItemService.create(undeadDustKey, amount);
    }

    private ItemStack createUndeadCore(int amount) {
        return customItemService.create(undeadCoreKey, amount);
    }

    private boolean isUndeadDust(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, undeadDustKey);
    }

    private boolean isUndeadCore(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, undeadCoreKey);
    }

    private boolean craftingMatrixContainsBlockedCustomItem(ItemStack[] matrix) {
        if (isUndeadCoreRecipeMatrix(matrix) || isZombieVillagerEggRecipeMatrix(matrix)) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (isUndeadDust(item) || isUndeadCore(item) || isGeneratorItem(item) || isVillagerBreederItem(item) || isVillagerTradingStationItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUndeadCoreRecipeMatrix(ItemStack[] matrix) {
        if (matrix.length != 9) {
            return false;
        }
        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack item = matrix[slot];
            if (slot == 4) {
                if (!isUndeadDust(item)) {
                    return false;
                }
                continue;
            }
            if (isAir(item) || item.getType() != Material.ROTTEN_FLESH) {
                return false;
            }
        }
        return true;
    }

    private boolean isZombieVillagerEggRecipeMatrix(ItemStack[] matrix) {
        int villagerEggs = 0;
        int undeadDusts = 0;
        for (ItemStack item : matrix) {
            if (isAir(item)) {
                continue;
            }
            if (isVillagerSpawnEgg(item)) {
                villagerEggs++;
                continue;
            }
            if (isUndeadDust(item)) {
                undeadDusts++;
                continue;
            }
            return false;
        }
        return villagerEggs == 1 && undeadDusts == 1;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private BlockFace frontFor(Player player) {
        Vector direction = player.getLocation().getDirection();
        double absX = Math.abs(direction.getX());
        double absY = Math.abs(direction.getY());
        double absZ = Math.abs(direction.getZ());
        if (absY > absX && absY > absZ) {
            return direction.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }
        if (absX > absZ) {
            return direction.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return direction.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private float yawFor(BlockFace front) {
        return switch (front) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> -90.0f;
            default -> 0.0f;
        };
    }

    private float pitchFor(BlockFace front) {
        return switch (front) {
            case UP -> 90.0f;
            case DOWN -> -90.0f;
            default -> 0.0f;
        };
    }

    private String displayFace(BlockFace face) {
        return switch (face) {
            case NORTH -> "北";
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            case UP -> "上";
            case DOWN -> "下";
            default -> face.name();
        };
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private void loadMachines() {
        machines.clear();
        machinesConfig = YamlConfiguration.loadConfiguration(machinesFile);
        ConfigurationSection root = machinesConfig.getConfigurationSection("machines");
        if (root == null) {
            return;
        }
        for (String sectionKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(sectionKey);
            if (section == null) {
                continue;
            }
            String world = section.getString("world", "");
            int x = section.getInt("x");
            int y = section.getInt("y");
            int z = section.getInt("z");
            MachineType type;
            try {
                type = MachineType.valueOf(section.getString("type", MachineType.GENERATOR.name()));
            } catch (IllegalArgumentException ex) {
                type = MachineType.GENERATOR;
            }
            BlockFace front;
            try {
                front = BlockFace.valueOf(section.getString("front", "NORTH"));
            } catch (IllegalArgumentException ex) {
                front = BlockFace.NORTH;
            }
            MachineState state = new MachineState(type, world, x, y, z, front);
            state.water = clamp(section.getInt("water"), 0, WATER_LIMIT);
            state.lava = clamp(section.getInt("lava"), 0, LAVA_LIMIT);
            state.input = sanitizeInput(section.getItemStack("input"));
            state.breederEnabled = section.getBoolean("breeder.enabled", false);
            state.breederTicks = clamp(section.getInt("breeder.ticks"), 0, VILLAGER_BREEDER_INTERVAL_TICKS);
            ItemStack breederOutput = section.getItemStack("breeder.output");
            state.breederOutput = isVillagerSpawnEgg(breederOutput) ? breederOutput : null;
            state.tradingProfession = parseProfession(section.getString("trading.profession"));
            state.selectedTradeIds.clear();
            for (String tradeId : section.getStringList("trading.trades")) {
                TradeSpec trade = tradeById(tradeId);
                if (trade != null && state.tradingProfession != null && trade.profession().equals(state.tradingProfession)) {
                    state.selectedTradeIds.add(tradeId);
                }
            }
            machines.put(state.key(), state);
        }
    }

    private void saveMachines() {
        if (machinesConfig == null) {
            machinesConfig = new YamlConfiguration();
        }
        machinesConfig.set("machines", null);
        ConfigurationSection root = machinesConfig.createSection("machines");
        for (MachineState state : machines.values()) {
            ConfigurationSection section = root.createSection(storageKey(state.key()));
            section.set("type", state.type.name());
            section.set("world", state.world);
            section.set("x", state.x);
            section.set("y", state.y);
            section.set("z", state.z);
            section.set("front", state.front.name());
            section.set("water", state.water);
            section.set("lava", state.lava);
            if (!isAir(state.input)) {
                section.set("input", state.input);
            }
            section.set("breeder.enabled", state.breederEnabled);
            section.set("breeder.ticks", state.breederTicks);
            if (!isAir(state.breederOutput)) {
                section.set("breeder.output", state.breederOutput);
            }
            if (state.tradingProfession != null) {
                String professionId = professionId(state.tradingProfession);
                if (professionId != null) {
                    section.set("trading.profession", professionId);
                    section.set("trading.trades", selectedTradeIdsForStorage(state));
                }
            }
        }
        try {
            machinesConfig.save(machinesFile);
        } catch (IOException ex) {
            getLogger().warning("Failed to save machines.yml: " + ex.getMessage());
        }
    }

    private String storageKey(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Material> materialsOf(String... names) {
        List<Material> materials = new ArrayList<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return List.copyOf(materials);
    }

    private static <T> T randomFrom(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private static Map<Villager.Profession, List<TradeSpec>> buildVanillaTradeCatalog() {
        Map<Villager.Profession, List<TradeSpec>> catalog = new HashMap<>();
        addTrade(catalog, Villager.Profession.ARMORER, 1, "armorer_1_coal", "煤炭换绿宝石", item(Material.EMERALD, 1), item(Material.COAL, 15), null);
        addTrade(catalog, Villager.Profession.ARMORER, 2, "armorer_2_iron_leggings", "铁护腿", item(Material.IRON_LEGGINGS, 1), item(Material.EMERALD, 7), null);
        addTrade(catalog, Villager.Profession.ARMORER, 3, "armorer_3_lava_bucket", "熔岩桶换绿宝石", item(Material.EMERALD, 1), item(Material.LAVA_BUCKET, 1), null);
        addTrade(catalog, Villager.Profession.ARMORER, 4, "armorer_4_diamond", "钻石换绿宝石", item(Material.EMERALD, 1), item(Material.DIAMOND, 1), null);
        addTrade(catalog, Villager.Profession.ARMORER, 5, "armorer_5_diamond_chestplate", "钻石胸甲", item(Material.DIAMOND_CHESTPLATE, 1), item(Material.EMERALD, 21), null);

        addTrade(catalog, Villager.Profession.BUTCHER, 1, "butcher_1_chicken", "生鸡肉换绿宝石", item(Material.EMERALD, 1), item(Material.CHICKEN, 14), null);
        addTrade(catalog, Villager.Profession.BUTCHER, 2, "butcher_2_porkchop", "生猪排换绿宝石", item(Material.EMERALD, 1), item(Material.PORKCHOP, 7), null);
        addTrade(catalog, Villager.Profession.BUTCHER, 3, "butcher_3_mutton", "生羊肉换绿宝石", item(Material.EMERALD, 1), item(Material.MUTTON, 7), null);
        addTrade(catalog, Villager.Profession.BUTCHER, 4, "butcher_4_sweet_berries", "甜浆果换绿宝石", item(Material.EMERALD, 1), item(Material.SWEET_BERRIES, 10), null);
        addTrade(catalog, Villager.Profession.BUTCHER, 5, "butcher_5_rabbit_stew", "兔肉煲", item(Material.RABBIT_STEW, 1), item(Material.EMERALD, 1), null);

        addTrade(catalog, Villager.Profession.CARTOGRAPHER, 1, "cartographer_1_paper", "纸换绿宝石", item(Material.EMERALD, 1), item(Material.PAPER, 24), null);
        addTrade(catalog, Villager.Profession.CARTOGRAPHER, 2, "cartographer_2_map", "空地图", item(Material.MAP, 1), item(Material.EMERALD, 7), null);
        addTrade(catalog, Villager.Profession.CARTOGRAPHER, 3, "cartographer_3_glass_pane", "玻璃板换绿宝石", item(Material.EMERALD, 1), item(Material.GLASS_PANE, 11), null);
        addTrade(catalog, Villager.Profession.CARTOGRAPHER, 4, "cartographer_4_compass", "指南针换绿宝石", item(Material.EMERALD, 1), item(Material.COMPASS, 1), null);
        addTrade(catalog, Villager.Profession.CARTOGRAPHER, 5, "cartographer_5_banner", "旗帜图案", item(Material.FLOWER_BANNER_PATTERN, 1), item(Material.EMERALD, 8), null);

        addTrade(catalog, Villager.Profession.CLERIC, 1, "cleric_1_rotten_flesh", "腐肉换绿宝石", item(Material.EMERALD, 1), item(Material.ROTTEN_FLESH, 32), null);
        addTrade(catalog, Villager.Profession.CLERIC, 2, "cleric_2_redstone", "红石粉", item(Material.REDSTONE, 2), item(Material.EMERALD, 1), null);
        addTrade(catalog, Villager.Profession.CLERIC, 3, "cleric_3_gold_ingot", "金锭换绿宝石", item(Material.EMERALD, 1), item(Material.GOLD_INGOT, 3), null);
        addTrade(catalog, Villager.Profession.CLERIC, 4, "cleric_4_ender_pearl", "末影珍珠", item(Material.ENDER_PEARL, 1), item(Material.EMERALD, 5), null);
        addTrade(catalog, Villager.Profession.CLERIC, 5, "cleric_5_bottle_o_enchanting", "附魔之瓶", item(Material.EXPERIENCE_BOTTLE, 1), item(Material.EMERALD, 3), null);

        addTrade(catalog, Villager.Profession.FARMER, 1, "farmer_1_wheat", "小麦换绿宝石", item(Material.EMERALD, 1), item(Material.WHEAT, 20), null);
        addTrade(catalog, Villager.Profession.FARMER, 2, "farmer_2_carrot", "胡萝卜换绿宝石", item(Material.EMERALD, 1), item(Material.CARROT, 22), null);
        addTrade(catalog, Villager.Profession.FARMER, 3, "farmer_3_pumpkin", "南瓜换绿宝石", item(Material.EMERALD, 1), item(Material.PUMPKIN, 6), null);
        addTrade(catalog, Villager.Profession.FARMER, 4, "farmer_4_melon", "西瓜换绿宝石", item(Material.EMERALD, 1), item(Material.MELON, 4), null);
        addTrade(catalog, Villager.Profession.FARMER, 5, "farmer_5_golden_carrot", "金胡萝卜", item(Material.GOLDEN_CARROT, 3), item(Material.EMERALD, 3), null);

        addTrade(catalog, Villager.Profession.FISHERMAN, 1, "fisherman_1_string", "线换绿宝石", item(Material.EMERALD, 1), item(Material.STRING, 20), null);
        addTrade(catalog, Villager.Profession.FISHERMAN, 2, "fisherman_2_cod", "生鳕鱼换绿宝石", item(Material.EMERALD, 1), item(Material.COD, 15), null);
        addTrade(catalog, Villager.Profession.FISHERMAN, 3, "fisherman_3_campfire", "营火", item(Material.CAMPFIRE, 1), item(Material.EMERALD, 2), null);
        addTrade(catalog, Villager.Profession.FISHERMAN, 4, "fisherman_4_salmon", "生鲑鱼换绿宝石", item(Material.EMERALD, 1), item(Material.SALMON, 13), null);
        addTrade(catalog, Villager.Profession.FISHERMAN, 5, "fisherman_5_pufferfish", "河豚换绿宝石", item(Material.EMERALD, 1), item(Material.PUFFERFISH, 4), null);

        addTrade(catalog, Villager.Profession.FLETCHER, 1, "fletcher_1_stick", "木棍换绿宝石", item(Material.EMERALD, 1), item(Material.STICK, 32), null);
        addTrade(catalog, Villager.Profession.FLETCHER, 2, "fletcher_2_arrow", "箭", item(Material.ARROW, 16), item(Material.EMERALD, 1), null);
        addTrade(catalog, Villager.Profession.FLETCHER, 3, "fletcher_3_flint", "燧石换绿宝石", item(Material.EMERALD, 1), item(Material.FLINT, 26), null);
        addTrade(catalog, Villager.Profession.FLETCHER, 4, "fletcher_4_string", "线换绿宝石", item(Material.EMERALD, 1), item(Material.STRING, 14), null);
        addTrade(catalog, Villager.Profession.FLETCHER, 5, "fletcher_5_crossbow", "弩", item(Material.CROSSBOW, 1), item(Material.EMERALD, 8), null);

        addTrade(catalog, Villager.Profession.LEATHERWORKER, 1, "leatherworker_1_leather", "皮革换绿宝石", item(Material.EMERALD, 1), item(Material.LEATHER, 6), null);
        addTrade(catalog, Villager.Profession.LEATHERWORKER, 2, "leatherworker_2_leather_pants", "皮革裤子", item(Material.LEATHER_LEGGINGS, 1), item(Material.EMERALD, 3), null);
        addTrade(catalog, Villager.Profession.LEATHERWORKER, 3, "leatherworker_3_flint", "燧石换绿宝石", item(Material.EMERALD, 1), item(Material.FLINT, 26), null);
        addTrade(catalog, Villager.Profession.LEATHERWORKER, 4, "leatherworker_4_rabbit_hide", "兔子皮换绿宝石", item(Material.EMERALD, 1), item(Material.RABBIT_HIDE, 9), null);
        addTrade(catalog, Villager.Profession.LEATHERWORKER, 5, "leatherworker_5_saddle", "鞍", item(Material.SADDLE, 1), item(Material.EMERALD, 6), null);

        addTrade(catalog, Villager.Profession.LIBRARIAN, 1, "librarian_1_paper", "纸换绿宝石", item(Material.EMERALD, 1), item(Material.PAPER, 24), null);
        addTrade(catalog, Villager.Profession.LIBRARIAN, 1, "librarian_1_mending", "经验修补", enchantedBook(Enchantment.MENDING, 1), item(Material.EMERALD, 38), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 1, "librarian_1_unbreaking", "耐久 III", enchantedBook(Enchantment.UNBREAKING, 3), item(Material.EMERALD, 26), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 1, "librarian_1_silk_touch", "精准采集", enchantedBook(Enchantment.SILK_TOUCH, 1), item(Material.EMERALD, 20), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 2, "librarian_2_bookshelf", "书架", item(Material.BOOKSHELF, 1), item(Material.EMERALD, 9), null);
        addTrade(catalog, Villager.Profession.LIBRARIAN, 2, "librarian_2_efficiency", "效率 V", enchantedBook(Enchantment.EFFICIENCY, 5), item(Material.EMERALD, 45), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 2, "librarian_2_fortune", "时运 III", enchantedBook(Enchantment.FORTUNE, 3), item(Material.EMERALD, 32), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 2, "librarian_2_protection", "保护 IV", enchantedBook(Enchantment.PROTECTION, 4), item(Material.EMERALD, 36), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 3, "librarian_3_glass", "玻璃", item(Material.GLASS, 4), item(Material.EMERALD, 1), null);
        addTrade(catalog, Villager.Profession.LIBRARIAN, 3, "librarian_3_feather_falling", "摔落保护 IV", enchantedBook(Enchantment.FEATHER_FALLING, 4), item(Material.EMERALD, 28), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 3, "librarian_3_respiration", "水下呼吸 III", enchantedBook(Enchantment.RESPIRATION, 3), item(Material.EMERALD, 24), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 3, "librarian_3_depth_strider", "深海探索者 III", enchantedBook(Enchantment.DEPTH_STRIDER, 3), item(Material.EMERALD, 24), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 4, "librarian_4_compass", "指南针", item(Material.COMPASS, 1), item(Material.EMERALD, 4), null);
        addTrade(catalog, Villager.Profession.LIBRARIAN, 4, "librarian_4_sharpness", "锋利 V", enchantedBook(Enchantment.SHARPNESS, 5), item(Material.EMERALD, 45), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 4, "librarian_4_looting", "抢夺 III", enchantedBook(Enchantment.LOOTING, 3), item(Material.EMERALD, 30), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 4, "librarian_4_fire_aspect", "火焰附加 II", enchantedBook(Enchantment.FIRE_ASPECT, 2), item(Material.EMERALD, 22), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 5, "librarian_5_name_tag", "命名牌", item(Material.NAME_TAG, 1), item(Material.EMERALD, 20), null);
        addTrade(catalog, Villager.Profession.LIBRARIAN, 5, "librarian_5_power", "力量 V", enchantedBook(Enchantment.POWER, 5), item(Material.EMERALD, 32), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 5, "librarian_5_infinity", "无限", enchantedBook(Enchantment.INFINITY, 1), item(Material.EMERALD, 22), item(Material.BOOK, 1));
        addTrade(catalog, Villager.Profession.LIBRARIAN, 5, "librarian_5_quick_charge", "快速装填 III", enchantedBook(Enchantment.QUICK_CHARGE, 3), item(Material.EMERALD, 24), item(Material.BOOK, 1));

        addTrade(catalog, Villager.Profession.MASON, 1, "mason_1_clay_ball", "黏土球换绿宝石", item(Material.EMERALD, 1), item(Material.CLAY_BALL, 10), null);
        addTrade(catalog, Villager.Profession.MASON, 2, "mason_2_bricks", "红砖", item(Material.BRICK, 10), item(Material.EMERALD, 1), null);
        addTrade(catalog, Villager.Profession.MASON, 3, "mason_3_stone", "石头换绿宝石", item(Material.EMERALD, 1), item(Material.STONE, 20), null);
        addTrade(catalog, Villager.Profession.MASON, 4, "mason_4_quartz", "石英块", item(Material.QUARTZ_BLOCK, 1), item(Material.EMERALD, 1), null);
        addTrade(catalog, Villager.Profession.MASON, 5, "mason_5_glazed_terracotta", "带釉陶瓦", item(Material.WHITE_GLAZED_TERRACOTTA, 1), item(Material.EMERALD, 1), null);

        addTrade(catalog, Villager.Profession.SHEPHERD, 1, "shepherd_1_wool", "白色羊毛换绿宝石", item(Material.EMERALD, 1), item(Material.WHITE_WOOL, 18), null);
        addTrade(catalog, Villager.Profession.SHEPHERD, 2, "shepherd_2_shears", "剪刀", item(Material.SHEARS, 1), item(Material.EMERALD, 2), null);
        addTrade(catalog, Villager.Profession.SHEPHERD, 3, "shepherd_3_dye", "黑色染料换绿宝石", item(Material.EMERALD, 1), item(Material.BLACK_DYE, 12), null);
        addTrade(catalog, Villager.Profession.SHEPHERD, 4, "shepherd_4_banner", "白色旗帜", item(Material.WHITE_BANNER, 1), item(Material.EMERALD, 3), null);
        addTrade(catalog, Villager.Profession.SHEPHERD, 5, "shepherd_5_painting", "画", item(Material.PAINTING, 3), item(Material.EMERALD, 2), null);

        addTrade(catalog, Villager.Profession.TOOLSMITH, 1, "toolsmith_1_coal", "煤炭换绿宝石", item(Material.EMERALD, 1), item(Material.COAL, 15), null);
        addTrade(catalog, Villager.Profession.TOOLSMITH, 2, "toolsmith_2_stone_axe", "石斧", item(Material.STONE_AXE, 1), item(Material.EMERALD, 1), null);
        addTrade(catalog, Villager.Profession.TOOLSMITH, 3, "toolsmith_3_iron_ingot", "铁锭换绿宝石", item(Material.EMERALD, 1), item(Material.IRON_INGOT, 4), null);
        addTrade(catalog, Villager.Profession.TOOLSMITH, 4, "toolsmith_4_flint", "燧石换绿宝石", item(Material.EMERALD, 1), item(Material.FLINT, 30), null);
        addTrade(catalog, Villager.Profession.TOOLSMITH, 5, "toolsmith_5_diamond_pickaxe", "钻石镐", item(Material.DIAMOND_PICKAXE, 1), item(Material.EMERALD, 18), null);

        addTrade(catalog, Villager.Profession.WEAPONSMITH, 1, "weaponsmith_1_coal", "煤炭换绿宝石", item(Material.EMERALD, 1), item(Material.COAL, 15), null);
        addTrade(catalog, Villager.Profession.WEAPONSMITH, 2, "weaponsmith_2_iron_axe", "铁斧", item(Material.IRON_AXE, 1), item(Material.EMERALD, 3), null);
        addTrade(catalog, Villager.Profession.WEAPONSMITH, 3, "weaponsmith_3_flint", "燧石换绿宝石", item(Material.EMERALD, 1), item(Material.FLINT, 24), null);
        addTrade(catalog, Villager.Profession.WEAPONSMITH, 4, "weaponsmith_4_diamond", "钻石换绿宝石", item(Material.EMERALD, 1), item(Material.DIAMOND, 1), null);
        addTrade(catalog, Villager.Profession.WEAPONSMITH, 5, "weaponsmith_5_diamond_sword", "钻石剑", item(Material.DIAMOND_SWORD, 1), item(Material.EMERALD, 16), null);

        catalog.replaceAll((profession, trades) -> List.copyOf(trades));
        return Map.copyOf(catalog);
    }

    private static void addTrade(Map<Villager.Profession, List<TradeSpec>> catalog, Villager.Profession profession, int level,
                                 String id, String label, ItemStack result, ItemStack firstIngredient, ItemStack secondIngredient) {
        catalog.computeIfAbsent(profession, ignored -> new ArrayList<>()).add(new TradeSpec(
                id,
                profession,
                level,
                label,
                result,
                firstIngredient,
                secondIngredient,
                12,
                level,
                0.05F));
    }

    private static ItemStack item(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    private static ItemStack enchantedBook(Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(enchantment, level, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private record TradeSpec(
            String id,
            Villager.Profession profession,
            int level,
            String label,
            ItemStack result,
            ItemStack firstIngredient,
            ItemStack secondIngredient,
            int maxUses,
            int villagerExperience,
            float priceMultiplier) {
        private MerchantRecipe toRecipe() {
            MerchantRecipe recipe = new MerchantRecipe(result.clone(), 0, maxUses, true, villagerExperience, priceMultiplier);
            recipe.addIngredient(firstIngredient.clone());
            if (secondIngredient != null) {
                recipe.addIngredient(secondIngredient.clone());
            }
            return recipe;
        }
    }

    private static final class UndeadTideState {
        private final UUID id;
        private final Location center;
        private final HudBossBar bossBar;
        private final Set<UUID> zombieIds = new HashSet<>();
        private final Map<UUID, Set<UUID>> zombieContributors = new HashMap<>();
        private final Set<UUID> rewardPlayerIds = new HashSet<>();
        private int progress;
        private int ageTicks;
        private int nextSpawnTicks;
        private int consecutiveSpawnFailures;
        private int victoryTicksRemaining;

        private UndeadTideState(HudService hudService, UUID id, Location center) {
            this.id = id;
            this.center = center;
            this.bossBar = hudService.createBossBar("xicesimpleindustry:undead_tide:" + id, "亡灵潮 0/" + UNDEAD_TIDE_REQUIRED_PROGRESS, BarColor.RED, BarStyle.SEGMENTED_10);
            this.bossBar.setProgress(0.0D);
        }
    }

    private enum MachineType {
        GENERATOR,
        VILLAGER_BREEDER,
        VILLAGER_TRADING_STATION
    }

    private static final class MachineState {
        private final MachineType type;
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final BlockFace front;
        private int water;
        private int lava;
        private ItemStack input;
        private boolean breederEnabled;
        private int breederTicks;
        private ItemStack breederOutput;
        private Villager.Profession tradingProfession;
        private final Set<String> selectedTradeIds = new LinkedHashSet<>();

        private MachineState(MachineType type, String world, int x, int y, int z, BlockFace front) {
            this.type = type;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.front = front;
        }

        private String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        private int efficiency() {
            return Math.min(lava, water * 4);
        }

        private Location centerLocation() {
            World loadedWorld = Bukkit.getWorld(world);
            if (loadedWorld == null) {
                return null;
            }
            return new Location(loadedWorld, x + 0.5D, y + 0.5D, z + 0.5D);
        }
    }

    private static final class TradingStationJobMenu implements InventoryHolder {
        private final String machineKey;
        private Inventory inventory;

        private TradingStationJobMenu(String machineKey) {
            this.machineKey = machineKey;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class TradingStationTradeMenu implements InventoryHolder {
        private final String machineKey;
        private final int level;
        private Inventory inventory;

        private TradingStationTradeMenu(String machineKey, int level) {
            this.machineKey = machineKey;
            this.level = level;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class BreederMenu implements InventoryHolder {
        private final String machineKey;
        private Inventory inventory;

        private BreederMenu(String machineKey) {
            this.machineKey = machineKey;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class GeneratorMenu implements InventoryHolder {
        private final String machineKey;
        private Inventory inventory;

        private GeneratorMenu(String machineKey) {
            this.machineKey = machineKey;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MachineBreakSession {
        private final String blockKey;
        private final Location location;
        private double progress;

        private MachineBreakSession(String blockKey, Location location) {
            this.blockKey = blockKey;
            this.location = location;
        }
    }

}
