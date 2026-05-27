package com.xice.xicemc.customitem;

import net.kyori.adventure.text.Component;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public record CustomBlockDefinition(
        NamespacedKey id,
        Material carrierMaterial,
        Material displayMaterial,
        NamespacedKey displayModelKey,
        NamespacedKey displayMarkerKey,
        Component displayName,
        float displayWidth,
        float displayHeight,
        double breakHardness,
        CustomBlockTool requiredTool,
        CustomBlockToolTier minimumToolTier,
        Component infoName,
        List<Component> infoLore
) {
    public CustomBlockDefinition(
            NamespacedKey id,
            Material carrierMaterial,
            Material displayMaterial,
            NamespacedKey displayModelKey,
            NamespacedKey displayMarkerKey,
            Component displayName,
            float displayWidth,
            float displayHeight,
            double breakHardness
    ) {
        this(
                id,
                carrierMaterial,
                displayMaterial,
                displayModelKey,
                displayMarkerKey,
                displayName,
                displayWidth,
                displayHeight,
                breakHardness,
                CustomBlockTool.PICKAXE,
                CustomBlockToolTier.NONE,
                displayName,
                List.of());
    }

    public CustomBlockDefinition {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (carrierMaterial == null || !carrierMaterial.isBlock()) {
            throw new IllegalArgumentException("carrierMaterial must be a block material");
        }
        if (displayMaterial == null || !displayMaterial.isItem()) {
            throw new IllegalArgumentException("displayMaterial must be an item material");
        }
        if (displayModelKey == null) {
            displayModelKey = id;
        }
        if (displayMarkerKey == null) {
            displayMarkerKey = new NamespacedKey(id.getNamespace(), id.getKey() + "_display");
        }
        if (displayName == null) {
            displayName = Component.text(id.asString());
        }
        if (displayWidth <= 0.0F) {
            displayWidth = 1.0F;
        }
        if (displayHeight <= 0.0F) {
            displayHeight = 1.0F;
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
