package events.scheduleevents.events;

import commands.Category;
import constants.ExceptionRunnable;
import core.*;
import events.scheduleevents.ScheduleEventDaily;
import modules.fishery.FisheryGear;
import modules.fishery.FisheryStatus;
import mysql.hibernate.HibernateManager;
import mysql.hibernate.entity.GuildEntity;
import mysql.modules.fisheryusers.DBFishery;
import mysql.modules.fisheryusers.FisheryMemberData;
import mysql.modules.subs.DBSubs;
import mysql.modules.subs.SubSlot;
import mysql.modules.survey.DBSurvey;
import mysql.modules.survey.SurveyData;
import mysql.modules.survey.SurveyQuestion;
import mysql.modules.survey.SurveySecondVote;
import net.dv8tion.jda.api.EmbedBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@ScheduleEventDaily
public class FisherySurveyResults implements ExceptionRunnable {

    @Override
    public void run() {
        if (Program.productionMode()) {
            SurveyData surveyData = DBSurvey.getInstance().getCurrentSurvey();
            LocalDate today = LocalDate.now();
            if (!today.isBefore(surveyData.getNextDate())) {
                processCurrentResults();
            }
        }
    }

    public static void processCurrentResults() {
        DBSurvey.getInstance().clear();
        SurveyData lastSurvey = DBSurvey.getInstance().getCurrentSurvey();
        while (lastSurvey.getNextDate().isAfter(LocalDate.now())) {
            lastSurvey = DBSurvey.getInstance().retrieve(lastSurvey.getSurveyId() - 1);
        }
        DBSurvey.getInstance().updateSurveyId(lastSurvey.getSurveyId() + 1);

        MainLogger.get().info("Calculating survey results for ID {}...", lastSurvey.getSurveyId());
        processSurvey(lastSurvey);
    }

    private static void processSurvey(SurveyData lastSurvey) {
        byte won = lastSurvey.getWon();
        int percent = won != 2 ? (int) Math.round(lastSurvey.getFirstVoteNumbers(won) / (double) lastSurvey.getFirstVoteNumber() * 100) : 0;

        HashMap<Long, ArrayList<SurveySecondVote>> secondVotesByMember = groupSecondVotesToMembers(lastSurvey);

        /* processing survey results */
        MainLogger.get().info("Survey giving out prices for {} users", secondVotesByMember.keySet().size());
        for (long userId : secondVotesByMember.keySet()) {
            try {
                MainLogger.get().info("### SURVEY MANAGE USER {} ###", userId);
                processSurveyUser(secondVotesByMember.get(userId), userId, won);
            } catch (Throwable e) {
                MainLogger.get().error("Exception while managing user {}", userId, e);
            }
        }

        /* sending reminders */
        if (Program.isMainCluster()) {
            CustomObservableMap<Long, SubSlot> subMap = DBSubs.getInstance().retrieve(DBSubs.Command.SURVEY);
            for (SubSlot sub : new ArrayList<>(subMap.values())) {
                try {
                    sendSurveyResult(sub.getLocale(), lastSurvey.getSurveyQuestionAndAnswers(sub.getLocale()), sub, won, percent);
                } catch (IOException e) {
                    MainLogger.get().error("Survey error", e);
                }
            }
        }

        MainLogger.get().info("Survey results finished");
    }

    private static HashMap<Long, ArrayList<SurveySecondVote>> groupSecondVotesToMembers(SurveyData lastSurvey) {
        Map<Long, List<SurveySecondVote>> secondVotesByGuild = lastSurvey.getSecondVotes().values().stream()
                .filter(v -> v.getGuild().isPresent())
                .collect(Collectors.groupingBy(SurveySecondVote::getGuildId));

        HashMap<Long, ArrayList<SurveySecondVote>> secondVotesMap = new HashMap<>();
        for (Long guildId : secondVotesByGuild.keySet()) {
            try (GuildEntity guildEntity = HibernateManager.findGuildEntity(guildId)) {
                if (guildEntity.getFishery().getFisheryStatus() != FisheryStatus.ACTIVE) {
                    continue;
                }

                for (SurveySecondVote surveySecondVote : secondVotesByGuild.get(guildId)) {
                    secondVotesMap.computeIfAbsent(surveySecondVote.getMemberId(), k -> new ArrayList<>())
                            .add(surveySecondVote);
                }
            }
        }

        return secondVotesMap;
    }

    private static void processSurveyUser(List<SurveySecondVote> secondVotes, long userId, byte won) {
        for (SurveySecondVote secondVote : secondVotes) {
            if (won == 2 || secondVote.getVote() == won) {
                FisheryMemberData memberData = DBFishery.getInstance().retrieve(secondVote.getGuildId())
                        .getMemberData(userId);
                long prize = memberData.getMemberGear(FisheryGear.SURVEY).getEffect();
                MainLogger.get().info("Survey: Giving {} coins to {} on guild {} ({} coins)", prize, userId, secondVote.getGuildId(), memberData.getCoins());
                memberData.changeValues(0, prize);
                MainLogger.get().info("Survey: Coins of {} on guild {}: {}", userId, secondVote.getGuildId(), memberData.getCoins());
            }
        }
    }

    private static void sendSurveyResult(Locale locale, SurveyQuestion surveyQuestion, SubSlot sub, byte won, int percent) {
        EmbedBuilder eb = EmbedFactory.getEmbedDefault()
                .setTitle(TextManager.getString(locale, Category.FISHERY, "survey_results_message_title"))
                .setDescription(TextManager.getString(locale, Category.FISHERY, "survey_results_message_template", won == 2,
                        surveyQuestion.getQuestion(),
                        surveyQuestion.getAnswers()[0],
                        surveyQuestion.getAnswers()[1],
                        surveyQuestion.getAnswers()[Math.min(1, won)],
                        String.valueOf(percent)
                ));
        sub.sendEmbed(locale, eb);
    }

}
