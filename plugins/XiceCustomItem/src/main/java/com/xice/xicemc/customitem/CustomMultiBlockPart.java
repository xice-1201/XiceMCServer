package com.xice.xicemc.customitem;

import java.util.Map;
import org.bukkit.block.BlockFace;

public record CustomMultiBlockPart(
        String id,
        int offsetX,
        int offsetY,
        int offsetZ,
        Map<BlockFace, String> carrierBlockData,
        Map<BlockFace, String> displayBlockData,
        float displayWidth,
        float displayHeight
) {
    public CustomMultiBlockPart {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("part id cannot be blank");
        }
        carrierBlockData = carrierBlockData == null ? Map.of() : Map.copyOf(carrierBlockData);
        displayBlockData = displayBlockData == null ? Map.of() : Map.copyOf(displayBlockData);
        if (displayWidth <= 0.0F) {
            displayWidth = 1.0F;
        }
        if (displayHeight <= 0.0F) {
            displayHeight = 1.0F;
        }
    }

    public String carrierBlockData(BlockFace front) {
        return blockDataFor(carrierBlockData, front);
    }

    public String displayBlockData(BlockFace front) {
        return blockDataFor(displayBlockData, front);
    }

    private String blockDataFor(Map<BlockFace, String> values, BlockFace front) {
        String exact = values.get(front);
        if (exact != null) {
            return exact;
        }
        String fallback = values.get(BlockFace.NORTH);
        if (fallback != null) {
            return fallback;
        }
        return values.values().stream().findFirst().orElse(null);
    }
}
