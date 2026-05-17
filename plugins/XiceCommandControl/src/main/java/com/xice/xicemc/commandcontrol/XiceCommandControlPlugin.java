package com.xice.xicemc.commandcontrol;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class XiceCommandControlPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String SURVIVAL_COMMAND = "survival";
    private static final String CREATIVE_COMMAND = "creative";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerCommand(SURVIVAL_COMMAND);
        registerCommand(CREATIVE_COMMAND);
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("player-only")));
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
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

    private boolean canUse(Player player, String commandName) {
        Set<String> allowedCommands = new HashSet<>(lowercaseList(getConfig().getStringList("default-allowed-commands")));
        allowedCommands.addAll(lowercaseList(getConfig().getStringList("players." + player.getName() + ".commands")));
        allowedCommands.addAll(lowercaseList(getConfig().getStringList("players." + player.getUniqueId() + ".commands")));
        return allowedCommands.contains(commandName);
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
        return value.replace('&', '§');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
