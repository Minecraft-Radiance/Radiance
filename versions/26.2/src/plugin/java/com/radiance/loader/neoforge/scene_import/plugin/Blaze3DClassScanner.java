package com.radiance.loader.neoforge.scene_import.plugin;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class Blaze3DClassScanner {
    private Blaze3DClassScanner() {
    }

    public static List<Blaze3DCallsite> scanClassNode(ClassNode classNode) {
        List<Blaze3DCallsite> out = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            scanMethod(classNode, method, out);
        }
        return out;
    }

    private static void scanMethod(ClassNode classNode, MethodNode method, List<Blaze3DCallsite> out) {
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
            out.add(new Blaze3DCallsite(
                classNode.name,
                method.name,
                method.desc,
                line,
                opcodeName(insn.getOpcode()),
                call.owner,
                call.name,
                call.desc,
                Blaze3DCallTarget.category(call.owner, call.name, call.desc),
                nearbyStrings(insn)));
        }
    }

    public static List<String> nearbyStrings(AbstractInsnNode center) {
        ArrayList<String> strings = new ArrayList<>();
        collectStrings(center, -16, strings);
        collectStrings(center, 8, strings);
        return strings.stream().distinct().limit(16).toList();
    }

    private static void collectStrings(AbstractInsnNode center, int distance, List<String> out) {
        AbstractInsnNode insn = center;
        int steps = Math.abs(distance);
        for (int i = 0; i < steps && insn != null; i++) {
            insn = distance < 0 ? insn.getPrevious() : insn.getNext();
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                out.add(s);
            }
        }
    }

    private static String opcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
            case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
            case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
            case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
            case Opcodes.INVOKEDYNAMIC -> "INVOKEDYNAMIC";
            default -> "OPCODE_" + opcode;
        };
    }
}

