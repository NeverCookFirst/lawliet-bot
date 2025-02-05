package commands.runnables.utilitycategory;

import commands.CommandEvent;
import commands.listeners.CommandProperties;
import commands.listeners.MessageInputResponse;
import commands.runnables.NavigationAbstract;
import constants.LogStatus;
import core.EmbedFactory;
import core.LocalFile;
import core.TextManager;
import core.atomicassets.AtomicTextChannel;
import core.components.ActionRows;
import core.utils.FileUtil;
import core.utils.InternetUtil;
import core.utils.MentionUtil;
import core.utils.StringUtil;
import modules.Welcome;
import modules.graphics.WelcomeGraphics;
import mysql.modules.welcomemessage.DBWelcomeMessage;
import mysql.modules.welcomemessage.WelcomeMessageData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@CommandProperties(
        trigger = "welcome",
        botChannelPermissions = Permission.MESSAGE_EXT_EMOJI,
        userGuildPermissions = { Permission.MANAGE_SERVER },
        emoji = "🙋",
        usesExtEmotes = true,
        executableWithoutArgs = true
)
public class WelcomeCommand extends NavigationAbstract {

    public static int MAX_WELCOME_TITLE_LENGTH = 20;
    public static int MAX_WELCOME_DESCRIPTION_LENGTH = 1000;
    public static int MAX_DM_DESCRIPTION_LENGTH = 1000;
    public static int MAX_FAREWELL_DESCRIPTION_LENGTH = 1000;

    private WelcomeMessageData welcomeMessageData;
    private int category = 0;

    public WelcomeCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    @Override
    public boolean onTrigger(@NotNull CommandEvent event, @NotNull String args) {
        welcomeMessageData = DBWelcomeMessage.getInstance().retrieve(event.getGuild().getIdLong());
        welcomeMessageData.getWelcomeChannel().ifPresent(this::checkWriteEmbedInChannelAndAttachFilesWithLog);
        welcomeMessageData.getGoodbyeChannel().ifPresent(this::checkWriteEmbedInChannelAndAttachFilesWithLog);
        registerNavigationListener(event.getMember());
        return true;
    }

    @Override
    public MessageInputResponse controllerMessage(MessageReceivedEvent event, String input, int state) throws IOException, ExecutionException, InterruptedException {
        switch (state) {
            case 1:
                if (!input.isEmpty()) {
                    if (input.length() <= MAX_WELCOME_TITLE_LENGTH) {
                        welcomeMessageData.setWelcomeTitle(input);
                        setLog(LogStatus.SUCCESS, getString("titleset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    } else {
                        setLog(LogStatus.FAILURE, getString("titletoolarge", String.valueOf(MAX_WELCOME_TITLE_LENGTH)));
                        return MessageInputResponse.FAILED;
                    }
                }
                return MessageInputResponse.FAILED;

            case 2:
                if (!input.isEmpty()) {
                    if (input.length() <= MAX_WELCOME_DESCRIPTION_LENGTH) {
                        welcomeMessageData.setWelcomeText(input);
                        setLog(LogStatus.SUCCESS, getString("textset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    } else {
                        setLog(LogStatus.FAILURE, getString("descriptiontoolarge", StringUtil.numToString(MAX_WELCOME_DESCRIPTION_LENGTH)));
                        return MessageInputResponse.FAILED;
                    }
                }
                return MessageInputResponse.FAILED;

            case 3:
                List<TextChannel> channelList = MentionUtil.getTextChannels(event.getGuild(), input).getList();
                if (channelList.isEmpty()) {
                    setLog(LogStatus.FAILURE, TextManager.getNoResultsString(getLocale(), input));
                    return MessageInputResponse.FAILED;
                } else {
                    if (checkWriteEmbedInChannelAndAttachFilesWithLog(channelList.get(0))) {
                        welcomeMessageData.setWelcomeChannelId(channelList.get(0).getIdLong());
                        setLog(LogStatus.SUCCESS, getString("channelset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    } else {
                        return MessageInputResponse.FAILED;
                    }
                }

            case 4:
                List<Message.Attachment> attachmentList = event.getMessage().getAttachments();
                if (!attachmentList.isEmpty() && attachmentList.get(0).isImage()) {
                    LocalFile localFile = new LocalFile(LocalFile.Directory.WELCOME_BACKGROUNDS, String.format("%d.png", event.getGuild().getIdLong()));
                    if (FileUtil.downloadImageAttachment(attachmentList.get(0), localFile)) {
                        setLog(LogStatus.SUCCESS, getString("backgroundset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    }
                }

                setLog(LogStatus.FAILURE, TextManager.getString(getLocale(), TextManager.GENERAL, "imagenotfound"));
                return MessageInputResponse.FAILED;

            case 6:
                if (!input.isEmpty()) {
                    if (input.length() <= MAX_FAREWELL_DESCRIPTION_LENGTH) {
                        welcomeMessageData.setGoodbyeText(input);
                        setLog(LogStatus.SUCCESS, getString("textset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    } else {
                        setLog(LogStatus.FAILURE, getString("goodbyetoolarge", StringUtil.numToString(MAX_FAREWELL_DESCRIPTION_LENGTH)));
                        return MessageInputResponse.FAILED;
                    }
                }
                return MessageInputResponse.FAILED;

            case 7:
                channelList = MentionUtil.getTextChannels(event.getGuild(), input).getList();
                if (channelList.isEmpty()) {
                    setLog(LogStatus.FAILURE, TextManager.getNoResultsString(getLocale(), input));
                    return MessageInputResponse.FAILED;
                } else {
                    if (checkWriteEmbedInChannelAndAttachFilesWithLog(channelList.get(0))) {
                        welcomeMessageData.setGoodbyeChannelId(channelList.get(0).getIdLong());
                        setLog(LogStatus.SUCCESS, getString("channelset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    } else {
                        return MessageInputResponse.FAILED;
                    }
                }

            case 8:
                if (!input.isEmpty()) {
                    if (input.length() <= MAX_DM_DESCRIPTION_LENGTH) {
                        welcomeMessageData.setDmText(input);
                        setLog(LogStatus.SUCCESS, getString("textset"));
                        setState(0);
                        return MessageInputResponse.SUCCESS;
                    } else {
                        setLog(LogStatus.FAILURE, getString("dmtoolarge", StringUtil.numToString(MAX_DM_DESCRIPTION_LENGTH)));
                        return MessageInputResponse.FAILED;
                    }
                }
                return MessageInputResponse.FAILED;

            default:
                return null;
        }
    }

    @Override
    public boolean controllerButton(ButtonInteractionEvent event, int i, int state) {
        if (state == 0) {
            if (i == -1) {
                deregisterListenersWithComponentMessage();
                return false;
            }
            switch (category) {
                case 0 -> {
                    switch (i) {
                        case 0 -> {
                            welcomeMessageData.toggleWelcomeActive();
                            setLog(LogStatus.SUCCESS, getString("activeset", welcomeMessageData.isWelcomeActive(), getString("dashboard_join")));
                            return true;
                        }
                        case 1 -> {
                            setState(2);
                            return true;
                        }
                        case 2 -> {
                            welcomeMessageData.toggleWelcomeEmbed();
                            setLog(LogStatus.SUCCESS, getString("embedset", welcomeMessageData.getWelcomeEmbed()));
                            return true;
                        }
                        case 3 -> {
                            setState(3);
                            return true;
                        }
                        case 4 -> {
                            welcomeMessageData.toggleBanner();
                            setLog(LogStatus.SUCCESS, getString("bannerset", welcomeMessageData.getBanner()));
                            return true;
                        }
                        case 5 -> {
                            setState(1);
                            return true;
                        }
                        case 6 -> {
                            setState(4);
                            return true;
                        }
                        case 7 -> {
                            setState(5);
                            return true;
                        }
                    }
                }
                case 1 -> {
                    switch (i) {
                        case 0 -> {
                            welcomeMessageData.toggleDmActive();
                            setLog(LogStatus.SUCCESS, getString("activeset", welcomeMessageData.isDmActive(), getString("dashboard_dm")));
                            return true;
                        }
                        case 1 -> {
                            setState(8);
                            return true;
                        }
                        case 2 -> {
                            welcomeMessageData.toggleDmEmbed();
                            setLog(LogStatus.SUCCESS, getString("embedset", welcomeMessageData.getDmEmbed()));
                            return true;
                        }
                    }
                }
                case 2 -> {
                    switch (i) {
                        case 0 -> {
                            welcomeMessageData.toggleGoodbyeActive();
                            setLog(LogStatus.SUCCESS, getString("activeset", welcomeMessageData.isGoodbyeActive(), getString("dashboard_leave")));
                            return true;
                        }
                        case 1 -> {
                            setState(6);
                            return true;
                        }
                        case 2 -> {
                            welcomeMessageData.toggleGoodbyeEmbed();
                            setLog(LogStatus.SUCCESS, getString("embedset", welcomeMessageData.getGoodbyeEmbed()));
                            return true;
                        }
                        case 3 -> {
                            setState(7);
                            return true;
                        }
                    }
                }
                default -> throw new UnsupportedOperationException("Unsupported category value");
            }
            return false;
        } else if (i == -1) {
            setState(0);
            return true;
        }
        if (state == 4) {
            LocalFile localFile = new LocalFile(LocalFile.Directory.WELCOME_BACKGROUNDS, String.format("%d.png", event.getGuild().getIdLong()));
            localFile.delete();
            setLog(LogStatus.SUCCESS, getString("backgroundset"));
            setState(0);
            return true;
        }
        return false;
    }

    @Override
    public boolean controllerStringSelectMenu(StringSelectInteractionEvent event, int i, int state) throws Throwable {
        category = Integer.parseInt(event.getValues().get(0));
        return true;
    }

    @Override
    public EmbedBuilder draw(Member member, int state) throws ExecutionException, InterruptedException, IOException {
        String notSet = TextManager.getString(getLocale(), TextManager.GENERAL, "notset");

        if (state == 0) {
            String[] options = getString("state0_options_" + category).split("\n");
            ArrayList<Button> buttons = new ArrayList<>();
            for (int i = 0; i < options.length; i++) {
                Button button = Button.of(ButtonStyle.PRIMARY, String.valueOf(i), options[i]);
                buttons.add(button);
            }

            ArrayList<ActionRow> actionRows = new ArrayList<>(ActionRows.of(buttons));
            actionRows.add(generateSelectMenu());
            setActionRows(actionRows);

            TextChannel textChannel = getTextChannel().get();
            switch (category) {
                case 0:
                    return EmbedFactory.getEmbedDefault(this, getString("state0_description"), getString("dashboard_join"))
                            .addField(getString("state0_menabled"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.isWelcomeActive()), true)
                            .addField(getString("state0_mdescription"), StringUtil.shortenString(stressVariables(welcomeMessageData.getWelcomeText()), 1024), true)
                            .addField(getString("state0_membed"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.getWelcomeEmbed()), true)
                            .addField(getString("state0_mchannel"), welcomeMessageData.getWelcomeChannel().map(c -> new AtomicTextChannel(c).getPrefixedNameInField(getLocale())).orElse(notSet), true)
                            .addField(getString("state0_mbanner"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.getBanner()), true)
                            .addField(getString("state0_mtitle"), StringUtil.escapeMarkdown(welcomeMessageData.getWelcomeTitle()), true);

                case 1:
                    return EmbedFactory.getEmbedDefault(this, getString("state0_description"), getString("dashboard_dm"))
                            .addField(getString("state0_menabled"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.isDmActive()), true)
                            .addField(getString("state0_mdescription"), StringUtil.shortenString(stressVariables(welcomeMessageData.getDmText()), 1024), true)
                            .addField(getString("state0_membed"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.getDmEmbed()), true);

                case 2:
                    return EmbedFactory.getEmbedDefault(this, getString("state0_description"), getString("dashboard_leave"))
                            .addField(getString("state0_menabled"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.isGoodbyeActive()), true)
                            .addField(getString("state0_mdescription"), StringUtil.shortenString(stressVariables(welcomeMessageData.getGoodbyeText()), 1024), true)
                            .addField(getString("state0_membed"), StringUtil.getOnOffForBoolean(textChannel, getLocale(), welcomeMessageData.getGoodbyeEmbed()), true)
                            .addField(getString("state0_mchannel"), welcomeMessageData.getGoodbyeChannel().map(c -> new AtomicTextChannel(c).getPrefixedNameInField(getLocale())).orElse(notSet), true);

                default:
                    throw new UnsupportedOperationException("Invalid category");
            }
        } else if (state == 5) {
            return getWelcomeMessageTest(member);
        }
        if (state == 4) {
            setComponents(getString("state4_options"));
        }
        return EmbedFactory.getEmbedDefault(this, getString("state" + state + "_description"), getString("state" + state + "_title"));
    }

    private String stressVariables(String text) {
        return Welcome.resolveVariables(
                StringUtil.escapeMarkdown(text),
                "`%SERVER`",
                "`%USER_MENTION`",
                "`%USER_NAME`",
                "`%USER_DISCRIMINATED`",
                "`%MEMBERS`"
        );
    }

    public EmbedBuilder getWelcomeMessageTest(Member member) throws ExecutionException, InterruptedException, IOException {
        EmbedBuilder eb = EmbedFactory.getEmbedDefault()
                .setDescription(Welcome.resolveVariables(
                        welcomeMessageData.getWelcomeText(),
                        StringUtil.escapeMarkdown(member.getGuild().getName()),
                        member.getAsMention(),
                        StringUtil.escapeMarkdown(member.getUser().getName()),
                        StringUtil.escapeMarkdown(member.getUser().getAsTag()),
                        StringUtil.numToString(member.getGuild().getMemberCount())
                ));

        if (welcomeMessageData.getBanner()) {
            eb.setImage(InternetUtil.getUrlFromInputStream(
                    WelcomeGraphics.createImageWelcome(member, welcomeMessageData.getWelcomeTitle()).get(),
                    "png"
            ));
        }

        return eb;
    }

    private ActionRow generateSelectMenu() {
        String[] selectMenuOptions = getString("state0_selectmenu").split("\n");
        StringSelectMenu.Builder selectMenuBuilder = StringSelectMenu.create("category")
                .setRequiredRange(1, 1);
        for (int i = 0; i < selectMenuOptions.length; i++) {
            selectMenuBuilder.addOption(selectMenuOptions[i], String.valueOf(i));
        }
        selectMenuBuilder.setDefaultOptions(SelectOption.of(selectMenuOptions[category], String.valueOf(category)));
        return ActionRow.of(selectMenuBuilder.build());
    }

}
