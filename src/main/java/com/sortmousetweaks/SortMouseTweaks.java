package com.sortmousetweaks;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@Mod(SortMouseTweaks.MOD_ID)
public class SortMouseTweaks {

    public static final String MOD_ID = "sortmousetweaks";
    private static final String VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    public SortMouseTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        CHANNEL.registerMessage(0, MsgSort.class, MsgSort::encode, MsgSort::decode, MsgSort::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, MsgTransfer.class, MsgTransfer::encode, MsgTransfer::decode, MsgTransfer::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
