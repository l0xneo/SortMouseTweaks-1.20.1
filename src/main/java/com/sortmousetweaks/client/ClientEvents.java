package com.sortmousetweaks.client;

import com.sortmousetweaks.ClientConfig;
import com.sortmousetweaks.MsgSort;
import com.sortmousetweaks.MsgTransfer;
import com.sortmousetweaks.SortMouseTweaks;
import com.sortmousetweaks.Sorter;
import com.sortmousetweaks.Transfers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = SortMouseTweaks.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || screen instanceof CreativeModeInventoryScreen) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !SortMouseTweaks.CHANNEL.isRemotePresent(connection.getConnection())) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        Slot inventory = null;
        Slot container = null;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                if (inventory == null && slot.getContainerSlot() == 9) {
                    inventory = slot;
                }
            } else if (container == null) {
                container = slot;
            }
        }

        boolean chestLink = menu.getClass().getName().equals(Sorter.CHEST_LINK_MENU);
        boolean inside = chestLink || screen.getClass().getName().startsWith("net.minecraft.");
        boolean sortable = container != null && Sorter.canSort(menu);
        if (sortable) {
            int y = chestLink ? 4 : container.y - 13;
            int x = startX(screen, container, inside, 4);

            addTransfer(event, screen, x, y, "↓", "sortmousetweaks.take_all", Transfers.Action.TAKE_ALL);
            addButtons(event, screen, x + STEP, x + 3 * STEP, y, false);
            addTransfer(event, screen, x + 2 * STEP, y, "R", "sortmousetweaks.restock", Transfers.Action.RESTOCK);
        }
        if (inventory != null) {
            int x = startX(screen, inventory, inside, sortable ? 3 : 2);

            int first = sortable ? STEP : 0;
            if (sortable) {
                addTransfer(event, screen, x, inventory.y - 13, "↑", "sortmousetweaks.deposit_all", Transfers.Action.DEPOSIT_ALL);
            }
            addButtons(event, screen, x + first, x + first + STEP, inventory.y - 13, true);
        }
    }

    private static final int STEP = SortWidget.SIZE + 2;

    private static int startX(AbstractContainerScreen<?> screen, Slot firstSlot, boolean inside, int count) {
        return inside ? firstSlot.x + 9 * 18 - 1 - count * STEP + 2 : screen.getXSize() + 2;
    }

    private static void addTransfer(ScreenEvent.Init.Post event, AbstractContainerScreen<?> screen, int x, int y,
                                    String label, String tipKey, Transfers.Action action) {
        Component tip = Component.translatable(tipKey);
        event.addListener(new SortWidget(screen, x, y, () -> Component.literal(label), () -> tip,
                b -> SortMouseTweaks.CHANNEL.sendToServer(new MsgTransfer(screen.getMenu().containerId, action))));
    }

    private static void addButtons(ScreenEvent.Init.Post event, AbstractContainerScreen<?> screen, int x, int modeX, int y, boolean inventory) {
        Component sortTip = Component.translatable(inventory ? "sortmousetweaks.sort_inventory" : "sortmousetweaks.sort_container");
        event.addListener(new SortWidget(screen, x, y, () -> Component.literal("S"), () -> sortTip,
                b -> SortMouseTweaks.CHANNEL.sendToServer(new MsgSort(screen.getMenu().containerId, inventory, ClientConfig.MODE.get()))));
        SortWidget mode = new SortWidget(screen, modeX, y,
                () -> Component.translatable("sortmousetweaks.mode." + name() + ".short"),
                () -> Component.translatable("sortmousetweaks.mode." + name()),
                b -> {
                    Sorter.Mode[] modes = Sorter.Mode.values();
                    ClientConfig.MODE.set(modes[(ClientConfig.MODE.get().ordinal() + 1) % modes.length]);
                    ClientConfig.MODE.save();
                });
        mode.setTooltipDelay(750);
        event.addListener(mode);
    }

    private static String name() {
        return ClientConfig.MODE.get().name().toLowerCase(Locale.ROOT);
    }
}
