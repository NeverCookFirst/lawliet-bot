package commands.runnables.aitoyscategory;

import commands.CommandEvent;
import commands.listeners.CommandProperties;
import commands.runnables.RunPodAbstract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@CommandProperties(
        trigger = "txt2img",
        emoji = "🖌️",
        executableWithoutArgs = false,
        patreonRequired = true,
        aliases = {"stablediffusion", "diffusion", "imagine"}
)
public class Txt2ImgCommand extends RunPodAbstract {

    private static final String[] CONTENT_FILTERS = {"nsfw", "porn", "porno", "pornography", "sex", "sexual",
            "intercourse", "coitus", "explicit", "fuck", "fucking", "fucked", "rape", "raping", "raped", "blowjob",
            "anal", "nude", "naked", "penis", "cock", "dick", "vagina", "pussy", "cum", "sperm", "horny", "scat",
            "lewd"};

    public Txt2ImgCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    @Override
    public List<String> getFilters(@NotNull CommandEvent event) {
        return List.of(CONTENT_FILTERS);
    }

}
