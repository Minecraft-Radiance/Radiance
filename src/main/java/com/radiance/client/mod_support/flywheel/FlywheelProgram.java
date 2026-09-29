package com.radiance.client.mod_support.flywheel;

import com.radiance.client.proxy.world.EntityProxy;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.compile.FlwPrograms;
import dev.engine_room.flywheel.backend.compile.component.InstanceStructComponent;
import dev.engine_room.flywheel.backend.compile.component.SsboInstanceComponent;
import dev.engine_room.flywheel.backend.engine.MaterialEncoder;
import dev.engine_room.flywheel.backend.glsl.SourceComponent;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class FlywheelProgram implements AutoCloseable {
    final int handle;

    FlywheelProgram(InstanceType<?> type, Material material, boolean embedded) {
        Set<SourceComponent> sources = new LinkedHashSet<>();
        include(sources, resource("flywheel:internal/material.glsl"));
        include(sources, resource("flywheel:internal/uniforms/uniforms.glsl"));
        StringBuilder source = new StringBuilder("#version 450\n#define FLW_VERTEX_SHADER\nlayout(local_size_x=1) in;\n");
        if (embedded) source.append("#define FLW_EMBEDDED\n");
        for (SourceComponent component : sources) {
            String text = component.source();
            String[] names = {"Frame", "Fog", "Options", "Player", "Level"};
            for (int i = 0; i < names.length; i++) {
                text = text.replaceAll("layout\\s*\\(std140\\)\\s*uniform\\s+_Flw" + names[i] + "Uniforms",
                    "layout(std140,binding=" + (8 + i) + ") uniform _Flw" + names[i] + "Uniforms");
            }
            source.append(text).append('\n');
        }
        source.append("""
            vec4 flw_vertexPos;
            vec4 flw_vertexColor;
            vec2 flw_vertexTexCoord;
            ivec2 flw_vertexOverlay;
            vec2 flw_vertexLight;
            vec3 flw_vertexNormal;
            uint flw_vertexId;
            FlwMaterial flw_material;
            layout(std430,binding=16) readonly buffer RadianceVertex { float vertex[16]; };
            layout(std430,binding=17) writeonly buffer RadianceOutput { float result[16]; };
            layout(std430,binding=18) readonly buffer RadianceVertexIndex { uint vertexIndex; };
            """);
        Set<SourceComponent> emitted = new LinkedHashSet<>(sources);
        include(sources, new InstanceStructComponent(type));
        include(sources, new SsboInstanceComponent(type));
        include(sources, resource("flywheel:internal/packed_material.glsl"));
        include(sources, FlwPrograms.SOURCES.get(type.vertexShader()));
        include(sources, FlwPrograms.SOURCES.get(material.shaders().vertexSource()));
        for (SourceComponent component : sources) if (!emitted.contains(component)) source.append(component.source()).append('\n');
        source.append("void main() {\n_flw_unpackMaterialProperties(")
            .append(Integer.toUnsignedString(MaterialEncoder.packProperties(material))).append("u, flw_material);\n")
            .append("""
            FlwMaterial radianceOriginalMaterial = flw_material;
            flw_vertexId = vertexIndex;
            flw_vertexPos = vec4(vertex[0],vertex[1],vertex[2],1);
            flw_vertexColor = vec4(vertex[3],vertex[4],vertex[5],vertex[6]);
            flw_vertexTexCoord = vec2(vertex[7],vertex[8]);
            flw_vertexOverlay = ivec2(vertex[9],vertex[10]);
            flw_vertexLight = vec2(vertex[11],vertex[12]);
            flw_vertexNormal = vec3(vertex[13],vertex[14],vertex[15]);
            flw_instanceVertex(_flw_unpackInstance(0u));
            flw_materialVertex();
            result[0]=flw_vertexPos.x/flw_vertexPos.w;
            result[1]=flw_vertexPos.y/flw_vertexPos.w;
            result[2]=flw_vertexPos.z/flw_vertexPos.w;
            result[3]=flw_vertexColor.r; result[4]=flw_vertexColor.g;
            result[5]=flw_vertexColor.b; result[6]=flw_vertexColor.a;
            result[7]=flw_vertexTexCoord.x; result[8]=flw_vertexTexCoord.y;
            result[9]=float(flw_vertexOverlay.x); result[10]=float(flw_vertexOverlay.y);
            result[11]=flw_vertexLight.x; result[12]=flw_vertexLight.y;
            result[13]=flw_vertexNormal.x; result[14]=flw_vertexNormal.y; result[15]=flw_vertexNormal.z;
            if (flw_material != radianceOriginalMaterial) result[0] = uintBitsToFloat(0x7fc00000u);
            }
            """);
        handle = EntityProxy.registerVertexShaderNative(source.toString());
    }

    private static SourceComponent resource(String id) { return FlwPrograms.SOURCES.get(new ResourceLocation(id)); }

    private static void include(Set<SourceComponent> sources, SourceComponent component) {
        if (sources.contains(component)) return;
        for (SourceComponent child : component.included()) include(sources, child);
        sources.add(component);
    }

    @Override
    public void close() { EntityProxy.releaseVertexShaderNative(handle); }
}
