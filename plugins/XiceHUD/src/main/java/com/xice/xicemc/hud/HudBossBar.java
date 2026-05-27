package com.xice.xicemc.hud;

import java.util.List;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;

public interface HudBossBar {
    void setTitle(String legacyTitle);

    void setProgress(double progress);

    void setColor(BarColor color);

    void addPlayer(Player player);

    void removePlayer(Player player);

    void removeAll();

    List<Player> getPlayers();
}
