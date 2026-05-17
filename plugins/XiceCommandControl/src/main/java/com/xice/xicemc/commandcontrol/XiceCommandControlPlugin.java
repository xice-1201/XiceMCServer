package com.xice.xicemc.commandcontrol;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceCommandControlPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String SURVIVAL_COMMAND = "survival";
    private static final String CREATIVE_COMMAND = "creative";
    private static final String XCC_COMMAND = "xcc";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerCommand(SURVIVAL_COMMAND);
        registerCommand(CREATIVE_COMMAND);
        registerCommand(XCC_COMMAND);
        getLogger().info("XiceCommandControl enabled.");
    }

    private void registerCommand(String name) {
        var pluginCommand = getCommand(name);
        if (pluginCommand == null) {
            getLogger().warning("Command /" + name + " is not defined in plugin.yml.");
            return;
        }
        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (XCC_COMMAND.equals(commandName)) {
            return handleXccCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("player-only")));
            return true;
        }

        if (!canUse(player, commandName)) {
            player.sendMessage(color(message("no-permission")));
            return true;
        }

        switch (commandName) {
            case SURVIVAL_COMMAND -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(color(message("survival-enabled")));
            }
            case CREATIVE_COMMAND -> {
                player.setGameMode(GameMode.CREATIVE);
                player.sendMessage(color(message("creative-enabled")));
            }
            default -> player.sendMessage(color(message("unknown-command")));
        }
        return true;
    }

    private boolean handleXccCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color(message("xcc-usage")));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                if (!canUse(sender, "xcc.reload")) {
                    sender.sendMessage(color(message("no-permission")));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(color(message("reload-complete")));
                return true;
            }
            case "list" -> {
                if (!canUse(sender, "xcc.list")) {
                    sender.sendMessage(color(message("no-permission")));
                    return true;
                }
                if (args.length >= 2) {
                    listPlayerCommands(sender, args[1]);
                } else {
                    listAllConfiguredPlayers(sender);
                }
                return true;
            }
            default -> {
                sender.sendMessage(color(message("xcc-usage")));
                return true;
            }
        }
    }

    private boolean canUse(CommandSender sender, String commandName) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        Set<String> allowedCommands = new HashSet<>(lowercaseList(getConfig().getStringList("default-allowed-commands")));
        allowedCommands.addAll(lowercaseList(getConfig().getStringList("players." + player.getUniqueId() + ".commands")));
        return allowedCommands.contains(commandName.toLowerCase(Locale.ROOT));
    }

    private void listAllConfiguredPlayers(CommandSender sender) {
        ConfigurationSection players = getConfig().getConfigurationSection("players");
        if (players == null || players.getKeys(false).isEmpty()) {
            sender.sendMessage(color(message("list-empty")));
            return;
        }

        sender.sendMessage(color(message("list-header")));
        players.getKeys(false).stream()
                .sorted(Comparator.naturalOrder())
                .forEach(playerId -> sendPlayerCommandLine(sender, playerId));
    }

    private void listPlayerCommands(CommandSender sender, String query) {
        String playerId = resolveConfiguredPlayerId(query);
        if (playerId == null) {
            sender.sendMessage(color(message("list-not-found").replace("{query}", query)));
            return;
        }
        sendPlayerCommandLine(sender, playerId);
    }

    private String resolveConfiguredPlayerId(String query) {
        ConfigurationSection players = getConfig().getConfigurationSection("players");
        if (players == null) {
            return null;
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        for (String playerId : players.getKeys(false)) {
            if (playerId.equalsIgnoreCase(query)) {
                return playerId;
            }
            String configuredName = getConfig().getString("players." + playerId + ".name", "");
            if (configuredName.equalsIgnoreCase(query)) {
                return playerId;
            }
            try {
                UUID uuid = UUID.fromString(playerId);
                String cachedName = Bukkit.getOfflinePlayer(uuid).getName();
                if (cachedName != null && cachedName.toLowerCase(Locale.ROOT).equals(normalizedQuery)) {
                    return playerId;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid UUID entries can still be shown by key, but cannot be resolved through Bukkit.
            }
        }
        return null;
    }

    private void sendPlayerCommandLine(CommandSender sender, String playerId) {
        String configuredName = getConfig().getString("players." + playerId + ".name", playerId);
        List<String> commands = getConfig().getStringList("players." + playerId + ".commands");
        String commandText = commands.isEmpty() ? message("list-no-commands") : String.join(", ", commands);
        sender.sendMessage(color(message("list-line")
                .replace("{player}", configuredName)
                .replace("{uuid}", playerId)
                .replace("{commands}", commandText)));
    }

    private List<String> lowercaseList(List<String> values) {
        return values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private String message(String key) {
        return getConfig().getString("messages." + key, "");
    }

    private String color(String value) {
        return value.replace('&', '\u00A7');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!XCC_COMMAND.equalsIgnoreCase(command.getName())) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "list").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            ConfigurationSection players = getConfig().getConfigurationSection("players");
            if (players == null) {
                return Collections.emptyList();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return players.getKeys(false).stream()
                    .map(playerId -> getConfig().getString("players." + playerId + ".name", playerId))
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }

        return Collections.emptyList();
    }
}
