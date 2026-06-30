package com.radiance.loader.neoforge.scene_import;

import com.radiance.loader.neoforge.scene_import.plugin.Blaze3DClassScanner;
import com.radiance.loader.neoforge.scene_import.plugin.Blaze3DCallsite;
import com.radiance.loader.neoforge.scene_import.plugin.Blaze3DCallTarget;
import com.radiance.loader.neoforge.scene_import.plugin.CallsiteInjectionRecord;
import com.radiance.loader.neoforge.scene_import.plugin.SceneImportCallsiteInjectionReport;
import com.radiance.loader.neoforge.scene_import.plugin.SceneImportBundleResolver;
import com.radiance.loader.neoforge.scene_import.plugin.SceneImportCallsiteRules;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SceneImportCallsiteClassProcessorProvider implements ClassProcessorProvider {
    private static final Logger LOG =
        LoggerFactory.getLogger(SceneImportCallsiteClassProcessorProvider.class);

    @Override
    public void createProcessors(Context context, Collector collector) {
        SceneImportCallsiteInjectionReport.initialize(Path.of(System.getProperty("user.dir", ".")));
        collector.add(new Processor(loadRules()));
        LOG.info("[RadianceSceneImport] callsite ClassProcessor loaded");
    }

    private static SceneImportCallsiteRules loadRules() {
        Path gameDir = Path.of(System.getProperty("user.dir", "."));
        try {
            SceneImportBundleResolver.Bundle bundle = SceneImportBundleResolver.resolve(gameDir);
            LOG.info("[RadianceSceneImport] scene import bundle {} ({})",
                bundle.sha256(), bundle.source());
            return SceneImportCallsiteRules.load(bundle.rulesPath());
        } catch (IOException e) {
            LOG.warn("[RadianceSceneImport] failed to load scene import bundle: {}", e.toString());
            return new SceneImportCallsiteRules(List.of());
        }
    }

    static final class Processor implements ClassProcessor {
        private static final String RUNTIME_OWNER =
            "com/radiance/client/blaze3d/scene_import/SceneImportCallsiteRuntime";
        private static final String RENDER_PASS_OWNER = "com/mojang/blaze3d/systems/RenderPass";
        private static final String RENDER_PASS_DESC = "Lcom/mojang/blaze3d/systems/RenderPass;";

        private final SceneImportCallsiteRules rules;

        Processor(SceneImportCallsiteRules rules) {
            this.rules = rules;
        }

        @Override
        public ProcessorName name() {
            return new ProcessorName("radiance", "scene_import_callsite_injector");
        }

        @Override
        public Set<ProcessorName> runsAfter() {
            return Set.of(ClassProcessorIds.COMPUTING_FRAMES);
        }

        @Override
        public OrderingHint orderingHint() {
            return OrderingHint.EARLY;
        }

        @Override
        public boolean handlesClass(SelectionContext context) {
            if (context.empty()) {
                return false;
            }
            String className = context.type().getInternalName();
            return shouldProcessClass(className);
        }

        @Override
        public ComputeFlags processClass(TransformationContext context) {
            ClassNode classNode = context.node();
            boolean changed = false;
            if ("com/mojang/blaze3d/systems/RenderPass".equals(classNode.name)) {
                changed |= addRenderPassBridgeMethods(classNode);
            }
            changed |= injectCallsites(classNode);
            return changed ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
        }

        private boolean injectCallsites(ClassNode classNode) {
            boolean changed = false;
            for (MethodNode method : classNode.methods) {
                changed |= injectMethod(classNode, method);
            }
            return changed;
        }

        private boolean injectMethod(ClassNode classNode, MethodNode method) {
            boolean changed = false;
            int line = -1;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                 insn = insn.getNext()) {
                if (insn instanceof LineNumberNode lineNumber) {
                    line = lineNumber.line;
                    continue;
                }
                if (!(insn instanceof MethodInsnNode call)) {
                    continue;
                }
                if (!Blaze3DCallTarget.matches(call.owner, call.name, call.desc)) {
                    continue;
                }
                Blaze3DCallsite callsite = new Blaze3DCallsite(
                    classNode.name,
                    method.name,
                    method.desc,
                    line,
                    opcodeName(call.getOpcode()),
                    call.owner,
                    call.name,
                    call.desc,
                    Blaze3DCallTarget.category(call.owner, call.name, call.desc),
                    Blaze3DClassScanner.nearbyStrings(call));
                SceneImportCallsiteRules.MatchResult classified = this.rules.classify(callsite);
                InjectionMode injectionMode = injectionMode(call);
                boolean injected = injectionMode == InjectionMode.SET_PIPELINE
                    || (injectionMode == InjectionMode.DRAW && classified.injectsSceneImport());
                if (injected) {
                    method.instructions.insertBefore(call,
                        classificationCall(method, call, classified));
                    changed = true;
                }
                record(classified, injected);
            }
            return changed;
        }

        private static boolean shouldProcessClass(String className) {
            if (RENDER_PASS_OWNER.equals(className)) {
                return true;
            }
            if (className.startsWith("java/")
                || className.startsWith("javax/")
                || className.startsWith("jdk/")
                || className.startsWith("sun/")
                || className.startsWith("com/sun/")
                || className.startsWith("com/google/")
                || className.startsWith("com/ibm/")
                || className.startsWith("com/mojang/authlib/")
                || className.startsWith("com/mojang/brigadier/")
                || className.startsWith("com/mojang/datafixers/")
                || className.startsWith("com/mojang/logging/")
                || className.startsWith("com/mojang/serialization/")
                || className.startsWith("com/mojang/util/")
                || className.startsWith("it/unimi/")
                || className.startsWith("kotlin/")
                || className.startsWith("org/apache/")
                || className.startsWith("org/intellij/")
                || className.startsWith("org/jetbrains/")
                || className.startsWith("org/joml/")
                || className.startsWith("org/lwjgl/")
                || className.startsWith("org/objectweb/asm/")
                || className.startsWith("org/slf4j/")
                || className.startsWith("org/yaml/")
                || className.startsWith("org/spongepowered/")
                || className.startsWith("net/neoforged/")
                || className.startsWith("net/fabricmc/")
                || className.startsWith("cpw/mods/")
                || className.startsWith("com/radiance/client/blaze3d/scene_import/")
                || className.startsWith("com/radiance/loader/neoforge/scene_import/")) {
                return false;
            }
            return className.startsWith("net/minecraft/client/")
                || className.startsWith("com/mojang/blaze3d/")
                || !className.startsWith("net/minecraft/server/");
        }

        private static InjectionMode injectionMode(MethodInsnNode call) {
            if (!RENDER_PASS_OWNER.equals(call.owner)) {
                return InjectionMode.NONE;
            }
            if ("setPipeline".equals(call.name)
                && "(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V".equals(call.desc)) {
                return InjectionMode.SET_PIPELINE;
            }
            if (isRenderPassDrawCall(call.name, call.desc)) {
                return InjectionMode.DRAW;
            }
            return InjectionMode.NONE;
        }

        private static boolean isRenderPassDrawCall(String name, String desc) {
            return switch (name) {
                case "drawIndexed" -> "(IIIII)V".equals(desc);
                case "multiDrawIndexed" -> "(Ljava/nio/IntBuffer;III)V".equals(desc)
                    || "(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V".equals(desc);
                case "drawIndexedIndirect" -> "(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;I)V".equals(desc);
                case "drawMultipleIndexed" -> "(Ljava/util/Collection;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;Ljava/util/Collection;Ljava/lang/Object;)V".equals(desc);
                case "draw" -> "(IIII)V".equals(desc);
                case "multiDraw" -> "(Ljava/nio/IntBuffer;III)V".equals(desc)
                    || "(Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V".equals(desc);
                case "drawIndirect" -> "(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;I)V".equals(desc);
                default -> false;
            };
        }

        private static InsnList classificationCall(MethodNode method, MethodInsnNode call,
            SceneImportCallsiteRules.MatchResult classified) {
            InsnList list = new InsnList();
            StoredInvocation stored = storeInvocationStack(method, call, list);
            list.add(new VarInsnNode(Opcodes.ALOAD, stored.receiverLocal()));
            if (classified.injectsSceneImport()) {
                SceneImportCallsiteRules.Rule rule = classified.rule();
                pushInt(list, rule.sceneRouteMask());
                pushInt(list, rule.formatId());
                pushInt(list, rule.importKernelId());
                pushInt(list, rule.rasterClassId());
                pushInt(list, rule.materialPolicyId());
                pushInt(list, rule.transformRecipeId());
                list.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "com/mojang/blaze3d/systems/RenderPass",
                    "radiance$setSceneImportClassification",
                    "(IIIIII)V",
                    false));
            } else {
                list.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "com/mojang/blaze3d/systems/RenderPass",
                    "radiance$clearSceneImportClassification",
                    "()V",
                    false));
            }
            reloadInvocationStack(stored, list);
            return list;
        }

        private static StoredInvocation storeInvocationStack(MethodNode method, MethodInsnNode call,
            InsnList list) {
            Type[] argumentTypes = Type.getArgumentTypes(call.desc);
            int[] argumentLocals = new int[argumentTypes.length];
            int nextLocal = method.maxLocals;
            for (int i = argumentTypes.length - 1; i >= 0; i--) {
                Type type = argumentTypes[i];
                int local = nextLocal;
                nextLocal += type.getSize();
                argumentLocals[i] = local;
                list.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), local));
            }
            int receiverLocal = nextLocal++;
            list.add(new VarInsnNode(Opcodes.ASTORE, receiverLocal));
            method.maxLocals = Math.max(method.maxLocals, nextLocal);
            return new StoredInvocation(receiverLocal, argumentTypes, argumentLocals);
        }

        private static void reloadInvocationStack(StoredInvocation stored, InsnList list) {
            list.add(new VarInsnNode(Opcodes.ALOAD, stored.receiverLocal()));
            Type[] argumentTypes = stored.argumentTypes();
            int[] argumentLocals = stored.argumentLocals();
            for (int i = 0; i < argumentTypes.length; i++) {
                list.add(new VarInsnNode(argumentTypes[i].getOpcode(Opcodes.ILOAD),
                    argumentLocals[i]));
            }
        }

        private static boolean addRenderPassBridgeMethods(ClassNode classNode) {
            boolean changed = false;
            if (!hasMethod(classNode, "radiance$setSceneImportClassification", "(IIIIII)V")) {
                MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC,
                    "radiance$setSceneImportClassification", "(IIIIII)V", null, null);
                InsnList insns = method.instructions;
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETFIELD,
                    classNode.name,
                    "backend",
                    "Lcom/mojang/blaze3d/systems/RenderPassBackend;"));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 1));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 2));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 3));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 4));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 5));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 6));
                insns.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    RUNTIME_OWNER,
                    "setClassification",
                    "(Ljava/lang/Object;IIIIII)V",
                    false));
                insns.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
                classNode.methods.add(method);
                changed = true;
            }
            if (!hasMethod(classNode, "radiance$clearSceneImportClassification", "()V")) {
                MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC,
                    "radiance$clearSceneImportClassification", "()V", null, null);
                InsnList insns = method.instructions;
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETFIELD,
                    classNode.name,
                    "backend",
                    "Lcom/mojang/blaze3d/systems/RenderPassBackend;"));
                insns.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    RUNTIME_OWNER,
                    "clearClassification",
                    "(Ljava/lang/Object;)V",
                    false));
                insns.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
                classNode.methods.add(method);
                changed = true;
            }
            return changed;
        }

        private static boolean hasMethod(ClassNode classNode, String name, String desc) {
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    return true;
                }
            }
            return false;
        }

        private static void pushInt(InsnList list, int value) {
            if (value >= -1 && value <= 5) {
                int opcode = value == -1 ? Opcodes.ICONST_M1 : Opcodes.ICONST_0 + value;
                list.add(new org.objectweb.asm.tree.InsnNode(opcode));
            } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                list.add(new IntInsnNode(Opcodes.BIPUSH, value));
            } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                list.add(new IntInsnNode(Opcodes.SIPUSH, value));
            } else {
                list.add(new org.objectweb.asm.tree.LdcInsnNode(value));
            }
        }

        private static void record(SceneImportCallsiteRules.MatchResult classified,
            boolean injected) {
            Blaze3DCallsite c = classified.callsite();
            SceneImportCallsiteRules.Rule r = classified.rule();
            SceneImportCallsiteInjectionReport.record(new CallsiteInjectionRecord(
                c.className(),
                c.methodName(),
                c.methodDescriptor(),
                c.line(),
                c.owner(),
                c.name(),
                c.descriptor(),
                c.category(),
                r.id(),
                r.semantic(),
                r.sceneRoute(),
                r.format(),
                r.importKernel(),
                r.rasterClass(),
                r.materialPolicy(),
                r.transform(),
                r.sceneRouteMask(),
                r.formatId(),
                r.importKernelId(),
                r.rasterClassId(),
                r.materialPolicyId(),
                r.transformRecipeId(),
                injected,
                c.nearbyStrings()));
        }

        private static String opcodeName(int opcode) {
            return switch (opcode) {
                case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
                case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
                case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
                case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
                default -> "OPCODE_" + opcode;
            };
        }

        private enum InjectionMode {
            NONE,
            SET_PIPELINE,
            DRAW
        }

        private record StoredInvocation(int receiverLocal, Type[] argumentTypes,
            int[] argumentLocals) {
        }
    }
}
