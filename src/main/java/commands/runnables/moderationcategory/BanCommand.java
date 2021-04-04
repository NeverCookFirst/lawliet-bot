package commands.runnables.moderationcategory;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import commands.listeners.CommandProperties;
import core.EmbedFactory;
import core.mention.Mention;
import core.mention.MentionValue;
import core.utils.BotPermissionUtil;
import core.utils.MentionUtil;
import core.utils.StringUtil;
import core.utils.TimeUtil;
import modules.schedulers.TempBanScheduler;
import mysql.modules.tempban.DBTempBan;
import mysql.modules.tempban.TempBanSlot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

@CommandProperties(
        trigger = "ban",
        botGuildPermissions = Permission.BAN_MEMBERS,
        userGuildPermissions = Permission.BAN_MEMBERS,
        emoji = "\uD83D\uDEAB",
        executableWithoutArgs = false,
        aliases = { "tempban" }
)
public class BanCommand extends WarnCommand {

    private long minutes;

    public BanCommand(Locale locale, String prefix) {
        super(locale, prefix, true, false, true);
    }

    @Override
    protected boolean setUserListAndReason(GuildMessageReceivedEvent event, String args) throws Throwable {
        MentionValue<Long> mention = MentionUtil.getTimeMinutesExt(args);
        this.minutes = mention.getValue();
        return super.setUserListAndReason(event, mention.getFilteredArgs());
    }

    @Override
    protected void process(Guild guild, User target, String reason) {
        if (minutes > 0) {
            TempBanSlot tempBan = new TempBanSlot(guild.getIdLong(), target.getIdLong(), Instant.now().plus(Duration.ofMinutes(minutes)));
            DBTempBan.getInstance().retrieve(guild.getIdLong()).put(target.getIdLong(), tempBan);
            TempBanScheduler.getInstance().loadTempBan(tempBan);
        } else {
            DBTempBan.getInstance().retrieve(guild.getIdLong()).remove(target.getIdLong());
        }

        guild.ban(target.getId(), 1, reason)
                .submit()
                .exceptionally(e -> {
                    guild.ban(target.getId(), 1).queue();
                    return null;
                });
    }

    @Override
    protected boolean canProcess(Member executor, User target) {
        return BotPermissionUtil.canInteract(executor.getGuild(), target);
    }

    @Override
    protected EmbedBuilder getActionEmbed(Member executor, TextChannel channel) {
        String remaining = TimeUtil.getRemainingTimeString(getLocale(), minutes * 60_000, false);
        Mention mention = MentionUtil.getMentionedStringOfDiscriminatedUsers(getLocale(), getUserList());
        return EmbedFactory.getEmbedDefault(this, getString(minutes == 0 ? "action" : "action_temp", mention.isMultiple(), mention.getMentionText(), executor.getAsMention(), StringUtil.escapeMarkdown(channel.getGuild().getName()), remaining));
    }

    @Override
    protected EmbedBuilder getConfirmationEmbed() {
        String remaining = TimeUtil.getRemainingTimeString(getLocale(), minutes * 60_000, false);
        Mention mention = MentionUtil.getMentionedStringOfDiscriminatedUsers(getLocale(), getUserList());
        return EmbedFactory.getEmbedDefault(this, getString(minutes == 0 ? "confirmaion" : "confirmaion_temp", mention.getMentionText(), remaining));
    }

    @Override
    protected EmbedBuilder getSuccessEmbed() {
        String remaining = TimeUtil.getRemainingTimeString(getLocale(), minutes * 60_000, false);
        Mention mention = MentionUtil.getMentionedStringOfDiscriminatedUsers(getLocale(), getUserList());
        return EmbedFactory.getEmbedDefault(this, getString(minutes == 0 ? "success_description" : "success_description_temp", mention.isMultiple(), mention.getMentionText(), remaining));
    }

}
