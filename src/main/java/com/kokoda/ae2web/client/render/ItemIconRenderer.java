package com.kokoda.ae2web.client.render;

import com.kokoda.ae2web.AE2WebMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders item icons to PNG using OpenGL framebuffer.
 * Must run on the render thread (main client thread).
 */
@OnlyIn(Dist.CLIENT)
public class ItemIconRenderer {
    private static final int DEFAULT_SIZE = 64;
    private static final int FULL_BRIGHTNESS = 15728880;

    private static ItemIconRenderer instance;

    private final int width;
    private final int height;
    private RenderTarget frameBuffer;
    private boolean initialized = false;

    private ItemIconRenderer(int size) {
        this.width = size;
        this.height = size;
    }

    public static ItemIconRenderer getInstance() {
        if (instance == null) {
            instance = new ItemIconRenderer(DEFAULT_SIZE);
        }
        return instance;
    }

    /**
     * Initialize the renderer. Must be called on render thread.
     */
    public void initialize() {
        if (initialized) return;

        RenderSystem.assertOnRenderThread();

        frameBuffer = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        frameBuffer.setClearColor(0, 0, 0, 0);

        initialized = true;
        AE2WebMod.LOGGER.info("ItemIconRenderer initialized ({}x{})", width, height);
    }

    /**
     * Clean up resources.
     */
    public void cleanup() {
        if (frameBuffer != null) {
            frameBuffer.destroyBuffers();
            frameBuffer = null;
        }
        initialized = false;
    }

    /**
     * Render an item to PNG bytes.
     * Must be called on render thread.
     */
    public @Nullable byte[] renderItemToPng(ItemStack stack) {
        if (!initialized) {
            initialize();
        }

        if (stack == null || stack.isEmpty()) {
            return null;
        }

        RenderSystem.assertOnRenderThread();

        try {
            // Bind framebuffer
            frameBuffer.clear(Minecraft.ON_OSX);
            frameBuffer.bindWrite(true);

            // Setup projection
            Matrix4f projection = new Matrix4f().setOrtho(
                    0, width, height, 0, -1000, 1000
            );
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

            // Setup rendering state
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

            // Setup lighting for inventory items
            Lighting.setupForFlatItems();

            // Render item
            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.translate(width / 2.0f, height / 2.0f, 100);
            poseStack.scale(width * 0.85f, -height * 0.85f, 1);

            Minecraft mc = Minecraft.getInstance();
            ItemRenderer itemRenderer = mc.getItemRenderer();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.GUI,
                    FULL_BRIGHTNESS,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    mc.level,
                    0
            );

            bufferSource.endBatch();
            poseStack.popPose();

            // Restore state
            Lighting.setupFor3DItems();
            RenderSystem.restoreProjectionMatrix();
            frameBuffer.unbindWrite();

            // Read pixels using try-with-resources
            try (NativeImage nativeImage = new NativeImage(width, height, false)) {
                RenderSystem.bindTexture(frameBuffer.getColorTextureId());
                nativeImage.downloadTexture(0, false);

                // Process pixels: flip Y and convert to PNG
                return convertToPng(nativeImage);
            }

        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to render item: {}", stack.getItem().getDescriptionId(), e);
            frameBuffer.unbindWrite();
            return null;
        }
    }

    private byte[] convertToPng(NativeImage nativeImage) throws IOException {
        // Convert NativeImage to BufferedImage (with Y flip)
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // NativeImage is ABGR, flip Y axis
                int pixel = nativeImage.getPixelRGBA(x, height - 1 - y);

                // Convert from ABGR to ARGB
                int a = (pixel >> 24) & 0xFF;
                int b = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int r = pixel & 0xFF;

                // Make black pixels transparent
                if (r == 0 && g == 0 && b == 0 && a == 255) {
                    a = 0;
                }

                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                bufferedImage.setRGB(x, y, argb);
            }
        }

        // Write to PNG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Get all registered item IDs.
     */
    public static List<ResourceLocation> getAllItemIds() {
        return new ArrayList<>(ForgeRegistries.ITEMS.getKeys());
    }

    /**
     * Get ItemStack for a ResourceLocation.
     */
    public static @Nullable ItemStack getItemStack(ResourceLocation itemId) {
        var item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            return null;
        }
        return new ItemStack(item);
    }
}
