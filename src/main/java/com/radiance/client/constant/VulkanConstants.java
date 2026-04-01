package com.radiance.client.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL33;

public class VulkanConstants {

    public enum VkFormat {
        VK_FORMAT_R8_UNORM(9, "R8_UNORM"),
        VK_FORMAT_R8_SRGB(15, "R8_SRGB"),
        VK_FORMAT_R8G8_UNORM(16, "R8G8_UNORM"),
        VK_FORMAT_R8G8_SRGB(22, "R8G8_SRGB"),
        VK_FORMAT_R8G8B8_UNORM(23, "R8G8B8_UNORM"),
        VK_FORMAT_R8G8B8_SRGB(29, "R8G8B8_SRGB"),
        VK_FORMAT_R8G8B8A8_UNORM(37, "R8G8B8A8_UNORM"),
        VK_FORMAT_R8G8B8A8_SRGB(43, "R8G8B8A8_SRGB"),
        VK_FORMAT_R16_SFLOAT(76, "R16_SFLOAT"),
        VK_FORMAT_R16G16_SFLOAT(83, "R16G16_SFLOAT"),
        VK_FORMAT_R16G16B16_SFLOAT(90, "R16G16B16_SFLOAT"),
        VK_FORMAT_R16G16B16A16_SFLOAT(97, "R16G16B16A16_SFLOAT");

        private static final Map<String, Integer>
            BY_NAME =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkFormat::getName, VkFormat::getValue)));
        private final int value;
        private final String name;

        VkFormat(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public static int getVkFormatByName(String name) {
            if (BY_NAME.containsKey(name)) {
                return BY_NAME.get(name);
            } else {
                throw new IllegalStateException("Unsupported format: " + name);
            }
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        public VkFormat toUnorm() {
            return switch (this) {
                case VK_FORMAT_R8_SRGB -> VK_FORMAT_R8_UNORM;
                case VK_FORMAT_R8G8_SRGB -> VK_FORMAT_R8G8_UNORM;
                case VK_FORMAT_R8G8B8_SRGB -> VK_FORMAT_R8G8B8_UNORM;
                case VK_FORMAT_R8G8B8A8_SRGB -> VK_FORMAT_R8G8B8A8_UNORM;
                default -> this;
            };
        }

        public NativeImage.InternalFormat getNativeImageInternalFormat() {
            return switch (this) {
                case VK_FORMAT_R8_UNORM, VK_FORMAT_R8_SRGB -> NativeImage.InternalFormat.RED;
                case VK_FORMAT_R8G8_UNORM, VK_FORMAT_R8G8_SRGB -> NativeImage.InternalFormat.RG;
                case VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8_SRGB -> NativeImage.InternalFormat.RGB;
                case VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_SRGB -> NativeImage.InternalFormat.RGBA;
                default -> throw new IllegalStateException("Unexpected value: " + this.value);
            };
        }

        public NativeImage.Format getNativeImageFormat() {
            return switch (this) {
                case VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8_SRGB -> NativeImage.Format.RGB;
                case VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_SRGB -> NativeImage.Format.RGBA;
                default -> throw new IllegalStateException("Unexpected value: " + this.value);
            };
        }
    }

    public enum VkFilter {
        VK_FILTER_NEAREST(0),
        VK_FILTER_LINEAR(1);

        private final int value;

        VkFilter(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VkSamplerMipmapMode {
        VK_SAMPLER_MIPMAP_MODE_NEAREST(0),
        VK_SAMPLER_MIPMAP_MODE_LINEAR(1);

        private final int value;

        VkSamplerMipmapMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VkSamplerAddressMode {
        VK_SAMPLER_ADDRESS_MODE_REPEAT(0),
        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE(2);

        private final int value;

        VkSamplerAddressMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VkBlendFactor {
        VK_BLEND_FACTOR_ZERO(0, GL11.GL_ZERO),
        VK_BLEND_FACTOR_ONE(1, GL11.GL_ONE),
        VK_BLEND_FACTOR_SRC_COLOR(2, GL11.GL_SRC_COLOR),
        VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR(3, GL11.GL_ONE_MINUS_SRC_COLOR),
        VK_BLEND_FACTOR_DST_COLOR(4, GL11.GL_DST_COLOR),
        VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR(5, GL11.GL_ONE_MINUS_DST_COLOR),
        VK_BLEND_FACTOR_SRC_ALPHA(6, GL11.GL_SRC_ALPHA),
        VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA(7, GL11.GL_ONE_MINUS_SRC_ALPHA),
        VK_BLEND_FACTOR_DST_ALPHA(8, GL11.GL_DST_ALPHA),
        VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA(9, GL11.GL_ONE_MINUS_DST_ALPHA),
        VK_BLEND_FACTOR_CONSTANT_COLOR(10, GL14.GL_CONSTANT_COLOR),
        VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR(11, GL14.GL_ONE_MINUS_CONSTANT_COLOR),
        VK_BLEND_FACTOR_CONSTANT_ALPHA(12, GL14.GL_CONSTANT_ALPHA),
        VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA(13, GL14.GL_ONE_MINUS_CONSTANT_ALPHA),
        VK_BLEND_FACTOR_SRC_ALPHA_SATURATE(14, GL11.GL_SRC_ALPHA_SATURATE),
        VK_BLEND_FACTOR_SRC1_COLOR(15, GL33.GL_SRC1_COLOR),
        VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR(16, GL33.GL_ONE_MINUS_SRC1_COLOR),
        VK_BLEND_FACTOR_SRC1_ALPHA(17, GL33.GL_SRC1_ALPHA),
        VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA(18, GL33.GL_ONE_MINUS_SRC1_ALPHA);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkBlendFactor::getGlValue, VkBlendFactor::getValue)));
        private final int value;
        private final int glValue;

        VkBlendFactor(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkBlendOp {
        VK_BLEND_OP_ADD(0, GL14.GL_FUNC_ADD),
        VK_BLEND_OP_SUBTRACT(1, GL14.GL_FUNC_SUBTRACT),
        VK_BLEND_OP_REVERSE_SUBTRACT(2, GL14.GL_FUNC_REVERSE_SUBTRACT),
        VK_BLEND_OP_MIN(3, GL14.GL_MIN),
        VK_BLEND_OP_MAX(4, GL14.GL_MAX);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkBlendOp::getGlValue, VkBlendOp::getValue)));
        private final int value;
        private final int glValue;

        VkBlendOp(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkColorComponentFlagBits {
        VK_COLOR_COMPONENT_R_BIT(0x00000001),
        VK_COLOR_COMPONENT_G_BIT(0x00000002),
        VK_COLOR_COMPONENT_B_BIT(0x00000004),
        VK_COLOR_COMPONENT_A_BIT(0x00000008);

        private final int value;

        VkColorComponentFlagBits(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VkCompareOp {
        VK_COMPARE_OP_NEVER(0, GL11.GL_NEVER),
        VK_COMPARE_OP_LESS(1, GL11.GL_LESS),
        VK_COMPARE_OP_EQUAL(2, GL11.GL_EQUAL),
        VK_COMPARE_OP_LESS_OR_EQUAL(3, GL11.GL_LEQUAL),
        VK_COMPARE_OP_GREATER(4, GL11.GL_GREATER),
        VK_COMPARE_OP_NOT_EQUAL(5, GL11.GL_NOTEQUAL),
        VK_COMPARE_OP_GREATER_OR_EQUAL(6, GL11.GL_GEQUAL),
        VK_COMPARE_OP_ALWAYS(7, GL11.GL_ALWAYS);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkCompareOp::getGlValue, VkCompareOp::getValue)));
        private final int value;
        private final int glValue;

        VkCompareOp(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkStencilOp {
        VK_STENCIL_OP_KEEP(0, GL11.GL_KEEP),
        VK_STENCIL_OP_ZERO(1, GL11.GL_ZERO),
        VK_STENCIL_OP_REPLACE(2, GL11.GL_REPLACE),
        VK_STENCIL_OP_INCREMENT_AND_CLAMP(3, GL11.GL_INCR),
        VK_STENCIL_OP_DECREMENT_AND_CLAMP(4, GL11.GL_DECR),
        VK_STENCIL_OP_INVERT(5, GL11.GL_INVERT),
        VK_STENCIL_OP_INCREMENT_AND_WRAP(6, GL14.GL_INCR_WRAP),
        VK_STENCIL_OP_DECREMENT_AND_WRAP(7, GL14.GL_DECR_WRAP);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkStencilOp::getGlValue, VkStencilOp::getValue)));
        private final int value;
        private final int glValue;

        VkStencilOp(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkLogicOp {
        VK_LOGIC_OP_CLEAR(0, GL11.GL_CLEAR),
        VK_LOGIC_OP_AND(1, GL11.GL_AND),
        VK_LOGIC_OP_AND_REVERSE(2, GL11.GL_AND_REVERSE),
        VK_LOGIC_OP_COPY(3, GL11.GL_COPY),
        VK_LOGIC_OP_AND_INVERTED(4, GL11.GL_AND_INVERTED),
        VK_LOGIC_OP_NO_OP(5, GL11.GL_NOOP),
        VK_LOGIC_OP_XOR(6, GL11.GL_XOR),
        VK_LOGIC_OP_OR(7, GL11.GL_OR),
        VK_LOGIC_OP_NOR(8, GL11.GL_NOR),
        VK_LOGIC_OP_EQUIVALENT(9, GL11.GL_EQUIV),
        VK_LOGIC_OP_INVERT(10, GL11.GL_INVERT),
        VK_LOGIC_OP_OR_REVERSE(11, GL11.GL_OR_REVERSE),
        VK_LOGIC_OP_COPY_INVERTED(12, GL11.GL_COPY_INVERTED),
        VK_LOGIC_OP_OR_INVERTED(13, GL11.GL_OR_INVERTED),
        VK_LOGIC_OP_NAND(14, GL11.GL_NAND),
        VK_LOGIC_OP_SET(15, GL11.GL_SET);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkLogicOp::getGlValue, VkLogicOp::getValue)));
        private final int value;
        private final int glValue;

        VkLogicOp(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkCullMode {
        VK_CULL_MODE_NONE(0, -1),
        VK_CULL_MODE_FRONT_BIT(0x00000001, GL11.GL_FRONT),
        VK_CULL_MODE_BACK_BIT(0x00000002, GL11.GL_BACK),
        VK_CULL_MODE_FRONT_AND_BACK(0x00000003, GL11.GL_FRONT_AND_BACK);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkCullMode::getGlValue, VkCullMode::getValue)));
        private final int value;
        private final int glValue;

        VkCullMode(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkPolygonMode {
        VK_POLYGON_MODE_FILL(0, GL11.GL_FILL),
        VK_POLYGON_MODE_LINE(1, GL11.GL_LINE),
        VK_POLYGON_MODE_POINT(2, GL11.GL_POINT);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkPolygonMode::getGlValue, VkPolygonMode::getValue)));
        private final int value;
        private final int glValue;

        VkPolygonMode(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkFrontFace {
        VK_FRONT_FACE_COUNTER_CLOCKWISE(0, GL11.GL_CCW),
        VK_FRONT_FACE_CLOCKWISE(1, GL11.GL_CW);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkFrontFace::getGlValue, VkFrontFace::getValue)));
        private final int value;
        private final int glValue;

        VkFrontFace(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkImageAspectFlagBits {
        VK_IMAGE_ASPECT_COLOR_BIT(0x00000001, GL11.GL_COLOR_BUFFER_BIT),
        VK_IMAGE_ASPECT_DEPTH_BIT(0x00000002, GL11.GL_DEPTH_BUFFER_BIT),
        VK_IMAGE_ASPECT_STENCIL_BIT(0x00000004, GL11.GL_STENCIL_BUFFER_BIT);

        private static final Map<Integer, Integer>
            BY_GL_VALUE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(VkImageAspectFlagBits::getGlValue,
                    VkImageAspectFlagBits::getValue)));
        private final int value;
        private final int glValue;

        VkImageAspectFlagBits(int value, int glValue) {
            this.value = value;
            this.glValue = glValue;
        }

        public static int ofGL(int glValue) {
            return BY_GL_VALUE.get(glValue);
        }

        public int getValue() {
            return value;
        }

        public int getGlValue() {
            return glValue;
        }
    }

    public enum VkBufferUsageFlagBits {
        VK_BUFFER_USAGE_TRANSFER_SRC_BIT(0x00000001),
        VK_BUFFER_USAGE_TRANSFER_DST_BIT(0x00000002),
        VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT(0x00000004),
        VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT(0x00000008),
        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT(0x00000010),
        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT(0x00000020),
        VK_BUFFER_USAGE_INDEX_BUFFER_BIT(0x00000040),
        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT(0x00000080),
        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT(0x00000100),
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT(0x00020000);

        private final int value;

        VkBufferUsageFlagBits(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
