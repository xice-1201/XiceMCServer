package com.xice.xicemc.economy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EconomyCommand implements CommandExecutor, TabCompleter {
    private final XiceEconomyPlugin plugin;

    public EconomyCommand(XiceEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "money" -> handleMoney(sender, args);
            case "pay" -> handlePay(sender, args);
            case "eco" -> handleEco(sender, args);
            case "xiceeconomy" -> handleXiceEconomy(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("money".equals(name)) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>(List.of("top", "log"));
                if (sender.hasPermission("xiceeconomy.admin")) {
                    values.addAll(onlinePlayerNames());
                }
                return matching(values, args[0]);
            }
            if (args.length == 2 && "log".equalsIgnoreCase(args[0]) && sender.hasPermission("xiceeconomy.admin")) {
                return matching(onlinePlayerNames(), args[1]);
            }
        }
        if ("pay".equals(name) && args.length == 1) {
            return matching(onlinePlayerNames(), args[0]);
        }
        if ("eco".equals(name)) {
            if (args.length == 1) {
                return matching(List.of("balance", "give", "take", "set", "top", "log", "reload"), args[0]);
            }
            if (args.length == 2 && List.of("balance", "give", "take", "set", "log").contains(args[0].toLowerCase(Locale.ROOT))) {
                return matching(onlinePlayerNames(), args[1]);
            }
        }
        if ("xiceeconomy".equals(name)) {
            if (args.length == 1) {
                return matching(List.of("reload"), args[0]);
            }
        }
        return List.of();
    }

    private boolean handleMoney(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xiceeconomy.use")) {
            plugin.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.send(sender, "player-only");
                return true;
            }
            queryBalance(sender, player.getUniqueId(), player.getName(), true);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("top".equals(sub)) {
            int limit = parseLimit(sender, args, 1, plugin.defaultTopLimit(), plugin.maxTopLimit());
            if (limit < 0) {
                return true;
            }
            queryTop(sender, limit);
            return true;
        }

        if ("log".equals(sub) || "history".equals(sub)) {
            handleMoneyHistory(sender, args);
            return true;
        }

        if (!sender.hasPermission("xiceeconomy.admin")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        resolveTargetAndRun(sender, args[0], target -> queryBalance(sender, target.uuid(), target.name(), false));
        return true;
    }

    private void handleMoneyHistory(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                plugin.send(sender, "player-only");
                return;
            }
            queryHistory(sender, player.getUniqueId(), player.getName(), plugin.defaultHistoryLimit());
            return;
        }

        if (args.length == 2 && isInteger(args[1])) {
            if (!(sender instanceof Player player)) {
                plugin.send(sender, "player-only");
                return;
            }
            int limit = parseLimit(sender, args, 1, plugin.defaultHistoryLimit(), plugin.maxHistoryLimit());
            if (limit >= 0) {
                queryHistory(sender, player.getUniqueId(), player.getName(), limit);
            }
            return;
        }

        if (!sender.hasPermission("xiceeconomy.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        int limit = parseLimit(sender, args, 2, plugin.defaultHistoryLimit(), plugin.maxHistoryLimit());
        if (limit < 0) {
            return;
        }
        resolveTargetAndRun(sender, args[1], target -> queryHistory(sender, target.uuid(), target.name(), limit));
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return true;
        }
        if (!sender.hasPermission("xiceeconomy.pay")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/pay <player> <amount>");
            return true;
        }
        long amount = parseAmount(sender, args[1]);
        if (amount <= 0) {
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        plugin.runStorageTask(
                () -> {
                    Target target = resolveTarget(targetName, onlineTarget);
                    if (player.getUniqueId().equals(target.uuid())) {
                        throw new SelfPaymentException();
                    }
                    return plugin.storage().transfer(player.getUniqueId(), player.getName(), target.uuid(), target.name(), amount);
                },
                result -> {
                    plugin.notifyBalanceChanged(result.sender().playerUuid());
                    plugin.notifyBalanceChanged(result.receiver().playerUuid());
                    plugin.send(sender, "paid", Map.of(
                            "player", result.receiver().playerName(),
                            "amount", plugin.formatAmount(result.amount()),
                            "balance", plugin.formatAmount(result.sender().balance()),
                            "currency", plugin.currency().symbol()));
                    if (onlineTarget != null) {
                        plugin.send(onlineTarget, "received", Map.of(
                                "player", player.getName(),
                                "amount", plugin.formatAmount(result.amount()),
                                "balance", plugin.formatAmount(result.receiver().balance()),
                                "currency", plugin.currency().symbol()));
                    }
                },
                error -> handleError(sender, error));
        return true;
    }

    private boolean handleEco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xiceeconomy.admin")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/eco <balance|give|take|set|top|log|reload>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (plugin.reloadEconomy()) {
                    plugin.send(sender, "reload-complete");
                } else {
                    plugin.send(sender, "storage-error");
                }
            }
            case "balance" -> {
                if (args.length < 2) {
                    sender.sendMessage("/eco balance <player>");
                    return true;
                }
                resolveTargetAndRun(sender, args[1], target -> queryBalance(sender, target.uuid(), target.name(), false));
            }
            case "give", "take", "set" -> handleAdminAdjust(sender, sub, args);
            case "top" -> {
                int limit = parseLimit(sender, args, 1, plugin.defaultTopLimit(), plugin.maxTopLimit());
                if (limit >= 0) {
                    queryTop(sender, limit);
                }
            }
            case "log", "history" -> {
                if (args.length < 2) {
                    sender.sendMessage("/eco log <player> [limit]");
                    return true;
                }
                int limit = parseLimit(sender, args, 2, plugin.defaultHistoryLimit(), plugin.maxHistoryLimit());
                if (limit >= 0) {
                    resolveTargetAndRun(sender, args[1], target -> queryHistory(sender, target.uuid(), target.name(), limit));
                }
            }
            default -> sender.sendMessage("/eco <balance|give|take|set|top|log|reload>");
        }
        return true;
    }

    private boolean handleXiceEconomy(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/economy reload");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("xiceeconomy.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                if (plugin.reloadEconomy()) {
                    plugin.send(sender, "reload-complete");
                } else {
                    plugin.send(sender, "storage-error");
                }
            }
            default -> sender.sendMessage("/economy reload");
        }
        return true;
    }

    private void handleAdminAdjust(CommandSender sender, String sub, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/eco " + sub + " <player> <amount> [reason]");
            return;
        }
        long amount = parseAmount(sender, args[2]);
        if (amount <= 0) {
            return;
        }
        String reason = args.length > 3 ? String.join(" ", List.of(args).subList(3, args.length)) : sub;
        UUID actorUuid = sender instanceof Player player ? player.getUniqueId() : null;
        String actorName = sender.getName();
        resolveTargetAndRun(sender, args[1], target -> plugin.runStorageTask(
                () -> switch (sub) {
                    case "give" -> plugin.storage().adjust(target.uuid(), target.name(), actorUuid, actorName,
                            amount, "ADMIN_GIVE", reason);
                    case "take" -> plugin.storage().adjust(target.uuid(), target.name(), actorUuid, actorName,
                            -amount, "ADMIN_TAKE", reason);
                    case "set" -> plugin.storage().setBalance(target.uuid(), target.name(), actorUuid, actorName,
                            amount, reason);
                    default -> throw new IllegalArgumentException("Unknown economy action: " + sub);
                },
                result -> {
                    plugin.notifyBalanceChanged(result.account().playerUuid());
                    plugin.send(sender, "admin-adjusted", Map.of(
                            "player", result.account().playerName(),
                            "balance", plugin.formatAmount(result.account().balance()),
                            "currency", plugin.currency().symbol()));
                },
                error -> handleError(sender, error)));
    }

    private void queryBalance(CommandSender sender, UUID playerUuid, String playerName, boolean self) {
        plugin.runStorageTask(
                () -> plugin.storage().getOrCreateAccount(playerUuid, playerName),
                account -> plugin.send(sender, self ? "balance-self" : "balance-other", Map.of(
                        "player", account.playerName(),
                        "balance", plugin.formatAmount(account.balance()),
                        "currency", plugin.currency().symbol())),
                error -> handleError(sender, error));
    }

    private void queryTop(CommandSender sender, int limit) {
        plugin.runStorageTask(
                () -> plugin.storage().topAccounts(limit),
                accounts -> {
                    plugin.send(sender, "top-header");
                    int rank = 1;
                    for (EconomyAccount account : accounts) {
                        plugin.send(sender, "top-line", Map.of(
                                "rank", String.valueOf(rank++),
                                "player", account.playerName(),
                                "balance", plugin.formatAmount(account.balance()),
                                "currency", plugin.currency().symbol()));
                    }
                },
                error -> handleError(sender, error));
    }

    private void queryHistory(CommandSender sender, UUID playerUuid, String playerName, int limit) {
        plugin.runStorageTask(
                () -> {
                    plugin.storage().getOrCreateAccount(playerUuid, playerName);
                    return plugin.storage().history(playerUuid, limit);
                },
                transactions -> {
                    plugin.send(sender, "history-header", Map.of("player", playerName));
                    if (transactions.isEmpty()) {
                        plugin.send(sender, "history-empty");
                        return;
                    }
                    for (EconomyTransaction transaction : transactions) {
                        plugin.send(sender, "history-line", Map.of(
                                "id", String.valueOf(transaction.id()),
                                "type", transaction.type(),
                                "amount", signedAmount(transaction.amount()),
                                "balance", plugin.formatAmount(transaction.balanceAfter()),
                                "currency", plugin.currency().symbol(),
                                "reason", transaction.reason() == null ? "" : transaction.reason()));
                    }
                },
                error -> handleError(sender, error));
    }

    private void resolveTargetAndRun(CommandSender sender, String input, TargetConsumer consumer) {
        Player onlineTarget = Bukkit.getPlayerExact(input);
        plugin.runStorageTask(
                () -> resolveTarget(input, onlineTarget),
                consumer::accept,
                error -> handleError(sender, error));
    }

    private Target resolveTarget(String input, Player onlineTarget) throws SQLException, PlayerNotFoundException {
        if (onlineTarget != null) {
            return new Target(onlineTarget.getUniqueId(), onlineTarget.getName());
        }
        return plugin.storage().findAccountByName(input)
                .map(account -> new Target(account.playerUuid(), account.playerName()))
                .orElseThrow(() -> new PlayerNotFoundException(input));
    }

    private void handleError(CommandSender sender, Exception error) {
        if (error instanceof PlayerNotFoundException notFound) {
            plugin.send(sender, "player-not-found", Map.of("player", notFound.player()));
            return;
        }
        if (error instanceof SelfPaymentException) {
            plugin.send(sender, "cannot-pay-self");
            return;
        }
        if (error instanceof InsufficientFundsException insufficient) {
            plugin.send(sender, "insufficient-funds", Map.of(
                    "balance", plugin.formatAmount(insufficient.balance()),
                    "currency", plugin.currency().symbol()));
            return;
        }
        plugin.getLogger().log(Level.WARNING, "Economy command failed.", error);
        plugin.send(sender, "storage-error");
    }

    private long parseAmount(CommandSender sender, String value) {
        try {
            long amount = Long.parseLong(value);
            if (amount <= 0) {
                plugin.send(sender, "invalid-amount");
                return -1;
            }
            return amount;
        } catch (NumberFormatException exception) {
            plugin.send(sender, "invalid-amount");
            return -1;
        }
    }

    private int parseLimit(CommandSender sender, String[] args, int index, int defaultLimit, int maxLimit) {
        if (args.length <= index) {
            return defaultLimit;
        }
        try {
            int limit = Integer.parseInt(args[index]);
            if (limit < 1 || limit > maxLimit) {
                plugin.send(sender, "invalid-limit", Map.of("max", String.valueOf(maxLimit)));
                return -1;
            }
            return limit;
        } catch (NumberFormatException exception) {
            plugin.send(sender, "invalid-limit", Map.of("max", String.valueOf(maxLimit)));
            return -1;
        }
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String signedAmount(long amount) {
        if (amount > 0) {
            return "+" + plugin.formatAmount(amount);
        }
        return plugin.formatAmount(amount);
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
    }

    private List<String> matching(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .toList();
    }

    private record Target(UUID uuid, String name) {
    }

    @FunctionalInterface
    private interface TargetConsumer {
        void accept(Target target);
    }

    private static final class PlayerNotFoundException extends Exception {
        private final String player;

        private PlayerNotFoundException(String player) {
            super("Player not found: " + player);
            this.player = player;
        }

        private String player() {
            return player;
        }
    }

    private static final class SelfPaymentException extends Exception {
    }
}
