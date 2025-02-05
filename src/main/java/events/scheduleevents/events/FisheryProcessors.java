package events.scheduleevents.events;

import commands.Category;
import constants.ExceptionRunnable;
import core.EmbedFactory;
import core.MainLogger;
import core.ShardManager;
import core.TextManager;
import core.utils.MentionUtil;
import events.scheduleevents.ScheduleEventFixedRate;
import modules.fishery.Fishery;
import modules.fishery.FisheryGear;
import modules.fishery.FisheryStatus;
import mysql.hibernate.HibernateManager;
import mysql.hibernate.entity.GuildEntity;
import mysql.modules.autosell.AutoSellData;
import mysql.modules.autosell.DBAutoSell;
import mysql.modules.autowork.AutoWorkData;
import mysql.modules.autowork.DBAutoWork;
import mysql.modules.fisheryusers.DBFishery;
import mysql.modules.fisheryusers.FisheryGuildData;
import mysql.modules.fisheryusers.FisheryMemberData;
import mysql.modules.subs.DBSubs;
import mysql.modules.subs.SubSlot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@ScheduleEventFixedRate(rateValue = 1, rateUnit = ChronoUnit.MINUTES)
public class FisheryProcessors implements ExceptionRunnable {

    @Override
    public void run() throws Throwable {
        AtomicInteger voiceActivityActions = new AtomicInteger(0);
        AtomicInteger autoSellActions = new AtomicInteger(0);
        AtomicInteger autoWorkActions = new AtomicInteger(0);

        HashMap<Long, HashSet<Guild>> reminderGuildMap = new HashMap<>();
        Map<Long, SubSlot> subMap = DBSubs.getInstance().retrieve(DBSubs.Command.WORK);

        for (Guild guild : ShardManager.getLocalGuilds()) {
            try (GuildEntity guildEntity = HibernateManager.findGuildEntity(guild.getIdLong())) {
                if (guildEntity.getFishery().getFisheryStatus() == FisheryStatus.ACTIVE) {
                    processVoiceActivity(guild, guildEntity, voiceActivityActions);
                    processAutoSell(guild, autoSellActions);
                    processAutoWork(guild, subMap, autoWorkActions, reminderGuildMap);
                }
            }
        }

        MainLogger.get().info("Voice Channel - {} Actions", voiceActivityActions.get());
        MainLogger.get().info("Auto Sell - {} Actions", autoSellActions.get());
        MainLogger.get().info("Auto Work - {} Actions", autoWorkActions.get());

        autoWorkPostProcessing(reminderGuildMap, subMap);
    }

    private void processVoiceActivity(Guild guild, GuildEntity guildEntity, AtomicInteger actions) {
        try {
            FisheryGuildData serverBean = DBFishery.getInstance().retrieve(guild.getIdLong());
            for (VoiceChannel voiceChannel : guild.getVoiceChannels()) {
                try {
                    List<Member> validMembers = Fishery.getValidVoiceMembers(voiceChannel);
                    VoiceChannel afkVoice = guild.getAfkChannel();
                    if (afkVoice == null || voiceChannel.getIdLong() != afkVoice.getIdLong()) {
                        validMembers.forEach(member -> {
                            try {
                                serverBean.getMemberData(member.getIdLong()).registerVoice(guildEntity, 1);
                                actions.incrementAndGet();
                            } catch (ExecutionException e) {
                                MainLogger.get().error("Exception when registering vc", e);
                            }
                        });
                    }
                } catch (Throwable e) {
                    MainLogger.get().error("Error while fetching voice member list", e);
                }
            }
        } catch (Throwable e) {
            MainLogger.get().error("Could not process voice channel activities for guild {}", guild.getIdLong(), e);
        }
    }

    private void processAutoSell(Guild guild, AtomicInteger actions) {
        try {
            FisheryGuildData fisheryGuildData = DBFishery.getInstance().retrieve(guild.getIdLong());
            AutoSellData autoSellData = DBAutoSell.getInstance().retrieve();

            guild.getMembers().stream()
                    .filter(member -> autoSellData.getThreshold(member.getIdLong()) != null)
                    .forEach(member -> {
                        if (fisheryGuildData.getMemberData(member.getIdLong()).processAutoSell()) {
                            actions.incrementAndGet();
                        }
                    });
        } catch (Throwable e) {
            MainLogger.get().error("Could not process auto sell for guild {}", guild.getIdLong(), e);
        }
    }

    private void processAutoWork(Guild guild, Map<Long, SubSlot> subMap, AtomicInteger autoWorkActions, HashMap<Long, HashSet<Guild>> reminderGuildMap) {
        try {
            FisheryGuildData fisheryGuildData = DBFishery.getInstance().retrieve(guild.getIdLong());
            AutoWorkData autoWorkData = DBAutoWork.getInstance().retrieve();
            for (Member member : guild.getMembers()) {
                FisheryMemberData fisheryMemberData = fisheryGuildData.getMemberData(member.getIdLong());

                /* reminder */
                SubSlot sub = subMap.get(member.getIdLong());
                if (sub != null) {
                    Optional<Instant> nextWork = fisheryMemberData.getNextWork();
                    if (nextWork.isPresent() && Instant.now().isAfter(nextWork.get())) {
                        fisheryMemberData.removeWork();
                        reminderGuildMap.computeIfAbsent(member.getIdLong(), k -> new HashSet<>()).add(guild);
                    }
                }

                /* auto work */
                if (autoWorkData.isActive(member.getIdLong()) && fisheryMemberData.checkNextWork().isEmpty()) {
                    long coins = fisheryMemberData.getMemberGear(FisheryGear.WORK).getEffect();
                    fisheryMemberData.changeValues(0, coins);
                    fisheryMemberData.completeWork();
                    autoWorkActions.incrementAndGet();
                }
            }
        } catch (Throwable e) {
            MainLogger.get().error("Could not process auto work for guild {}", guild.getIdLong(), e);
        }
    }

    private static void autoWorkPostProcessing(HashMap<Long, HashSet<Guild>> reminderGuildMap, Map<Long, SubSlot> subMap) {
        try {
            for (Map.Entry<Long, HashSet<Guild>> slot : reminderGuildMap.entrySet()) {
                long userId = slot.getKey();
                HashSet<Guild> guilds = slot.getValue();
                SubSlot sub = subMap.get(userId);
                if (sub != null) {
                    Locale locale = sub.getLocale();
                    String guildsMention = MentionUtil.getMentionedStringOfGuilds(locale, new ArrayList<>(guilds)).getMentionText();
                    EmbedBuilder eb = EmbedFactory.getEmbedDefault()
                            .setTitle(TextManager.getString(locale, Category.FISHERY, "work_message_title"))
                            .setDescription(TextManager.getString(locale, Category.FISHERY, "work_message_desc", guildsMention));
                    sub.sendEmbed(locale, eb);
                }
            }
        } catch (Throwable e) {
            MainLogger.get().error("Auto work post processing errors", e);
        }
    }

}
