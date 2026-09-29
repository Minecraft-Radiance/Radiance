package com.radiance.client.opengl_ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.radiance.client.profiler.Profiler;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectFD;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreFD;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

public final class OpenGLUiBridge {

    public record DeviceIdentity(byte[] deviceUuids, byte[] driverUuid) {

        public DeviceIdentity {
            deviceUuids = deviceUuids.clone();
            driverUuid = driverUuid.clone();
        }
    }

    private static final String COMPOSITE_VERTEX_SHADER_SOURCE =
        "#version 330 core\n" +
            "out vec2 vUv;\n" +
            "void main() {\n" +
            "    vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n" +
            "    vUv = position;\n" +
            "    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);\n" +
            "}\n";

    private static final String COMPOSITE_FRAGMENT_SHADER_SOURCE =
        "#version 330 core\n" +
            "in vec2 vUv;\n" +
            "uniform sampler2D uWorldColor;\n" +
            "layout(location = 0) out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(uWorldColor, vec2(vUv.x, 1.0 - vUv.y));\n" +
            "}\n";

    private static final int[] NO_BUFFER_BARRIERS = new int[0];
    private static final long RESIZE_SETTLE_NANOS = 100_000_000L;

    private record SurfaceKey(long generation, int frameIndex) {
    }

    private record PreparedFrame(long generation, int frameIndex, int width, int height) {
    }

    private static final class SurfaceResources {

        private final long generation;
        private final int frameIndex;
        private final int width;
        private final int height;
        private final int memoryObject;
        private final int texture;
        private final int framebuffer;
        private final int vulkanReadySemaphore;
        private final int glCompleteSemaphore;
        private final int[] textureBarrier;
        private final int[] layoutBarrier;
        private final long win32MemoryHandle;
        private final long win32VulkanReadySemaphoreHandle;
        private final long win32GlCompleteSemaphoreHandle;
        private boolean closed;

        private SurfaceResources(long generation, int frameIndex, int width, int height,
            long memorySize, long memoryHandle, long vulkanReadySemaphoreHandle,
            long glCompleteSemaphoreHandle) {
            this.generation = generation;
            this.frameIndex = frameIndex;
            this.width = width;
            this.height = height;

            boolean win32Handles = Platform.get() == Platform.WINDOWS;
            this.win32MemoryHandle = win32Handles ? memoryHandle : 0L;
            this.win32VulkanReadySemaphoreHandle = win32Handles ? vulkanReadySemaphoreHandle : 0L;
            this.win32GlCompleteSemaphoreHandle = win32Handles ? glCompleteSemaphoreHandle : 0L;
            boolean importComplete = false;
            try {
                this.memoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();
                EXTMemoryObject.glMemoryObjectParameteriEXT(this.memoryObject,
                    EXTMemoryObject.GL_DEDICATED_MEMORY_OBJECT_EXT, GL11.GL_TRUE);
                if (win32Handles) {
                    EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(this.memoryObject,
                        memorySize, EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
                        memoryHandle);
                } else if (Platform.get() == Platform.LINUX) {
                    EXTMemoryObjectFD.glImportMemoryFdEXT(this.memoryObject, memorySize,
                        EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT,
                        Math.toIntExact(memoryHandle));
                } else {
                    throw new IllegalStateException(
                        "OpenGL UI external memory is unsupported on " + Platform.get());
                }

                this.texture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, EXTMemoryObject.GL_TEXTURE_TILING_EXT,
                    EXTMemoryObject.GL_OPTIMAL_TILING_EXT);
                EXTMemoryObject.glTexStorageMem2DEXT(GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8,
                    width, height, this.memoryObject, 0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                    GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                    GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                    GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                    GL12.GL_CLAMP_TO_EDGE);

                this.framebuffer = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, this.texture, 0);
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException(
                        "OpenGL UI external framebuffer is incomplete: 0x"
                            + Integer.toHexString(status));
                }

                this.vulkanReadySemaphore = EXTSemaphore.glGenSemaphoresEXT();
                this.glCompleteSemaphore = EXTSemaphore.glGenSemaphoresEXT();
                if (win32Handles) {
                    EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                        this.vulkanReadySemaphore,
                        EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
                        vulkanReadySemaphoreHandle);
                    EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                        this.glCompleteSemaphore,
                        EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
                        glCompleteSemaphoreHandle);
                } else {
                    EXTSemaphoreFD.glImportSemaphoreFdEXT(this.vulkanReadySemaphore,
                        EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT,
                        Math.toIntExact(vulkanReadySemaphoreHandle));
                    EXTSemaphoreFD.glImportSemaphoreFdEXT(this.glCompleteSemaphore,
                        EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT,
                        Math.toIntExact(glCompleteSemaphoreHandle));
                }
                importComplete = true;
            } finally {
                if (win32Handles && !importComplete) {
                    OpenGLUiProxy.releaseWin32Handles(memoryHandle,
                        vulkanReadySemaphoreHandle, glCompleteSemaphoreHandle);
                }
            }
            this.textureBarrier = new int[]{this.texture};
            this.layoutBarrier = new int[]{EXTSemaphore.GL_LAYOUT_GENERAL_EXT};
        }

        private void waitForVulkan() {
            if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.waitForVulkan");
            try {
                EXTSemaphore.glWaitSemaphoreEXT(this.vulkanReadySemaphore, NO_BUFFER_BARRIERS,
                    this.textureBarrier, this.layoutBarrier);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
        }

        private void compositeWorldToBoundFramebuffer() {
            if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.drawWorld");
            try {
                drawFullscreenTexture(this.texture);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
        }

        private void copyFramebufferToExternal(RenderTarget uiFramebuffer) {
            if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.copyFramebufferToExternal");
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, uiFramebuffer.frameBufferId);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.framebuffer);
                GL30.glBlitFramebuffer(0, 0, uiFramebuffer.width, uiFramebuffer.height,
                    0, this.height, this.width, 0, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
        }

        private void signalForVulkan() {
            if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.signalForVulkan");
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                EXTSemaphore.glSignalSemaphoreEXT(this.glCompleteSemaphore, NO_BUFFER_BARRIERS,
                    this.textureBarrier, this.layoutBarrier);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
            if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.flush");
            try {
                GL11.glFlush();
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
        }

        private void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                EXTSemaphore.glDeleteSemaphoresEXT(this.vulkanReadySemaphore);
                EXTSemaphore.glDeleteSemaphoresEXT(this.glCompleteSemaphore);
                GL30.glDeleteFramebuffers(this.framebuffer);
                GL11.glDeleteTextures(this.texture);
                EXTMemoryObject.glDeleteMemoryObjectsEXT(this.memoryObject);
            } finally {
                // AMD's Windows driver still uses the imported NT handles during semaphore waits.
                // Keep them alive until their GL objects have been destroyed.
                if (Platform.get() == Platform.WINDOWS) {
                    OpenGLUiProxy.releaseWin32Handles(this.win32MemoryHandle,
                        this.win32VulkanReadySemaphoreHandle, this.win32GlCompleteSemaphoreHandle);
                }
            }
        }
    }

    private static final Map<SurfaceKey, SurfaceResources> surfaces = new HashMap<>();
    private static PreparedFrame preparedFrame = null;
    private static SurfaceResources currentSurface = null;
    private static RenderTarget compositedFramebuffer = null;
    private static long glContextWindow = 0L;
    private static boolean glContextCapabilitiesCreated = false;
    private static boolean capabilitiesChecked = false;
    private static int compositeProgram = 0;
    private static int compositeVertexArray = 0;
    private static int compositeSamplerUniform = -1;
    private static volatile boolean framebufferResizePending = false;
    private static volatile long lastFramebufferResizeNanos = 0L;

    private OpenGLUiBridge() {
    }

    public static void adoptEarlyDisplayContext(long contextWindow) {
        if (contextWindow == 0L) {
            throw new IllegalArgumentException("Cannot adopt a null early-display window");
        }
        if (GLFW.glfwGetWindowAttrib(contextWindow, GLFW.GLFW_CLIENT_API) == GLFW.GLFW_NO_API) {
            throw new IllegalStateException(
                "OpenGL UI requires NeoForge early display to provide an OpenGL window");
        }
        if (glContextWindow != 0L && glContextWindow != contextWindow) {
            throw new IllegalStateException("OpenGL UI context is already initialized");
        }
        glContextWindow = contextWindow;
    }

    public static void initializeOpenGLContext() {
        if (glContextWindow == 0L) {
            throw new IllegalStateException(
                "NeoForge early-display OpenGL context was not available for the OpenGL UI backend");
        }
        GLFW.glfwMakeContextCurrent(glContextWindow);
        if (!glContextCapabilitiesCreated) {
            GL.createCapabilities();
            glContextCapabilitiesCreated = true;
        }
    }

    public static DeviceIdentity queryDeviceIdentity() {
        makeContextCurrent();
        checkCapabilities();

        int deviceCount = GL11.glGetInteger(EXTMemoryObject.GL_NUM_DEVICE_UUIDS_EXT);
        if (deviceCount <= 0) {
            throw new IllegalStateException("OpenGL UI context did not report a device UUID");
        }

        int uuidSize = EXTMemoryObject.GL_UUID_SIZE_EXT;
        byte[] deviceUuids = new byte[Math.multiplyExact(deviceCount, uuidSize)];
        byte[] driverUuid = new byte[uuidSize];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uuid = stack.malloc(uuidSize);
            for (int index = 0; index < deviceCount; index++) {
                uuid.clear();
                EXTMemoryObject.glGetUnsignedBytei_vEXT(
                    EXTMemoryObject.GL_DEVICE_UUID_EXT, index, uuid);
                uuid.get(0, deviceUuids, index * uuidSize, uuidSize);
            }
            uuid.clear();
            EXTMemoryObject.glGetUnsignedBytevEXT(
                EXTMemoryObject.GL_DRIVER_UUID_EXT, uuid);
            uuid.get(0, driverUuid, 0, uuidSize);
        }
        return new DeviceIdentity(deviceUuids, driverUuid);
    }

    public static void releaseContextForVulkanInitialization() {
        if (glContextWindow == 0L) {
            throw new IllegalStateException("OpenGL UI context is not initialized");
        }
        if (GLFW.glfwGetCurrentContext() == glContextWindow) {
            GL11.glFinish();
            GLFW.glfwMakeContextCurrent(0L);
        }
    }

    public static void restoreContextAfterVulkanInitialization() {
        makeContextCurrent();
    }

    public static void onFramebufferSizeChanged() {
        framebufferResizePending = true;
        lastFramebufferResizeNanos = System.nanoTime();
    }

    public static boolean processPendingResize() {
        boolean nativeResizePending = glContextWindow != 0L && OpenGLUiProxy.hasResizePending();
        if (!framebufferResizePending && !nativeResizePending) {
            return true;
        }

        if (glContextWindow == 0L) {
            return true;
        }

        if (framebufferResizePending
            && System.nanoTime() - lastFramebufferResizeNanos < RESIZE_SETTLE_NANOS) {
            if (OpenGLUiProxy.hasActiveFrame()) {
                return true;
            }
            GLFW.glfwWaitEventsTimeout(0.01);
            return false;
        }

        GLFW.glfwMakeContextCurrent(glContextWindow);
        GL11.glFinish();
        if (!OpenGLUiProxy.prepareResize()) {
            return true;
        }

        releaseImportedSurfaces();
        GLFW.glfwMakeContextCurrent(0L);
        try {
            OpenGLUiProxy.completeResize();
        } finally {
            GLFW.glfwMakeContextCurrent(glContextWindow);
        }
        framebufferResizePending = false;
        return true;
    }

    public static boolean isResizePending() {
        return framebufferResizePending
            || (glContextWindow != 0L && OpenGLUiProxy.hasResizePending());
    }

    public static boolean hasActiveFrame() {
        return glContextWindow != 0L && OpenGLUiProxy.hasActiveFrame();
    }

    public static void prepareFrame() {
        if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.prepareFrame");
        try {
            makeContextCurrent();
            checkCapabilities();
            if (preparedFrame != null) {
                throw new IllegalStateException("OpenGL UI world frame was prepared twice");
            }

            long[] frameInfo = OpenGLUiProxy.prepareFrame();
            if (frameInfo.length != 4) {
                throw new IllegalStateException("Native OpenGL UI frame info is invalid");
            }

            long generation = frameInfo[0];
            int frameIndex = Math.toIntExact(frameInfo[1]);
            int width = Math.toIntExact(frameInfo[2]);
            int height = Math.toIntExact(frameInfo[3]);
            if (generation <= 0L || frameIndex < 0 || width <= 0 || height <= 0) {
                throw new IllegalStateException("Native OpenGL UI frame info contains invalid dimensions");
            }

            preparedFrame = new PreparedFrame(generation, frameIndex, width, height);
            currentSurface = null;
            compositedFramebuffer = null;
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    public static void compositePreparedWorld(RenderTarget uiFramebuffer, boolean setViewport) {
        if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.compositePreparedWorld");
        try {
            makeContextCurrent();
            if (preparedFrame == null && OpenGLUiProxy.hasActiveFrame()) {
                prepareFrame();
            }
            if (preparedFrame == null) {
                throw new IllegalStateException("OpenGL UI world was not prepared before framebuffer composition");
            }
            if (compositedFramebuffer != null) {
                throw new IllegalStateException("OpenGL UI world was composited twice");
            }

            validateUiFramebuffer(uiFramebuffer);
            currentSurface = getOrImportSurface(preparedFrame);
            currentSurface.waitForVulkan();
            uiFramebuffer.bindWrite(setViewport);
            currentSurface.compositeWorldToBoundFramebuffer();
            compositedFramebuffer = uiFramebuffer;
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    public static void submitCompositedFrame(RenderTarget uiFramebuffer) {
        if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.submitCompositedFrame");
        try {
            makeContextCurrent();
            if (preparedFrame == null && OpenGLUiProxy.hasActiveFrame()) {
                prepareFrame();
            }
            if (preparedFrame == null) {
                return;
            }
            if (currentSurface == null) {
                currentSurface = getOrImportSurface(preparedFrame);
                currentSurface.waitForVulkan();
            }
            if (compositedFramebuffer != null && compositedFramebuffer != uiFramebuffer) {
                throw new IllegalStateException("OpenGL UI frame was not composited into the presented framebuffer");
            }

            uiFramebuffer.unbindWrite();
            currentSurface.copyFramebufferToExternal(uiFramebuffer);
            currentSurface.signalForVulkan();
            OpenGLUiProxy.submitFrame();
            resetFrameState();
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    public static void releaseImportedSurfaces() {
        if (glContextWindow == 0L) {
            resetFrameState();
            return;
        }
        GLFW.glfwMakeContextCurrent(glContextWindow);
        GL11.glFinish();
        resetFrameState();
        Iterator<SurfaceResources> iterator = surfaces.values().iterator();
        while (iterator.hasNext()) {
            iterator.next().close();
            iterator.remove();
        }
        GL11.glFinish();
    }

    public static void closeOpenGLContext() {
        releaseImportedSurfaces();
        if (glContextWindow != 0L) {
            releaseCompositeResources();
            GLFW.glfwMakeContextCurrent(0L);
            glContextWindow = 0L;
            glContextCapabilitiesCreated = false;
            capabilitiesChecked = false;
            framebufferResizePending = false;
            lastFramebufferResizeNanos = 0L;
        }
    }

    private static void makeContextCurrent() {
        if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.makeContextCurrent");
        try {
            if (glContextWindow == 0L) {
                throw new IllegalStateException("OpenGL UI context is not initialized");
            }
            if (GLFW.glfwGetCurrentContext() == glContextWindow) {
                if (Profiler.ENABLED) Profiler.metric("opengl_ui.context_switch_skipped_count", 1);
                return;
            }
            if (Profiler.ENABLED) Profiler.enter("java.OpenGLUiBridge.switchContext");
            try {
                GLFW.glfwMakeContextCurrent(glContextWindow);
                if (Profiler.ENABLED) Profiler.metric("opengl_ui.context_switch_count", 1);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    private static void drawFullscreenTexture(int sourceTexture) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWriteEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try {
            ensureCompositeResources();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL20.glUseProgram(compositeProgram);
            GL30.glBindVertexArray(compositeVertexArray);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
            GL20.glUniform1i(compositeSamplerUniform, 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL30.glBindVertexArray(previousVertexArray);
            GL20.glUseProgram(previousProgram);
            restoreEnabled(GL11.GL_DEPTH_TEST, depthTestEnabled);
            GL11.glDepthMask(depthWriteEnabled);
            restoreEnabled(GL11.GL_BLEND, blendEnabled);
            restoreEnabled(GL11.GL_CULL_FACE, cullEnabled);
            restoreEnabled(GL11.GL_SCISSOR_TEST, scissorEnabled);
            GL13.glActiveTexture(previousActiveTexture);
            BufferUploader.invalidate();
        }
    }

    private static void restoreEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static void ensureCompositeResources() {
        if (compositeProgram != 0) {
            return;
        }

        compositeProgram = createCompositeProgram();
        compositeVertexArray = GL30.glGenVertexArrays();
        compositeSamplerUniform = GL20.glGetUniformLocation(compositeProgram, "uWorldColor");
        GL20.glUseProgram(compositeProgram);
        GL20.glUniform1i(compositeSamplerUniform, 0);
        GL20.glUseProgram(0);
    }

    private static int createCompositeProgram() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, COMPOSITE_VERTEX_SHADER_SOURCE);
        int fragmentShader =
            compileShader(GL20.GL_FRAGMENT_SHADER, COMPOSITE_FRAGMENT_SHADER_SOURCE);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String infoLog = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("OpenGL UI composite shader link failed: " + infoLog);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String infoLog = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("OpenGL UI composite shader compile failed: " + infoLog);
        }
        return shader;
    }

    private static void releaseCompositeResources() {
        if (compositeVertexArray != 0) {
            GL30.glDeleteVertexArrays(compositeVertexArray);
            compositeVertexArray = 0;
        }
        if (compositeProgram != 0) {
            GL20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
        compositeSamplerUniform = -1;
    }

    private static SurfaceResources getOrImportSurface(PreparedFrame frame) {
        SurfaceKey key = new SurfaceKey(frame.generation, frame.frameIndex);
        SurfaceResources existing = surfaces.get(key);
        if (existing != null) {
            if (existing.width != frame.width || existing.height != frame.height) {
                throw new IllegalStateException("OpenGL UI surface dimensions changed without a new generation");
            }
            return existing;
        }

        long[] handles = OpenGLUiProxy.exportSurface(frame.frameIndex);
        if (handles.length != 8) {
            throw new IllegalStateException("Native OpenGL UI surface export is invalid");
        }
        if (handles[0] != frame.generation || handles[1] != frame.frameIndex
            || handles[2] != frame.width || handles[3] != frame.height) {
            throw new IllegalStateException("Native OpenGL UI surface export does not match current frame");
        }

        SurfaceResources imported = new SurfaceResources(frame.generation, frame.frameIndex,
            frame.width, frame.height, handles[4], handles[5], handles[6], handles[7]);
        surfaces.put(key, imported);
        removeOlderGenerations(frame.generation);
        return imported;
    }

    private static void removeOlderGenerations(long currentGeneration) {
        boolean releasedSurface = false;
        Iterator<Map.Entry<SurfaceKey, SurfaceResources>> iterator = surfaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SurfaceKey, SurfaceResources> entry = iterator.next();
            if (entry.getKey().generation < currentGeneration) {
                entry.getValue().close();
                iterator.remove();
                releasedSurface = true;
            }
        }
        if (releasedSurface) {
            GL11.glFinish();
        }
    }

    private static void checkCapabilities() {
        if (capabilitiesChecked) {
            return;
        }
        GLCapabilities capabilities = GL.getCapabilities();
        boolean platformInteropAvailable;
        String platformInteropExtensions;
        if (Platform.get() == Platform.WINDOWS) {
            platformInteropAvailable = capabilities.GL_EXT_memory_object_win32
                && capabilities.GL_EXT_semaphore_win32;
            platformInteropExtensions =
                "GL_EXT_memory_object_win32 and GL_EXT_semaphore_win32";
        } else if (Platform.get() == Platform.LINUX) {
            platformInteropAvailable = capabilities.GL_EXT_memory_object_fd
                && capabilities.GL_EXT_semaphore_fd;
            platformInteropExtensions = "GL_EXT_memory_object_fd and GL_EXT_semaphore_fd";
        } else {
            platformInteropAvailable = false;
            platformInteropExtensions = "platform external-memory and semaphore extensions";
        }
        if (!capabilities.GL_EXT_memory_object || !capabilities.GL_EXT_semaphore
            || !platformInteropAvailable) {
            throw new IllegalStateException(
                "OpenGL UI renderer '" + GL11.glGetString(GL11.GL_RENDERER) + "' from '"
                    + GL11.glGetString(GL11.GL_VENDOR)
                    + "' requires GL_EXT_memory_object, GL_EXT_semaphore, "
                    + platformInteropExtensions);
        }
        capabilitiesChecked = true;
    }

    private static void validateUiFramebuffer(RenderTarget framebuffer) {
        if (framebuffer.width <= 0 || framebuffer.height <= 0
            || framebuffer.getColorTextureId() <= 0 || framebuffer.frameBufferId <= 0) {
            throw new IllegalStateException("OpenGL UI framebuffer is unavailable");
        }
    }

    private static void resetFrameState() {
        preparedFrame = null;
        currentSurface = null;
        compositedFramebuffer = null;
    }
}
