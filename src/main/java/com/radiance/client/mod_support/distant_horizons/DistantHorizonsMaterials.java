package com.radiance.client.mod_support.distant_horizons;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_neoforge;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper_neoforge;
import net.minecraft.world.level.block.state.BlockState;

public class DistantHorizonsMaterials {
    public static int encode(int material, int tile) {
        return 0x80000000 | ((material & 15) << 16) | (tile & 65535);
    }

    public static int face(int material, short set, EDhDirection direction) {
        short[] faces = BlockTextureRegistry.INSTANCE.getFaceTileIds(Short.toUnsignedInt(set));
        return encode(material, faces == null ? 0 : Short.toUnsignedInt(faces[direction.faceIndex]));
    }

    public static int emission(int material) {
        return material == EDhApiBlockMaterial.LAVA.index
            || material == EDhApiBlockMaterial.ILLUMINATED.index ? 0x3c000000 : 0;
    }

    public static int[] sides(BlockState state) {
        var level = MinecraftClientWrapper_neoforge.INSTANCE.getWrappedClientLevel();
        if (level == null) { return new int[4]; }
        var block = BlockStateWrapper_neoforge.fromBlockState(state, level);
        short set = BlockTextureRegistry.INSTANCE.getOrRegisterBlockStateSetId(block);
        int material = block.getMaterialId();
        return new int[]{face(material, set, EDhDirection.WEST), face(material, set, EDhDirection.EAST),
            face(material, set, EDhDirection.NORTH), face(material, set, EDhDirection.SOUTH)};
    }
}
