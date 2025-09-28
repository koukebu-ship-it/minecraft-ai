package com.example.observer.agent;

import com.example.observer.ObserverMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AiInbox {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("agent");
    private static final Path FILE = DIR.resolve("inbox.jsonl");

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ai")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    writeInbox(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "text"));
                                    return 1;
                                }))
        );
    }

    private static void writeInbox(CommandSourceStack src, String text) {
        try {
            Files.createDirectories(DIR);
            JsonObject j = new JsonObject();
            j.addProperty("tick", src.getLevel().getGameTime());
            j.addProperty("name", src.getTextName());
            j.addProperty("text", text);
            try (OutputStream out = Files.newOutputStream(FILE,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write((GSON.toJson(j) + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));
            }
            src.sendSuccess(() -> Component.literal("已提交给AI: " + text), true);
        } catch (Exception e) {
            src.sendFailure(Component.literal("AI提交失败: " + e.getMessage()));
        }
    }
}
