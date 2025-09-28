package com.example.observer;

import net.minecraftforge.fml.common.Mod;

@Mod(ObserverMod.MODID)
public class ObserverMod {
    public static final String MODID = "observer";

    public ObserverMod() {
        // 这里什么都不用做。
        // 我们所有功能的代码 (Keys.java, HudOverlay.java)
        // 已经通过 @Mod.EventBusSubscriber 注解自动注册到事件总线上了。
    }
}