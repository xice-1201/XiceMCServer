package com.xice.xicemc.customitem;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public record CustomItemDefinition(
        NamespacedKey id,
        Material material,
        NamespacedKey markerKey,
        NamespacedKey itemModelKey,
        Component displayName,
        List<Component> lore,
        Boolean glintOverride
) {
    public CustomItemDefinition {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("material must be an item material");
        }
        if (markerKey == null) {
            markerKey = id;
        }
        if (itemModelKey == null) {
            itemModelKey = id;
        }
        if (displayName == null) {
            displayName = Component.text(id.asString());
        }
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    public static CustomItemDefinition simple(
            NamespacedKey id,
            Material material,
            NamespacedKey itemModelKey,
            Component displayName
    ) {
        return new CustomItemDefinition(id, material, id, itemModelKey, displayName, List.of(), null);
    }
}
