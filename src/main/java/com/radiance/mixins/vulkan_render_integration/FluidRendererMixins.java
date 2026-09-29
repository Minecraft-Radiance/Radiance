package com.radiance.mixins.vulkan_render_integration;

import static net.minecraft.client.renderer.block.LiquidBlockRenderer.shouldRenderFace;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public abstract class FluidRendererMixins {

    @Final
    @Shadow
    private TextureAtlasSprite[] lavaIcons;

    @Final
    @Shadow
    private TextureAtlasSprite[] waterIcons;

    @Shadow
    private TextureAtlasSprite waterOverlay;

    @Shadow
    private static boolean isNeighborSameFluid(FluidState a, FluidState b) {
        return false;
    }

    @Shadow
    private static boolean isFaceOccludedByState(BlockGetter world, Direction direction, float height,
        BlockPos pos, BlockState state) {
        return false;
    }

    @Shadow
    private static boolean isFaceOccludedByNeighbor(BlockGetter world, BlockPos pos, Direction direction,
        float maxDeviation, BlockState state) {
        return false;
    }

    @Shadow
    private static boolean isFaceOccludedBySelf(BlockGetter world, BlockPos pos, BlockState state,
        Direction direction) {
        return false;
    }

    @Shadow
    protected abstract float calculateAverageHeight(BlockAndTintGetter world,
        Fluid fluid,
        float originHeight,
        float northSouthHeight,
        float eastWestHeight,
        BlockPos pos);

    @Shadow
    protected abstract void addWeightedHeight(float[] weightedAverageHeight, float height);

    @Shadow
    protected abstract float getHeight(BlockAndTintGetter world, Fluid fluid, BlockPos pos);

    @Shadow
    protected abstract float getHeight(BlockAndTintGetter world, Fluid fluid, BlockPos pos,
        BlockState blockState, FluidState fluidState);

    @Shadow
    protected abstract int getLightColor(BlockAndTintGetter world, BlockPos pos);

    @Unique
    private void vertex(VertexConsumer vertexConsumer,
        float x,
        float y,
        float z,
        float red,
        float green,
        float blue,
        float u,
        float v,
        int light,
        float nx,
        float ny,
        float nz) {
        vertexConsumer.addVertex(x, y, z)
            .setColor(red, green, blue, 1.0F)
            .setUv(u, v)
            .setLight(light)
            .setNormal(nx, ny, nz);
    }

    @Inject(method =
        "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void addNormalToVertex(BlockAndTintGetter world,
        BlockPos pos,
        VertexConsumer vertexConsumer,
        BlockState blockState,
        FluidState fluidState,
        CallbackInfo ci) {
        boolean isLava = fluidState.is(FluidTags.LAVA);
        TextureAtlasSprite[] fluidSprites = isLava ? this.lavaIcons : this.waterIcons;
        int tintColor = isLava ? 16777215 : BiomeColors.getAverageWaterColor(world, pos);
        float red = (tintColor >> 16 & 0xFF) / 255.0F;
        float green = (tintColor >> 8 & 0xFF) / 255.0F;
        float blue = (tintColor & 0xFF) / 255.0F;

        BlockState stateDown = world.getBlockState(pos.relative(Direction.DOWN));
        FluidState fluidDown = stateDown.getFluidState();
        BlockState stateUp = world.getBlockState(pos.relative(Direction.UP));
        FluidState fluidUp = stateUp.getFluidState();
        BlockState stateNorth = world.getBlockState(pos.relative(Direction.NORTH));
        FluidState fluidNorth = stateNorth.getFluidState();
        BlockState stateSouth = world.getBlockState(pos.relative(Direction.SOUTH));
        FluidState fluidSouth = stateSouth.getFluidState();
        BlockState stateWest = world.getBlockState(pos.relative(Direction.WEST));
        FluidState fluidWest = stateWest.getFluidState();
        BlockState stateEast = world.getBlockState(pos.relative(Direction.EAST));
        FluidState fluidEast = stateEast.getFluidState();

        boolean renderTop = !isNeighborSameFluid(fluidState, fluidUp);
        boolean
            renderBottom =
            shouldRenderFace(world, pos, fluidState, blockState, Direction.DOWN, fluidDown)
                && !isFaceOccludedByNeighbor(world, pos, Direction.DOWN, 0.8888889F, stateDown);
        boolean renderNorth = shouldRenderFace(world, pos, fluidState, blockState, Direction.NORTH,
            fluidNorth);
        boolean renderSouth = shouldRenderFace(world, pos, fluidState, blockState, Direction.SOUTH,
            fluidSouth);
        boolean renderWest = shouldRenderFace(world, pos, fluidState, blockState, Direction.WEST,
            fluidWest);
        boolean renderEast = shouldRenderFace(world, pos, fluidState, blockState, Direction.EAST,
            fluidEast);

        if (renderTop || renderBottom || renderEast || renderWest || renderNorth || renderSouth) {
            float lightDown = world.getShade(Direction.DOWN, true);
            float lightUp = world.getShade(Direction.UP, true);
            float lightNorth = world.getShade(Direction.NORTH, true);
            float lightWest = world.getShade(Direction.WEST, true);

            Fluid fluid = fluidState.getType();
            float currentHeight = this.getHeight(world, fluid, pos, blockState, fluidState);
            float heightNE;
            float heightNW;
            float heightSE;
            float heightSW;

            if (currentHeight >= 1.0F) {
                heightNE = 1.0F;
                heightNW = 1.0F;
                heightSE = 1.0F;
                heightSW = 1.0F;
            } else {
                float hNorth = this.getHeight(world, fluid, pos.north(), stateNorth,
                    fluidNorth);
                float hSouth = this.getHeight(world, fluid, pos.south(), stateSouth,
                    fluidSouth);
                float hEast = this.getHeight(world, fluid, pos.east(), stateEast, fluidEast);
                float hWest = this.getHeight(world, fluid, pos.west(), stateWest, fluidWest);

                heightNE =
                    this.calculateAverageHeight(world,
                        fluid,
                        currentHeight,
                        hNorth,
                        hEast,
                        pos.relative(Direction.NORTH)
                            .relative(Direction.EAST));
                heightNW =
                    this.calculateAverageHeight(world,
                        fluid,
                        currentHeight,
                        hNorth,
                        hWest,
                        pos.relative(Direction.NORTH)
                            .relative(Direction.WEST));
                heightSE =
                    this.calculateAverageHeight(world,
                        fluid,
                        currentHeight,
                        hSouth,
                        hEast,
                        pos.relative(Direction.SOUTH)
                            .relative(Direction.EAST));
                heightSW =
                    this.calculateAverageHeight(world,
                        fluid,
                        currentHeight,
                        hSouth,
                        hWest,
                        pos.relative(Direction.SOUTH)
                            .relative(Direction.WEST));
            }

            float x = pos.getX() & 15;
            float y = pos.getY() & 15;
            float z = pos.getZ() & 15;
            float bottomYOffset = renderBottom ? 0.001F : 0.0F;

            if (renderTop && !isFaceOccludedByNeighbor(world, pos, Direction.UP,
                Math.min(Math.min(heightNW, heightSW), Math.min(heightSE, heightNE)), stateUp)) {

                heightNW -= 0.001F;
                heightSW -= 0.001F;
                heightSE -= 0.001F;
                heightNE -= 0.001F;

                Vec3 flowVector = fluidState.getFlow(world, pos);
                float u1, v1, u2, v2, u3, v3, u4, v4;

                if (flowVector.x == 0.0 && flowVector.z == 0.0) {
                    TextureAtlasSprite stillSprite = fluidSprites[0];
                    u1 = stillSprite.getU(0.0F);
                    v1 = stillSprite.getV(0.0F);
                    u2 = u1;
                    v2 = stillSprite.getV(1.0F);
                    u3 = stillSprite.getU(1.0F);
                    v3 = v2;
                    u4 = u3;
                    v4 = v1;
                } else {
                    TextureAtlasSprite flowSprite = fluidSprites[1];
                    float angle =
                        (float) Mth.atan2(flowVector.z, flowVector.x) - (float) (Math.PI
                            / 2);
                    float sin = Mth.sin(angle) * 0.25F;
                    float cos = Mth.cos(angle) * 0.25F;

                    u1 = flowSprite.getU(0.5F + (-cos - sin));
                    v1 = flowSprite.getV(0.5F + (-cos + sin));
                    u2 = flowSprite.getU(0.5F + (-cos + sin));
                    v2 = flowSprite.getV(0.5F + (cos + sin));
                    u3 = flowSprite.getU(0.5F + (cos + sin));
                    v3 = flowSprite.getV(0.5F + (cos - sin));
                    u4 = flowSprite.getU(0.5F + (cos - sin));
                    v4 = flowSprite.getV(0.5F + (-cos - sin));
                }

                float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
                float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
                float animationDelta = fluidSprites[0].uvShrinkRatio();

                u1 = Mth.lerp(animationDelta, u1, uAvg);
                u2 = Mth.lerp(animationDelta, u2, uAvg);
                u3 = Mth.lerp(animationDelta, u3, uAvg);
                u4 = Mth.lerp(animationDelta, u4, uAvg);
                v1 = Mth.lerp(animationDelta, v1, vAvg);
                v2 = Mth.lerp(animationDelta, v2, vAvg);
                v3 = Mth.lerp(animationDelta, v3, vAvg);
                v4 = Mth.lerp(animationDelta, v4, vAvg);

                int packedLight = this.getLightColor(world, pos);
                float shadedRed = lightUp * red;
                float shadedGreen = lightUp * green;
                float shadedBlue = lightUp * blue;

                float normalX = (heightNW - heightNE) + (heightSW - heightSE);
                float normalZ = (heightNW - heightSW) + (heightNE - heightSE);
                float normalY = 1.0F;

                float length = Mth.sqrt(
                    normalX * normalX + normalY * normalY + normalZ * normalZ);
                normalX /= length;
                normalY /= length;
                normalZ /= length;

                this.vertex(vertexConsumer,
                    x + 0.0F,
                    y + heightNW,
                    z + 0.0F,
                    shadedRed,
                    shadedGreen,
                    shadedBlue,
                    u1,
                    v1,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
                this.vertex(vertexConsumer,
                    x + 0.0F,
                    y + heightSW,
                    z + 1.0F,
                    shadedRed,
                    shadedGreen,
                    shadedBlue,
                    u2,
                    v2,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
                this.vertex(vertexConsumer,
                    x + 1.0F,
                    y + heightSE,
                    z + 1.0F,
                    shadedRed,
                    shadedGreen,
                    shadedBlue,
                    u3,
                    v3,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
                this.vertex(vertexConsumer,
                    x + 1.0F,
                    y + heightNE,
                    z + 0.0F,
                    shadedRed,
                    shadedGreen,
                    shadedBlue,
                    u4,
                    v4,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);

                if (fluidState.shouldRenderBackwardUpFace(world, pos.above())) {
                    this.vertex(vertexConsumer,
                        x + 0.0F,
                        y + heightNW,
                        z + 0.0F,
                        shadedRed,
                        shadedGreen,
                        shadedBlue,
                        u1,
                        v1,
                        packedLight,
                        -normalX,
                        -normalY,
                        -normalZ);
                    this.vertex(vertexConsumer,
                        x + 1.0F,
                        y + heightNE,
                        z + 0.0F,
                        shadedRed,
                        shadedGreen,
                        shadedBlue,
                        u4,
                        v4,
                        packedLight,
                        -normalX,
                        -normalY,
                        -normalZ);
                    this.vertex(vertexConsumer,
                        x + 1.0F,
                        y + heightSE,
                        z + 1.0F,
                        shadedRed,
                        shadedGreen,
                        shadedBlue,
                        u3,
                        v3,
                        packedLight,
                        -normalX,
                        -normalY,
                        -normalZ);
                    this.vertex(vertexConsumer,
                        x + 0.0F,
                        y + heightSW,
                        z + 1.0F,
                        shadedRed,
                        shadedGreen,
                        shadedBlue,
                        u2,
                        v2,
                        packedLight,
                        -normalX,
                        -normalY,
                        -normalZ);
                }
            }

            if (renderBottom) {
                float minU = fluidSprites[0].getU0();
                float maxU = fluidSprites[0].getU1();
                float minV = fluidSprites[0].getV0();
                float maxV = fluidSprites[0].getV1();

                int packedLightDown = this.getLightColor(world, pos.below());
                float shadedRedDown = lightDown * red;
                float shadedGreenDown = lightDown * green;
                float shadedBlueDown = lightDown * blue;

                this.vertex(vertexConsumer,
                    x,
                    y + bottomYOffset,
                    z + 1.0F,
                    shadedRedDown,
                    shadedGreenDown,
                    shadedBlueDown,
                    minU,
                    maxV,
                    packedLightDown,
                    0.0F,
                    -1.0F,
                    0.0F);
                this.vertex(vertexConsumer,
                    x,
                    y + bottomYOffset,
                    z,
                    shadedRedDown,
                    shadedGreenDown,
                    shadedBlueDown,
                    minU,
                    minV,
                    packedLightDown,
                    0.0F,
                    -1.0F,
                    0.0F);
                this.vertex(vertexConsumer,
                    x + 1.0F,
                    y + bottomYOffset,
                    z,
                    shadedRedDown,
                    shadedGreenDown,
                    shadedBlueDown,
                    maxU,
                    minV,
                    packedLightDown,
                    0.0F,
                    -1.0F,
                    0.0F);
                this.vertex(vertexConsumer,
                    x + 1.0F,
                    y + bottomYOffset,
                    z + 1.0F,
                    shadedRedDown,
                    shadedGreenDown,
                    shadedBlueDown,
                    maxU,
                    maxV,
                    packedLightDown,
                    0.0F,
                    -1.0F,
                    0.0F);
            }

            int packedLightCenter = this.getLightColor(world, pos);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                float yStart, yEnd, xStart, xEnd, zStart, zEnd;
                boolean shouldRenderSide;

                switch (direction) {
                    case NORTH:
                        yStart = heightNW;
                        yEnd = heightNE;
                        xStart = x;
                        xEnd = x + 1.0F;
                        zStart = z + 0.001F;
                        zEnd = z + 0.001F;
                        shouldRenderSide = renderNorth;
                        break;
                    case SOUTH:
                        yStart = heightSE;
                        yEnd = heightSW;
                        xStart = x + 1.0F;
                        xEnd = x;
                        zStart = z + 1.0F - 0.001F;
                        zEnd = z + 1.0F - 0.001F;
                        shouldRenderSide = renderSouth;
                        break;
                    case WEST:
                        yStart = heightSW;
                        yEnd = heightNW;
                        xStart = x + 0.001F;
                        xEnd = x + 0.001F;
                        zStart = z + 1.0F;
                        zEnd = z;
                        shouldRenderSide = renderWest;
                        break;
                    default:
                        yStart = heightNE;
                        yEnd = heightSE;
                        xStart = x + 1.0F - 0.001F;
                        xEnd = x + 1.0F - 0.001F;
                        zStart = z;
                        zEnd = z + 1.0F;
                        shouldRenderSide = renderEast;
                }

                if (shouldRenderSide && !isFaceOccludedByNeighbor(world, pos, direction,
                    Math.max(yStart, yEnd), world.getBlockState(pos.relative(direction)))) {
                    BlockPos sidePos = pos.relative(direction);
                    TextureAtlasSprite sideSprite = fluidSprites[1];
                    if (!isLava) {
                        Block
                            sideBlock =
                            world.getBlockState(sidePos)
                                .getBlock();
                        if (sideBlock instanceof HalfTransparentBlock
                            || sideBlock instanceof LeavesBlock) {
                            sideSprite = this.waterOverlay;
                        }
                    }

                    float uStart = sideSprite.getU(0.0F);
                    float uCenter = sideSprite.getU(0.5F);
                    float vStart = sideSprite.getV((1.0F - yStart) * 0.5F);
                    float vEnd = sideSprite.getV((1.0F - yEnd) * 0.5F);
                    float vCenter = sideSprite.getV(0.5F);

                    float sideDimming =
                        direction.getAxis() == Direction.Axis.Z ? lightNorth : lightWest;
                    float sideRed = lightUp * sideDimming * red;
                    float sideGreen = lightUp * sideDimming * green;
                    float sideBlue = lightUp * sideDimming * blue;

                    float dirX = (float) direction.getStepX();
                    float dirY = (float) direction.getStepY();
                    float dirZ = (float) direction.getStepZ();

                    this.vertex(vertexConsumer,
                        xStart,
                        y + yStart,
                        zStart,
                        sideRed,
                        sideGreen,
                        sideBlue,
                        uStart,
                        vStart,
                        packedLightCenter,
                        dirX,
                        dirY,
                        dirZ);
                    this.vertex(vertexConsumer,
                        xEnd,
                        y + yEnd,
                        zEnd,
                        sideRed,
                        sideGreen,
                        sideBlue,
                        uCenter,
                        vEnd,
                        packedLightCenter,
                        dirX,
                        dirY,
                        dirZ);
                    this.vertex(vertexConsumer,
                        xEnd,
                        y + bottomYOffset,
                        zEnd,
                        sideRed,
                        sideGreen,
                        sideBlue,
                        uCenter,
                        vCenter,
                        packedLightCenter,
                        dirX,
                        dirY,
                        dirZ);
                    this.vertex(vertexConsumer,
                        xStart,
                        y + bottomYOffset,
                        zStart,
                        sideRed,
                        sideGreen,
                        sideBlue,
                        uStart,
                        vCenter,
                        packedLightCenter,
                        dirX,
                        dirY,
                        dirZ);

                    if (sideSprite != this.waterOverlay) {
                        this.vertex(vertexConsumer,
                            xStart,
                            y + bottomYOffset,
                            zStart,
                            sideRed,
                            sideGreen,
                            sideBlue,
                            uStart,
                            vCenter,
                            packedLightCenter,
                            dirX,
                            dirY,
                            dirZ);
                        this.vertex(vertexConsumer,
                            xEnd,
                            y + bottomYOffset,
                            zEnd,
                            sideRed,
                            sideGreen,
                            sideBlue,
                            uCenter,
                            vCenter,
                            packedLightCenter,
                            dirX,
                            dirY,
                            dirZ);
                        this.vertex(vertexConsumer,
                            xEnd,
                            y + yEnd,
                            zEnd,
                            sideRed,
                            sideGreen,
                            sideBlue,
                            uCenter,
                            vEnd,
                            packedLightCenter,
                            dirX,
                            dirY,
                            dirZ);
                        this.vertex(vertexConsumer,
                            xStart,
                            y + yStart,
                            zStart,
                            sideRed,
                            sideGreen,
                            sideBlue,
                            uStart,
                            vStart,
                            packedLightCenter,
                            dirX,
                            dirY,
                            dirZ);
                    }
                }
            }
        }

        ci.cancel();
    }
}
