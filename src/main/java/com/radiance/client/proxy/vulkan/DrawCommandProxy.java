package com.radiance.client.proxy.vulkan;

import com.radiance.client.constant.VulkanConstants;
import org.lwjgl.opengl.GL11;

public class DrawCommandProxy {

    public static class Overlay {

        // region <vulkan>
        private static native void vkCmdClearEntireColorAttachmentNative();

        public static void vkCmdClearEntireColorAttachment() {
            vkCmdClearEntireColorAttachmentNative();
        }

        private static native void vkCmdClearEntireDepthStencilAttachmentNative(int mask);

        public static void vkCmdClearEntireDepthStencilAttachment(int mask) {
            vkCmdClearEntireDepthStencilAttachmentNative(mask);
        }
        // endregion

        // region <openGL>
        public static void glClear(int mask) {
            if ((mask & GL11.GL_COLOR_BUFFER_BIT) > 0) {
                vkCmdClearEntireColorAttachment();
            }

            int vkMask = 0;
            if ((mask & GL11.GL_DEPTH_BUFFER_BIT) > 0) {
                vkMask |= VulkanConstants.VkImageAspectFlagBits.ofGL(GL11.GL_DEPTH_BUFFER_BIT);
            }
            if ((mask & GL11.GL_STENCIL_BUFFER_BIT) > 0) {
                vkMask |= VulkanConstants.VkImageAspectFlagBits.ofGL(GL11.GL_STENCIL_BUFFER_BIT);
            }
            if (vkMask > 0) {
                vkCmdClearEntireDepthStencilAttachment(vkMask);
            }
        }
        // endregion
    }
}
