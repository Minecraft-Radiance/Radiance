package com.radiance.mixin_related;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.INameMappingService;
import java.util.Optional;
import java.util.function.BiFunction;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

final class SrgMixinRemapper implements IRemapper {

    private static boolean installed;

    private final BiFunction<INameMappingService.Domain, String, String> nameMapping;

    private SrgMixinRemapper(
        BiFunction<INameMappingService.Domain, String, String> nameMapping) {
        this.nameMapping = nameMapping;
    }

    static synchronized void install() {
        if (installed || Launcher.INSTANCE == null) {
            return;
        }

        Optional<BiFunction<INameMappingService.Domain, String, String>> nameMapping =
            Launcher.INSTANCE.environment().findNameMapping("srg");
        if (nameMapping.isEmpty()) {
            return;
        }

        MixinEnvironment.getCurrentEnvironment().getRemappers()
            .add(new SrgMixinRemapper(nameMapping.get()));
        installed = true;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return mapMember(owner, name, 'm', INameMappingService.Domain.METHOD);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return mapMember(owner, name, 'f', INameMappingService.Domain.FIELD);
    }

    @Override
    public String map(String typeName) {
        return typeName;
    }

    @Override
    public String unmap(String typeName) {
        return typeName;
    }

    @Override
    public String mapDesc(String descriptor) {
        return descriptor;
    }

    @Override
    public String unmapDesc(String descriptor) {
        return descriptor;
    }

    private String mapMember(String owner, String name, char prefix,
        INameMappingService.Domain domain) {
        if (owner == null || !owner.startsWith("net/minecraft/")
            || !isSrgMemberName(name, prefix)) {
            return name;
        }

        String mappedName = nameMapping.apply(domain, name);
        return mappedName == null ? name : mappedName;
    }

    private static boolean isSrgMemberName(String name, char prefix) {
        if (name == null || name.length() < 4 || name.charAt(0) != prefix
            || name.charAt(1) != '_' || name.charAt(name.length() - 1) != '_') {
            return false;
        }

        for (int index = 2; index < name.length() - 1; index++) {
            if (!Character.isDigit(name.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
