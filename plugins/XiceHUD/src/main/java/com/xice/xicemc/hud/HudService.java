package com.xice.xicemc.hud;

import java.util.List;
import java.util.UUID;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

public interface HudService {
    void setActionBar(UUID playerUuid, String owner, String legacyText, int priority, long ttlTicks);

    void clearActionBar(UUID playerUuid, String owner);

    void setSidebar(UUID playerUuid, String owner, String legacyTitle, List<String> legacyLines, int priority);

    void clearSidebar(UUID playerUuid, String owner);

    void setTabListWorld(UUID playerUuid, String owner, String legacyWorldName, int priority);

    void clearTabListWorld(UUID playerUuid, String owner);

    HudBossBar createBossBar(String owner, String legacyTitle, BarColor color, BarStyle style);
}
