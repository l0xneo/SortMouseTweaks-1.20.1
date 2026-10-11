package com.sortmousetweaks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public final class Transfers {

    public enum Action {
        TAKE_ALL,
        RESTOCK,
        DEPOSIT_ALL
    }

    private Transfers() {
    }

    public static void run(ServerPlayer player, int containerId, Action action) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || !Sorter.canSort(menu) || !menu.stillValid(player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        switch (action) {
            case TAKE_ALL -> {
                for (Slot slot : menu.slots) {
                    if (slot.container != inventory && slot.hasItem() && slot.mayPickup(player)) {
                        menu.quickMoveStack(player, slot.index);
                    }
                }
            }
            case DEPOSIT_ALL -> {
                for (Slot slot : menu.slots) {
                    if (slot.container == inventory && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36 && slot.hasItem()) {
                        menu.quickMoveStack(player, slot.index);
                    }
                }
            }
            case RESTOCK -> restock(player, menu);
        }
        menu.broadcastChanges();
    }

    private static void restock(ServerPlayer player, AbstractContainerMenu menu) {
        Inventory inventory = player.getInventory();

        List<Container> chests = Sorter.chestLinkChests(menu);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack want = inventory.getItem(i);
            if (want.isEmpty() || want.getCount() >= want.getMaxStackSize()) {
                continue;
            }
            if (chests != null) {
                for (Container chest : chests) {
                    for (int j = 0; j < chest.getContainerSize() && want.getCount() < want.getMaxStackSize(); j++) {
                        if (ItemHandlerHelper.canItemStacksStack(chest.getItem(j), want)) {
                            want.grow(chest.removeItem(j, want.getMaxStackSize() - want.getCount()).getCount());
                            chest.setChanged();
                        }
                    }
                }
            } else {
                for (Slot slot : menu.slots) {
                    if (want.getCount() >= want.getMaxStackSize()) {
                        break;
                    }
                    if (slot.container != inventory && slot.mayPickup(player) && ItemHandlerHelper.canItemStacksStack(slot.getItem(), want)) {
                        want.grow(slot.remove(want.getMaxStackSize() - want.getCount()).getCount());
                        slot.setChanged();
                    }
                }
            }
        }
        inventory.setChanged();
    }
}
