package rustythecodeguy.gigachat.utils;

import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

public class utils {
    /**
     * Applies old Minecraft formatting
     *
     * @param text - Text builder object
     * @return Text.builder()
     */
    @Deprecated
    public Text applyOldMinecraftFormat(Text text){
        return Text.builder(TextSerializers.LEGACY_FORMATTING_CODE.serialize(text)).build();
    }
    /**
     * Applies Minecraft formatting
     *
     * @param text - Text builder object
     * @return Text.builder()
     */
    public Text applyMinecraftFormat(Text text){
        return Text.builder(TextSerializers.FORMATTING_CODE.serialize(text)).build();
    }
}
