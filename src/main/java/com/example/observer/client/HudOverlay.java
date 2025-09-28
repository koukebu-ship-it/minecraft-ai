package com.example.observer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "observer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HudOverlay {

    private static boolean enabled = true;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().player == null) return;
        if (Keys.TOGGLE_HUD != null && Keys.TOGGLE_HUD.consumeClick()) {
            enabled = !enabled;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post e) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 准星优先：只在看着方块时显示
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bhr)) return;

        BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
        EnergyAdapter.getEnergy(be).ifPresent(snap -> {
            GuiGraphics gg = e.getGuiGraphics();
            Font font = mc.font;

            String l1 = "§f" + snap.bestLabel();
            String l2 = String.format("§a%,d§7 / §a%,d §7FE  §8(%.1fm)", snap.stored(), snap.max(), snap.dist());

            int x = 8, y = 8;
            int w = Math.max(font.width(l1), font.width(l2)) + 8;
            int h = font.lineHeight * 2 + 6;

            gg.fill(x - 4, y - 4, x - 4 + w, y - 4 + h, 0x80000000);
            gg.drawString(font, l1, x, y, 0xFFFFFF, false);
            gg.drawString(font, l2, x, y + font.lineHeight + 2, 0xA0FFA0, false);

            // 进度条
            if (snap.max() > 0) {
                int bw = w - 8, bh = 4, by = y + font.lineHeight * 2 + 4;
                int fill = (int) Math.round(bw * (snap.stored() / (double) snap.max()));
                gg.fill(x, by, x + bw, by + bh, 0xFF202020);
                gg.fill(x, by, x + fill, by + bh, 0xFF5BEA7A);
            }
        });
    }
}
