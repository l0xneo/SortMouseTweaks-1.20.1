package com.sortmousetweaks.client;

import com.sortmousetweaks.SortMouseTweaks;
import com.sortmousetweaks.Sorter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = SortMouseTweaks.MOD_ID, value = Dist.CLIENT)
public final class SlotTweaks {

    private static final long DOUBLE_CLICK_MS = 250;

    private static boolean shiftDragging;
    private static final Set<Slot> visited = new HashSet<>();

    @Nullable
    private static Slot collectStart;
    private static boolean collecting;
    @Nullable
    private static Slot lastPressSlot;
    private static long lastPressTime;

    private SlotTweaks() {
    }

    @Nullable
    private static AbstractContainerScreen<?> screen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen) ? container : null;
    }

    @SubscribeEvent
    public static void onPress(ScreenEvent.MouseButtonPressed.Pre event) {
        visited.clear();
        collectStart = null;
        collecting = false;
        AbstractContainerScreen<?> screen = screen(event.getScreen());
        if (screen == null) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        boolean left = event.getButton() == 0;
        long now = System.currentTimeMillis();
        boolean doubleClick = left && slot != null && slot == lastPressSlot && now - lastPressTime < DOUBLE_CLICK_MS;
        lastPressSlot = left ? slot : null;
        lastPressTime = now;
        boolean carrying = !screen.getMenu().getCarried().isEmpty();

        shiftDragging = left && Screen.hasShiftDown() && !carrying;
        if (shiftDragging && slot != null) {
            visited.add(slot);
        }

        if (left && carrying && slot != null && !Screen.hasShiftDown() && !doubleClick) {
            collectStart = slot;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        AbstractContainerScreen<?> screen = screen(event.getScreen());
        if (collectStart != null && screen != null) {
            event.setCanceled(true);
            Slot slot = screen.getSlotUnderMouse();
            if (!collecting && slot != null && slot != collectStart) {
                collecting = true;
                collect(screen, collectStart);
                visited.add(collectStart);
            }
            if (collecting && slot != null && visited.add(slot)) {
                collect(screen, slot);
            }
            return;
        }
        if (!shiftDragging || screen == null || event.getMouseButton() != 0 || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot != null && slot.hasItem() && visited.add(slot)) {
            click(screen, slot, 0, ClickType.QUICK_MOVE);
        }
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        AbstractContainerScreen<?> screen = screen(event.getScreen());
        if (collecting) {
            event.setCanceled(true);
        } else if (collectStart != null && screen != null && screen.getSlotUnderMouse() != null) {
            click(screen, screen.getSlotUnderMouse(), 0, ClickType.PICKUP);
            event.setCanceled(true);
        }
        shiftDragging = false;
        collectStart = null;
        collecting = false;
        visited.clear();
    }

    private static void collect(AbstractContainerScreen<?> screen, Slot slot) {
        ItemStack carried = screen.getMenu().getCarried();
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || slot instanceof ResultSlot || !slot.mayPlace(carried) || !slot.mayPickup(Minecraft.getInstance().player)
                || !ItemStack.isSameItemSameTags(stack, carried) || carried.getCount() + stack.getCount() > carried.getMaxStackSize()) {
            return;
        }
        click(screen, slot, 0, ClickType.PICKUP);
        click(screen, slot, 0, ClickType.PICKUP);
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        AbstractContainerScreen<?> screen = screen(event.getScreen());
        if (screen == null || event.getScrollDelta() == 0) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        Slot slot = screen.getSlotUnderMouse();

        if (slot == null || !slot.hasItem() || slot instanceof ResultSlot || !menu.getCarried().isEmpty()
                || menu.getClass().getName().equals(Sorter.CHEST_LINK_MENU)) {
            return;
        }
        boolean inPlayer = slot.container instanceof Inventory;
        List<Slot> other = menu.slots.stream().filter(s -> (s.container instanceof Inventory) != inPlayer && !(s instanceof ResultSlot)).toList();
        if (other.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (event.getScrollDelta() < 0) {
            sendOne(screen, slot);
        } else {
            pullOne(screen, slot, other);
        }
    }

    private static void sendOne(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot.getItem().getCount() == 1) {
            click(screen, slot, 0, ClickType.QUICK_MOVE);
            return;
        }
        click(screen, slot, 0, ClickType.PICKUP);
        click(screen, slot, 1, ClickType.PICKUP);
        click(screen, slot, 0, ClickType.QUICK_MOVE);
        click(screen, slot, 0, ClickType.PICKUP);
    }

    private static void pullOne(AbstractContainerScreen<?> screen, Slot target, List<Slot> other) {
        ItemStack want = target.getItem();
        if (want.getCount() >= target.getMaxStackSize(want)) {
            return;
        }
        for (int i = other.size() - 1; i >= 0; i--) {
            Slot source = other.get(i);
            if (source.hasItem() && source.mayPickup(Minecraft.getInstance().player) && ItemStack.isSameItemSameTags(source.getItem(), want)) {
                click(screen, source, 0, ClickType.PICKUP);
                click(screen, target, 1, ClickType.PICKUP);
                click(screen, source, 0, ClickType.PICKUP);
                return;
            }
        }
    }

    private static void click(AbstractContainerScreen<?> screen, Slot slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, button, type, mc.player);
    }
}
