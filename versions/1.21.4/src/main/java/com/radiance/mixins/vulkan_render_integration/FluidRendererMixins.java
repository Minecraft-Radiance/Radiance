package com.radiance.mixins.vulkan_render_integration;

import static net.minecraft.client.render.block.FluidRenderer.shouldRenderSide;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.TranslucentBlock;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixins {

    @Final
    @Shadow
    private Sprite[] lavaSprites;

    @Final
    @Shadow
    private Sprite[] waterSprites;

    @Shadow
    private Sprite waterOverlaySprite;

    @Shadow
    private static boolean isSameFluid(FluidState a, FluidState b) {
        return false;
    }

    @Shadow
    private static boolean isSideCovered(Direction direction, float f, BlockState blockState) {
        VoxelShape voxelShape = blockState.getCullingFace(direction.getOpposite());
        if (voxelShape == VoxelShapes.empty()) {
            return false;
        } else if (voxelShape == VoxelShapes.fullCube()) {
            boolean bl = f == 1.0F;
            return direction != Direction.UP || bl;
        } else {
            VoxelShape voxelShape2 = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, f, 1.0);
            return VoxelShapes.isSideCovered(voxelShape2, voxelShape, direction);
        }
    }

    @Shadow
    private static boolean method_3344(Direction direction, float f, BlockState blockState) {
        return false;
    }

    @Shadow
    private static boolean isOppositeSideCovered(BlockState blockState, Direction direction) {
        return false;
    }

    @Shadow
    protected abstract float calculateFluidHeight(BlockRenderView world,
        Fluid fluid,
        float originHeight,
        float northSouthHeight,
        float eastWestHeight,
        BlockPos pos);

    @Shadow
    protected abstract void addHeight(float[] weightedAverageHeight, float height);

    @Shadow
    protected abstract float getFluidHeight(BlockRenderView world, Fluid fluid, BlockPos pos);

    @Shadow
    protected abstract float getFluidHeight(BlockRenderView world, Fluid fluid, BlockPos pos,
        BlockState blockState, FluidState fluidState);

    @Shadow
    protected abstract int getLight(BlockRenderView world, BlockPos pos);

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
        vertexConsumer.vertex(x, y, z)
            .color(red, green, blue, 1.0F)
            .texture(u, v)
            .light(light)
            .normal(nx, ny, nz);
    }

    @Inject(method =
        "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;" +
            "Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void addNormalToVertex(BlockRenderView world,
        BlockPos pos,
        VertexConsumer vertexConsumer,
        BlockState blockState,
        FluidState fluidState,
        CallbackInfo ci) {
        boolean isLava = fluidState.isIn(FluidTags.LAVA);
        Sprite[] fluidSprites = isLava ? this.lavaSprites : this.waterSprites;
        int tintColor = isLava ? 16777215 : BiomeColors.getWaterColor(world, pos);
        float red = (tintColor >> 16 & 0xFF) / 255.0F;
        float green = (tintColor >> 8 & 0xFF) / 255.0F;
        float blue = (tintColor & 0xFF) / 255.0F;

        BlockState stateDown = world.getBlockState(pos.offset(Direction.DOWN));
        FluidState fluidDown = stateDown.getFluidState();
        BlockState stateUp = world.getBlockState(pos.offset(Direction.UP));
        FluidState fluidUp = stateUp.getFluidState();
        BlockState stateNorth = world.getBlockState(pos.offset(Direction.NORTH));
        FluidState fluidNorth = stateNorth.getFluidState();
        BlockState stateSouth = world.getBlockState(pos.offset(Direction.SOUTH));
        FluidState fluidSouth = stateSouth.getFluidState();
        BlockState stateWest = world.getBlockState(pos.offset(Direction.WEST));
        FluidState fluidWest = stateWest.getFluidState();
        BlockState stateEast = world.getBlockState(pos.offset(Direction.EAST));
        FluidState fluidEast = stateEast.getFluidState();

        boolean renderTop = !isSameFluid(fluidState, fluidUp);
        boolean
            renderBottom =
            shouldRenderSide(fluidState, blockState, Direction.DOWN, fluidDown) && !method_3344(
                Direction.DOWN, 0.8888889F, stateDown);
        boolean renderNorth = shouldRenderSide(fluidState, blockState, Direction.NORTH, fluidNorth);
        boolean renderSouth = shouldRenderSide(fluidState, blockState, Direction.SOUTH, fluidSouth);
        boolean renderWest = shouldRenderSide(fluidState, blockState, Direction.WEST, fluidWest);
        boolean renderEast = shouldRenderSide(fluidState, blockState, Direction.EAST, fluidEast);

        if (renderTop || renderBottom || renderEast || renderWest || renderNorth || renderSouth) {
            float lightDown = world.getBrightness(Direction.DOWN, true);
            float lightUp = world.getBrightness(Direction.UP, true);
            float lightNorth = world.getBrightness(Direction.NORTH, true);
            float lightWest = world.getBrightness(Direction.WEST, true); // 用于侧面阴影计算

            Fluid fluid = fluidState.getFluid();
            float currentHeight = this.getFluidHeight(world, fluid, pos, blockState, fluidState);
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
                float hNorth = this.getFluidHeight(world, fluid, pos.north(), stateNorth,
                    fluidNorth);
                float hSouth = this.getFluidHeight(world, fluid, pos.south(), stateSouth,
                    fluidSouth);
                float hEast = this.getFluidHeight(world, fluid, pos.east(), stateEast, fluidEast);
                float hWest = this.getFluidHeight(world, fluid, pos.west(), stateWest, fluidWest);

                heightNE =
                    this.calculateFluidHeight(world,
                        fluid,
                        currentHeight,
                        hNorth,
                        hEast,
                        pos.offset(Direction.NORTH)
                            .offset(Direction.EAST));
                heightNW =
                    this.calculateFluidHeight(world,
                        fluid,
                        currentHeight,
                        hNorth,
                        hWest,
                        pos.offset(Direction.NORTH)
                            .offset(Direction.WEST));
                heightSE =
                    this.calculateFluidHeight(world,
                        fluid,
                        currentHeight,
                        hSouth,
                        hEast,
                        pos.offset(Direction.SOUTH)
                            .offset(Direction.EAST));
                heightSW =
                    this.calculateFluidHeight(world,
                        fluid,
                        currentHeight,
                        hSouth,
                        hWest,
                        pos.offset(Direction.SOUTH)
                            .offset(Direction.WEST));
            }

            float x = pos.getX() & 15;
            float y = pos.getY() & 15;
            float z = pos.getZ() & 15;
            float bottomYOffset = renderBottom ? 0.001F : 0.0F;

            // ==========================================
            // 1. 渲染顶面 (Surface)
            // ==========================================
            if (renderTop && !method_3344(Direction.UP,
                Math.min(Math.min(heightNW, heightSW), Math.min(heightSE, heightNE)), stateUp)) {
                // 稍微调低一点避免 Z-Fighting
                heightNW -= 0.001F;
                heightSW -= 0.001F;
                heightSE -= 0.001F;
                heightNE -= 0.001F;

                Vec3d flowVector = fluidState.getVelocity(world, pos);
                float u1, v1, u2, v2, u3, v3, u4, v4; // 对应四个角的UV

                if (flowVector.x == 0.0 && flowVector.z == 0.0) {
                    Sprite stillSprite = fluidSprites[0];
                    u1 = stillSprite.getFrameU(0.0F);
                    v1 = stillSprite.getFrameV(0.0F);
                    u2 = u1;
                    v2 = stillSprite.getFrameV(1.0F);
                    u3 = stillSprite.getFrameU(1.0F);
                    v3 = v2;
                    u4 = u3;
                    v4 = v1;
                } else {
                    Sprite flowSprite = fluidSprites[1];
                    float angle =
                        (float) MathHelper.atan2(flowVector.z, flowVector.x) - (float) (Math.PI
                            / 2);
                    float sin = MathHelper.sin(angle) * 0.25F;
                    float cos = MathHelper.cos(angle) * 0.25F;

                    u1 = flowSprite.getFrameU(0.5F + (-cos - sin));
                    v1 = flowSprite.getFrameV(0.5F + (-cos + sin));
                    u2 = flowSprite.getFrameU(0.5F + (-cos + sin));
                    v2 = flowSprite.getFrameV(0.5F + (cos + sin));
                    u3 = flowSprite.getFrameU(0.5F + (cos + sin));
                    v3 = flowSprite.getFrameV(0.5F + (cos - sin));
                    u4 = flowSprite.getFrameU(0.5F + (cos - sin));
                    v4 = flowSprite.getFrameV(0.5F + (-cos - sin));
                }

                float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
                float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
                float animationDelta = fluidSprites[0].getAnimationFrameDelta();

                u1 = MathHelper.lerp(animationDelta, u1, uAvg);
                u2 = MathHelper.lerp(animationDelta, u2, uAvg);
                u3 = MathHelper.lerp(animationDelta, u3, uAvg);
                u4 = MathHelper.lerp(animationDelta, u4, uAvg);
                v1 = MathHelper.lerp(animationDelta, v1, vAvg);
                v2 = MathHelper.lerp(animationDelta, v2, vAvg);
                v3 = MathHelper.lerp(animationDelta, v3, vAvg);
                v4 = MathHelper.lerp(animationDelta, v4, vAvg);

                int packedLight = this.getLight(world, pos);
                float shadedRed = lightUp * red;
                float shadedGreen = lightUp * green;
                float shadedBlue = lightUp * blue;

                // --- 法线计算 (顶面) ---
                // 坐标系：NW(0,0), NE(1,0), SW(0,1), SE(1,1)
                // X轴斜率贡献: (左 - 右) => (NW - NE) + (SW - SE)
                // Z轴斜率贡献: (上 - 下) => (NW - SW) + (NE - SE)
                float normalX = (heightNW - heightNE) + (heightSW - heightSE);
                float normalZ = (heightNW - heightSW) + (heightNE - heightSE);
                float normalY = 1.0F; // 基础垂直分量

                // 归一化
                float length = MathHelper.sqrt(
                    normalX * normalX + normalY * normalY + normalZ * normalZ);
                normalX /= length;
                normalY /= length;
                normalZ /= length;

                // 绘制顶面 (四个顶点)
                // 0: NW (0, 0) -> heightNW
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
                // 1: SW (0, 1) -> heightSW
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
                // 2: SE (1, 1) -> heightSE
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
                // 3: NE (1, 0) -> heightNE
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

                if (fluidState.canFlowTo(world, pos.up())) {
                    // 绘制内顶面 (Backface)，法线取反
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

            // ==========================================
            // 2. 渲染底面 (Bottom)
            // ==========================================
            if (renderBottom) {
                float minU = fluidSprites[0].getMinU();
                float maxU = fluidSprites[0].getMaxU();
                float minV = fluidSprites[0].getMinV();
                float maxV = fluidSprites[0].getMaxV();

                int packedLightDown = this.getLight(world, pos.down());
                float shadedRedDown = lightDown * red;
                float shadedGreenDown = lightDown * green;
                float shadedBlueDown = lightDown * blue;

                // 法线向下 (0, -1, 0)
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

            int packedLightCenter = this.getLight(world, pos);

            // ==========================================
            // 3. 渲染侧面 (Sides)
            // ==========================================
            for (Direction direction : Direction.Type.HORIZONTAL) {
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
                    default: // EAST
                        yStart = heightNE;
                        yEnd = heightSE;
                        xStart = x + 1.0F - 0.001F;
                        xEnd = x + 1.0F - 0.001F;
                        zStart = z;
                        zEnd = z + 1.0F;
                        shouldRenderSide = renderEast;
                }

                if (shouldRenderSide && !method_3344(direction, Math.max(yStart, yEnd),
                    world.getBlockState(pos.offset(direction)))) {
                    BlockPos sidePos = pos.offset(direction);
                    Sprite sideSprite = fluidSprites[1];
                    if (!isLava) {
                        Block
                            sideBlock =
                            world.getBlockState(sidePos)
                                .getBlock();
                        if (sideBlock instanceof TranslucentBlock
                            || sideBlock instanceof LeavesBlock) {
                            sideSprite = this.waterOverlaySprite;
                        }
                    }

                    float uStart = sideSprite.getFrameU(0.0F);
                    float uCenter = sideSprite.getFrameU(0.5F);
                    float vStart = sideSprite.getFrameV((1.0F - yStart) * 0.5F);
                    float vEnd = sideSprite.getFrameV((1.0F - yEnd) * 0.5F);
                    float vCenter = sideSprite.getFrameV(0.5F);

                    // MC 使用 lightNorth (0.8) 或 lightWest (0.6) 模拟侧面阴影
                    float sideDimming =
                        direction.getAxis() == Direction.Axis.Z ? lightNorth : lightWest;
                    float sideRed = lightUp * sideDimming * red;
                    float sideGreen = lightUp * sideDimming * green;
                    float sideBlue = lightUp * sideDimming * blue;

                    // 侧面法线
                    float dirX = (float) direction.getOffsetX();
                    float dirY = (float) direction.getOffsetY(); // 0
                    float dirZ = (float) direction.getOffsetZ();

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

                    if (sideSprite != this.waterOverlaySprite) {
                        // 双面渲染（通常用于查看背面时），法线保持几何方向或取反均可。
                        // 这里为了保持光照一致性，通常使用与面朝向相同的法线。
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
