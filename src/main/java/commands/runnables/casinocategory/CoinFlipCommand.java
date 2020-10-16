package commands.runnables.casinocategory;

import commands.listeners.CommandProperties;
import commands.listeners.OnReactionAddListener;

import commands.runnables.CasinoAbstract;
import constants.Category;
import constants.Emojis;
import constants.LogStatus;
import core.*;
import core.utils.EmbedUtil;
import core.utils.StringUtil;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.reaction.SingleReactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;


@CommandProperties(
        trigger = "coinflip",
        emoji = "\uD83D\uDCB0",
        executableWithoutArgs = true,
        aliases = { "coin", "coins", "cf", "cointoss" }
)
public class CoinFlipCommand extends CasinoAbstract implements OnReactionAddListener {

    private final static Logger LOGGER = LoggerFactory.getLogger(CoinFlipCommand.class);

    private String log;
    private final String[] EMOJIS = {"\uD83C\uDDED", "\uD83C\uDDF9"};
    private final int[] selection = {-1, -1};
    private LogStatus logStatus;

    public CoinFlipCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    @Override
    public boolean onMessageReceived(MessageCreateEvent event, String followedString) throws Throwable {
        onlyNumbersAsArg = false;
        if (onGameStart(event, followedString)) {
            useCalculatedMultiplicator = false;
            winMultiplicator = 1;

            int coinSideSelection = getCoinValue(followedString);
            if (coinSideSelection >= 0) selection[0] = coinSideSelection;

            message = event.getChannel().sendMessage(getEmbed(event.getServerTextChannel().get(), event.getMessage().getUserAuthor().get())).get();
            if (selection[0] == -1) for (String str : EMOJIS) message.addReaction(str);
            else manageEnd();

            return true;
        }
        return false;
    }

    private int getCoinValue(String followedString) {
        for (String word : followedString.toLowerCase().split(" ")) {
            if (word.equals("h")) return 0;
            if (word.equals("t")) return 1;
            if (word.startsWith("head")) return 0;
            if (word.startsWith("tail")) return 1;
        }

        return -1;
    }

    private String getChoiceString(ServerTextChannel channel, int pos) {
        if (pos == 1 && selection[0] == -1) return Emojis.EMPTY_EMOJI;

        switch (selection[pos]) {
            case 0:
                return EMOJIS[0];
            case 1:
                return EMOJIS[1];
            default:
                return StringUtil.getLoadingReaction(channel);
        }
    }

    private EmbedBuilder getEmbed() throws IOException {
        return getEmbed(message.getServerTextChannel().get(), player);
    }

    private EmbedBuilder getEmbed(ServerTextChannel channel, User user) throws IOException {
        EmbedBuilder eb = EmbedFactory.getEmbedDefault(this);
        eb.addField(getString("yourbet"), getChoiceString(channel, 0), true);
        eb.addField(getString("yourthrow"), getChoiceString(channel, 1), true);
        eb.addField(Emojis.EMPTY_EMOJI, getString("template", user.getDisplayName(server), StringUtil.numToString(coinsInput)));

        if (selection[0] == -1) eb.addField(Emojis.EMPTY_EMOJI, getString("expl", EMOJIS[0], EMOJIS[1]));

        if (coinsInput != 0) eb.setFooter(TextManager.getString(getLocale(), Category.CASINO, "casino_footer"));

        if (!active) {
            eb = EmbedUtil.addLog(eb, logStatus, log);
            eb = addRetryOption(eb);
        }

        return eb;
    }

    private void manageEnd() {
        if (selection[0] == -1) return;
        removeReactionListener(getReactionMessage());

        Thread t = new CustomThread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted", e);
            }

            selection[1] = new Random().nextInt(2);

            try {
                message.edit(getEmbed());
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                ExceptionHandler.handleCommandException(e, this, message.getServerTextChannel().get());
            }

            if (selection[0] == selection[1]) {
                try {
                    log = TextManager.getString(getLocale(), TextManager.GENERAL, "won");
                    logStatus = LogStatus.WIN;
                    onWin();
                } catch (ExecutionException e) {
                    ExceptionHandler.handleCommandException(e, this, message.getServerTextChannel().get());
                }
            } else {
                try {
                    log = TextManager.getString(getLocale(), TextManager.GENERAL, "lost");
                    logStatus = LogStatus.LOSE;
                    onLose();
                } catch (ExecutionException e) {
                    ExceptionHandler.handleCommandException(e, this, message.getServerTextChannel().get());
                }
            }

            try {
                message.edit(getEmbed());
            } catch (IOException e) {
                ExceptionHandler.handleCommandException(e, this, message.getServerTextChannel().get());
            }
        }, "coinflip_cpu", 1);
        t.start();
    }

    @Override
    public void onReactionAdd(SingleReactionEvent event) throws Throwable {
        if (!active) {
            onReactionAddRetry(event);
            return;
        }

        if (event.getEmoji().isUnicodeEmoji()) {
            for(int i = 0; i < 2; i++) {
                if (event.getEmoji().asUnicodeEmoji().get().equalsIgnoreCase(EMOJIS[i])) {
                    selection[0] = i;
                    message.edit(getEmbed());
                    manageEnd();
                    return;
                }
            }
        }
    }

    @Override
    public Message getReactionMessage() {
        return message;
    }

    @Override
    public void onReactionTimeOut(Message message) throws Throwable {
        if (active) {
            selection[0] = 0;
            message.edit(getEmbed());
            manageEnd();
        }
    }
}
