package com.example.observer.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "observer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Keys {
    public static KeyMapping TOGGLE_HUD;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent e) {
        TOGGLE_HUD = new KeyMapping("key.observer.toggle_hud", GLFW.GLFW_KEY_H, "key.categories.observer");
        e.register(TOGGLE_HUD);
    }
}
