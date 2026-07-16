package com.radiance.client.option.compat;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
//#if MC>=260200
//$$ import net.minecraft.util.StringRepresentable;
//#else
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;
//#endif

//#if MC>=260200
//$$ public interface RadianceOptionEnum extends StringRepresentable {
//#else
public interface RadianceOptionEnum extends TranslatableOption, StringIdentifiable {
//#endif

    String serializedName();

    int optionId();

    String translationKey();

    //#if MC>=260200
    //$$ @Override
    //$$ default String getSerializedName() {
    //$$     return serializedName();
    //$$ }
    //#else
    @Override
    default String asString() {
        return serializedName();
    }
    //#endif

    //#if MC<260200
    @Override
    //#endif
    default int getId() {
        return optionId();
    }

    //#if MC<260200
    @Override
    //#endif
    default String getTranslationKey() {
        return translationKey();
    }

    //#if MC>=260200
    //$$ static <E extends Enum<E> & RadianceOptionEnum> Codec<E> codec(Supplier<E[]> values) {
    //$$     return StringRepresentable.fromEnum(values);
    //$$ }
    //#else
    static <E extends Enum<E> & RadianceOptionEnum> Codec<E> codec(Supplier<E[]> values) {
        return StringIdentifiable.createCodec(values);
    }
    //#endif
}
