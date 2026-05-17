package com.xice.xicemc.auditlog;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ContainerSnapshot {
    private ContainerSnapshot() {
    }

    public static Map<Material, Integer> capture(Inventory inventory) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return counts;
    }
}
