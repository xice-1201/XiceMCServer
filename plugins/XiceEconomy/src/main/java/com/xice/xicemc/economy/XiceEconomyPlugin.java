package com.xice.xicemc.economy;

import com.xice.xicemc.customitem.CustomBlockDefinition;
import com.xice.xicemc.customitem.CustomBlockService;
import com.xice.xicemc.customitem.CustomItemDefinition;
import com.xice.xicemc.customitem.CustomItemService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class XiceEconomyPlugin extends JavaPlugin implements Listener {
    private static final Material VIRTUAL_CURRENCY_MACHINE_CARRIER = Material.BARRIER;
    private static final Material VIRTUAL_CURRENCY_MACHINE_ITEM = Material.LODESTONE;
    private static final int MACHINE_MENU_SIZE = 27;
    private static final int DEPOSIT_INPUT_SLOT = 11;
    private static final int DEPOSIT_BALANCE_SLOT = 13;
    private static final int DEPOSIT_CONFIRM_SLOT = 15;
    private static final int WITHDRAW_OUTPUT_SLOT = 10;
    private static final int WITHDRAW_BALANCE_SLOT = 13;
    private static final int WITHDRAW_AMOUNT_SLOT = 16;
    private static final int WITHDRAW_CONFIRM_SLOT = 22;
    private static final double QUARTZ_BLOCK_HARDNESS = 0.8D;
    private static final double MACHINE_BREAK_REACH_SQUARED = 36.0D;

    private final Set<Consumer<UUID>> balanceChangeListeners = new HashSet<>();
    private final Set<BlockKey> virtualCurrencyMachines = new HashSet<>();
    private final Map<BlockKey, BlockFace> virtualCurrencyMachineFaces = new java.util.HashMap<>();
    private final Map<UUID, MachineBreakSession> breakSessions = new java.util.HashMap<>();
    private EconomyStorage storage;
    private CurrencyConfig currency;
    private CustomItemService customItemService;
    private CustomBlockService customBlockService;
    private NamespacedKey physicalCurrencyKey;
    private NamespacedKey physicalCurrencyItemModelKey;
    private NamespacedKey virtualCurrencyMachineKey;
    private NamespacedKey virtualCurrencyMachineItemModelKey;
    private NamespacedKey virtualCurrencyMachineRecipeKey;
    private NamespacedKey virtualCurrencyMachineDisplayKey;
    private CustomBlockDefinition virtualCurrencyMachineBlockDefinition;
    private File machinesFile;
    private FileConfiguration machinesConfig;
    private int defaultHistoryLimit;
    private int maxHistoryLimit;
    private int defaultTopLimit;
    private int maxTopLimit;
    private int breakTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        physicalCurrencyKey = new NamespacedKey(this, "physical_currency");
        physicalCurrencyItemModelKey = new NamespacedKey(this, "physical_currency");
        virtualCurrencyMachineKey = new NamespacedKey(this, "virtual_currency_machine");
        virtualCurrencyMachineItemModelKey = new NamespacedKey(this, "virtual_currency_machine");
        virtualCurrencyMachineRecipeKey = new NamespacedKey(this, "virtual_currency_machine_recipe");
        virtualCurrencyMachineDisplayKey = new NamespacedKey(this, "virtual_currency_machine_display");
        customItemService = Bukkit.getServicesManager().load(CustomItemService.class);
        if (customItemService == null) {
            getLogger().severe("XiceCustomItem service is not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        customBlockService = Bukkit.getServicesManager().load(CustomBlockService.class);
        if (customBlockService == null) {
            getLogger().severe("XiceCustomItem custom block service is not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerCustomItems();
        registerCustomBlocks();
        machinesFile = new File(getDataFolder(), "virtual-currency-machines.yml");
        if (!reloadEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadVirtualCurrencyMachines();
        removePhysicalCurrencyRecipes();
        registerVirtualCurrencyMachineRecipe();
        EconomyCommand command = new EconomyCommand(this);
        registerCommand("money", command);
        registerCommand("pay", command);
        registerCommand("eco", command);
        registerCommand("xiceeconomy", command);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::refreshLoadedVirtualCurrencyMachineDisplays);
        breakTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickMachineBreaking, 1L, 1L).getTaskId();
        getLogger().info("XiceEconomy enabled. Currency: " + currency.displayName() + " (" + currency.code() + ")");
    }

    @Override
    public void onDisable() {
        if (breakTaskId != -1) {
            Bukkit.getScheduler().cancelTask(breakTaskId);
            breakTaskId = -1;
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
        if (customItemService != null) {
            customItemService.unregisterRecipe(virtualCurrencyMachineRecipeKey);
        }
        if (customBlockService != null) {
            customBlockService.unregisterBlock(virtualCurrencyMachineKey);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        runStorageTask(
                () -> storage.getOrCreateAccount(player.getUniqueId(), player.getName()),
                ignored -> {
                    notifyBalanceChanged(player.getUniqueId());
                    if (hasNetherQuartz(player)) {
                        unlockVirtualCurrencyMachineRecipe(player);
                    }
                },
                error -> getLogger().log(Level.WARNING, "Failed to ensure economy account for " + player.getName(), error));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getItem().getItemStack().getType() != Material.QUARTZ) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (hasNetherQuartz(player)) {
                unlockVirtualCurrencyMachineRecipe(player);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isVirtualCurrencyMachineItem(event.getItemInHand())) {
            return;
        }
        Block block = event.getBlockPlaced();
        BlockKey key = BlockKey.of(block);
        BlockFace front = customBlockService.frontFor(event.getPlayer());
        block.setType(VIRTUAL_CURRENCY_MACHINE_CARRIER, false);
        virtualCurrencyMachines.add(key);
        virtualCurrencyMachineFaces.put(key, front);
        saveVirtualCurrencyMachines();
        refreshVirtualCurrencyMachineDisplay(block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockKey key = BlockKey.of(block);
        if (!virtualCurrencyMachines.contains(key)) {
            return;
        }
        event.setDropItems(false);
        breakVirtualCurrencyMachine(block, event.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE || !virtualCurrencyMachines.contains(BlockKey.of(event.getBlock()))) {
            return;
        }
        if (!ensureVirtualCurrencyMachineCarrier(event.getBlock())) {
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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!isVirtualCurrencyMachineBlock(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        openMachineMenu(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        refreshVirtualCurrencyMachineDisplays(event.getChunk());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (hasNetherQuartz(player)) {
                    unlockVirtualCurrencyMachineRecipe(player);
                }
            });
        }

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof MachineMainMenu)
                && !(holder instanceof DepositMenu)
                && !(holder instanceof WithdrawMenu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (holder instanceof MachineMainMenu) {
            handleMachineMainClick(player, event);
        } else if (holder instanceof DepositMenu menu) {
            handleDepositClick(player, menu, event);
        } else if (holder instanceof WithdrawMenu menu) {
            handleWithdrawClick(player, menu, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof DepositMenu) {
            boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
            boolean allowed = event.getRawSlots().stream().filter(slot -> slot < top.getSize()).allMatch(slot -> slot == DEPOSIT_INPUT_SLOT)
                    && isPhysicalCurrencyItem(event.getOldCursor());
            if (touchesTop && !allowed) {
                event.setCancelled(true);
            }
        } else if (holder instanceof WithdrawMenu || holder instanceof MachineMainMenu) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder instanceof DepositMenu menu) {
            ItemStack input = inventory.getItem(DEPOSIT_INPUT_SLOT);
            if (!menu.busy && !isAir(input)) {
                inventory.setItem(DEPOSIT_INPUT_SLOT, null);
                giveOrDrop(player, input);
            }
        } else if (holder instanceof WithdrawMenu menu) {
            ItemStack output = inventory.getItem(WITHDRAW_OUTPUT_SLOT);
            if (!menu.busy && !isAir(output)) {
                inventory.setItem(WITHDRAW_OUTPUT_SLOT, null);
                giveOrDrop(player, output);
            }
        }
    }

    public boolean reloadEconomy() {
        reloadConfig();
        CurrencyConfig loadedCurrency = loadCurrencyConfig();
        int loadedDefaultHistoryLimit = Math.max(1, getConfig().getInt("queries.default-history-limit", 10));
        int loadedMaxHistoryLimit = Math.max(loadedDefaultHistoryLimit, getConfig().getInt("queries.max-history-limit", 30));
        int loadedDefaultTopLimit = Math.max(1, getConfig().getInt("queries.default-top-limit", 10));
        int loadedMaxTopLimit = Math.max(loadedDefaultTopLimit, getConfig().getInt("queries.max-top-limit", 30));

        EconomyStorage newStorage;
        try {
            newStorage = createStorage(loadedCurrency);
            newStorage.start();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize economy database.", exception);
            return false;
        }

        if (storage != null) {
            storage.close();
        }
        storage = newStorage;
        currency = loadedCurrency;
        defaultHistoryLimit = loadedDefaultHistoryLimit;
        maxHistoryLimit = loadedMaxHistoryLimit;
        defaultTopLimit = loadedDefaultTopLimit;
        maxTopLimit = loadedMaxTopLimit;
        registerCustomItems();
        return true;
    }

    public EconomyStorage storage() {
        return storage;
    }

    public CurrencyConfig currency() {
        return currency;
    }

    public int defaultHistoryLimit() {
        return defaultHistoryLimit;
    }

    public int maxHistoryLimit() {
        return maxHistoryLimit;
    }

    public int defaultTopLimit() {
        return defaultTopLimit;
    }

    public int maxTopLimit() {
        return maxTopLimit;
    }

    public String formatAmount(long amount) {
        return String.format("%,d", amount);
    }

    private void registerCustomItems() {
        if (customItemService == null || physicalCurrencyKey == null || virtualCurrencyMachineKey == null) {
            return;
        }
        customItemService.register(CustomItemDefinition.simple(
                physicalCurrencyKey,
                Material.EMERALD,
                physicalCurrencyItemModelKey,
                legacyComponent(getConfig().getString("physical-currency.display-name", "货币"))));
        customItemService.register(new CustomItemDefinition(
                virtualCurrencyMachineKey,
                VIRTUAL_CURRENCY_MACHINE_ITEM,
                virtualCurrencyMachineKey,
                virtualCurrencyMachineItemModelKey,
                legacyComponent("&6虚拟货币机"),
                List.of(legacyComponent("&7放置后可在实体货币与账户余额之间转换。")),
                null));
    }

    private void registerCustomBlocks() {
        virtualCurrencyMachineBlockDefinition = new CustomBlockDefinition(
                virtualCurrencyMachineKey,
                VIRTUAL_CURRENCY_MACHINE_CARRIER,
                VIRTUAL_CURRENCY_MACHINE_ITEM,
                virtualCurrencyMachineItemModelKey,
                virtualCurrencyMachineDisplayKey,
                Component.text("Virtual Currency Machine"),
                1.0F,
                1.0F,
                QUARTZ_BLOCK_HARDNESS);
        customBlockService.registerBlock(virtualCurrencyMachineBlockDefinition);
    }

    public ItemStack createPhysicalCurrencyItem(int amount) {
        return customItemService.create(physicalCurrencyKey, amount);
    }

    public boolean isPhysicalCurrencyItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, physicalCurrencyKey);
    }

    public ItemStack createVirtualCurrencyMachineItem(int amount) {
        return customItemService.create(virtualCurrencyMachineKey, amount);
    }

    public boolean isVirtualCurrencyMachineItem(ItemStack item) {
        return customItemService != null && customItemService.isCustomItem(item, virtualCurrencyMachineKey);
    }

    public boolean canUseAction(CommandSender sender, String action) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String normalized = action.toLowerCase();
        Set<String> allowed = new HashSet<>();
        for (String value : getConfig().getStringList("access.default-allowed-actions")) {
            allowed.add(value.toLowerCase());
        }
        for (String value : getConfig().getStringList("access.players." + player.getUniqueId() + ".actions")) {
            allowed.add(value.toLowerCase());
        }
        return allowed.contains(normalized);
    }

    public void requestFormattedBalance(
            UUID playerUuid,
            String playerName,
            Consumer<String> success,
            Consumer<Exception> failure
    ) {
        runStorageTask(
                () -> {
                    EconomyAccount account = storage.getOrCreateAccount(playerUuid, playerName);
                    return formatAmount(account.balance()) + " " + currency.symbol();
                },
                success::accept,
                failure::accept);
    }

    public void requestFormattedBalanceValue(
            UUID playerUuid,
            String playerName,
            Consumer<String> success,
            Consumer<Exception> failure
    ) {
        runStorageTask(
                () -> {
                    EconomyAccount account = storage.getOrCreateAccount(playerUuid, playerName);
                    return formatAmount(account.balance());
                },
                success::accept,
                failure::accept);
    }

    public void registerBalanceChangeListener(Consumer<UUID> listener) {
        balanceChangeListeners.add(listener);
    }

    public void unregisterBalanceChangeListener(Consumer<UUID> listener) {
        balanceChangeListeners.remove(listener);
    }

    public void notifyBalanceChanged(UUID playerUuid) {
        for (Consumer<UUID> listener : Set.copyOf(balanceChangeListeners)) {
            try {
                listener.accept(playerUuid);
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "Economy balance listener failed.", exception);
            }
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String text = getConfig().getString("messages." + key, key);
        String prefix = getConfig().getString("messages.prefix", "");
        if (!"prefix".equals(key)) {
            text = prefix + text;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private Component legacyComponent(String value) {
        return LegacyComponentSerializer.legacySection().deserialize(color(value));
    }

    private void openMachineMenu(Player player) {
        MachineMainMenu menu = new MachineMainMenu();
        Inventory inventory = Bukkit.createInventory(menu, MACHINE_MENU_SIZE, color("&6虚拟货币机"));
        menu.inventory = inventory;
        fillMenu(inventory);
        inventory.setItem(11, menuItem(Material.EMERALD, "&a存入", List.of("&7将实体货币存入账户余额。")));
        inventory.setItem(15, menuItem(Material.CHEST, "&e取出", List.of("&7从账户余额取出实体货币。")));
        player.openInventory(inventory);
    }

    private void openDepositMenu(Player player) {
        DepositMenu menu = new DepositMenu();
        Inventory inventory = Bukkit.createInventory(menu, MACHINE_MENU_SIZE, color("&6虚拟货币机 · 存入"));
        menu.inventory = inventory;
        fillMenu(inventory);
        inventory.setItem(DEPOSIT_INPUT_SLOT, null);
        inventory.setItem(DEPOSIT_BALANCE_SLOT, loadingBalanceItem());
        inventory.setItem(DEPOSIT_CONFIRM_SLOT, menuItem(Material.LIME_CONCRETE, "&a确认存入", List.of("&7删除输入槽中的实体货币，并增加账户余额。")));
        inventory.setItem(22, menuItem(Material.ARROW, "&e返回", List.of("&7返回虚拟货币机主菜单。")));
        player.openInventory(inventory);
        refreshBalanceDisplay(player, inventory, DEPOSIT_BALANCE_SLOT);
    }

    private void openWithdrawMenu(Player player) {
        WithdrawMenu menu = new WithdrawMenu();
        Inventory inventory = Bukkit.createInventory(menu, MACHINE_MENU_SIZE, color("&6虚拟货币机 · 取出"));
        menu.inventory = inventory;
        fillMenu(inventory);
        inventory.setItem(WITHDRAW_OUTPUT_SLOT, null);
        inventory.setItem(WITHDRAW_BALANCE_SLOT, loadingBalanceItem());
        renderWithdrawAmount(inventory, menu);
        inventory.setItem(WITHDRAW_CONFIRM_SLOT, menuItem(Material.LIME_CONCRETE, "&a确认取出", List.of("&7扣除账户余额，并在输出槽生成实体货币。")));
        inventory.setItem(18, menuItem(Material.REDSTONE_BLOCK, "&c-10", List.of("&7减少取出数额。")));
        inventory.setItem(19, menuItem(Material.REDSTONE, "&c-1", List.of("&7减少取出数额。")));
        inventory.setItem(25, menuItem(Material.EMERALD, "&a+1", List.of("&7增加取出数额。")));
        inventory.setItem(26, menuItem(Material.EMERALD_BLOCK, "&a+10", List.of("&7增加取出数额。")));
        inventory.setItem(4, menuItem(Material.ARROW, "&e返回", List.of("&7返回虚拟货币机主菜单。")));
        player.openInventory(inventory);
        refreshBalanceDisplay(player, inventory, WITHDRAW_BALANCE_SLOT);
    }

    private void handleMachineMainClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() == 11) {
            openDepositMenu(player);
        } else if (event.getRawSlot() == 15) {
            openWithdrawMenu(player);
        }
    }

    private void handleDepositClick(Player player, DepositMenu menu, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (menu.busy) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 22) {
            event.setCancelled(true);
            returnTopItem(player, top, DEPOSIT_INPUT_SLOT);
            openMachineMenu(player);
            return;
        }
        if (rawSlot == DEPOSIT_CONFIRM_SLOT) {
            event.setCancelled(true);
            confirmDeposit(player, menu, top);
            return;
        }
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            if (rawSlot == DEPOSIT_INPUT_SLOT) {
                if (event.getClick() == ClickType.NUMBER_KEY) {
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    if (!isAir(hotbarItem) && !isPhysicalCurrencyItem(hotbarItem)) {
                        event.setCancelled(true);
                    }
                    return;
                }
                if (event.getClick() == ClickType.SWAP_OFFHAND) {
                    ItemStack offhandItem = player.getInventory().getItemInOffHand();
                    if (!isAir(offhandItem) && !isPhysicalCurrencyItem(offhandItem)) {
                        event.setCancelled(true);
                    }
                    return;
                }
                ItemStack cursor = event.getCursor();
                if (!isAir(cursor) && !isPhysicalCurrencyItem(cursor)) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (isPhysicalCurrencyItem(current)) {
                moveCurrencyToDepositInput(top, current);
            }
        }
    }

    private void handleWithdrawClick(Player player, WithdrawMenu menu, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (menu.busy) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 4) {
            event.setCancelled(true);
            returnTopItem(player, top, WITHDRAW_OUTPUT_SLOT);
            openMachineMenu(player);
            return;
        }
        if (rawSlot == WITHDRAW_OUTPUT_SLOT) {
            if (event.getClick().isKeyboardClick()) {
                event.setCancelled(true);
                return;
            }
            if (!isAir(event.getCursor())) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            event.setCancelled(true);
            switch (rawSlot) {
                case 18 -> adjustWithdrawAmount(top, menu, -10);
                case 19 -> adjustWithdrawAmount(top, menu, -1);
                case 25 -> adjustWithdrawAmount(top, menu, 1);
                case 26 -> adjustWithdrawAmount(top, menu, 10);
                case WITHDRAW_CONFIRM_SLOT -> confirmWithdraw(player, menu, top);
                default -> {
                }
            }
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    private void confirmDeposit(Player player, DepositMenu menu, Inventory inventory) {
        ItemStack input = inventory.getItem(DEPOSIT_INPUT_SLOT);
        if (!isPhysicalCurrencyItem(input)) {
            sendPrefixed(player, "&c请先放入实体货币。");
            return;
        }
        int amount = input.getAmount();
        inventory.setItem(DEPOSIT_INPUT_SLOT, null);
        menu.busy = true;
        runStorageTask(
                () -> storage.adjust(player.getUniqueId(), player.getName(), null, "VIRTUAL_CURRENCY_MACHINE", amount, "DEPOSIT", "physical-currency-deposit"),
                change -> {
                    menu.busy = false;
                    notifyBalanceChanged(player.getUniqueId());
                    inventory.setItem(DEPOSIT_BALANCE_SLOT, balanceItem(change.account().balance()));
                    sendPrefixed(player, "&a已存入 &e" + amount + " " + currency.symbol() + "&a。");
                },
                error -> {
                    menu.busy = false;
                    giveOrDrop(player, input);
                    sendPrefixed(player, "&c存入失败，实体货币已返还。");
                    getLogger().log(Level.WARNING, "Failed to deposit physical currency.", error);
                });
    }

    private void confirmWithdraw(Player player, WithdrawMenu menu, Inventory inventory) {
        if (!isAir(inventory.getItem(WITHDRAW_OUTPUT_SLOT))) {
            sendPrefixed(player, "&c请先取走输出槽中的实体货币。");
            return;
        }
        int amount = Math.max(1, Math.min(64, menu.amount));
        menu.busy = true;
        runStorageTask(
                () -> storage.adjust(player.getUniqueId(), player.getName(), null, "VIRTUAL_CURRENCY_MACHINE", -amount, "WITHDRAW", "physical-currency-withdraw"),
                change -> {
                    menu.busy = false;
                    notifyBalanceChanged(player.getUniqueId());
                    ItemStack output = createPhysicalCurrencyItem(amount);
                    if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                        inventory.setItem(WITHDRAW_OUTPUT_SLOT, output);
                        inventory.setItem(WITHDRAW_BALANCE_SLOT, balanceItem(change.account().balance()));
                    } else {
                        giveOrDrop(player, output);
                    }
                    sendPrefixed(player, "&a已取出 &e" + amount + " " + currency.symbol() + "&a。");
                },
                error -> {
                    menu.busy = false;
                    if (error instanceof InsufficientFundsException insufficient) {
                        sendPrefixed(player, "&c余额不足。当前余额：" + formatAmount(insufficient.balance()) + " " + currency.symbol());
                    } else {
                        sendPrefixed(player, "&c取出失败，请稍后再试。");
                        getLogger().log(Level.WARNING, "Failed to withdraw physical currency.", error);
                    }
                    refreshBalanceDisplay(player, inventory, WITHDRAW_BALANCE_SLOT);
                });
    }

    private void adjustWithdrawAmount(Inventory inventory, WithdrawMenu menu, int delta) {
        menu.amount = Math.max(1, Math.min(64, menu.amount + delta));
        renderWithdrawAmount(inventory, menu);
    }

    private void renderWithdrawAmount(Inventory inventory, WithdrawMenu menu) {
        inventory.setItem(WITHDRAW_AMOUNT_SLOT, menuItem(Material.GOLD_INGOT, "&e取出数额: " + menu.amount,
                List.of("&7单次最多取出 64 个实体货币。")));
    }

    private void refreshBalanceDisplay(Player player, Inventory inventory, int slot) {
        runStorageTask(
                () -> storage.getOrCreateAccount(player.getUniqueId(), player.getName()),
                account -> inventory.setItem(slot, balanceItem(account.balance())),
                error -> {
                    inventory.setItem(slot, menuItem(Material.BARRIER, "&c余额读取失败", List.of("&7请稍后再试。")));
                    getLogger().log(Level.WARNING, "Failed to load balance for virtual currency machine.", error);
                });
    }

    private void moveCurrencyToDepositInput(Inventory top, ItemStack source) {
        ItemStack input = top.getItem(DEPOSIT_INPUT_SLOT);
        if (!isAir(input) && (!isPhysicalCurrencyItem(input) || input.getAmount() >= input.getMaxStackSize())) {
            return;
        }
        int moveAmount = source.getAmount();
        if (!isAir(input)) {
            moveAmount = Math.min(moveAmount, input.getMaxStackSize() - input.getAmount());
            input.setAmount(input.getAmount() + moveAmount);
        } else {
            moveAmount = Math.min(moveAmount, source.getMaxStackSize());
            ItemStack moved = source.clone();
            moved.setAmount(moveAmount);
            top.setItem(DEPOSIT_INPUT_SLOT, moved);
        }
        source.setAmount(source.getAmount() - moveAmount);
    }

    private void returnTopItem(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (!isAir(item)) {
            inventory.setItem(slot, null);
            giveOrDrop(player, item);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (isAir(item)) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack leftoverItem : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
        }
    }

    private void fillMenu(Inventory inventory) {
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack loadingBalanceItem() {
        return menuItem(Material.SUNFLOWER, "&e余额读取中", List.of("&7正在读取你的账户余额。"));
    }

    private ItemStack balanceItem(long balance) {
        return menuItem(Material.SUNFLOWER, "&e账户余额", List.of("&f" + formatAmount(balance) + " " + currency.symbol(), "&7这是你的虚拟账户余额，不是实体货币物品。"));
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(this::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private void sendPrefixed(Player player, String message) {
        player.sendMessage(color(getConfig().getString("messages.prefix", "") + message));
    }

    private void breakVirtualCurrencyMachine(Block block, boolean dropMachineItem) {
        BlockKey key = BlockKey.of(block);
        if (!virtualCurrencyMachines.remove(key)) {
            return;
        }
        virtualCurrencyMachineFaces.remove(key);
        clearBreakSessionsFor(block);
        removeVirtualCurrencyMachineDisplays(block.getWorld(), key);
        if (dropMachineItem) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), createVirtualCurrencyMachineItem(1));
        }
        block.setType(Material.AIR, false);
        saveVirtualCurrencyMachines();
    }

    private void startMachineBreak(Player player, Block block) {
        String key = displayId(BlockKey.of(block));
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
            player.sendBlockDamage(session.location, 0.0F);
        }
    }

    private void clearBreakSessionsFor(Block block) {
        String key = displayId(BlockKey.of(block));
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
            BlockKey key = BlockKey.of(block);
            Block target = player.getTargetBlockExact(6);
            if (!virtualCurrencyMachines.contains(key)
                    || !ensureVirtualCurrencyMachineCarrier(block)
                    || player.getGameMode() != GameMode.SURVIVAL
                    || !player.getWorld().equals(block.getWorld())
                    || target == null
                    || !target.equals(block)
                    || player.getEyeLocation().distanceSquared(block.getLocation().add(0.5D, 0.5D, 0.5D)) > MACHINE_BREAK_REACH_SQUARED) {
                stopMachineBreak(player);
                continue;
            }
            session.progress += customBlockService.blockBreakProgressPerTick(player, virtualCurrencyMachineBlockDefinition);
            player.sendBlockDamage(session.location, (float) Math.min(1.0D, session.progress));
            if (session.progress >= 1.0D) {
                stopMachineBreak(player);
                breakVirtualCurrencyMachine(block, true);
            }
        }
    }

    private double quartzBlockBreakProgressPerTick(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPickaxe(tool)) {
            return 1.0D / QUARTZ_BLOCK_HARDNESS / 100.0D;
        }
        double speed = pickaxeSpeed(tool.getType());
        int efficiency = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiency > 0) {
            speed += efficiency * efficiency + 1.0D;
        }
        return speed / QUARTZ_BLOCK_HARDNESS / 30.0D;
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

    private boolean isVirtualCurrencyMachineBlock(Block block) {
        BlockKey key = BlockKey.of(block);
        if (!virtualCurrencyMachines.contains(key)) {
            return false;
        }
        if (ensureVirtualCurrencyMachineCarrier(block)) {
            return true;
        }
        virtualCurrencyMachines.remove(key);
        virtualCurrencyMachineFaces.remove(key);
        saveVirtualCurrencyMachines();
        return false;
    }

    private void loadVirtualCurrencyMachines() {
        virtualCurrencyMachines.clear();
        virtualCurrencyMachineFaces.clear();
        machinesConfig = YamlConfiguration.loadConfiguration(machinesFile);
        ConfigurationSection section = machinesConfig.getConfigurationSection("machines");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "machines." + key;
            String world = machinesConfig.getString(path + ".world", "");
            if (world.isBlank()) {
                continue;
            }
            BlockKey blockKey = new BlockKey(
                    world,
                    machinesConfig.getInt(path + ".x"),
                    machinesConfig.getInt(path + ".y"),
                    machinesConfig.getInt(path + ".z"));
            virtualCurrencyMachines.add(blockKey);
            virtualCurrencyMachineFaces.put(blockKey, parseFace(machinesConfig.getString(path + ".front", "SOUTH")));
        }
    }

    private void saveVirtualCurrencyMachines() {
        if (machinesConfig == null) {
            machinesConfig = new YamlConfiguration();
        }
        machinesConfig.set("machines", null);
        int index = 0;
        for (BlockKey key : virtualCurrencyMachines) {
            String path = "machines." + index++;
            machinesConfig.set(path + ".world", key.world());
            machinesConfig.set(path + ".x", key.x());
            machinesConfig.set(path + ".y", key.y());
            machinesConfig.set(path + ".z", key.z());
            machinesConfig.set(path + ".front", virtualCurrencyMachineFaces.getOrDefault(key, BlockFace.SOUTH).name());
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder.");
            }
            machinesConfig.save(machinesFile);
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to save virtual currency machines.", exception);
        }
    }

    private void refreshLoadedVirtualCurrencyMachineDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                refreshVirtualCurrencyMachineDisplays(chunk);
            }
        }
    }

    private void refreshVirtualCurrencyMachineDisplays(Chunk chunk) {
        for (BlockKey key : virtualCurrencyMachines) {
            if (!key.world().equals(chunk.getWorld().getName())) {
                continue;
            }
            if ((key.x() >> 4) == chunk.getX() && (key.z() >> 4) == chunk.getZ()) {
                refreshVirtualCurrencyMachineDisplay(chunk.getWorld().getBlockAt(key.x(), key.y(), key.z()));
            }
        }
    }

    private void refreshVirtualCurrencyMachineDisplay(Block block) {
        if (!ensureVirtualCurrencyMachineCarrier(block)) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        if (!virtualCurrencyMachines.contains(key)) {
            return;
        }
        BlockFace front = virtualCurrencyMachineFaces.getOrDefault(key, BlockFace.SOUTH);
        if (customBlockService != null && virtualCurrencyMachineBlockDefinition != null) {
            customBlockService.spawnOrReplaceDisplay(block, virtualCurrencyMachineBlockDefinition, front);
            return;
        }
        removeVirtualCurrencyMachineDisplays(block.getWorld(), key);
        Location location = block.getLocation().add(0.5D, 0.5D, 0.5D);
        block.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(createVirtualCurrencyMachineDisplayItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setDisplayWidth(1.0F);
            display.setDisplayHeight(1.0F);
            display.setCustomName(color("&6虚拟货币机"));
            display.setCustomNameVisible(false);
            display.setRotation(yawFor(front), pitchFor(front));
            display.getPersistentDataContainer().set(virtualCurrencyMachineDisplayKey, PersistentDataType.STRING, displayId(key));
        });
    }

    private void removeVirtualCurrencyMachineDisplays(World world, BlockKey key) {
        if (customBlockService != null && virtualCurrencyMachineBlockDefinition != null) {
            customBlockService.removeDisplays(
                    world,
                    new Location(world, key.x() + 0.5D, key.y() + 0.5D, key.z() + 0.5D),
                    virtualCurrencyMachineBlockDefinition,
                    displayId(key),
                    1.2D);
            return;
        }
        if (world == null) {
            return;
        }
        String id = displayId(key);
        Location center = new Location(world, key.x() + 0.5D, key.y() + 0.5D, key.z() + 0.5D);
        for (Entity entity : world.getNearbyEntities(center, 1.2D, 1.2D, 1.2D)) {
            String displayId = entity.getPersistentDataContainer().get(virtualCurrencyMachineDisplayKey, PersistentDataType.STRING);
            if (id.equals(displayId)) {
                entity.remove();
            }
        }
    }

    private boolean ensureVirtualCurrencyMachineCarrier(Block block) {
        if (block.getType() == VIRTUAL_CURRENCY_MACHINE_CARRIER) {
            return true;
        }
        if (block.getType() == VIRTUAL_CURRENCY_MACHINE_ITEM) {
            block.setType(VIRTUAL_CURRENCY_MACHINE_CARRIER, false);
            return true;
        }
        return false;
    }

    private ItemStack createVirtualCurrencyMachineDisplayItem() {
        ItemStack item = new ItemStack(VIRTUAL_CURRENCY_MACHINE_ITEM);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(virtualCurrencyMachineItemModelKey);
        meta.setDisplayName(color("&6虚拟货币机"));
        item.setItemMeta(meta);
        return item;
    }

    private String displayId(BlockKey key) {
        return key.world() + "|" + key.x() + "|" + key.y() + "|" + key.z();
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
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
    }

    private float pitchFor(BlockFace front) {
        return switch (front) {
            case UP -> 90.0F;
            case DOWN -> -90.0F;
            default -> 0.0F;
        };
    }

    private BlockFace parseFace(String value) {
        if (value == null) {
            return BlockFace.SOUTH;
        }
        try {
            BlockFace face = BlockFace.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            return switch (face) {
                case UP, DOWN, NORTH, EAST, SOUTH, WEST -> face;
                default -> BlockFace.SOUTH;
            };
        } catch (IllegalArgumentException ignored) {
            return BlockFace.SOUTH;
        }
    }

    private void removePhysicalCurrencyRecipes() {
        List<NamespacedKey> recipeKeys = new ArrayList<>();
        Iterator<Recipe> recipes = getServer().recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (isPhysicalCurrencyItem(recipe.getResult()) && recipe instanceof Keyed keyed) {
                recipeKeys.add(keyed.getKey());
            }
        }
        for (NamespacedKey key : recipeKeys) {
            getServer().removeRecipe(key);
        }
        if (!recipeKeys.isEmpty()) {
            getLogger().info("Removed physical currency recipes: " + recipeKeys.size());
        }
    }

    private void registerVirtualCurrencyMachineRecipe() {
        customItemService.unregisterRecipe(virtualCurrencyMachineRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(virtualCurrencyMachineRecipeKey, createVirtualCurrencyMachineItem(1));
        recipe.shape("IGI", "ARA", "QQQ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('A', Material.GOLD_INGOT);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('Q', Material.QUARTZ);
        customItemService.registerRecipe(recipe);
    }

    private boolean hasNetherQuartz(Player player) {
        return player.getInventory().contains(Material.QUARTZ);
    }

    private void unlockVirtualCurrencyMachineRecipe(Player player) {
        if (virtualCurrencyMachineRecipeKey == null || player.hasDiscoveredRecipe(virtualCurrencyMachineRecipeKey)) {
            return;
        }
        customItemService.discoverRecipe(player, virtualCurrencyMachineRecipeKey);
    }

    public <T> void runStorageTask(StorageSupplier<T> supplier, StorageSuccess<T> success, StorageFailure failure) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                T result = supplier.get();
                getServer().getScheduler().runTask(this, () -> success.accept(result));
            } catch (Exception exception) {
                getServer().getScheduler().runTask(this, () -> failure.accept(exception));
            }
        });
    }

    private CurrencyConfig loadCurrencyConfig() {
        String code = normalizeCurrencyCode(getConfig().getString("currency.code", "money"));
        String displayName = getConfig().getString("currency.display-name", "货币");
        String symbol = getConfig().getString("currency.symbol", displayName);
        long initialBalance = Math.max(0L, getConfig().getLong("currency.initial-balance", 0L));
        boolean allowNegative = getConfig().getBoolean("currency.allow-negative", false);
        return new CurrencyConfig(code, displayName, symbol, initialBalance, allowNegative);
    }

    private String normalizeCurrencyCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (normalized.isBlank()) {
            return "money";
        }
        return normalized;
    }

    private EconomyStorage createStorage(CurrencyConfig loadedCurrency) throws SQLException {
        String type = getConfig().getString("storage.type", "postgresql").toLowerCase();
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

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        return new PostgresEconomyStorage(getLogger(), jdbcUrl, username, password, loadedCurrency);
    }

    private void registerCommand(String name, EconomyCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Missing command declaration: " + name);
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @FunctionalInterface
    public interface StorageSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface StorageSuccess<T> {
        void accept(T result);
    }

    @FunctionalInterface
    public interface StorageFailure {
        void accept(Exception exception);
    }

    private static final class MachineMainMenu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class DepositMenu implements InventoryHolder {
        private Inventory inventory;
        private boolean busy;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class WithdrawMenu implements InventoryHolder {
        private Inventory inventory;
        private int amount = 1;
        private boolean busy;

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

    private record BlockKey(String world, int x, int y, int z) {
        private static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }
}
