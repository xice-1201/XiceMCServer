package com.xice.xicemc.morepotioneffects;

import com.xice.xicemc.hud.HudService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.event.player.PlayerArmSwingEvent;

public final class XiceMorePotionEffectsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String WARP_SUPPRESSION_ID = "warp_suppression";
    private static final String WARP_SUPPRESSION_NAME = "跃迁抑制";
    private static final String STRONG_BAN_ID = "strong_ban";
    private static final String STRONG_BAN_NAME = "强效封禁";
    private static final String SWORDSMAN_MEMORY_ID = "swordsman_memory";
    private static final String REBIRTH_BLESSING_ID = "rebirth_blessing";
    private static final String REBIRTH_BLESSING_NAME = "\u590d\u751f\u4e4b\u795d\u798f";
    private static final String SWORDSMAN_MEMORY_NAME = "剑士的记忆";
    private static final String SIDEBAR_OWNER = "xicemorepotioneffects:effects";
    private static final String WITHERING_BLADE_ID = "withering_blade";
    private static final String WITHERING_BLADE_NAME = "凋亡之刃";
    private static final String PAIN_BLADE_ID = "pain_blade";
    private static final String PAIN_BLADE_NAME = "苦痛之刃";
    private static final String SELF_GROWING_ID = "self_growing";
    private static final String SATIETY_VIGOR_ID = "satiety_vigor";
    private static final String SATIETY_VIGOR_NAME = "饱腹活力";
    private static final String SELF_GROWING_NAME = "自生";
    private static final String EXTENDING_HAND_ID = "extending_hand";
    private static final String EXTENDING_HAND_NAME = "延伸之手";
    private static final String STEADY_ID = "steady";
    private static final String STEADY_NAME = "沉稳";
    private static final int MIN_CUSTOM_ENCHANT_LEVEL = 1;
    private static final int MAX_CUSTOM_ENCHANT_LEVEL = 5;
    private static final int MAX_SATIETY_VIGOR_LEVEL = 2;
    private static final int MAX_EXTENDING_HAND_LEVEL = 3;
    private static final double PAIN_BLADE_DAMAGE_BONUS_PER_LEVEL = 0.03D;
    private static final int SELF_GROWING_REPAIR_PER_SECOND = 2;
    private static final long CLAIM_TELEPORT_SUPPRESSION_MILLIS = 30_000L;
    private static final long PORTAL_TELEPORT_SUPPRESSION_MILLIS = 20_000L;
    private static final long ITEM_TELEPORT_SUPPRESSION_MILLIS = 5_000L;

    private final Map<UUID, WarpSuppression> suppressions = new HashMap<>();
    private final Map<UUID, StrongBan> strongBans = new HashMap<>();
    private final Map<UUID, SwordsmanMemory> swordsmanMemories = new HashMap<>();
    private final Map<UUID, RebirthBlessing> rebirthBlessings = new HashMap<>();
    private final Set<UUID> playerIgnitedThisTick = new HashSet<>();
    private HudService hudService;
    private NamespacedKey witheringBladeKey;
    private NamespacedKey painBladeKey;
    private NamespacedKey selfGrowingKey;
    private NamespacedKey satietyVigorKey;
    private NamespacedKey satietyVigorHealthKey;
    private NamespacedKey extendingHandKey;
    private NamespacedKey extendingHandBlockRangeKey;
    private NamespacedKey extendingHandEntityRangeKey;
    private NamespacedKey steadyKey;
    private NamespacedKey steadyKnockbackResistanceKey;
    private BukkitTask sidebarTask;
    private BukkitTask selfGrowingTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        witheringBladeKey = new NamespacedKey(this, WITHERING_BLADE_ID);
        painBladeKey = new NamespacedKey(this, PAIN_BLADE_ID);
        selfGrowingKey = new NamespacedKey(this, SELF_GROWING_ID);
        satietyVigorKey = new NamespacedKey(this, SATIETY_VIGOR_ID);
        satietyVigorHealthKey = new NamespacedKey(this, "satiety_vigor_health");
        extendingHandKey = new NamespacedKey(this, EXTENDING_HAND_ID);
        extendingHandBlockRangeKey = new NamespacedKey(this, "extending_hand_block_range");
        extendingHandEntityRangeKey = new NamespacedKey(this, "extending_hand_entity_range");
        steadyKey = new NamespacedKey(this, STEADY_ID);
        steadyKnockbackResistanceKey = new NamespacedKey(this, "steady_knockback_resistance");
        hudService = Bukkit.getServicesManager().load(HudService.class);
        if (hudService == null) {
            getLogger().severe("XiceHUD service is not available.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("morepotioneffects");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        sidebarTask = Bukkit.getScheduler().runTaskTimer(this, this::updateAllSidebars, 20L, 20L);
        selfGrowingTask = Bukkit.getScheduler().runTaskTimer(this, this::tickEquipmentCustomEnchants, 20L, 20L);
        getLogger().info("XiceMorePotionEffects enabled.");
    }

    @Override
    public void onDisable() {
        if (sidebarTask != null) {
            sidebarTask.cancel();
            sidebarTask = null;
        }
        if (selfGrowingTask != null) {
            selfGrowingTask.cancel();
            selfGrowingTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeSatietyVigorHealthModifier(player);
            removeExtendingHandModifiers(player);
            removeSteadyModifier(player);
            if (hudService != null) {
                hudService.clearSidebar(player.getUniqueId(), SIDEBAR_OWNER);
            }
        }
        playerIgnitedThisTick.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!hasSuppression(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleportComplete(PlayerTeleportEvent event) {
        long durationMillis = suppressionDurationFor(event.getCause());
        if (durationMillis <= 0L) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> applyWarpSuppression(event.getPlayer(), durationMillis));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRebirthBlessingDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && hasRebirthBlessing(player)) {
            event.setCancelled(true);
            player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 10));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRebirthBlessingInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            consumeRebirthBlessing(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRebirthBlessingInteractEntity(PlayerInteractEntityEvent event) {
        consumeRebirthBlessing(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRebirthBlessingArmSwing(PlayerArmSwingEvent event) {
        consumeRebirthBlessing(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRebirthBlessingBlockBreak(BlockBreakEvent event) {
        consumeRebirthBlessing(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRebirthBlessingBlockPlace(BlockPlaceEvent event) {
        consumeRebirthBlessing(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRebirthBlessingCommand(PlayerCommandPreprocessEvent event) {
        consumeRebirthBlessing(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        consumeRebirthBlessing(event.getPlayer());
        if (event.getItem().getType() != Material.MILK_BUCKET) {
            return;
        }
        if (!hasStrongBan(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        consumeMilkWithoutClearing(event.getPlayer(), event.getHand());
        updateSidebar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerConsumeComplete(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET) {
            return;
        }
        clearAllCustomEffects(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            consumeRebirthBlessing(player);
        }
        if (event.getEntity() instanceof Player player && hasSwordsmanMemory(player)) {
            event.setCancelled(true);
            player.sendMessage(color("&c剑士的记忆排斥远程武器。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        consumeRebirthBlessing(player);
        if (!(event.getEntity() instanceof AbstractArrow) && !(event.getEntity() instanceof Trident)) {
            return;
        }
        if (!hasSwordsmanMemory(player)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(color("&c剑士的记忆排斥远程武器。"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        consumeRebirthBlessing(player);
        if (hasSwordsmanMemory(player)) {
            event.setDamage(event.getDamage() * 1.2D);
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            applyPainBladeDamage(player, target, event);
        }
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        int level = witheringBladeLevel(player.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        int durationTicks = Math.max(1, 7 * level - 5) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, durationTicks, level - 1, false, true, true), true);
    }

    private void applyPainBladeDamage(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        int level = painBladeLevel(player.getInventory().getItemInMainHand());
        if (level <= 0 || !hasNegativeCondition(target, true)) {
            return;
        }
        event.setDamage(event.getDamage() * (1.0D + PAIN_BLADE_DAMAGE_BONUS_PER_LEVEL * level));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityCombustByPlayer(EntityCombustByEntityEvent event) {
        if (!(event.getCombuster() instanceof Player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        UUID targetUuid = target.getUniqueId();
        playerIgnitedThisTick.add(targetUuid);
        Bukkit.getScheduler().runTask(this, () -> playerIgnitedThisTick.remove(targetUuid));
    }

    private void tickEquipmentCustomEnchants() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean changed = sanitizeSelfGrowingMendingConflicts(player);
            repairSelfGrowingEquipment(player);
            applySatietyVigor(player);
            applyExtendingHand(player);
            applySteady(player);
            if (changed) {
                player.updateInventory();
            }
        }
    }

    private boolean sanitizeSelfGrowingMendingConflicts(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (removeMendingFromSelfGrowingItem(item)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        ItemStack mainHand = inventory.getItemInMainHand();
        if (removeMendingFromSelfGrowingItem(mainHand)) {
            inventory.setItemInMainHand(mainHand);
            changed = true;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (removeMendingFromSelfGrowingItem(offHand)) {
            inventory.setItemInOffHand(offHand);
            changed = true;
        }
        ItemStack helmet = inventory.getHelmet();
        if (removeMendingFromSelfGrowingItem(helmet)) {
            inventory.setHelmet(helmet);
            changed = true;
        }
        ItemStack chestplate = inventory.getChestplate();
        if (removeMendingFromSelfGrowingItem(chestplate)) {
            inventory.setChestplate(chestplate);
            changed = true;
        }
        ItemStack leggings = inventory.getLeggings();
        if (removeMendingFromSelfGrowingItem(leggings)) {
            inventory.setLeggings(leggings);
            changed = true;
        }
        ItemStack boots = inventory.getBoots();
        if (removeMendingFromSelfGrowingItem(boots)) {
            inventory.setBoots(boots);
            changed = true;
        }
        return changed;
    }

    private void repairSelfGrowingEquipment(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        ItemStack mainHand = inventory.getItemInMainHand();
        if (repairSelfGrowingItem(mainHand)) {
            inventory.setItemInMainHand(mainHand);
            changed = true;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (repairSelfGrowingItem(offHand)) {
            inventory.setItemInOffHand(offHand);
            changed = true;
        }
        ItemStack helmet = inventory.getHelmet();
        if (repairSelfGrowingItem(helmet)) {
            inventory.setHelmet(helmet);
            changed = true;
        }
        ItemStack chestplate = inventory.getChestplate();
        if (repairSelfGrowingItem(chestplate)) {
            inventory.setChestplate(chestplate);
            changed = true;
        }
        ItemStack leggings = inventory.getLeggings();
        if (repairSelfGrowingItem(leggings)) {
            inventory.setLeggings(leggings);
            changed = true;
        }
        ItemStack boots = inventory.getBoots();
        if (repairSelfGrowingItem(boots)) {
            inventory.setBoots(boots);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private boolean repairSelfGrowingItem(ItemStack item) {
        if (!hasSelfGrowing(item) || !isDurableItem(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return false;
        }
        damageable.setDamage(Math.max(0, damageable.getDamage() - SELF_GROWING_REPAIR_PER_SECOND));
        item.setItemMeta(meta);
        return true;
    }

    private void applySatietyVigor(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        int level = satietyVigorLevel(player.getInventory().getChestplate());
        double bonus = player.getFoodLevel() >= 12 && level > 0 ? 2.0D * level : 0.0D;
        double before = maxHealth.getValue();
        removeSatietyVigorModifier(maxHealth);
        if (bonus > 0.0D) {
            maxHealth.addTransientModifier(new AttributeModifier(
                    satietyVigorHealthKey,
                    bonus,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
        double after = maxHealth.getValue();
        if (after < before && player.getHealth() > after) {
            player.setHealth(after);
        }
    }

    private void removeSatietyVigorHealthModifier(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        removeSatietyVigorModifier(maxHealth);
        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void removeSatietyVigorModifier(AttributeInstance maxHealth) {
        for (AttributeModifier modifier : List.copyOf(maxHealth.getModifiers())) {
            if (satietyVigorHealthKey.equals(modifier.getKey())) {
                maxHealth.removeModifier(modifier);
            }
        }
    }

    private void applyExtendingHand(Player player) {
        int level = Math.max(
                extendingHandLevel(player.getInventory().getItemInMainHand()),
                extendingHandLevel(player.getInventory().getItemInOffHand()));
        double bonus = 2.0D * level;
        applyExtendingHandModifier(player, Attribute.BLOCK_INTERACTION_RANGE, extendingHandBlockRangeKey, bonus);
        applyExtendingHandModifier(player, Attribute.ENTITY_INTERACTION_RANGE, extendingHandEntityRangeKey, bonus);
    }

    private void applyExtendingHandModifier(Player player, Attribute attribute, NamespacedKey key, double bonus) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeModifier(instance, key);
        if (bonus > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    key,
                    bonus,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void removeExtendingHandModifiers(Player player) {
        AttributeInstance blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (blockRange != null) {
            removeModifier(blockRange, extendingHandBlockRangeKey);
        }
        AttributeInstance entityRange = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (entityRange != null) {
            removeModifier(entityRange, extendingHandEntityRangeKey);
        }
    }

    private void applySteady(Player player) {
        AttributeInstance knockbackResistance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) {
            return;
        }
        removeModifier(knockbackResistance, steadyKnockbackResistanceKey);
        if (hasSteady(player.getInventory().getLeggings())) {
            knockbackResistance.addTransientModifier(new AttributeModifier(
                    steadyKnockbackResistanceKey,
                    1.0D,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void removeSteadyModifier(Player player) {
        AttributeInstance knockbackResistance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            removeModifier(knockbackResistance, steadyKnockbackResistanceKey);
        }
    }

    private void refreshExtendingHandNextTick(Player player) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                applyExtendingHand(player);
                applySteady(player);
            }
        });
    }

    private void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }

    private boolean removeMendingFromSelfGrowingItem(ItemStack item) {
        if (!hasSelfGrowing(item) || !hasMending(item)) {
            return false;
        }
        item.removeEnchantment(Enchantment.MENDING);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (hasSelfGrowing(event.getItem())) {
            event.getEnchantsToAdd().remove(Enchantment.MENDING);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (isEmptyItem(result)) {
            return;
        }
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);
        if ((hasSelfGrowing(result) && hasMending(result))
                || (hasMending(result) && (hasSelfGrowing(first) || hasSelfGrowing(second)))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemMend(PlayerItemMendEvent event) {
        if (hasSelfGrowing(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityKnockback(EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player player && hasSteady(player.getInventory().getLeggings())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        refreshExtendingHandNextTick(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        refreshExtendingHandNextTick(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        consumeRebirthBlessing(event.getPlayer());
        refreshExtendingHandNextTick(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshExtendingHandNextTick(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeSatietyVigorHealthModifier(event.getPlayer());
        removeExtendingHandModifiers(event.getPlayer());
        removeSteadyModifier(event.getPlayer());
        rebirthBlessings.remove(uuid);
        if (hudService != null) {
            hudService.clearSidebar(uuid, SIDEBAR_OWNER);
        }
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
        if (args.length >= 1 && List.of("enchant", "addenchant").contains(args[0].toLowerCase(Locale.ROOT))) {
            addCustomEnchant(sender, args);
            return true;
        }
        if (args.length < 3) {
            send(sender, message("usage"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        List<Player> targets = resolveTargetPlayers(sender, args[1]);
        if (targets.isEmpty()) {
            send(sender, message("player-not-found"), "player", args[1]);
            return true;
        }
        CustomEffect effect = effectById(args[2]);
        if (effect == null) {
            send(sender, message("unknown-effect"), "effect", args[2]);
            return true;
        }
        switch (action) {
            case "give", "apply", "set" -> applyEffect(sender, targets, effect, args);
            case "clear", "remove" -> clearEffect(sender, targets, effect);
            case "check", "info" -> checkEffect(sender, targets, effect);
            default -> send(sender, message("usage"));
        }
        return true;
    }

    public void applyWarpSuppression(Player player, long durationMillis) {
        if (player == null || !player.isOnline() || durationMillis <= 0L) {
            return;
        }
        applyEffectSilently(player, CustomEffect.WARP_SUPPRESSION, durationMillis);
    }

    public void applyStrongBan(Player player, long durationMillis) {
        if (player == null || !player.isOnline() || durationMillis <= 0L) {
            return;
        }
        applyEffectSilently(player, CustomEffect.STRONG_BAN, durationMillis);
    }

    public void applySwordsmanMemory(Player player, long durationMillis) {
        if (player == null || !player.isOnline() || durationMillis <= 0L) {
            return;
        }
        applyEffectSilently(player, CustomEffect.SWORDSMAN_MEMORY, durationMillis);
    }

    public void applyRebirthBlessing(Player player, long durationMillis) {
        if (player == null || !player.isOnline() || durationMillis <= 0L) {
            return;
        }
        applyEffectSilently(player, CustomEffect.REBIRTH_BLESSING, durationMillis);
    }

    public void clearWarpSuppression(Player player) {
        if (player == null) {
            return;
        }
        suppressions.remove(player.getUniqueId());
        updateSidebar(player);
    }

    public void clearStrongBan(Player player) {
        if (player == null) {
            return;
        }
        strongBans.remove(player.getUniqueId());
        updateSidebar(player);
    }

    public void clearSwordsmanMemory(Player player) {
        if (player == null) {
            return;
        }
        swordsmanMemories.remove(player.getUniqueId());
        updateSidebar(player);
    }

    public void clearRebirthBlessing(Player player) {
        if (player == null) {
            return;
        }
        rebirthBlessings.remove(player.getUniqueId());
        updateSidebar(player);
    }

    public void applyClaimTeleportSuppression(Player player) {
        applyWarpSuppression(player, CLAIM_TELEPORT_SUPPRESSION_MILLIS);
    }

    private void applyEffect(CommandSender sender, List<Player> targets, CustomEffect effect, String[] args) {
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
        for (Player target : targets) {
            applyEffectSilently(target, effect, durationMillis);
        }
        String durationText = formatDuration(durationMillis);
        send(sender, message("applied"), "player", describeTargets(targets), "effect", effect.displayName, "duration", durationText);
    }

    private void clearEffect(CommandSender sender, List<Player> targets, CustomEffect effect) {
        if (!sender.hasPermission("xicemorepotioneffects.admin")) {
            send(sender, message("no-permission"));
            return;
        }
        for (Player target : targets) {
            if (effect == CustomEffect.WARP_SUPPRESSION) {
                suppressions.remove(target.getUniqueId());
            } else if (effect == CustomEffect.STRONG_BAN) {
                strongBans.remove(target.getUniqueId());
            } else if (effect == CustomEffect.SWORDSMAN_MEMORY) {
                swordsmanMemories.remove(target.getUniqueId());
            } else if (effect == CustomEffect.REBIRTH_BLESSING) {
                rebirthBlessings.remove(target.getUniqueId());
            }
            updateSidebar(target);
        }
        send(sender, message("cleared"), "player", describeTargets(targets), "effect", effect.displayName);
    }

    private void checkEffect(CommandSender sender, List<Player> targets, CustomEffect effect) {
        if (!sender.hasPermission("xicemorepotioneffects.admin")) {
            send(sender, message("no-permission"));
            return;
        }
        for (Player target : targets) {
            TimedEffectState state = effectState(target, effect);
            long now = System.currentTimeMillis();
            if (state == null || state.expiresAt <= now) {
                removeEffect(target, effect);
                send(sender, message("not-active"), "player", target.getName(), "effect", effect.displayName);
                updateSidebar(target);
                continue;
            }
            send(sender, message("active"), "player", target.getName(), "effect", effect.displayName, "duration", formatDuration(state.expiresAt - now));
        }
    }

    private void addCustomEnchant(CommandSender sender, String[] args) {
        if (!canUseAction(sender, "enchant")) {
            send(sender, message("no-permission"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can enchant their main-hand item.");
            return;
        }
        if (args.length >= 2 && isSelfGrowingId(args[1])) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!applySelfGrowing(item)) {
                sendRaw(player, "&c自生只能添加到主手中没有经验修补的耐久物品上。");
                return;
            }
            player.getInventory().setItemInMainHand(item);
            sendRaw(player, "&a已为主手物品添加 &a" + SELF_GROWING_NAME + "&a。");
            return;
        }
        if (args.length >= 2 && isSatietyVigorId(args[1])) {
            if (args.length < 3) {
                sendRaw(player, "&c用法：/morepotioneffects enchant satiety_vigor <等级>");
                return;
            }
            Integer level = parseSatietyVigorLevel(args[2]);
            if (level == null) {
                sendRaw(player, "&c等级必须是 1-" + MAX_SATIETY_VIGOR_LEVEL + " 的整数。");
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!applySatietyVigor(item, level)) {
                sendRaw(player, "&c饱腹活力只能添加到主手胸甲上。");
                return;
            }
            player.getInventory().setItemInMainHand(item);
            sendRaw(player, "&a已为主手胸甲添加 &6" + SATIETY_VIGOR_NAME + " " + romanLevel(level) + "&a。");
            return;
        }
        if (args.length >= 2 && isExtendingHandId(args[1])) {
            if (args.length < 3) {
                sendRaw(player, "&c用法：/morepotioneffects enchant extending_hand <等级>");
                return;
            }
            Integer level = parseExtendingHandLevel(args[2]);
            if (level == null) {
                sendRaw(player, "&c等级必须是 1-" + MAX_EXTENDING_HAND_LEVEL + " 的整数。");
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!applyExtendingHand(item, level)) {
                sendRaw(player, "&c延伸之手只能添加到指定武器或工具上。");
                return;
            }
            player.getInventory().setItemInMainHand(item);
            applyExtendingHand(player);
            sendRaw(player, "&a已为主手物品添加 &b" + EXTENDING_HAND_NAME + " " + romanLevel(level) + "&a。");
            return;
        }
        if (args.length >= 2 && isSteadyId(args[1])) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!applySteadyEnchant(item)) {
                sendRaw(player, "&c沉稳只能添加到没有其它自定义附魔的护腿上。");
                return;
            }
            player.getInventory().setItemInMainHand(item);
            sendRaw(player, "&a已为主手护腿添加 &3" + STEADY_NAME + "&a。");
            return;
        }
        if (args.length >= 2 && isPainBladeId(args[1])) {
            if (args.length < 3) {
                sendRaw(player, "&c用法：/morepotioneffects enchant pain_blade <等级>");
                return;
            }
            Integer level = parseCustomEnchantLevel(args[2]);
            if (level == null) {
                sendRaw(player, "&c等级必须是 " + MIN_CUSTOM_ENCHANT_LEVEL + "-" + MAX_CUSTOM_ENCHANT_LEVEL + " 的整数。");
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!applyPainBlade(item, level)) {
                sendRaw(player, "&c苦痛之刃只能添加到没有其它自定义附魔的主手剑类武器上。");
                return;
            }
            player.getInventory().setItemInMainHand(item);
            sendRaw(player, "&a已为主手武器添加 &5" + PAIN_BLADE_NAME + " " + romanLevel(level) + "&a。");
            return;
        }
        if (args.length < 3) {
            sendRaw(player, "&c用法：/morepotioneffects enchant withering_blade <等级>");
            return;
        }
        if (!isWitheringBladeId(args[1])) {
            sendRaw(player, "&c未知自定义附魔：" + args[1]);
            return;
        }
        Integer level = parseCustomEnchantLevel(args[2]);
        if (level == null) {
            sendRaw(player, "&c等级必须是 " + MIN_CUSTOM_ENCHANT_LEVEL + "-" + MAX_CUSTOM_ENCHANT_LEVEL + " 的整数。");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isSword(item)) {
            sendRaw(player, "&c凋亡之刃只能添加到主手剑类武器上。");
            return;
        }
        if (!applyWitheringBlade(item, level)) {
            sendRaw(player, "&c凋亡之刃只能添加到主手剑类武器上。");
            return;
        }
        player.getInventory().setItemInMainHand(item);
        sendRaw(player, "&a已为主手武器添加 &5" + WITHERING_BLADE_NAME + " " + romanLevel(level) + "&a。");
    }

    private void applyEffectSilently(Player target, CustomEffect effect, long durationMillis) {
        long expiresAt = System.currentTimeMillis() + durationMillis;
        if (effect == CustomEffect.WARP_SUPPRESSION) {
            suppressions.put(target.getUniqueId(), new WarpSuppression(expiresAt));
        } else if (effect == CustomEffect.STRONG_BAN) {
            strongBans.put(target.getUniqueId(), new StrongBan(expiresAt));
        } else if (effect == CustomEffect.SWORDSMAN_MEMORY) {
            swordsmanMemories.put(target.getUniqueId(), new SwordsmanMemory(expiresAt));
        } else if (effect == CustomEffect.REBIRTH_BLESSING) {
            rebirthBlessings.put(target.getUniqueId(), new RebirthBlessing(expiresAt));
        }
        updateSidebar(target);
    }

    private void clearAllCustomEffects(Player player) {
        suppressions.remove(player.getUniqueId());
        strongBans.remove(player.getUniqueId());
        swordsmanMemories.remove(player.getUniqueId());
        rebirthBlessings.remove(player.getUniqueId());
        updateSidebar(player);
    }

    private long suppressionDurationFor(PlayerTeleportEvent.TeleportCause cause) {
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return PORTAL_TELEPORT_SUPPRESSION_MILLIS;
        }
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || "CHORUS_FRUIT".equals(cause.name())
                || "CONSUMABLE_EFFECT".equals(cause.name())) {
            return ITEM_TELEPORT_SUPPRESSION_MILLIS;
        }
        return 0L;
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

    private boolean hasStrongBan(Player player) {
        StrongBan strongBan = strongBans.get(player.getUniqueId());
        if (strongBan == null) {
            return false;
        }
        if (strongBan.expiresAt <= System.currentTimeMillis()) {
            strongBans.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean hasSwordsmanMemory(Player player) {
        SwordsmanMemory swordsmanMemory = swordsmanMemories.get(player.getUniqueId());
        if (swordsmanMemory == null) {
            return false;
        }
        if (swordsmanMemory.expiresAt <= System.currentTimeMillis()) {
            swordsmanMemories.remove(player.getUniqueId());
            updateSidebar(player);
            return false;
        }
        return true;
    }

    private boolean hasRebirthBlessing(Player player) {
        RebirthBlessing blessing = rebirthBlessings.get(player.getUniqueId());
        if (blessing == null) {
            return false;
        }
        if (blessing.expiresAt <= System.currentTimeMillis()) {
            rebirthBlessings.remove(player.getUniqueId());
            updateSidebar(player);
            return false;
        }
        return true;
    }

    private boolean consumeRebirthBlessing(Player player) {
        if (!hasRebirthBlessing(player)) {
            return false;
        }
        rebirthBlessings.remove(player.getUniqueId());
        updateSidebar(player);
        return true;
    }

    public boolean hasNegativeCustomEffect(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (CustomEffect effect : CustomEffect.values()) {
            if (!effect.negative) {
                continue;
            }
            TimedEffectState state = effectState(player, effect);
            if (state == null) {
                continue;
            }
            if (state.expiresAt <= now) {
                removeEffect(player, effect);
                changed = true;
                continue;
            }
            if (changed) {
                updateSidebar(player);
            }
            return true;
        }
        if (changed) {
            updateSidebar(player);
        }
        return false;
    }

    private boolean hasNegativeCondition(LivingEntity target, boolean ignoreCurrentHitFire) {
        boolean fireCounts = target.getFireTicks() > 0
                && !(ignoreCurrentHitFire && playerIgnitedThisTick.contains(target.getUniqueId()));
        if (fireCounts || target.getFreezeTicks() > 0 || hasNegativeCustomEffect(target)) {
            return true;
        }
        for (PotionEffect effect : target.getActivePotionEffects()) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    private void removeEffect(Player player, CustomEffect effect) {
        if (effect == CustomEffect.WARP_SUPPRESSION) {
            suppressions.remove(player.getUniqueId());
        } else if (effect == CustomEffect.STRONG_BAN) {
            strongBans.remove(player.getUniqueId());
        } else if (effect == CustomEffect.SWORDSMAN_MEMORY) {
            swordsmanMemories.remove(player.getUniqueId());
        } else if (effect == CustomEffect.REBIRTH_BLESSING) {
            rebirthBlessings.remove(player.getUniqueId());
        }
    }

    private TimedEffectState effectState(Player player, CustomEffect effect) {
        if (effect == CustomEffect.WARP_SUPPRESSION) {
            WarpSuppression suppression = suppressions.get(player.getUniqueId());
            return suppression == null ? null : new TimedEffectState(suppression.expiresAt);
        }
        if (effect == CustomEffect.STRONG_BAN) {
            StrongBan strongBan = strongBans.get(player.getUniqueId());
            return strongBan == null ? null : new TimedEffectState(strongBan.expiresAt);
        }
        if (effect == CustomEffect.SWORDSMAN_MEMORY) {
            SwordsmanMemory swordsmanMemory = swordsmanMemories.get(player.getUniqueId());
            return swordsmanMemory == null ? null : new TimedEffectState(swordsmanMemory.expiresAt);
        }
        if (effect == CustomEffect.REBIRTH_BLESSING) {
            RebirthBlessing blessing = rebirthBlessings.get(player.getUniqueId());
            return blessing == null ? null : new TimedEffectState(blessing.expiresAt);
        }
        return null;
    }

    private void consumeMilkWithoutClearing(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.MILK_BUCKET) {
            return;
        }
        if (item.getAmount() <= 1) {
            ItemStack bucket = new ItemStack(Material.BUCKET);
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(bucket);
            } else {
                player.getInventory().setItemInMainHand(bucket);
            }
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().addItem(new ItemStack(Material.BUCKET)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void updateAllSidebars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateSidebar(player);
        }
    }

    private void updateSidebar(Player player) {
        List<ActiveEffect> activeEffects = activeEffects(player);
        if (hudService == null) {
            return;
        }
        if (activeEffects.isEmpty()) {
            hudService.clearSidebar(player.getUniqueId(), SIDEBAR_OWNER);
            return;
        }
        hudService.setSidebar(player.getUniqueId(), SIDEBAR_OWNER,
                getConfig().getString("sidebar.title", "&d自定义药水效果"),
                activeEffects.stream().map(this::sidebarLine).toList(),
                100);
    }

    private List<ActiveEffect> activeEffects(Player player) {
        List<ActiveEffect> active = new ArrayList<>();
        WarpSuppression suppression = suppressions.get(player.getUniqueId());
        if (suppression != null) {
            long remainingMillis = suppression.expiresAt - System.currentTimeMillis();
            if (remainingMillis <= 0L) {
                suppressions.remove(player.getUniqueId());
            } else {
                active.add(new ActiveEffect(CustomEffect.WARP_SUPPRESSION, remainingSeconds(remainingMillis)));
            }
        }
        StrongBan strongBan = strongBans.get(player.getUniqueId());
        if (strongBan != null) {
            long remainingMillis = strongBan.expiresAt - System.currentTimeMillis();
            if (remainingMillis <= 0L) {
                strongBans.remove(player.getUniqueId());
            } else {
                active.add(new ActiveEffect(CustomEffect.STRONG_BAN, remainingSeconds(remainingMillis)));
            }
        }
        SwordsmanMemory swordsmanMemory = swordsmanMemories.get(player.getUniqueId());
        if (swordsmanMemory != null) {
            long remainingMillis = swordsmanMemory.expiresAt - System.currentTimeMillis();
            if (remainingMillis <= 0L) {
                swordsmanMemories.remove(player.getUniqueId());
            } else {
                active.add(new ActiveEffect(CustomEffect.SWORDSMAN_MEMORY, remainingSeconds(remainingMillis)));
            }
        }
        RebirthBlessing rebirthBlessing = rebirthBlessings.get(player.getUniqueId());
        if (rebirthBlessing != null) {
            long remainingMillis = rebirthBlessing.expiresAt - System.currentTimeMillis();
            if (remainingMillis <= 0L) {
                rebirthBlessings.remove(player.getUniqueId());
            } else {
                active.add(new ActiveEffect(CustomEffect.REBIRTH_BLESSING, remainingSeconds(remainingMillis)));
            }
        }
        return active;
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

    private List<Player> resolveTargetPlayers(CommandSender sender, String selector) {
        if (selector.startsWith("@")) {
            try {
                return Bukkit.selectEntities(sender, selector).stream()
                        .filter(Player.class::isInstance)
                        .map(Player.class::cast)
                        .toList();
            } catch (IllegalArgumentException ignored) {
                return List.of();
            }
        }
        Player player = Bukkit.getPlayer(selector);
        return player == null ? List.of() : List.of(player);
    }

    private boolean isWitheringBladeId(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals(WITHERING_BLADE_ID)
                || normalized.equals("withering")
                || normalized.equals("wither")
                || raw.trim().equals(WITHERING_BLADE_NAME);
    }

    private boolean isPainBladeId(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals(PAIN_BLADE_ID)
                || normalized.equals("pain")
                || normalized.equals("suffering_blade")
                || raw.trim().equals(PAIN_BLADE_NAME);
    }

    private boolean isSelfGrowingId(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals(SELF_GROWING_ID)
                || normalized.equals("self")
                || normalized.equals("self_grow")
                || normalized.equals("selfgrowth")
                || raw.trim().equals(SELF_GROWING_NAME);
    }

    private boolean isSatietyVigorId(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals(SATIETY_VIGOR_ID)
                || normalized.equals("satiety")
                || normalized.equals("vigor")
                || normalized.equals("satiation_vigor")
                || raw.trim().equals(SATIETY_VIGOR_NAME);
    }

    private boolean isExtendingHandId(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals(EXTENDING_HAND_ID)
                || normalized.equals("extend")
                || normalized.equals("extended_hand")
                || normalized.equals("long_arm")
                || raw.trim().equals(EXTENDING_HAND_NAME);
    }

    private boolean isSteadyId(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals(STEADY_ID)
                || normalized.equals("stable")
                || normalized.equals("steadfast")
                || raw.trim().equals(STEADY_NAME);
    }

    private Integer parseCustomEnchantLevel(String raw) {
        try {
            int level = Integer.parseInt(raw);
            if (level < MIN_CUSTOM_ENCHANT_LEVEL || level > MAX_CUSTOM_ENCHANT_LEVEL) {
                return null;
            }
            return level;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseSatietyVigorLevel(String raw) {
        Integer level = parseCustomEnchantLevel(raw);
        if (level == null || level > MAX_SATIETY_VIGOR_LEVEL) {
            return null;
        }
        return level;
    }

    private Integer parseExtendingHandLevel(String raw) {
        Integer level = parseCustomEnchantLevel(raw);
        if (level == null || level > MAX_EXTENDING_HAND_LEVEL) {
            return null;
        }
        return level;
    }

    public int customEnchantLevel(ItemStack item, String enchantId) {
        if (isWitheringBladeId(enchantId)) {
            return witheringBladeLevel(item);
        }
        if (isPainBladeId(enchantId)) {
            return painBladeLevel(item);
        }
        if (isSelfGrowingId(enchantId)) {
            return hasSelfGrowing(item) ? 1 : 0;
        }
        if (isSatietyVigorId(enchantId)) {
            return satietyVigorLevel(item);
        }
        if (isExtendingHandId(enchantId)) {
            return extendingHandLevel(item);
        }
        if (isSteadyId(enchantId)) {
            return hasSteady(item) ? 1 : 0;
        }
        return 0;
    }

    public String firstCustomEnchantId(ItemStack item) {
        if (witheringBladeLevel(item) > 0) {
            return WITHERING_BLADE_ID;
        }
        if (painBladeLevel(item) > 0) {
            return PAIN_BLADE_ID;
        }
        if (hasSelfGrowing(item)) {
            return SELF_GROWING_ID;
        }
        if (satietyVigorLevel(item) > 0) {
            return SATIETY_VIGOR_ID;
        }
        if (extendingHandLevel(item) > 0) {
            return EXTENDING_HAND_ID;
        }
        if (hasSteady(item)) {
            return STEADY_ID;
        }
        return null;
    }

    public int customEnchantMaxLevel(String enchantId) {
        if (isWitheringBladeId(enchantId)) {
            return MAX_CUSTOM_ENCHANT_LEVEL;
        }
        if (isPainBladeId(enchantId)) {
            return MAX_CUSTOM_ENCHANT_LEVEL;
        }
        if (isSelfGrowingId(enchantId)) {
            return 1;
        }
        if (isSatietyVigorId(enchantId)) {
            return MAX_SATIETY_VIGOR_LEVEL;
        }
        if (isExtendingHandId(enchantId)) {
            return MAX_EXTENDING_HAND_LEVEL;
        }
        if (isSteadyId(enchantId)) {
            return 1;
        }
        return 0;
    }

    private boolean hasOtherCustomEnchant(ItemStack item, String enchantId) {
        if (isEmptyItem(item)) {
            return false;
        }
        return (!isWitheringBladeId(enchantId) && witheringBladeLevel(item) > 0)
                || (!isPainBladeId(enchantId) && painBladeLevel(item) > 0)
                || (!isSelfGrowingId(enchantId) && hasSelfGrowing(item))
                || (!isSatietyVigorId(enchantId) && satietyVigorLevel(item) > 0)
                || (!isExtendingHandId(enchantId) && extendingHandLevel(item) > 0)
                || (!isSteadyId(enchantId) && hasSteady(item));
    }

    public boolean applyCustomEnchant(ItemStack item, String enchantId, int level) {
        if (isWitheringBladeId(enchantId)) {
            return applyWitheringBlade(item, level);
        }
        if (isPainBladeId(enchantId)) {
            return applyPainBlade(item, level);
        }
        if (isSelfGrowingId(enchantId)) {
            return level >= 1 && applySelfGrowing(item);
        }
        if (isSatietyVigorId(enchantId)) {
            return applySatietyVigor(item, level);
        }
        if (isExtendingHandId(enchantId)) {
            return applyExtendingHand(item, level);
        }
        if (isSteadyId(enchantId)) {
            return level >= 1 && applySteadyEnchant(item);
        }
        return false;
    }

    public boolean removeCustomEnchant(ItemStack item, String enchantId) {
        if (isEmptyItem(item) || !item.hasItemMeta() || customEnchantLevel(item, enchantId) <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (isWitheringBladeId(enchantId)) {
            meta.getPersistentDataContainer().remove(witheringBladeKey);
            lore.removeIf(line -> plain.serialize(line).startsWith(WITHERING_BLADE_NAME + " "));
        } else if (isPainBladeId(enchantId)) {
            meta.getPersistentDataContainer().remove(painBladeKey);
            lore.removeIf(line -> plain.serialize(line).startsWith(PAIN_BLADE_NAME + " "));
        } else if (isSelfGrowingId(enchantId)) {
            meta.getPersistentDataContainer().remove(selfGrowingKey);
            lore.removeIf(line -> plain.serialize(line).equals(SELF_GROWING_NAME));
        } else if (isSatietyVigorId(enchantId)) {
            meta.getPersistentDataContainer().remove(satietyVigorKey);
            lore.removeIf(line -> plain.serialize(line).startsWith(SATIETY_VIGOR_NAME + " "));
        } else if (isExtendingHandId(enchantId)) {
            meta.getPersistentDataContainer().remove(extendingHandKey);
            lore.removeIf(line -> plain.serialize(line).startsWith(EXTENDING_HAND_NAME + " "));
        } else if (isSteadyId(enchantId)) {
            meta.getPersistentDataContainer().remove(steadyKey);
            lore.removeIf(line -> plain.serialize(line).equals(STEADY_NAME));
        } else {
            return false;
        }
        meta.lore(lore.isEmpty() ? null : lore);
        if (!hasCustomEnchant(meta)) {
            meta.setEnchantmentGlintOverride(null);
        }
        item.setItemMeta(meta);
        return true;
    }

    private boolean hasCustomEnchant(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(witheringBladeKey, PersistentDataType.INTEGER)
                || meta.getPersistentDataContainer().has(painBladeKey, PersistentDataType.INTEGER)
                || meta.getPersistentDataContainer().has(selfGrowingKey, PersistentDataType.BYTE)
                || meta.getPersistentDataContainer().has(satietyVigorKey, PersistentDataType.INTEGER)
                || meta.getPersistentDataContainer().has(extendingHandKey, PersistentDataType.INTEGER)
                || meta.getPersistentDataContainer().has(steadyKey, PersistentDataType.BYTE);
    }

    public boolean applyWitheringBlade(ItemStack item, int level) {
        if (!isSword(item) || level < MIN_CUSTOM_ENCHANT_LEVEL || level > MAX_CUSTOM_ENCHANT_LEVEL
                || hasOtherCustomEnchant(item, WITHERING_BLADE_ID)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(witheringBladeKey, PersistentDataType.INTEGER, level);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(line -> plain.serialize(line).startsWith(WITHERING_BLADE_NAME + " "));
        lore.add(Component.text(WITHERING_BLADE_NAME + " " + romanLevel(level), NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return true;
    }

    public int witheringBladeLevel(ItemStack item) {
        if (!isSword(item) || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(witheringBladeKey, PersistentDataType.INTEGER);
        return level == null ? 0 : Math.min(MAX_CUSTOM_ENCHANT_LEVEL, Math.max(0, level));
    }

    public boolean applyPainBlade(ItemStack item, int level) {
        if (!isSword(item) || level < MIN_CUSTOM_ENCHANT_LEVEL || level > MAX_CUSTOM_ENCHANT_LEVEL
                || hasOtherCustomEnchant(item, PAIN_BLADE_ID)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(painBladeKey, PersistentDataType.INTEGER, level);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(line -> plain.serialize(line).startsWith(PAIN_BLADE_NAME + " "));
        lore.add(Component.text(PAIN_BLADE_NAME + " " + romanLevel(level), NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return true;
    }

    public int painBladeLevel(ItemStack item) {
        if (!isSword(item) || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(painBladeKey, PersistentDataType.INTEGER);
        return level == null ? 0 : Math.min(MAX_CUSTOM_ENCHANT_LEVEL, Math.max(0, level));
    }

    public boolean applySelfGrowing(ItemStack item) {
        if (!isDurableItem(item) || hasMending(item) || hasOtherCustomEnchant(item, SELF_GROWING_ID)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(selfGrowingKey, PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(line -> plain.serialize(line).equals(SELF_GROWING_NAME));
        lore.add(Component.text(SELF_GROWING_NAME, NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return true;
    }

    public boolean hasSelfGrowing(ItemStack item) {
        return isDurableItem(item)
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(selfGrowingKey, PersistentDataType.BYTE);
    }

    public boolean applySatietyVigor(ItemStack item, int level) {
        if (!isChestplate(item) || level < MIN_CUSTOM_ENCHANT_LEVEL || level > MAX_SATIETY_VIGOR_LEVEL
                || hasOtherCustomEnchant(item, SATIETY_VIGOR_ID)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(satietyVigorKey, PersistentDataType.INTEGER, level);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(line -> plain.serialize(line).startsWith(SATIETY_VIGOR_NAME + " "));
        lore.add(Component.text(SATIETY_VIGOR_NAME + " " + romanLevel(level), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return true;
    }

    public int satietyVigorLevel(ItemStack item) {
        if (!isChestplate(item) || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(satietyVigorKey, PersistentDataType.INTEGER);
        return level == null ? 0 : Math.min(MAX_SATIETY_VIGOR_LEVEL, Math.max(0, level));
    }

    public boolean applyExtendingHand(ItemStack item, int level) {
        if (!isExtendingHandItem(item) || level < MIN_CUSTOM_ENCHANT_LEVEL || level > MAX_EXTENDING_HAND_LEVEL
                || hasOtherCustomEnchant(item, EXTENDING_HAND_ID)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(extendingHandKey, PersistentDataType.INTEGER, level);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(line -> plain.serialize(line).startsWith(EXTENDING_HAND_NAME + " "));
        lore.add(Component.text(EXTENDING_HAND_NAME + " " + romanLevel(level), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return true;
    }

    public int extendingHandLevel(ItemStack item) {
        if (!isExtendingHandItem(item) || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(extendingHandKey, PersistentDataType.INTEGER);
        return level == null ? 0 : Math.min(MAX_EXTENDING_HAND_LEVEL, Math.max(0, level));
    }

    public boolean applySteadyEnchant(ItemStack item) {
        if (!isLeggings(item) || hasOtherCustomEnchant(item, STEADY_ID)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(steadyKey, PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(line -> plain.serialize(line).equals(STEADY_NAME));
        lore.add(Component.text(STEADY_NAME, NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return true;
    }

    public boolean hasSteady(ItemStack item) {
        return isLeggings(item)
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(steadyKey, PersistentDataType.BYTE);
    }

    private boolean isSword(ItemStack item) {
        return item != null && switch (item.getType()) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> true;
            default -> false;
        };
    }

    private boolean isExtendingHandItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
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

    private boolean isChestplate(ItemStack item) {
        return item != null && item.getType().name().endsWith("_CHESTPLATE");
    }

    private boolean isLeggings(ItemStack item) {
        return item != null && item.getType().name().endsWith("_LEGGINGS");
    }

    private boolean isDurableItem(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && item.getType().getMaxDurability() > 0
                && item.getItemMeta() instanceof Damageable;
    }

    private boolean isEmptyItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private boolean hasMending(ItemStack item) {
        return !isEmptyItem(item) && item.containsEnchantment(Enchantment.MENDING);
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

    private String describeTargets(List<Player> targets) {
        if (targets.size() == 1) {
            return targets.getFirst().getName();
        }
        return targets.size() + " 名玩家";
    }

    private void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(color(getConfig().getString("messages.prefix", "") + text));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("give", "clear", "check", "enchant", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && List.of("enchant", "addenchant").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of(WITHERING_BLADE_ID, WITHERING_BLADE_NAME, PAIN_BLADE_ID, PAIN_BLADE_NAME,
                            SELF_GROWING_ID, SELF_GROWING_NAME,
                            SATIETY_VIGOR_ID, SATIETY_VIGOR_NAME, EXTENDING_HAND_ID, EXTENDING_HAND_NAME,
                            STEADY_ID, STEADY_NAME)
                    .stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 3 && List.of("enchant", "addenchant").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (isSelfGrowingId(args[1]) || isSteadyId(args[1])) {
                return List.of();
            }
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if (isSatietyVigorId(args[1])) {
                return List.of("1", "2").stream()
                        .filter(value -> value.startsWith(prefix))
                        .toList();
            }
            if (isExtendingHandId(args[1])) {
                return List.of("1", "2", "3").stream()
                        .filter(value -> value.startsWith(prefix))
                        .toList();
            }
            return List.of("1", "2", "3", "4", "5").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>(List.of("@s", "@a", "@p", "@r"));
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList());
            return suggestions.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of(WARP_SUPPRESSION_ID, WARP_SUPPRESSION_NAME, STRONG_BAN_ID, STRONG_BAN_NAME,
                            SWORDSMAN_MEMORY_ID, SWORDSMAN_MEMORY_NAME, REBIRTH_BLESSING_ID, REBIRTH_BLESSING_NAME)
                    .stream()
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

    private record StrongBan(long expiresAt) {
    }

    private record SwordsmanMemory(long expiresAt) {
    }

    private record RebirthBlessing(long expiresAt) {
    }

    private record TimedEffectState(long expiresAt) {
    }

    private record ActiveEffect(CustomEffect effect, long seconds) {
    }

    private enum CustomEffect {
        WARP_SUPPRESSION(WARP_SUPPRESSION_ID, WARP_SUPPRESSION_NAME, true),
        STRONG_BAN(STRONG_BAN_ID, STRONG_BAN_NAME, true),
        SWORDSMAN_MEMORY(SWORDSMAN_MEMORY_ID, SWORDSMAN_MEMORY_NAME, false),
        REBIRTH_BLESSING(REBIRTH_BLESSING_ID, REBIRTH_BLESSING_NAME, false);

        private final String id;
        private final String displayName;
        private final boolean negative;

        CustomEffect(String id, String displayName, boolean negative) {
            this.id = id;
            this.displayName = displayName;
            this.negative = negative;
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
        if (normalized.equals(STRONG_BAN_ID)
                || normalized.equals("strongban")
                || normalized.equals("seal")
                || normalized.equals("ban")
                || raw.trim().equals(STRONG_BAN_NAME)) {
            return CustomEffect.STRONG_BAN;
        }
        if (normalized.equals(SWORDSMAN_MEMORY_ID)
                || normalized.equals("swordsman")
                || normalized.equals("sword")
                || normalized.equals("melee")
                || raw.trim().equals(SWORDSMAN_MEMORY_NAME)) {
            return CustomEffect.SWORDSMAN_MEMORY;
        }
        if (normalized.equals(REBIRTH_BLESSING_ID)
                || normalized.equals("rebirth")
                || normalized.equals("revive")
                || normalized.equals("respawn")
                || raw.trim().equals(REBIRTH_BLESSING_NAME)) {
            return CustomEffect.REBIRTH_BLESSING;
        }
        return null;
    }
}
