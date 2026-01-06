package com.kokoda.ae2web.client.render;

import com.kokoda.ae2web.AE2WebMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private static final int RENDER_SIZE = 128;  // Render at higher resolution for SSAA
    private static final int OUTPUT_SIZE = 64;   // Final output size
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
            instance = new ItemIconRenderer(RENDER_SIZE);
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

        boolean prevDepthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        boolean projectionBackedUp = false;
        boolean lightingModified = false;
        boolean framebufferBound = false;

        try {
            frameBuffer.clear(Minecraft.ON_OSX);
            frameBuffer.bindWrite(true);
            framebufferBound = true;

            Matrix4f projection = new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000);
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;
            RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

            Lighting.setupForFlatItems();
            lightingModified = true;

            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            try {
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
            } finally {
                poseStack.popPose();
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to render item: {}", stack.getItem().getDescriptionId(), e);
            return null;
        } finally {
            // Restore GL state before returning to Minecraft rendering.
            if (prevDepthTestEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthFunc(prevDepthFunc);

            if (prevBlendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            GlStateManager._blendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);

            if (lightingModified) {
                Lighting.setupFor3DItems();
            }
            if (projectionBackedUp) {
                RenderSystem.restoreProjectionMatrix();
            }
            if (framebufferBound) {
                frameBuffer.unbindWrite();
            }
        }

        try (NativeImage nativeImage = new NativeImage(width, height, false)) {
            RenderSystem.bindTexture(frameBuffer.getColorTextureId());
            nativeImage.downloadTexture(0, false);
            return convertToPng(nativeImage);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to read rendered item pixels: {}", stack.getItem().getDescriptionId(), e);
            return null;
        }
    }

    private byte[] convertToPng(NativeImage nativeImage) throws IOException {
        // Convert NativeImage to BufferedImage (with Y flip)
        BufferedImage fullImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

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
                fullImage.setRGB(x, y, argb);
            }
        }

        // Downscale with high-quality interpolation for SSAA
        BufferedImage outputImage = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = outputImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(fullImage, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
        g2d.dispose();

        // Write to PNG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(outputImage, "PNG", baos);
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

    /**
     * Get all registered fluid IDs.
     */
    public static List<ResourceLocation> getAllFluidIds() {
        List<ResourceLocation> fluids = new ArrayList<>();
        for (ResourceLocation id : ForgeRegistries.FLUIDS.getKeys()) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
            // Skip empty/air fluids
            if (fluid != null && fluid != Fluids.EMPTY) {
                fluids.add(id);
            }
        }
        return fluids;
    }

    /**
     * Get FluidStack for a ResourceLocation.
     */
    public static @Nullable FluidStack getFluidStack(ResourceLocation fluidId) {
        var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }
        return new FluidStack(fluid, 1000);
    }

    /**
     * Render a fluid to PNG bytes.
     * Must be called on render thread.
     */
    public @Nullable byte[] renderFluidToPng(FluidStack fluidStack) {
        if (!initialized) {
            initialize();
        }

        if (fluidStack == null || fluidStack.isEmpty()) {
            return null;
        }

        RenderSystem.assertOnRenderThread();

        boolean prevBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        // Get fluid texture info before touching framebuffer / projection state.
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        int tintColor = fluidExtensions.getTintColor(fluidStack);
        if (stillTexture == null) {
            return null;
        }

        boolean projectionBackedUp = false;
        boolean framebufferBound = false;

        try {
            frameBuffer.clear(Minecraft.ON_OSX);
            frameBuffer.bindWrite(true);
            framebufferBound = true;

            Matrix4f projection = new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000);
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

            // Get sprite from atlas
            Minecraft mc = Minecraft.getInstance();
            TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);

            // Extract ARGB components
            float alpha = ((tintColor >> 24) & 0xFF) / 255.0f;
            float red = ((tintColor >> 16) & 0xFF) / 255.0f;
            float green = ((tintColor >> 8) & 0xFF) / 255.0f;
            float blue = (tintColor & 0xFF) / 255.0f;

            // If alpha is 0, assume fully opaque
            if (alpha == 0) alpha = 1.0f;

            // Render the fluid texture as a quad
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

            int padding = (int)(width * 0.075f);
            int size = width - padding * 2;

            BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();

            bufferBuilder.vertex(padding, padding + size, 0).uv(u0, v1).color(red, green, blue, alpha).endVertex();
            bufferBuilder.vertex(padding + size, padding + size, 0).uv(u1, v1).color(red, green, blue, alpha).endVertex();
            bufferBuilder.vertex(padding + size, padding, 0).uv(u1, v0).color(red, green, blue, alpha).endVertex();
            bufferBuilder.vertex(padding, padding, 0).uv(u0, v0).color(red, green, blue, alpha).endVertex();

            BufferUploader.drawWithShader(bufferBuilder.end());
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to render fluid: {}", fluidStack.getFluid().getFluidType().getDescriptionId(), e);
            return null;
        } finally {
            if (prevBlendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            GlStateManager._blendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);

            if (projectionBackedUp) {
                RenderSystem.restoreProjectionMatrix();
            }
            if (framebufferBound) {
                frameBuffer.unbindWrite();
            }
        }

        try (NativeImage nativeImage = new NativeImage(width, height, false)) {
            RenderSystem.bindTexture(frameBuffer.getColorTextureId());
            nativeImage.downloadTexture(0, false);
            return convertToPng(nativeImage);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to read rendered fluid pixels: {}", fluidStack.getFluid().getFluidType().getDescriptionId(), e);
            return null;
        }
    }
}
