package com.sortmousetweaks;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MsgSort(int containerId, boolean inventory, Sorter.Mode mode) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBoolean(inventory);
        buf.writeEnum(mode);
    }

    public static MsgSort decode(FriendlyByteBuf buf) {
        return new MsgSort(buf.readVarInt(), buf.readBoolean(), buf.readEnum(Sorter.Mode.class));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> Sorter.sort(player, containerId, inventory, mode));
        }
        ctx.setPacketHandled(true);
    }
}
