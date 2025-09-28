package com.example.observer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class HudOverlay {
    public static void render(GuiGraphics gg) {
        if (!KeyBindingController.isHudVisible) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.player.pick(20, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockEntity be = mc.level.getBlockEntity(((BlockHitResult) hit).getBlockPos());
        EnergyAdapter.getEnergy(be).ifPresent(snap -> {
            String text = String.format("%s: %,d / %,d FE (%.1fm)",
                    snap.bestLabel(), snap.stored(), snap.max(), snap.dist());
            Font font = mc.font;
            PoseStack ps = gg.pose();
            gg.drawString(font, text, 6, 6, 0xFFFFFF, true);
        });
    }
}
