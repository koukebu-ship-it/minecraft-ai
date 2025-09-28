package com.example.observer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;

@Mod(ObserverMod.MODID)
public class ObserverMod {
    public static final String MODID = "observer";

    public ObserverMod() {
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[Observer] loaded");
    }

    // —— 极简 JSONL 日志：写到 游戏目录/observer-logs/events.jsonl ——
    static final class Log {
        private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("observer-logs");
        private static final Path FILE = DIR.resolve("events.jsonl");
        static void write(String s) {
            try {
                Files.createDirectories(DIR);
                Files.write(FILE, (s + System.lineSeparator()).getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) { System.out.println("[Observer] log fail: " + e); }
        }
        static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
        static void kv(String type, String more) {
            write(String.format(Locale.ROOT, "{\"ts\":\"%s\",\"type\":\"%s\"%s}",
                    Instant.now(), esc(type), more));
        }
    }

    // 方块放置
    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        BlockPos p = e.getPos();
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(e.getPlacedBlock().getBlock());
        String who = e.getEntity() != null ? e.getEntity().getName().getString() : "<null>";
        Log.kv("place", String.format(Locale.ROOT,
                ",\"dim\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d,\"block\":\"%s\",\"by\":\"%s\"",
                level.dimension().location(), p.getX(), p.getY(), p.getZ(), key, Log.esc(who)));
    }

    // 方块破坏
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        BlockPos p = e.getPos();
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(e.getState().getBlock());
        String who = e.getPlayer() != null ? e.getPlayer().getGameProfile().getName() : "<null>";
        Log.kv("break", String.format(Locale.ROOT,
                ",\"dim\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d,\"block\":\"%s\",\"by\":\"%s\"",
                level.dimension().location(), p.getX(), p.getY(), p.getZ(), key, Log.esc(who)));
    }

    // 每 20 tick 扫描玩家周围 9x9x9：读取 Forge Energy（Thermal/AE2/机制类模组常用）
    private int tick = 0;
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Level level = e.player.level();
        if (level.isClientSide()) return;
        if (++tick % 20 != 0) return;

        BlockPos cp = e.player.blockPosition();
        Log.kv("player", String.format(Locale.ROOT,
                ",\"name\":\"%s\",\"dim\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d",
                Log.esc(e.player.getGameProfile().getName()),
                level.dimension().location(), cp.getX(), cp.getY(), cp.getZ()));

        int r = 4;
        for (int dx=-r; dx<=r; dx++) for (int dy=-r; dy<=r; dy++) for (int dz=-r; dz<=r; dz++) {
            BlockPos p = cp.offset(dx, dy, dz);
            if (!level.hasChunkAt(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            be.getCapability(ForgeCapabilities.ENERGY, null).ifPresent((IEnergyStorage es) -> {
                Log.kv("energy", String.format(Locale.ROOT,
                        ",\"x\":%d,\"y\":%d,\"z\":%d,\"stored\":%d,\"max\":%d",
                        p.getX(), p.getY(), p.getZ(), es.getEnergyStored(), es.getMaxEnergyStored()));
            });
        }
    }
}
