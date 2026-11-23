package com.example.antiesp;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class VisibilityChecker {

    public static final Set<Material> HIDDEN_BLOCKS = EnumSet.noneOf(Material.class);

    public static void loadBlocks(List<String> names) {
        HIDDEN_BLOCKS.clear();
        for (String name : names) {
            Material mat = Material.matchMaterial(name.trim());
            if (mat != null && mat.isBlock()) {
                HIDDEN_BLOCKS.add(mat);
            }
        }
    }

    public static boolean canPlayerSeeBlock(Player player, Block targetBlock, double maxDistance) {
        Location eyeLoc = player.getEyeLocation();
        Location blockCenter = targetBlock.getLocation().add(0.5, 0.5, 0.5);

        double distanceSquared = eyeLoc.distanceSquared(blockCenter);
        if (distanceSquared > maxDistance * maxDistance) {
            return false;
        }

        Vector direction = blockCenter.toVector().subtract(eyeLoc.toVector());
        double distance = direction.length();
        if (distance < 0.001) {
            return true;
        }
        direction.normalize();

        World world = player.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(
                eyeLoc,
                direction,
                distance,
                FluidCollisionMode.NEVER,
                true
        );

        return hit == null || (hit.getHitBlock() != null && hit.getHitBlock().equals(targetBlock));
    }

    public static void sendFakeBlock(Player player, Block block) {
        Material fakeType = block.getY() < 0 ? Material.DEEPSLATE : Material.STONE;
        player.sendBlockChange(block.getLocation(), fakeType.createBlockData());
    }

    public static void sendRealBlock(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }
}
