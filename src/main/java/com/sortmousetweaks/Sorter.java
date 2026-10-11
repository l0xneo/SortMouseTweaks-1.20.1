package com.sortmousetweaks;

import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Sorter {

    public enum Mode {
        GROUPS,
        TYPE,
        NAME,
        ID,
        COUNT
    }

    public static final String CHEST_LINK_MENU = "com.smartsorting.gui.ContainerStorage";
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Key(Item item, CompoundTag tag) {
    }

    private static final Comparator<List<ItemStack>> BY_ID = Comparator
            .comparing((List<ItemStack> group) -> String.valueOf(ForgeRegistries.ITEMS.getKey(group.get(0).getItem())))
            .thenComparing(group -> String.valueOf(group.get(0).getTag()));

    private static final List<String> TYPES = List.of("ores", "raw_materials", "ingots", "gems", "dusts", "nuggets", "storage_blocks");

    private record Type(int index, String material) {
    }

    @Nullable
    private static Map<Item, Integer> creativeOrder;

    private Sorter() {
    }

    public static boolean canSort(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu.getClass().getName().equals(CHEST_LINK_MENU);
    }

    public static void sort(ServerPlayer player, int containerId, boolean inventory, Mode mode) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId) {
            return;
        }
        Comparator<List<ItemStack>> order = order(mode, player.serverLevel());
        if (inventory) {
            sortRange(List.of(player.getInventory()), 9, 36, order);
        } else if (!canSort(menu) || !menu.stillValid(player)) {
            return;
        } else if (menu instanceof ChestMenu chest) {
            sortRange(List.of(chest.getContainer()), 0, chest.getContainer().getContainerSize(), order);
        } else if (menu instanceof ShulkerBoxMenu) {
            Container box = menu.getSlot(0).container;
            sortRange(List.of(box), 0, box.getContainerSize(), order);
        } else {
            sortChestLink(menu, order);
        }
        menu.broadcastChanges();
    }

    private static void sortChestLink(AbstractContainerMenu menu, Comparator<List<ItemStack>> order) {
        List<Container> chests = chestLinkChests(menu);
        if (chests == null) {
            return;
        }
        sortRange(chests, 0, Integer.MAX_VALUE, order);
        try {
            menu.getClass().getMethod("refresh").invoke(menu);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Could not refresh Chest Link storage", e);
        }
    }

    @Nullable
    static List<Container> chestLinkChests(AbstractContainerMenu menu) {
        if (!menu.getClass().getName().equals(CHEST_LINK_MENU)) {
            return null;
        }
        try {
            List<Container> chests = new ArrayList<>();
            for (Object chest : (List<?>) menu.getClass().getMethod("getChests").invoke(menu)) {
                chests.add((Container) chest);
            }
            return chests;
        } catch (ReflectiveOperationException | ClassCastException e) {
            LOGGER.warn("Could not read Chest Link storage", e);
            return null;
        }
    }

    private static void sortRange(List<Container> containers, int from, int to, Comparator<List<ItemStack>> order) {
        List<ItemStack> items = new ArrayList<>();
        int slots = 0;
        for (Container container : containers) {
            for (int i = from; i < Math.min(to, container.getContainerSize()); i++, slots++) {
                items.add(container.getItem(i));
            }
        }
        List<ItemStack> sorted = merge(items, order);
        if (sorted.size() > slots) {
            return;
        }
        int next = 0;
        for (Container container : containers) {
            for (int i = from; i < Math.min(to, container.getContainerSize()); i++) {
                container.setItem(i, next < sorted.size() ? sorted.get(next++) : ItemStack.EMPTY);
            }
            container.setChanged();
        }
    }

    static List<ItemStack> merge(List<ItemStack> items, Comparator<List<ItemStack>> order) {
        Map<Key, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = stack.copy();
            List<ItemStack> group = groups.computeIfAbsent(new Key(rest.getItem(), rest.getTag()), key -> new ArrayList<>());
            for (ItemStack into : group) {
                if (rest.isEmpty()) {
                    break;
                }
                if (ItemHandlerHelper.canItemStacksStack(into, rest)) {
                    int move = Math.min(rest.getCount(), Math.max(0, into.getMaxStackSize() - into.getCount()));
                    into.grow(move);
                    rest.shrink(move);
                }
            }
            if (!rest.isEmpty()) {
                group.add(rest);
            }
        }
        List<List<ItemStack>> sortedGroups = new ArrayList<>(groups.values());
        sortedGroups.sort(order);
        List<ItemStack> sorted = new ArrayList<>();
        for (List<ItemStack> group : sortedGroups) {
            group.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
            sorted.addAll(group);
        }
        return sorted;
    }

    private static Comparator<List<ItemStack>> order(Mode mode, ServerLevel level) {
        Map<Item, Integer> creative = mode == Mode.GROUPS || mode == Mode.TYPE ? creativeOrder(level) : Map.of();
        Comparator<List<ItemStack>> byGroups = Comparator
                .comparingInt((List<ItemStack> group) -> creative.getOrDefault(group.get(0).getItem(), Integer.MAX_VALUE))
                .thenComparing(BY_ID);
        return switch (mode) {
            case GROUPS -> byGroups;
            case TYPE -> Comparator.comparingInt((List<ItemStack> group) -> type(group.get(0)).index())
                    .thenComparing(group -> type(group.get(0)).material())
                    .thenComparing(byGroups);
            case NAME -> Comparator.comparing((List<ItemStack> group) -> group.get(0).getHoverName().getString().toLowerCase(Locale.ROOT))
                    .thenComparing(BY_ID);
            case ID -> BY_ID;
            case COUNT -> Comparator.comparingInt((List<ItemStack> group) -> -group.stream().mapToInt(ItemStack::getCount).sum())
                    .thenComparing(BY_ID);
        };
    }

    private static Type type(ItemStack stack) {
        int best = TYPES.size();
        String material = "";
        for (TagKey<Item> tag : (Iterable<TagKey<Item>>) stack.getTags()::iterator) {
            if (!tag.location().getNamespace().equals("forge")) {
                continue;
            }
            String path = tag.location().getPath();
            int slash = path.indexOf('/');
            int index = TYPES.indexOf(slash < 0 ? path : path.substring(0, slash));
            if (index < 0 || index > best) {
                continue;
            }
            if (index < best) {
                best = index;
                material = "";
            }
            if (slash >= 0 && material.isEmpty()) {
                material = path.substring(slash + 1);
            }
        }
        return new Type(best, material);
    }

    private static Map<Item, Integer> creativeOrder(ServerLevel level) {
        if (creativeOrder != null) {
            return creativeOrder;
        }
        if (CreativeModeTabs.getDefaultTab().getDisplayItems().isEmpty()) {
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
        }
        Map<Item, Integer> order = new HashMap<>();
        for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY) {
                for (ItemStack stack : tab.getDisplayItems()) {
                    order.putIfAbsent(stack.getItem(), order.size());
                }
            }
        }
        if (!order.isEmpty()) {
            creativeOrder = order;
        }
        return order;
    }
}
