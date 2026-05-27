package com.xice.xicemc.customitem;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;

public record CustomMultiBlockDefinition(
        NamespacedKey id,
        NamespacedKey displayMarkerKey,
        NamespacedKey displayPartMarkerKey,
        Component displayName,
        List<CustomMultiBlockPart> parts,
        double breakHardness,
        CustomBlockTool requiredTool,
        CustomBlockToolTier minimumToolTier,
        Component infoName,
        List<Component> infoLore
) {
    public CustomMultiBlockDefinition {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (displayMarkerKey == null) {
            displayMarkerKey = new NamespacedKey(id.getNamespace(), id.getKey() + "_display");
        }
        if (displayPartMarkerKey == null) {
            displayPartMarkerKey = new NamespacedKey(id.getNamespace(), id.getKey() + "_display_part");
        }
        if (displayName == null) {
            displayName = Component.text(id.asString());
        }
        parts = parts == null ? List.of() : List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("multi-block definition must contain at least one part");
        }
        if (breakHardness <= 0.0D) {
            breakHardness = 1.0D;
        }
        if (requiredTool == null) {
            requiredTool = CustomBlockTool.PICKAXE;
        }
        if (minimumToolTier == null) {
            minimumToolTier = CustomBlockToolTier.NONE;
        }
        if (infoName == null) {
            infoName = displayName;
        }
        infoLore = infoLore == null ? List.of() : List.copyOf(infoLore);
    }
}
