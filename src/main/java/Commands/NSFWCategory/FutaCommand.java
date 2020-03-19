package Commands.NSFWCategory;

import CommandListeners.CommandProperties;
import Commands.GelbooruAbstract;

@CommandProperties(
        trigger = "futa",
        executable = true,
        emoji = "\uD83D\uDD1E",
        nsfw = true,
        withLoadingBar = true
)
public class FutaCommand extends GelbooruAbstract {

    @Override
    protected String getSearchKey() {
        return "animated_gif futa";
    }

    @Override
    protected boolean isGifOnly() {
        return true;
    }

}