package com.xice.xicemc.rpg;

import com.xice.xicemc.customitem.CustomBlockDefinition;
import com.xice.xicemc.customitem.CustomBlockService;
import com.xice.xicemc.customitem.CustomItemDefinition;
import com.xice.xicemc.customitem.CustomItemService;
import com.xice.xicemc.hud.HudBossBar;
import com.xice.xicemc.hud.HudService;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Display;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class XiceRPGPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final Pattern MODULE_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{1,32}$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String RPG_TAB_WORLD_OWNER = "xicerpg:world";
    private static final String ROTTEN_GUARD_TYPE = "rotten_guard";
    private static final float ROTTEN_GUARD_DISPLAY_PICK_SIZE = 0.0F;
    private static final double ROTTEN_GUARD_MAX_HEALTH = 60.0D;
    private static final double ROTTEN_GUARD_ARMOR = 10.0D;
    private static final double ROTTEN_GUARD_ATTACK_DAMAGE = 20.0D;
    private static final double ROTTEN_GUARD_FOLLOW_RANGE = 64.0D;
    private static final double ROTTEN_GUARD_MOVEMENT_SPEED = 0.3D;
    private static final double ROTTEN_GUARD_HITBOX_EXPANSION = 0.18D;
    private static final double ROTTEN_GUARD_WALK_SPEED_SQUARED = 0.0036D;
    private static final String GULPER_TYPE = "gulper";
    private static final float GULPER_DISPLAY_PICK_SIZE = 0.0F;
    private static final double GULPER_MAX_HEALTH = 200.0D;
    private static final double GULPER_HEALTH_DECAY_PER_SECOND = GULPER_MAX_HEALTH * 0.02D;
    private static final double GULPER_ATTACK_HEAL = 1.0D;
    private static final double GULPER_ARMOR = 10.0D;
    private static final double GULPER_ATTACK_DAMAGE = 15.0D;
    private static final double GULPER_FOLLOW_RANGE = 64.0D;
    private static final double GULPER_MOVEMENT_SPEED = 0.34D;
    private static final double GULPER_KNOCKBACK_RESISTANCE = 0.0D;
    private static final double GULPER_HITBOX_EXPANSION = 0.28D;
    private static final double GULPER_SATURATION_DRAIN = 4.0D;
    private static final int GULPER_FOOD_DRAIN = 4;
    private static final double GULPER_HEALTH_DRAIN = 1.0D;
    private static final double BOSS_ENTITY_MAX_HEALTH = 1024.0D;
    private static final String FERRYMAN_TYPE = "ferryman";
    private static final float FERRYMAN_DISPLAY_PICK_SIZE = 0.0F;
    private static final double FERRYMAN_MAX_HEALTH = BOSS_ENTITY_MAX_HEALTH;
    private static final double FERRYMAN_ALL_DAMAGE_REDUCTION = 0.20D;
    private static final double FERRYMAN_ARMOR = 20.0D;
    private static final double FERRYMAN_ATTACK_DAMAGE = 30.0D;
    private static final double FERRYMAN_FOLLOW_RANGE = 64.0D;
    private static final double FERRYMAN_MOVEMENT_SPEED = 0.30D;
    private static final double FERRYMAN_KNOCKBACK_RESISTANCE = 1.0D;
    private static final int FERRYMAN_EXPERIENCE = 1000;
    private static final double FERRYMAN_HEALTH_DISPLAY_HEIGHT = 3.0D;
    private static final double FERRYMAN_HITBOX_EXPANSION = 0.38D;
    private static final double FERRYMAN_DIMENSION_DISORDER_Y = 70.0D;
    private static final long FERRYMAN_FIRST_SKILL_DELAY_TICKS = 100L;
    private static final long FERRYMAN_SKILL_INTERVAL_TICKS = 120L;
    private static final long FERRYMAN_CAST_TICKS = 60L;
    private static final long FERRYMAN_SOULFIRE_CHARGE_TICKS = 8L;
    private static final long FERRYMAN_SOULFIRE_AFTERSHOCK_TICKS = 20L;
    private static final long FERRYMAN_WASTELAND_CAST_TICKS = 200L;
    private static final long FERRYMAN_WASTELAND_ENRAGE_TICKS = 20L * 480L;
    private static final double FERRYMAN_FERRY_RADIUS = 8.0D;
    private static final double FERRYMAN_FERRY_DAMAGE = 120.0D;
    private static final double FERRYMAN_SOULFIRE_PATH_DAMAGE = 60.0D;
    private static final double FERRYMAN_SOULFIRE_EMPOWERED_PATH_DAMAGE = 120.0D;
    private static final double FERRYMAN_SOULFIRE_EMPOWER_DISTANCE = 8.0D;
    private static final double FERRYMAN_SOULFIRE_EMPOWER_KNOCKBACK = 2.4D;
    private static final double FERRYMAN_SOULFIRE_PATH_WIDTH = 1.55D;
    private static final double FERRYMAN_SOULFIRE_AFTERSHOCK_RADIUS = 3.0D;
    private static final double FERRYMAN_SOULFIRE_AFTERSHOCK_DAMAGE = 10.0D;
    private static final int FERRYMAN_SOULFIRE_BURN_TICKS = 20 * 10;
    private static final double FERRYMAN_SHOCK_DAMAGE = 80.0D;
    private static final float SATIETY_SKILL_ORB_RESTORE_AMOUNT = 12.0F;
    private static final long SATIETY_SKILL_ORB_COOLDOWN_MILLIS = 20_000L;
    private static final BossSkillType[] FERRYMAN_SKILL_SEQUENCE = {
            BossSkillType.FERRY,
            BossSkillType.SOULFIRE,
            BossSkillType.SHOCK
    };
    private static final String PUS_BUG_TYPE = "pus_bug";
    private static final float PUS_BUG_DISPLAY_PICK_SIZE = 0.0F;
    private static final double PUS_BUG_MAX_HEALTH = 40.0D;
    private static final double PUS_BUG_ARMOR = 8.0D;
    private static final double PUS_BUG_ATTACK_DAMAGE = 10.0D;
    private static final double PUS_BUG_EXPLOSION_DAMAGE = PUS_BUG_ATTACK_DAMAGE * 2.5D;
    private static final double PUS_BUG_FOLLOW_RANGE = 64.0D;
    private static final double PUS_BUG_MOVEMENT_SPEED = 0.2D;
    private static final double PUS_BUG_KNOCKBACK_RESISTANCE = 0.95D;
    private static final double PUS_BUG_JUMP_STRENGTH = 0.0D;
    private static final double PUS_BUG_EXPLOSION_RADIUS = 3.5D;
    private static final double PUS_BUG_EXPLOSION_KNOCKBACK = 1.1D;
    private static final double PUS_BUG_HITBOX_EXPANSION = 0.46D;
    private static final double PUS_BUG_VERTICAL_HITBOX_EXPANSION = 0.42D;
    private static final double ROTTEN_GUARD_HEALTH_DISPLAY_HEIGHT = 2.35D;
    private static final double GULPER_HEALTH_DISPLAY_HEIGHT = 2.55D;
    private static final double PUS_BUG_HEALTH_DISPLAY_HEIGHT = 1.45D;
    private static final String TRAINING_DUMMY_TYPE = "training_dummy";
    private static final float TRAINING_DUMMY_DISPLAY_PICK_SIZE = 0.9F;
    private static final double TRAINING_DUMMY_DEFAULT_MAX_HEALTH = 100.0D;
    private static final double TRAINING_DUMMY_DEFAULT_ARMOR = 0.0D;
    private static final double TRAINING_DUMMY_HEALTH_DISPLAY_HEIGHT = 2.55D;
    private static final double TRAINING_DUMMY_HITBOX_EXPANSION = 0.32D;
    private static final long TRAINING_DUMMY_DPS_WINDOW_TICKS = 20L * 60L;
    private static final long SPLIT_DAMAGE_INVULNERABILITY_TICKS = 20L;
    private static final long SPLIT_DAMAGE_REPLACE_TICKS = 10L;
    private static final long REBIRTH_BLESSING_DURATION_MILLIS = 5_000L;
    private static final String TRAINING_DUMMY_TAG_UNDEAD = "undead";
    private static final String TRAINING_DUMMY_TAG_ARTHROPOD = "arthropod";
    private static final String TRAINING_DUMMY_TAG_AQUATIC = "aquatic";
    private static final double PUS_POOL_RADIUS = 3.0D;
    private static final double PUS_POOL_DAMAGE = 2.0D;
    private static final long PUS_POOL_DELAY_TICKS = 40L;
    private static final long PUS_POOL_INTERVAL_TICKS = 10L;
    private static final int PUS_POOL_CHECKS = 40;
    private static final int PUS_POISON_DURATION_TICKS = 200;
    private static final int PUS_POISON_AMPLIFIER = 2;
    private static final int CREATE_SIZE = 27;
    private static final int DELETE_SIZE = 27;
    private static final int SLOT_DECREASE_LARGE = 10;
    private static final int SLOT_DECREASE_SMALL = 11;
    private static final int SLOT_BORDER_INFO = 13;
    private static final int SLOT_INCREASE_SMALL = 15;
    private static final int SLOT_INCREASE_LARGE = 16;
    private static final int SLOT_USE_CURRENT_XZ = 3;
    private static final int SLOT_RESET_XZ = 5;
    private static final int SLOT_CURSE_DECREASE = 6;
    private static final int SLOT_CURSE_INFO = 7;
    private static final int SLOT_CURSE_INCREASE = 8;
    private static final int SLOT_DUNGEON_NAME = 20;
    private static final int SLOT_SPAWN_INFO = 22;
    private static final int SLOT_CONFIRM = 24;
    private static final int SLOT_CANCEL = 18;
    private static final int SLOT_ICON = 21;
    private static final int TOWER_MENU_SIZE = 54;
    private static final int SLOT_TOWER_BLESSING = 53;
    private static final int BLESSING_MENU_SIZE = 27;
    private static final int SLOT_BLESSING_NONE = 11;
    private static final int SLOT_BLESSING_ARCHER_BLESSING = 13;
    private static final int SLOT_BLESSING_SWORDSMAN_MEMORY = 15;
    private static final int DUNGEON_INFO_SIZE = 27;
    private static final int SLOT_DUNGEON_EXIT = 10;
    private static final int SLOT_DUNGEON_BESTIARY = 13;
    private static final int SLOT_DUNGEON_CLOSE = 16;
    private static final int DUNGEON_EXIT_CONFIRM_SIZE = 27;
    private static final int SLOT_DUNGEON_EXIT_CANCEL = 11;
    private static final int SLOT_DUNGEON_EXIT_CONFIRM = 15;
    private static final int BESTIARY_SIZE = 27;
    private static final int ENEMY_DETAIL_SIZE = 27;
    private static final int SLOT_ENEMY_DETAIL_INFO = 13;
    private static final int SLOT_ENEMY_DETAIL_BACK = 11;
    private static final int SLOT_ENEMY_DETAIL_CLOSE = 15;
    private static final Material DUNGEON_STARTER_CARRIER = Material.LODESTONE;
    private static final Material DUNGEON_STARTER_ITEM = Material.LODESTONE;
    private static final int DUNGEON_STARTER_CONFIG_SIZE = 27;
    private static final int DUNGEON_WAVE_DETAIL_SIZE = 45;
    private static final int MAX_DUNGEON_WAVES = 7;
    private static final int MAX_DUNGEON_WAVE_COUNT = 20;
    private static final int MAX_DUNGEON_WAVE_WAIT_SECONDS = 300;
    private static final int DEFAULT_DUNGEON_WAVE_WAIT_SECONDS = 3;
    private static final String DEFAULT_DUNGEON_BOSS_TYPE = FERRYMAN_TYPE;
    private static final String DEFAULT_DUNGEON_BOSS_DISPLAY_NAME = "引渡人";
    private static final double DEFAULT_DUNGEON_BOSS_MAX_HEALTH = FERRYMAN_MAX_HEALTH;
    private static final double DEFAULT_DUNGEON_BOSS_ATTACK_DAMAGE = FERRYMAN_ATTACK_DAMAGE;
    private static final double DEFAULT_DUNGEON_BOSS_ARMOR = FERRYMAN_ARMOR;
    private static final double DEFAULT_DUNGEON_BOSS_MOVEMENT_SPEED = FERRYMAN_MOVEMENT_SPEED;
    private static final double DEFAULT_DUNGEON_BOSS_FOLLOW_RANGE = FERRYMAN_FOLLOW_RANGE;
    private static final double DEFAULT_DUNGEON_BOSS_ALL_DAMAGE_REDUCTION = FERRYMAN_ALL_DAMAGE_REDUCTION;
    private static final double DUNGEON_BOSS_FALL_RESET_Y_OFFSET = 8.0D;
    private static final double DUNGEON_BOSS_PLATFORM_RESET_RADIUS = 24.0D;
    private static final int SLOT_ADD_DUNGEON_WAVE = 21;
    private static final int SLOT_REMOVE_DUNGEON_WAVE = 23;
    private static final int SLOT_DUNGEON_REWARDS = 25;
    private static final int DUNGEON_REWARD_SIZE = 54;
    private static final int SLOT_WAVE_WAIT_DECREASE_LARGE = 20;
    private static final int SLOT_WAVE_WAIT_DECREASE_SMALL = 21;
    private static final int SLOT_WAVE_WAIT_INFO = 22;
    private static final int SLOT_WAVE_WAIT_INCREASE_SMALL = 23;
    private static final int SLOT_WAVE_WAIT_INCREASE_LARGE = 24;
    private static final int SLOT_WAVE_ADD_ENEMY = 29;
    private static final int SLOT_WAVE_ADD_GULPER = 31;
    private static final int SLOT_WAVE_ADD_PUS_BUG = 33;
    private static final int SLOT_WAVE_REMOVE_ENEMY = 35;
    private static final int SLOT_WAVE_DETAIL_BACK = 40;
    private static final Material MAGIC_ANVIL_CARRIER = Material.ANVIL;
    private static final int MAGIC_ANVIL_MENU_SIZE = 27;
    private static final int MAGIC_ANVIL_MAIN_INPUT_SLOT = 10;
    private static final int MAGIC_ANVIL_SIDE_INPUT_SLOT = 12;
    private static final int MAGIC_ANVIL_CONFIRM_SLOT = 14;
    private static final int MAGIC_ANVIL_OUTPUT_SLOT = 16;
    private static final int MAGIC_ANVIL_PROBABILITY_SLOT = 22;
    private static final int MAGIC_ANVIL_FORESIGHT_SLOT = 4;
    private static final double MAGIC_ANVIL_HARDNESS = 5.0D;
    private static final String MAGIC_ANVIL_WEAPON_SWORD = "sword";
    private static final String MAGIC_ANVIL_ITEM_DURABLE = "durable";
    private static final String MAGIC_ANVIL_ITEM_EXTENDING_HAND = "extending_hand_item";
    private static final String MAGIC_ANVIL_ARMOR_CHESTPLATE = "chestplate";
    private static final String MAGIC_ANVIL_ARMOR_LEGGINGS = "leggings";
    private static final String CUSTOM_ENCHANT_WITHERING_BLADE = "withering_blade";
    private static final String CUSTOM_ENCHANT_PAIN_BLADE = "pain_blade";
    private static final String CUSTOM_ENCHANT_SELF_GROWING = "self_growing";
    private static final String CUSTOM_ENCHANT_SATIETY_VIGOR = "satiety_vigor";
    private static final String CUSTOM_ENCHANT_EXTENDING_HAND = "extending_hand";
    private static final String CUSTOM_ENCHANT_STEADY = "steady";
    private static final List<String> CUSTOM_ENCHANT_IDS = List.of(
            CUSTOM_ENCHANT_WITHERING_BLADE,
            CUSTOM_ENCHANT_PAIN_BLADE,
            CUSTOM_ENCHANT_SELF_GROWING,
            CUSTOM_ENCHANT_SATIETY_VIGOR,
            CUSTOM_ENCHANT_EXTENDING_HAND,
            CUSTOM_ENCHANT_STEADY);
    private static final double MAGIC_ANVIL_BASE_SUCCESS_PERCENT = 2.0D;
    private static final double MAGIC_ANVIL_FAILURE_BONUS_PERCENT = 1.0D;
    private static final long MAGIC_ANVIL_CONFIRM_COOLDOWN_TICKS = 5L;
    private static final Material MAGIC_GRINDSTONE_CARRIER = Material.BARRIER;
    private static final Material MAGIC_GRINDSTONE_LEGACY_CARRIER = Material.GRINDSTONE;
    private static final double MAGIC_GRINDSTONE_HARDNESS = 2.0D;
    private static final double CUSTOM_BLOCK_BREAK_REACH_SQUARED = 36.0D;
    private static final int MAGIC_GRINDSTONE_MENU_SIZE = 27;
    private static final int MAGIC_GRINDSTONE_INPUT_SLOT = 10;
    private static final int MAGIC_GRINDSTONE_CONFIRM_SLOT = 13;
    private static final int MAGIC_GRINDSTONE_OUTPUT_SLOT = 16;
    private static final int MAGIC_GRINDSTONE_INFO_SLOT = 4;
    private static final int MAGIC_GRINDSTONE_REFUND_PER_LEVEL = 5;
    private static final int TRAINING_DUMMY_MENU_SIZE = 45;
    private static final int SLOT_TRAINING_DUMMY_HEALTH_DEC = 10;
    private static final int SLOT_TRAINING_DUMMY_HEALTH_INFO = 11;
    private static final int SLOT_TRAINING_DUMMY_HEALTH_INC = 12;
    private static final int SLOT_TRAINING_DUMMY_ARMOR_DEC = 14;
    private static final int SLOT_TRAINING_DUMMY_ARMOR_INFO = 15;
    private static final int SLOT_TRAINING_DUMMY_ARMOR_INC = 16;
    private static final int SLOT_TRAINING_DUMMY_TAG_UNDEAD = 28;
    private static final int SLOT_TRAINING_DUMMY_TAG_ARTHROPOD = 30;
    private static final int SLOT_TRAINING_DUMMY_TAG_AQUATIC = 32;
    private static final int SLOT_TRAINING_DUMMY_RECLAIM = 40;
    private static final long TOWER_ENTRY_COUNTDOWN_TICKS = 60L;
    private static final double TOWER_ENTRY_MOVE_CANCEL_DISTANCE_SQUARED = 0.04D;
    private static final int TOWER_ENTRY_PRELOAD_CHUNK_RADIUS = 2;
    private static final int TEMPORARY_VIEW_DISTANCE = 2;
    private static final long PRE_TELEPORT_VIEW_DISTANCE_LEAD_TICKS = 10L;
    private static final long VIEW_DISTANCE_RAMP_INTERVAL_TICKS = 40L;
    private static final long DUNGEON_INFO_MENU_DELAY_TICKS = 60L;
    private static final int DUNGEON_SPAWN_PREVIEW_PARTICLES = 24;
    private static final double DUNGEON_SPAWN_PREVIEW_RADIUS = 0.75D;
    private static final List<Material> MODULE_ICON_CYCLE = List.of(
            Material.ECHO_SHARD,
            Material.DEEPSLATE_TILES,
            Material.POLISHED_BLACKSTONE_BRICKS,
            Material.LODESTONE,
            Material.RECOVERY_COMPASS,
            Material.ENDER_EYE,
            Material.AMETHYST_SHARD);

    private final Map<String, ModuleRecord> modules = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, TowerInstance> towerInstancesByPlayer = new HashMap<>();
    private final Map<String, TowerInstance> towerInstancesByWorld = new HashMap<>();
    private final Map<String, ModuleSnapshotState> moduleSnapshotStates = new HashMap<>();
    private final Map<UUID, PendingTowerEntry> pendingTowerEntries = new HashMap<>();
    private final Map<UUID, PlayerViewDistanceState> temporaryViewDistances = new HashMap<>();
    private final Map<UUID, String> lastDeathWorldNames = new HashMap<>();
    private final Map<UUID, CreateMenu> pendingCreateNameEdits = new HashMap<>();
    private final Set<UUID> pendingTowerInstances = new HashSet<>();
    private final Map<UUID, CustomBlockBreakSession> customBlockBreakSessions = new HashMap<>();
    private final Set<String> magicAnvils = new HashSet<>();
    private final Set<String> magicGrindstones = new HashSet<>();
    private final Map<String, List<String>> magicAnvilEnchantLists = new HashMap<>();
    private File modulesFile;
    private File magicAnvilsFile;
    private File magicGrindstonesFile;
    private File magicAnvilEnchantListsFile;
    private FileConfiguration modulesConfig;
    private FileConfiguration magicAnvilsConfig;
    private FileConfiguration magicGrindstonesConfig;
    private FileConfiguration magicAnvilEnchantListsConfig;
    private CustomItemService customItemService;
    private CustomBlockService customBlockService;
    private HudService hudService;
    private NamespacedKey magicDustKey;
    private NamespacedKey magicDustItemModelKey;
    private NamespacedKey magicAnvilItemKey;
    private NamespacedKey magicAnvilItemModelKey;
    private NamespacedKey magicAnvilDisplayKey;
    private NamespacedKey magicAnvilRecipeKey;
    private NamespacedKey magicGrindstoneItemKey;
    private NamespacedKey magicGrindstoneItemModelKey;
    private NamespacedKey magicGrindstoneDisplayKey;
    private NamespacedKey magicGrindstoneRecipeKey;
    private NamespacedKey magicEnchantedGoldenAppleRecipeKey;
    private NamespacedKey magicTowerKey;
    private NamespacedKey magicTowerKeyItemModelKey;
    private NamespacedKey magicTowerKeyRecipeKey;
    private NamespacedKey dungeonStarterItemKey;
    private NamespacedKey dungeonStarterItemModelKey;
    private NamespacedKey dungeonStarterDisplayKey;
    private NamespacedKey satietySkillOrbKey;
    private NamespacedKey satietySkillOrbItemModelKey;
    private CustomBlockDefinition magicAnvilBlockDefinition;
    private CustomBlockDefinition magicGrindstoneBlockDefinition;
    private CustomBlockDefinition dungeonStarterBlockDefinition;
    private NamespacedKey selectedBlessingKey;
    private NamespacedKey magicAnvilFailuresKey;
    private final Map<RottenGuardPart, NamespacedKey> rottenGuardPartModelKeys = new EnumMap<>(RottenGuardPart.class);
    private NamespacedKey monsterTypeKey;
    private NamespacedKey monsterDisplayKey;
    private NamespacedKey monsterDisplayOwnerKey;
    private NamespacedKey monsterHealthDisplayKey;
    private NamespacedKey monsterHealthDisplayOwnerKey;
    private NamespacedKey gulperModelKey;
    private NamespacedKey gulperDisplayKey;
    private NamespacedKey gulperDisplayOwnerKey;
    private NamespacedKey ferrymanModelKey;
    private NamespacedKey ferrymanDisplayKey;
    private NamespacedKey ferrymanDisplayOwnerKey;
    private NamespacedKey pusBugModelKey;
    private NamespacedKey pusBugDisplayKey;
    private NamespacedKey pusBugDisplayOwnerKey;
    private final Map<TrainingDummyPart, NamespacedKey> trainingDummyPartModelKeys = new EnumMap<>(TrainingDummyPart.class);
    private NamespacedKey trainingDummyDisplayKey;
    private NamespacedKey trainingDummyDisplayOwnerKey;
    private NamespacedKey trainingDummyMaxHealthKey;
    private NamespacedKey trainingDummyArmorKey;
    private NamespacedKey trainingDummyTagsKey;
    private NamespacedKey moduleReturnLocationKey;
    private NamespacedKey moduleLastLocationKey;
    private NamespacedKey completedModulesKey;
    private BukkitTask dungeonEffectTask;
    private BukkitTask customMonsterTask;
    private long customMonsterTick;
    private final Map<UUID, EnumMap<RottenGuardPart, UUID>> rottenGuardDisplays = new HashMap<>();
    private final Map<UUID, Long> rottenGuardAttackTicks = new HashMap<>();
    private final Map<UUID, Long> rottenGuardHurtTicks = new HashMap<>();
    private final Map<UUID, Float> rottenGuardBodyYaws = new HashMap<>();
    private final Map<UUID, UUID> gulperDisplays = new HashMap<>();
    private final Map<UUID, UUID> ferrymanDisplays = new HashMap<>();
    private final Map<UUID, Long> ferrymanAttackTicks = new HashMap<>();
    private final Map<UUID, Long> ferrymanHurtTicks = new HashMap<>();
    private final Map<UUID, UUID> pusBugDisplays = new HashMap<>();
    private final Map<UUID, EnumMap<TrainingDummyPart, UUID>> trainingDummyDisplays = new HashMap<>();
    private final Map<UUID, List<TrainingDummyDamageSample>> trainingDummyDamageSamples = new HashMap<>();
    private final Map<UUID, Map<String, SplitDamageCooldown>> splitDamageCooldowns = new HashMap<>();
    private final Map<UUID, Long> satietySkillOrbCooldowns = new HashMap<>();
    private final Map<UUID, Integer> dungeonPlayerNoDamageDefaults = new HashMap<>();
    private final Map<UUID, UUID> customMonsterHealthDisplays = new HashMap<>();
    private final Map<String, DungeonRun> dungeonRunsByWorld = new HashMap<>();
    private final Set<UUID> curseForcedExits = new HashSet<>();
    private final List<PusPool> pusPools = new ArrayList<>();
    private int customBlockBreakTaskId = -1;
    private boolean applyingBossSkillDamage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        magicDustKey = new NamespacedKey(this, "magic_dust");
        magicDustItemModelKey = new NamespacedKey(this, "magic_dust");
        magicAnvilItemKey = new NamespacedKey(this, "magic_anvil");
        magicAnvilItemModelKey = new NamespacedKey(this, "magic_anvil");
        magicAnvilDisplayKey = new NamespacedKey(this, "magic_anvil_display");
        magicAnvilRecipeKey = new NamespacedKey(this, "magic_anvil");
        magicGrindstoneItemKey = new NamespacedKey(this, "magic_grindstone");
        magicGrindstoneItemModelKey = new NamespacedKey(this, "magic_grindstone");
        magicGrindstoneDisplayKey = new NamespacedKey(this, "magic_grindstone_display");
        magicGrindstoneRecipeKey = new NamespacedKey(this, "magic_grindstone");
        magicEnchantedGoldenAppleRecipeKey = new NamespacedKey(this, "magic_enchanted_golden_apple");
        magicTowerKey = new NamespacedKey(this, "magic_tower_key");
        magicTowerKeyItemModelKey = new NamespacedKey(this, "magic_tower_key");
        magicTowerKeyRecipeKey = new NamespacedKey(this, "magic_tower_key_recipe");
        dungeonStarterItemKey = new NamespacedKey(this, "dungeon_starter");
        dungeonStarterItemModelKey = new NamespacedKey(this, "dungeon_starter");
        dungeonStarterDisplayKey = new NamespacedKey(this, "dungeon_starter_display");
        satietySkillOrbKey = new NamespacedKey(this, "satiety_skill_orb");
        satietySkillOrbItemModelKey = new NamespacedKey(this, "satiety_skill_orb");
        selectedBlessingKey = new NamespacedKey(this, "selected_blessing");
        magicAnvilFailuresKey = new NamespacedKey(this, "magic_anvil_failures");
        for (RottenGuardPart part : RottenGuardPart.values()) {
            rottenGuardPartModelKeys.put(part, new NamespacedKey(this, part.modelKey));
        }
        monsterTypeKey = new NamespacedKey(this, "monster_type");
        monsterDisplayKey = new NamespacedKey(this, "monster_display");
        monsterDisplayOwnerKey = new NamespacedKey(this, "monster_display_owner");
        monsterHealthDisplayKey = new NamespacedKey(this, "monster_health_display");
        monsterHealthDisplayOwnerKey = new NamespacedKey(this, "monster_health_display_owner");
        gulperModelKey = new NamespacedKey(this, "gulper_model");
        gulperDisplayKey = new NamespacedKey(this, "gulper_display");
        gulperDisplayOwnerKey = new NamespacedKey(this, "gulper_display_owner");
        ferrymanModelKey = new NamespacedKey(this, "ferryman_model");
        ferrymanDisplayKey = new NamespacedKey(this, "ferryman_display");
        ferrymanDisplayOwnerKey = new NamespacedKey(this, "ferryman_display_owner");
        pusBugModelKey = new NamespacedKey(this, "pus_bug_model");
        pusBugDisplayKey = new NamespacedKey(this, "pus_bug_display");
        pusBugDisplayOwnerKey = new NamespacedKey(this, "pus_bug_display_owner");
        for (TrainingDummyPart part : TrainingDummyPart.values()) {
            trainingDummyPartModelKeys.put(part, new NamespacedKey(this, part.modelKey));
        }
        trainingDummyDisplayKey = new NamespacedKey(this, "training_dummy_display");
        trainingDummyDisplayOwnerKey = new NamespacedKey(this, "training_dummy_display_owner");
        trainingDummyMaxHealthKey = new NamespacedKey(this, "training_dummy_max_health");
        trainingDummyArmorKey = new NamespacedKey(this, "training_dummy_armor");
        trainingDummyTagsKey = new NamespacedKey(this, "training_dummy_tags");
        moduleReturnLocationKey = new NamespacedKey(this, "module_return_location");
        moduleLastLocationKey = new NamespacedKey(this, "module_last_location");
        completedModulesKey = new NamespacedKey(this, "completed_modules");
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
        modulesFile = new File(getDataFolder(), "modules.yml");
        magicAnvilsFile = new File(getDataFolder(), "magic-anvils.yml");
        magicGrindstonesFile = new File(getDataFolder(), "magic-grindstones.yml");
        magicAnvilEnchantListsFile = new File(getDataFolder(), "magic-anvil-enchants.yml");
        loadModules();
        Bukkit.getScheduler().runTask(this, this::updateAllHudTabListWorlds);
        ensureDefaultModuleSettings();
        loadMagicAnvils();
        loadMagicGrindstones();
        loadMagicAnvilEnchantLists();
        Bukkit.getScheduler().runTaskLater(this, this::refreshLoadedMagicAnvilDisplays, 20L);
        Bukkit.getScheduler().runTaskLater(this, this::refreshLoadedMagicGrindstoneDisplays, 20L);
        cleanupOrphanTowerInstances();
        prepareMissingModuleSnapshots();
        registerMagicTowerKeyRecipe();
        registerMagicAnvilRecipe();
        registerMagicGrindstoneRecipe();
        registerMagicEnchantedGoldenAppleRecipe();
        customItemService.allowCustomIngredientRecipe(magicEnchantedGoldenAppleRecipeKey);
        Objects.requireNonNull(getCommand("module")).setExecutor(this);
        Objects.requireNonNull(getCommand("module")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("rpgmob")).setExecutor(this);
        Objects.requireNonNull(getCommand("rpgmob")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        dungeonEffectTask = Bukkit.getScheduler().runTaskTimer(this, this::applyDungeonEffectsToActivePlayers, 200L, 200L);
        customMonsterTask = Bukkit.getScheduler().runTaskTimer(this, this::tickCustomMonsters, 1L, 1L);
        customBlockBreakTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickCustomBlockBreaking, 1L, 1L).getTaskId();
        Bukkit.getScheduler().runTask(this, this::refreshLoadedDungeonStarterDisplays);
        getLogger().info("XiceRPG enabled. Modules loaded: " + modules.size());
    }

    @Override
    public void onDisable() {
        if (dungeonEffectTask != null) {
            dungeonEffectTask.cancel();
            dungeonEffectTask = null;
        }
        if (customMonsterTask != null) {
            customMonsterTask.cancel();
            customMonsterTask = null;
        }
        if (customBlockBreakTaskId != -1) {
            Bukkit.getScheduler().cancelTask(customBlockBreakTaskId);
            customBlockBreakTaskId = -1;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopCustomBlockBreak(player);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            hudService.clearTabListWorld(player.getUniqueId(), RPG_TAB_WORLD_OWNER);
            restoreDungeonPlayerDamageRules(player);
            restorePlayerViewDistance(player, "disable");
        }
        for (PendingTowerEntry entry : new ArrayList<>(pendingTowerEntries.values())) {
            cancelPendingTowerEntry(entry, null, true);
        }
        for (TowerInstance instance : new ArrayList<>(towerInstancesByWorld.values())) {
            cleanupTowerInstance(instance, true);
        }
        dungeonRunsByWorld.clear();
        pusPools.clear();
        removeAllRottenGuardDisplays();
        removeAllGulperDisplays();
        removeAllFerrymanDisplays();
        removeAllPusBugDisplays();
        removeAllTrainingDummyDisplays();
        removeAllCustomMonsterHealthDisplays();
        if (customItemService != null) {
            customItemService.unregisterRecipe(magicTowerKeyRecipeKey);
            customItemService.unregisterRecipe(magicAnvilRecipeKey);
            customItemService.unregisterRecipe(magicGrindstoneRecipeKey);
            customItemService.unregisterRecipe(magicEnchantedGoldenAppleRecipeKey);
        } else {
            Bukkit.removeRecipe(magicTowerKeyRecipeKey);
            Bukkit.removeRecipe(magicAnvilRecipeKey);
            Bukkit.removeRecipe(magicGrindstoneRecipeKey);
            Bukkit.removeRecipe(magicEnchantedGoldenAppleRecipeKey);
        }
        if (customBlockService != null) {
            customBlockService.unregisterBlock(magicAnvilItemKey);
            customBlockService.unregisterBlock(magicGrindstoneItemKey);
            customBlockService.unregisterBlock(dungeonStarterItemKey);
        }
        if (customItemService != null) {
            customItemService.disallowCustomIngredientRecipe(magicEnchantedGoldenAppleRecipeKey);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("rpgmob".equalsIgnoreCase(command.getName())) {
            return handleRpgMobCommand(sender, label, args);
        }

        if (args.length == 0) {
            sender.sendMessage("用法: /" + label + " <create|enter|exit|delete|reload> [名称]");
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            if (!sender.hasPermission("xicerpg.admin")) {
                sender.sendMessage("你没有重载 XiceRPG 的权限。");
                return true;
            }
            reloadConfig();
            loadModules();
            updateAllHudTabListWorlds();
            sender.sendMessage("XiceRPG 配置已重载。");
            return true;
        }

        if ("exit".equals(subCommand)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("该指令只能由玩家在游戏内使用。");
                return true;
            }
            exitModuleWorld(player);
            return true;
        }

        if (!canUseAction(sender, "module")) {
            sender.sendMessage("你没有使用 module 管理指令的权限。");
            return true;
        }

        return switch (subCommand) {
            case "create" -> handleCreate(sender, label, args);
            case "enter" -> handleEnter(sender, label, args);
            case "delete" -> handleDelete(sender, label, args);
            default -> {
                sender.sendMessage("未知子命令。用法: /" + label + " <create|enter|exit|delete> [名称]");
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该指令只能由玩家在游戏内使用。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /" + label + " create <名称>");
            return true;
        }
        String key = normalizeModuleName(args[1]);
        if (key == null) {
            sender.sendMessage("模板世界名称只能包含文字、数字、下划线或短横线，长度 1-32。");
            return true;
        }
        if (modules.containsKey(key)) {
            sender.sendMessage("该模板世界已存在。");
            return true;
        }
        CreateMenu menu = new CreateMenu(key, args[1], args[1], defaultModuleIcon(), defaultBorderDistance(),
                defaultCurseEscapeDeaths(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        openCreateMenu(player, menu);
        return true;
    }

    private boolean handleEnter(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该指令只能由玩家在游戏内使用。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /" + label + " enter <名称>");
            return true;
        }
        ModuleRecord module = moduleByInput(args[1]);
        if (module == null) {
            sender.sendMessage("未找到该模板世界。");
            return true;
        }
        enterModuleWorld(player, module);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该指令只能由玩家在游戏内使用。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /" + label + " delete <名称>");
            return true;
        }
        ModuleRecord module = moduleByInput(args[1]);
        if (module == null) {
            sender.sendMessage("未找到该模板世界。");
            return true;
        }
        openDeleteMenu(player, new DeleteMenu(module.key()));
        return true;
    }

    private boolean handleRpgMobCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("xicerpg.admin") && !canUseAction(sender, "mob")) {
            sender.sendMessage("你没有使用 RPG 怪物指令的权限。");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该指令只能由玩家在游戏内使用。");
            return true;
        }
        if (args.length < 2 || !"spawn".equalsIgnoreCase(args[0]) || !isCustomMonsterInput(args[1])) {
            sender.sendMessage("用法: /" + label + " spawn <rotten_guard|gulper|ferryman|pus_bug|training_dummy> [数量]");
            return true;
        }
        String monsterType = normalizeCustomMonsterType(args[1]);
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("数量必须是 1-20 之间的整数。");
                return true;
            }
        }
        Location spawnLocation = player.getLocation();
        for (int i = 0; i < amount; i++) {
            spawnCustomMonster(monsterType, spawnLocation);
        }
        EnemyEntry entry = enemyEntryByType(monsterType);
        sender.sendMessage("已生成 " + amount + " 个" + (entry == null ? monsterType : entry.displayName()) + "。");
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCustomMonsterTarget(EntityTargetLivingEntityEvent event) {
        if (dungeonRunByBoss(event.getEntity().getUniqueId()) != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        if (isCustomMonster(event.getEntity()) && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCustomMonsterDeath(EntityDeathEvent event) {
        DungeonRun bossRun = dungeonRunByBoss(event.getEntity().getUniqueId());
        if (bossRun != null) {
            if (isFerryman(event.getEntity())) {
                removeFerrymanDisplay(event.getEntity().getUniqueId());
            }
            event.getEntity().setSilent(true);
            event.setShouldPlayDeathSound(false);
            event.setDeathSound(null);
            event.setDeathSoundVolume(0.0F);
            event.getDrops().clear();
            event.setDroppedExp(FERRYMAN_EXPERIENCE);
            completeDungeonRun(bossRun, event.getEntity().getWorld());
            return;
        }
        if (isRottenGuard(event.getEntity())) {
            removeRottenGuardDisplay(event.getEntity().getUniqueId());
            event.getEntity().setSilent(true);
            event.setShouldPlayDeathSound(false);
            event.setDeathSound(null);
            event.setDeathSoundVolume(0.0F);
            event.getDrops().clear();
            event.setDroppedExp(10);
            return;
        }
        if (isGulper(event.getEntity())) {
            removeGulperDisplay(event.getEntity().getUniqueId());
            event.getEntity().setSilent(true);
            event.setShouldPlayDeathSound(false);
            event.setDeathSound(null);
            event.setDeathSoundVolume(0.0F);
            event.getDrops().clear();
            event.setDroppedExp(15);
            return;
        }
        if (isFerryman(event.getEntity())) {
            removeFerrymanDisplay(event.getEntity().getUniqueId());
            event.getEntity().setSilent(true);
            event.setShouldPlayDeathSound(false);
            event.setDeathSound(null);
            event.setDeathSoundVolume(0.0F);
            event.getDrops().clear();
            event.setDroppedExp(FERRYMAN_EXPERIENCE);
            return;
        }
        if (isPusBug(event.getEntity())) {
            removePusBugDisplay(event.getEntity().getUniqueId());
            event.getEntity().setSilent(true);
            event.setShouldPlayDeathSound(false);
            event.setDeathSound(null);
            event.setDeathSoundVolume(0.0F);
            event.getDrops().clear();
            event.setDroppedExp(6);
            triggerPusBugDeath((LivingEntity) event.getEntity());
            return;
        }
        if (isTrainingDummy(event.getEntity())) {
            removeTrainingDummyDisplay(event.getEntity().getUniqueId());
            event.getEntity().setSilent(true);
            event.setShouldPlayDeathSound(false);
            event.setDeathSound(null);
            event.setDeathSoundVolume(0.0F);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String deathWorldName = player.getWorld().getName();
        lastDeathWorldNames.put(player.getUniqueId(), deathWorldName);
        TowerInstance instance = towerInstancesByWorld.get(deathWorldName);
        if (instance == null || !instance.ownerUuid().equals(player.getUniqueId())) {
            return;
        }
        ModuleRecord module = modules.get(instance.moduleKey());
        int allowedDeaths = module == null ? defaultCurseEscapeDeaths() : module.curseEscapeDeaths();
        int deaths = instance.recordDeath();
        if (deaths > allowedDeaths) {
            curseForcedExits.add(player.getUniqueId());
            player.sendMessage("挣脱诅咒已耗尽，你将被送出副本。");
        } else {
            player.sendMessage("挣脱诅咒: " + deaths + "/" + allowedDeaths);
        }
        respawnPlayerNextTick(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomMonsterDamage(EntityDamageEvent event) {
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            DungeonRun bossRun = dungeonRunByBoss(living.getUniqueId());
            if (bossRun != null) {
                applyBossAllDamageReduction(event, bossRun);
                if (event.getFinalDamage() <= 0.0D) {
                    return;
                }
            }
        }
        if (isRottenGuard(event.getEntity())) {
            Zombie zombie = (Zombie) event.getEntity();
            if (event.getFinalDamage() >= zombie.getHealth()) {
                event.setCancelled(true);
                killCustomMonsterSilently(zombie);
                return;
            }
            rottenGuardHurtTicks.put(event.getEntity().getUniqueId(), customMonsterTick);
        }
        if (isGulper(event.getEntity())) {
            Zombie zombie = (Zombie) event.getEntity();
            zombie.setSilent(true);
            if (event.getFinalDamage() >= zombie.getHealth()) {
                event.setCancelled(true);
                killCustomMonsterSilently(zombie);
                return;
            }
        }
        if (isFerryman(event.getEntity())) {
            Zombie zombie = (Zombie) event.getEntity();
            zombie.setSilent(true);
            ferrymanHurtTicks.put(event.getEntity().getUniqueId(), customMonsterTick);
        }
        if (isPusBug(event.getEntity())) {
            Endermite endermite = (Endermite) event.getEntity();
            endermite.setSilent(true);
            if (event.getFinalDamage() >= endermite.getHealth()) {
                event.setCancelled(true);
                killCustomMonsterSilently(endermite);
                return;
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && isRottenGuard(byEntity.getDamager())) {
            rottenGuardAttackTicks.put(byEntity.getDamager().getUniqueId(), customMonsterTick);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && isGulper(byEntity.getDamager()) && event.getEntity() instanceof Player player) {
            applyGulperDrain((Zombie) byEntity.getDamager(), player);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && isFerryman(byEntity.getDamager())) {
            if (!applyingBossSkillDamage && isBossSkillLocked(byEntity.getDamager().getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (!applyingBossSkillDamage) {
                ferrymanAttackTicks.put(byEntity.getDamager().getUniqueId(), customMonsterTick);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplitDamageImmunityTargetDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !usesSplitDamageImmunity(target)) {
            return;
        }
        applySplitDamageTargetBase(target);
        boolean trainingDummy = isTrainingDummy(target);
        if (trainingDummy && event instanceof EntityDamageByEntityEvent byEntity) {
            applyTrainingDummyTagBonus((Slime) target, byEntity);
        }
        double incomingDamage = Math.max(0.0D, event.getFinalDamage());
        if (incomingDamage <= 0.0D) {
            return;
        }
        double damage = effectiveSplitDamage(target.getUniqueId(), damageImmunityChannel(event), incomingDamage);
        if (damage <= 0.0D) {
            event.setCancelled(true);
            target.setNoDamageTicks(0);
            return;
        }
        if (trainingDummy) {
            recordTrainingDummyDamage(target.getUniqueId(), damage);
            if (shouldLetTrainingDummyHitPassThrough(event)) {
                event.setDamage(safeTrainingDummyVanillaDamage(target));
                target.setNoDamageTicks(0);
                restoreTrainingDummyAfterVanillaHit((Slime) target);
                return;
            }
            event.setCancelled(true);
            target.setNoDamageTicks(0);
            applyTrainingDummyStats((Slime) target, false);
            return;
        }
        scaleEventToFinalDamage(event, damage);
        target.setNoDamageTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrainingDummyKnockback(EntityKnockbackEvent event) {
        if (isTrainingDummy(event.getEntity()) || isFerryman(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrainingDummyInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Slime dummy = null;
        if (isTrainingDummy(event.getRightClicked())) {
            dummy = (Slime) event.getRightClicked();
        } else if (isTrainingDummyDisplay(event.getRightClicked())) {
            dummy = trainingDummyOwner(event.getRightClicked());
        }
        if (dummy == null || !dummy.isValid() || dummy.isDead()) {
            return;
        }
        event.setCancelled(true);
        openTrainingDummyMenu(event.getPlayer(), dummy);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerArmSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
            return;
        }
        LivingEntity monster = rayTraceExpandedCustomMonsterHitbox(player);
        if (monster != null) {
            player.attack(monster);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && clickMayGrantDiamond(event)) {
            Bukkit.getScheduler().runTask(this, () -> unlockMagicTowerKeyRecipe(player));
        }
        if (event.getWhoClicked() instanceof Player player && clickMayGrantMagicDust(event)) {
            Bukkit.getScheduler().runTask(this, () -> unlockMagicDustRecipes(player));
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof CreateMenu)
                && !(holder instanceof DeleteMenu)
                && !(holder instanceof TowerMenu)
                && !(holder instanceof BlessingMenu)
                && !(holder instanceof DungeonInfoMenu)
                && !(holder instanceof DungeonExitConfirmMenu)
                && !(holder instanceof BestiaryMenu)
                && !(holder instanceof EnemyDetailMenu)
                && !(holder instanceof MagicAnvilMenu)
                && !(holder instanceof MagicGrindstoneMenu)
                && !(holder instanceof TrainingDummyMenu)
                && !(holder instanceof DungeonStarterMenu)
                && !(holder instanceof DungeonWaveDetailMenu)
                && !(holder instanceof DungeonRewardMenu)) {
            return;
        }
        if (holder instanceof DungeonRewardMenu) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder instanceof MagicAnvilMenu) {
            handleMagicAnvilMenuClick(player, event);
            return;
        }
        if (holder instanceof MagicGrindstoneMenu) {
            handleMagicGrindstoneMenuClick(player, event);
            return;
        }
        if (holder instanceof TrainingDummyMenu menu) {
            handleTrainingDummyMenuClick(player, menu, event);
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }
        if (holder instanceof CreateMenu menu) {
            handleCreateMenuClick(player, menu, event.getRawSlot(), event.getCursor());
        } else if (holder instanceof DeleteMenu menu) {
            handleDeleteMenuClick(player, menu, event.getRawSlot());
        } else if (holder instanceof TowerMenu menu) {
            handleTowerMenuClick(player, menu, event.getRawSlot());
        } else if (holder instanceof BlessingMenu) {
            handleBlessingMenuClick(player, event.getRawSlot());
        } else if (holder instanceof DungeonInfoMenu menu) {
            handleDungeonInfoMenuClick(player, menu, event.getRawSlot());
        } else if (holder instanceof DungeonExitConfirmMenu menu) {
            handleDungeonExitConfirmMenuClick(player, menu, event.getRawSlot());
        } else if (holder instanceof BestiaryMenu menu) {
            handleBestiaryMenuClick(player, menu, event.getRawSlot());
        } else if (holder instanceof EnemyDetailMenu menu) {
            handleEnemyDetailMenuClick(player, menu, event.getRawSlot());
        } else if (holder instanceof DungeonStarterMenu menu) {
            handleDungeonStarterMenuClick(player, menu, event.getRawSlot(), event.isRightClick(), event.isShiftClick());
        } else if (holder instanceof DungeonWaveDetailMenu menu) {
            handleDungeonWaveDetailMenuClick(player, menu, event.getRawSlot(), event.isRightClick(), event.isShiftClick());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MagicAnvilMenu)
                && !(event.getInventory().getHolder() instanceof MagicGrindstoneMenu)
                && !(event.getInventory().getHolder() instanceof TrainingDummyMenu)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isMagicAnvilItem(event.getItemInHand())) {
            Block block = event.getBlockPlaced();
            block.setType(MAGIC_ANVIL_CARRIER, false);
            magicAnvils.add(blockKey(block));
            saveMagicAnvils();
            refreshMagicAnvilDisplay(block);
            return;
        }
        if (isMagicGrindstoneItem(event.getItemInHand())) {
            Block block = event.getBlockPlaced();
            block.setType(MAGIC_GRINDSTONE_CARRIER, false);
            magicGrindstones.add(blockKey(block));
            saveMagicGrindstones();
            refreshMagicGrindstoneDisplay(block);
            return;
        }
        if (!isDungeonStarterItem(event.getItemInHand())) {
            return;
        }
        ModuleRecord module = moduleByWorldName(event.getBlockPlaced().getWorld().getName());
        if (module == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("副本启动仪只能放置在模板世界中。");
            return;
        }
        event.getBlockPlaced().setType(DUNGEON_STARTER_CARRIER, false);
        BlockPos previous = module.dungeonStarter();
        if (previous != null) {
            World world = Bukkit.getWorld(module.worldName());
            if (world != null && world.getBlockAt(previous.x(), previous.y(), previous.z()).getType() == DUNGEON_STARTER_CARRIER) {
                removeDungeonStarterDisplay(world, previous);
                world.getBlockAt(previous.x(), previous.y(), previous.z()).setType(Material.AIR, false);
            }
        }
        ModuleRecord updated = module.withDungeonStarter(BlockPos.of(event.getBlockPlaced()));
        modules.put(module.key(), updated);
        saveModules();
        refreshDungeonStarterDisplay(event.getBlockPlaced());
        event.getPlayer().sendMessage("已设置 " + module.displayName() + " 的副本启动仪位置。");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String magicAnvilKey = blockKey(event.getBlock());
        if (magicAnvils.contains(magicAnvilKey)) {
            event.setDropItems(false);
            event.setCancelled(true);
            breakMagicAnvil(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        String magicGrindstoneKey = blockKey(event.getBlock());
        if (magicGrindstones.contains(magicGrindstoneKey)) {
            event.setDropItems(false);
            event.setCancelled(true);
            breakMagicGrindstone(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        ModuleRecord module = moduleByWorldName(event.getBlock().getWorld().getName());
        if (module == null || module.dungeonStarter() == null || !module.dungeonStarter().matches(event.getBlock())) {
            return;
        }
        event.setDropItems(false);
        removeDungeonStarterDisplay(event.getBlock().getWorld(), module.dungeonStarter());
        modules.put(module.key(), module.withDungeonStarter(null));
        saveModules();
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), createDungeonStarterItem(1));
        }
        event.getPlayer().sendMessage("已移除 " + module.displayName() + " 的副本启动仪。");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        boolean magicTowerKey = isMagicTowerKey(event.getItem());
        boolean satietySkillOrb = isSatietySkillOrb(event.getItem());
        if (event.isCancelled() && !magicTowerKey && !satietySkillOrb) {
            return;
        }
        if (satietySkillOrb) {
            event.setCancelled(true);
            useSatietySkillOrb(player);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            if (isMagicAnvilBlock(clicked)) {
                if (shouldPassMagicBlockInteractionToBlockPlacement(event)) {
                    return;
                }
                event.setCancelled(true);
                if (!player.isSneaking()) {
                    refreshMagicAnvilDisplay(clicked);
                    openMagicAnvilMenu(player, clicked);
                }
                return;
            }
            if (isMagicGrindstoneBlock(clicked)) {
                if (shouldPassMagicBlockInteractionToBlockPlacement(event)) {
                    return;
                }
                event.setCancelled(true);
                if (!player.isSneaking()) {
                    refreshMagicGrindstoneDisplay(clicked);
                    openMagicGrindstoneMenu(player, clicked);
                }
                return;
            }
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && handleDungeonStarterInteraction(player, event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (!magicTowerKey) {
            return;
        }
        event.setCancelled(true);
        TowerInstance instance = towerInstancesByWorld.get(player.getWorld().getName());
        if (instance != null && instance.ownerUuid().equals(player.getUniqueId())) {
            ModuleRecord module = modules.get(instance.moduleKey());
            if (module != null) {
                openDungeonInfoMenu(player, module);
            }
            return;
        }
        openTowerMenu(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(this, () -> updateHudTabListWorld(event.getPlayer()));
        ModuleRecord module = moduleByWorldName(event.getFrom().getName());
        if (module != null) {
            Bukkit.getScheduler().runTask(this, () -> {
                World world = Bukkit.getWorld(module.worldName());
                if (world != null && world.getPlayers().isEmpty()) {
                    prepareModuleSnapshot(module, true);
                }
            });
        }
        TowerInstance instance = towerInstancesByWorld.get(event.getFrom().getName());
        if (instance == null || !instance.ownerUuid().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        clearDungeonEffects(event.getPlayer());
        Bukkit.getScheduler().runTask(this, () -> {
            TowerInstance current = towerInstancesByWorld.get(instance.worldName());
            if (current != null && Bukkit.getWorld(instance.worldName()) != null
                    && Bukkit.getWorld(instance.worldName()).getPlayers().isEmpty()) {
                cleanupTowerInstance(instance, false);
            }
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        PendingTowerEntry entry = pendingTowerEntries.get(event.getPlayer().getUniqueId());
        if (entry == null || event.getTo() == null || !towerEntryPositionChanged(event.getFrom(), event.getTo())) {
            return;
        }
        cancelPendingTowerEntry(entry, "副本传送已取消：你移动了。", false);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hudService.clearTabListWorld(player.getUniqueId(), RPG_TAB_WORLD_OWNER);
        stopCustomBlockBreak(player);
        satietySkillOrbCooldowns.remove(player.getUniqueId());
        restorePlayerViewDistance(player, "quit");
        PendingTowerEntry entry = pendingTowerEntries.get(player.getUniqueId());
        if (entry != null) {
            cancelPendingTowerEntry(entry, null, false);
        }
        if (isModuleWorld(player.getWorld())) {
            rememberModuleSession(player);
            return;
        }
        TowerInstance instance = towerInstancesByWorld.get(event.getPlayer().getWorld().getName());
        if (instance == null || !instance.ownerUuid().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        clearDungeonEffects(event.getPlayer());
        event.getPlayer().teleport(instance.returnLocation());
        event.getPlayer().setGameMode(instance.returnGameMode());
        cleanupTowerInstance(instance, false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String deathWorldName = lastDeathWorldNames.remove(player.getUniqueId());
        if (deathWorldName == null) {
            deathWorldName = player.getWorld().getName();
        }
        ModuleRecord module = moduleByWorldName(deathWorldName);
        if (module != null) {
            World moduleWorld = Bukkit.getWorld(module.worldName());
            if (moduleWorld == null) {
                return;
            }
            Location spawn = module.spawn(moduleWorld);
            configureWorld(moduleWorld, spawn, module.borderDistance());
            event.setRespawnLocation(spawn);
            return;
        }

        TowerInstance instance = towerInstancesByWorld.get(deathWorldName);
        if (instance == null || !instance.ownerUuid().equals(player.getUniqueId())) {
            return;
        }
        if (curseForcedExits.remove(player.getUniqueId())) {
            Location destination = instance.returnLocation().getWorld() == null ? mainWorldSpawn() : instance.returnLocation();
            event.setRespawnLocation(destination);
            Bukkit.getScheduler().runTask(this, () -> {
                TowerInstance current = towerInstancesByPlayer.get(player.getUniqueId());
                if (current == null || !current.worldName().equals(instance.worldName())) {
                    return;
                }
                clearDungeonEffects(player);
                player.setGameMode(current.returnGameMode());
                if (!player.getWorld().equals(destination.getWorld()) || player.getLocation().distanceSquared(destination) > 1.0D) {
                    player.teleport(destination);
                }
                player.sendMessage("你被挣脱诅咒强制送出了副本。");
                cleanupTowerInstance(current, false);
            });
            return;
        }
        ModuleRecord moduleRecord = modules.get(instance.moduleKey());
        World instanceWorld = Bukkit.getWorld(instance.worldName());
        if (moduleRecord == null || instanceWorld == null) {
            return;
        }
        Location spawn = moduleRecord.spawn(instanceWorld);
        configureWorld(instanceWorld, spawn, moduleRecord.borderDistance());
        event.setRespawnLocation(spawn);
        Bukkit.getScheduler().runTask(this, () -> callPotionEffectsPlugin(
                "applyRebirthBlessing",
                player,
                REBIRTH_BLESSING_DURATION_MILLIS));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getItem().getItemStack().getType() == Material.DIAMOND) {
            Bukkit.getScheduler().runTask(this, () -> unlockMagicTowerKeyRecipe(player));
        }
        if (isMagicDust(event.getItem().getItemStack())) {
            Bukkit.getScheduler().runTask(this, () -> unlockMagicDustRecipes(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE || customBlockDefinitionFor(event.getBlock()) == null) {
            return;
        }
        event.setCancelled(true);
        startCustomBlockBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        stopCustomBlockBreak(event.getPlayer());
    }

    private void startCustomBlockBreak(Player player, Block block) {
        String key = blockKey(block);
        CustomBlockBreakSession existing = customBlockBreakSessions.get(player.getUniqueId());
        if (existing != null && existing.blockKey.equals(key)) {
            return;
        }
        stopCustomBlockBreak(player);
        customBlockBreakSessions.put(player.getUniqueId(), new CustomBlockBreakSession(key, block.getLocation()));
    }

    private void stopCustomBlockBreak(Player player) {
        CustomBlockBreakSession session = customBlockBreakSessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendBlockDamage(session.location, 0.0F);
        }
    }

    private void tickCustomBlockBreaking() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            CustomBlockBreakSession session = customBlockBreakSessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }
            Block block = session.location.getBlock();
            CustomBlockDefinition definition = customBlockDefinitionFor(block);
            Block target = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
            if (definition == null
                    || player.getGameMode() != GameMode.SURVIVAL
                    || !player.getWorld().equals(block.getWorld())
                    || target == null
                    || !target.equals(block)
                    || player.getEyeLocation().distanceSquared(block.getLocation().add(0.5D, 0.5D, 0.5D)) > CUSTOM_BLOCK_BREAK_REACH_SQUARED) {
                stopCustomBlockBreak(player);
                continue;
            }
            session.progress += customBlockService.blockBreakProgressPerTick(player, definition);
            player.sendBlockDamage(session.location, (float) Math.min(1.0D, session.progress));
            if (session.progress >= 1.0D) {
                stopCustomBlockBreak(player);
                breakTrackedCustomBlock(block, true);
            }
        }
    }

    private CustomBlockDefinition customBlockDefinitionFor(Block block) {
        if (isMagicAnvilBlock(block)) {
            return magicAnvilBlockDefinition;
        }
        if (isMagicGrindstoneBlock(block)) {
            return magicGrindstoneBlockDefinition;
        }
        return null;
    }

    private void breakTrackedCustomBlock(Block block, boolean dropItem) {
        if (isMagicAnvilBlock(block)) {
            breakMagicAnvil(block, dropItem);
            return;
        }
        if (isMagicGrindstoneBlock(block)) {
            breakMagicGrindstone(block, dropItem);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> {
            restoreModuleSessionIfNeeded(event.getPlayer());
            unlockMagicTowerKeyRecipeIfHasDiamond(event.getPlayer());
            unlockMagicDustRecipesIfHasMagicDust(event.getPlayer());
            updateHudTabListWorld(event.getPlayer());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (craftingMatrixContainsMagicTowerKey(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (craftingMatrixContainsMagicTowerKey(event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
        if (event.getCurrentItem() != null && isMagicTowerKey(event.getCurrentItem())
                && event.getWhoClicked() instanceof Player player) {
            unlockMagicTowerKeyRecipe(player);
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        CreateMenu menu = pendingCreateNameEdits.remove(event.getPlayer().getUniqueId());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        String value = event.getMessage().trim();
        if (value.isEmpty() || value.length() > 32) {
            event.getPlayer().sendMessage("副本名称长度必须为 1-32 个字符。");
            Bukkit.getScheduler().runTask(this, () -> openCreateMenu(event.getPlayer(), menu));
            return;
        }
        menu.dungeonName = value;
        Bukkit.getScheduler().runTask(this, () -> openCreateMenu(event.getPlayer(), menu));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CreateMenu menu) {
            menu.closed = true;
        } else if (holder instanceof DungeonRewardMenu menu && event.getPlayer() instanceof Player player) {
            saveDungeonRewards(player, menu, event.getInventory());
        } else if (holder instanceof MagicAnvilMenu && event.getPlayer() instanceof Player player) {
            returnMagicAnvilItems(player, event.getInventory());
        } else if (holder instanceof MagicGrindstoneMenu && event.getPlayer() instanceof Player player) {
            returnMagicGrindstoneItems(player, event.getInventory());
        }
    }

    private void handleCreateMenuClick(Player player, CreateMenu menu, int slot, ItemStack cursor) {
        if (slot == SLOT_DECREASE_LARGE) {
            menu.borderDistance = clampBorderDistance(menu.borderDistance - 64);
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_DECREASE_SMALL) {
            menu.borderDistance = clampBorderDistance(menu.borderDistance - 16);
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_INCREASE_SMALL) {
            menu.borderDistance = clampBorderDistance(menu.borderDistance + 16);
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_INCREASE_LARGE) {
            menu.borderDistance = clampBorderDistance(menu.borderDistance + 64);
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_USE_CURRENT_XZ) {
            menu.spawnX = player.getLocation().getBlockX();
            menu.spawnZ = player.getLocation().getBlockZ();
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_RESET_XZ) {
            menu.spawnX = 0;
            menu.spawnZ = 0;
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_CURSE_DECREASE) {
            menu.curseEscapeDeaths = clampCurseEscapeDeaths(menu.curseEscapeDeaths - 1);
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_CURSE_INCREASE) {
            menu.curseEscapeDeaths = clampCurseEscapeDeaths(menu.curseEscapeDeaths + 1);
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_DUNGEON_NAME) {
            pendingCreateNameEdits.put(player.getUniqueId(), menu);
            player.closeInventory();
            player.sendMessage("请在聊天栏输入新的副本名称；输入内容不会发送给其他玩家。");
            return;
        }
        if (slot == SLOT_ICON) {
            Material cursorIcon = iconMaterial(cursor == null ? null : cursor.getType());
            menu.iconMaterial = cursorIcon == null ? nextModuleIcon(menu.iconMaterial) : cursorIcon;
            openCreateMenu(player, menu);
            return;
        }
        if (slot == SLOT_CANCEL) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_CONFIRM) {
            player.closeInventory();
            createModuleWorld(player, menu);
        }
    }

    private void handleDeleteMenuClick(Player player, DeleteMenu menu, int slot) {
        if (slot == 11) {
            player.closeInventory();
            return;
        }
        if (slot == 15) {
            player.closeInventory();
            ModuleRecord module = modules.get(menu.key);
            if (module == null) {
                player.sendMessage("该模板世界已经不存在。");
                return;
            }
            deleteModuleWorld(player, module);
        }
    }

    private void handleTowerMenuClick(Player player, TowerMenu menu, int slot) {
        if (slot == SLOT_TOWER_BLESSING) {
            openBlessingMenu(player);
            return;
        }
        if (slot < 0 || slot >= menu.moduleKeys.size()) {
            return;
        }
        ModuleRecord module = modules.get(menu.moduleKeys.get(slot));
        if (module == null) {
            player.sendMessage("该魔塔副本已经不存在。");
            player.closeInventory();
            return;
        }
        if (!hasModuleEntryAccess(player, module)) {
            player.sendMessage("尚未满足进入 " + module.dungeonName() + " 的条件，需要先通关: " + missingRequirementNames(player, module));
            return;
        }
        player.closeInventory();
        enterTowerInstance(player, module);
    }

    private void handleBlessingMenuClick(Player player, int slot) {
        if (slot == SLOT_BLESSING_NONE) {
            setSelectedBlessing(player, DungeonBlessing.NONE);
            openTowerMenu(player);
            return;
        }
        if (slot == SLOT_BLESSING_ARCHER_BLESSING) {
            setSelectedBlessing(player, DungeonBlessing.ARCHER_BLESSING);
            openTowerMenu(player);
            return;
        }
        if (slot == SLOT_BLESSING_SWORDSMAN_MEMORY) {
            setSelectedBlessing(player, DungeonBlessing.SWORDSMAN_MEMORY);
            openTowerMenu(player);
        }
    }

    private void handleDungeonInfoMenuClick(Player player, DungeonInfoMenu menu, int slot) {
        if (slot == SLOT_DUNGEON_BESTIARY) {
            openBestiaryMenu(player, menu.moduleKey);
        } else if (slot == SLOT_DUNGEON_EXIT) {
            openDungeonExitConfirmMenu(player, menu.moduleKey);
        } else if (slot == SLOT_DUNGEON_CLOSE) {
            player.closeInventory();
        }
    }

    private void handleDungeonExitConfirmMenuClick(Player player, DungeonExitConfirmMenu menu, int slot) {
        if (slot == SLOT_DUNGEON_EXIT_CANCEL) {
            ModuleRecord module = modules.get(menu.moduleKey);
            if (module != null) {
                openDungeonInfoMenu(player, module);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot != SLOT_DUNGEON_EXIT_CONFIRM) {
            return;
        }
        TowerInstance instance = towerInstancesByPlayer.get(player.getUniqueId());
        if (instance == null || !instance.moduleKey().equals(menu.moduleKey)) {
            player.closeInventory();
            player.sendMessage("当前不在该副本中。");
            return;
        }
        player.closeInventory();
        exitTowerInstance(player, true);
    }

    private void handleBestiaryMenuClick(Player player, BestiaryMenu menu, int slot) {
        List<EnemyEntry> enemies = enemiesForModule(menu.moduleKey);
        if (slot < 0 || slot >= enemies.size()) {
            return;
        }
        openEnemyDetailMenu(player, menu.moduleKey, enemies.get(slot));
    }

    private void handleEnemyDetailMenuClick(Player player, EnemyDetailMenu menu, int slot) {
        if (slot == SLOT_ENEMY_DETAIL_BACK) {
            openBestiaryMenu(player, menu.moduleKey);
        } else if (slot == SLOT_ENEMY_DETAIL_CLOSE) {
            player.closeInventory();
        }
    }

    private void handleDungeonStarterMenuClick(Player player, DungeonStarterMenu menu, int slot, boolean rightClick, boolean shiftClick) {
        ModuleRecord module = modules.get(menu.moduleKey);
        if (module == null) {
            player.closeInventory();
            player.sendMessage("当前模板世界已不存在。");
            return;
        }
        if (module.dungeonType() == DungeonType.BOSS) {
            if (slot == SLOT_DUNGEON_REWARDS) {
                openDungeonRewardMenu(player, module);
            }
            return;
        }
        List<DungeonWaveConfig> waves = new ArrayList<>(module.dungeonWaves());
        if (slot >= 10 && slot < 10 + waves.size()) {
            int index = slot - 10;
            if (rightClick) {
                waves.remove(index);
                modules.put(module.key(), module.withDungeonWaves(waves));
                saveModules();
                openDungeonStarterMenu(player, modules.get(module.key()));
                return;
            }
            openDungeonWaveDetailMenu(player, module, index);
            return;
        }
        if (slot == SLOT_ADD_DUNGEON_WAVE && waves.size() < MAX_DUNGEON_WAVES) {
            waves.add(defaultDungeonWave());
            modules.put(module.key(), module.withDungeonWaves(waves));
            saveModules();
            openDungeonStarterMenu(player, modules.get(module.key()));
            return;
        }
        if (slot == SLOT_DUNGEON_REWARDS) {
            openDungeonRewardMenu(player, module);
        }
    }

    private void handleDungeonWaveDetailMenuClick(Player player, DungeonWaveDetailMenu menu, int slot, boolean rightClick, boolean shiftClick) {
        ModuleRecord module = modules.get(menu.moduleKey);
        if (module == null) {
            player.closeInventory();
            player.sendMessage("当前模板世界已不存在。");
            return;
        }
        List<DungeonWaveConfig> waves = new ArrayList<>(module.dungeonWaves());
        if (menu.waveIndex < 0 || menu.waveIndex >= waves.size()) {
            openDungeonStarterMenu(player, module);
            return;
        }
        DungeonWaveConfig wave = waves.get(menu.waveIndex);
        List<DungeonWaveEnemyConfig> enemies = new ArrayList<>(wave.enemies());
        if (slot >= 10 && slot < 10 + enemies.size()) {
            int index = slot - 10;
            int delta = shiftClick ? 5 : 1;
            DungeonWaveEnemyConfig enemy = enemies.get(index);
            int count = enemy.count() + (rightClick ? -delta : delta);
            if (count <= 0) {
                enemies.remove(index);
            } else {
                enemies.set(index, new DungeonWaveEnemyConfig(enemy.type(), clampDungeonWaveCount(count)));
            }
            saveDungeonWaveAndReopen(player, module, waves, menu.waveIndex, new DungeonWaveConfig(enemies, wave.waitSeconds()));
            return;
        }
        if (slot == SLOT_WAVE_ADD_ENEMY) {
            DungeonWaveEnemyConfig added = addEnemyToWave(enemies, ROTTEN_GUARD_TYPE, shiftClick ? 5 : 1);
            saveDungeonWaveAndReopen(player, module, waves, menu.waveIndex, new DungeonWaveConfig(replaceEnemy(enemies, added), wave.waitSeconds()));
            return;
        }
        if (slot == SLOT_WAVE_ADD_GULPER) {
            DungeonWaveEnemyConfig added = addEnemyToWave(enemies, GULPER_TYPE, shiftClick ? 5 : 1);
            saveDungeonWaveAndReopen(player, module, waves, menu.waveIndex, new DungeonWaveConfig(replaceEnemy(enemies, added), wave.waitSeconds()));
            return;
        }
        if (slot == SLOT_WAVE_ADD_PUS_BUG) {
            DungeonWaveEnemyConfig added = addEnemyToWave(enemies, PUS_BUG_TYPE, shiftClick ? 5 : 1);
            saveDungeonWaveAndReopen(player, module, waves, menu.waveIndex, new DungeonWaveConfig(replaceEnemy(enemies, added), wave.waitSeconds()));
            return;
        }
        if (slot == SLOT_WAVE_REMOVE_ENEMY && !enemies.isEmpty()) {
            enemies.removeLast();
            saveDungeonWaveAndReopen(player, module, waves, menu.waveIndex, new DungeonWaveConfig(enemies, wave.waitSeconds()));
            return;
        }
        int waitDelta = switch (slot) {
            case SLOT_WAVE_WAIT_DECREASE_LARGE -> -10;
            case SLOT_WAVE_WAIT_DECREASE_SMALL -> -1;
            case SLOT_WAVE_WAIT_INCREASE_SMALL -> 1;
            case SLOT_WAVE_WAIT_INCREASE_LARGE -> 10;
            default -> 0;
        };
        if (waitDelta != 0) {
            int waitSeconds = clampDungeonWaveWaitSeconds(wave.waitSeconds() + waitDelta);
            saveDungeonWaveAndReopen(player, module, waves, menu.waveIndex, new DungeonWaveConfig(enemies, waitSeconds));
            return;
        }
        if (slot == SLOT_WAVE_DETAIL_BACK) {
            openDungeonStarterMenu(player, module);
        }
    }

    private void openTrainingDummyMenu(Player player, Slime dummy) {
        TrainingDummyMenu menu = new TrainingDummyMenu(dummy.getUniqueId());
        Inventory inventory = Bukkit.createInventory(menu, TRAINING_DUMMY_MENU_SIZE, Component.text("测试木桩配置", NamedTextColor.GOLD));
        renderTrainingDummyMenu(inventory, dummy);
        player.openInventory(inventory);
    }

    private void renderTrainingDummyMenu(Inventory inventory, Slime dummy) {
        double maxHealth = trainingDummyMaxHealth(dummy);
        double armor = trainingDummyArmor(dummy);
        Set<String> tags = trainingDummyTags(dummy);
        inventory.setItem(SLOT_TRAINING_DUMMY_HEALTH_DEC, menuItem(Material.RED_DYE, "生命上限 -10", NamedTextColor.RED,
                List.of("当前: " + formatStat(maxHealth), "按住 Shift 点击为 -100。")));
        inventory.setItem(SLOT_TRAINING_DUMMY_HEALTH_INFO, menuItem(Material.APPLE, "生命上限: " + formatStat(maxHealth), NamedTextColor.GREEN,
                List.of("测试木桩不会死亡。", "该数值用于伤害计算与配置记录。")));
        inventory.setItem(SLOT_TRAINING_DUMMY_HEALTH_INC, menuItem(Material.LIME_DYE, "生命上限 +10", NamedTextColor.GREEN,
                List.of("当前: " + formatStat(maxHealth), "按住 Shift 点击为 +100。")));
        inventory.setItem(SLOT_TRAINING_DUMMY_ARMOR_DEC, menuItem(Material.REDSTONE, "护甲值 -1", NamedTextColor.RED,
                List.of("当前: " + formatStat(armor), "按住 Shift 点击为 -5。")));
        inventory.setItem(SLOT_TRAINING_DUMMY_ARMOR_INFO, menuItem(Material.IRON_CHESTPLATE, "护甲值: " + formatStat(armor), NamedTextColor.AQUA,
                List.of("影响木桩记录到的最终伤害。")));
        inventory.setItem(SLOT_TRAINING_DUMMY_ARMOR_INC, menuItem(Material.EMERALD, "护甲值 +1", NamedTextColor.GREEN,
                List.of("当前: " + formatStat(armor), "按住 Shift 点击为 +5。")));
        inventory.setItem(SLOT_TRAINING_DUMMY_TAG_UNDEAD, trainingDummyTagItem(tags, TRAINING_DUMMY_TAG_UNDEAD, Material.ROTTEN_FLESH, "亡灵"));
        inventory.setItem(SLOT_TRAINING_DUMMY_TAG_ARTHROPOD, trainingDummyTagItem(tags, TRAINING_DUMMY_TAG_ARTHROPOD, Material.SPIDER_EYE, "节肢"));
        inventory.setItem(SLOT_TRAINING_DUMMY_TAG_AQUATIC, trainingDummyTagItem(tags, TRAINING_DUMMY_TAG_AQUATIC, Material.PRISMARINE_SHARD, "水生"));
        inventory.setItem(SLOT_TRAINING_DUMMY_RECLAIM, menuItem(Material.BARRIER, "收回测试木桩", NamedTextColor.RED,
                List.of("删除这个测试木桩及其展示血条。")));
    }

    private ItemStack trainingDummyTagItem(Set<String> tags, String tag, Material material, String displayName) {
        boolean enabled = tags.contains(tag);
        return menuItem(enabled ? Material.LIME_CONCRETE : material,
                displayName + "标签: " + (enabled ? "已启用" : "未启用"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of("点击切换该生物类型标签。", "标签会影响对应克制附魔的伤害统计。"));
    }

    private void handleTrainingDummyMenuClick(Player player, TrainingDummyMenu menu, InventoryClickEvent event) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }
        Entity entity = Bukkit.getEntity(menu.dummyUuid);
        if (!isTrainingDummy(entity)) {
            player.closeInventory();
            player.sendMessage("这个测试木桩已经不存在。");
            return;
        }
        Slime dummy = (Slime) entity;
        int slot = event.getRawSlot();
        if (slot == SLOT_TRAINING_DUMMY_RECLAIM) {
            removeTrainingDummyDisplay(dummy.getUniqueId());
            dummy.remove();
            player.closeInventory();
            player.sendMessage("已收回测试木桩。");
            return;
        }
        if (slot == SLOT_TRAINING_DUMMY_HEALTH_DEC || slot == SLOT_TRAINING_DUMMY_HEALTH_INC) {
            double delta = event.isShiftClick() ? 100.0D : 10.0D;
            setTrainingDummyMaxHealth(dummy, trainingDummyMaxHealth(dummy) + (slot == SLOT_TRAINING_DUMMY_HEALTH_INC ? delta : -delta));
        } else if (slot == SLOT_TRAINING_DUMMY_ARMOR_DEC || slot == SLOT_TRAINING_DUMMY_ARMOR_INC) {
            double delta = event.isShiftClick() ? 5.0D : 1.0D;
            setTrainingDummyArmor(dummy, trainingDummyArmor(dummy) + (slot == SLOT_TRAINING_DUMMY_ARMOR_INC ? delta : -delta));
        } else if (slot == SLOT_TRAINING_DUMMY_TAG_UNDEAD) {
            toggleTrainingDummyTag(dummy, TRAINING_DUMMY_TAG_UNDEAD);
        } else if (slot == SLOT_TRAINING_DUMMY_TAG_ARTHROPOD) {
            toggleTrainingDummyTag(dummy, TRAINING_DUMMY_TAG_ARTHROPOD);
        } else if (slot == SLOT_TRAINING_DUMMY_TAG_AQUATIC) {
            toggleTrainingDummyTag(dummy, TRAINING_DUMMY_TAG_AQUATIC);
        } else {
            return;
        }
        renderTrainingDummyMenu(top, dummy);
        syncCustomMonsterHealthDisplay(dummy);
    }

    private void openCreateMenu(Player player, CreateMenu menu) {
        Inventory inventory = Bukkit.createInventory(menu, CREATE_SIZE, Component.text("创建模板世界: " + menu.displayName, NamedTextColor.DARK_AQUA));
        inventory.setItem(SLOT_DECREASE_LARGE, menuItem(Material.REDSTONE_BLOCK, "边界 -64", NamedTextColor.RED, List.of("当前距离: " + menu.borderDistance)));
        inventory.setItem(SLOT_DECREASE_SMALL, menuItem(Material.REDSTONE, "边界 -16", NamedTextColor.RED, List.of("当前距离: " + menu.borderDistance)));
        inventory.setItem(SLOT_BORDER_INFO, menuItem(Material.FILLED_MAP, "边界距离: " + menu.borderDistance, NamedTextColor.GOLD, List.of("世界边界直径: " + (menu.borderDistance * 2), "以重生点为中心。")));
        inventory.setItem(SLOT_INCREASE_SMALL, menuItem(Material.EMERALD, "边界 +16", NamedTextColor.GREEN, List.of("当前距离: " + menu.borderDistance)));
        inventory.setItem(SLOT_INCREASE_LARGE, menuItem(Material.EMERALD_BLOCK, "边界 +64", NamedTextColor.GREEN, List.of("当前距离: " + menu.borderDistance)));
        inventory.setItem(SLOT_USE_CURRENT_XZ, menuItem(Material.ENDER_PEARL, "使用当前位置 X/Z", NamedTextColor.AQUA, List.of("X: " + player.getLocation().getBlockX(), "Z: " + player.getLocation().getBlockZ(), "Y 使用虚空模板默认重生高度。")));
        inventory.setItem(SLOT_RESET_XZ, menuItem(Material.LODESTONE, "重生点回到世界中心", NamedTextColor.YELLOW, List.of("设置为 X: 0 / Z: 0。")));
        inventory.setItem(SLOT_CURSE_DECREASE, menuItem(Material.REDSTONE_TORCH, "挣脱诅咒 -1", NamedTextColor.RED, List.of("当前允许死亡: " + menu.curseEscapeDeaths)));
        inventory.setItem(SLOT_CURSE_INFO, menuItem(Material.RESPAWN_ANCHOR, "挣脱诅咒: " + menu.curseEscapeDeaths, NamedTextColor.DARK_PURPLE,
                List.of("玩家进入副本后可死亡 " + menu.curseEscapeDeaths + " 次。", "第 " + (menu.curseEscapeDeaths + 1) + " 次死亡会被强制送出副本。")));
        inventory.setItem(SLOT_CURSE_INCREASE, menuItem(Material.SOUL_TORCH, "挣脱诅咒 +1", NamedTextColor.GREEN, List.of("当前允许死亡: " + menu.curseEscapeDeaths)));
        inventory.setItem(SLOT_SPAWN_INFO, menuItem(Material.COMPASS, "重生点: X " + menu.spawnX + " / Z " + menu.spawnZ, NamedTextColor.WHITE, List.of("Y: " + defaultSpawnY(), "脚下会生成唯一的基岩落脚点。")));
        inventory.setItem(SLOT_ICON, menuItem(menu.iconMaterial, "副本图标: " + menu.iconMaterial.name().toLowerCase(Locale.ROOT), NamedTextColor.AQUA, List.of("用鼠标拿着物品点击此处可设为图标。", "空手点击会在常用图标中轮换。")));
        inventory.setItem(SLOT_CONFIRM, menuItem(Material.LIME_CONCRETE, "确认创建", NamedTextColor.GREEN, List.of("创建并登记新的模板世界。")));
        inventory.setItem(SLOT_CANCEL, menuItem(Material.BARRIER, "取消", NamedTextColor.RED, List.of("不创建模板世界。")));
        menu.closed = false;
        player.openInventory(inventory);
        inventory.setItem(SLOT_DUNGEON_NAME, menuItem(Material.NAME_TAG, "副本名称: " + menu.dungeonName, NamedTextColor.LIGHT_PURPLE, List.of("点击后在聊天栏输入新的副本名称。", "该名称会显示在魔塔密钥页面。")));
    }

    private void openDeleteMenu(Player player, DeleteMenu menu) {
        ModuleRecord module = modules.get(menu.key);
        String displayName = module == null ? menu.key : module.displayName();
        Inventory inventory = Bukkit.createInventory(menu, DELETE_SIZE, Component.text("删除模板世界: " + displayName, NamedTextColor.DARK_RED));
        inventory.setItem(11, menuItem(Material.BARRIER, "取消", NamedTextColor.GRAY, List.of("保留模板世界。")));
        inventory.setItem(15, menuItem(Material.RED_CONCRETE, "确认删除", NamedTextColor.RED, List.of("将卸载并删除模板世界目录。", "该操作不可撤销。")));
        player.openInventory(inventory);
    }

    private void openTowerMenu(Player player) {
        List<ModuleRecord> towers = towerModules();
        List<ModuleRecord> shownTowers = towers.stream().limit(TOWER_MENU_SIZE - 1L).toList();
        TowerMenu menu = new TowerMenu(shownTowers.stream().map(ModuleRecord::key).toList());
        Inventory inventory = Bukkit.createInventory(menu, TOWER_MENU_SIZE, Component.text("魔塔副本", NamedTextColor.DARK_PURPLE));
        for (int i = 0; i < shownTowers.size(); i++) {
            ModuleRecord module = shownTowers.get(i);
            List<String> lore = new ArrayList<>();
            boolean accessible = hasModuleEntryAccess(player, module);
            if (accessible) {
                lore.add("点击进入临时副本。");
            } else {
                lore.add("尚未满足准入条件。");
                lore.add("需要先通关: " + missingRequirementNames(player, module));
            }
            inventory.setItem(i, menuItem(accessible ? module.iconMaterial() : Material.BARRIER,
                    module.dungeonName(), accessible ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED, lore));
        }
        DungeonBlessing blessing = selectedBlessing(player);
        inventory.setItem(SLOT_TOWER_BLESSING, menuItem(blessing.icon(), "祝福选择: " + blessing.displayName(), NamedTextColor.AQUA,
                List.of("点击选择进入副本时携带的祝福。", "当前: " + blessing.displayName())));
        if (towers.isEmpty()) {
            inventory.setItem(22, menuItem(Material.BARRIER, "暂无魔塔副本", NamedTextColor.RED,
                    List.of("当前没有以 tower_ 开头的模板世界。")));
        }
        player.openInventory(inventory);
    }

    private void openBlessingMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new BlessingMenu(), BLESSING_MENU_SIZE, Component.text("祝福选择", NamedTextColor.DARK_AQUA));
        DungeonBlessing selected = selectedBlessing(player);
        inventory.setItem(SLOT_BLESSING_NONE, blessingItem(DungeonBlessing.NONE, selected));
        inventory.setItem(SLOT_BLESSING_ARCHER_BLESSING, blessingItem(DungeonBlessing.ARCHER_BLESSING, selected));
        inventory.setItem(SLOT_BLESSING_SWORDSMAN_MEMORY, blessingItem(DungeonBlessing.SWORDSMAN_MEMORY, selected));
        player.openInventory(inventory);
    }

    private ItemStack blessingItem(DungeonBlessing blessing, DungeonBlessing selected) {
        List<String> lore = new ArrayList<>();
        lore.addAll(blessing.lore());
        lore.add(blessing == selected ? "当前已选择。" : "点击选择该祝福。");
        return menuItem(blessing.icon(), blessing.displayName(), blessing == selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW, lore);
    }

    private void openDungeonInfoMenu(Player player, ModuleRecord module) {
        DungeonInfoMenu menu = new DungeonInfoMenu(module.key());
        Inventory inventory = Bukkit.createInventory(menu, DUNGEON_INFO_SIZE, Component.text(module.dungeonName(), NamedTextColor.DARK_PURPLE));
        inventory.setItem(SLOT_DUNGEON_EXIT, menuItem(Material.IRON_DOOR, "离开副本", NamedTextColor.YELLOW,
                List.of("返回主服务器。", "需要再次确认。")));
        inventory.setItem(SLOT_DUNGEON_BESTIARY, menuItem(Material.WRITABLE_BOOK, "怪物图鉴", NamedTextColor.GOLD,
                List.of("查看本层可能遭遇的敌人。")));
        inventory.setItem(SLOT_DUNGEON_CLOSE, menuItem(Material.BARRIER, "关闭", NamedTextColor.RED,
                List.of("关闭副本介绍。")));
        player.openInventory(inventory);
    }

    private void openDungeonExitConfirmMenu(Player player, String moduleKey) {
        ModuleRecord module = modules.get(moduleKey);
        String title = module == null ? "离开副本" : "离开 " + module.dungeonName();
        Inventory inventory = Bukkit.createInventory(new DungeonExitConfirmMenu(moduleKey), DUNGEON_EXIT_CONFIRM_SIZE,
                Component.text(title, NamedTextColor.DARK_RED));
        inventory.setItem(SLOT_DUNGEON_EXIT_CANCEL, menuItem(Material.ARROW, "返回", NamedTextColor.YELLOW,
                List.of("返回副本介绍。")));
        inventory.setItem(SLOT_DUNGEON_EXIT_CONFIRM, menuItem(Material.REDSTONE_BLOCK, "确认离开", NamedTextColor.RED,
                List.of("结束当前副本并返回主服务器。")));
        player.openInventory(inventory);
    }

    private void openBestiaryMenu(Player player, String moduleKey) {
        ModuleRecord module = modules.get(moduleKey);
        String title = module == null ? "怪物图鉴" : module.dungeonName() + " 图鉴";
        List<EnemyEntry> enemies = enemiesForModule(moduleKey);
        BestiaryMenu menu = new BestiaryMenu(moduleKey);
        Inventory inventory = Bukkit.createInventory(menu, BESTIARY_SIZE, Component.text(title, NamedTextColor.DARK_GREEN));
        for (int i = 0; i < Math.min(enemies.size(), BESTIARY_SIZE); i++) {
            EnemyEntry enemy = enemies.get(i);
            inventory.setItem(i, menuItem(enemy.icon(), enemy.displayName(), NamedTextColor.DARK_GREEN,
                    List.of("点击查看敌人情报。")));
        }
        if (enemies.isEmpty()) {
            inventory.setItem(13, menuItem(Material.BARRIER, "暂无敌人记录", NamedTextColor.GRAY,
                    List.of("当前副本尚未登记敌人。")));
        }
        player.openInventory(inventory);
    }

    private void openEnemyDetailMenu(Player player, String moduleKey, EnemyEntry enemy) {
        EnemyDetailMenu menu = new EnemyDetailMenu(moduleKey, enemy.type());
        Inventory inventory = Bukkit.createInventory(menu, ENEMY_DETAIL_SIZE, Component.text(enemy.displayName(), NamedTextColor.DARK_GREEN));
        BossConfig bossConfig = bossConfigForEnemy(moduleKey, enemy.type());
        double maxHealth = bossConfig == null ? enemy.maxHealth() : bossConfig.maxHealth();
        double attackDamage = bossConfig == null ? enemy.attackDamage() : bossConfig.attackDamage();
        double armor = bossConfig == null ? enemy.armor() : bossConfig.armor();
        double allDamageReduction = bossConfig == null ? enemy.allDamageReduction() : bossConfig.allDamageReduction();
        double movementSpeed = bossConfig == null ? enemy.movementSpeed() : bossConfig.movementSpeed();
        List<String> lore = new ArrayList<>();
        lore.add("生命值: " + formatStat(maxHealth));
        lore.add("攻击力: " + formatStat(attackDamage));
        lore.add("护甲值: " + formatStat(armor));
        if (allDamageReduction > 0.0D) {
            lore.add("全类型伤害减免: " + formatPercent(allDamageReduction * 100.0D));
        }
        lore.add("移速: " + formatStat(movementSpeed));
        if (enemy.undead()) {
            lore.add("亡灵生物");
        }
        if (enemy.arthropod()) {
            lore.add("节肢生物");
        }
        if (PUS_BUG_TYPE.equals(enemy.type())) {
            lore.add("死亡爆裂: " + formatStat(PUS_BUG_EXPLOSION_DAMAGE) + " 物理伤害");
            lore.add("爆裂与脓液会施加中毒 III");
        }
        if (GULPER_TYPE.equals(enemy.type())) {
            lore.add("每秒自然流失 " + formatStat(GULPER_HEALTH_DECAY_PER_SECOND) + " 生命值。");
            lore.add("攻击会优先扣除 4 点饱和度，再扣除饱食度。");
            lore.add("若目标饱和度和饱食度均耗尽，则额外扣除 1 点生命。");
            lore.add("每次命中目标后恢复 " + formatStat(GULPER_ATTACK_HEAL) + " 点生命。");
        }
        if (FERRYMAN_TYPE.equals(enemy.type())) {
            lore.add("完全免疫击退。");
            lore.add("使用普通近战追击玩家。");
            lore.add("斗笠下已无可辨认的面容，只剩留客于塔的执念。");
        }
        inventory.setItem(SLOT_ENEMY_DETAIL_INFO, menuItem(enemy.icon(), enemy.displayName(), NamedTextColor.DARK_GREEN, lore));
        inventory.setItem(SLOT_ENEMY_DETAIL_BACK, menuItem(Material.ARROW, "返回图鉴", NamedTextColor.YELLOW,
                List.of("返回怪物图鉴。")));
        inventory.setItem(SLOT_ENEMY_DETAIL_CLOSE, menuItem(Material.BARRIER, "关闭", NamedTextColor.RED,
                List.of("关闭怪物详情。")));
        player.openInventory(inventory);
    }

    private void openDungeonStarterMenu(Player player, ModuleRecord module) {
        DungeonStarterMenu menu = new DungeonStarterMenu(module.key());
        Inventory inventory = Bukkit.createInventory(menu, DUNGEON_STARTER_CONFIG_SIZE, Component.text("副本启动仪: " + module.displayName(), NamedTextColor.DARK_PURPLE));
        if (module.dungeonType() == DungeonType.BOSS) {
            BossConfig boss = module.bossConfig();
            EnemyEntry entry = enemyEntryByType(boss.type());
            Material icon = entry == null ? Material.SOUL_LANTERN : entry.icon();
            inventory.setItem(13, menuItem(icon, "Boss模式: " + boss.displayName(), NamedTextColor.LIGHT_PURPLE,
                    List.of("生命值: " + formatStat(boss.maxHealth()),
                            "全类型伤害减免: " + formatPercent(boss.allDamageReduction() * 100.0D),
                            "护甲值: " + formatStat(boss.armor()),
                            "攻击力: " + formatStat(boss.attackDamage()))));
            inventory.setItem(SLOT_DUNGEON_REWARDS, menuItem(Material.CHEST, "通关奖励", NamedTextColor.GOLD,
                    List.of("点击配置当前副本的通关奖励。", "当前奖励: " + module.dungeonRewards().size() + " 件物品")));
            player.openInventory(inventory);
            return;
        }
        List<DungeonWaveConfig> waves = module.dungeonWaves();
        for (int i = 0; i < waves.size(); i++) {
            DungeonWaveConfig wave = waves.get(i);
            inventory.setItem(10 + i, menuItem(Material.ROTTEN_FLESH, "第 " + (i + 1) + " 波", NamedTextColor.GOLD,
                    List.of("敌人: " + summarizeWaveEnemies(wave), "下一波等待: " + wave.waitSeconds() + " 秒", "左键打开详细配置。", "右键删除该波次。")));
        }
        inventory.setItem(SLOT_ADD_DUNGEON_WAVE, menuItem(Material.LIME_DYE, "添加一波", NamedTextColor.GREEN,
                List.of("最多 " + MAX_DUNGEON_WAVES + " 波。")));
        inventory.setItem(SLOT_REMOVE_DUNGEON_WAVE, menuItem(Material.BOOK, "波次配置", NamedTextColor.AQUA,
                List.of("左键进入波次详情。", "右键直接删除对应波次。")));
        inventory.setItem(SLOT_DUNGEON_REWARDS, menuItem(Material.CHEST, "通关奖励", NamedTextColor.GOLD,
                List.of("点击配置当前副本的通关奖励。", "当前奖励: " + module.dungeonRewards().size() + " 件物品")));
        player.openInventory(inventory);
    }

    private void openDungeonRewardMenu(Player player, ModuleRecord module) {
        DungeonRewardMenu menu = new DungeonRewardMenu(module.key());
        Inventory inventory = Bukkit.createInventory(menu, DUNGEON_REWARD_SIZE, Component.text("通关奖励: " + module.displayName(), NamedTextColor.GOLD));
        List<ItemStack> rewards = module.dungeonRewards();
        for (int i = 0; i < Math.min(rewards.size(), inventory.getSize()); i++) {
            inventory.setItem(i, rewards.get(i).clone());
        }
        player.openInventory(inventory);
    }

    private void saveDungeonRewards(Player player, DungeonRewardMenu menu, Inventory inventory) {
        List<ItemStack> rewards = nonEmptyItems(inventory);
        ModuleRecord module = modules.get(menu.moduleKey);
        if (module == null) {
            returnItemsToPlayer(player, rewards);
            player.sendMessage("当前模板世界已不存在，奖励物品已返还。");
            return;
        }
        modules.put(module.key(), module.withDungeonRewards(rewards));
        saveModules();
        player.sendMessage("已保存 " + module.displayName() + " 的通关奖励。");
    }

    private void openDungeonWaveDetailMenu(Player player, ModuleRecord module, int waveIndex) {
        if (waveIndex < 0 || waveIndex >= module.dungeonWaves().size()) {
            openDungeonStarterMenu(player, module);
            return;
        }
        DungeonWaveConfig wave = module.dungeonWaves().get(waveIndex);
        DungeonWaveDetailMenu menu = new DungeonWaveDetailMenu(module.key(), waveIndex);
        Inventory inventory = Bukkit.createInventory(menu, DUNGEON_WAVE_DETAIL_SIZE, Component.text("第 " + (waveIndex + 1) + " 波配置", NamedTextColor.DARK_PURPLE));
        for (int i = 0; i < Math.min(wave.enemies().size(), 7); i++) {
            DungeonWaveEnemyConfig enemy = wave.enemies().get(i);
            EnemyEntry entry = enemyEntryByType(enemy.type());
            Material icon = entry == null ? Material.ROTTEN_FLESH : entry.icon();
            String name = entry == null ? enemy.type() : entry.displayName();
            inventory.setItem(10 + i, menuItem(icon, name + " x " + enemy.count(), NamedTextColor.GOLD,
                    List.of("左键数量 +1，右键数量 -1。", "按住 Shift 每次调整 5。", "数量降至 0 时删除该敌人。")));
        }
        if (wave.enemies().isEmpty()) {
            inventory.setItem(13, menuItem(Material.BARRIER, "暂无敌人", NamedTextColor.GRAY,
                    List.of("点击下方按钮添加敌人。")));
        }
        inventory.setItem(SLOT_WAVE_WAIT_DECREASE_LARGE, menuItem(Material.REDSTONE_BLOCK, "等待 -10 秒", NamedTextColor.RED,
                List.of("当前: " + wave.waitSeconds() + " 秒。")));
        inventory.setItem(SLOT_WAVE_WAIT_DECREASE_SMALL, menuItem(Material.REDSTONE, "等待 -1 秒", NamedTextColor.RED,
                List.of("当前: " + wave.waitSeconds() + " 秒。")));
        inventory.setItem(SLOT_WAVE_WAIT_INFO, menuItem(Material.CLOCK, "下一波等待: " + wave.waitSeconds() + " 秒", NamedTextColor.AQUA,
                List.of("当前波次清空后，等待该时间进入下一波。")));
        inventory.setItem(SLOT_WAVE_WAIT_INCREASE_SMALL, menuItem(Material.EMERALD, "等待 +1 秒", NamedTextColor.GREEN,
                List.of("当前: " + wave.waitSeconds() + " 秒。")));
        inventory.setItem(SLOT_WAVE_WAIT_INCREASE_LARGE, menuItem(Material.EMERALD_BLOCK, "等待 +10 秒", NamedTextColor.GREEN,
                List.of("当前: " + wave.waitSeconds() + " 秒。")));
        inventory.setItem(SLOT_WAVE_ADD_ENEMY, menuItem(Material.SPAWNER, "添加朽败卫兵", NamedTextColor.GREEN,
                List.of("添加 1 个朽败卫兵。", "按住 Shift 添加 5 个。")));
        inventory.setItem(SLOT_WAVE_ADD_GULPER, menuItem(Material.SCULK_SHRIEKER, "添加啜食者", NamedTextColor.GREEN,
                List.of("添加 1 个啜食者。", "按住 Shift 添加 5 个。")));
        inventory.setItem(SLOT_WAVE_ADD_PUS_BUG, menuItem(Material.SPIDER_EYE, "添加脓包虫", NamedTextColor.GREEN,
                List.of("添加 1 个脓包虫。", "按住 Shift 添加 5 个。")));
        inventory.setItem(SLOT_WAVE_REMOVE_ENEMY, menuItem(Material.RED_DYE, "删除末尾敌人", NamedTextColor.RED,
                List.of("删除当前列表最后一种敌人。")));
        inventory.setItem(SLOT_WAVE_DETAIL_BACK, menuItem(Material.ARROW, "返回波次列表", NamedTextColor.YELLOW,
                List.of("返回副本启动仪主页面。")));
        player.openInventory(inventory);
    }

    private List<EnemyEntry> enemiesForModule(String moduleKey) {
        ModuleRecord module = modules.get(moduleKey);
        if (module == null) {
            return List.of();
        }
        Set<String> types = new java.util.LinkedHashSet<>();
        if (module.dungeonType() == DungeonType.BOSS) {
            types.add(normalizeCustomMonsterType(module.bossConfig().type()));
            return types.stream()
                    .map(this::enemyEntryByType)
                    .filter(Objects::nonNull)
                    .toList();
        }
        for (DungeonWaveConfig wave : module.dungeonWaves()) {
            for (DungeonWaveEnemyConfig enemy : wave.enemies()) {
                types.add(normalizeCustomMonsterType(enemy.type()));
            }
        }
        return types.stream()
                .map(this::enemyEntryByType)
                .filter(Objects::nonNull)
                .toList();
    }

    private static EnemyEntry enemyEntryByStaticType(String type) {
        for (EnemyEntry entry : EnemyEntry.values()) {
            if (entry.type().equals(type)) {
                return entry;
            }
        }
        return null;
    }

    private EnemyEntry enemyEntryByType(String type) {
        return enemyEntryByStaticType(normalizeCustomMonsterType(type));
    }

    private BossConfig bossConfigForEnemy(String moduleKey, String type) {
        ModuleRecord module = modules.get(moduleKey);
        if (module == null || module.dungeonType() != DungeonType.BOSS) {
            return null;
        }
        BossConfig boss = module.bossConfig();
        return normalizeCustomMonsterType(boss.type()).equals(normalizeCustomMonsterType(type)) ? boss : null;
    }

    private String formatStat(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private String summarizeWaveEnemies(DungeonWaveConfig wave) {
        if (wave.enemies().isEmpty()) {
            return "无";
        }
        return wave.enemies().stream()
                .map(enemy -> {
                    EnemyEntry entry = enemyEntryByType(enemy.type());
                    String name = entry == null ? enemy.type() : entry.displayName();
                    return name + " x " + enemy.count();
                })
                .reduce((left, right) -> left + "，" + right)
                .orElse("无");
    }

    private List<ItemStack> nonEmptyItems(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (!isEmptyItem(item)) {
                items.add(item.clone());
            }
        }
        return List.copyOf(items);
    }

    private void returnItemsToPlayer(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void openMagicAnvilMenu(Player player, Block block) {
        MagicAnvilMenu menu = new MagicAnvilMenu(blockKey(block));
        Inventory inventory = Bukkit.createInventory(menu, MAGIC_ANVIL_MENU_SIZE, Component.text("魔法砧", NamedTextColor.DARK_PURPLE));
        menu.inventory = inventory;
        renderMagicAnvilMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderMagicAnvilMenu(Inventory inventory) {
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != MAGIC_ANVIL_MAIN_INPUT_SLOT
                    && slot != MAGIC_ANVIL_SIDE_INPUT_SLOT
                    && slot != MAGIC_ANVIL_OUTPUT_SLOT) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(MAGIC_ANVIL_CONFIRM_SLOT, menuItem(Material.LIME_DYE, "确认", NamedTextColor.GREEN,
                List.of("当前尚未接入实际强化效果。")));
        inventory.setItem(MAGIC_ANVIL_PROBABILITY_SLOT, menuItem(Material.EXPERIENCE_BOTTLE, "概率", NamedTextColor.AQUA,
                List.of("等待放入可用材料后计算。")));
        refreshMagicAnvilDynamicItems(inventory);
    }

    private void refreshMagicAnvilDynamicItems(Inventory inventory) {
        ItemStack main = inventory.getItem(MAGIC_ANVIL_MAIN_INPUT_SLOT);
        inventory.setItem(MAGIC_ANVIL_CONFIRM_SLOT, menuItem(Material.LIME_DYE, "确认强化", NamedTextColor.GREEN,
                List.of("消耗 1 个魔法粉尘尝试强化主槽武器。")));
        inventory.setItem(MAGIC_ANVIL_PROBABILITY_SLOT, magicAnvilProbabilityItem(main));
        inventory.setItem(MAGIC_ANVIL_FORESIGHT_SLOT, magicAnvilForesightItem(main));
    }

    private void handleMagicAnvilMenuClick(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            event.setCancelled(true);
            if (rawSlot == MAGIC_ANVIL_MAIN_INPUT_SLOT || rawSlot == MAGIC_ANVIL_SIDE_INPUT_SLOT) {
                handleMagicAnvilInputSlotClick(player, top, rawSlot);
                refreshMagicAnvilDynamicItems(top);
            } else if (rawSlot == MAGIC_ANVIL_OUTPUT_SLOT) {
                handleMagicAnvilOutputSlotClick(player, top);
            } else if (rawSlot == MAGIC_ANVIL_CONFIRM_SLOT) {
                MagicAnvilMenu menu = top.getHolder() instanceof MagicAnvilMenu magicAnvilMenu ? magicAnvilMenu : null;
                handleMagicAnvilConfirm(player, top, menu);
                refreshMagicAnvilDynamicItems(top);
            }
            return;
        }
        if (!event.isShiftClick() || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        int targetSlot = magicAnvilTargetSlot(current);
        if (targetSlot < 0) {
            return;
        }
        ItemStack remaining = moveIntoMagicAnvilSlot(top, targetSlot, current);
        event.setCurrentItem(isEmptyItem(remaining) ? null : remaining);
        refreshMagicAnvilDynamicItems(top);
        player.updateInventory();
    }

    private void handleMagicAnvilInputSlotClick(Player player, Inventory inventory, int slot) {
        ItemStack current = inventory.getItem(slot);
        ItemStack cursor = player.getItemOnCursor();
        if (isEmptyItem(cursor)) {
            if (!isEmptyItem(current)) {
                player.setItemOnCursor(current);
                inventory.setItem(slot, null);
                player.updateInventory();
            }
            return;
        }
        if (!isValidMagicAnvilInput(slot, cursor)) {
            return;
        }
        ItemStack remaining = moveIntoMagicAnvilSlot(inventory, slot, cursor);
        player.setItemOnCursor(isEmptyItem(remaining) ? null : remaining);
        player.updateInventory();
    }

    private void handleMagicAnvilOutputSlotClick(Player player, Inventory inventory) {
        ItemStack output = inventory.getItem(MAGIC_ANVIL_OUTPUT_SLOT);
        if (isEmptyItem(output) || !isEmptyItem(player.getItemOnCursor())) {
            return;
        }
        player.setItemOnCursor(output);
        inventory.setItem(MAGIC_ANVIL_OUTPUT_SLOT, null);
        player.updateInventory();
    }

    private void handleMagicAnvilConfirm(Player player, Inventory inventory, MagicAnvilMenu menu) {
        ItemStack weapon = inventory.getItem(MAGIC_ANVIL_MAIN_INPUT_SLOT);
        ItemStack dust = inventory.getItem(MAGIC_ANVIL_SIDE_INPUT_SLOT);
        if (!isMagicAnvilEnchantableItem(weapon) || !isMagicDust(dust)) {
            return;
        }
        if (!isEmptyItem(inventory.getItem(MAGIC_ANVIL_OUTPUT_SLOT))) {
            player.sendMessage("请先取走魔法砧输出槽中的物品。");
            return;
        }
        if (isMagicAnvilCustomEnchantMaxed(weapon)) {
            player.sendMessage("这件武器的自定义附魔已达到最高等级。");
            return;
        }
        String enchantId = nextMagicAnvilEnchant(weapon);
        if (enchantId == null) {
            player.sendMessage("这件武器暂时无法通过魔法砧获得新的附魔。");
            return;
        }
        if (!isCustomEnchantServiceAvailable()) {
            player.sendMessage("强化失败：自定义附魔服务暂不可用。");
            return;
        }
        if (menu != null) {
            if (customMonsterTick - menu.lastConfirmTick < MAGIC_ANVIL_CONFIRM_COOLDOWN_TICKS) {
                return;
            }
            menu.lastConfirmTick = customMonsterTick;
        }
        dust.setAmount(dust.getAmount() - 1);
        inventory.setItem(MAGIC_ANVIL_SIDE_INPUT_SLOT, dust.getAmount() <= 0 ? null : dust);

        int failures = magicAnvilFailures(weapon);
        double successPercent = magicAnvilSuccessPercent(weapon);
        if (Math.random() * 100.0D >= successPercent) {
            setMagicAnvilFailures(weapon, failures + 1);
            inventory.setItem(MAGIC_ANVIL_MAIN_INPUT_SLOT, weapon);
            player.sendMessage("强化失败，武器中的魔力痕迹更清晰了。");
            return;
        }

        ItemStack result = weapon.clone();
        int currentLevel = customEnchantLevel(result, enchantId);
        int nextLevel = currentLevel <= 0 ? 1 : currentLevel + 1;
        boolean gainedNewEnchant = currentLevel <= 0;
        if (!applyCustomEnchant(result, enchantId, nextLevel)) {
            player.sendMessage("强化失败：自定义附魔服务暂不可用。");
            setMagicAnvilFailures(weapon, failures + 1);
            inventory.setItem(MAGIC_ANVIL_MAIN_INPUT_SLOT, weapon);
            return;
        }
        clearMagicAnvilFailures(result);
        inventory.setItem(MAGIC_ANVIL_MAIN_INPUT_SLOT, null);
        inventory.setItem(MAGIC_ANVIL_OUTPUT_SLOT, result);
        player.sendMessage("强化成功，物品获得了 " + customEnchantLevelText(enchantId, nextLevel) + "。");
        if (gainedNewEnchant) {
            player.sendMessage("效果：" + customEnchantEffectDescription(enchantId, nextLevel));
        }
    }

    private ItemStack moveIntoMagicAnvilSlot(Inventory inventory, int slot, ItemStack incoming) {
        if (!isValidMagicAnvilInput(slot, incoming)) {
            return incoming;
        }
        ItemStack current = inventory.getItem(slot);
        if (slot == MAGIC_ANVIL_MAIN_INPUT_SLOT) {
            if (!isEmptyItem(current)) {
                return incoming;
            }
            ItemStack placed = incoming.clone();
            placed.setAmount(1);
            inventory.setItem(slot, placed);
            ItemStack remaining = incoming.clone();
            remaining.setAmount(incoming.getAmount() - 1);
            return remaining.getAmount() <= 0 ? null : remaining;
        }
        if (!isEmptyItem(current) && !current.isSimilar(incoming)) {
            return incoming;
        }
        int currentAmount = isEmptyItem(current) ? 0 : current.getAmount();
        int maxStack = Math.min(incoming.getMaxStackSize(), 64);
        int moveAmount = Math.min(incoming.getAmount(), maxStack - currentAmount);
        if (moveAmount <= 0) {
            return incoming;
        }
        ItemStack placed = isEmptyItem(current) ? incoming.clone() : current.clone();
        placed.setAmount(currentAmount + moveAmount);
        inventory.setItem(slot, placed);
        ItemStack remaining = incoming.clone();
        remaining.setAmount(incoming.getAmount() - moveAmount);
        return remaining.getAmount() <= 0 ? null : remaining;
    }

    private int magicAnvilTargetSlot(ItemStack item) {
        if (isMagicAnvilEnchantableItem(item)) {
            return MAGIC_ANVIL_MAIN_INPUT_SLOT;
        }
        if (isMagicDust(item)) {
            return MAGIC_ANVIL_SIDE_INPUT_SLOT;
        }
        return -1;
    }

    private boolean isValidMagicAnvilInput(int slot, ItemStack item) {
        if (slot == MAGIC_ANVIL_MAIN_INPUT_SLOT) {
            return isMagicAnvilEnchantableItem(item);
        }
        if (slot == MAGIC_ANVIL_SIDE_INPUT_SLOT) {
            return isMagicDust(item);
        }
        return false;
    }

    private void returnMagicAnvilItems(Player player, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : List.of(MAGIC_ANVIL_MAIN_INPUT_SLOT, MAGIC_ANVIL_SIDE_INPUT_SLOT, MAGIC_ANVIL_OUTPUT_SLOT)) {
            ItemStack item = inventory.getItem(slot);
            if (!isEmptyItem(item)) {
                items.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        returnItemsToPlayer(player, items);
    }

    private void openMagicGrindstoneMenu(Player player, Block block) {
        MagicGrindstoneMenu menu = new MagicGrindstoneMenu(blockKey(block));
        Inventory inventory = Bukkit.createInventory(menu, MAGIC_GRINDSTONE_MENU_SIZE, Component.text("魔法砂轮", NamedTextColor.DARK_PURPLE));
        menu.inventory = inventory;
        renderMagicGrindstoneMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderMagicGrindstoneMenu(Inventory inventory) {
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != MAGIC_GRINDSTONE_INPUT_SLOT && slot != MAGIC_GRINDSTONE_OUTPUT_SLOT) {
                inventory.setItem(slot, filler);
            }
        }
        refreshMagicGrindstoneDynamicItems(inventory);
    }

    private void refreshMagicGrindstoneDynamicItems(Inventory inventory) {
        ItemStack input = inventory.getItem(MAGIC_GRINDSTONE_INPUT_SLOT);
        inventory.setItem(MAGIC_GRINDSTONE_CONFIRM_SLOT, menuItem(Material.LIME_DYE, "确认清除", NamedTextColor.GREEN,
                List.of("清除主槽物品上的自定义附魔。")));
        inventory.setItem(MAGIC_GRINDSTONE_INFO_SLOT, magicGrindstoneInfoItem(input));
    }

    private void handleMagicGrindstoneMenuClick(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            event.setCancelled(true);
            if (rawSlot == MAGIC_GRINDSTONE_INPUT_SLOT) {
                handleMagicGrindstoneInputSlotClick(player, top);
                refreshMagicGrindstoneDynamicItems(top);
            } else if (rawSlot == MAGIC_GRINDSTONE_OUTPUT_SLOT) {
                handleMagicGrindstoneOutputSlotClick(player, top);
            } else if (rawSlot == MAGIC_GRINDSTONE_CONFIRM_SLOT) {
                handleMagicGrindstoneConfirm(player, top);
                refreshMagicGrindstoneDynamicItems(top);
            }
            return;
        }
        if (!event.isShiftClick() || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (!isMagicGrindstoneInputItem(current) || !isEmptyItem(top.getItem(MAGIC_GRINDSTONE_INPUT_SLOT))) {
            return;
        }
        ItemStack placed = current.clone();
        placed.setAmount(1);
        top.setItem(MAGIC_GRINDSTONE_INPUT_SLOT, placed);
        ItemStack remaining = current.clone();
        remaining.setAmount(current.getAmount() - 1);
        event.setCurrentItem(remaining.getAmount() <= 0 ? null : remaining);
        refreshMagicGrindstoneDynamicItems(top);
        player.updateInventory();
    }

    private void handleMagicGrindstoneInputSlotClick(Player player, Inventory inventory) {
        ItemStack current = inventory.getItem(MAGIC_GRINDSTONE_INPUT_SLOT);
        ItemStack cursor = player.getItemOnCursor();
        if (isEmptyItem(cursor)) {
            if (!isEmptyItem(current)) {
                player.setItemOnCursor(current);
                inventory.setItem(MAGIC_GRINDSTONE_INPUT_SLOT, null);
                player.updateInventory();
            }
            return;
        }
        if (!isMagicGrindstoneInputItem(cursor) || !isEmptyItem(current)) {
            return;
        }
        ItemStack placed = cursor.clone();
        placed.setAmount(1);
        inventory.setItem(MAGIC_GRINDSTONE_INPUT_SLOT, placed);
        ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(remaining.getAmount() <= 0 ? null : remaining);
        player.updateInventory();
    }

    private void handleMagicGrindstoneOutputSlotClick(Player player, Inventory inventory) {
        ItemStack output = inventory.getItem(MAGIC_GRINDSTONE_OUTPUT_SLOT);
        if (isEmptyItem(output) || !isEmptyItem(player.getItemOnCursor())) {
            return;
        }
        player.setItemOnCursor(output);
        inventory.setItem(MAGIC_GRINDSTONE_OUTPUT_SLOT, null);
        player.updateInventory();
    }

    private void handleMagicGrindstoneConfirm(Player player, Inventory inventory) {
        ItemStack input = inventory.getItem(MAGIC_GRINDSTONE_INPUT_SLOT);
        if (isEmptyItem(input)) {
            return;
        }
        if (!isEmptyItem(inventory.getItem(MAGIC_GRINDSTONE_OUTPUT_SLOT))) {
            player.sendMessage("请先取走魔法砂轮输出槽中的物品。");
            return;
        }
        CustomEnchantInfo enchant = firstCustomEnchantInfo(input);
        if (enchant == null) {
            player.sendMessage("这件物品没有可清除的自定义附魔。");
            return;
        }
        if (!isCustomEnchantServiceAvailable()) {
            player.sendMessage("清除失败：自定义附魔服务暂不可用。");
            return;
        }
        ItemStack result = input.clone();
        if (!removeCustomEnchant(result, enchant.id())) {
            player.sendMessage("清除失败：自定义附魔服务暂不可用。");
            return;
        }
        clearMagicAnvilFailures(result);
        inventory.setItem(MAGIC_GRINDSTONE_INPUT_SLOT, null);
        inventory.setItem(MAGIC_GRINDSTONE_OUTPUT_SLOT, result);
        int refund = enchant.level() * MAGIC_GRINDSTONE_REFUND_PER_LEVEL;
        returnItemsToPlayer(player, List.of(createMagicDust(refund)));
        player.sendMessage("已清除 " + customEnchantLevelText(enchant.id(), enchant.level()) + "，返还 " + refund + " 个魔法粉尘。");
    }

    private void returnMagicGrindstoneItems(Player player, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : List.of(MAGIC_GRINDSTONE_INPUT_SLOT, MAGIC_GRINDSTONE_OUTPUT_SLOT)) {
            ItemStack item = inventory.getItem(slot);
            if (!isEmptyItem(item)) {
                items.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        returnItemsToPlayer(player, items);
    }

    private ItemStack magicGrindstoneInfoItem(ItemStack input) {
        CustomEnchantInfo enchant = firstCustomEnchantInfo(input);
        if (enchant == null) {
            return menuItem(Material.EXPERIENCE_BOTTLE, "返还预览", NamedTextColor.AQUA,
                    List.of("放入带自定义附魔的物品后显示返还数量。"));
        }
        int refund = enchant.level() * MAGIC_GRINDSTONE_REFUND_PER_LEVEL;
        return menuItem(Material.EXPERIENCE_BOTTLE, "返还预览", NamedTextColor.AQUA,
                List.of(
                        "将清除: " + customEnchantLevelText(enchant.id(), enchant.level()),
                        "返还: " + refund + " 个魔法粉尘"));
    }

    private boolean isMagicGrindstoneInputItem(ItemStack item) {
        return firstCustomEnchantInfo(item) != null;
    }

    private CustomEnchantInfo firstCustomEnchantInfo(ItemStack item) {
        if (isEmptyItem(item) || !isCustomEnchantServiceAvailable()) {
            return null;
        }
        String first = firstCustomEnchantId(item);
        if (first != null) {
            int level = customEnchantLevel(item, first);
            if (level > 0) {
                return new CustomEnchantInfo(first, level);
            }
        }
        for (String enchantId : CUSTOM_ENCHANT_IDS) {
            int level = customEnchantLevel(item, enchantId);
            if (level > 0) {
                return new CustomEnchantInfo(enchantId, level);
            }
        }
        return null;
    }

    private ItemStack magicAnvilProbabilityItem(ItemStack weapon) {
        if (!isMagicAnvilEnchantableItem(weapon)) {
            return menuItem(Material.EXPERIENCE_BOTTLE, "强化概率", NamedTextColor.AQUA,
                    List.of("放入可耐久物品后显示本次强化成功率。"));
        }
        int failures = magicAnvilFailures(weapon);
        double chance = magicAnvilSuccessPercent(weapon);
        return menuItem(Material.EXPERIENCE_BOTTLE, "强化概率", NamedTextColor.AQUA,
                List.of(
                        "当前成功率: " + formatPercent(chance),
                        "累计失败: " + failures + " 次",
                        "失败后下次成功率 +1%"));
    }

    private ItemStack magicAnvilForesightItem(ItemStack weapon) {
        if (!isMagicAnvilEnchantableItem(weapon)) {
            return menuItem(Material.SPYGLASS, "远视", NamedTextColor.LIGHT_PURPLE,
                    List.of("放入可耐久物品后显示可获得的自定义附魔。"));
        }
        List<String> enchantIds = magicAnvilEnchantListFor(weapon);
        if (enchantIds.isEmpty()) {
            return menuItem(Material.SPYGLASS, "远视", NamedTextColor.LIGHT_PURPLE,
                    List.of("这件物品暂时无法获取自定义附魔。"));
        }
        String existing = existingMagicAnvilCustomEnchant(weapon, enchantIds);
        if (existing != null) {
            int level = customEnchantLevel(weapon, existing);
            if (!isCustomEnchantAtMax(weapon, existing)) {
                return menuItem(Material.SPYGLASS, "远视", NamedTextColor.LIGHT_PURPLE,
                        List.of("下次成功: " + customEnchantLevelText(existing, level + 1)));
            }
            return menuItem(Material.SPYGLASS, "远视", NamedTextColor.LIGHT_PURPLE,
                    List.of("已有自定义附魔已达到最高等级。"));
        }
        List<String> missing = missingCustomEnchants(weapon, enchantIds);
        if (missing.isEmpty()) {
            return menuItem(Material.SPYGLASS, "远视", NamedTextColor.LIGHT_PURPLE,
                    List.of("可用自定义附魔均已达到最高等级"));
        }
        return menuItem(Material.SPYGLASS, "远视", NamedTextColor.LIGHT_PURPLE,
                missing.stream()
                        .map(id -> "可获得: " + customEnchantLevelText(id, 1))
                        .toList());
    }

    private double magicAnvilSuccessPercent(ItemStack weapon) {
        return Math.min(100.0D, MAGIC_ANVIL_BASE_SUCCESS_PERCENT + magicAnvilFailures(weapon) * MAGIC_ANVIL_FAILURE_BONUS_PERCENT);
    }

    private int magicAnvilFailures(ItemStack item) {
        if (isEmptyItem(item) || !item.hasItemMeta()) {
            return 0;
        }
        Integer failures = item.getItemMeta().getPersistentDataContainer().get(magicAnvilFailuresKey, PersistentDataType.INTEGER);
        return failures == null ? 0 : Math.max(0, failures);
    }

    private void setMagicAnvilFailures(ItemStack item, int failures) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(magicAnvilFailuresKey, PersistentDataType.INTEGER, Math.max(0, failures));
        item.setItemMeta(meta);
    }

    private void clearMagicAnvilFailures(ItemStack item) {
        if (isEmptyItem(item) || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(magicAnvilFailuresKey);
        item.setItemMeta(meta);
    }

    private String nextMagicAnvilEnchant(ItemStack weapon) {
        List<String> enchantIds = magicAnvilEnchantListFor(weapon);
        if (enchantIds.isEmpty()) {
            return null;
        }
        String existing = existingMagicAnvilCustomEnchant(weapon, enchantIds);
        if (existing != null) {
            return isCustomEnchantAtMax(weapon, existing) ? null : existing;
        }
        List<String> missing = missingCustomEnchants(weapon, enchantIds);
        if (missing.isEmpty()) {
            return null;
        }
        return missing.get(new Random().nextInt(missing.size()));
    }

    private boolean isMagicAnvilCustomEnchantMaxed(ItemStack weapon) {
        return !magicAnvilEnchantListFor(weapon).isEmpty() && nextMagicAnvilEnchant(weapon) == null;
    }

    private boolean isCustomEnchantAtMax(ItemStack item, String enchantId) {
        int maxLevel = customEnchantMaxLevel(enchantId);
        return maxLevel > 0 && customEnchantLevel(item, enchantId) >= maxLevel;
    }

    private String existingMagicAnvilCustomEnchant(ItemStack weapon, List<String> enchantIds) {
        for (String enchantId : enchantIds) {
            if (customEnchantLevel(weapon, enchantId) > 0) {
                return enchantId;
            }
        }
        return null;
    }

    private List<String> missingCustomEnchants(ItemStack weapon, List<String> enchantIds) {
        return enchantIds.stream()
                .filter(enchantId -> customEnchantLevel(weapon, enchantId) <= 0)
                .filter(enchantId -> isCustomEnchantApplicableToItem(weapon, enchantId))
                .toList();
    }

    private List<String> magicAnvilEnchantListFor(ItemStack item) {
        List<String> itemTypes = magicAnvilItemTypes(item);
        if (itemTypes.isEmpty()) {
            return List.of();
        }
        Set<String> enchantIds = new java.util.LinkedHashSet<>();
        for (String itemType : itemTypes) {
            enchantIds.addAll(magicAnvilEnchantLists.getOrDefault(itemType, List.of()));
        }
        return List.copyOf(enchantIds);
    }

    private List<String> magicAnvilItemTypes(ItemStack item) {
        List<String> itemTypes = new ArrayList<>();
        if (isSword(item)) {
            itemTypes.add(MAGIC_ANVIL_WEAPON_SWORD);
        }
        if (isChestplate(item)) {
            itemTypes.add(MAGIC_ANVIL_ARMOR_CHESTPLATE);
        }
        if (isLeggings(item)) {
            itemTypes.add(MAGIC_ANVIL_ARMOR_LEGGINGS);
        }
        if (isExtendingHandItem(item)) {
            itemTypes.add(MAGIC_ANVIL_ITEM_EXTENDING_HAND);
        }
        if (isMagicAnvilEnchantableItem(item)) {
            itemTypes.add(MAGIC_ANVIL_ITEM_DURABLE);
        }
        return itemTypes;
    }

    private boolean isCustomEnchantApplicableToItem(ItemStack item, String enchantId) {
        return !CUSTOM_ENCHANT_SELF_GROWING.equals(enchantId) || !hasMending(item);
    }

    private String customEnchantDisplayName(String enchantId) {
        if (CUSTOM_ENCHANT_WITHERING_BLADE.equals(enchantId)) {
            return "凋亡之刃";
        }
        if (CUSTOM_ENCHANT_PAIN_BLADE.equals(enchantId)) {
            return "苦痛之刃";
        }
        if (CUSTOM_ENCHANT_SELF_GROWING.equals(enchantId)) {
            return "自生";
        }
        if (CUSTOM_ENCHANT_SATIETY_VIGOR.equals(enchantId)) {
            return "饱腹活力";
        }
        if (CUSTOM_ENCHANT_EXTENDING_HAND.equals(enchantId)) {
            return "延伸之手";
        }
        if (CUSTOM_ENCHANT_STEADY.equals(enchantId)) {
            return "沉稳";
        }
        return enchantId;
    }

    private String customEnchantEffectDescription(String enchantId, int level) {
        if (CUSTOM_ENCHANT_WITHERING_BLADE.equals(enchantId)) {
            int seconds = Math.max(1, 7 * level - 5);
            return "直接命中主目标时施加凋零 " + romanLevel(level) + "，持续 " + seconds + " 秒；不由横扫触发。";
        }
        if (CUSTOM_ENCHANT_PAIN_BLADE.equals(enchantId)) {
            return "直接命中主目标时，目标每有 1 种负面状态，当次直接物理伤害 +" + formatPercent(level * 2.0D)
                    + "；不提升横扫伤害或异常状态伤害。";
        }
        if (CUSTOM_ENCHANT_SELF_GROWING.equals(enchantId)) {
            return "被玩家装备或手持时，每秒恢复 2 点耐久。";
        }
        if (CUSTOM_ENCHANT_SATIETY_VIGOR.equals(enchantId)) {
            return "装备胸甲且饥饿值不低于 60% 时，生命上限 +" + (2 * level) + "。";
        }
        if (CUSTOM_ENCHANT_EXTENDING_HAND.equals(enchantId)) {
            return "手持该物品时，方块与实体交互距离 +" + (2 * level) + " 格。";
        }
        if (CUSTOM_ENCHANT_STEADY.equals(enchantId)) {
            return "装备护腿时免疫击退。";
        }
        return "暂无效果说明。";
    }

    private String customEnchantLevelText(String enchantId, int level) {
        if (customEnchantMaxLevel(enchantId) <= 1) {
            return customEnchantDisplayName(enchantId);
        }
        return customEnchantDisplayName(enchantId) + " " + romanLevel(level);
    }

    private boolean isMagicAnvilEnchantableItem(ItemStack item) {
        return !isEmptyItem(item) && item.getType().getMaxDurability() > 0;
    }

    private boolean isChestplate(ItemStack item) {
        return !isEmptyItem(item) && item.getType().name().endsWith("_CHESTPLATE");
    }

    private boolean isLeggings(ItemStack item) {
        return !isEmptyItem(item) && item.getType().name().endsWith("_LEGGINGS");
    }

    private boolean hasMending(ItemStack item) {
        return !isEmptyItem(item) && item.containsEnchantment(Enchantment.MENDING);
    }

    private String formatPercent(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value) + "%";
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private double elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.2fms", millis);
    }

    private String romanLevel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }

    private void saveDungeonWaveAndReopen(Player player, ModuleRecord module, List<DungeonWaveConfig> waves, int waveIndex, DungeonWaveConfig wave) {
        waves.set(waveIndex, wave);
        ModuleRecord updated = module.withDungeonWaves(waves);
        modules.put(module.key(), updated);
        saveModules();
        openDungeonWaveDetailMenu(player, updated, waveIndex);
    }

    private DungeonWaveEnemyConfig addEnemyToWave(List<DungeonWaveEnemyConfig> enemies, String type, int amount) {
        for (DungeonWaveEnemyConfig enemy : enemies) {
            if (enemy.type().equals(type)) {
                return new DungeonWaveEnemyConfig(type, clampDungeonWaveCount(enemy.count() + amount));
            }
        }
        return new DungeonWaveEnemyConfig(type, clampDungeonWaveCount(amount));
    }

    private List<DungeonWaveEnemyConfig> replaceEnemy(List<DungeonWaveEnemyConfig> enemies, DungeonWaveEnemyConfig replacement) {
        List<DungeonWaveEnemyConfig> updated = new ArrayList<>();
        boolean replaced = false;
        for (DungeonWaveEnemyConfig enemy : enemies) {
            if (enemy.type().equals(replacement.type())) {
                if (replacement.count() > 0) {
                    updated.add(replacement);
                }
                replaced = true;
            } else {
                updated.add(enemy);
            }
        }
        if (!replaced && replacement.count() > 0) {
            updated.add(replacement);
        }
        return updated;
    }

    private void createModuleWorld(Player player, CreateMenu menu) {
        if (modules.containsKey(menu.key)) {
            player.sendMessage("该模板世界已存在。");
            return;
        }
        String worldName = worldPrefix() + menu.key;
        if (Bukkit.getWorld(worldName) != null || new File(Bukkit.getWorldContainer(), worldName).exists()) {
            player.sendMessage("世界目录已存在，无法创建同名模板世界。");
            return;
        }
        World world = Bukkit.createWorld(moduleWorldCreator(worldName));
        if (world == null) {
            player.sendMessage("模板世界创建失败。");
            return;
        }
        Location spawn = findVoidSpawn(world, menu.spawnX, menu.spawnZ);
        configureWorld(world, spawn, menu.borderDistance);
        ModuleRecord module = new ModuleRecord(menu.key, menu.displayName, menu.dungeonName, menu.iconMaterial, worldName, menu.borderDistance,
                spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch(), menu.curseEscapeDeaths,
                null, DungeonType.WAVES, defaultDungeonWaves(), BossConfig.defaultConfig(), List.of(), List.of());
        modules.put(menu.key, module);
        saveModules();
        player.sendMessage("已创建模板世界 " + menu.displayName + "。可使用 /module enter " + menu.displayName + " 进入。");
    }

    private void enterModuleWorld(Player player, ModuleRecord module) {
        if (moduleSnapshotStates.get(module.key()) == ModuleSnapshotState.PREPARING) {
            player.sendMessage("模板世界正在固化，请稍后再进入。");
            return;
        }
        World world = Bukkit.getWorld(module.worldName());
        if (world == null) {
            world = Bukkit.createWorld(moduleWorldCreator(module.worldName()));
        }
        if (world == null) {
            player.sendMessage("模板世界加载失败。");
            return;
        }
        Location spawn = module.spawn(world);
        configureWorld(world, spawn, module.borderDistance());
        if (!isModuleWorld(player.getWorld())) {
            returnLocations.put(player.getUniqueId(), player.getLocation());
            player.getPersistentDataContainer().set(moduleReturnLocationKey, PersistentDataType.STRING, encodeLocation(player.getLocation()));
        }
        if (player.teleport(spawn)) {
            updateHudTabListWorld(player);
            player.sendMessage("已进入模板世界 " + module.displayName() + "。");
            giveDungeonStarterIfMissing(player, module);
        } else {
            player.sendMessage("传送失败。");
        }
    }

    private void giveDungeonStarterIfMissing(Player player, ModuleRecord module) {
        World world = Bukkit.getWorld(module.worldName());
        if (world == null || hasDungeonStarterBlock(world, module)) {
            return;
        }
        player.getInventory().addItem(createDungeonStarterItem(1)).values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("当前模板世界还没有副本启动仪，已自动给予你一个。");
    }

    private boolean hasDungeonStarterBlock(World world, ModuleRecord module) {
        BlockPos pos = module.dungeonStarter();
        return pos != null && world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == DUNGEON_STARTER_CARRIER;
    }

    private void exitModuleWorld(Player player) {
        if (!isModuleWorld(player.getWorld())) {
            return;
        }
        Location destination = returnLocations.remove(player.getUniqueId());
        if (destination == null || destination.getWorld() == null) {
            destination = decodeLocation(player.getPersistentDataContainer().get(moduleReturnLocationKey, PersistentDataType.STRING));
        }
        if (destination == null || destination.getWorld() == null) {
            destination = mainWorldSpawn();
        }
        clearModuleSession(player);
        player.teleport(destination);
        player.sendMessage("已返回主服务器。");
    }

    private void rememberModuleSession(Player player) {
        player.getPersistentDataContainer().set(moduleLastLocationKey, PersistentDataType.STRING, encodeLocation(player.getLocation()));
        Location destination = returnLocations.get(player.getUniqueId());
        if (destination != null && destination.getWorld() != null) {
            player.getPersistentDataContainer().set(moduleReturnLocationKey, PersistentDataType.STRING, encodeLocation(destination));
        }
    }

    private void restoreModuleSessionIfNeeded(Player player) {
        String lastLocationText = player.getPersistentDataContainer().get(moduleLastLocationKey, PersistentDataType.STRING);
        if (lastLocationText == null) {
            return;
        }
        Location lastLocation = decodeLocation(lastLocationText);
        if (lastLocation == null || lastLocation.getWorld() == null || !isModuleWorld(lastLocation.getWorld())) {
            Location fallback = decodeLocation(player.getPersistentDataContainer().get(moduleReturnLocationKey, PersistentDataType.STRING));
            clearModuleSession(player);
            if (fallback != null && fallback.getWorld() != null) {
                player.teleport(fallback);
            }
            return;
        }
        ModuleRecord module = moduleByWorldName(lastLocation.getWorld().getName());
        if (module != null) {
            configureWorld(lastLocation.getWorld(), module.spawn(lastLocation.getWorld()), module.borderDistance());
        }
        player.teleport(lastLocation);
    }

    private void clearModuleSession(Player player) {
        returnLocations.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(moduleReturnLocationKey);
        player.getPersistentDataContainer().remove(moduleLastLocationKey);
    }

    private void deleteModuleWorld(Player player, ModuleRecord module) {
        World world = Bukkit.getWorld(module.worldName());
        if (world != null) {
            Location fallback = mainWorldSpawn();
            for (Player online : new ArrayList<>(world.getPlayers())) {
                online.teleport(fallback);
                online.sendMessage("模板世界已被删除，你已返回主服务器。");
            }
            if (!Bukkit.unloadWorld(world, false)) {
                player.sendMessage("模板世界卸载失败，删除已取消。");
                return;
            }
        }
        Path worldPath = new File(Bukkit.getWorldContainer(), module.worldName()).toPath().toAbsolutePath().normalize();
        if (!isSafeModuleWorldPath(worldPath)) {
            player.sendMessage("模板世界目录校验失败，删除已取消。");
            return;
        }
        try {
            deleteDirectory(worldPath);
        } catch (IOException exception) {
            player.sendMessage("模板世界目录删除失败: " + exception.getMessage());
            return;
        }
        Path snapshotPath = moduleSnapshotPath(module);
        if (isSafeSnapshotPath(snapshotPath)) {
            tryDeleteDirectory(snapshotPath);
            tryDeleteDirectory(moduleSnapshotTempPath(module));
        }
        moduleSnapshotStates.remove(module.key());
        modules.remove(module.key());
        saveModules();
        player.sendMessage("已删除模板世界 " + module.displayName() + "。");
    }

    private void enterTowerInstance(Player player, ModuleRecord module) {
        if (!hasModuleEntryAccess(player, module)) {
            player.sendMessage("尚未满足进入 " + module.dungeonName() + " 的条件，需要先通关: " + missingRequirementNames(player, module));
            return;
        }
        if (pendingTowerInstances.contains(player.getUniqueId())) {
            player.sendMessage("副本正在准备中，请稍候。");
            return;
        }
        if (moduleSnapshotStates.get(module.key()) == ModuleSnapshotState.PREPARING) {
            player.sendMessage("模板世界正在固化，请稍后再进入副本。");
            return;
        }
        Path snapshotPath = moduleSnapshotPath(module);
        if (!Files.exists(snapshotPath)) {
            prepareModuleSnapshot(module, Bukkit.getWorld(module.worldName()) != null);
            player.sendMessage("模板世界快照尚未就绪，已开始准备，请稍后再试。");
            return;
        }
        TowerInstance existing = towerInstancesByPlayer.get(player.getUniqueId());
        if (existing != null) {
            exitTowerInstance(player, false);
        }
        String worldName = instanceWorldPrefix()
                + module.key()
                + "_"
                + player.getUniqueId().toString().replace("-", "")
                + "_"
                + System.currentTimeMillis();
        Path instancePath = new File(Bukkit.getWorldContainer(), worldName).toPath().toAbsolutePath().normalize();
        if (!isSafeTowerInstancePath(instancePath)) {
            player.sendMessage("副本世界目录校验失败。");
            return;
        }
        World staleWorld = Bukkit.getWorld(worldName);
        if (staleWorld != null) {
            if (!staleWorld.getPlayers().isEmpty()) {
                player.sendMessage("旧副本世界仍有人停留，请稍后再试。");
                return;
            }
            Bukkit.unloadWorld(staleWorld, false);
        }
        PendingTowerEntry entry = new PendingTowerEntry(player.getUniqueId(), module.key(), worldName, instancePath,
                player.getLocation(), player.getGameMode(), player.getLocation());
        pendingTowerInstances.add(player.getUniqueId());
        pendingTowerEntries.put(player.getUniqueId(), entry);
        getLogger().info("[Perf] tower-entry countdown-start module=" + module.key()
                + " player=" + player.getName()
                + " world=" + worldName
                + " snapshot=" + snapshotPath.getFileName());
        player.sendMessage("传送将在 3 秒后开始。");
        entry.particleTask = startTowerEntryParticles(player);
        entry.viewDistanceTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            PendingTowerEntry current = pendingTowerEntries.get(player.getUniqueId());
            if (current == entry && player.isOnline()) {
                reducePlayerViewDistance(player, "tower-entry-pre-teleport");
            }
        }, Math.max(1L, TOWER_ENTRY_COUNTDOWN_TICKS - PRE_TELEPORT_VIEW_DISTANCE_LEAD_TICKS));
        entry.countdownTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            PendingTowerEntry current = pendingTowerEntries.get(player.getUniqueId());
            if (current != entry) {
                return;
            }
            entry.countdownComplete = true;
            completeTowerEntryIfReady(player, module, entry);
        }, TOWER_ENTRY_COUNTDOWN_TICKS);
        prepareTowerInstanceAsync(player.getUniqueId(), module, snapshotPath, entry);
    }

    private void prepareTowerInstanceAsync(UUID playerUuid, ModuleRecord module, Path snapshotPath, PendingTowerEntry entry) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long startNanos = System.nanoTime();
            long cleanupStartNanos = startNanos;
            double cleanupMillis = 0.0D;
            try {
                if (Files.exists(entry.instancePath)) {
                    deleteDirectory(entry.instancePath);
                }
                cleanupMillis = elapsedMillis(cleanupStartNanos);
                long copyStartNanos = System.nanoTime();
                copyWorldDirectory(snapshotPath, entry.instancePath);
                Files.deleteIfExists(entry.instancePath.resolve("uid.dat"));
                Files.deleteIfExists(entry.instancePath.resolve("session.lock"));
                deleteDirectory(entry.instancePath.resolve("playerdata"));
                deleteDirectory(entry.instancePath.resolve("advancements"));
                deleteDirectory(entry.instancePath.resolve("stats"));
                entry.copyMillis = elapsedMillis(copyStartNanos);
                entry.prepareMillis = elapsedMillis(startNanos);
            } catch (IOException exception) {
                Bukkit.getScheduler().runTask(this, () -> failPendingTowerEntry(playerUuid, entry, "副本世界复制失败: " + exception.getMessage()));
                return;
            }
            double finalCleanupMillis = cleanupMillis;
            Bukkit.getScheduler().runTask(this, () -> {
                getLogger().info("[Perf] tower-entry instance-copy module=" + module.key()
                        + " player=" + playerUuid
                        + " world=" + entry.worldName
                        + " cleanup=" + formatMillis(finalCleanupMillis)
                        + " copy=" + formatMillis(entry.copyMillis)
                        + " total=" + formatMillis(entry.prepareMillis));
                loadPreparedTowerInstance(playerUuid, module, entry);
            });
        });
    }

    private void loadPreparedTowerInstance(UUID playerUuid, ModuleRecord module, PendingTowerEntry entry) {
        PendingTowerEntry current = pendingTowerEntries.get(playerUuid);
        if (current != entry || entry.cancelled) {
            deleteDirectoryAsync(entry.instancePath, "tower-entry-cancelled-before-load " + entry.worldName);
            return;
        }
        long loadStartNanos = System.nanoTime();
        World world = Bukkit.createWorld(moduleWorldCreator(entry.worldName));
        double createWorldMillis = elapsedMillis(loadStartNanos);
        if (world == null) {
            failPendingTowerEntry(playerUuid, entry, "副本世界加载失败。");
            deleteDirectoryAsync(entry.instancePath, "tower-entry-load-failed " + entry.worldName);
            return;
        }
        long configureStartNanos = System.nanoTime();
        Location spawn = module.spawn(world);
        configureWorld(world, spawn, module.borderDistance());
        double configureMillis = elapsedMillis(configureStartNanos);
        long chunkStartNanos = System.nanoTime();
        world.getChunkAt(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4).load(true);
        double chunkMillis = elapsedMillis(chunkStartNanos);
        long displayStartNanos = System.nanoTime();
        if (module.dungeonStarter() != null) {
            Block starterBlock = world.getBlockAt(module.dungeonStarter().x(), module.dungeonStarter().y(), module.dungeonStarter().z());
            if (starterBlock.getType() == DUNGEON_STARTER_CARRIER) {
                refreshDungeonStarterDisplay(starterBlock);
            }
        }
        double displayMillis = elapsedMillis(displayStartNanos);
        entry.loadMillis = elapsedMillis(loadStartNanos);
        getLogger().info("[Perf] tower-entry world-load module=" + module.key()
                + " player=" + playerUuid
                + " world=" + entry.worldName
                + " createWorld=" + formatMillis(createWorldMillis)
                + " configure=" + formatMillis(configureMillis)
                + " chunk=" + formatMillis(chunkMillis)
                + " display=" + formatMillis(displayMillis)
                + " total=" + formatMillis(entry.loadMillis));
        entry.world = world;
        warmTowerEntryChunks(playerUuid, module, entry, world, spawn);
    }

    private void warmTowerEntryChunks(UUID playerUuid, ModuleRecord module, PendingTowerEntry entry, World world, Location spawn) {
        long warmStartNanos = System.nanoTime();
        int centerChunkX = spawn.getBlockX() >> 4;
        int centerChunkZ = spawn.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -TOWER_ENTRY_PRELOAD_CHUNK_RADIUS; dx <= TOWER_ENTRY_PRELOAD_CHUNK_RADIUS; dx++) {
            for (int dz = -TOWER_ENTRY_PRELOAD_CHUNK_RADIUS; dz <= TOWER_ENTRY_PRELOAD_CHUNK_RADIUS; dz++) {
                futures.add(world.getChunkAtAsync(centerChunkX + dx, centerChunkZ + dz, true));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(this, () -> {
            PendingTowerEntry current = pendingTowerEntries.get(playerUuid);
            if (current != entry || entry.cancelled) {
                deleteDirectoryAsync(entry.instancePath, "tower-entry-cancelled-after-warm " + entry.worldName);
                return;
            }
            entry.warmMillis = elapsedMillis(warmStartNanos);
            entry.worldReady = true;
            getLogger().info("[Perf] tower-entry chunk-warm module=" + module.key()
                    + " player=" + playerUuid
                    + " world=" + entry.worldName
                    + " radius=" + TOWER_ENTRY_PRELOAD_CHUNK_RADIUS
                    + " chunks=" + futures.size()
                    + " success=" + (throwable == null)
                    + " duration=" + formatMillis(entry.warmMillis));
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                completeTowerEntryIfReady(player, module, entry);
            }
        }));
    }

    private void completeTowerEntryIfReady(Player player, ModuleRecord module, PendingTowerEntry entry) {
        PendingTowerEntry current = pendingTowerEntries.get(player.getUniqueId());
        if (current != entry || entry.cancelled || !entry.countdownComplete) {
            return;
        }
        if (!entry.worldReady || entry.world == null) {
            player.sendMessage("副本仍在准备中，请保持不动。");
            getLogger().info("[Perf] tower-entry countdown-finished-waiting module=" + module.key()
                    + " player=" + player.getName()
                    + " world=" + entry.worldName
                    + " elapsed=" + formatMillis(elapsedMillis(entry.createdNanos)));
            return;
        }
        pendingTowerEntries.remove(player.getUniqueId());
        pendingTowerInstances.remove(player.getUniqueId());
        entry.cancelTasks();
        TowerInstance instance = new TowerInstance(player.getUniqueId(), module.key(), entry.worldName, entry.returnLocation, entry.returnGameMode, entry.instancePath);
        towerInstancesByPlayer.put(player.getUniqueId(), instance);
        towerInstancesByWorld.put(entry.worldName, instance);
        Location spawn = module.spawn(entry.world);
        reducePlayerViewDistanceIfNeeded(player, "tower-entry-teleport");
        long teleportStartNanos = System.nanoTime();
        boolean teleported = player.teleport(spawn);
        double teleportMillis = elapsedMillis(teleportStartNanos);
        getLogger().info("[Perf] tower-entry teleport module=" + module.key()
                + " player=" + player.getName()
                + " world=" + entry.worldName
                + " success=" + teleported
                + " teleport=" + formatMillis(teleportMillis)
                + " copy=" + formatMillis(entry.copyMillis)
                + " load=" + formatMillis(entry.loadMillis)
                + " warm=" + formatMillis(entry.warmMillis)
                + " total=" + formatMillis(elapsedMillis(entry.createdNanos)));
        if (teleported) {
            updateHudTabListWorld(player);
            rampRestorePlayerViewDistance(player, "tower-entry-success");
            resetDungeonPlayerState(player);
            player.setGameMode(GameMode.ADVENTURE);
            applyDungeonEffects(player);
            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6F, 1.5F);
            player.sendMessage("已进入 " + module.dungeonName() + "。手持魔塔密钥右键可重新打开副本介绍。");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                TowerInstance currentInstance = towerInstancesByPlayer.get(player.getUniqueId());
                if (player.isOnline() && currentInstance != null && currentInstance.worldName().equals(instance.worldName())) {
                    openDungeonInfoMenu(player, module);
                }
            }, DUNGEON_INFO_MENU_DELAY_TICKS);
        } else {
            restorePlayerViewDistance(player, "tower-entry-teleport-failed");
            towerInstancesByPlayer.remove(player.getUniqueId());
            towerInstancesByWorld.remove(entry.worldName);
            cleanupTowerInstance(instance, false);
            player.sendMessage("副本传送失败。");
        }
    }

    private void failPendingTowerEntry(UUID playerUuid, PendingTowerEntry entry, String message) {
        PendingTowerEntry current = pendingTowerEntries.get(playerUuid);
        if (current != entry) {
            deleteDirectoryAsync(entry.instancePath, "tower-entry-stale " + entry.worldName);
            return;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        cancelPendingTowerEntry(entry, message, false);
        if (player == null && message != null) {
            getLogger().warning(message);
        }
    }

    private void cancelPendingTowerEntry(PendingTowerEntry entry, String message, boolean disable) {
        entry.cancelled = true;
        pendingTowerEntries.remove(entry.playerUuid);
        pendingTowerInstances.remove(entry.playerUuid);
        entry.cancelTasks();
        Player player = Bukkit.getPlayer(entry.playerUuid);
        if (player != null && message != null && !message.isBlank()) {
            player.sendMessage(message);
        }
        if (player != null) {
            restorePlayerViewDistance(player, "tower-entry-cancel");
        }
        if (entry.world != null) {
            long unloadStartNanos = System.nanoTime();
            Bukkit.unloadWorld(entry.world, false);
            getLogger().info("[Perf] tower-entry cancel-unload world=" + entry.worldName
                    + " duration=" + formatMillis(elapsedMillis(unloadStartNanos)));
        }
        if (disable) {
            tryDeleteDirectory(entry.instancePath);
        } else {
            deleteDirectoryAsync(entry.instancePath, "tower-entry-cancel " + entry.worldName);
        }
    }

    private BukkitTask startTowerEntryParticles(Player player) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(145, 65, 255), 1.25F);
        return new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!player.isOnline() || !pendingTowerEntries.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                spawnTowerEntryParticles(player.getLocation(), elapsedTicks, dust);
                elapsedTicks += 5;
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void spawnTowerEntryParticles(Location base, int elapsedTicks, Particle.DustOptions dust) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        double progress = Math.min(1.0D, elapsedTicks / (double) TOWER_ENTRY_COUNTDOWN_TICKS);
        int count = 8 + (int) Math.round(progress * 28.0D);
        double phase = elapsedTicks * 0.28D;
        for (int index = 0; index < count; index++) {
            double angle = phase + index * Math.PI * 2.0D / count;
            double baseRadius = 0.14D + (index % 4) * 0.035D;
            double outward = 0.26D + progress * 0.42D;
            double upward = 0.42D + progress * 0.36D + (index % 3) * 0.05D;
            Location origin = base.clone().add(Math.cos(angle) * baseRadius, 0.06D, Math.sin(angle) * baseRadius);
            world.spawnParticle(Particle.WITCH, origin, 0, Math.cos(angle) * outward, upward, Math.sin(angle) * outward, 0.85D);
            if (index % 3 == 0) {
                Location spark = origin.clone().add(Math.cos(angle) * 0.18D, 0.12D + progress * 0.55D, Math.sin(angle) * 0.18D);
                world.spawnParticle(Particle.DUST, spark, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
            }
        }
    }

    private boolean towerEntryPositionChanged(Location from, Location to) {
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            return true;
        }
        return from.distanceSquared(to) > TOWER_ENTRY_MOVE_CANCEL_DISTANCE_SQUARED;
    }

    private void reducePlayerViewDistance(Player player, String reason) {
        restorePlayerViewDistance(player, "replace");
        PlayerViewDistanceState state = new PlayerViewDistanceState(player.getViewDistance(), player.getSendViewDistance());
        temporaryViewDistances.put(player.getUniqueId(), state);
        applyPlayerViewDistance(player, TEMPORARY_VIEW_DISTANCE, TEMPORARY_VIEW_DISTANCE);
        getLogger().info("[Perf] view-distance reduce plugin=XiceRPG reason=" + reason
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
                getLogger().info("[Perf] view-distance restored plugin=XiceRPG reason=" + reason
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
        getLogger().info("[Perf] view-distance restore-immediate plugin=XiceRPG reason=" + reason
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
            getLogger().warning("[Perf] view-distance apply-failed plugin=XiceRPG player=" + player.getName()
                    + " view=" + safeViewDistance
                    + " send=" + safeSendViewDistance
                    + " error=" + exception.getMessage());
        }
    }

    private void resetDungeonPlayerState(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getDefaultValue());
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private void exitTowerInstance(Player player, boolean message) {
        TowerInstance instance = towerInstancesByPlayer.get(player.getUniqueId());
        if (instance == null) {
            instance = towerInstancesByWorld.get(player.getWorld().getName());
        }
        if (instance == null) {
            clearDungeonEffects(player);
            player.teleport(mainWorldSpawn());
            updateHudTabListWorld(player);
            return;
        }
        clearDungeonEffects(player);
        player.teleport(instance.returnLocation());
        player.setGameMode(instance.returnGameMode());
        updateHudTabListWorld(player);
        if (message) {
            player.sendMessage("已离开魔塔副本。");
        }
        cleanupTowerInstance(instance, false);
    }

    private void cleanupTowerInstance(TowerInstance instance, boolean disable) {
        long startNanos = System.nanoTime();
        towerInstancesByPlayer.remove(instance.ownerUuid());
        towerInstancesByWorld.remove(instance.worldName());
        curseForcedExits.remove(instance.ownerUuid());
        DungeonRun run = dungeonRunsByWorld.remove(instance.worldName());
        if (run != null) {
            removeDungeonRunBars(run);
        }
        World world = Bukkit.getWorld(instance.worldName());
        int movedPlayers = 0;
        double unloadMillis = 0.0D;
        if (world != null) {
            Location fallback = instance.returnLocation().getWorld() == null ? mainWorldSpawn() : instance.returnLocation();
            for (Player player : new ArrayList<>(world.getPlayers())) {
                clearDungeonEffects(player);
                if (player.getUniqueId().equals(instance.ownerUuid())) {
                    player.setGameMode(instance.returnGameMode());
                }
                player.teleport(fallback);
                movedPlayers++;
            }
            if (!disable) {
                long unloadStartNanos = System.nanoTime();
                Bukkit.unloadWorld(world, false);
                unloadMillis = elapsedMillis(unloadStartNanos);
            }
        }
        getLogger().info("[Perf] tower-instance cleanup world=" + instance.worldName()
                + " disable=" + disable
                + " movedPlayers=" + movedPlayers
                + " unload=" + formatMillis(unloadMillis)
                + " beforeDelete=" + formatMillis(elapsedMillis(startNanos)));
        if (disable) {
            getLogger().info("[Perf] tower-instance deferred-delete world=" + instance.worldName()
                    + " reason=server-disable");
        } else {
            deleteDirectoryAsync(instance.worldPath(), "tower-instance-cleanup " + instance.worldName());
        }
    }

    private List<ModuleRecord> towerModules() {
        return modules.values().stream()
                .filter(module -> module.key().startsWith("tower_"))
                .sorted(Comparator.comparing(ModuleRecord::key))
                .toList();
    }

    private boolean hasModuleEntryAccess(Player player, ModuleRecord module) {
        return missingRequiredCompletions(player, module).isEmpty();
    }

    private List<String> missingRequiredCompletions(Player player, ModuleRecord module) {
        if (module.requiredCompletions().isEmpty()) {
            return List.of();
        }
        Set<String> completed = completedModules(player);
        return module.requiredCompletions().stream()
                .filter(required -> !completed.contains(required))
                .toList();
    }

    private String missingRequirementNames(Player player, ModuleRecord module) {
        return missingRequiredCompletions(player, module).stream()
                .map(required -> {
                    ModuleRecord requiredModule = modules.get(required);
                    return requiredModule == null ? required : requiredModule.dungeonName();
                })
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private void startDungeonRun(Player player, TowerInstance instance, ModuleRecord module, Location center) {
        DungeonRun run = new DungeonRun(hudService, instance.worldName(), module.key(), center,
                module.dungeonType(), module.dungeonWaves(), module.bossConfig(), customMonsterTick);
        dungeonRunsByWorld.put(instance.worldName(), run);
        addDungeonBossBarPlayers(run);
        player.sendMessage(module.dungeonName() + " 已启动。");
        player.playSound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 0.8F, 0.9F);
        if (run.isBossDungeon()) {
            spawnDungeonBoss(run);
        } else {
            spawnDungeonWave(run);
        }
    }

    private void tickDungeonRuns(boolean combatTick) {
        for (DungeonRun run : new ArrayList<>(dungeonRunsByWorld.values())) {
            World world = Bukkit.getWorld(run.worldName);
            if (world == null || !towerInstancesByWorld.containsKey(run.worldName)) {
                dungeonRunsByWorld.remove(run.worldName);
                removeDungeonRunBars(run);
                continue;
            }
            addDungeonBossBarPlayers(run);
            if (run.isBossDungeon()) {
                tickDungeonBossRun(run, world);
                continue;
            }
            if (!combatTick) {
                continue;
            }
            run.liveMobs.removeIf(uuid -> {
                Entity entity = Bukkit.getEntity(uuid);
                return entity == null || entity.isDead() || !entity.isValid() || !entity.getWorld().equals(world);
            });
            if (!run.liveMobs.isEmpty()) {
                updateDungeonBossBar(run);
                continue;
            }
            if (run.nextWaveIndex >= run.waves.size()) {
                completeDungeonRun(run, world);
                continue;
            }
            if (run.restStartTick < 0L) {
                run.restStartTick = customMonsterTick;
                run.nextWaveTick = customMonsterTick + currentDungeonWave(run).waitSeconds() * 20L;
                if (run.nextWaveTick > run.restStartTick) {
                    playDungeonRestStartEffect(run.center);
                }
            }
            if (customMonsterTick >= run.nextWaveTick) {
                spawnDungeonWave(run);
            } else {
                playDungeonRestParticles(run.center);
                previewNextDungeonWaveSpawns(run);
                updateDungeonBossBar(run);
            }
        }
    }

    private void tickIdleDungeonSpawnPreviews() {
        for (TowerInstance instance : new ArrayList<>(towerInstancesByWorld.values())) {
            if (dungeonRunsByWorld.containsKey(instance.worldName())) {
                continue;
            }
            World world = Bukkit.getWorld(instance.worldName());
            if (world == null || world.getPlayers().isEmpty()) {
                continue;
            }
            ModuleRecord module = modules.get(instance.moduleKey());
            if (module == null || module.dungeonStarter() == null || module.dungeonType() != DungeonType.WAVES || module.dungeonWaves().isEmpty()) {
                continue;
            }
            BlockPos starter = module.dungeonStarter();
            Block block = world.getBlockAt(starter.x(), starter.y(), starter.z());
            if (block.getType() != DUNGEON_STARTER_CARRIER) {
                continue;
            }
            previewDungeonWaveSpawns(block.getLocation().add(0.5D, 0.0D, 0.5D), module.dungeonWaves().getFirst());
        }
    }

    private void previewNextDungeonWaveSpawns(DungeonRun run) {
        if (run.nextWaveIndex < 0 || run.nextWaveIndex >= run.waves.size()) {
            return;
        }
        previewDungeonWaveSpawns(run.center, run.waves.get(run.nextWaveIndex));
    }

    private void previewDungeonWaveSpawns(Location center, DungeonWaveConfig wave) {
        int totalCount = wave.enemies().stream().mapToInt(DungeonWaveEnemyConfig::count).sum();
        if (totalCount <= 0) {
            return;
        }
        int index = 0;
        for (DungeonWaveEnemyConfig enemy : wave.enemies()) {
            for (int i = 0; i < enemy.count(); i++) {
                playDungeonSpawnPreviewCircle(findDungeonMobSpawn(center, index, totalCount));
                index++;
            }
        }
    }

    private void spawnDungeonWave(DungeonRun run) {
        World world = Bukkit.getWorld(run.worldName);
        if (world == null || run.nextWaveIndex >= run.waves.size()) {
            return;
        }
        int waveIndex = run.nextWaveIndex++;
        DungeonWaveConfig wave = run.waves.get(waveIndex);
        run.currentWaveIndex = waveIndex;
        run.currentWaveInitialMobs = 0;
        run.restStartTick = -1L;
        run.nextWaveTick = -1L;
        int totalCount = wave.enemies().stream().mapToInt(DungeonWaveEnemyConfig::count).sum();
        int spawned = 0;
        for (DungeonWaveEnemyConfig enemy : wave.enemies()) {
            for (int i = 0; i < enemy.count(); i++) {
                Location spawn = findDungeonMobSpawn(run.center, spawned, totalCount);
                Entity spawnedEntity = spawnDungeonEnemy(enemy.type(), spawn);
                if (spawnedEntity != null) {
                    run.liveMobs.add(spawnedEntity.getUniqueId());
                    run.currentWaveInitialMobs++;
                }
                spawned++;
            }
        }
        if (run.currentWaveInitialMobs == 0) {
            run.restStartTick = customMonsterTick;
            run.nextWaveTick = customMonsterTick + wave.waitSeconds() * 20L;
            if (run.nextWaveTick > run.restStartTick) {
                playDungeonRestStartEffect(run.center);
            }
        }
        updateDungeonBossBar(run);
        for (Player player : world.getPlayers()) {
            player.sendMessage("第 " + (waveIndex + 1) + " 波敌人出现了。");
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.45F, 1.2F);
        }
    }

    private Entity spawnDungeonEnemy(String type, Location spawn) {
        if (isCustomMonsterInput(type)) {
            return spawnCustomMonster(normalizeCustomMonsterType(type), spawn);
        }
        return null;
    }

    private void spawnDungeonBoss(DungeonRun run) {
        World world = Bukkit.getWorld(run.worldName);
        if (world == null) {
            return;
        }
        BossConfig boss = run.bossConfig;
        Location spawn = run.center.clone();
        Entity spawnedBoss = isCustomMonsterInput(boss.type()) ? spawnCustomMonster(normalizeCustomMonsterType(boss.type()), spawn) : null;
        if (spawnedBoss instanceof LivingEntity living) {
            applyDungeonBossStats(living, boss, true);
            run.bossUuid = living.getUniqueId();
            updateDungeonBossBar(run);
            for (Player player : world.getPlayers()) {
                player.sendMessage(boss.displayName() + " 出现了。");
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.65F, 0.75F);
            }
            return;
        }
        Zombie entity = world.spawn(spawn, Zombie.class, zombie -> {
            zombie.customName(Component.text(boss.displayName(), NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            zombie.setCustomNameVisible(false);
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(true);
            zombie.setSilent(true);
            zombie.setAI(true);
            applyDungeonBossStats(zombie, boss, true);
            EntityEquipment equipment = zombie.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
                equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                equipment.setItemInMainHandDropChance(0.0F);
                equipment.setHelmetDropChance(0.0F);
                equipment.setChestplateDropChance(0.0F);
                equipment.setLeggingsDropChance(0.0F);
                equipment.setBootsDropChance(0.0F);
            }
        });
        run.bossUuid = entity.getUniqueId();
        updateDungeonBossBar(run);
        for (Player player : world.getPlayers()) {
            player.sendMessage(boss.displayName() + " 出现了。");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.65F, 0.75F);
        }
    }

    private void applyDungeonBossStats(LivingEntity entity, BossConfig boss, boolean heal) {
        setAttribute(entity, Attribute.MAX_HEALTH, boss.maxHealth());
        setAttribute(entity, Attribute.ARMOR, boss.armor());
        setAttribute(entity, Attribute.ATTACK_DAMAGE, boss.attackDamage());
        setAttribute(entity, Attribute.MOVEMENT_SPEED, boss.movementSpeed());
        setAttribute(entity, Attribute.FOLLOW_RANGE, boss.followRange());
        if (FERRYMAN_TYPE.equals(normalizeCustomMonsterType(boss.type()))) {
            setAttribute(entity, Attribute.KNOCKBACK_RESISTANCE, FERRYMAN_KNOCKBACK_RESISTANCE);
        }
        if (heal) {
            entity.setHealth(Math.max(1.0D, Math.min(boss.maxHealth(), maxHealth(entity))));
        }
    }

    private void tickDungeonBossRun(DungeonRun run, World world) {
        LivingEntity boss = run.bossEntity();
        if (boss == null || boss.isDead() || !boss.isValid() || !boss.getWorld().equals(world)) {
            updateDungeonBossBar(run);
            return;
        }
        tickBossPassiveRules(run, world, boss);
        BossConfig config = run.bossConfig;
        applyDungeonBossStats(boss, config, false);
        if (isFerryman(boss)) {
            tickFerrymanBossSkills(run, world, boss);
        }
        if (boss instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            zombie.setSilent(true);
            if (run.activeBossSkill != null || run.bossEnraged) {
                zombie.setAI(false);
                zombie.setTarget(null);
                zombie.setVelocity(new Vector(0.0D, zombie.getVelocity().getY(), 0.0D));
            } else if (zombie.getTarget() instanceof Player target && isValidMonsterTarget(zombie, target)) {
                zombie.setAI(true);
                // Keep its current target when still valid.
            } else {
                zombie.setAI(true);
                zombie.setTarget(nearestMonsterTarget(zombie));
            }
        }
        if (shouldResetDungeonBoss(run, boss)) {
            resetDungeonBossToCenter(run, boss);
        }
        updateDungeonBossBar(run);
    }

    private void tickBossPassiveRules(DungeonRun run, World world, LivingEntity boss) {
        if (!isFerryman(boss)) {
            return;
        }
        if (boss.getLocation().getY() < FERRYMAN_DIMENSION_DISORDER_Y) {
            resetDungeonBossToCenter(run, boss);
        }
        for (Player player : world.getPlayers()) {
            if (!isActiveDungeonPlayer(player) || player.getLocation().getY() >= FERRYMAN_DIMENSION_DISORDER_Y) {
                continue;
            }
            killDungeonPlayer(player);
        }
    }

    private void tickFerrymanBossSkills(DungeonRun run, World world, LivingEntity boss) {
        if (run.bossEnraged) {
            killAllDungeonPlayers(world);
            return;
        }
        long elapsed = customMonsterTick - run.bossStartTick;
        if (elapsed >= FERRYMAN_WASTELAND_ENRAGE_TICKS
                && (run.activeBossSkill == null || run.activeBossSkill.type != BossSkillType.WASTELAND)) {
            startBossSkill(run, boss, BossSkillType.WASTELAND);
        }
        if (run.activeBossSkill == null && customMonsterTick >= run.nextBossSkillTick) {
            BossSkillType type = FERRYMAN_SKILL_SEQUENCE[run.nextBossSkillIndex % FERRYMAN_SKILL_SEQUENCE.length];
            run.nextBossSkillIndex++;
            startBossSkill(run, boss, type);
        }
        if (run.activeBossSkill != null) {
            tickActiveBossSkill(run, world, boss);
        }
    }

    private void startBossSkill(DungeonRun run, LivingEntity boss, BossSkillType type) {
        BossSkillCast cast = new BossSkillCast(type, customMonsterTick);
        if (type == BossSkillType.SOULFIRE) {
            Player target = nearestMonsterTarget(boss);
            if (target == null) {
                run.nextBossSkillTick = customMonsterTick + 20L;
                return;
            }
            cast.targetUuid = target.getUniqueId();
        } else if (type == BossSkillType.WASTELAND) {
            resetDungeonBossToCenter(run, boss);
            run.nextBossSkillTick = Long.MAX_VALUE;
        }
        run.activeBossSkill = cast;
        boss.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        if (boss instanceof Zombie zombie) {
            zombie.setAI(false);
            zombie.setTarget(null);
        }
    }

    private void tickActiveBossSkill(DungeonRun run, World world, LivingEntity boss) {
        BossSkillCast cast = run.activeBossSkill;
        if (cast == null) {
            return;
        }
        boss.setVelocity(new Vector(0.0D, boss.getVelocity().getY(), 0.0D));
        if (cast.type == BossSkillType.FERRY) {
            playFerrymanFerryWarning(boss.getLocation());
            if (cast.age(customMonsterTick) >= FERRYMAN_CAST_TICKS) {
                dealBossPhysicalDamageInRadius(boss, boss.getLocation(), FERRYMAN_FERRY_RADIUS, FERRYMAN_FERRY_DAMAGE);
                playFerrymanFerryReleaseEffect(boss.getLocation());
                finishBossSkill(run);
            }
            return;
        }
        if (cast.type == BossSkillType.SHOCK) {
            if (cast.age(customMonsterTick) >= FERRYMAN_CAST_TICKS) {
                dealBossPhysicalDamageToAll(boss, world, FERRYMAN_SHOCK_DAMAGE);
                finishBossSkill(run);
            }
            return;
        }
        if (cast.type == BossSkillType.WASTELAND) {
            playFerrymanWastelandWarning(world, run.center, cast.age(customMonsterTick));
            if (cast.age(customMonsterTick) >= FERRYMAN_WASTELAND_CAST_TICKS) {
                run.activeBossSkill = null;
                run.bossEnraged = true;
                killAllDungeonPlayers(world);
            }
            return;
        }
        tickSoulfireSkill(run, world, boss, cast);
    }

    private void tickSoulfireSkill(DungeonRun run, World world, LivingEntity boss, BossSkillCast cast) {
        Player target = cast.targetUuid == null ? null : Bukkit.getPlayer(cast.targetUuid);
        if (cast.phase == BossSkillPhase.CASTING) {
            if (target == null || !target.isOnline() || !target.getWorld().equals(world) || target.isDead()) {
                target = nearestMonsterTarget(boss);
                cast.targetUuid = target == null ? null : target.getUniqueId();
            }
            if (target != null) {
                playSoulfireChargeWarning(boss.getLocation(), target.getLocation(), cast.age(customMonsterTick));
            }
            if (cast.age(customMonsterTick) >= FERRYMAN_CAST_TICKS) {
                if (target == null) {
                    finishBossSkill(run);
                    return;
                }
                cast.phase = BossSkillPhase.CHARGING;
                cast.phaseStartedTick = customMonsterTick;
                cast.chargeStart = boss.getLocation().clone();
                cast.chargeEnd = target.getLocation().clone();
                cast.empoweredSoulfire = horizontalDistanceSquared(cast.chargeStart, cast.chargeEnd)
                        > FERRYMAN_SOULFIRE_EMPOWER_DISTANCE * FERRYMAN_SOULFIRE_EMPOWER_DISTANCE;
                cast.hitPlayers.clear();
                playSoulfireChargeStartEffect(cast.chargeStart, cast.chargeEnd, cast.empoweredSoulfire);
            }
            return;
        }
        if (cast.phase == BossSkillPhase.CHARGING) {
            double progress = Math.min(1.0D, (double) cast.phaseAge(customMonsterTick) / FERRYMAN_SOULFIRE_CHARGE_TICKS);
            Location next = interpolateLocation(cast.chargeStart, cast.chargeEnd, progress);
            boss.teleport(next);
            playSoulfireChargeParticles(cast.chargeStart, next, cast.empoweredSoulfire);
            damagePlayersNearSoulfirePath(boss, world, cast, cast.chargeStart, next, cast.targetUuid);
            if (cast.phaseAge(customMonsterTick) >= FERRYMAN_SOULFIRE_CHARGE_TICKS) {
                damagePlayersNearSoulfirePath(boss, world, cast, cast.chargeStart, cast.chargeEnd, cast.targetUuid);
                damageSoulfireImpactTarget(boss, target, cast);
                cast.phase = BossSkillPhase.AFTERSHOCK;
                cast.phaseStartedTick = customMonsterTick;
                playSoulfireSlamWindupStart(boss.getLocation());
            }
            return;
        }
        playFerrymanAftershockWarning(boss.getLocation(), cast.phaseAge(customMonsterTick));
        if (cast.phaseAge(customMonsterTick) >= FERRYMAN_SOULFIRE_AFTERSHOCK_TICKS) {
            dealSoulfireAftershock(world, boss.getLocation());
            finishBossSkill(run);
        }
    }

    private void finishBossSkill(DungeonRun run) {
        run.activeBossSkill = null;
        run.nextBossSkillTick = customMonsterTick + FERRYMAN_SKILL_INTERVAL_TICKS;
    }

    private boolean isBossSkillLocked(UUID bossUuid) {
        DungeonRun run = dungeonRunByBoss(bossUuid);
        return run != null && (run.activeBossSkill != null || run.bossEnraged);
    }

    private boolean shouldResetDungeonBoss(DungeonRun run, LivingEntity boss) {
        Location location = boss.getLocation();
        if (location.getY() < run.center.getY() - DUNGEON_BOSS_FALL_RESET_Y_OFFSET) {
            return true;
        }
        if (!Objects.equals(location.getWorld(), run.center.getWorld())) {
            return true;
        }
        double dx = location.getX() - run.center.getX();
        double dz = location.getZ() - run.center.getZ();
        double maxDistance = DUNGEON_BOSS_PLATFORM_RESET_RADIUS;
        return dx * dx + dz * dz > maxDistance * maxDistance && location.getY() <= run.center.getY() + 2.0D;
    }

    private void resetDungeonBossToCenter(DungeonRun run, LivingEntity boss) {
        Location target = run.center.clone();
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        boss.teleport(target);
        boss.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        boss.setFallDistance(0.0F);
        world.spawnParticle(Particle.PORTAL, target.clone().add(0.0D, 1.0D, 0.0D), 48, 0.6D, 0.9D, 0.6D, 0.05D);
        world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.65F, 0.75F);
    }

    private void dealBossPhysicalDamageInRadius(LivingEntity boss, Location center, double radius, double damage) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (!isActiveDungeonPlayer(player) || player.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            dealBossPhysicalDamage(boss, player, damage);
        }
        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 1.1F, 0.55F);
    }

    private void dealBossPhysicalDamageToAll(LivingEntity boss, World world, double damage) {
        for (Player player : world.getPlayers()) {
            if (isActiveDungeonPlayer(player)) {
                dealBossPhysicalDamage(boss, player, damage);
            }
        }
        world.playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.75F, 0.8F);
    }

    private void dealBossPhysicalDamage(LivingEntity boss, Player player, double damage) {
        player.setNoDamageTicks(0);
        applyingBossSkillDamage = true;
        try {
            player.damage(damage, boss);
        } finally {
            applyingBossSkillDamage = false;
            player.setNoDamageTicks(0);
        }
    }

    private void dealBossMagicDamage(Player player, double damage) {
        if (!isActiveDungeonPlayer(player) || damage <= 0.0D) {
            return;
        }
        player.setNoDamageTicks(0);
        if (player.getHealth() <= damage) {
            player.setHealth(0.0D);
            return;
        }
        player.setHealth(Math.max(0.0D, player.getHealth() - damage));
    }

    private void killAllDungeonPlayers(World world) {
        for (Player player : world.getPlayers()) {
            killDungeonPlayer(player);
        }
    }

    private void killDungeonPlayer(Player player) {
        if (!isActiveDungeonPlayer(player)) {
            return;
        }
        player.setNoDamageTicks(0);
        player.setHealth(0.0D);
    }

    private boolean isActiveDungeonPlayer(Player player) {
        return player != null
                && player.isOnline()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private void damagePlayersNearSoulfirePath(LivingEntity boss, World world, BossSkillCast cast, Location start, Location end, UUID exemptPlayerUuid) {
        Vector a = start.toVector();
        Vector b = end.toVector();
        double widthSquared = FERRYMAN_SOULFIRE_PATH_WIDTH * FERRYMAN_SOULFIRE_PATH_WIDTH;
        for (Player player : world.getPlayers()) {
            if (!isActiveDungeonPlayer(player) || cast.hitPlayers.contains(player.getUniqueId())) {
                continue;
            }
            if (exemptPlayerUuid != null && exemptPlayerUuid.equals(player.getUniqueId())) {
                continue;
            }
            if (distanceSquaredToSegment(player.getLocation().toVector(), a, b) > widthSquared) {
                continue;
            }
            cast.hitPlayers.add(player.getUniqueId());
            dealBossPhysicalDamage(boss, player, cast.soulfirePathDamage());
            applySoulfireEmpoweredKnockback(player, cast);
        }
    }

    private void damageSoulfireImpactTarget(LivingEntity boss, Player target, BossSkillCast cast) {
        if (!isActiveDungeonPlayer(target)) {
            return;
        }
        cast.hitPlayers.add(target.getUniqueId());
        dealBossPhysicalDamage(boss, target, cast.soulfirePathDamage());
        applySoulfireEmpoweredKnockback(target, cast);
    }

    private void applySoulfireEmpoweredKnockback(Player player, BossSkillCast cast) {
        if (!cast.empoweredSoulfire || cast.chargeStart == null || cast.chargeEnd == null) {
            return;
        }
        Vector direction = cast.chargeEnd.toVector().subtract(cast.chargeStart.toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() <= 0.0001D) {
            direction = player.getLocation().toVector().subtract(cast.chargeStart.toVector());
            direction.setY(0.0D);
        }
        if (direction.lengthSquared() <= 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        Vector velocity = direction.normalize().multiply(FERRYMAN_SOULFIRE_EMPOWER_KNOCKBACK);
        velocity.setY(0.65D);
        player.setVelocity(velocity);
    }

    private double distanceSquaredToSegment(Vector point, Vector start, Vector end) {
        Vector segment = end.clone().subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared <= 0.0001D) {
            return point.distanceSquared(start);
        }
        double t = point.clone().subtract(start).dot(segment) / lengthSquared;
        t = Math.max(0.0D, Math.min(1.0D, t));
        Vector projection = start.clone().add(segment.multiply(t));
        return point.distanceSquared(projection);
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        if (first == null || second == null || first.getWorld() != second.getWorld()) {
            return 0.0D;
        }
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private void dealSoulfireAftershock(World world, Location center) {
        double radiusSquared = FERRYMAN_SOULFIRE_AFTERSHOCK_RADIUS * FERRYMAN_SOULFIRE_AFTERSHOCK_RADIUS;
        for (Player player : world.getPlayers()) {
            if (!isActiveDungeonPlayer(player) || player.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            dealBossMagicDamage(player, FERRYMAN_SOULFIRE_AFTERSHOCK_DAMAGE);
            player.setVelocity(player.getVelocity().add(new Vector(0.0D, 1.05D, 0.0D)));
            player.setFireTicks(Math.max(player.getFireTicks(), FERRYMAN_SOULFIRE_BURN_TICKS));
        }
        Location impact = center.clone().add(0.0D, 0.25D, 0.0D);
        world.spawnParticle(Particle.EXPLOSION, impact, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, impact, 96, 1.4D, 0.25D, 1.4D, 0.1D);
        world.spawnParticle(Particle.SOUL, impact.clone().add(0.0D, 0.45D, 0.0D), 72, 1.8D, 0.18D, 1.8D, 0.08D);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.65F);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.65F, 1.35F);
    }

    private Location interpolateLocation(Location start, Location end, double progress) {
        World world = start.getWorld();
        double x = start.getX() + (end.getX() - start.getX()) * progress;
        double y = start.getY() + (end.getY() - start.getY()) * progress;
        double z = start.getZ() + (end.getZ() - start.getZ()) * progress;
        Location location = new Location(world, x, y, z, start.getYaw(), start.getPitch());
        Vector direction = end.toVector().subtract(start.toVector());
        if (end.getWorld() == world && direction.lengthSquared() > 0.0001D) {
            location.setDirection(direction);
        }
        return location;
    }

    private void playFerrymanFerryWarning(Location center) {
        playFilledWarningCircle(center, FERRYMAN_FERRY_RADIUS, Color.fromRGB(255, 228, 72), 0.08D);
    }

    private void playFerrymanFerryReleaseEffect(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location origin = center.clone().add(0.0D, 0.12D, 0.0D);
        for (int i = 0; i < 140; i++) {
            double angle = Math.random() * Math.PI * 2.0D;
            double distance = Math.sqrt(Math.random()) * FERRYMAN_FERRY_RADIUS;
            Location point = origin.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            world.spawnParticle(Particle.SOUL, point, 1, 0.03D, 0.02D, 0.03D, 0.02D);
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, origin.clone().add(0.0D, 0.35D, 0.0D), 48,
                FERRYMAN_FERRY_RADIUS * 0.38D, 0.18D, FERRYMAN_FERRY_RADIUS * 0.38D, 0.04D);
    }

    private void playFerrymanAftershockWarning(Location center, long age) {
        double progress = Math.max(0.0D, Math.min(1.0D, (double) age / FERRYMAN_SOULFIRE_AFTERSHOCK_TICKS));
        double radius = FERRYMAN_SOULFIRE_AFTERSHOCK_RADIUS * (1.12D - progress * 0.12D);
        playFilledWarningCircle(center, radius, Color.fromRGB(255, 130, 38), 0.12D);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location column = center.clone().add(0.0D, 2.8D - progress * 2.0D, 0.0D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, column, 8, 0.22D, 0.18D, 0.22D, 0.035D);
        if (age % 5L == 0L) {
            world.playSound(center, Sound.BLOCK_SOUL_SAND_STEP, 0.45F, 0.65F + (float) progress * 0.35F);
        }
    }

    private void playFerrymanWastelandWarning(World world, Location center, long age) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(112, 52, 132), 1.8F);
        double radius = 4.0D + Math.min(12.0D, age / 12.0D);
        playWarningCircle(center, radius, Color.fromRGB(155, 65, 185), 56, 0.16D);
        world.spawnParticle(Particle.DUST, center.clone().add(0.0D, 1.0D, 0.0D), 18, 1.2D, 1.2D, 1.2D, 0.0D, dust);
        if (age % 20L == 0L) {
            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.1F, 0.55F);
        }
    }

    private void playWarningCircle(Location center, double radius, Color color, int points, double yOffset) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.25F);
        double phase = customMonsterTick % 10L < 5L ? 0.0D : Math.PI / points;
        Location origin = center.clone().add(0.0D, yOffset, 0.0D);
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            Location point = origin.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
        }
    }

    private void playFilledWarningCircle(Location center, double radius, Color color, double yOffset) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        playWarningCircle(center, radius, color, 56, yOffset);
        if (customMonsterTick % 2L != 0L) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.85F);
        Location origin = center.clone().add(0.0D, yOffset, 0.0D);
        world.spawnParticle(Particle.DUST, origin, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
        for (double currentRadius = 1.5D; currentRadius < radius; currentRadius += 1.5D) {
            int points = Math.max(8, (int) Math.ceil(currentRadius * 6.0D));
            double phase = (customMonsterTick % 20L) * 0.08D;
            for (int i = 0; i < points; i++) {
                double angle = phase + Math.PI * 2.0D * i / points;
                Location point = origin.clone().add(Math.cos(angle) * currentRadius, 0.0D, Math.sin(angle) * currentRadius);
                world.spawnParticle(Particle.DUST, point, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
            }
        }
    }

    private void playSoulfireChargeWarning(Location from, Location to, long age) {
        boolean empoweredPreview = horizontalDistanceSquared(from, to)
                > FERRYMAN_SOULFIRE_EMPOWER_DISTANCE * FERRYMAN_SOULFIRE_EMPOWER_DISTANCE;
        playSoulfireChargeWarning(from, to, age, empoweredPreview);
    }

    private void playSoulfireChargeWarning(Location from, Location to, long age, boolean empoweredPreview) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) {
            return;
        }
        Color mainColor = empoweredPreview ? Color.fromRGB(232, 58, 48) : Color.fromRGB(74, 232, 102);
        Color pulseColor = empoweredPreview ? Color.fromRGB(255, 112, 54) : Color.fromRGB(86, 255, 196);
        Particle.DustOptions dust = new Particle.DustOptions(mainColor, 1.2F);
        Particle.DustOptions pulseDust = new Particle.DustOptions(pulseColor, 1.35F);
        Vector start = from.clone().add(0.0D, 1.15D, 0.0D).toVector();
        Vector end = to.clone().add(0.0D, 0.55D, 0.0D).toVector();
        Vector delta = end.clone().subtract(start);
        double length = delta.length();
        if (length <= 0.0001D) {
            return;
        }
        Vector direction = delta.clone().normalize();
        int points = Math.max(6, (int) Math.ceil(delta.length() * 2.0D));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Vector point = start.clone().add(delta.clone().multiply(t));
            world.spawnParticle(Particle.DUST, point.toLocation(world), 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
        }
        double pulsePhase = (age % 10L) / 10.0D;
        for (double base = pulsePhase * 2.0D; base < length; base += 2.0D) {
            Vector point = start.clone().add(direction.clone().multiply(base));
            world.spawnParticle(Particle.DUST, point.toLocation(world), 2, 0.035D, 0.035D, 0.035D, 0.0D, pulseDust);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, point.toLocation(world), 1, 0.03D, 0.03D, 0.03D, 0.01D);
        }
        playSoulfireArrowHead(world, end, direction, pulseDust);
        if (age % 20L == 0L) {
            world.playSound(from, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55F, empoweredPreview ? 0.85F : 1.35F);
        }
    }

    private void playSoulfireArrowHead(World world, Vector end, Vector direction, Particle.DustOptions dust) {
        Vector horizontal = direction.clone();
        horizontal.setY(0.0D);
        if (horizontal.lengthSquared() <= 0.0001D) {
            horizontal = direction.clone();
        }
        horizontal.normalize();
        Vector side = new Vector(-horizontal.getZ(), 0.0D, horizontal.getX()).normalize();
        Vector back = horizontal.clone().multiply(-1.0D);
        for (int sideSign : new int[] {-1, 1}) {
            for (double step = 0.0D; step <= 1.6D; step += 0.4D) {
                Vector point = end.clone()
                        .add(back.clone().multiply(step))
                        .add(side.clone().multiply(sideSign * step * 0.55D));
                world.spawnParticle(Particle.DUST, point.toLocation(world), 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
            }
        }
    }

    private void playSoulfireChargeStartEffect(Location start, Location end, boolean empowered) {
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        Location origin = start.clone().add(0.0D, 0.85D, 0.0D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, origin, empowered ? 44 : 24, 0.45D, 0.45D, 0.45D, 0.08D);
        world.playSound(start, empowered ? Sound.ENTITY_BLAZE_SHOOT : Sound.ENTITY_ENDER_DRAGON_FLAP, empowered ? 1.0F : 0.65F, empowered ? 0.75F : 1.25F);
        if (empowered) {
            playSoulfireChargeWarning(start, end, FERRYMAN_CAST_TICKS, true);
        }
    }

    private void playSoulfireChargeParticles(Location from, Location to, boolean empoweredPreview) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) {
            return;
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, to.clone().add(0.0D, 0.8D, 0.0D), 10, 0.28D, 0.35D, 0.28D, 0.04D);
        playSoulfireChargeWarning(from, to, customMonsterTick, empoweredPreview);
    }

    private void playSoulfireSlamWindupStart(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location lifted = center.clone().add(0.0D, 1.7D, 0.0D);
        world.spawnParticle(Particle.REVERSE_PORTAL, lifted, 48, 0.45D, 0.7D, 0.45D, 0.02D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, lifted, 28, 0.25D, 0.45D, 0.25D, 0.04D);
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.75F, 1.45F);
    }

    private DungeonRun dungeonRunByBoss(UUID bossUuid) {
        if (bossUuid == null) {
            return null;
        }
        for (DungeonRun run : dungeonRunsByWorld.values()) {
            if (bossUuid.equals(run.bossUuid)) {
                return run;
            }
        }
        return null;
    }

    private void completeDungeonRun(DungeonRun run, World world) {
        dungeonRunsByWorld.remove(run.worldName);
        removeDungeonRunBars(run);
        ModuleRecord module = modules.get(run.moduleKey);
        for (Player player : world.getPlayers()) {
            if (module != null) {
                markModuleCompleted(player, module.key());
                giveDungeonRewards(player, module);
            }
            player.sendMessage("副本试炼已完成。");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        }
    }

    private void giveDungeonRewards(Player player, ModuleRecord module) {
        List<ItemStack> rewards = module.dungeonRewards();
        if (rewards.isEmpty()) {
            return;
        }
        for (ItemStack reward : rewards) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward.clone());
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.sendMessage("已获得 " + module.dungeonName() + " 的通关奖励。");
    }

    private DungeonWaveConfig currentDungeonWave(DungeonRun run) {
        if (run.currentWaveIndex < 0 || run.currentWaveIndex >= run.waves.size()) {
            return defaultDungeonWave();
        }
        return run.waves.get(run.currentWaveIndex);
    }

    private void addDungeonBossBarPlayers(DungeonRun run) {
        World world = Bukkit.getWorld(run.worldName);
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!run.bossBar.getPlayers().contains(player)) {
                run.bossBar.addPlayer(player);
            }
            if (run.castBar != null && !run.castBar.getPlayers().contains(player)) {
                run.castBar.addPlayer(player);
            }
        }
    }

    private void removeDungeonRunBars(DungeonRun run) {
        run.bossBar.removeAll();
        if (run.castBar != null) {
            run.castBar.removeAll();
        }
    }

    private void updateDungeonBossBar(DungeonRun run) {
        if (run.isBossDungeon()) {
            BossConfig config = run.bossConfig;
            LivingEntity boss = run.bossEntity();
            if (boss == null || boss.isDead() || !boss.isValid()) {
                run.bossBar.setTitle(config.displayName() + " - 已消失");
                run.bossBar.setProgress(0.0D);
                run.bossBar.setColor(BarColor.PURPLE);
                updateDungeonCastBar(run);
                return;
            }
            double maxHealth = Math.max(1.0D, maxHealth(boss));
            double health = Math.max(0.0D, Math.min(boss.getHealth(), maxHealth));
            run.bossBar.setTitle(config.displayName() + " - " + formatHealthValue(health) + "/" + formatHealthValue(maxHealth));
            run.bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, health / maxHealth)));
            run.bossBar.setColor(BarColor.PURPLE);
            updateDungeonCastBar(run);
            return;
        }
        int totalWaves = Math.max(1, run.waves.size());
        if (run.restStartTick >= 0L && run.nextWaveTick > run.restStartTick) {
            long restDuration = run.nextWaveTick - run.restStartTick;
            long elapsed = Math.max(0L, customMonsterTick - run.restStartTick);
            double progress = Math.max(0.0D, Math.min(1.0D, (double) elapsed / restDuration));
            long remainingTicks = Math.max(0L, run.nextWaveTick - customMonsterTick);
            run.bossBar.setTitle("第 " + (run.currentWaveIndex + 1) + "/" + totalWaves + " 波结束 - 休整 " + ((remainingTicks + 19L) / 20L) + " 秒");
            run.bossBar.setProgress(progress);
            run.bossBar.setColor(BarColor.BLUE);
            return;
        }
        int remaining = run.liveMobs.size();
        int initial = Math.max(1, run.currentWaveInitialMobs);
        double progress = Math.max(0.0D, Math.min(1.0D, (double) remaining / initial));
        run.bossBar.setTitle("第 " + (run.currentWaveIndex + 1) + "/" + totalWaves + " 波 - 剩余敌人: " + remaining);
        run.bossBar.setProgress(progress);
        run.bossBar.setColor(BarColor.RED);
    }

    private void updateDungeonCastBar(DungeonRun run) {
        if (run.castBar == null) {
            return;
        }
        BossSkillCast cast = run.activeBossSkill;
        if (cast == null || cast.phase != BossSkillPhase.CASTING) {
            run.castBar.setVisible(false);
            return;
        }
        long duration = cast.type == BossSkillType.WASTELAND ? FERRYMAN_WASTELAND_CAST_TICKS : FERRYMAN_CAST_TICKS;
        double progress = Math.max(0.0D, Math.min(1.0D, (double) cast.age(customMonsterTick) / duration));
        run.castBar.setTitle("读条: " + cast.type.displayName());
        run.castBar.setProgress(progress);
        run.castBar.setColor(cast.type == BossSkillType.WASTELAND ? BarColor.PURPLE : BarColor.YELLOW);
        run.castBar.setVisible(true);
    }

    private Location findDungeonMobSpawn(Location center, int index, int total) {
        World world = center.getWorld();
        if (world == null) {
            return center;
        }
        double baseAngle = total <= 0 ? 0.0D : Math.PI * 2.0D * index / total;
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = baseAngle + attempt * 0.73D;
            double radius = 4.0D + attempt % 7;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            for (int yOffset = 0; yOffset <= 8; yOffset++) {
                Location upper = dungeonSpawnLocationIfValid(world, x, center.getBlockY() + yOffset, z);
                if (upper != null) {
                    return upper;
                }
                Location lower = dungeonSpawnLocationIfValid(world, x, center.getBlockY() - yOffset, z);
                if (lower != null) {
                    return lower;
                }
            }
        }
        return center.clone().add(0.0D, 0.1D, 0.0D);
    }

    private Location dungeonSpawnLocationIfValid(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) {
            return null;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!feet.isPassable() || !head.isPassable() || ground.isPassable()) {
            return null;
        }
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private void applyDungeonEffectsToActivePlayers() {
        for (TowerInstance instance : new ArrayList<>(towerInstancesByPlayer.values())) {
            Player player = Bukkit.getPlayer(instance.ownerUuid());
            if (player != null && player.isOnline() && player.getWorld().getName().equals(instance.worldName())) {
                applyDungeonEffects(player);
            }
        }
    }

    private void applyDungeonEffects(Player player) {
        applyDungeonPlayerDamageRules(player);
        callPotionEffectsPlugin("applyWarpSuppression", player, 25_000L);
        callPotionEffectsPlugin("applyStrongBan", player, 25_000L);
        if (selectedBlessing(player) == DungeonBlessing.ARCHER_BLESSING) {
            callPotionEffectsPlugin("applyArcherBlessing", player, 25_000L);
        }
        if (selectedBlessing(player) == DungeonBlessing.SWORDSMAN_MEMORY) {
            callPotionEffectsPlugin("applySwordsmanMemory", player, 25_000L);
        }
    }

    private void clearDungeonEffects(Player player) {
        restoreDungeonPlayerDamageRules(player);
        callPotionEffectsPlugin("clearWarpSuppression", player);
        callPotionEffectsPlugin("clearStrongBan", player);
        callPotionEffectsPlugin("clearSwordsmanMemory", player);
        callPotionEffectsPlugin("clearArcherBlessing", player);
        callPotionEffectsPlugin("clearRebirthBlessing", player);
    }

    private Set<UUID> tickDungeonDamageRules() {
        Set<UUID> liveTargets = new HashSet<>();
        for (TowerInstance instance : new ArrayList<>(towerInstancesByWorld.values())) {
            World world = Bukkit.getWorld(instance.worldName());
            if (world == null) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                applyDungeonPlayerDamageRules(player);
            }
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player || entity.isDead() || !entity.isValid()) {
                    continue;
                }
                applySplitDamageTargetBase(entity);
                liveTargets.add(entity.getUniqueId());
            }
        }
        return liveTargets;
    }

    private void applyDungeonPlayerDamageRules(Player player) {
        dungeonPlayerNoDamageDefaults.putIfAbsent(player.getUniqueId(), player.getMaximumNoDamageTicks());
        if (player.getMaximumNoDamageTicks() != 0) {
            player.setMaximumNoDamageTicks(0);
        }
        player.setNoDamageTicks(0);
    }

    private void restoreDungeonPlayerDamageRules(Player player) {
        Integer originalTicks = dungeonPlayerNoDamageDefaults.remove(player.getUniqueId());
        if (originalTicks != null) {
            player.setMaximumNoDamageTicks(Math.max(0, originalTicks));
        }
        player.setNoDamageTicks(0);
    }

    private void applySplitDamageTargetBase(LivingEntity target) {
        if (target.getMaximumNoDamageTicks() != 0) {
            target.setMaximumNoDamageTicks(0);
        }
        target.setNoDamageTicks(0);
    }

    private boolean usesSplitDamageImmunity(LivingEntity target) {
        return isTrainingDummy(target)
                || (!(target instanceof Player) && isTowerInstanceWorld(target.getWorld()));
    }

    private boolean isTowerInstanceWorld(World world) {
        return world != null && towerInstancesByWorld.containsKey(world.getName());
    }

    private String damageImmunityChannel(EntityDamageEvent event) {
        String cause = event.getCause().name();
        return switch (cause) {
            case "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK" -> "direct_physical";
            case "PROJECTILE" -> "projectile_physical";
            case "MAGIC", "DRAGON_BREATH", "SONIC_BOOM" -> "magic";
            case "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR", "CAMPFIRE" -> "fire";
            case "WITHER" -> "wither";
            case "POISON" -> "poison";
            case "ENTITY_EXPLOSION", "BLOCK_EXPLOSION" -> "explosion";
            default -> cause.toLowerCase(Locale.ROOT);
        };
    }

    private double effectiveSplitDamage(UUID uuid, String channel, double incomingDamage) {
        if (incomingDamage <= 0.0D) {
            return 0.0D;
        }
        Map<String, SplitDamageCooldown> byChannel = splitDamageCooldowns.computeIfAbsent(uuid, ignored -> new HashMap<>());
        SplitDamageCooldown cooldown = byChannel.get(channel);
        if (cooldown == null) {
            byChannel.put(channel, new SplitDamageCooldown(customMonsterTick, incomingDamage));
            return incomingDamage;
        }
        long age = customMonsterTick - cooldown.startedTick();
        if (age < 0L || age >= SPLIT_DAMAGE_INVULNERABILITY_TICKS) {
            byChannel.put(channel, new SplitDamageCooldown(customMonsterTick, incomingDamage));
            return incomingDamage;
        }

        long remaining = SPLIT_DAMAGE_INVULNERABILITY_TICKS - age;
        if (remaining > SPLIT_DAMAGE_REPLACE_TICKS) {
            if (incomingDamage <= cooldown.lastDamage() + 0.0001D) {
                return 0.0D;
            }
            byChannel.put(channel, new SplitDamageCooldown(cooldown.startedTick(), incomingDamage));
            return incomingDamage - cooldown.lastDamage();
        }

        byChannel.put(channel, new SplitDamageCooldown(customMonsterTick, incomingDamage));
        return incomingDamage;
    }

    private boolean shouldLetTrainingDummyHitPassThrough(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player
                && "direct_physical".equals(damageImmunityChannel(event));
    }

    private void applyBossAllDamageReduction(EntityDamageEvent event, DungeonRun run) {
        double reduction = run.bossConfig.allDamageReduction();
        if (reduction <= 0.0D) {
            return;
        }
        double reducedFinalDamage = event.getFinalDamage() * (1.0D - reduction);
        scaleEventToFinalDamage(event, reducedFinalDamage);
    }

    private double safeTrainingDummyVanillaDamage(LivingEntity target) {
        return Math.max(0.001D, Math.min(0.25D, target.getHealth() - 0.001D));
    }

    private void restoreTrainingDummyAfterVanillaHit(Slime dummy) {
        UUID uuid = dummy.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Entity entity = Bukkit.getEntity(uuid);
            if (!isTrainingDummy(entity)) {
                return;
            }
            Slime current = (Slime) entity;
            current.setNoDamageTicks(0);
            applyTrainingDummyStats(current, false);
            syncCustomMonsterHealthDisplay(current);
        });
    }

    private void scaleEventToFinalDamage(EntityDamageEvent event, double desiredFinalDamage) {
        double currentFinalDamage = event.getFinalDamage();
        double currentBaseDamage = event.getDamage();
        if (currentFinalDamage <= 0.0D || currentBaseDamage <= 0.0D) {
            event.setDamage(Math.max(0.0D, desiredFinalDamage));
            return;
        }
        event.setDamage(Math.max(0.0D, currentBaseDamage * desiredFinalDamage / currentFinalDamage));
    }

    private void removeMissingSplitDamageCooldowns(Set<UUID> liveTargets) {
        splitDamageCooldowns.keySet().removeIf(uuid -> !liveTargets.contains(uuid));
    }

    private void callPotionEffectsPlugin(String methodName, Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getClass().getMethod(methodName, Player.class).invoke(plugin, player);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects." + methodName + ": " + exception.getMessage());
        }
    }

    private void callPotionEffectsPlugin(String methodName, Player player, long durationMillis) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getClass().getMethod(methodName, Player.class, long.class).invoke(plugin, player, durationMillis);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects." + methodName + ": " + exception.getMessage());
        }
    }

    private int customEnchantLevel(ItemStack item, String enchantId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return 0;
        }
        try {
            Object result = plugin.getClass().getMethod("customEnchantLevel", ItemStack.class, String.class).invoke(plugin, item, enchantId);
            return result instanceof Integer value ? Math.max(0, value) : 0;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects.customEnchantLevel: " + exception.getMessage());
            return 0;
        }
    }

    private int customEnchantMaxLevel(String enchantId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return 0;
        }
        try {
            Object result = plugin.getClass().getMethod("customEnchantMaxLevel", String.class).invoke(plugin, enchantId);
            return result instanceof Integer value ? Math.max(0, value) : 0;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects.customEnchantMaxLevel: " + exception.getMessage());
            return 0;
        }
    }

    private String firstCustomEnchantId(ItemStack item) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Object result = plugin.getClass().getMethod("firstCustomEnchantId", ItemStack.class).invoke(plugin, item);
            return result instanceof String value && !value.isBlank() ? value : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects.firstCustomEnchantId: " + exception.getMessage());
            return null;
        }
    }

    private boolean isCustomEnchantServiceAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        return plugin != null && plugin.isEnabled();
    }

    private boolean applyCustomEnchant(ItemStack item, String enchantId, int level) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Object result = plugin.getClass().getMethod("applyCustomEnchant", ItemStack.class, String.class, int.class).invoke(plugin, item, enchantId, level);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects.applyCustomEnchant: " + exception.getMessage());
            return false;
        }
    }

    private boolean removeCustomEnchant(ItemStack item, String enchantId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("XiceMorePotionEffects");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Object result = plugin.getClass().getMethod("removeCustomEnchant", ItemStack.class, String.class).invoke(plugin, item, enchantId);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to call XiceMorePotionEffects.removeCustomEnchant: " + exception.getMessage());
            return false;
        }
    }

    private void cleanupOrphanTowerInstances() {
        File container = Bukkit.getWorldContainer();
        File[] children = container.listFiles(file -> file.isDirectory() && file.getName().startsWith(instanceWorldPrefix()));
        if (children == null) {
            return;
        }
        for (File child : children) {
            tryDeleteDirectory(child.toPath());
        }
    }

    private void prepareMissingModuleSnapshots() {
        for (ModuleRecord module : modules.values()) {
            if (!Files.exists(moduleSnapshotPath(module))) {
                prepareModuleSnapshot(module, Bukkit.getWorld(module.worldName()) != null);
            }
        }
    }

    private void prepareModuleSnapshot(ModuleRecord module, boolean saveLoadedWorld) {
        if (moduleSnapshotStates.get(module.key()) == ModuleSnapshotState.PREPARING) {
            return;
        }
        if (hasPendingTowerEntry(module.key())) {
            Bukkit.getScheduler().runTaskLater(this, () -> prepareModuleSnapshot(module, saveLoadedWorld), 20L);
            return;
        }
        long startNanos = System.nanoTime();
        moduleSnapshotStates.put(module.key(), ModuleSnapshotState.PREPARING);
        World world = Bukkit.getWorld(module.worldName());
        double saveMillis = 0.0D;
        if (saveLoadedWorld && world != null) {
            long saveStartNanos = System.nanoTime();
            world.save();
            saveMillis = elapsedMillis(saveStartNanos);
        }
        Path sourcePath = new File(Bukkit.getWorldContainer(), module.worldName()).toPath().toAbsolutePath().normalize();
        Path snapshotPath = moduleSnapshotPath(module);
        Path tempPath = moduleSnapshotTempPath(module);
        if (!isSafeModuleWorldPath(sourcePath) || !isSafeSnapshotPath(snapshotPath) || !isSafeSnapshotPath(tempPath)) {
            moduleSnapshotStates.remove(module.key());
            getLogger().warning("Skipping unsafe module snapshot path for " + module.key());
            return;
        }
        double finalSaveMillis = saveMillis;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long asyncStartNanos = System.nanoTime();
            double tempDeleteMillis;
            double copyMillis;
            double oldDeleteMillis;
            double moveMillis;
            try {
                long tempDeleteStartNanos = System.nanoTime();
                deleteDirectory(tempPath);
                tempDeleteMillis = elapsedMillis(tempDeleteStartNanos);
                long copyStartNanos = System.nanoTime();
                copyWorldDirectory(sourcePath, tempPath);
                copyMillis = elapsedMillis(copyStartNanos);
                long oldDeleteStartNanos = System.nanoTime();
                deleteDirectory(snapshotPath);
                oldDeleteMillis = elapsedMillis(oldDeleteStartNanos);
                long moveStartNanos = System.nanoTime();
                Files.move(tempPath, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
                moveMillis = elapsedMillis(moveStartNanos);
            } catch (IOException exception) {
                Bukkit.getScheduler().runTask(this, () -> {
                    moduleSnapshotStates.remove(module.key());
                    getLogger().warning("Failed to prepare module snapshot for " + module.key() + ": " + exception.getMessage());
                });
                return;
            }
            double asyncMillis = elapsedMillis(asyncStartNanos);
            double totalMillis = elapsedMillis(startNanos);
            Bukkit.getScheduler().runTask(this, () -> {
                moduleSnapshotStates.put(module.key(), ModuleSnapshotState.READY);
                getLogger().info("[Perf] module-snapshot prepared module=" + module.key()
                        + " save=" + formatMillis(finalSaveMillis)
                        + " tempDelete=" + formatMillis(tempDeleteMillis)
                        + " copy=" + formatMillis(copyMillis)
                        + " oldDelete=" + formatMillis(oldDeleteMillis)
                        + " move=" + formatMillis(moveMillis)
                        + " async=" + formatMillis(asyncMillis)
                        + " total=" + formatMillis(totalMillis));
            });
        });
    }

    private boolean hasPendingTowerEntry(String moduleKey) {
        return pendingTowerEntries.values().stream().anyMatch(entry -> entry.moduleKey.equals(moduleKey));
    }

    private boolean isSafeTowerInstancePath(Path worldPath) {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        return worldPath.startsWith(container)
                && worldPath.getFileName() != null
                && worldPath.getFileName().toString().startsWith(instanceWorldPrefix());
    }

    private boolean isSafeSnapshotPath(Path worldPath) {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        return worldPath.startsWith(container)
                && worldPath.getFileName() != null
                && worldPath.getFileName().toString().startsWith(snapshotWorldPrefix());
    }

    private void tryDeleteDirectory(Path path) {
        try {
            deleteDirectory(path);
        } catch (IOException exception) {
            getLogger().warning("Failed to delete tower instance directory " + path + ": " + exception.getMessage());
        }
    }

    private void deleteDirectoryAsync(Path path, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long startNanos = System.nanoTime();
            try {
                deleteDirectory(path);
                getLogger().info("[Perf] async-delete reason=" + reason
                        + " path=" + path.getFileName()
                        + " duration=" + formatMillis(elapsedMillis(startNanos)));
            } catch (IOException exception) {
                getLogger().warning("Failed to delete directory " + path + ": " + exception.getMessage());
            }
        });
    }

    private void copyWorldDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                if (!relative.toString().isEmpty()) {
                    String name = relative.getFileName().toString();
                    if (name.equals("playerdata") || name.equals("advancements") || name.equals("stats")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (!name.equals("uid.dat") && !name.equals("session.lock")) {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private WorldCreator moduleWorldCreator(String worldName) {
        return new WorldCreator(worldName)
                .generator(new VoidChunkGenerator())
                .generateStructures(false);
    }

    private Location findVoidSpawn(World world, int x, int z) {
        world.getChunkAt(x >> 4, z >> 4).load(true);
        int y = Math.max(world.getMinHeight() + 2, Math.min(world.getMaxHeight() - 2, defaultSpawnY()));
        return new Location(world, x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
    }

    private void configureWorld(World world, Location spawn, int borderDistance) {
        world.setSpawnLocation(spawn);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(spawn);
        border.setSize(borderDistance * 2.0D);
        spawn.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().setType(Material.BEDROCK, false);
    }

    private Location mainWorldSpawn() {
        World world = Bukkit.getWorld(getConfig().getString("module.main-world", "main"));
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        return world.getSpawnLocation().add(0.5D, 0.0D, 0.5D);
    }

    private boolean isModuleWorld(World world) {
        if (world == null) {
            return false;
        }
        return modules.values().stream().anyMatch(module -> module.worldName().equals(world.getName()));
    }

    private ModuleRecord moduleByWorldName(String worldName) {
        return modules.values().stream()
                .filter(module -> module.worldName().equals(worldName))
                .findFirst()
                .orElse(null);
    }

    private void updateAllHudTabListWorlds() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHudTabListWorld(player);
        }
    }

    private void updateHudTabListWorld(Player player) {
        String displayName = rpgWorldDisplayName(player.getWorld().getName());
        if (displayName == null) {
            hudService.clearTabListWorld(player.getUniqueId(), RPG_TAB_WORLD_OWNER);
            return;
        }
        hudService.setTabListWorld(player.getUniqueId(), RPG_TAB_WORLD_OWNER, displayName, 100);
    }

    private String rpgWorldDisplayName(String worldName) {
        ModuleRecord module = moduleByWorldName(worldName);
        if (module != null) {
            return module.dungeonName();
        }
        TowerInstance instance = towerInstancesByWorld.get(worldName);
        if (instance == null) {
            return null;
        }
        module = modules.get(instance.moduleKey());
        return module == null ? instance.moduleKey() : module.dungeonName();
    }

    private String encodeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName()
                + "|" + location.getX()
                + "|" + location.getY()
                + "|" + location.getZ()
                + "|" + location.getYaw()
                + "|" + location.getPitch();
    }

    private Location decodeLocation(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.split("\\|", -1);
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            ModuleRecord module = moduleByWorldName(parts[0]);
            if (module != null) {
                world = Bukkit.createWorld(moduleWorldCreator(module.worldName()));
            }
        }
        if (world == null) {
            return null;
        }
        try {
            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isSafeModuleWorldPath(Path worldPath) {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        return worldPath.startsWith(container)
                && worldPath.getFileName() != null
                && worldPath.getFileName().toString().startsWith(worldPrefix());
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path current : paths) {
                Files.deleteIfExists(current);
            }
        }
    }

    private void loadModules() {
        modules.clear();
        modulesConfig = YamlConfiguration.loadConfiguration(modulesFile);
        ConfigurationSection section = modulesConfig.getConfigurationSection("modules");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "modules." + key;
            String worldName = modulesConfig.getString(path + ".world-name", worldPrefix() + key);
            String displayName = modulesConfig.getString(path + ".display-name", key);
            String dungeonName = modulesConfig.getString(path + ".dungeon-name", displayName);
            Material iconMaterial = iconMaterial(modulesConfig.getString(path + ".icon-material", null));
            int borderDistance = modulesConfig.getInt(path + ".border-distance", defaultBorderDistance());
            double x = modulesConfig.getDouble(path + ".spawn.x", 0.5D);
            double y = modulesConfig.getDouble(path + ".spawn.y", 80.0D);
            double z = modulesConfig.getDouble(path + ".spawn.z", 0.5D);
            float yaw = (float) modulesConfig.getDouble(path + ".spawn.yaw", 0.0D);
            float pitch = (float) modulesConfig.getDouble(path + ".spawn.pitch", 0.0D);
            int curseEscapeDeaths = clampCurseEscapeDeaths(modulesConfig.getInt(path + ".curse-escape-deaths", defaultCurseEscapeDeaths()));
            DungeonType dungeonType = DungeonType.byId(modulesConfig.getString(path + ".dungeon-type", DungeonType.WAVES.id()));
            BlockPos dungeonStarter = null;
            if (modulesConfig.isConfigurationSection(path + ".dungeon-starter")) {
                dungeonStarter = new BlockPos(
                        modulesConfig.getInt(path + ".dungeon-starter.x"),
                        modulesConfig.getInt(path + ".dungeon-starter.y"),
                        modulesConfig.getInt(path + ".dungeon-starter.z"));
            }
            List<DungeonWaveConfig> dungeonWaves = loadDungeonWaves(path + ".dungeon-waves");
            BossConfig bossConfig = loadBossConfig(path + ".boss");
            List<ItemStack> dungeonRewards = loadDungeonRewards(path + ".dungeon-rewards");
            List<String> requiredCompletions = loadModuleRequirements(path + ".entry-requirements.completed-modules");
            modules.put(key, new ModuleRecord(key, displayName, dungeonName, iconMaterial == null ? defaultModuleIcon() : iconMaterial,
                    worldName, borderDistance, x, y, z, yaw, pitch, curseEscapeDeaths,
                    dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions));
        }
    }

    private void saveModules() {
        YamlConfiguration out = new YamlConfiguration();
        for (ModuleRecord module : modules.values().stream().sorted(Comparator.comparing(ModuleRecord::key)).toList()) {
            String path = "modules." + module.key();
            out.set(path + ".display-name", module.displayName());
            out.set(path + ".dungeon-name", module.dungeonName());
            out.set(path + ".icon-material", module.iconMaterial().name());
            out.set(path + ".world-name", module.worldName());
            out.set(path + ".border-distance", module.borderDistance());
            out.set(path + ".spawn.x", module.spawnX());
            out.set(path + ".spawn.y", module.spawnY());
            out.set(path + ".spawn.z", module.spawnZ());
            out.set(path + ".spawn.yaw", module.spawnYaw());
            out.set(path + ".spawn.pitch", module.spawnPitch());
            out.set(path + ".curse-escape-deaths", module.curseEscapeDeaths());
            out.set(path + ".dungeon-type", module.dungeonType().id());
            out.set(path + ".entry-requirements.completed-modules", module.requiredCompletions());
            if (module.dungeonStarter() != null) {
                out.set(path + ".dungeon-starter.x", module.dungeonStarter().x());
                out.set(path + ".dungeon-starter.y", module.dungeonStarter().y());
                out.set(path + ".dungeon-starter.z", module.dungeonStarter().z());
            }
            out.set(path + ".dungeon-waves", dumpDungeonWaves(module.dungeonWaves()));
            out.set(path + ".boss.type", module.bossConfig().type());
            out.set(path + ".boss.display-name", module.bossConfig().displayName());
            out.set(path + ".boss.max-health", module.bossConfig().maxHealth());
            out.set(path + ".boss.attack-damage", module.bossConfig().attackDamage());
            out.set(path + ".boss.armor", module.bossConfig().armor());
            out.set(path + ".boss.movement-speed", module.bossConfig().movementSpeed());
            out.set(path + ".boss.follow-range", module.bossConfig().followRange());
            out.set(path + ".boss.all-damage-reduction", module.bossConfig().allDamageReduction());
            out.set(path + ".dungeon-rewards", copyRewardItems(module.dungeonRewards()));
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder.");
            }
            out.save(modulesFile);
            modulesConfig = out;
        } catch (IOException exception) {
            getLogger().severe("Failed to save modules.yml: " + exception.getMessage());
        }
    }

    private void ensureDefaultModuleSettings() {
        boolean changed = false;
        ModuleRecord tower1 = modules.get("tower_1");
        if (tower1 != null && !modulesConfig.contains("modules.tower_1.entry-requirements.completed-modules")) {
            modules.put(tower1.key(), tower1.withRequiredCompletions(List.of()));
            changed = true;
        }
        ModuleRecord tower2 = modules.get("tower_2");
        if (tower2 != null && !modulesConfig.contains("modules.tower_2.entry-requirements.completed-modules")) {
            modules.put(tower2.key(), tower2.withRequiredCompletions(List.of("tower_1")));
            changed = true;
        }
        ModuleRecord tower5 = modules.get("tower_5");
        if (tower5 != null && !modulesConfig.contains("modules.tower_5.entry-requirements.completed-modules")) {
            tower5 = tower5.withRequiredCompletions(List.of("tower_4"));
            modules.put(tower5.key(), tower5);
            changed = true;
        }
        tower5 = modules.get("tower_5");
        if (tower5 != null && !modulesConfig.contains("modules.tower_5.dungeon-type")) {
            modules.put(tower5.key(), tower5.withDungeonType(DungeonType.BOSS));
            changed = true;
        }
        for (ModuleRecord module : List.copyOf(modules.values())) {
            if (module.dungeonType() != DungeonType.BOSS) {
                continue;
            }
            String bossPath = "modules." + module.key() + ".boss";
            BossConfig boss = module.bossConfig();
            BossConfig normalizedBoss = boss.withMaxHealth(Math.min(BOSS_ENTITY_MAX_HEALTH, boss.maxHealth()));
            if (FERRYMAN_TYPE.equals(normalizeCustomMonsterType(boss.type()))
                    && Math.abs(normalizedBoss.allDamageReduction() - FERRYMAN_ALL_DAMAGE_REDUCTION) > 0.0001D) {
                normalizedBoss = normalizedBoss.withAllDamageReduction(FERRYMAN_ALL_DAMAGE_REDUCTION);
            }
            if (!modulesConfig.contains(bossPath + ".all-damage-reduction")
                    || Math.abs(boss.maxHealth() - normalizedBoss.maxHealth()) > 0.0001D
                    || Math.abs(boss.allDamageReduction() - normalizedBoss.allDamageReduction()) > 0.0001D) {
                module = module.withBossConfig(normalizedBoss);
                modules.put(module.key(), module);
                changed = true;
            }
            if (!module.dungeonWaves().isEmpty()) {
                modules.put(module.key(), module.withDungeonWaves(List.of()));
                changed = true;
            }
        }
        for (ModuleRecord module : List.copyOf(modules.values())) {
            String path = "modules." + module.key() + ".curse-escape-deaths";
            boolean fixedTower = "tower_1".equals(module.key()) || "tower_2".equals(module.key());
            int expectedDeaths = fixedTower ? 2 : module.curseEscapeDeaths();
            if (!modulesConfig.contains(path)) {
                expectedDeaths = fixedTower ? 2 : defaultCurseEscapeDeaths();
            }
            expectedDeaths = clampCurseEscapeDeaths(expectedDeaths);
            if (!modulesConfig.contains(path) || module.curseEscapeDeaths() != expectedDeaths) {
                modules.put(module.key(), module.withCurseEscapeDeaths(expectedDeaths));
                changed = true;
            }
        }
        if (changed) {
            saveModules();
        }
    }

    private void loadMagicAnvils() {
        magicAnvils.clear();
        magicAnvilsConfig = YamlConfiguration.loadConfiguration(magicAnvilsFile);
        magicAnvils.addAll(magicAnvilsConfig.getStringList("blocks"));
    }

    private void saveMagicAnvils() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("blocks", magicAnvils.stream().sorted().toList());
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder.");
            }
            out.save(magicAnvilsFile);
            magicAnvilsConfig = out;
        } catch (IOException exception) {
            getLogger().severe("Failed to save magic-anvils.yml: " + exception.getMessage());
        }
    }

    private void loadMagicGrindstones() {
        magicGrindstones.clear();
        magicGrindstonesConfig = YamlConfiguration.loadConfiguration(magicGrindstonesFile);
        magicGrindstones.addAll(magicGrindstonesConfig.getStringList("blocks"));
    }

    private void saveMagicGrindstones() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("blocks", magicGrindstones.stream().sorted().toList());
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder.");
            }
            out.save(magicGrindstonesFile);
            magicGrindstonesConfig = out;
        } catch (IOException exception) {
            getLogger().severe("Failed to save magic-grindstones.yml: " + exception.getMessage());
        }
    }

    private void loadMagicAnvilEnchantLists() {
        magicAnvilEnchantLists.clear();
        magicAnvilEnchantListsConfig = YamlConfiguration.loadConfiguration(magicAnvilEnchantListsFile);
        boolean changed = false;
        List<String> sword = new ArrayList<>(magicAnvilEnchantListsConfig.getStringList("weapon-types." + MAGIC_ANVIL_WEAPON_SWORD));
        if (sword.isEmpty()) {
            sword.add(CUSTOM_ENCHANT_WITHERING_BLADE);
            changed = true;
        }
        if (!sword.contains(CUSTOM_ENCHANT_PAIN_BLADE)) {
            sword.add(CUSTOM_ENCHANT_PAIN_BLADE);
            changed = true;
        }
        if (!sword.contains(CUSTOM_ENCHANT_SELF_GROWING)) {
            sword.add(CUSTOM_ENCHANT_SELF_GROWING);
            changed = true;
        }
        List<String> durable = new ArrayList<>(magicAnvilEnchantListsConfig.getStringList("weapon-types." + MAGIC_ANVIL_ITEM_DURABLE));
        if (!durable.contains(CUSTOM_ENCHANT_SELF_GROWING)) {
            durable.add(CUSTOM_ENCHANT_SELF_GROWING);
            changed = true;
        }
        List<String> extendingHand = new ArrayList<>(magicAnvilEnchantListsConfig.getStringList("weapon-types." + MAGIC_ANVIL_ITEM_EXTENDING_HAND));
        if (!extendingHand.contains(CUSTOM_ENCHANT_EXTENDING_HAND)) {
            extendingHand.add(CUSTOM_ENCHANT_EXTENDING_HAND);
            changed = true;
        }
        List<String> chestplate = new ArrayList<>(magicAnvilEnchantListsConfig.getStringList("weapon-types." + MAGIC_ANVIL_ARMOR_CHESTPLATE));
        if (!chestplate.contains(CUSTOM_ENCHANT_SATIETY_VIGOR)) {
            chestplate.add(CUSTOM_ENCHANT_SATIETY_VIGOR);
            changed = true;
        }
        List<String> leggings = new ArrayList<>(magicAnvilEnchantListsConfig.getStringList("weapon-types." + MAGIC_ANVIL_ARMOR_LEGGINGS));
        if (!leggings.contains(CUSTOM_ENCHANT_STEADY)) {
            leggings.add(CUSTOM_ENCHANT_STEADY);
            changed = true;
        }
        magicAnvilEnchantLists.put(MAGIC_ANVIL_WEAPON_SWORD, List.copyOf(sword));
        magicAnvilEnchantLists.put(MAGIC_ANVIL_ITEM_DURABLE, List.copyOf(durable));
        magicAnvilEnchantLists.put(MAGIC_ANVIL_ITEM_EXTENDING_HAND, List.copyOf(extendingHand));
        magicAnvilEnchantLists.put(MAGIC_ANVIL_ARMOR_CHESTPLATE, List.copyOf(chestplate));
        magicAnvilEnchantLists.put(MAGIC_ANVIL_ARMOR_LEGGINGS, List.copyOf(leggings));
        if (changed) {
            saveMagicAnvilEnchantLists(magicAnvilEnchantLists);
        }
    }

    private void saveMagicAnvilEnchantLists(Map<String, List<String>> lists) {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
            out.set("weapon-types." + entry.getKey(), entry.getValue());
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder.");
            }
            out.save(magicAnvilEnchantListsFile);
            magicAnvilEnchantListsConfig = out;
        } catch (IOException exception) {
            getLogger().severe("Failed to save magic-anvil-enchants.yml: " + exception.getMessage());
        }
    }

    private boolean isMagicAnvilBlock(Block block) {
        return block != null
                && block.getType() == MAGIC_ANVIL_CARRIER
                && magicAnvils.contains(blockKey(block));
    }

    private boolean isMagicGrindstoneBlock(Block block) {
        return block != null
                && (block.getType() == MAGIC_GRINDSTONE_CARRIER || block.getType() == MAGIC_GRINDSTONE_LEGACY_CARRIER)
                && magicGrindstones.contains(blockKey(block));
    }

    private boolean shouldPassMagicBlockInteractionToBlockPlacement(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        return event.getPlayer().isSneaking()
                && item != null
                && !item.getType().isAir()
                && item.getType().isBlock();
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + "|" + block.getX() + "|" + block.getY() + "|" + block.getZ();
    }

    private ModuleRecord moduleByInput(String input) {
        String key = normalizeModuleName(input);
        if (key == null) {
            return null;
        }
        return modules.get(key);
    }

    private List<DungeonWaveConfig> defaultDungeonWaves() {
        return List.of(defaultDungeonWave());
    }

    private DungeonWaveConfig defaultDungeonWave() {
        return new DungeonWaveConfig(List.of(new DungeonWaveEnemyConfig(ROTTEN_GUARD_TYPE, 3)), DEFAULT_DUNGEON_WAVE_WAIT_SECONDS);
    }

    private List<DungeonWaveConfig> loadDungeonWaves(String path) {
        List<?> rawWaves = modulesConfig.getList(path);
        if (rawWaves == null) {
            return defaultDungeonWaves();
        }
        if (rawWaves.isEmpty()) {
            return List.of();
        }
        List<DungeonWaveConfig> waves = new ArrayList<>();
        for (Object raw : rawWaves) {
            if (waves.size() >= MAX_DUNGEON_WAVES) {
                break;
            }
            if (raw instanceof Number number) {
                waves.add(new DungeonWaveConfig(List.of(new DungeonWaveEnemyConfig(ROTTEN_GUARD_TYPE, number.intValue())), DEFAULT_DUNGEON_WAVE_WAIT_SECONDS));
            } else if (raw instanceof Map<?, ?> map) {
                waves.add(loadDungeonWave(map));
            }
        }
        return waves.isEmpty() ? defaultDungeonWaves() : List.copyOf(waves);
    }

    private DungeonWaveConfig loadDungeonWave(Map<?, ?> map) {
        int waitSeconds = intFromMap(map, "wait-seconds", DEFAULT_DUNGEON_WAVE_WAIT_SECONDS);
        List<DungeonWaveEnemyConfig> enemies = new ArrayList<>();
        Object rawEnemies = map.get("enemies");
        if (rawEnemies instanceof List<?> enemyList) {
            for (Object rawEnemy : enemyList) {
                if (rawEnemy instanceof Map<?, ?> enemyMap) {
                    String type = stringFromMap(enemyMap, "type", ROTTEN_GUARD_TYPE);
                    int count = intFromMap(enemyMap, "count", 1);
                    enemies.add(new DungeonWaveEnemyConfig(type, count));
                }
            }
        }
        if (enemies.isEmpty()) {
            int count = intFromMap(map, "count", 3);
            enemies.add(new DungeonWaveEnemyConfig(ROTTEN_GUARD_TYPE, count));
        }
        return new DungeonWaveConfig(enemies, waitSeconds);
    }

    private BossConfig loadBossConfig(String path) {
        String type = modulesConfig.getString(path + ".type", DEFAULT_DUNGEON_BOSS_TYPE);
        double configuredMaxHealth = modulesConfig.getDouble(path + ".max-health", DEFAULT_DUNGEON_BOSS_MAX_HEALTH);
        boolean hasDamageReduction = modulesConfig.contains(path + ".all-damage-reduction");
        double allDamageReduction = hasDamageReduction
                ? modulesConfig.getDouble(path + ".all-damage-reduction", 0.0D)
                : inferredBossDamageReduction(type, configuredMaxHealth);
        return new BossConfig(
                type,
                modulesConfig.getString(path + ".display-name", DEFAULT_DUNGEON_BOSS_DISPLAY_NAME),
                Math.min(configuredMaxHealth, BOSS_ENTITY_MAX_HEALTH),
                modulesConfig.getDouble(path + ".attack-damage", DEFAULT_DUNGEON_BOSS_ATTACK_DAMAGE),
                modulesConfig.getDouble(path + ".armor", DEFAULT_DUNGEON_BOSS_ARMOR),
                modulesConfig.getDouble(path + ".movement-speed", DEFAULT_DUNGEON_BOSS_MOVEMENT_SPEED),
                modulesConfig.getDouble(path + ".follow-range", DEFAULT_DUNGEON_BOSS_FOLLOW_RANGE),
                allDamageReduction);
    }

    private double inferredBossDamageReduction(String type, double configuredMaxHealth) {
        if (configuredMaxHealth > BOSS_ENTITY_MAX_HEALTH) {
            return 1.0D - BOSS_ENTITY_MAX_HEALTH / configuredMaxHealth;
        }
        if (FERRYMAN_TYPE.equals(normalizeCustomMonsterType(type))) {
            return DEFAULT_DUNGEON_BOSS_ALL_DAMAGE_REDUCTION;
        }
        return 0.0D;
    }

    private List<ItemStack> loadDungeonRewards(String path) {
        List<?> rawRewards = modulesConfig.getList(path);
        if (rawRewards == null || rawRewards.isEmpty()) {
            return List.of();
        }
        List<ItemStack> rewards = new ArrayList<>();
        for (Object raw : rawRewards) {
            ItemStack item = null;
            if (raw instanceof ItemStack stack) {
                item = stack;
            } else if (raw instanceof Map<?, ?> map) {
                Map<String, Object> serialized = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        serialized.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                try {
                    item = ItemStack.deserialize(serialized);
                } catch (IllegalArgumentException ignored) {
                    item = null;
                }
            }
            if (!isEmptyItem(item) && rewards.size() < DUNGEON_REWARD_SIZE) {
                rewards.add(item.clone());
            }
        }
        return List.copyOf(rewards);
    }

    private List<String> loadModuleRequirements(String path) {
        List<String> keys = new ArrayList<>();
        for (String raw : modulesConfig.getStringList(path)) {
            String key = normalizeModuleName(raw);
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    private List<Map<String, Object>> dumpDungeonWaves(List<DungeonWaveConfig> waves) {
        List<Map<String, Object>> rawWaves = new ArrayList<>();
        for (DungeonWaveConfig wave : waves) {
            Map<String, Object> rawWave = new HashMap<>();
            rawWave.put("wait-seconds", wave.waitSeconds());
            List<Map<String, Object>> rawEnemies = new ArrayList<>();
            for (DungeonWaveEnemyConfig enemy : wave.enemies()) {
                Map<String, Object> rawEnemy = new HashMap<>();
                rawEnemy.put("type", enemy.type());
                rawEnemy.put("count", enemy.count());
                rawEnemies.add(rawEnemy);
            }
            rawWave.put("enemies", rawEnemies);
            rawWaves.add(rawWave);
        }
        return rawWaves;
    }

    private int intFromMap(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String stringFromMap(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private int clampDungeonWaveCount(int value) {
        return Math.max(0, Math.min(MAX_DUNGEON_WAVE_COUNT, value));
    }

    private int clampDungeonWaveWaitSeconds(int value) {
        return Math.max(0, Math.min(MAX_DUNGEON_WAVE_WAIT_SECONDS, value));
    }

    private String normalizeModuleName(String input) {
        String value = input == null ? "" : input.trim();
        if (!MODULE_NAME_PATTERN.matcher(value).matches()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean canUseAction(CommandSender sender, String action) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        Set<String> actions = new HashSet<>();
        for (String value : getConfig().getStringList("access.default-allowed-actions")) {
            actions.add(value.toLowerCase(Locale.ROOT));
        }
        for (String value : getConfig().getStringList("access.players." + player.getUniqueId() + ".actions")) {
            actions.add(value.toLowerCase(Locale.ROOT));
        }
        return actions.contains(action.toLowerCase(Locale.ROOT));
    }

    private int defaultBorderDistance() {
        return clampBorderDistance(getConfig().getInt("module.default-border-distance", 256));
    }

    private int defaultCurseEscapeDeaths() {
        return clampCurseEscapeDeaths(getConfig().getInt("module.default-curse-escape-deaths", 2));
    }

    private int defaultSpawnY() {
        return getConfig().getInt("module.default-spawn-y", 80);
    }

    private int clampBorderDistance(int value) {
        int min = Math.max(1, getConfig().getInt("module.min-border-distance", 32));
        int max = Math.max(min, getConfig().getInt("module.max-border-distance", 4096));
        return Math.max(min, Math.min(max, value));
    }

    private int clampCurseEscapeDeaths(int value) {
        return Math.max(0, Math.min(99, value));
    }

    private String worldPrefix() {
        return getConfig().getString("module.world-prefix", "xicerpg_module_");
    }

    private String instanceWorldPrefix() {
        return getConfig().getString("module.instance-world-prefix", "xicerpg_instance_");
    }

    private String snapshotWorldPrefix() {
        return getConfig().getString("module.snapshot-world-prefix", "xicerpg_snapshot_");
    }

    private Path moduleSnapshotPath(ModuleRecord module) {
        return new File(Bukkit.getWorldContainer(), snapshotWorldPrefix() + module.key()).toPath().toAbsolutePath().normalize();
    }

    private Path moduleSnapshotTempPath(ModuleRecord module) {
        return new File(Bukkit.getWorldContainer(), snapshotWorldPrefix() + module.key() + "_tmp").toPath().toAbsolutePath().normalize();
    }

    private Material defaultModuleIcon() {
        return Material.ECHO_SHARD;
    }

    private Material iconMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return iconMaterial(Material.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Material iconMaterial(Material material) {
        if (material == null || material == Material.AIR || !material.isItem()) {
            return null;
        }
        return material;
    }

    private Material nextModuleIcon(Material current) {
        int index = MODULE_ICON_CYCLE.indexOf(current);
        return MODULE_ICON_CYCLE.get((index + 1 + MODULE_ICON_CYCLE.size()) % MODULE_ICON_CYCLE.size());
    }

    private void registerMagicTowerKeyRecipe() {
        customItemService.unregisterRecipe(magicTowerKeyRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(magicTowerKeyRecipeKey, createMagicTowerKey(1));
        recipe.shape("IB ", "SDR", "IB ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('B', Material.BONE);
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('R', Material.BLAZE_ROD);
        customItemService.registerRecipe(recipe);
    }

    private void registerMagicAnvilRecipe() {
        customItemService.unregisterRecipe(magicAnvilRecipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(magicAnvilRecipeKey, createMagicAnvilItem(1));
        recipe.addIngredient(Material.ANVIL);
        recipe.addIngredient(new RecipeChoice.ExactChoice(createMagicDust(1)));
        recipe.addIngredient(new RecipeChoice.ExactChoice(createMagicDust(1)));
        customItemService.registerRecipe(recipe);
    }

    private void registerMagicGrindstoneRecipe() {
        customItemService.unregisterRecipe(magicGrindstoneRecipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(magicGrindstoneRecipeKey, createMagicGrindstoneItem(1));
        recipe.addIngredient(Material.GRINDSTONE);
        recipe.addIngredient(new RecipeChoice.ExactChoice(createMagicDust(1)));
        recipe.addIngredient(new RecipeChoice.ExactChoice(createMagicDust(1)));
        customItemService.registerRecipe(recipe);
    }

    private void registerMagicEnchantedGoldenAppleRecipe() {
        customItemService.unregisterRecipe(magicEnchantedGoldenAppleRecipeKey);
        ItemStack result = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8);
        ShapedRecipe recipe = new ShapedRecipe(magicEnchantedGoldenAppleRecipeKey, result);
        recipe.shape("GGG", "GDG", "GGG");
        recipe.setIngredient('G', Material.GOLDEN_APPLE);
        recipe.setIngredient('D', new RecipeChoice.ExactChoice(createMagicDust(1)));
        customItemService.registerRecipe(recipe);
    }

    private void registerCustomItems() {
        customItemService.register(new CustomItemDefinition(
                magicDustKey,
                Material.GLOWSTONE_DUST,
                magicDustKey,
                magicDustItemModelKey,
                Component.text("魔法粉末", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("微光在粉末间缓慢流动，等待被写进新的仪式。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                magicAnvilItemKey,
                Material.ANVIL,
                magicAnvilItemKey,
                magicAnvilItemModelKey,
                Component.text("魔法砧", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("被魔法粉末唤醒的锻造台，等待承载更复杂的仪式。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                true));
        customItemService.register(new CustomItemDefinition(
                magicGrindstoneItemKey,
                Material.GRINDSTONE,
                magicGrindstoneItemKey,
                magicGrindstoneItemModelKey,
                Component.text("魔法砂轮", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("磨去附魔刻痕，回收其中残留的魔法粉尘。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                true));
        customItemService.register(new CustomItemDefinition(
                magicTowerKey,
                Material.ECHO_SHARD,
                magicTowerKey,
                magicTowerKeyItemModelKey,
                Component.text("魔塔密钥", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("塔门会回应它的回声。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("右键选择魔塔副本；副本内右键返回。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                dungeonStarterItemKey,
                DUNGEON_STARTER_ITEM,
                dungeonStarterItemKey,
                dungeonStarterItemModelKey,
                Component.text("副本启动仪", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("放置在模板世界中，用于配置并启动副本试炼。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("模板世界右键配置；副本世界右键启动。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
        customItemService.register(new CustomItemDefinition(
                satietySkillOrbKey,
                Material.MAGMA_CREAM,
                satietySkillOrbKey,
                satietySkillOrbItemModelKey,
                Component.text("饱食技能珠", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("右键使用: 恢复 12 点隐藏饱和度。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("冷却时间: 20 秒，同类技能珠共享冷却。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)),
                null));
    }

    private void registerCustomBlocks() {
        magicAnvilBlockDefinition = new CustomBlockDefinition(
                magicAnvilItemKey,
                MAGIC_ANVIL_CARRIER,
                Material.ANVIL,
                magicAnvilItemModelKey,
                magicAnvilDisplayKey,
                Component.text("Magic Anvil"),
                1.2F,
                1.0F,
                MAGIC_ANVIL_HARDNESS);
        magicGrindstoneBlockDefinition = new CustomBlockDefinition(
                magicGrindstoneItemKey,
                MAGIC_GRINDSTONE_CARRIER,
                Material.GRINDSTONE,
                magicGrindstoneItemModelKey,
                magicGrindstoneDisplayKey,
                Component.text("Magic Grindstone"),
                1.2F,
                1.0F,
                MAGIC_GRINDSTONE_HARDNESS);
        dungeonStarterBlockDefinition = new CustomBlockDefinition(
                dungeonStarterItemKey,
                DUNGEON_STARTER_CARRIER,
                DUNGEON_STARTER_ITEM,
                dungeonStarterItemModelKey,
                dungeonStarterDisplayKey,
                Component.text("Dungeon Starter"),
                1.0F,
                1.0F,
                3.5D);
        customBlockService.registerBlock(magicAnvilBlockDefinition);
        customBlockService.registerBlock(magicGrindstoneBlockDefinition);
        customBlockService.registerBlock(dungeonStarterBlockDefinition);
    }

    private ItemStack createMagicDust(int amount) {
        return customItemService.create(magicDustKey, amount);
    }

    private ItemStack createMagicAnvilItem(int amount) {
        return customItemService.create(magicAnvilItemKey, amount);
    }

    private ItemStack createMagicGrindstoneItem(int amount) {
        return customItemService.create(magicGrindstoneItemKey, amount);
    }

    private ItemStack createMagicTowerKey(int amount) {
        return customItemService.create(magicTowerKey, amount);
    }

    private ItemStack createDungeonStarterItem(int amount) {
        return customItemService.create(dungeonStarterItemKey, amount);
    }

    private ItemStack createSatietySkillOrb(int amount) {
        return customItemService.create(satietySkillOrbKey, amount);
    }

    private ItemStack createDungeonStarterDisplayItem() {
        ItemStack item = new ItemStack(DUNGEON_STARTER_ITEM);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(dungeonStarterItemModelKey);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isMagicTowerKey(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, magicTowerKey);
    }

    private boolean isMagicDust(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, magicDustKey);
    }

    private boolean isMagicAnvilItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, magicAnvilItemKey);
    }

    private boolean isMagicGrindstoneItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, magicGrindstoneItemKey);
    }

    private boolean isDungeonStarterItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, dungeonStarterItemKey);
    }

    private boolean isSatietySkillOrb(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, satietySkillOrbKey);
    }

    private void useSatietySkillOrb(Player player) {
        long now = System.currentTimeMillis();
        long cooldownUntil = satietySkillOrbCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (cooldownUntil > now) {
            long remainingSeconds = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            player.sendActionBar(Component.text("饱食技能珠冷却中: " + remainingSeconds + " 秒", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45F, 0.7F);
            return;
        }
        float previous = player.getSaturation();
        float restored = Math.min(20.0F, previous + SATIETY_SKILL_ORB_RESTORE_AMOUNT);
        if (restored <= previous + 0.001F) {
            player.sendActionBar(Component.text("饱和度已充盈", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45F, 1.3F);
            return;
        }
        player.setSaturation(restored);
        satietySkillOrbCooldowns.put(player.getUniqueId(), now + SATIETY_SKILL_ORB_COOLDOWN_MILLIS);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().clone().add(0.0D, 1.2D, 0.0D), 4, 0.35D, 0.25D, 0.35D, 0.0D);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.85F, 1.15F);
        player.sendActionBar(Component.text("饱食技能珠恢复了隐藏饱和度", NamedTextColor.RED));
    }

    private boolean isSword(ItemStack item) {
        return item != null && switch (item.getType()) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> true;
            default -> false;
        };
    }

    private boolean isExtendingHandItem(ItemStack item) {
        if (isEmptyItem(item)) {
            return false;
        }
        String name = item.getType().name();
        return isSword(item)
                || name.equals("SPEAR")
                || name.endsWith("_SPEAR")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_HOE")
                || name.equals("TRIDENT")
                || name.equals("MACE")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("SHEARS")
                || name.equals("BRUSH");
    }

    private static boolean isEmptyItem(ItemStack item) {
        return item == null || item.getAmount() <= 0 || item.getType().isAir();
    }

    private static List<ItemStack> copyRewardItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> !isEmptyItem(item))
                .limit(DUNGEON_REWARD_SIZE)
                .map(ItemStack::clone)
                .toList();
    }

    private DungeonBlessing selectedBlessing(Player player) {
        String raw = player.getPersistentDataContainer().get(selectedBlessingKey, PersistentDataType.STRING);
        return DungeonBlessing.byId(raw);
    }

    private Set<String> completedModules(Player player) {
        String raw = player.getPersistentDataContainer().get(completedModulesKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> completed = new HashSet<>();
        for (String part : raw.split(",")) {
            String key = normalizeModuleName(part);
            if (key != null) {
                completed.add(key);
            }
        }
        return completed;
    }

    private void markModuleCompleted(Player player, String moduleKey) {
        String key = normalizeModuleName(moduleKey);
        if (key == null) {
            return;
        }
        Set<String> completed = new HashSet<>(completedModules(player));
        if (completed.add(key)) {
            player.getPersistentDataContainer().set(completedModulesKey, PersistentDataType.STRING,
                    completed.stream().sorted().collect(java.util.stream.Collectors.joining(",")));
        }
    }

    private void setSelectedBlessing(Player player, DungeonBlessing blessing) {
        DungeonBlessing value = blessing == null ? DungeonBlessing.NONE : blessing;
        if (value == DungeonBlessing.NONE) {
            player.getPersistentDataContainer().remove(selectedBlessingKey);
        } else {
            player.getPersistentDataContainer().set(selectedBlessingKey, PersistentDataType.STRING, value.id());
        }
        player.sendMessage("已选择祝福: " + value.displayName() + "。");
    }

    private boolean handleDungeonStarterInteraction(Player player, Block block) {
        ModuleRecord module = moduleByWorldName(block.getWorld().getName());
        if (module != null && module.dungeonStarter() != null && module.dungeonStarter().matches(block)
                && block.getType() == DUNGEON_STARTER_CARRIER) {
            refreshDungeonStarterDisplay(block);
            openDungeonStarterMenu(player, module);
            return true;
        }

        TowerInstance instance = towerInstancesByWorld.get(block.getWorld().getName());
        if (instance == null || !instance.ownerUuid().equals(player.getUniqueId())) {
            return false;
        }
        ModuleRecord instanceModule = modules.get(instance.moduleKey());
        if (instanceModule == null || instanceModule.dungeonStarter() == null || !instanceModule.dungeonStarter().matches(block)
                || block.getType() != DUNGEON_STARTER_CARRIER) {
            return false;
        }
        if (dungeonRunsByWorld.containsKey(block.getWorld().getName())) {
            player.sendMessage("副本已经启动。");
            return true;
        }
        playDungeonStartEffect(block.getLocation().add(0.5D, 0.5D, 0.5D));
        removeDungeonStarterDisplay(block.getWorld(), instanceModule.dungeonStarter());
        block.setType(Material.AIR, false);
        startDungeonRun(player, instance, instanceModule, block.getLocation().add(0.5D, 0.0D, 0.5D));
        return true;
    }

    private void playDungeonStartEffect(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(132, 66, 255), 1.2F);
        for (int i = 0; i < 56; i++) {
            double angle = Math.PI * 2.0D * i / 56.0D;
            double upward = 0.12D + (i % 7) * 0.018D;
            world.spawnParticle(Particle.DUST, center, 0, Math.cos(angle) * 0.22D, upward, Math.sin(angle) * 0.22D, 1.0D, dust);
        }
        world.spawnParticle(Particle.PORTAL, center, 80, 0.65D, 0.65D, 0.65D, 0.18D);
        world.playSound(center, Sound.ENTITY_WITHER_HURT, 0.9F, 0.75F);
    }

    private void playDungeonRestStartEffect(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.ENTITY_WITHER_HURT, 0.65F, 1.15F);
        playDungeonRestParticles(center);
    }

    private void playDungeonRestParticles(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location origin = center.clone().add(0.0D, 0.45D, 0.0D);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(70, 145, 255), 1.0F);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2.0D * i / 40.0D;
            double lift = 0.02D + (i % 5) * 0.01D;
            world.spawnParticle(Particle.DUST, origin, 0, Math.cos(angle) * 0.28D, lift, Math.sin(angle) * 0.28D, 1.0D, dust);
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, origin, 18, 0.55D, 0.25D, 0.55D, 0.035D);
    }

    private void playDungeonSpawnPreviewCircle(Location spawn) {
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 24, 24), 1.15F);
        Location center = spawn.clone().add(0.0D, 0.06D, 0.0D);
        for (int i = 0; i < DUNGEON_SPAWN_PREVIEW_PARTICLES; i++) {
            double angle = Math.PI * 2.0D * i / DUNGEON_SPAWN_PREVIEW_PARTICLES;
            Location point = center.clone().add(
                    Math.cos(angle) * DUNGEON_SPAWN_PREVIEW_RADIUS,
                    0.0D,
                    Math.sin(angle) * DUNGEON_SPAWN_PREVIEW_RADIUS);
            world.spawnParticle(Particle.DUST, point, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
        }
    }

    private void refreshLoadedDungeonStarterDisplays() {
        for (ModuleRecord module : modules.values()) {
            World world = Bukkit.getWorld(module.worldName());
            if (world == null || module.dungeonStarter() == null) {
                continue;
            }
            Block block = world.getBlockAt(module.dungeonStarter().x(), module.dungeonStarter().y(), module.dungeonStarter().z());
            if (block.getType() == DUNGEON_STARTER_CARRIER) {
                refreshDungeonStarterDisplay(block);
            }
        }
        for (TowerInstance instance : towerInstancesByWorld.values()) {
            ModuleRecord module = modules.get(instance.moduleKey());
            World world = Bukkit.getWorld(instance.worldName());
            if (module == null || world == null || module.dungeonStarter() == null) {
                continue;
            }
            Block block = world.getBlockAt(module.dungeonStarter().x(), module.dungeonStarter().y(), module.dungeonStarter().z());
            if (block.getType() == DUNGEON_STARTER_CARRIER) {
                refreshDungeonStarterDisplay(block);
            }
        }
    }

    private void refreshLoadedMagicAnvilDisplays() {
        for (String key : List.copyOf(magicAnvils)) {
            StoredBlockPos pos = storedBlockPos(key);
            if (pos == null) {
                continue;
            }
            World world = Bukkit.getWorld(pos.worldName());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (block.getType() == MAGIC_ANVIL_CARRIER) {
                refreshMagicAnvilDisplay(block);
            }
        }
    }

    private void refreshLoadedMagicGrindstoneDisplays() {
        for (String key : List.copyOf(magicGrindstones)) {
            StoredBlockPos pos = storedBlockPos(key);
            if (pos == null) {
                continue;
            }
            World world = Bukkit.getWorld(pos.worldName());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (block.getType() == MAGIC_GRINDSTONE_LEGACY_CARRIER) {
                block.setType(MAGIC_GRINDSTONE_CARRIER, false);
            }
            if (block.getType() == MAGIC_GRINDSTONE_CARRIER) {
                refreshMagicGrindstoneDisplay(block);
            }
        }
    }

    private void refreshMagicAnvilDisplay(Block block) {
        if (customBlockService != null && magicAnvilBlockDefinition != null) {
            customBlockService.spawnOrReplaceDisplay(block, magicAnvilBlockDefinition, BlockFace.SOUTH);
            return;
        }
        removeMagicAnvilDisplay(block);
        block.getWorld().spawn(block.getLocation().add(0.5D, 0.5D, 0.5D), ItemDisplay.class, display -> {
            display.setItemStack(createMagicAnvilDisplayItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setDisplayWidth(1.2F);
            display.setDisplayHeight(1.0F);
            display.getPersistentDataContainer().set(magicAnvilDisplayKey, PersistentDataType.STRING, blockKey(block));
        });
    }

    private void removeMagicAnvilDisplay(Block block) {
        if (block == null) {
            return;
        }
        if (customBlockService != null && magicAnvilBlockDefinition != null) {
            customBlockService.removeDisplays(
                    block.getWorld(),
                    block.getLocation().add(0.5D, 0.5D, 0.5D),
                    magicAnvilBlockDefinition,
                    blockKey(block),
                    1.2D);
            return;
        }
        String id = blockKey(block);
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.2D, 1.2D, 1.2D)) {
            String displayId = entity.getPersistentDataContainer().get(magicAnvilDisplayKey, PersistentDataType.STRING);
            if (id.equals(displayId)) {
                entity.remove();
            }
        }
    }

    private void breakMagicAnvil(Block block, boolean dropItem) {
        if (magicAnvils.remove(blockKey(block))) {
            clearCustomBlockBreakSessionsFor(block);
            removeMagicAnvilDisplay(block);
            saveMagicAnvils();
            block.setType(Material.AIR, false);
            if (dropItem) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), createMagicAnvilItem(1));
            }
        }
    }

    private ItemStack createMagicAnvilDisplayItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Magic Anvil", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(magicAnvilItemModelKey);
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private void refreshMagicGrindstoneDisplay(Block block) {
        if (customBlockService != null && magicGrindstoneBlockDefinition != null) {
            customBlockService.spawnOrReplaceDisplay(block, magicGrindstoneBlockDefinition, BlockFace.SOUTH);
            return;
        }
        removeMagicGrindstoneDisplay(block);
        block.getWorld().spawn(block.getLocation().add(0.5D, 0.5D, 0.5D), ItemDisplay.class, display -> {
            display.setItemStack(createMagicGrindstoneDisplayItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setDisplayWidth(1.2F);
            display.setDisplayHeight(1.0F);
            display.getPersistentDataContainer().set(magicGrindstoneDisplayKey, PersistentDataType.STRING, blockKey(block));
        });
    }

    private void removeMagicGrindstoneDisplay(Block block) {
        if (block == null) {
            return;
        }
        if (customBlockService != null && magicGrindstoneBlockDefinition != null) {
            customBlockService.removeDisplays(
                    block.getWorld(),
                    block.getLocation().add(0.5D, 0.5D, 0.5D),
                    magicGrindstoneBlockDefinition,
                    blockKey(block),
                    1.2D);
            return;
        }
        String id = blockKey(block);
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.2D, 1.2D, 1.2D)) {
            String displayId = entity.getPersistentDataContainer().get(magicGrindstoneDisplayKey, PersistentDataType.STRING);
            if (id.equals(displayId)) {
                entity.remove();
            }
        }
    }

    private void breakMagicGrindstone(Block block, boolean dropItem) {
        if (magicGrindstones.remove(blockKey(block))) {
            clearCustomBlockBreakSessionsFor(block);
            removeMagicGrindstoneDisplay(block);
            saveMagicGrindstones();
            block.setType(Material.AIR, false);
            if (dropItem) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), createMagicGrindstoneItem(1));
            }
        }
    }

    private void clearCustomBlockBreakSessionsFor(Block block) {
        String key = blockKey(block);
        for (Player player : Bukkit.getOnlinePlayers()) {
            CustomBlockBreakSession session = customBlockBreakSessions.get(player.getUniqueId());
            if (session != null && session.blockKey.equals(key)) {
                stopCustomBlockBreak(player);
            }
        }
    }

    private ItemStack createMagicGrindstoneDisplayItem() {
        ItemStack item = new ItemStack(Material.GRINDSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Magic Grindstone", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(magicGrindstoneItemModelKey);
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private StoredBlockPos storedBlockPos(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split("\\|", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new StoredBlockPos(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void refreshDungeonStarterDisplay(Block block) {
        BlockPos pos = BlockPos.of(block);
        if (customBlockService != null && dungeonStarterBlockDefinition != null) {
            customBlockService.spawnOrReplaceDisplay(block, dungeonStarterBlockDefinition, BlockFace.SOUTH);
            return;
        }
        removeDungeonStarterDisplay(block.getWorld(), pos);
        block.getWorld().spawn(block.getLocation().add(0.5D, 0.5D, 0.5D), ItemDisplay.class, display -> {
            display.setItemStack(createDungeonStarterDisplayItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.getPersistentDataContainer().set(dungeonStarterDisplayKey, PersistentDataType.STRING, dungeonStarterDisplayId(block.getWorld(), pos));
        });
    }

    private void removeDungeonStarterDisplay(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        String id = dungeonStarterDisplayId(world, pos);
        if (customBlockService != null && dungeonStarterBlockDefinition != null) {
            customBlockService.removeDisplays(
                    world,
                    new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D),
                    dungeonStarterBlockDefinition,
                    id,
                    1.2D);
        }
        Location center = new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D);
        for (Entity entity : world.getNearbyEntities(center, 1.2D, 1.2D, 1.2D)) {
            String displayId = entity.getPersistentDataContainer().get(dungeonStarterDisplayKey, PersistentDataType.STRING);
            if (id.equals(displayId) || displayId != null) {
                entity.remove();
            }
        }
    }

    private String dungeonStarterDisplayId(World world, BlockPos pos) {
        return world.getName() + "|" + pos.x() + "|" + pos.y() + "|" + pos.z();
    }

    private boolean craftingMatrixContainsMagicTowerKey(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (isMagicTowerKey(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickMayGrantDiamond(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        return (current != null && current.getType() == Material.DIAMOND)
                || (cursor != null && cursor.getType() == Material.DIAMOND);
    }

    private boolean clickMayGrantMagicDust(InventoryClickEvent event) {
        return isMagicDust(event.getCurrentItem()) || isMagicDust(event.getCursor());
    }

    private void unlockMagicTowerKeyRecipe(Player player) {
        customItemService.discoverRecipe(player, magicTowerKeyRecipeKey);
    }

    private void unlockMagicAnvilRecipe(Player player) {
        customItemService.discoverRecipe(player, magicAnvilRecipeKey);
    }

    private void unlockMagicGrindstoneRecipe(Player player) {
        customItemService.discoverRecipe(player, magicGrindstoneRecipeKey);
    }

    private void unlockMagicEnchantedGoldenAppleRecipe(Player player) {
        customItemService.discoverRecipe(player, magicEnchantedGoldenAppleRecipeKey);
    }

    private void unlockMagicDustRecipes(Player player) {
        unlockMagicAnvilRecipe(player);
        unlockMagicGrindstoneRecipe(player);
        unlockMagicEnchantedGoldenAppleRecipe(player);
    }

    private void unlockMagicTowerKeyRecipeIfHasDiamond(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                unlockMagicTowerKeyRecipe(player);
                return;
            }
        }
    }

    private void unlockMagicDustRecipesIfHasMagicDust(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMagicDust(item)) {
                unlockMagicDustRecipes(player);
                return;
            }
        }
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private Zombie spawnRottenGuard(Location location) {
        Zombie zombie = location.getWorld().spawn(location, Zombie.class, spawned -> {
            spawned.getPersistentDataContainer().set(monsterTypeKey, PersistentDataType.STRING, ROTTEN_GUARD_TYPE);
            spawned.customName(Component.text("朽败的卫兵", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(true);
            spawned.setCanPickupItems(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setBaby(false);
            applyRottenGuardStats(spawned, true);
            applyRottenGuardVisualBase(spawned);
        });
        removeRottenGuardChickenJockeyMount(zombie);
        Bukkit.getScheduler().runTask(this, () -> removeRottenGuardChickenJockeyMount(zombie));
        return zombie;
    }

    private void removeRottenGuardChickenJockeyMount(Zombie zombie) {
        if (zombie == null || !zombie.isValid() || !isRottenGuard(zombie) || !zombie.isInsideVehicle()) {
            return;
        }
        Entity vehicle = zombie.getVehicle();
        zombie.leaveVehicle();
        if (vehicle instanceof Chicken chicken) {
            chicken.remove();
        }
    }

    private Entity spawnCustomMonster(String type, Location location) {
        if (FERRYMAN_TYPE.equals(type)) {
            return spawnFerryman(location);
        }
        if (GULPER_TYPE.equals(type)) {
            return spawnGulper(location);
        }
        if (PUS_BUG_TYPE.equals(type)) {
            return spawnPusBug(location);
        }
        if (TRAINING_DUMMY_TYPE.equals(type)) {
            return spawnTrainingDummy(location);
        }
        return spawnRottenGuard(location);
    }

    private Zombie spawnGulper(Location location) {
        Zombie zombie = location.getWorld().spawn(location, Zombie.class, spawned -> {
            spawned.getPersistentDataContainer().set(monsterTypeKey, PersistentDataType.STRING, GULPER_TYPE);
            spawned.customName(Component.text("啜食者", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(true);
            spawned.setCanPickupItems(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setBaby(false);
            spawned.setSilent(true);
            applyGulperStats(spawned, true);
            applyGulperVisualBase(spawned);
            syncGulperDisplay(spawned);
        });
        removeRottenGuardChickenJockeyMount(zombie);
        Bukkit.getScheduler().runTask(this, () -> removeRottenGuardChickenJockeyMount(zombie));
        return zombie;
    }

    private Zombie spawnFerryman(Location location) {
        Zombie zombie = location.getWorld().spawn(location, Zombie.class, spawned -> {
            spawned.getPersistentDataContainer().set(monsterTypeKey, PersistentDataType.STRING, FERRYMAN_TYPE);
            spawned.customName(Component.text("引渡人", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(false);
            spawned.setCanPickupItems(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAdult();
            spawned.setShouldBurnInDay(false);
            spawned.setPersistent(true);
            spawned.setSilent(true);
            spawned.setAI(true);
            applyFerrymanStats(spawned, true);
            applyFerrymanVisualBase(spawned);
            syncFerrymanDisplay(spawned);
        });
        removeRottenGuardChickenJockeyMount(zombie);
        Bukkit.getScheduler().runTask(this, () -> removeRottenGuardChickenJockeyMount(zombie));
        return zombie;
    }

    private Endermite spawnPusBug(Location location) {
        return location.getWorld().spawn(location, Endermite.class, endermite -> {
            endermite.getPersistentDataContainer().set(monsterTypeKey, PersistentDataType.STRING, PUS_BUG_TYPE);
            endermite.customName(Component.text("脓包虫", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            endermite.setCustomNameVisible(true);
            endermite.setCanPickupItems(false);
            endermite.setRemoveWhenFarAway(false);
            endermite.setPlayerSpawned(false);
            endermite.setLifetimeTicks(0);
            endermite.setSilent(true);
            endermite.setAI(true);
            applyPusBugStats(endermite, true);
            applyPusBugVisualBase(endermite);
            syncPusBugDisplay(endermite);
        });
    }

    private Slime spawnTrainingDummy(Location location) {
        return location.getWorld().spawn(location, Slime.class, slime -> {
            slime.getPersistentDataContainer().set(monsterTypeKey, PersistentDataType.STRING, TRAINING_DUMMY_TYPE);
            slime.getPersistentDataContainer().set(trainingDummyMaxHealthKey, PersistentDataType.DOUBLE, TRAINING_DUMMY_DEFAULT_MAX_HEALTH);
            slime.getPersistentDataContainer().set(trainingDummyArmorKey, PersistentDataType.DOUBLE, TRAINING_DUMMY_DEFAULT_ARMOR);
            slime.customName(Component.text("测试木桩", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            slime.setCustomNameVisible(true);
            slime.setCanPickupItems(false);
            slime.setRemoveWhenFarAway(false);
            slime.setPersistent(true);
            slime.setSize(2);
            slime.setAI(false);
            slime.setSilent(true);
            applyTrainingDummyStats(slime, true);
            applyTrainingDummyVisualBase(slime);
            syncTrainingDummyDisplay(slime);
        });
    }

    private void tickCustomMonsters() {
        boolean updateCombatLogic = customMonsterTick++ % 20L == 0L;
        Set<UUID> liveRottenGuards = new HashSet<>();
        Set<UUID> liveGulpers = new HashSet<>();
        Set<UUID> liveFerrymen = new HashSet<>();
        Set<UUID> livePusBugs = new HashSet<>();
        Set<UUID> liveTrainingDummies = new HashSet<>();
        Set<UUID> liveCustomMonsters = new HashSet<>();
        Set<UUID> liveSplitDamageTargets = tickDungeonDamageRules();
        for (World world : Bukkit.getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                if (isGulper(zombie) && !zombie.isDead() && zombie.isValid()) {
                    liveGulpers.add(zombie.getUniqueId());
                    liveCustomMonsters.add(zombie.getUniqueId());
                    removeRottenGuardChickenJockeyMount(zombie);
                    applyGulperVisualBase(zombie);
                    syncGulperDisplay(zombie);
                    syncCustomMonsterHealthDisplay(zombie);
                    if (updateCombatLogic) {
                        applyGulperStats(zombie, false);
                        decayGulperHealth(zombie);
                        if (zombie.isDead() || !zombie.isValid()) {
                            continue;
                        }
                        if (zombie.getTarget() instanceof Player target && isValidMonsterTarget(zombie, target)) {
                            continue;
                        }
                        zombie.setTarget(nearestMonsterTarget(zombie));
                    }
                    continue;
                }
                if (isFerryman(zombie) && !zombie.isDead() && zombie.isValid()) {
                    liveFerrymen.add(zombie.getUniqueId());
                    liveCustomMonsters.add(zombie.getUniqueId());
                    removeRottenGuardChickenJockeyMount(zombie);
                    applyFerrymanVisualBase(zombie);
                    syncFerrymanDisplay(zombie);
                    if (dungeonRunByBoss(zombie.getUniqueId()) == null) {
                        syncCustomMonsterHealthDisplay(zombie);
                    }
                    if (updateCombatLogic) {
                        applyFerrymanStats(zombie, false);
                        if (zombie.getTarget() instanceof Player target && isValidMonsterTarget(zombie, target)) {
                            continue;
                        }
                        zombie.setTarget(nearestMonsterTarget(zombie));
                    }
                    continue;
                }
                if (!isRottenGuard(zombie) || zombie.isDead() || !zombie.isValid()) {
                    continue;
                }
                liveRottenGuards.add(zombie.getUniqueId());
                liveCustomMonsters.add(zombie.getUniqueId());
                removeRottenGuardChickenJockeyMount(zombie);
                applyRottenGuardVisualBase(zombie);
                syncRottenGuardDisplay(zombie);
                syncCustomMonsterHealthDisplay(zombie);
                if (!updateCombatLogic) {
                    continue;
                }
                applyRottenGuardStats(zombie, false);
                if (zombie.getTarget() instanceof Player target && isValidMonsterTarget(zombie, target)) {
                    continue;
                }
                zombie.setTarget(nearestMonsterTarget(zombie));
            }
            for (Endermite endermite : world.getEntitiesByClass(Endermite.class)) {
                if (!isPusBug(endermite) || endermite.isDead() || !endermite.isValid()) {
                    continue;
                }
                livePusBugs.add(endermite.getUniqueId());
                liveCustomMonsters.add(endermite.getUniqueId());
                endermite.setSilent(true);
                endermite.setAI(true);
                endermite.setLifetimeTicks(0);
                applyPusBugVisualBase(endermite);
                syncPusBugDisplay(endermite);
                syncCustomMonsterHealthDisplay(endermite);
                if (updateCombatLogic) {
                    applyPusBugStats(endermite, false);
                    if (endermite.getTarget() instanceof Player target && isValidMonsterTarget(endermite, target)) {
                        continue;
                    }
                    endermite.setTarget(nearestMonsterTarget(endermite));
                }
            }
            for (Slime slime : world.getEntitiesByClass(Slime.class)) {
                if (!isTrainingDummy(slime) || slime.isDead() || !slime.isValid()) {
                    continue;
                }
                liveTrainingDummies.add(slime.getUniqueId());
                liveCustomMonsters.add(slime.getUniqueId());
                liveSplitDamageTargets.add(slime.getUniqueId());
                applyTrainingDummyVisualBase(slime);
                syncTrainingDummyDisplay(slime);
                if (updateCombatLogic) {
                    pruneTrainingDummyDamageSamples(slime.getUniqueId());
                    applyTrainingDummyStats(slime, false);
                    syncCustomMonsterHealthDisplay(slime);
                }
            }
        }
        tickPusPools();
        removeMissingRottenGuardDisplays(liveRottenGuards);
        removeMissingGulperDisplays(liveGulpers);
        removeMissingFerrymanDisplays(liveFerrymen);
        removeMissingPusBugDisplays(livePusBugs);
        removeMissingTrainingDummyDisplays(liveTrainingDummies);
        removeMissingCustomMonsterHealthDisplays(liveCustomMonsters);
        removeMissingSplitDamageCooldowns(liveSplitDamageTargets);
        if (updateCombatLogic) {
            tickIdleDungeonSpawnPreviews();
        }
        tickDungeonRuns(updateCombatLogic);
    }

    private void applyRottenGuardVisualBase(Zombie zombie) {
        zombie.setInvisible(true);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        clearRottenGuardEquipment(zombie);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void applyGulperVisualBase(Zombie zombie) {
        zombie.setInvisible(true);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        clearRottenGuardEquipment(zombie);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void applyFerrymanVisualBase(Zombie zombie) {
        zombie.setInvisible(true);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        zombie.setShouldBurnInDay(false);
        clearRottenGuardEquipment(zombie);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void applyPusBugVisualBase(Endermite endermite) {
        endermite.setInvisible(true);
        endermite.setSilent(true);
        endermite.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void applyTrainingDummyVisualBase(Slime slime) {
        slime.setInvisible(true);
        slime.setSilent(true);
        slime.setAI(false);
        applySplitDamageTargetBase(slime);
        slime.setVelocity(new Vector(0.0D, slime.getVelocity().getY(), 0.0D));
        slime.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void clearRottenGuardEquipment(Zombie zombie) {
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(null);
        equipment.setChestplate(null);
        equipment.setLeggings(null);
        equipment.setBoots(null);
        equipment.setItemInMainHand(null);
        equipment.setItemInOffHand(null);
        equipment.setHelmetDropChance(0.0F);
        equipment.setChestplateDropChance(0.0F);
        equipment.setLeggingsDropChance(0.0F);
        equipment.setBootsDropChance(0.0F);
        equipment.setItemInMainHandDropChance(0.0F);
        equipment.setItemInOffHandDropChance(0.0F);
    }

    private void syncRottenGuardDisplay(Zombie zombie) {
        double speedSquared = zombie.getVelocity().setY(0.0D).lengthSquared();
        boolean moving = speedSquared > ROTTEN_GUARD_WALK_SPEED_SQUARED;
        boolean animated = moving || rottenGuardHasRecentAnimation(zombie.getUniqueId());
        double phase = customMonsterTick * 0.55D;
        float yaw = stableRottenGuardYaw(zombie, moving);
        for (RottenGuardPart part : RottenGuardPart.values()) {
            ItemDisplay display = rottenGuardDisplay(zombie, part);
            Location location = rottenGuardPartLocation(zombie, part, phase, speedSquared, yaw);
            configureRottenGuardDisplayBounds(display);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(animated ? 2 : 0);
            display.setTeleportDuration(animated ? 2 : 0);
            display.setTransformation(rottenGuardPartAnimation(zombie, part, phase, speedSquared));
            display.teleport(location);
        }
    }

    private boolean rottenGuardHasRecentAnimation(UUID uuid) {
        long tick = customMonsterTick;
        long attackAge = tick - rottenGuardAttackTicks.getOrDefault(uuid, -100L);
        long hurtAge = tick - rottenGuardHurtTicks.getOrDefault(uuid, -100L);
        return (attackAge >= 0L && attackAge < 8L) || (hurtAge >= 0L && hurtAge < 7L);
    }

    private float stableRottenGuardYaw(Zombie zombie, boolean moving) {
        UUID uuid = zombie.getUniqueId();
        float current = zombie.getBodyYaw();
        Float previous = rottenGuardBodyYaws.get(uuid);
        if (previous == null || moving || angularDistance(previous, current) > 8.0F) {
            rottenGuardBodyYaws.put(uuid, current);
            return current;
        }
        return previous;
    }

    private float angularDistance(float a, float b) {
        float distance = Math.abs(a - b) % 360.0F;
        return distance > 180.0F ? 360.0F - distance : distance;
    }

    private Location rottenGuardPartLocation(Zombie zombie, RottenGuardPart part, double phase, double speedSquared, float yaw) {
        float bob = speedSquared > ROTTEN_GUARD_WALK_SPEED_SQUARED ? (float) (Math.abs(Math.sin(phase)) * 0.035D) : 0.0F;
        Location location = zombie.getLocation().clone();
        double[] rotated = rotateRottenGuardOffset(part.offsetX, part.offsetZ, yaw);
        location.add(rotated[0], part.offsetY + bob, rotated[1]);
        location.setYaw(yaw);
        location.setPitch(0.0F);
        return location;
    }

    private double[] rotateRottenGuardOffset(double x, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new double[] {x * cos - z * sin, x * sin + z * cos};
    }

    private Transformation rottenGuardPartAnimation(Zombie zombie, RottenGuardPart part, double phase, double speedSquared) {
        long tick = customMonsterTick;
        UUID uuid = zombie.getUniqueId();
        float pitch = 0.0F;
        float yaw = 0.0F;
        float roll = 0.0F;

        if (speedSquared > ROTTEN_GUARD_WALK_SPEED_SQUARED) {
            float swing = (float) Math.sin(phase);
            float oppositeSwing = -swing;
            switch (part) {
                case RIGHT_ARM, LEFT_LEG -> pitch += swing * 0.45F;
                case LEFT_ARM, RIGHT_LEG -> pitch += oppositeSwing * 0.45F;
                case BODY -> roll += swing * 0.035F;
                case HEAD -> yaw += swing * 0.035F;
            }
        }

        long attackAge = tick - rottenGuardAttackTicks.getOrDefault(uuid, -100L);
        if (attackAge >= 0L && attackAge < 8L) {
            float progress = 1.0F - attackAge / 8.0F;
            if (part == RottenGuardPart.RIGHT_ARM || part == RottenGuardPart.LEFT_ARM) {
                pitch += -0.85F * progress;
            } else if (part == RottenGuardPart.BODY || part == RottenGuardPart.HEAD) {
                pitch += -0.18F * progress;
            }
        }

        long hurtAge = tick - rottenGuardHurtTicks.getOrDefault(uuid, -100L);
        if (hurtAge >= 0L && hurtAge < 7L) {
            float progress = 1.0F - hurtAge / 7.0F;
            roll += (hurtAge % 2L == 0L ? 0.18F : -0.18F) * progress;
            pitch += 0.10F * progress;
        }

        if (attackAge >= 8L) {
            rottenGuardAttackTicks.remove(uuid);
        }
        if (hurtAge >= 7L) {
            rottenGuardHurtTicks.remove(uuid);
        }

        return new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(pitch, 1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new AxisAngle4f(yaw == 0.0F ? roll : yaw, 0.0F, yaw == 0.0F ? 0.0F : 1.0F, yaw == 0.0F ? 1.0F : 0.0F));
    }

    private ItemDisplay rottenGuardDisplay(Zombie zombie, RottenGuardPart part) {
        EnumMap<RottenGuardPart, UUID> displayUuids = rottenGuardDisplays.computeIfAbsent(zombie.getUniqueId(), ignored -> new EnumMap<>(RottenGuardPart.class));
        UUID displayUuid = displayUuids.get(part);
        Entity existing = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (existing instanceof ItemDisplay display && display.isValid() && display.getWorld().equals(zombie.getWorld())) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        Location displayLocation = rottenGuardPartLocation(zombie, part, customMonsterTick * 0.55D, 0.0D, zombie.getBodyYaw());
        ItemDisplay display = zombie.getWorld().spawn(displayLocation, ItemDisplay.class, spawned -> {
            spawned.setItemStack(createRottenGuardDisplayItem(part));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setPersistent(false);
            spawned.customName(Component.text("朽败的卫兵", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(false);
            configureRottenGuardDisplayBounds(spawned);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.getPersistentDataContainer().set(monsterDisplayKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(monsterDisplayOwnerKey, PersistentDataType.STRING, zombie.getUniqueId().toString());
        });
        displayUuids.put(part, display.getUniqueId());
        return display;
    }

    private void configureRottenGuardDisplayBounds(ItemDisplay display) {
        display.setDisplayWidth(ROTTEN_GUARD_DISPLAY_PICK_SIZE);
        display.setDisplayHeight(ROTTEN_GUARD_DISPLAY_PICK_SIZE);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private ItemStack createRottenGuardDisplayItem(RottenGuardPart part) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("朽败的卫兵", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(rottenGuardPartModelKeys.get(part));
        item.setItemMeta(meta);
        return item;
    }

    private void removeRottenGuardDisplay(UUID zombieUuid) {
        removeCustomMonsterHealthDisplay(zombieUuid);
        rottenGuardAttackTicks.remove(zombieUuid);
        rottenGuardHurtTicks.remove(zombieUuid);
        rottenGuardBodyYaws.remove(zombieUuid);
        EnumMap<RottenGuardPart, UUID> displayUuids = rottenGuardDisplays.remove(zombieUuid);
        if (displayUuids != null) {
            for (UUID displayUuid : displayUuids.values()) {
                Entity display = Bukkit.getEntity(displayUuid);
                if (display != null) {
                    display.remove();
                }
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay candidate : world.getEntitiesByClass(ItemDisplay.class)) {
                if (zombieUuid.toString().equals(candidate.getPersistentDataContainer().get(monsterDisplayOwnerKey, PersistentDataType.STRING))) {
                    candidate.remove();
                }
            }
        }
    }

    private void removeMissingRottenGuardDisplays(Set<UUID> liveRottenGuards) {
        for (UUID zombieUuid : new HashSet<>(rottenGuardDisplays.keySet())) {
            if (!liveRottenGuards.contains(zombieUuid)) {
                removeRottenGuardDisplay(zombieUuid);
                continue;
            }
            EnumMap<RottenGuardPart, UUID> displays = rottenGuardDisplays.get(zombieUuid);
            if (displays == null) {
                continue;
            }
            displays.values().removeIf(displayUuid -> {
                Entity display = Bukkit.getEntity(displayUuid);
                return display == null || !display.isValid();
            });
            if (displays.isEmpty()) {
                rottenGuardDisplays.remove(zombieUuid);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getPersistentDataContainer().has(monsterDisplayKey, PersistentDataType.BYTE)) {
                    continue;
                }
                String owner = display.getPersistentDataContainer().get(monsterDisplayOwnerKey, PersistentDataType.STRING);
                if (owner == null) {
                    display.remove();
                    continue;
                }
                try {
                    if (!liveRottenGuards.contains(UUID.fromString(owner))) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllRottenGuardDisplays() {
        rottenGuardDisplays.clear();
        rottenGuardAttackTicks.clear();
        rottenGuardHurtTicks.clear();
        rottenGuardBodyYaws.clear();
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(monsterDisplayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void syncGulperDisplay(Zombie zombie) {
        ItemDisplay display = gulperDisplay(zombie);
        Location location = gulperDisplayLocation(zombie);
        configureGulperDisplayBounds(display);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
        syncDisplayFire(display, zombie);
        display.teleport(location);
    }

    private Location gulperDisplayLocation(Zombie zombie) {
        Location location = zombie.getLocation().clone();
        location.add(0.0D, 1.0D, 0.0D);
        location.setYaw(zombie.getBodyYaw());
        location.setPitch(0.0F);
        return location;
    }

    private ItemDisplay gulperDisplay(Zombie zombie) {
        UUID displayUuid = gulperDisplays.get(zombie.getUniqueId());
        Entity existing = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (existing instanceof ItemDisplay display && display.isValid() && display.getWorld().equals(zombie.getWorld())) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        ItemDisplay display = zombie.getWorld().spawn(gulperDisplayLocation(zombie), ItemDisplay.class, spawned -> {
            spawned.setItemStack(createGulperDisplayItem());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setPersistent(false);
            spawned.customName(Component.text("啜食者", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(false);
            configureGulperDisplayBounds(spawned);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.getPersistentDataContainer().set(gulperDisplayKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(gulperDisplayOwnerKey, PersistentDataType.STRING, zombie.getUniqueId().toString());
        });
        gulperDisplays.put(zombie.getUniqueId(), display.getUniqueId());
        return display;
    }

    private void configureGulperDisplayBounds(ItemDisplay display) {
        display.setDisplayWidth(GULPER_DISPLAY_PICK_SIZE);
        display.setDisplayHeight(GULPER_DISPLAY_PICK_SIZE);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private void syncDisplayFire(Entity display, Entity owner) {
        display.setVisualFire(owner.getFireTicks() > 0);
    }

    private ItemStack createGulperDisplayItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("啜食者", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(gulperModelKey);
        item.setItemMeta(meta);
        return item;
    }

    private void removeGulperDisplay(UUID zombieUuid) {
        removeCustomMonsterHealthDisplay(zombieUuid);
        UUID displayUuid = gulperDisplays.remove(zombieUuid);
        Entity display = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (display != null) {
            display.remove();
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay candidate : world.getEntitiesByClass(ItemDisplay.class)) {
                if (zombieUuid.toString().equals(candidate.getPersistentDataContainer().get(gulperDisplayOwnerKey, PersistentDataType.STRING))) {
                    candidate.remove();
                }
            }
        }
    }

    private void removeMissingGulperDisplays(Set<UUID> liveGulpers) {
        for (UUID zombieUuid : new HashSet<>(gulperDisplays.keySet())) {
            if (!liveGulpers.contains(zombieUuid)) {
                removeGulperDisplay(zombieUuid);
                continue;
            }
            Entity display = Bukkit.getEntity(gulperDisplays.get(zombieUuid));
            if (display == null || !display.isValid()) {
                gulperDisplays.remove(zombieUuid);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getPersistentDataContainer().has(gulperDisplayKey, PersistentDataType.BYTE)) {
                    continue;
                }
                String owner = display.getPersistentDataContainer().get(gulperDisplayOwnerKey, PersistentDataType.STRING);
                if (owner == null) {
                    display.remove();
                    continue;
                }
                try {
                    if (!liveGulpers.contains(UUID.fromString(owner))) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllGulperDisplays() {
        gulperDisplays.clear();
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(gulperDisplayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void syncFerrymanDisplay(Zombie zombie) {
        ItemDisplay display = ferrymanDisplay(zombie);
        Location location = ferrymanDisplayLocation(zombie);
        configureFerrymanDisplayBounds(display);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
        syncDisplayFire(display, zombie);
        display.setTransformation(ferrymanAnimation(zombie));
        display.teleport(location);
        playFerrymanAmbientEffects(zombie);
    }

    private Location ferrymanDisplayLocation(Zombie zombie) {
        Vector velocity = zombie.getVelocity();
        double horizontalSpeedSquared = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        double moveAmount = Math.min(1.0D, Math.sqrt(horizontalSpeedSquared) / Math.max(0.01D, FERRYMAN_MOVEMENT_SPEED));
        double gait = Math.sin(customMonsterTick * 0.42D);
        double bob = Math.sin(customMonsterTick * 0.12D) * 0.035D + Math.abs(gait) * 0.055D * moveAmount;
        Location location = zombie.getLocation().clone();
        if (moveAmount > 0.04D) {
            double yawRadians = Math.toRadians(zombie.getBodyYaw());
            double sideStep = Math.sin(customMonsterTick * 0.42D) * 0.055D * moveAmount;
            location.add(Math.cos(yawRadians) * sideStep, 0.0D, Math.sin(yawRadians) * sideStep);
        }
        location.add(0.0D, 1.05D + bob, 0.0D);
        location.setYaw(zombie.getBodyYaw());
        location.setPitch(0.0F);
        return location;
    }

    private Transformation ferrymanAnimation(Zombie zombie) {
        long tick = customMonsterTick;
        UUID uuid = zombie.getUniqueId();
        Vector velocity = zombie.getVelocity();
        double horizontalSpeedSquared = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        float moveAmount = (float) Math.min(1.0D, Math.sqrt(horizontalSpeedSquared) / Math.max(0.01D, FERRYMAN_MOVEMENT_SPEED));
        float gait = (float) Math.sin(tick * 0.42D);
        float pitch = (float) Math.sin(tick * 0.08D) * 0.025F - 0.10F * moveAmount;
        float roll = (float) Math.sin(tick * 0.10D) * 0.035F + gait * 0.085F * moveAmount;
        long attackAge = tick - ferrymanAttackTicks.getOrDefault(uuid, -100L);
        if (attackAge >= 0L && attackAge < 9L) {
            float progress = 1.0F - attackAge / 9.0F;
            pitch -= 0.18F * progress;
            roll += 0.12F * progress;
        }
        float lift = 0.0F;
        DungeonRun run = dungeonRunByBoss(uuid);
        BossSkillCast cast = run == null ? null : run.activeBossSkill;
        if (cast != null && cast.type == BossSkillType.SOULFIRE && cast.phase == BossSkillPhase.AFTERSHOCK) {
            float progress = Math.max(0.0F, Math.min(1.0F, (float) cast.phaseAge(tick) / FERRYMAN_SOULFIRE_AFTERSHOCK_TICKS));
            lift = (float) Math.sin(progress * Math.PI) * 0.32F;
            pitch -= 0.34F * (1.0F - progress);
            roll += (float) Math.sin(progress * Math.PI * 4.0D) * 0.06F;
        }
        long hurtAge = tick - ferrymanHurtTicks.getOrDefault(uuid, -100L);
        if (hurtAge >= 0L && hurtAge < 7L) {
            roll += (hurtAge % 2L == 0L ? 0.10F : -0.10F) * (1.0F - hurtAge / 7.0F);
        }
        if (attackAge >= 9L) {
            ferrymanAttackTicks.remove(uuid);
        }
        if (hurtAge >= 7L) {
            ferrymanHurtTicks.remove(uuid);
        }
        return new Transformation(
                new Vector3f(0.0F, lift, 0.0F),
                new AxisAngle4f(pitch, 1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new AxisAngle4f(roll, 0.0F, 0.0F, 1.0F));
    }

    private void playFerrymanAmbientEffects(Zombie zombie) {
        if (customMonsterTick % 5L != 0L) {
            return;
        }
        Location center = zombie.getLocation().clone().add(0.0D, 0.12D, 0.0D);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.SMOKE, center, 3, 0.42D, 0.08D, 0.42D, 0.01D);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(76, 52, 91), 0.9F);
        world.spawnParticle(Particle.DUST, center.clone().add(0.0D, 1.1D, 0.0D), 2, 0.35D, 0.45D, 0.35D, 0.0D, dust);
    }

    private ItemDisplay ferrymanDisplay(Zombie zombie) {
        UUID displayUuid = ferrymanDisplays.get(zombie.getUniqueId());
        Entity existing = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (existing instanceof ItemDisplay display && display.isValid() && display.getWorld().equals(zombie.getWorld())) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        ItemDisplay display = zombie.getWorld().spawn(ferrymanDisplayLocation(zombie), ItemDisplay.class, spawned -> {
            spawned.setItemStack(createFerrymanDisplayItem());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setPersistent(false);
            spawned.customName(Component.text("引渡人", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(false);
            configureFerrymanDisplayBounds(spawned);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.getPersistentDataContainer().set(ferrymanDisplayKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(ferrymanDisplayOwnerKey, PersistentDataType.STRING, zombie.getUniqueId().toString());
        });
        ferrymanDisplays.put(zombie.getUniqueId(), display.getUniqueId());
        return display;
    }

    private void configureFerrymanDisplayBounds(ItemDisplay display) {
        display.setDisplayWidth(FERRYMAN_DISPLAY_PICK_SIZE);
        display.setDisplayHeight(FERRYMAN_DISPLAY_PICK_SIZE);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private ItemStack createFerrymanDisplayItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("引渡人", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(ferrymanModelKey);
        item.setItemMeta(meta);
        return item;
    }

    private void removeFerrymanDisplay(UUID zombieUuid) {
        removeCustomMonsterHealthDisplay(zombieUuid);
        ferrymanAttackTicks.remove(zombieUuid);
        ferrymanHurtTicks.remove(zombieUuid);
        UUID displayUuid = ferrymanDisplays.remove(zombieUuid);
        Entity display = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (display != null) {
            display.remove();
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay candidate : world.getEntitiesByClass(ItemDisplay.class)) {
                if (zombieUuid.toString().equals(candidate.getPersistentDataContainer().get(ferrymanDisplayOwnerKey, PersistentDataType.STRING))) {
                    candidate.remove();
                }
            }
        }
    }

    private void removeMissingFerrymanDisplays(Set<UUID> liveFerrymen) {
        for (UUID zombieUuid : new HashSet<>(ferrymanDisplays.keySet())) {
            if (!liveFerrymen.contains(zombieUuid)) {
                removeFerrymanDisplay(zombieUuid);
                continue;
            }
            Entity display = Bukkit.getEntity(ferrymanDisplays.get(zombieUuid));
            if (display == null || !display.isValid()) {
                ferrymanDisplays.remove(zombieUuid);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getPersistentDataContainer().has(ferrymanDisplayKey, PersistentDataType.BYTE)) {
                    continue;
                }
                String owner = display.getPersistentDataContainer().get(ferrymanDisplayOwnerKey, PersistentDataType.STRING);
                if (owner == null) {
                    display.remove();
                    continue;
                }
                try {
                    if (!liveFerrymen.contains(UUID.fromString(owner))) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllFerrymanDisplays() {
        ferrymanDisplays.clear();
        ferrymanAttackTicks.clear();
        ferrymanHurtTicks.clear();
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(ferrymanDisplayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void syncPusBugDisplay(Endermite endermite) {
        ItemDisplay display = pusBugDisplay(endermite);
        Location location = pusBugDisplayLocation(endermite);
        configurePusBugDisplayBounds(display);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
        display.teleport(location);
    }

    private Location pusBugDisplayLocation(Endermite endermite) {
        Location location = endermite.getLocation().clone();
        location.add(0.0D, 0.68D, 0.0D);
        location.setYaw(endermite.getBodyYaw());
        location.setPitch(0.0F);
        return location;
    }

    private ItemDisplay pusBugDisplay(Endermite endermite) {
        UUID displayUuid = pusBugDisplays.get(endermite.getUniqueId());
        Entity existing = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (existing instanceof ItemDisplay display && display.isValid() && display.getWorld().equals(endermite.getWorld())) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        ItemDisplay display = endermite.getWorld().spawn(pusBugDisplayLocation(endermite), ItemDisplay.class, spawned -> {
            spawned.setItemStack(createPusBugDisplayItem());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setPersistent(false);
            spawned.customName(Component.text("脓包虫", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(false);
            configurePusBugDisplayBounds(spawned);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.getPersistentDataContainer().set(pusBugDisplayKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(pusBugDisplayOwnerKey, PersistentDataType.STRING, endermite.getUniqueId().toString());
        });
        pusBugDisplays.put(endermite.getUniqueId(), display.getUniqueId());
        return display;
    }

    private void configurePusBugDisplayBounds(ItemDisplay display) {
        display.setDisplayWidth(PUS_BUG_DISPLAY_PICK_SIZE);
        display.setDisplayHeight(PUS_BUG_DISPLAY_PICK_SIZE);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private ItemStack createPusBugDisplayItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("脓包虫", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(pusBugModelKey);
        item.setItemMeta(meta);
        return item;
    }

    private void removePusBugDisplay(UUID endermiteUuid) {
        removeCustomMonsterHealthDisplay(endermiteUuid);
        UUID displayUuid = pusBugDisplays.remove(endermiteUuid);
        Entity display = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (display != null) {
            display.remove();
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay candidate : world.getEntitiesByClass(ItemDisplay.class)) {
                if (endermiteUuid.toString().equals(candidate.getPersistentDataContainer().get(pusBugDisplayOwnerKey, PersistentDataType.STRING))) {
                    candidate.remove();
                }
            }
        }
    }

    private void removeMissingPusBugDisplays(Set<UUID> livePusBugs) {
        for (UUID endermiteUuid : new HashSet<>(pusBugDisplays.keySet())) {
            if (!livePusBugs.contains(endermiteUuid)) {
                removePusBugDisplay(endermiteUuid);
                continue;
            }
            Entity display = Bukkit.getEntity(pusBugDisplays.get(endermiteUuid));
            if (display == null || !display.isValid()) {
                pusBugDisplays.remove(endermiteUuid);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getPersistentDataContainer().has(pusBugDisplayKey, PersistentDataType.BYTE)) {
                    continue;
                }
                String owner = display.getPersistentDataContainer().get(pusBugDisplayOwnerKey, PersistentDataType.STRING);
                if (owner == null) {
                    display.remove();
                    continue;
                }
                try {
                    if (!livePusBugs.contains(UUID.fromString(owner))) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllPusBugDisplays() {
        pusBugDisplays.clear();
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(pusBugDisplayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void syncTrainingDummyDisplay(Slime slime) {
        float yaw = slime.getLocation().getYaw();
        for (TrainingDummyPart part : TrainingDummyPart.values()) {
            ItemDisplay display = trainingDummyDisplay(slime, part);
            Location location = trainingDummyPartLocation(slime, part, yaw);
            configureTrainingDummyDisplayBounds(display);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            display.setTransformation(trainingDummyPartTransformation());
            syncDisplayFire(display, slime);
            display.teleport(location);
        }
    }

    private Location trainingDummyPartLocation(Slime slime, TrainingDummyPart part, float yaw) {
        Location location = slime.getLocation().clone();
        double[] rotated = rotateRottenGuardOffset(part.offsetX, part.offsetZ, yaw);
        location.add(rotated[0], part.offsetY, rotated[1]);
        location.setYaw(yaw);
        location.setPitch(0.0F);
        return location;
    }

    private Transformation trainingDummyPartTransformation() {
        return new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F));
    }

    private ItemDisplay trainingDummyDisplay(Slime slime, TrainingDummyPart part) {
        EnumMap<TrainingDummyPart, UUID> displayUuids = trainingDummyDisplays.computeIfAbsent(slime.getUniqueId(), ignored -> new EnumMap<>(TrainingDummyPart.class));
        UUID displayUuid = displayUuids.get(part);
        Entity existing = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (existing instanceof ItemDisplay display && display.isValid() && display.getWorld().equals(slime.getWorld())) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        Location displayLocation = trainingDummyPartLocation(slime, part, slime.getLocation().getYaw());
        ItemDisplay display = slime.getWorld().spawn(displayLocation, ItemDisplay.class, spawned -> {
            spawned.setItemStack(createTrainingDummyDisplayItem(part));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setPersistent(false);
            spawned.customName(Component.text("测试木桩", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            spawned.setCustomNameVisible(false);
            configureTrainingDummyDisplayBounds(spawned);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.getPersistentDataContainer().set(trainingDummyDisplayKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(trainingDummyDisplayOwnerKey, PersistentDataType.STRING, slime.getUniqueId().toString());
        });
        displayUuids.put(part, display.getUniqueId());
        return display;
    }

    private void configureTrainingDummyDisplayBounds(ItemDisplay display) {
        display.setDisplayWidth(TRAINING_DUMMY_DISPLAY_PICK_SIZE);
        display.setDisplayHeight(2.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private ItemStack createTrainingDummyDisplayItem(TrainingDummyPart part) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("测试木桩", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(trainingDummyPartModelKeys.get(part));
        item.setItemMeta(meta);
        return item;
    }

    private void removeTrainingDummyDisplay(UUID slimeUuid) {
        removeCustomMonsterHealthDisplay(slimeUuid);
        trainingDummyDamageSamples.remove(slimeUuid);
        splitDamageCooldowns.remove(slimeUuid);
        EnumMap<TrainingDummyPart, UUID> displayUuids = trainingDummyDisplays.remove(slimeUuid);
        if (displayUuids != null) {
            for (UUID displayUuid : displayUuids.values()) {
                Entity display = Bukkit.getEntity(displayUuid);
                if (display != null) {
                    display.remove();
                }
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay candidate : world.getEntitiesByClass(ItemDisplay.class)) {
                if (slimeUuid.toString().equals(candidate.getPersistentDataContainer().get(trainingDummyDisplayOwnerKey, PersistentDataType.STRING))) {
                    candidate.remove();
                }
            }
        }
    }

    private void removeMissingTrainingDummyDisplays(Set<UUID> liveTrainingDummies) {
        for (UUID slimeUuid : new HashSet<>(trainingDummyDisplays.keySet())) {
            if (!liveTrainingDummies.contains(slimeUuid)) {
                removeTrainingDummyDisplay(slimeUuid);
                continue;
            }
            EnumMap<TrainingDummyPart, UUID> displays = trainingDummyDisplays.get(slimeUuid);
            if (displays == null) {
                continue;
            }
            displays.values().removeIf(displayUuid -> {
                Entity display = Bukkit.getEntity(displayUuid);
                return display == null || !display.isValid();
            });
            if (displays.isEmpty()) {
                trainingDummyDisplays.remove(slimeUuid);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getPersistentDataContainer().has(trainingDummyDisplayKey, PersistentDataType.BYTE)) {
                    continue;
                }
                String owner = display.getPersistentDataContainer().get(trainingDummyDisplayOwnerKey, PersistentDataType.STRING);
                if (owner == null) {
                    display.remove();
                    continue;
                }
                try {
                    if (!liveTrainingDummies.contains(UUID.fromString(owner))) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllTrainingDummyDisplays() {
        trainingDummyDisplays.clear();
        trainingDummyDamageSamples.clear();
        splitDamageCooldowns.clear();
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(trainingDummyDisplayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private Slime trainingDummyOwner(Entity display) {
        String owner = display.getPersistentDataContainer().get(trainingDummyDisplayOwnerKey, PersistentDataType.STRING);
        if (owner == null) {
            return null;
        }
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(owner));
            return isTrainingDummy(entity) ? (Slime) entity : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void syncCustomMonsterHealthDisplay(LivingEntity monster) {
        TextDisplay display = customMonsterHealthDisplay(monster);
        display.text(customMonsterHealthText(monster));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
        display.teleport(customMonsterHealthDisplayLocation(monster));
    }

    private TextDisplay customMonsterHealthDisplay(LivingEntity monster) {
        UUID displayUuid = customMonsterHealthDisplays.get(monster.getUniqueId());
        Entity existing = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (existing instanceof TextDisplay display && display.isValid() && display.getWorld().equals(monster.getWorld())) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        TextDisplay display = monster.getWorld().spawn(customMonsterHealthDisplayLocation(monster), TextDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setLineWidth(96);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.getPersistentDataContainer().set(monsterHealthDisplayKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(monsterHealthDisplayOwnerKey, PersistentDataType.STRING, monster.getUniqueId().toString());
        });
        customMonsterHealthDisplays.put(monster.getUniqueId(), display.getUniqueId());
        return display;
    }

    private Location customMonsterHealthDisplayLocation(LivingEntity monster) {
        double height = isTrainingDummy(monster)
                ? TRAINING_DUMMY_HEALTH_DISPLAY_HEIGHT
                : (isPusBug(monster) ? PUS_BUG_HEALTH_DISPLAY_HEIGHT : (isGulper(monster) ? GULPER_HEALTH_DISPLAY_HEIGHT
                : (isFerryman(monster) ? FERRYMAN_HEALTH_DISPLAY_HEIGHT : ROTTEN_GUARD_HEALTH_DISPLAY_HEIGHT)));
        Location location = monster.getLocation().clone().add(0.0D, height, 0.0D);
        location.setPitch(0.0F);
        return location;
    }

    private Component customMonsterHealthText(LivingEntity monster) {
        if (isTrainingDummy(monster)) {
            return Component.text("DPS：", NamedTextColor.YELLOW)
                    .append(Component.text("❤×", NamedTextColor.RED))
                    .append(Component.text(formatHealthValue(trainingDummyDps(monster.getUniqueId())), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false);
        }
        double maxHealth = maxHealth(monster);
        double health = Math.max(0.0D, Math.min(monster.getHealth(), maxHealth));
        return Component.text("❤×", NamedTextColor.RED)
                .append(Component.text(formatHealthValue(health) + "/" + formatHealthValue(maxHealth), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private double maxHealth(LivingEntity monster) {
        AttributeInstance attribute = monster.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, monster.getHealth()) : attribute.getValue();
    }

    private String formatHealthValue(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.05D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void removeCustomMonsterHealthDisplay(UUID monsterUuid) {
        UUID displayUuid = customMonsterHealthDisplays.remove(monsterUuid);
        Entity display = displayUuid == null ? null : Bukkit.getEntity(displayUuid);
        if (display != null) {
            display.remove();
        }
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay candidate : world.getEntitiesByClass(TextDisplay.class)) {
                if (monsterUuid.toString().equals(candidate.getPersistentDataContainer().get(monsterHealthDisplayOwnerKey, PersistentDataType.STRING))) {
                    candidate.remove();
                }
            }
        }
    }

    private void removeMissingCustomMonsterHealthDisplays(Set<UUID> liveCustomMonsters) {
        for (UUID monsterUuid : new HashSet<>(customMonsterHealthDisplays.keySet())) {
            if (!liveCustomMonsters.contains(monsterUuid)) {
                removeCustomMonsterHealthDisplay(monsterUuid);
                continue;
            }
            Entity display = Bukkit.getEntity(customMonsterHealthDisplays.get(monsterUuid));
            if (display == null || !display.isValid()) {
                customMonsterHealthDisplays.remove(monsterUuid);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (!display.getPersistentDataContainer().has(monsterHealthDisplayKey, PersistentDataType.BYTE)) {
                    continue;
                }
                String owner = display.getPersistentDataContainer().get(monsterHealthDisplayOwnerKey, PersistentDataType.STRING);
                if (owner == null) {
                    display.remove();
                    continue;
                }
                try {
                    if (!liveCustomMonsters.contains(UUID.fromString(owner))) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllCustomMonsterHealthDisplays() {
        customMonsterHealthDisplays.clear();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(monsterHealthDisplayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void applyTrainingDummyStats(Slime slime, boolean heal) {
        double maxHealth = trainingDummyMaxHealth(slime);
        slime.setAI(false);
        slime.setSilent(true);
        slime.setSize(2);
        setAttribute(slime, Attribute.MAX_HEALTH, maxHealth);
        setAttribute(slime, Attribute.ARMOR, trainingDummyArmor(slime));
        setAttribute(slime, Attribute.KNOCKBACK_RESISTANCE, 1.0D);
        if (heal || slime.getHealth() < maxHealth) {
            slime.setHealth(maxHealth);
        }
    }

    private double trainingDummyMaxHealth(Slime slime) {
        Double value = slime.getPersistentDataContainer().get(trainingDummyMaxHealthKey, PersistentDataType.DOUBLE);
        return clampTrainingDummyHealth(value == null ? TRAINING_DUMMY_DEFAULT_MAX_HEALTH : value);
    }

    private double trainingDummyArmor(Slime slime) {
        Double value = slime.getPersistentDataContainer().get(trainingDummyArmorKey, PersistentDataType.DOUBLE);
        return clampTrainingDummyArmor(value == null ? TRAINING_DUMMY_DEFAULT_ARMOR : value);
    }

    private void setTrainingDummyMaxHealth(Slime slime, double value) {
        slime.getPersistentDataContainer().set(trainingDummyMaxHealthKey, PersistentDataType.DOUBLE, clampTrainingDummyHealth(value));
        applyTrainingDummyStats(slime, true);
    }

    private void setTrainingDummyArmor(Slime slime, double value) {
        slime.getPersistentDataContainer().set(trainingDummyArmorKey, PersistentDataType.DOUBLE, clampTrainingDummyArmor(value));
        applyTrainingDummyStats(slime, false);
    }

    private double clampTrainingDummyHealth(double value) {
        return Math.max(1.0D, Math.min(10000.0D, value));
    }

    private double clampTrainingDummyArmor(double value) {
        return Math.max(0.0D, Math.min(30.0D, value));
    }

    private Set<String> trainingDummyTags(Slime slime) {
        String text = slime.getPersistentDataContainer().get(trainingDummyTagsKey, PersistentDataType.STRING);
        Set<String> tags = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tags;
        }
        for (String tag : text.split(",", -1)) {
            String normalized = normalizeTrainingDummyTag(tag);
            if (!normalized.isBlank()) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    private void toggleTrainingDummyTag(Slime slime, String tag) {
        Set<String> tags = trainingDummyTags(slime);
        String normalized = normalizeTrainingDummyTag(tag);
        if (tags.contains(normalized)) {
            tags.remove(normalized);
        } else {
            tags.add(normalized);
        }
        slime.getPersistentDataContainer().set(trainingDummyTagsKey, PersistentDataType.STRING, String.join(",", tags.stream().sorted().toList()));
    }

    private String normalizeTrainingDummyTag(String tag) {
        String normalized = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        if (TRAINING_DUMMY_TAG_UNDEAD.equals(normalized)
                || TRAINING_DUMMY_TAG_ARTHROPOD.equals(normalized)
                || TRAINING_DUMMY_TAG_AQUATIC.equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private void recordTrainingDummyDamage(UUID uuid, double damage) {
        List<TrainingDummyDamageSample> samples = trainingDummyDamageSamples.computeIfAbsent(uuid, ignored -> new ArrayList<>());
        samples.add(new TrainingDummyDamageSample(customMonsterTick, damage));
        pruneTrainingDummyDamageSamples(uuid);
    }

    private void pruneTrainingDummyDamageSamples(UUID uuid) {
        List<TrainingDummyDamageSample> samples = trainingDummyDamageSamples.get(uuid);
        if (samples == null) {
            return;
        }
        long oldestTick = customMonsterTick - TRAINING_DUMMY_DPS_WINDOW_TICKS;
        samples.removeIf(sample -> sample.tick() < oldestTick);
        if (samples.isEmpty()) {
            trainingDummyDamageSamples.remove(uuid);
        }
    }

    private double trainingDummyDps(UUID uuid) {
        pruneTrainingDummyDamageSamples(uuid);
        List<TrainingDummyDamageSample> samples = trainingDummyDamageSamples.get(uuid);
        if (samples == null || samples.isEmpty()) {
            return 0.0D;
        }
        double damage = 0.0D;
        for (TrainingDummyDamageSample sample : samples) {
            damage += sample.damage();
        }
        return damage / 60.0D;
    }

    private void applyTrainingDummyTagBonus(Slime dummy, EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        Set<String> tags = trainingDummyTags(dummy);
        if (tags.isEmpty()) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        double bonus = 0.0D;
        if (tags.contains(TRAINING_DUMMY_TAG_UNDEAD)) {
            bonus += weapon.getEnchantmentLevel(Enchantment.SMITE) * 2.5D;
        }
        if (tags.contains(TRAINING_DUMMY_TAG_ARTHROPOD)) {
            bonus += weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS) * 2.5D;
        }
        if (tags.contains(TRAINING_DUMMY_TAG_AQUATIC)) {
            bonus += weapon.getEnchantmentLevel(Enchantment.IMPALING) * 2.5D;
        }
        if (bonus > 0.0D) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    private void applyPusBugStats(Endermite endermite, boolean heal) {
        endermite.setAI(true);
        endermite.setLifetimeTicks(0);
        setAttribute(endermite, Attribute.MAX_HEALTH, PUS_BUG_MAX_HEALTH);
        setAttribute(endermite, Attribute.ARMOR, PUS_BUG_ARMOR);
        setAttribute(endermite, Attribute.ATTACK_DAMAGE, PUS_BUG_ATTACK_DAMAGE);
        setAttribute(endermite, Attribute.FOLLOW_RANGE, PUS_BUG_FOLLOW_RANGE);
        setAttribute(endermite, Attribute.MOVEMENT_SPEED, PUS_BUG_MOVEMENT_SPEED);
        setAttribute(endermite, Attribute.KNOCKBACK_RESISTANCE, PUS_BUG_KNOCKBACK_RESISTANCE);
        setAttribute(endermite, Attribute.JUMP_STRENGTH, PUS_BUG_JUMP_STRENGTH);
        if (heal) {
            endermite.setHealth(PUS_BUG_MAX_HEALTH);
        }
    }

    private void applyGulperStats(Zombie zombie, boolean heal) {
        zombie.setAI(true);
        zombie.setSilent(true);
        zombie.setAdult();
        setAttribute(zombie, Attribute.MAX_HEALTH, GULPER_MAX_HEALTH);
        setAttribute(zombie, Attribute.ARMOR, GULPER_ARMOR);
        setAttribute(zombie, Attribute.ATTACK_DAMAGE, GULPER_ATTACK_DAMAGE);
        setAttribute(zombie, Attribute.FOLLOW_RANGE, GULPER_FOLLOW_RANGE);
        setAttribute(zombie, Attribute.MOVEMENT_SPEED, GULPER_MOVEMENT_SPEED);
        setAttribute(zombie, Attribute.KNOCKBACK_RESISTANCE, GULPER_KNOCKBACK_RESISTANCE);
        if (heal) {
            zombie.setHealth(GULPER_MAX_HEALTH);
        }
    }

    private void applyFerrymanStats(Zombie zombie, boolean heal) {
        zombie.setAI(true);
        zombie.setSilent(true);
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        setAttribute(zombie, Attribute.MAX_HEALTH, FERRYMAN_MAX_HEALTH);
        setAttribute(zombie, Attribute.ARMOR, FERRYMAN_ARMOR);
        setAttribute(zombie, Attribute.ATTACK_DAMAGE, FERRYMAN_ATTACK_DAMAGE);
        setAttribute(zombie, Attribute.FOLLOW_RANGE, FERRYMAN_FOLLOW_RANGE);
        setAttribute(zombie, Attribute.MOVEMENT_SPEED, FERRYMAN_MOVEMENT_SPEED);
        setAttribute(zombie, Attribute.KNOCKBACK_RESISTANCE, FERRYMAN_KNOCKBACK_RESISTANCE);
        if (heal) {
            zombie.setHealth(FERRYMAN_MAX_HEALTH);
        }
    }

    private void decayGulperHealth(Zombie zombie) {
        if (zombie.isDead() || !zombie.isValid()) {
            return;
        }
        if (zombie.getHealth() <= GULPER_HEALTH_DECAY_PER_SECOND) {
            killCustomMonsterSilently(zombie);
            return;
        }
        zombie.setHealth(Math.max(0.0D, zombie.getHealth() - GULPER_HEALTH_DECAY_PER_SECOND));
    }

    private void applyGulperDrain(Zombie gulper, Player target) {
        if (!target.isOnline() || target.isDead()) {
            return;
        }
        float saturation = target.getSaturation();
        if (saturation > 0.0F) {
            target.setSaturation((float) Math.max(0.0D, saturation - GULPER_SATURATION_DRAIN));
        } else if (target.getFoodLevel() > 0) {
            target.setFoodLevel(Math.max(0, target.getFoodLevel() - GULPER_FOOD_DRAIN));
        } else if (target.getHealth() > 0.0D) {
            target.setHealth(Math.max(0.0D, target.getHealth() - GULPER_HEALTH_DRAIN));
        }
        AttributeInstance maxHealth = gulper.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealth == null ? GULPER_MAX_HEALTH : maxHealth.getValue();
        gulper.setHealth(Math.min(cap, gulper.getHealth() + GULPER_ATTACK_HEAL));
    }

    private void killCustomMonsterSilently(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        entity.setSilent(true);
        if (isRottenGuard(entity)) {
            removeRottenGuardDisplay(uuid);
            spawnExperience(entity.getLocation(), 10);
        } else if (isGulper(entity)) {
            removeGulperDisplay(uuid);
            spawnExperience(entity.getLocation(), 15);
        } else if (isFerryman(entity)) {
            removeFerrymanDisplay(uuid);
            spawnExperience(entity.getLocation(), FERRYMAN_EXPERIENCE);
        } else if (isPusBug(entity)) {
            removePusBugDisplay(uuid);
            triggerPusBugDeath(entity);
            spawnExperience(entity.getLocation(), 6);
        } else if (isTrainingDummy(entity)) {
            removeTrainingDummyDisplay(uuid);
        }
        markDungeonMobRemoved(uuid);
        entity.remove();
    }

    private void spawnExperience(Location location, int amount) {
        World world = location.getWorld();
        if (world == null || amount <= 0) {
            return;
        }
        world.spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
    }

    private void markDungeonMobRemoved(UUID uuid) {
        for (DungeonRun run : dungeonRunsByWorld.values()) {
            run.liveMobs.remove(uuid);
        }
    }

    private void triggerPusBugDeath(LivingEntity bug) {
        Location center = bug.getLocation().add(0.0D, 0.3D, 0.0D);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.75F, 1.45F);
        world.playSound(center, Sound.ENTITY_SLIME_SQUISH, 1.0F, 0.55F);
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticle(Particle.ITEM_SLIME, center, 48, 0.9D, 0.45D, 0.9D, 0.12D);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(140, 210, 24), 1.4F);
        world.spawnParticle(Particle.DUST, center, 60, 1.2D, 0.25D, 1.2D, 0.02D, dust);
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) > PUS_BUG_EXPLOSION_RADIUS * PUS_BUG_EXPLOSION_RADIUS) {
                continue;
            }
            player.damage(PUS_BUG_EXPLOSION_DAMAGE, bug);
            Vector push = player.getLocation().toVector().subtract(center.toVector());
            push.setY(0.0D);
            if (push.lengthSquared() < 0.0001D) {
                push = new Vector(Math.random() - 0.5D, 0.0D, Math.random() - 0.5D);
            }
            push.normalize().multiply(PUS_BUG_EXPLOSION_KNOCKBACK).setY(0.62D);
            player.setVelocity(player.getVelocity().add(push));
            applyPusPoison(player);
        }
        pusPools.add(new PusPool(center.clone(), customMonsterTick));
    }

    private void tickPusPools() {
        if (pusPools.isEmpty()) {
            return;
        }
        for (PusPool pool : new ArrayList<>(pusPools)) {
            if (pool.checksDone >= PUS_POOL_CHECKS) {
                pusPools.remove(pool);
                continue;
            }
            playPusPoolParticles(pool.center);
            long age = customMonsterTick - pool.createdTick;
            if (age < PUS_POOL_DELAY_TICKS || customMonsterTick < pool.nextCheckTick) {
                continue;
            }
            pool.nextCheckTick = customMonsterTick + PUS_POOL_INTERVAL_TICKS;
            pool.checksDone++;
            World world = pool.center.getWorld();
            if (world == null) {
                pusPools.remove(pool);
                continue;
            }
            double radiusSquared = PUS_POOL_RADIUS * PUS_POOL_RADIUS;
            for (Player player : world.getPlayers()) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                    continue;
                }
                if (player.getLocation().distanceSquared(pool.center) > radiusSquared) {
                    continue;
                }
                double nonLethalDamage = Math.min(PUS_POOL_DAMAGE, Math.max(0.0D, player.getHealth() - 1.0D));
                if (nonLethalDamage > 0.0D) {
                    player.damage(nonLethalDamage);
                }
                applyPusPoison(player);
            }
        }
    }

    private void playPusPoolParticles(Location center) {
        World world = center.getWorld();
        if (world == null || customMonsterTick % 2L != 0L) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(104, 170, 21), 1.05F);
        Location origin = center.clone().add(0.0D, 0.06D, 0.0D);
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2.0D * i / 20.0D;
            Location point = origin.clone().add(Math.cos(angle) * PUS_POOL_RADIUS, 0.0D, Math.sin(angle) * PUS_POOL_RADIUS);
            world.spawnParticle(Particle.DUST, point, 1, 0.01D, 0.01D, 0.01D, 0.0D, dust);
        }
        world.spawnParticle(Particle.ITEM_SLIME, origin, 4, PUS_POOL_RADIUS * 0.45D, 0.03D, PUS_POOL_RADIUS * 0.45D, 0.02D);
    }

    private void applyPusPoison(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, PUS_POISON_DURATION_TICKS, PUS_POISON_AMPLIFIER, false, true, true), true);
    }

    private void respawnPlayerNextTick(Player player) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    private void applyRottenGuardStats(Zombie zombie, boolean heal) {
        setAttribute(zombie, Attribute.MAX_HEALTH, ROTTEN_GUARD_MAX_HEALTH);
        setAttribute(zombie, Attribute.ARMOR, ROTTEN_GUARD_ARMOR);
        setAttribute(zombie, Attribute.ATTACK_DAMAGE, ROTTEN_GUARD_ATTACK_DAMAGE);
        setAttribute(zombie, Attribute.FOLLOW_RANGE, ROTTEN_GUARD_FOLLOW_RANGE);
        setAttribute(zombie, Attribute.MOVEMENT_SPEED, ROTTEN_GUARD_MOVEMENT_SPEED);
        if (heal) {
            zombie.setHealth(ROTTEN_GUARD_MAX_HEALTH);
        }
    }

    private LivingEntity rayTraceExpandedCustomMonsterHitbox(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double range = entityInteractionRange(player);
        RayTraceResult normalEntityHit = player.getWorld().rayTraceEntities(eye, direction, range, 0.0D,
                entity -> entity.isValid() && !entity.equals(player) && !isCustomMonsterDisplay(entity));
        if (normalEntityHit != null) {
            return null;
        }

        Vector origin = eye.toVector();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double searchRadius = range + Math.max(TRAINING_DUMMY_HITBOX_EXPANSION, Math.max(FERRYMAN_HITBOX_EXPANSION,
                Math.max(GULPER_HITBOX_EXPANSION, Math.max(ROTTEN_GUARD_HITBOX_EXPANSION, PUS_BUG_HITBOX_EXPANSION)))) + 1.0D;
        for (Entity entity : player.getWorld().getNearbyEntities(eye, searchRadius, searchRadius, searchRadius, this::isCustomMonster)) {
            if (!(entity instanceof LivingEntity monster) || monster.isDead() || !monster.isValid()) {
                continue;
            }
            double horizontalExpansion = isTrainingDummy(monster)
                    ? TRAINING_DUMMY_HITBOX_EXPANSION
                    : (isPusBug(monster) ? PUS_BUG_HITBOX_EXPANSION : (isGulper(monster) ? GULPER_HITBOX_EXPANSION
                    : (isFerryman(monster) ? FERRYMAN_HITBOX_EXPANSION : ROTTEN_GUARD_HITBOX_EXPANSION)));
            double verticalExpansion = isTrainingDummy(monster)
                    ? 0.9D
                    : (isPusBug(monster) ? PUS_BUG_VERTICAL_HITBOX_EXPANSION : (isGulper(monster) ? 0.55D
                    : (isFerryman(monster) ? 0.75D : 0.08D)));
            BoundingBox expandedBox = monster.getBoundingBox().expand(horizontalExpansion, verticalExpansion, horizontalExpansion);
            RayTraceResult hit = expandedBox.rayTrace(origin, direction, range);
            if (hit == null) {
                continue;
            }
            double distance = hit.getHitPosition().distance(origin);
            if (distance < bestDistance) {
                best = monster;
                bestDistance = distance;
            }
        }
        if (best == null || isBlockedBeforeHit(player, eye, direction, bestDistance)) {
            return null;
        }
        return best;
    }

    private boolean isBlockedBeforeHit(Player player, Location eye, Vector direction, double hitDistance) {
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eye, direction, hitDistance, FluidCollisionMode.NEVER, true);
        return blockHit != null && blockHit.getHitPosition().distance(eye.toVector()) + 0.01D < hitDistance;
    }

    private double entityInteractionRange(Player player) {
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range != null) {
            return range.getValue();
        }
        return player.getGameMode() == GameMode.CREATIVE ? 5.0D : 3.0D;
    }

    private boolean isRottenGuardDisplay(Entity entity) {
        return entity instanceof ItemDisplay
                && entity.getPersistentDataContainer().has(monsterDisplayKey, PersistentDataType.BYTE);
    }

    private boolean isPusBugDisplay(Entity entity) {
        return entity instanceof ItemDisplay
                && entity.getPersistentDataContainer().has(pusBugDisplayKey, PersistentDataType.BYTE);
    }

    private boolean isGulperDisplay(Entity entity) {
        return entity instanceof ItemDisplay
                && entity.getPersistentDataContainer().has(gulperDisplayKey, PersistentDataType.BYTE);
    }

    private boolean isFerrymanDisplay(Entity entity) {
        return entity instanceof ItemDisplay
                && entity.getPersistentDataContainer().has(ferrymanDisplayKey, PersistentDataType.BYTE);
    }

    private boolean isTrainingDummyDisplay(Entity entity) {
        return entity instanceof ItemDisplay
                && entity.getPersistentDataContainer().has(trainingDummyDisplayKey, PersistentDataType.BYTE);
    }

    private boolean isCustomMonsterHealthDisplay(Entity entity) {
        return entity instanceof TextDisplay
                && entity.getPersistentDataContainer().has(monsterHealthDisplayKey, PersistentDataType.BYTE);
    }

    private boolean isCustomMonsterDisplay(Entity entity) {
        return isRottenGuardDisplay(entity) || isGulperDisplay(entity) || isFerrymanDisplay(entity)
                || isPusBugDisplay(entity) || isTrainingDummyDisplay(entity) || isCustomMonsterHealthDisplay(entity);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private Player nearestMonsterTarget(LivingEntity entity) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!isValidMonsterTarget(entity, player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean isValidMonsterTarget(LivingEntity entity, Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        if (!player.getWorld().equals(entity.getWorld())) {
            return false;
        }
        AttributeInstance followRange = entity.getAttribute(Attribute.FOLLOW_RANGE);
        double range = followRange == null ? 64.0D : followRange.getValue();
        return player.getLocation().distanceSquared(entity.getLocation()) <= range * range;
    }

    private boolean isRottenGuard(Entity entity) {
        return entity instanceof Zombie
                && entity.getPersistentDataContainer().has(monsterTypeKey, PersistentDataType.STRING)
                && ROTTEN_GUARD_TYPE.equals(entity.getPersistentDataContainer().get(monsterTypeKey, PersistentDataType.STRING));
    }

    private boolean isPusBug(Entity entity) {
        return entity instanceof Endermite
                && entity.getPersistentDataContainer().has(monsterTypeKey, PersistentDataType.STRING)
                && PUS_BUG_TYPE.equals(entity.getPersistentDataContainer().get(monsterTypeKey, PersistentDataType.STRING));
    }

    private boolean isGulper(Entity entity) {
        return entity instanceof Zombie
                && entity.getPersistentDataContainer().has(monsterTypeKey, PersistentDataType.STRING)
                && GULPER_TYPE.equals(entity.getPersistentDataContainer().get(monsterTypeKey, PersistentDataType.STRING));
    }

    private boolean isFerryman(Entity entity) {
        return entity instanceof Zombie
                && entity.getPersistentDataContainer().has(monsterTypeKey, PersistentDataType.STRING)
                && FERRYMAN_TYPE.equals(entity.getPersistentDataContainer().get(monsterTypeKey, PersistentDataType.STRING));
    }

    private boolean isTrainingDummy(Entity entity) {
        return entity instanceof Slime
                && entity.getPersistentDataContainer().has(monsterTypeKey, PersistentDataType.STRING)
                && TRAINING_DUMMY_TYPE.equals(entity.getPersistentDataContainer().get(monsterTypeKey, PersistentDataType.STRING));
    }

    private boolean isCustomMonster(Entity entity) {
        return isRottenGuard(entity) || isGulper(entity) || isFerryman(entity) || isPusBug(entity) || isTrainingDummy(entity);
    }

    private boolean isRottenGuardInput(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return ROTTEN_GUARD_TYPE.equals(normalized)
                || "rotten-guard".equals(normalized)
                || "朽败的卫兵".equals(input);
    }

    private boolean isPusBugInput(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return PUS_BUG_TYPE.equals(normalized)
                || "pusbug".equals(normalized)
                || "脓包虫".equals(input);
    }

    private boolean isGulperInput(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return GULPER_TYPE.equals(normalized)
                || "sipper".equals(normalized)
                || "drinker".equals(normalized)
                || "啜食者".equals(input);
    }

    private boolean isFerrymanInput(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return FERRYMAN_TYPE.equals(normalized)
                || "ferry_man".equals(normalized)
                || "guide".equals(normalized)
                || "boatman".equals(normalized)
                || "引渡人".equals(input);
    }

    private boolean isTrainingDummyInput(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return TRAINING_DUMMY_TYPE.equals(normalized)
                || "trainingdummy".equals(normalized)
                || "dummy".equals(normalized)
                || "test_dummy".equals(normalized)
                || "测试木桩".equals(input);
    }

    private boolean isCustomMonsterInput(String input) {
        return isRottenGuardInput(input) || isGulperInput(input) || isFerrymanInput(input) || isPusBugInput(input) || isTrainingDummyInput(input);
    }

    private String normalizeCustomMonsterType(String input) {
        if (isFerrymanInput(input)) {
            return FERRYMAN_TYPE;
        }
        if (isTrainingDummyInput(input)) {
            return TRAINING_DUMMY_TYPE;
        }
        if (isGulperInput(input)) {
            return GULPER_TYPE;
        }
        if (isPusBugInput(input)) {
            return PUS_BUG_TYPE;
        }
        if (isRottenGuardInput(input)) {
            return ROTTEN_GUARD_TYPE;
        }
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("rpgmob".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                return List.of("spawn").stream()
                        .filter(value -> value.startsWith(prefix))
                        .toList();
            }
            if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return List.of(ROTTEN_GUARD_TYPE, GULPER_TYPE, PUS_BUG_TYPE, TRAINING_DUMMY_TYPE).stream()
                        .filter(value -> value.startsWith(prefix))
                        .toList();
            }
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("create", "enter", "exit", "delete", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && List.of("enter", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return modules.values().stream()
                    .map(ModuleRecord::displayName)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private record ModuleRecord(String key, String displayName, String dungeonName, Material iconMaterial, String worldName, int borderDistance,
                                double spawnX, double spawnY, double spawnZ, float spawnYaw, float spawnPitch,
                                int curseEscapeDeaths, BlockPos dungeonStarter, DungeonType dungeonType, List<DungeonWaveConfig> dungeonWaves,
                                BossConfig bossConfig, List<ItemStack> dungeonRewards, List<String> requiredCompletions) {
        private ModuleRecord {
            curseEscapeDeaths = Math.max(0, Math.min(99, curseEscapeDeaths));
            dungeonType = dungeonType == null ? DungeonType.WAVES : dungeonType;
            dungeonWaves = dungeonWaves == null ? List.of() : List.copyOf(dungeonWaves);
            bossConfig = bossConfig == null ? BossConfig.defaultConfig() : bossConfig;
            dungeonRewards = copyRewardItems(dungeonRewards);
            requiredCompletions = requiredCompletions == null ? List.of() : requiredCompletions.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        private Location spawn(World world) {
            return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
        }

        private ModuleRecord withDungeonStarter(BlockPos dungeonStarter) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }

        private ModuleRecord withDungeonType(DungeonType dungeonType) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }

        private ModuleRecord withDungeonWaves(List<DungeonWaveConfig> dungeonWaves) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }

        private ModuleRecord withDungeonRewards(List<ItemStack> dungeonRewards) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }

        private ModuleRecord withBossConfig(BossConfig bossConfig) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }

        private ModuleRecord withRequiredCompletions(List<String> requiredCompletions) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }

        private ModuleRecord withCurseEscapeDeaths(int curseEscapeDeaths) {
            return new ModuleRecord(key, displayName, dungeonName, iconMaterial, worldName, borderDistance,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, curseEscapeDeaths, dungeonStarter, dungeonType, dungeonWaves, bossConfig, dungeonRewards, requiredCompletions);
        }
    }

    private enum DungeonType {
        WAVES("waves"),
        BOSS("boss");

        private final String id;

        DungeonType(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private static DungeonType byId(String raw) {
            if (raw == null || raw.isBlank()) {
                return WAVES;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (DungeonType type : values()) {
                if (type.id.equals(normalized)) {
                    return type;
                }
            }
            return WAVES;
        }
    }

    private record BossConfig(String type, String displayName, double maxHealth, double attackDamage,
                              double armor, double movementSpeed, double followRange, double allDamageReduction) {
        private BossConfig {
            type = type == null || type.isBlank() ? DEFAULT_DUNGEON_BOSS_TYPE : type;
            displayName = displayName == null || displayName.isBlank() ? DEFAULT_DUNGEON_BOSS_DISPLAY_NAME : displayName;
            maxHealth = Math.max(1.0D, Math.min(BOSS_ENTITY_MAX_HEALTH, maxHealth));
            attackDamage = Math.max(0.0D, Math.min(10000.0D, attackDamage));
            armor = Math.max(0.0D, Math.min(30.0D, armor));
            movementSpeed = Math.max(0.0D, Math.min(1.0D, movementSpeed));
            followRange = Math.max(1.0D, Math.min(256.0D, followRange));
            allDamageReduction = Math.max(0.0D, Math.min(0.99D, allDamageReduction));
        }

        private static BossConfig defaultConfig() {
            return new BossConfig(DEFAULT_DUNGEON_BOSS_TYPE, DEFAULT_DUNGEON_BOSS_DISPLAY_NAME,
                    DEFAULT_DUNGEON_BOSS_MAX_HEALTH, DEFAULT_DUNGEON_BOSS_ATTACK_DAMAGE,
                    DEFAULT_DUNGEON_BOSS_ARMOR, DEFAULT_DUNGEON_BOSS_MOVEMENT_SPEED,
                    DEFAULT_DUNGEON_BOSS_FOLLOW_RANGE, DEFAULT_DUNGEON_BOSS_ALL_DAMAGE_REDUCTION);
        }

        private BossConfig withMaxHealth(double maxHealth) {
            return new BossConfig(type, displayName, maxHealth, attackDamage, armor, movementSpeed, followRange, allDamageReduction);
        }

        private BossConfig withAllDamageReduction(double allDamageReduction) {
            return new BossConfig(type, displayName, maxHealth, attackDamage, armor, movementSpeed, followRange, allDamageReduction);
        }
    }

    private record DungeonWaveConfig(List<DungeonWaveEnemyConfig> enemies, int waitSeconds) {
        private DungeonWaveConfig {
            enemies = enemies == null ? List.of() : enemies.stream()
                    .filter(enemy -> enemy != null && enemy.count() > 0 && enemy.type() != null && !enemy.type().isBlank())
                    .map(enemy -> new DungeonWaveEnemyConfig(enemy.type(), Math.max(0, Math.min(MAX_DUNGEON_WAVE_COUNT, enemy.count()))))
                    .toList();
            waitSeconds = Math.max(0, Math.min(MAX_DUNGEON_WAVE_WAIT_SECONDS, waitSeconds));
        }
    }

    private record DungeonWaveEnemyConfig(String type, int count) {
        private DungeonWaveEnemyConfig {
            type = type == null || type.isBlank() ? ROTTEN_GUARD_TYPE : type;
            count = Math.max(0, Math.min(MAX_DUNGEON_WAVE_COUNT, count));
        }
    }

    private static final class TowerInstance {
        private final UUID ownerUuid;
        private final String moduleKey;
        private final String worldName;
        private final Location returnLocation;
        private final GameMode returnGameMode;
        private final Path worldPath;
        private int deaths;

        private TowerInstance(UUID ownerUuid, String moduleKey, String worldName, Location returnLocation, GameMode returnGameMode, Path worldPath) {
            this.ownerUuid = ownerUuid;
            this.moduleKey = moduleKey;
            this.worldName = worldName;
            this.returnLocation = returnLocation.clone();
            this.returnGameMode = returnGameMode;
            this.worldPath = worldPath;
        }

        private UUID ownerUuid() {
            return ownerUuid;
        }

        private String moduleKey() {
            return moduleKey;
        }

        private String worldName() {
            return worldName;
        }

        private Location returnLocation() {
            return returnLocation.clone();
        }

        private GameMode returnGameMode() {
            return returnGameMode;
        }

        private Path worldPath() {
            return worldPath;
        }

        private int recordDeath() {
            deaths++;
            return deaths;
        }
    }

    private record BlockPos(int x, int y, int z) {
        private static BlockPos of(Block block) {
            return new BlockPos(block.getX(), block.getY(), block.getZ());
        }

        private boolean matches(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }

    private record StoredBlockPos(String worldName, int x, int y, int z) {
    }

    private static final class PusPool {
        private final Location center;
        private final long createdTick;
        private long nextCheckTick;
        private int checksDone;

        private PusPool(Location center, long createdTick) {
            this.center = center.clone();
            this.createdTick = createdTick;
            this.nextCheckTick = createdTick + PUS_POOL_DELAY_TICKS;
        }
    }

    private enum BossSkillType {
        FERRY("引渡"),
        SOULFIRE("魂火"),
        SHOCK("震击"),
        WASTELAND("荒芜之魔塔");

        private final String displayName;

        BossSkillType(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }

    private enum BossSkillPhase {
        CASTING,
        CHARGING,
        AFTERSHOCK
    }

    private static final class BossSkillCast {
        private final BossSkillType type;
        private final long startedTick;
        private long phaseStartedTick;
        private BossSkillPhase phase = BossSkillPhase.CASTING;
        private UUID targetUuid;
        private Location chargeStart;
        private Location chargeEnd;
        private boolean empoweredSoulfire;
        private final Set<UUID> hitPlayers = new HashSet<>();

        private BossSkillCast(BossSkillType type, long startedTick) {
            this.type = type;
            this.startedTick = startedTick;
            this.phaseStartedTick = startedTick;
        }

        private long age(long now) {
            return now - startedTick;
        }

        private long phaseAge(long now) {
            return now - phaseStartedTick;
        }

        private double soulfirePathDamage() {
            return empoweredSoulfire ? FERRYMAN_SOULFIRE_EMPOWERED_PATH_DAMAGE : FERRYMAN_SOULFIRE_PATH_DAMAGE;
        }
    }

    private static final class DungeonRun {
        private final String worldName;
        private final String moduleKey;
        private final Location center;
        private final DungeonType dungeonType;
        private final List<DungeonWaveConfig> waves;
        private final BossConfig bossConfig;
        private final Set<UUID> liveMobs = new HashSet<>();
        private final HudBossBar bossBar;
        private final HudBossBar castBar;
        private final long bossStartTick;
        private UUID bossUuid;
        private BossSkillCast activeBossSkill;
        private boolean bossEnraged;
        private int nextBossSkillIndex;
        private long nextBossSkillTick;
        private int currentWaveIndex = -1;
        private int currentWaveInitialMobs;
        private int nextWaveIndex;
        private long nextWaveTick = -1L;
        private long restStartTick = -1L;

        private DungeonRun(HudService hudService, String worldName, String moduleKey, Location center,
                           DungeonType dungeonType, List<DungeonWaveConfig> waves, BossConfig bossConfig, long startTick) {
            this.worldName = worldName;
            this.moduleKey = moduleKey;
            this.center = center.clone();
            this.dungeonType = dungeonType == null ? DungeonType.WAVES : dungeonType;
            this.waves = List.copyOf(waves);
            this.bossConfig = bossConfig == null ? BossConfig.defaultConfig() : bossConfig;
            this.bossStartTick = startTick;
            this.nextBossSkillTick = startTick + FERRYMAN_FIRST_SKILL_DELAY_TICKS;
            this.bossBar = hudService.createBossBar("xicerpg:dungeon:" + worldName, "副本准备中",
                    this.dungeonType == DungeonType.BOSS ? BarColor.PURPLE : BarColor.RED,
                    this.dungeonType == DungeonType.BOSS ? BarStyle.SOLID : BarStyle.SEGMENTED_10);
            this.bossBar.setProgress(1.0D);
            this.castBar = this.dungeonType == DungeonType.BOSS
                    ? hudService.createBossBar("xicerpg:dungeon_cast:" + worldName, "", BarColor.YELLOW, BarStyle.SOLID)
                    : null;
            if (this.castBar != null) {
                this.castBar.setProgress(0.0D);
                this.castBar.setVisible(false);
            }
        }

        private boolean isBossDungeon() {
            return dungeonType == DungeonType.BOSS;
        }

        private LivingEntity bossEntity() {
            Entity entity = bossUuid == null ? null : Bukkit.getEntity(bossUuid);
            return entity instanceof LivingEntity living ? living : null;
        }
    }

    private static final class CreateMenu implements InventoryHolder {
        private final String key;
        private final String displayName;
        private String dungeonName;
        private Material iconMaterial;
        private int borderDistance;
        private int curseEscapeDeaths;
        private int spawnX;
        private int spawnZ;
        private boolean closed;

        private CreateMenu(String key, String displayName, String dungeonName, Material iconMaterial, int borderDistance,
                           int curseEscapeDeaths, int spawnX, int spawnZ) {
            this.key = key;
            this.displayName = displayName;
            this.dungeonName = dungeonName;
            this.iconMaterial = iconMaterial;
            this.borderDistance = borderDistance;
            this.curseEscapeDeaths = curseEscapeDeaths;
            this.spawnX = spawnX;
            this.spawnZ = spawnZ;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, CREATE_SIZE);
        }
    }

    private static final class DeleteMenu implements InventoryHolder {
        private final String key;

        private DeleteMenu(String key) {
            this.key = key;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, DELETE_SIZE);
        }
    }

    private static final class TowerMenu implements InventoryHolder {
        private final List<String> moduleKeys;

        private TowerMenu(List<String> moduleKeys) {
            this.moduleKeys = moduleKeys;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, TOWER_MENU_SIZE);
        }
    }

    private static final class BlessingMenu implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, BLESSING_MENU_SIZE);
        }
    }

    private static final class DungeonInfoMenu implements InventoryHolder {
        private final String moduleKey;

        private DungeonInfoMenu(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, DUNGEON_INFO_SIZE);
        }
    }

    private static final class DungeonExitConfirmMenu implements InventoryHolder {
        private final String moduleKey;

        private DungeonExitConfirmMenu(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, DUNGEON_EXIT_CONFIRM_SIZE);
        }
    }

    private static final class BestiaryMenu implements InventoryHolder {
        private final String moduleKey;

        private BestiaryMenu(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, BESTIARY_SIZE);
        }
    }

    private static final class EnemyDetailMenu implements InventoryHolder {
        private final String moduleKey;
        private final String enemyType;

        private EnemyDetailMenu(String moduleKey, String enemyType) {
            this.moduleKey = moduleKey;
            this.enemyType = enemyType;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, ENEMY_DETAIL_SIZE);
        }
    }

    private static final class DungeonStarterMenu implements InventoryHolder {
        private final String moduleKey;

        private DungeonStarterMenu(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, DUNGEON_STARTER_CONFIG_SIZE);
        }
    }

    private static final class MagicAnvilMenu implements InventoryHolder {
        private final String blockKey;
        private Inventory inventory;
        private long lastConfirmTick = -1L;

        private MagicAnvilMenu(String blockKey) {
            this.blockKey = blockKey;
        }

        @Override
        public Inventory getInventory() {
            return inventory == null ? Bukkit.createInventory(this, MAGIC_ANVIL_MENU_SIZE) : inventory;
        }
    }

    private static final class MagicGrindstoneMenu implements InventoryHolder {
        private final String blockKey;
        private Inventory inventory;

        private MagicGrindstoneMenu(String blockKey) {
            this.blockKey = blockKey;
        }

        @Override
        public Inventory getInventory() {
            return inventory == null ? Bukkit.createInventory(this, MAGIC_GRINDSTONE_MENU_SIZE) : inventory;
        }
    }

    private record CustomEnchantInfo(String id, int level) {
    }

    private static final class TrainingDummyMenu implements InventoryHolder {
        private final UUID dummyUuid;

        private TrainingDummyMenu(UUID dummyUuid) {
            this.dummyUuid = dummyUuid;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, TRAINING_DUMMY_MENU_SIZE);
        }
    }

    private record TrainingDummyDamageSample(long tick, double damage) {
    }

    private record SplitDamageCooldown(long startedTick, double lastDamage) {
    }

    private enum ModuleSnapshotState {
        PREPARING,
        READY
    }

    private static final class PendingTowerEntry {
        private final UUID playerUuid;
        private final String moduleKey;
        private final String worldName;
        private final Path instancePath;
        private final Location returnLocation;
        private final GameMode returnGameMode;
        private final Location startLocation;
        private final long createdNanos = System.nanoTime();
        private BukkitTask particleTask;
        private BukkitTask countdownTask;
        private BukkitTask viewDistanceTask;
        private World world;
        private double prepareMillis;
        private double copyMillis;
        private double loadMillis;
        private double warmMillis;
        private boolean worldReady;
        private boolean countdownComplete;
        private boolean cancelled;

        private PendingTowerEntry(UUID playerUuid, String moduleKey, String worldName, Path instancePath,
                                  Location returnLocation, GameMode returnGameMode, Location startLocation) {
            this.playerUuid = playerUuid;
            this.moduleKey = moduleKey;
            this.worldName = worldName;
            this.instancePath = instancePath;
            this.returnLocation = returnLocation.clone();
            this.returnGameMode = returnGameMode;
            this.startLocation = startLocation.clone();
        }

        private void cancelTasks() {
            if (particleTask != null) {
                particleTask.cancel();
                particleTask = null;
            }
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            if (viewDistanceTask != null) {
                viewDistanceTask.cancel();
                viewDistanceTask = null;
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

    private static final class DungeonWaveDetailMenu implements InventoryHolder {
        private final String moduleKey;
        private final int waveIndex;

        private DungeonWaveDetailMenu(String moduleKey, int waveIndex) {
            this.moduleKey = moduleKey;
            this.waveIndex = waveIndex;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, DUNGEON_WAVE_DETAIL_SIZE);
        }
    }

    private static final class DungeonRewardMenu implements InventoryHolder {
        private final String moduleKey;

        private DungeonRewardMenu(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, DUNGEON_REWARD_SIZE);
        }
    }

    private enum EnemyEntry {
        ROTTEN_GUARD(ROTTEN_GUARD_TYPE, "朽败卫兵", Material.ROTTEN_FLESH,
                ROTTEN_GUARD_MAX_HEALTH, ROTTEN_GUARD_ATTACK_DAMAGE, ROTTEN_GUARD_ARMOR, 0.0D, ROTTEN_GUARD_MOVEMENT_SPEED, true, false),
        GULPER(GULPER_TYPE, "啜食者", Material.SCULK_SHRIEKER,
                GULPER_MAX_HEALTH, GULPER_ATTACK_DAMAGE, GULPER_ARMOR, 0.0D, GULPER_MOVEMENT_SPEED, true, false),
        FERRYMAN(FERRYMAN_TYPE, "引渡人", Material.SOUL_LANTERN,
                FERRYMAN_MAX_HEALTH, FERRYMAN_ATTACK_DAMAGE, FERRYMAN_ARMOR, FERRYMAN_ALL_DAMAGE_REDUCTION, FERRYMAN_MOVEMENT_SPEED, true, false),
        PUS_BUG(PUS_BUG_TYPE, "脓包虫", Material.SPIDER_EYE,
                PUS_BUG_MAX_HEALTH, PUS_BUG_ATTACK_DAMAGE, PUS_BUG_ARMOR, 0.0D, PUS_BUG_MOVEMENT_SPEED, false, true),
        TRAINING_DUMMY(TRAINING_DUMMY_TYPE, "测试木桩", Material.PLAYER_HEAD,
                TRAINING_DUMMY_DEFAULT_MAX_HEALTH, 0.0D, TRAINING_DUMMY_DEFAULT_ARMOR, 0.0D, 0.0D, false, false);

        private final String type;
        private final String displayName;
        private final Material icon;
        private final double maxHealth;
        private final double attackDamage;
        private final double armor;
        private final double allDamageReduction;
        private final double movementSpeed;
        private final boolean undead;
        private final boolean arthropod;

        EnemyEntry(String type, String displayName, Material icon, double maxHealth, double attackDamage, double armor, double allDamageReduction, double movementSpeed, boolean undead, boolean arthropod) {
            this.type = type;
            this.displayName = displayName;
            this.icon = icon;
            this.maxHealth = maxHealth;
            this.attackDamage = attackDamage;
            this.armor = armor;
            this.allDamageReduction = allDamageReduction;
            this.movementSpeed = movementSpeed;
            this.undead = undead;
            this.arthropod = arthropod;
        }

        private String type() {
            return type;
        }

        private String displayName() {
            return displayName;
        }

        private Material icon() {
            return icon;
        }

        private double maxHealth() {
            return maxHealth;
        }

        private double attackDamage() {
            return attackDamage;
        }

        private double armor() {
            return armor;
        }

        private double allDamageReduction() {
            return allDamageReduction;
        }

        private double movementSpeed() {
            return movementSpeed;
        }

        private boolean undead() {
            return undead;
        }

        private boolean arthropod() {
            return arthropod;
        }
    }

    private enum RottenGuardPart {
        HEAD("rotten_guard_head", 0.0D, 1.72D, 0.0D),
        BODY("rotten_guard_body", 0.0D, 1.10D, 0.0D),
        RIGHT_ARM("rotten_guard_right_arm", -0.38D, 1.10D, 0.0D),
        LEFT_ARM("rotten_guard_left_arm", 0.38D, 1.10D, 0.0D),
        RIGHT_LEG("rotten_guard_right_leg", -0.13D, 0.42D, 0.0D),
        LEFT_LEG("rotten_guard_left_leg", 0.13D, 0.42D, 0.0D);

        private final String modelKey;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;

        RottenGuardPart(String modelKey, double offsetX, double offsetY, double offsetZ) {
            this.modelKey = modelKey;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }
    }

    private enum TrainingDummyPart {
        HEAD("training_dummy_head", 0.0D, 1.72D, 0.0D),
        BODY("training_dummy_body", 0.0D, 1.10D, 0.0D),
        RIGHT_ARM("training_dummy_right_arm", -0.38D, 1.10D, 0.0D),
        LEFT_ARM("training_dummy_left_arm", 0.38D, 1.10D, 0.0D),
        RIGHT_LEG("training_dummy_right_leg", -0.13D, 0.42D, 0.0D),
        LEFT_LEG("training_dummy_left_leg", 0.13D, 0.42D, 0.0D);

        private final String modelKey;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;

        TrainingDummyPart(String modelKey, double offsetX, double offsetY, double offsetZ) {
            this.modelKey = modelKey;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }
    }

    private static final class CustomBlockBreakSession {
        private final String blockKey;
        private final Location location;
        private double progress;

        private CustomBlockBreakSession(String blockKey, Location location) {
            this.blockKey = blockKey;
            this.location = location;
        }
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    private enum DungeonBlessing {
        NONE("none", "无祝福", Material.GRAY_DYE, List.of("不携带额外祝福进入副本。")),
        ARCHER_BLESSING("archer_blessing", "弓手的祝福", Material.BOW,
                List.of("进入副本后获得远程专注。", "远程武器命中时，每格距离提升 1.75% 伤害。")),
        SWORDSMAN_MEMORY("swordsman_memory", "剑士的记忆", Material.IRON_SWORD,
                List.of("进入副本后持续唤醒古老剑术。", "无法使用远程武器，近战伤害提升 20%。"));

        private final String id;
        private final String displayName;
        private final Material icon;
        private final List<String> lore;

        DungeonBlessing(String id, String displayName, Material icon, List<String> lore) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.lore = lore;
        }

        private String id() {
            return id;
        }

        private String displayName() {
            return displayName;
        }

        private Material icon() {
            return icon;
        }

        private List<String> lore() {
            return lore;
        }

        private static DungeonBlessing byId(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (DungeonBlessing blessing : values()) {
                if (blessing.id.equals(normalized)) {
                    return blessing;
                }
            }
            return NONE;
        }
    }
}
