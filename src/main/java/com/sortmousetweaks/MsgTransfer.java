package com.sortmousetweaks;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MsgTransfer(int containerId, Transfers.Action action) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeEnum(action);
    }

    public static MsgTransfer decode(FriendlyByteBuf buf) {
        return new MsgTransfer(buf.readVarInt(), buf.readEnum(Transfers.Action.class));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> Transfers.run(player, containerId, action));
        }
        ctx.setPacketHandled(true);
    }
}
