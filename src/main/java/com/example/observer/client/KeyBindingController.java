package com.example.observer.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "observer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KeyBindingController {
    public static KeyMapping TOGGLE;
    public static boolean isHudVisible = true;
    private static boolean lastDown = false;

    @Mod.EventBusSubscriber(modid = "observer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent e) {
            TOGGLE = new KeyMapping("key.observer.toggle", GLFW.GLFW_KEY_O, "key.categories.observer");
            e.register(TOGGLE);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (TOGGLE == null) return;
        boolean down = TOGGLE.isDown();
        if (down && !lastDown) isHudVisible = !isHudVisible; // 只在按下那一刻触发
        lastDown = down;
    }
}
