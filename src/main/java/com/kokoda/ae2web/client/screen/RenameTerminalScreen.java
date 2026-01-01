package com.kokoda.ae2web.client.screen;

import com.kokoda.ae2web.menu.RenameTerminalMenu;
import com.kokoda.ae2web.network.PacketHandler;
import com.kokoda.ae2web.network.RenameTerminalPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenameTerminalScreen extends AbstractContainerScreen<RenameTerminalMenu> {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/demo_background.png");

    private EditBox nameField;

    public RenameTerminalScreen(RenameTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();

        // Hide inventory label
        this.inventoryLabelY = 10000;
        this.titleLabelY = 6;

        // Create text input field
        int fieldX = this.leftPos + 10;
        int fieldY = this.topPos + 30;
        this.nameField = new EditBox(this.font, fieldX, fieldY, 156, 20, Component.literal("Name"));
        this.nameField.setMaxLength(50);
        this.nameField.setValue(this.menu.getCurrentName());
        this.nameField.setResponder(this::onNameChanged);
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        // OK button
        int buttonY = this.topPos + 60;
        this.addRenderableWidget(Button.builder(Component.literal("OK"), this::onConfirm)
                .pos(this.leftPos + 25, buttonY)
                .size(55, 20)
                .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), this::onCancel)
                .pos(this.leftPos + 95, buttonY)
                .size(55, 20)
                .build());
    }

    private void onNameChanged(String name) {
        // Optional: validate name in real-time
    }

    private void onConfirm(Button button) {
        String newName = this.nameField.getValue().trim();
        PacketHandler.sendToServer(new RenameTerminalPacket(this.menu.getBlockPos(), newName));
        this.onClose();
    }

    private void onCancel(Button button) {
        this.onClose();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int width, int height) {
        String currentValue = this.nameField.getValue();
        super.resize(mc, width, height);
        this.nameField.setValue(currentValue);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Draw a simple dark background
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xCC1a1d24);

        // Draw border
        int borderColor = 0xFF3d4654;
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + 1, borderColor);
        guiGraphics.fill(this.leftPos, this.topPos + this.imageHeight - 1, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, borderColor);
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + 1, this.topPos + this.imageHeight, borderColor);
        guiGraphics.fill(this.leftPos + this.imageWidth - 1, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, borderColor);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x00d4aa, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key confirms
        if (keyCode == 257) { // GLFW_KEY_ENTER
            String newName = this.nameField.getValue().trim();
            PacketHandler.sendToServer(new RenameTerminalPacket(this.menu.getBlockPos(), newName));
            this.onClose();
            return true;
        }

        // Let EditBox handle the key first
        if (this.nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
