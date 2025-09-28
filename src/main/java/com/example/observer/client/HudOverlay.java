package com.example.observer.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = "observer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HudOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 扫描参数
    private static final int RADIUS = 6;           // 扫描半径（方块）
    private static final long SCAN_INTERVAL_MS = 250;

    // 缓存，减少每帧全图遍历
    private static long lastScanAt = 0L;
    private static EnergySnapshot cached = null;

    private record EnergySnapshot(BlockPos pos, String label, int stored, int max, double dist){}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScanAt > SCAN_INTERVAL_MS) {
            cached = scanNearestEnergy(mc.level, mc.player.blockPosition());
            lastScanAt = now;
        }

        if (cached == null) return;

        GuiGraphics gg = e.getGuiGraphics();
        Font font = mc.font;

        String l1 = "§f" + cached.label();
        String l2 = String.format("§a%d§7 / §a%d §7FE  §8(%.1fm)",
                cached.stored(), cached.max(), cached.dist());

        int x = 8, y = 8;
        // 简单背景
        int w = Math.max(font.width(Component.literal(l1)), font.width(Component.literal(l2))) + 8;
        int h = font.lineHeight * 2 + 6;
        gg.fill(x - 4, y - 4, x - 4 + w, y - 4 + h, 0x80000000);

        gg.drawString(font, l1, x, y, 0xFFFFFF, false);
        gg.drawString(font, l2, x, y + font.lineHeight + 2, 0xA0FFA0, false);
    }

    private static EnergySnapshot scanNearestEnergy(Level level, BlockPos center) {
        BlockPos bestPos = null;
        String bestLabel = null;
        int bestStored = 0, bestMax = 0;
        double bestDist2 = Double.MAX_VALUE;

        int r = RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be == null) continue;

                    be.getCapability(ForgeCapabilities.ENERGY).ifPresent(storage -> {
                        // no-op；只是为了触发 lambda，真正读取在下方
                    });

                    IEnergyStorage es = be.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                    if (es == null) continue;

                    int max = es.getMaxEnergyStored();
                    int cur = es.getEnergyStored();
                    if (max <= 0 && cur <= 0) continue;

                    double d2 = p.distToCenterSqr(center.getX()+0.5, center.getY()+0.5, center.getZ()+0.5);
                    if (d2 < bestDist2) {
                        bestDist2 = d2;
                        bestPos = p;
                        bestStored = cur;
                        bestMax = max;

                        var state = level.getBlockState(p);
                        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                        String name = state.getBlock().getName().getString();
                        bestLabel = (name == null || name.isBlank()) ? (key == null ? "Unknown" : key.toString()) : name;
                    }
                }
            }
        }

        if (bestPos == null) return null;
        double dist = Math.sqrt(bestDist2);
        return new EnergySnapshot(bestPos, bestLabel, bestStored, bestMax, dist);
    }
}
