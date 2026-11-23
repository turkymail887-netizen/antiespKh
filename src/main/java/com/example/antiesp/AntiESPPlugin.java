package com.example.antiesp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AntiESPPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Set<Location>> revealedBlocks = new HashMap<>();
    private double maxViewDistance;
    private int radiusXZ;
    private int radiusY;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        Bukkit.getPluginManager().registerEvents(this, this);

        long interval = Math.max(1L, getConfig().getLong("scan-interval-ticks", 5L));
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("antiesp.bypass")) {
                        continue;
                    }
                    updatePlayerView(player);
                }
            }
        }.runTaskTimer(this, 10L, interval);

        getLogger().info("AntiESP enabled!");
    }

    public void reloadSettings() {
        reloadConfig();
        maxViewDistance = getConfig().getDouble("max-view-distance", 24.0);
        radiusXZ = getConfig().getInt("radius-xz", 16);
        radiusY = getConfig().getInt("radius-y", 10);
        VisibilityChecker.loadBlocks(getConfig().getStringList("hidden-blocks"));
    }

    @Override
    public void onDisable() {
        revealedBlocks.clear();
        getLogger().info("AntiESP disabled!");
    }

    private void updatePlayerView(Player player) {
        UUID uuid = player.getUniqueId();
        Set<Location> shown = revealedBlocks.computeIfAbsent(uuid, k -> new HashSet<>());

        World world = player.getWorld();
        Location base = player.getLocation();

        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        int minY = Math.max(world.getMinHeight(), by - radiusY);
        int maxY = Math.min(world.getMaxHeight() - 1, by + radiusY);

        for (int x = bx - radiusXZ; x <= bx + radiusXZ; x++) {
            for (int z = bz - radiusXZ; z <= bz + radiusXZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();

                    if (!VisibilityChecker.HIDDEN_BLOCKS.contains(type)) {
                        continue;
                    }

                    Location loc = block.getLocation();
                    boolean visible = VisibilityChecker.canPlayerSeeBlock(player, block, maxViewDistance);

                    if (visible) {
                        if (!shown.contains(loc)) {
                            VisibilityChecker.sendRealBlock(player, block);
                            shown.add(loc);
                        }
                    } else {
                        if (!shown.contains(loc)) {
                            VisibilityChecker.sendFakeBlock(player, block);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        revealedBlocks.put(event.getPlayer().getUniqueId(), new HashSet<>());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        revealedBlocks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        for (Set<Location> set : revealedBlocks.values()) {
            set.remove(loc);
        }
        for (Player p : block.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 32 * 32) {
                VisibilityChecker.sendRealBlock(p, block);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (VisibilityChecker.HIDDEN_BLOCKS.contains(block.getType())) {
            revealedBlocks.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new HashSet<>())
                    .add(block.getLocation());
        }
    }
}
