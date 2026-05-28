package com.xice.xicemc.customitem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class XiceCustomItemPlugin extends JavaPlugin implements CustomItemService, CustomBlockService, Listener, TabCompleter {
    private final Map<NamespacedKey, CustomItemDefinition> itemDefinitions = new HashMap<>();
    private final Map<NamespacedKey, CustomBlockDefinition> blockDefinitions = new HashMap<>();
    private final Map<NamespacedKey, CustomMultiBlockDefinition> multiBlockDefinitions = new HashMap<>();
    private final Set<NamespacedKey> customIngredientRecipeKeys = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getServicesManager().register(CustomItemService.class, this, this, ServicePriority.Normal);
        Bukkit.getServicesManager().register(CustomBlockService.class, this, this, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("xicecustomitem") != null) {
            getCommand("xicecustomitem").setTabCompleter(this);
        }
        getLogger().info("XiceCustomItem enabled.");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregister(CustomItemService.class, this);
        Bukkit.getServicesManager().unregister(CustomBlockService.class, this);
        itemDefinitions.clear();
        blockDefinitions.clear();
        multiBlockDefinitions.clear();
        customIngredientRecipeKeys.clear();
    }

    @Override
    public void register(CustomItemDefinition definition) {
        itemDefinitions.put(definition.id(), definition);
    }

    @Override
    public Optional<CustomItemDefinition> definition(NamespacedKey id) {
        return Optional.ofNullable(itemDefinitions.get(id));
    }

    @Override
    public Collection<CustomItemDefinition> definitions() {
        return List.copyOf(itemDefinitions.values());
    }

    @Override
    public void registerBlock(CustomBlockDefinition definition) {
        blockDefinitions.put(definition.id(), definition);
    }

    @Override
    public void unregisterBlock(NamespacedKey id) {
        blockDefinitions.remove(id);
    }

    @Override
    public Optional<CustomBlockDefinition> blockDefinition(NamespacedKey id) {
        return Optional.ofNullable(blockDefinitions.get(id));
    }

    @Override
    public Collection<CustomBlockDefinition> blockDefinitions() {
        return List.copyOf(blockDefinitions.values());
    }

    @Override
    public void registerMultiBlock(CustomMultiBlockDefinition definition) {
        multiBlockDefinitions.put(definition.id(), definition);
    }

    @Override
    public void unregisterMultiBlock(NamespacedKey id) {
        multiBlockDefinitions.remove(id);
    }

    @Override
    public Optional<CustomMultiBlockDefinition> multiBlockDefinition(NamespacedKey id) {
        return Optional.ofNullable(multiBlockDefinitions.get(id));
    }

    @Override
    public Collection<CustomMultiBlockDefinition> multiBlockDefinitions() {
        return List.copyOf(multiBlockDefinitions.values());
    }

    @Override
    public ItemStack create(NamespacedKey id, int amount) {
        CustomItemDefinition definition = itemDefinitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown custom item: " + id);
        }
        ItemStack item = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(definition.displayName());
        if (!definition.lore().isEmpty()) {
            meta.lore(definition.lore());
        }
        meta.setItemModel(definition.itemModelKey());
        if (definition.glintOverride() != null) {
            meta.setEnchantmentGlintOverride(definition.glintOverride());
        }
        meta.getPersistentDataContainer().set(definition.markerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean isCustomItem(ItemStack item, NamespacedKey id) {
        CustomItemDefinition definition = itemDefinitions.get(id);
        if (definition == null || item == null || item.getType() != definition.material() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(definition.markerKey(), PersistentDataType.BYTE);
    }

    @Override
    public void allowCustomIngredientRecipe(NamespacedKey recipeKey) {
        customIngredientRecipeKeys.add(recipeKey);
    }

    @Override
    public void disallowCustomIngredientRecipe(NamespacedKey recipeKey) {
        customIngredientRecipeKeys.remove(recipeKey);
    }

    @Override
    public void registerRecipe(Recipe recipe) {
        if (recipe instanceof Keyed keyed) {
            Bukkit.removeRecipe(keyed.getKey());
        }
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void unregisterRecipe(NamespacedKey recipeKey) {
        if (recipeKey != null) {
            Bukkit.removeRecipe(recipeKey);
            customIngredientRecipeKeys.remove(recipeKey);
        }
    }

    @Override
    public void discoverRecipe(Player player, NamespacedKey recipeKey) {
        if (player != null && recipeKey != null && !player.hasDiscoveredRecipe(recipeKey)) {
            player.discoverRecipe(recipeKey);
        }
    }

    @Override
    public void discoverRecipes(Player player, Collection<NamespacedKey> recipeKeys) {
        if (player == null || recipeKeys == null) {
            return;
        }
        for (NamespacedKey recipeKey : recipeKeys) {
            discoverRecipe(player, recipeKey);
        }
    }

    @Override
    public void rememberRecipeKnowledge(Player player, NamespacedKey knowledgeKey) {
        if (player != null && knowledgeKey != null) {
            player.getPersistentDataContainer().set(knowledgeKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @Override
    public boolean hasRecipeKnowledge(Player player, NamespacedKey knowledgeKey) {
        return player != null
                && knowledgeKey != null
                && player.getPersistentDataContainer().has(knowledgeKey, PersistentDataType.BYTE);
    }

    @Override
    public void rememberAndDiscoverRecipe(Player player, NamespacedKey knowledgeKey, NamespacedKey recipeKey) {
        rememberRecipeKnowledge(player, knowledgeKey);
        discoverRecipe(player, recipeKey);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (shouldBlockCrafting(event.getInventory().getMatrix(), event.getInventory().getResult(), event.getRecipe())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (shouldBlockCrafting(event.getInventory().getMatrix(), event.getCurrentItem(), event.getRecipe())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockCrafting(ItemStack[] matrix, ItemStack result, Recipe recipe) {
        if (!matrixContainsRegisteredCustomItem(matrix)) {
            return false;
        }
        if (recipe instanceof Keyed keyed && customIngredientRecipeKeys.contains(keyed.getKey())) {
            return false;
        }
        return !isAnyRegisteredCustomItem(result);
    }

    private boolean matrixContainsRegisteredCustomItem(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (isAnyRegisteredCustomItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyRegisteredCustomItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        for (CustomItemDefinition definition : itemDefinitions.values()) {
            if (item.getType() == definition.material()
                    && item.getItemMeta().getPersistentDataContainer().has(definition.markerKey(), PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String blockId(Block block) {
        return blockId(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @Override
    public String blockId(World world, int x, int y, int z) {
        return world.getName() + "|" + x + "|" + y + "|" + z;
    }

    @Override
    public BlockFace frontFor(Player player) {
        Vector direction = player.getLocation().getDirection();
        double absX = Math.abs(direction.getX());
        double absY = Math.abs(direction.getY());
        double absZ = Math.abs(direction.getZ());
        if (absY > absX && absY > absZ) {
            return direction.getY() > 0.0D ? BlockFace.DOWN : BlockFace.UP;
        }
        if (absX > absZ) {
            return direction.getX() > 0.0D ? BlockFace.WEST : BlockFace.EAST;
        }
        return direction.getZ() > 0.0D ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    @Override
    public float yawFor(BlockFace front) {
        return switch (front) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public float pitchFor(BlockFace front) {
        return switch (front) {
            case UP -> 90.0F;
            case DOWN -> -90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public ItemDisplay spawnOrReplaceDisplay(Block block, CustomBlockDefinition definition, BlockFace front) {
        String id = blockId(block);
        removeDisplays(block.getWorld(), block.getLocation().add(0.5D, 0.5D, 0.5D), definition, id, 1.2D);
        Location location = block.getLocation().add(0.5D, 0.5D, 0.5D);
        return block.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(createDisplayItem(definition));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setDisplayWidth(definition.displayWidth());
            display.setDisplayHeight(definition.displayHeight());
            display.customName(definition.displayName());
            display.setCustomNameVisible(false);
            display.setRotation(yawFor(front), pitchFor(front));
            display.getPersistentDataContainer().set(definition.displayMarkerKey(), PersistentDataType.STRING, id);
        });
    }

    @Override
    public ItemDisplay spawnOrReplaceDisplay(Block block, NamespacedKey definitionId, BlockFace front) {
        CustomBlockDefinition definition = blockDefinitions.get(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown custom block: " + definitionId);
        }
        return spawnOrReplaceDisplay(block, definition, front);
    }

    @Override
    public void removeDisplays(World world, Location center, CustomBlockDefinition definition, String blockId, double radius) {
        if (world == null || center == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            String displayId = persistentString(entity.getPersistentDataContainer(), definition.displayMarkerKey());
            if (blockId.equals(displayId)) {
                entity.remove();
            }
        }
    }

    @Override
    public Block relativePartBlock(Block anchor, CustomMultiBlockPart part, BlockFace front) {
        int x = part.offsetX();
        int z = part.offsetZ();
        int rotatedX = switch (front) {
            case SOUTH -> -x;
            case EAST -> -z;
            case WEST -> z;
            default -> x;
        };
        int rotatedZ = switch (front) {
            case SOUTH -> -z;
            case EAST -> x;
            case WEST -> -x;
            default -> z;
        };
        return anchor.getRelative(rotatedX, part.offsetY(), rotatedZ);
    }

    @Override
    public List<Block> multiBlockPartBlocks(Block anchor, CustomMultiBlockDefinition definition, BlockFace front) {
        return definition.parts().stream()
                .map(part -> relativePartBlock(anchor, part, front))
                .toList();
    }

    @Override
    public boolean canPlaceMultiBlock(Block anchor, CustomMultiBlockDefinition definition, BlockFace front) {
        if (anchor == null || definition == null) {
            return false;
        }
        for (Block block : multiBlockPartBlocks(anchor, definition, front)) {
            if (block.getY() < block.getWorld().getMinHeight() || block.getY() >= block.getWorld().getMaxHeight()) {
                return false;
            }
            if (!block.isReplaceable()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setMultiBlockCarrierBlocks(Block anchor, CustomMultiBlockDefinition definition, BlockFace front) {
        for (CustomMultiBlockPart part : definition.parts()) {
            Block block = relativePartBlock(anchor, part, front);
            String blockData = part.carrierBlockData(front);
            if (blockData == null || blockData.isBlank()) {
                continue;
            }
            block.setBlockData(Bukkit.createBlockData(blockData), false);
        }
    }

    @Override
    public List<BlockDisplay> spawnOrReplaceMultiBlockDisplays(
            Block anchor,
            CustomMultiBlockDefinition definition,
            BlockFace front,
            String structureId
    ) {
        removeMultiBlockDisplays(
                anchor.getWorld(),
                anchor.getLocation().add(0.5D, 0.5D, 0.5D),
                definition,
                structureId,
                Math.max(2.0D, definition.parts().size() + 0.5D));
        List<BlockDisplay> displays = new ArrayList<>();
        for (CustomMultiBlockPart part : definition.parts()) {
            Block block = relativePartBlock(anchor, part, front);
            String displayBlockData = part.displayBlockData(front);
            if (displayBlockData == null || displayBlockData.isBlank()) {
                continue;
            }
            BlockData data = Bukkit.createBlockData(displayBlockData);
            displays.add(block.getWorld().spawn(block.getLocation(), BlockDisplay.class, display -> {
                display.setBlock(data);
                display.setPersistent(true);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setDisplayWidth(part.displayWidth());
                display.setDisplayHeight(part.displayHeight());
                display.customName(definition.displayName());
                display.setCustomNameVisible(false);
                display.getPersistentDataContainer().set(definition.displayMarkerKey(), PersistentDataType.STRING, structureId);
                display.getPersistentDataContainer().set(definition.displayPartMarkerKey(), PersistentDataType.STRING, part.id());
            }));
        }
        return displays;
    }

    @Override
    public void removeMultiBlockDisplays(
            World world,
            Location center,
            CustomMultiBlockDefinition definition,
            String structureId,
            double radius
    ) {
        if (world == null || center == null || structureId == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            String displayId = persistentString(entity.getPersistentDataContainer(), definition.displayMarkerKey());
            if (structureId.equals(displayId)) {
                entity.remove();
            }
        }
    }

    private String persistentString(PersistentDataContainer data, NamespacedKey key) {
        try {
            return data.get(key, PersistentDataType.STRING);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public double blockBreakProgressPerTick(Player player, double hardness) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPickaxe(tool)) {
            return 1.0D / hardness / 100.0D;
        }
        double speed = switch (tool.getType()) {
            case WOODEN_PICKAXE -> 2.0D;
            case STONE_PICKAXE -> 4.0D;
            case IRON_PICKAXE -> 6.0D;
            case DIAMOND_PICKAXE -> 8.0D;
            case NETHERITE_PICKAXE -> 9.0D;
            case GOLDEN_PICKAXE -> 12.0D;
            default -> 1.0D;
        };
        int efficiency = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiency > 0) {
            speed += efficiency * efficiency + 1.0D;
        }
        return speed / hardness / 30.0D;
    }

    @Override
    public double blockBreakProgressPerTick(Player player, CustomBlockDefinition definition) {
        if (definition == null) {
            return 0.0D;
        }
        if (definition.requiredTool() == CustomBlockTool.PICKAXE && definition.minimumToolTier() == CustomBlockToolTier.NONE) {
            return blockBreakProgressPerTick(player, definition.breakHardness());
        }
        if (definition.requiredTool() != CustomBlockTool.NONE && !isCorrectTool(player, definition)) {
            return 1.0D / definition.breakHardness() / 100.0D;
        }
        double speed = toolSpeed(player.getInventory().getItemInMainHand(), definition.requiredTool());
        return speed / definition.breakHardness() / 30.0D;
    }

    @Override
    public boolean isCorrectTool(Player player, CustomBlockDefinition definition) {
        if (definition == null || definition.requiredTool() == CustomBlockTool.NONE) {
            return true;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!matchesTool(tool, definition.requiredTool())) {
            return false;
        }
        return toolTier(tool).ordinal() >= definition.minimumToolTier().ordinal();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("xicecustomitem.admin")) {
                sender.sendMessage(color("&c你没有权限使用该命令。"));
                return true;
            }
            List<CustomItemDefinition> definitions = new ArrayList<>(itemDefinitions.values());
            definitions.sort(Comparator.comparing(definition -> definition.id().asString()));
            sender.sendMessage(color("&6已注册自定义物品: &f" + definitions.size()));
            for (CustomItemDefinition definition : definitions) {
                sender.sendMessage(color("&7- &f" + definition.id().asString()
                        + " &8(" + definition.material().name().toLowerCase(java.util.Locale.ROOT) + ")"));
            }
            return true;
        }
        if ("give".equalsIgnoreCase(args[0])) {
            return handleGive(sender, label, args);
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("xicecustomitem.admin")) {
                sender.sendMessage(color("&c你没有权限使用该命令。"));
                return true;
            }
            reloadConfig();
            sender.sendMessage(color("&aXiceCustomItem 配置已重载。"));
            return true;
        }
        if ("checkpack".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("xicecustomitem.admin")) {
                sender.sendMessage(color("&c你没有权限使用该命令。"));
                return true;
            }
            File root = args.length >= 2 ? new File(args[1]) : defaultResourcePackRoot();
            List<String> missing = missingItemModelFiles(root);
            if (missing.isEmpty()) {
                sender.sendMessage(color("&a资源包检查通过，已注册物品的 items 模型入口都存在。"));
            } else {
                sender.sendMessage(color("&c资源包缺少 " + missing.size() + " 个 items 模型入口:"));
                for (String value : missing) {
                    sender.sendMessage(color("&7- &f" + value));
                }
            }
            return true;
        }
        sender.sendMessage(color("&c用法: /" + label + " <give|list|checkpack|reload>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(List.of("give", "list", "checkpack", "reload"), args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return matching(itemIdSuggestions(), args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<>(List.of("1", "8", "16", "64"));
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return matching(values, args[2]);
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            return matching(List.of("1", "8", "16", "64"), args[3]);
        }
        return List.of();
    }

    private boolean handleGive(CommandSender sender, String label, String[] args) {
        if (!canUseAction(sender, "give")) {
            sender.sendMessage(color("&c你没有权限使用该命令。"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color("&c用法: /" + label + " give <物品ID> [玩家] [数量]"));
            return true;
        }
        CustomItemDefinition definition = resolveDefinition(args[1]);
        if (definition == null) {
            sender.sendMessage(color("&c未知自定义物品: &f" + args[1]));
            return true;
        }

        int amount = 1;
        List<Player> targets;
        if (args.length >= 3) {
            if (args.length == 3 && sender instanceof Player player && isInteger(args[2])) {
                targets = List.of(player);
                amount = parseAmount(sender, args[2]);
            } else {
                targets = resolveTargets(sender, args[2]);
                if (targets.isEmpty()) {
                    sender.sendMessage(color("&c没有找到在线目标玩家: &f" + args[2]));
                    return true;
                }
                if (args.length >= 4) {
                    amount = parseAmount(sender, args[3]);
                }
            }
        } else if (sender instanceof Player player) {
            targets = List.of(player);
        } else {
            sender.sendMessage(color("&c控制台使用: /" + label + " give <物品ID> <玩家> [数量]"));
            return true;
        }
        if (amount < 1) {
            return true;
        }

        for (Player target : targets) {
            ItemStack item = create(definition.id(), amount);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            target.sendMessage(color("&6[XiceCustomItem] &a你获得了 &f" + definition.id().asString() + " &ax" + amount + "。"));
        }
        sender.sendMessage(color("&a已向 &f" + targets.size() + " &a名玩家发放 &f" + definition.id().asString() + " &ax" + amount + "。"));
        return true;
    }

    private boolean canUseAction(CommandSender sender, String action) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.hasPermission("xicecustomitem.admin")) {
            return true;
        }
        String normalized = action.toLowerCase(java.util.Locale.ROOT);
        Set<String> allowed = new HashSet<>();
        for (String value : getConfig().getStringList("access.default-allowed-actions")) {
            allowed.add(value.toLowerCase(java.util.Locale.ROOT));
        }
        for (String value : getConfig().getStringList("access.players." + player.getUniqueId() + ".actions")) {
            allowed.add(value.toLowerCase(java.util.Locale.ROOT));
        }
        return allowed.contains(normalized);
    }

    private CustomItemDefinition resolveDefinition(String input) {
        NamespacedKey key = NamespacedKey.fromString(input);
        if (key != null && itemDefinitions.containsKey(key)) {
            return itemDefinitions.get(key);
        }
        String normalized = input.toLowerCase(java.util.Locale.ROOT);
        List<CustomItemDefinition> matches = itemDefinitions.values().stream()
                .filter(definition -> definition.id().getKey().equals(normalized)
                        || definition.id().asString().equals(normalized))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        Player exact = Bukkit.getPlayerExact(selector);
        if (exact != null) {
            return List.of(exact);
        }
        try {
            return Bukkit.selectEntities(sender, selector).stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .toList();
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private int parseAmount(CommandSender sender, String value) {
        try {
            int amount = Integer.parseInt(value);
            if (amount < 1 || amount > 64) {
                sender.sendMessage(color("&c数量必须是 1-64 之间的整数。"));
                return -1;
            }
            return amount;
        } catch (NumberFormatException ignored) {
            sender.sendMessage(color("&c数量必须是 1-64 之间的整数。"));
            return -1;
        }
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private List<String> itemIdSuggestions() {
        List<String> values = new ArrayList<>();
        for (CustomItemDefinition definition : itemDefinitions.values()) {
            values.add(definition.id().asString());
            values.add(definition.id().getKey());
        }
        values.sort(String::compareTo);
        return values;
    }

    private List<String> matching(Collection<String> values, String prefix) {
        String normalized = prefix.toLowerCase(java.util.Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(normalized))
                .toList();
    }

    private File defaultResourcePackRoot() {
        List<File> candidates = List.of(
                new File("server/resourcepacks/xiceclaim"),
                new File("/opt/xicemc/repo/server/resourcepacks/xiceclaim"),
                new File("../repo/server/resourcepacks/xiceclaim"));
        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private ItemStack createDisplayItem(CustomBlockDefinition definition) {
        ItemStack item = new ItemStack(definition.displayMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(definition.displayName());
        meta.setItemModel(definition.displayModelKey());
        item.setItemMeta(meta);
        return item;
    }

    private List<String> missingItemModelFiles(File resourcePackRoot) {
        List<String> missing = new ArrayList<>();
        for (CustomItemDefinition definition : itemDefinitions.values()) {
            NamespacedKey model = definition.itemModelKey();
            File itemModel = new File(resourcePackRoot,
                    "assets/" + model.getNamespace() + "/items/" + model.getKey() + ".json");
            if (!itemModel.isFile()) {
                missing.add(model.asString());
            }
        }
        return missing;
    }

    private boolean isPickaxe(ItemStack item) {
        return item != null && switch (item.getType()) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE, GOLDEN_PICKAXE -> true;
            default -> false;
        };
    }

    private boolean matchesTool(ItemStack item, CustomBlockTool requiredTool) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String name = item.getType().name();
        return switch (requiredTool) {
            case NONE -> true;
            case PICKAXE -> name.endsWith("_PICKAXE");
            case AXE -> name.endsWith("_AXE") && !name.endsWith("_PICKAXE");
            case SHOVEL -> name.endsWith("_SHOVEL");
            case HOE -> name.endsWith("_HOE");
            case SWORD -> name.endsWith("_SWORD");
            case SHEARS -> item.getType() == Material.SHEARS;
        };
    }

    private CustomBlockToolTier toolTier(ItemStack item) {
        if (item == null) {
            return CustomBlockToolTier.NONE;
        }
        String name = item.getType().name();
        if (name.startsWith("NETHERITE_")) {
            return CustomBlockToolTier.NETHERITE;
        }
        if (name.startsWith("DIAMOND_")) {
            return CustomBlockToolTier.DIAMOND;
        }
        if (name.startsWith("IRON_")) {
            return CustomBlockToolTier.IRON;
        }
        if (name.startsWith("STONE_")) {
            return CustomBlockToolTier.STONE;
        }
        if (name.startsWith("WOODEN_") || name.startsWith("GOLDEN_")) {
            return CustomBlockToolTier.WOOD;
        }
        return CustomBlockToolTier.NONE;
    }

    private double toolSpeed(ItemStack item, CustomBlockTool requiredTool) {
        if (requiredTool == CustomBlockTool.NONE) {
            return 1.0D;
        }
        if (requiredTool == CustomBlockTool.SHEARS) {
            return 5.0D;
        }
        if (requiredTool == CustomBlockTool.SWORD) {
            return 1.5D;
        }
        double speed = switch (toolTier(item)) {
            case WOOD -> item != null && item.getType().name().startsWith("GOLDEN_") ? 12.0D : 2.0D;
            case STONE -> 4.0D;
            case IRON -> 6.0D;
            case DIAMOND -> 8.0D;
            case NETHERITE -> 9.0D;
            case NONE -> 1.0D;
        };
        int efficiency = item == null ? 0 : item.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiency > 0) {
            speed += efficiency * efficiency + 1.0D;
        }
        return speed;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
