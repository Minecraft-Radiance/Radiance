package com.radiance.client.vista;

import com.radiance.client.proxy.world.VistaProxy;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.util.shape.VoxelShapes;

public class ChunkTerrainBuilder {
    private VistaTerrain.Builder builder;
    private final Map<BlockState, Material> materials = new HashMap<>();
    private final BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
    private final Random random = Random.create(42);

    public void add(BlockRenderView world, BlockPos pos, BlockState state) {
        int x = pos.getX() & 15;
        int z = pos.getZ() & 15;
        if (x != 0 && x != 15 && z != 0 && z != 15) { return; }
        if (builder == null) {
            builder = new VistaTerrain.Builder(world.getBrightness(Direction.WEST, true),
                world.getBrightness(Direction.EAST, true), world.getBrightness(Direction.NORTH, true),
                world.getBrightness(Direction.SOUTH, true));
        }
        if (state.getRenderType() == BlockRenderType.MODEL) {
            List<Box> boxes = state.getCullingShape(world, pos).getBoundingBoxes();
            if (!boxes.isEmpty()) {
                Material material = materials.computeIfAbsent(state, this::getMaterial);
                int tint = material.tintIndex < 0 ? 0xffffff
                    : MinecraftClient.getInstance().getBlockColors().getColor(state, world, pos, material.tintIndex);
                int color = tint(material.color, tint);
                for (Box box : boxes) {
                    if ((x != 0 || box.minX > 0) && (x != 15 || box.maxX < 1)
                        && (z != 0 || box.minZ > 0) && (z != 15 || box.maxZ < 1)) { continue; }
                    builder.add(pos.getX(), pos.getZ(), pos.getY() + (float) box.minY,
                        pos.getY() + (float) box.maxY, (float) box.minX, (float) box.maxX,
                        (float) box.minZ, (float) box.maxZ, color, 0,
                        material.sides[0], material.sides[1], material.sides[2], material.sides[3]);
                }
            }
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            boolean water = fluid.isIn(FluidTags.WATER);
            Fluid type = fluid.getFluid();
            float center = fluidHeight(world, pos, type);
            float minimum = center;
            float height = center;
            if (center < 1) {
                float north = fluidHeight(world, pos.north(), type);
                float south = fluidHeight(world, pos.south(), type);
                float west = fluidHeight(world, pos.west(), type);
                float east = fluidHeight(world, pos.east(), type);
                float nw = cornerHeight(world, pos.north().west(), type, center, north, west);
                float ne = cornerHeight(world, pos.north().east(), type, center, north, east);
                float sw = cornerHeight(world, pos.south().west(), type, center, south, west);
                float se = cornerHeight(world, pos.south().east(), type, center, south, east);
                minimum = Math.min(Math.min(nw, ne), Math.min(sw, se));
                height = Math.max(Math.max(nw, ne), Math.max(sw, se));
            }
            BlockState above = world.getBlockState(pos.up());
            if (!type.matchesType(above.getFluidState().getFluid())
                && !sideCovered(world, pos.up(), above, Direction.UP, minimum)) {
                height -= 0.001f;
            }
            BlockState below = world.getBlockState(pos.down());
            boolean renderBottom = FluidRenderer.shouldRenderSide(world, pos, fluid, state,
                Direction.DOWN, below.getFluidState())
                && !sideCovered(world, pos.down(), below, Direction.DOWN, 0.8888889f);
            Sprite sprite = (water ? ModelLoader.WATER_FLOW : ModelLoader.LAVA_FLOW).getSprite();
            int color = ((ISpriteContentsExt) sprite.getContents()).radiance$getAverageColor();
            if (water) { color = tint(color, BiomeColors.getWaterColor(world, pos)); }
            int[] sides = materials.computeIfAbsent(fluid.getBlockState(), this::getMaterial).sides;
            builder.add(pos.getX(), pos.getZ(), pos.getY() + (renderBottom ? 0.001f : 0),
                pos.getY() + height,
                0, 1, 0, 1, color, water ? VistaProxy.MATERIAL_WATER : 0,
                sides[0], sides[1], sides[2], sides[3]);
        }
    }

    public VistaTerrain build() { return builder == null ? VistaTerrain.EMPTY : builder.build(); }

    private Material getMaterial(BlockState state) {
        BakedModel model = blockRenderManager.getModel(state);
        random.setSeed(42);
        List<BakedQuad> quads = model.getQuads(state, Direction.NORTH, random);
        if (quads.isEmpty()) {
            random.setSeed(42);
            quads = model.getQuads(state, Direction.UP, random);
        }
        BakedQuad quad = quads.isEmpty() ? null : quads.get(0);
        Sprite sprite = quad == null ? model.getParticleSprite() : quad.getSprite();
        int color = ((ISpriteContentsExt) sprite.getContents()).radiance$getAverageColor();
        if (RenderLayers.getBlockLayer(state) == RenderLayer.getSolid()) { color |= 0xff000000; }
        return new Material(color, quad != null && quad.hasColor() ? quad.getColorIndex() : -1,
            Vista.sideMaterials(state));
    }

    private static int tint(int color, int tint) {
        int red = (color & 255) * ((tint >>> 16) & 255) / 255;
        int green = ((color >>> 8) & 255) * ((tint >>> 8) & 255) / 255;
        int blue = ((color >>> 16) & 255) * (tint & 255) / 255;
        return (color & 0xff000000) | red | (green << 8) | (blue << 16);
    }

    private static float fluidHeight(BlockRenderView world, BlockPos pos, Fluid fluid) {
        BlockState state = world.getBlockState(pos);
        FluidState sample = state.getFluidState();
        if (fluid.matchesType(sample.getFluid())) {
            return fluid.matchesType(world.getFluidState(pos.up()).getFluid()) ? 1 : sample.getHeight();
        }
        return state.isSolid() ? -1 : 0;
    }

    private static float cornerHeight(BlockRenderView world, BlockPos diagonalPos, Fluid fluid,
        float center, float northSouth, float eastWest) {
        if (eastWest >= 1 || northSouth >= 1) { return 1; }
        float total = 0;
        float weight = 0;
        if (eastWest > 0 || northSouth > 0) {
            float diagonal = fluidHeight(world, diagonalPos, fluid);
            if (diagonal >= 1) { return 1; }
            if (diagonal >= 0) {
                float w = diagonal >= 0.8f ? 10 : 1;
                total += diagonal * w;
                weight += w;
            }
        }
        float centerWeight = center >= 0.8f ? 10 : 1;
        total += center * centerWeight;
        weight += centerWeight;
        if (eastWest >= 0) {
            float w = eastWest >= 0.8f ? 10 : 1;
            total += eastWest * w;
            weight += w;
        }
        if (northSouth >= 0) {
            float w = northSouth >= 0.8f ? 10 : 1;
            total += northSouth * w;
            weight += w;
        }
        return total / weight;
    }

    private static boolean sideCovered(BlockRenderView world, BlockPos pos, BlockState state,
        Direction direction, float height) {
        return state.isOpaque() && VoxelShapes.isSideCovered(
            VoxelShapes.cuboid(0, 0, 0, 1, height, 1), state.getCullingShape(world, pos), direction);
    }

    private record Material(int color, int tintIndex, int[] sides) {}
}
