package com.xice.xicemc.auditlog;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public record PendingContainerChange(
        UUID playerUuid,
        String playerName,
        Location location,
        String containerType,
        Inventory inventory,
        Map<Material, Integer> before
) {
}
