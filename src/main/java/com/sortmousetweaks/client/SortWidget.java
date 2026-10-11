package com.sortmousetweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

class SortWidget extends Button {

    static final int SIZE = 11;

    private final AbstractContainerScreen<?> screen;
    private final int relX;
    private final int relY;
    private final Supplier<Component> label;
    private final Supplier<Component> tooltip;
    private Component shownTooltip;

    SortWidget(AbstractContainerScreen<?> screen, int relX, int relY, Supplier<Component> label, Supplier<Component> tooltip, OnPress onPress) {
        super(screen.getGuiLeft() + relX, screen.getGuiTop() + relY, SIZE, SIZE, label.get(), onPress, DEFAULT_NARRATION);
        this.screen = screen;
        this.relX = relX;
        this.relY = relY;
        this.label = label;
        this.tooltip = tooltip;
        setTooltipDelay(2000);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        setX(screen.getGuiLeft() + relX);
        setY(screen.getGuiTop() + relY);
        setMessage(label.get());
        Component tip = tooltip.get();
        if (!tip.equals(shownTooltip)) {
            shownTooltip = tip;
            setTooltip(Tooltip.create(tip));
        }
        int x = getX();
        int y = getY();
        boolean hot = isHoveredOrFocused();
        boolean down = hot && isMouseOver(mouseX, mouseY) && Minecraft.getInstance().mouseHandler.isLeftPressed();
        g.fill(x, y, x + SIZE, y + SIZE, 0xFF101418);
        g.fillGradient(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, hot ? 0xFF4FA3E0 : 0xFF3A4A5C, hot ? 0xFF2C6FA8 : 0xFF243140);
        g.fill(x + 1, y + 1, x + SIZE - 1, y + 2, down ? 0x40000000 : 0x60FFFFFF);
        g.fill(x + 1, y + SIZE - 2, x + SIZE - 1, y + SIZE - 1, down ? 0x30FFFFFF : 0x50000000);
        Font font = Minecraft.getInstance().font;

        g.drawString(font, getMessage(), x + (SIZE - font.width(getMessage()) + 1) / 2, y + (down ? 3 : 2), hot ? 0xFFFFFFFF : 0xFFD8E2EC, true);
    }
}
