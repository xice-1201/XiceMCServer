package com.xice.xicemc.customitem;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface CustomBlockService {
    void registerBlock(CustomBlockDefinition definition);

    void unregisterBlock(NamespacedKey id);

    Optional<CustomBlockDefinition> blockDefinition(NamespacedKey id);

    Collection<CustomBlockDefinition> blockDefinitions();

    void registerMultiBlock(CustomMultiBlockDefinition definition);

    void unregisterMultiBlock(NamespacedKey id);

    Optional<CustomMultiBlockDefinition> multiBlockDefinition(NamespacedKey id);

    Collection<CustomMultiBlockDefinition> multiBlockDefinitions();

    String blockId(Block block);

    String blockId(World world, int x, int y, int z);

    BlockFace frontFor(Player player);

    float yawFor(BlockFace front);

    float pitchFor(BlockFace front);

    ItemDisplay spawnOrReplaceDisplay(Block block, CustomBlockDefinition definition, BlockFace front);

    ItemDisplay spawnOrReplaceDisplay(Block block, NamespacedKey definitionId, BlockFace front);

    void removeDisplays(World world, Location center, CustomBlockDefinition definition, String blockId, double radius);

    Block relativePartBlock(Block anchor, CustomMultiBlockPart part, BlockFace front);

    List<Block> multiBlockPartBlocks(Block anchor, CustomMultiBlockDefinition definition, BlockFace front);

    boolean canPlaceMultiBlock(Block anchor, CustomMultiBlockDefinition definition, BlockFace front);

    void setMultiBlockCarrierBlocks(Block anchor, CustomMultiBlockDefinition definition, BlockFace front);

    List<BlockDisplay> spawnOrReplaceMultiBlockDisplays(Block anchor, CustomMultiBlockDefinition definition, BlockFace front, String structureId);

    void removeMultiBlockDisplays(World world, Location center, CustomMultiBlockDefinition definition, String structureId, double radius);

    double blockBreakProgressPerTick(Player player, double hardness);

    double blockBreakProgressPerTick(Player player, CustomBlockDefinition definition);

    boolean isCorrectTool(Player player, CustomBlockDefinition definition);
}
