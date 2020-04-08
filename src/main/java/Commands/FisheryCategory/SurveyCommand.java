package Commands.FisheryCategory;

import CommandListeners.*;
import CommandSupporters.Command;
import Constants.LetterEmojis;
import Constants.LogStatus;
import Constants.Permission;
import Constants.Settings;
import General.*;
import General.Survey.SurveyManager;
import General.Survey.SurveyQuestion;
import General.Tools.StringTools;
import General.Tools.TimeTools;
import General.Tracker.TrackerData;
import MySQL.Modules.Survey.DBSurvey;
import MySQL.Modules.Survey.SurveyBean;
import MySQL.Modules.Survey.SurveyFirstVote;
import MySQL.Modules.Survey.SurveySecondVote;
import javafx.util.Pair;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.Reaction;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.reaction.ReactionAddEvent;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;

@CommandProperties(
    trigger = "survey",
    botPermissions = Permission.MANAGE_MESSAGES,
    thumbnail = "http://icons.iconarchive.com/icons/iconarchive/blue-election/128/Election-Polling-Box-icon.png",
    emoji = "✅",
    executable = true
)
public class SurveyCommand extends Command implements onReactionAddStaticListener, onTrackerRequestListener {

    private static long lastAccess = 0;

    @Override
    public boolean onMessageReceived(MessageCreateEvent event, String followedString) throws Throwable {
        sendMessages(event.getServerTextChannel().get(), false);
        return true;
    }

    @Override
    public void onReactionAddStatic(Message message, ReactionAddEvent event) throws Throwable {
        if (!PermissionCheckRuntime.getInstance().botHasPermission(getLocale(), getClass(), event.getServerTextChannel().get(), Permission.MANAGE_MESSAGES)) return;
        event.removeReaction().get();

        for(Reaction reaction: message.getReactions()) {
            boolean correctEmoji = false;
            for (int i = 0; i < 2; i++) {
                if (reaction.getEmoji().isUnicodeEmoji() && (reaction.getEmoji().asUnicodeEmoji().get().equalsIgnoreCase(LetterEmojis.LETTERS[i]) || reaction.getEmoji().asUnicodeEmoji().get().equalsIgnoreCase(LetterEmojis.RED_LETTERS[i]))) {
                    correctEmoji = true;
                    break;
                }
            }

            if (!correctEmoji) reaction.remove().get();
            else if (reaction.getCount() > 1) {
                for(User user: reaction.getUsers().get()) {
                    if (!user.isYourself()) reaction.removeUser(user).get();
                }
            }
        }

        if (event.getEmoji().isUnicodeEmoji()) {
            for (byte i = 0; i < 2; i++) {
                int hit = 0;
                if (event.getEmoji().asUnicodeEmoji().get().equalsIgnoreCase(LetterEmojis.LETTERS[i])) hit = 1;
                if (event.getEmoji().asUnicodeEmoji().get().equalsIgnoreCase(LetterEmojis.RED_LETTERS[i])) hit = 2;

                if (hit > 0) {
                    SurveyBean surveyBean = DBSurvey.getInstance().getCurrentSurvey();

                    if (message.getCreationTimestamp().isAfter(surveyBean.getStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant())) {
                        if (hit == 1) surveyBean.getFirstVotes().put(event.getUser().getId(), new SurveyFirstVote(event.getUser().getId(), i));
                        else {
                            if (surveyBean.getFirstVotes().containsKey(event.getUser().getId()))
                                surveyBean.getSecondVotes().put(
                                        new Pair<>(event.getServer().get().getId(), event.getUser().getId()),
                                        new SurveySecondVote(event.getServer().get().getId(), event.getUser().getId(), i)
                                );
                            else {
                                EmbedBuilder eb = EmbedFactory.getCommandEmbedError(this, getString("vote_error"), TextManager.getString(getLocale(), TextManager.GENERAL, "rejected"));
                                event.getUser().sendMessage(eb);
                                return;
                            }
                        }

                        SurveyQuestion surveyQuestion = SurveyManager.getSurveyQuestionAndAnswers(surveyBean.getSurveyId(), getLocale());;
                        String[] voteStrings = new String[2];

                        voteStrings[0] = "• " + surveyQuestion.getAnswers()[surveyBean.getFirstVotes().get(event.getUser().getId()).getVote()];

                        List<SurveySecondVote> surveySecondVotes = SurveyManager.getSurveySecondVotesForUserId(surveyBean, event.getUser().getId());

                        if (surveySecondVotes.size() == 0) voteStrings[1] = TextManager.getString(getLocale(), TextManager.GENERAL, "notset");
                        else voteStrings[1] = "";

                        for (SurveySecondVote surveySecondVote: surveySecondVotes) {
                            voteStrings[1] += "• " + surveyQuestion.getAnswers()[surveySecondVote.getVote()] + " (" + DiscordApiCollection.getInstance().getServerById(surveySecondVote.getServerId()).get().getName() + ")\n";
                        }

                        EmbedBuilder eb = EmbedFactory.getCommandEmbedSuccess(this, getString("vote_description") + "\n" + Settings.EMPTY_EMOJI)
                                .addField(surveyQuestion.getQuestion(), voteStrings[0])
                                .addField(getString("majority"), voteStrings[1]);

                        event.getUser().sendMessage(eb);
                    }
                    break;
                }
            }
        }
    }

    private Message sendMessages(ServerTextChannel channel, boolean tracker) throws InterruptedException, IOException, SQLException, ExecutionException {
        while(lastAccess != 0 && System.currentTimeMillis() <= lastAccess + 1000 * 60) {
            Thread.sleep(1000);
        }

        SurveyBean currentSurvey = DBSurvey.getInstance().getCurrentSurvey();
        SurveyBean lastSurvey = DBSurvey.getInstance().getBean(currentSurvey.getSurveyId() - 1);

        lastAccess = System.currentTimeMillis();

        //Results Message
        channel.sendMessage(getResultsEmbed(lastSurvey));

        //Survey Message
        EmbedBuilder eb = getSurveyEmbed(currentSurvey);
        if (!tracker) EmbedFactory.addLog(eb, LogStatus.WARNING, TextManager.getString(getLocale(), TextManager.GENERAL, "tracker", getPrefix(), getTrigger()));
        Message message = channel.sendMessage(eb).get();

        for(int i = 0; i < 2; i++) {
            for(int j = 0; j < 2; j++) {
                if (i == 0) message.addReaction(LetterEmojis.LETTERS[j]).get();
                else message.addReaction(LetterEmojis.RED_LETTERS[j]).get();
            }
        }

        lastAccess = 0;

        return message;
    }

    private EmbedBuilder getResultsEmbed(SurveyBean lastSurvey) throws IOException {
        SurveyQuestion surveyQuestion = SurveyManager.getSurveyQuestionAndAnswers(lastSurvey.getSurveyId(), getLocale());

        EmbedBuilder eb = EmbedFactory.getCommandEmbedStandard(this, "", getString("results_title"));
        eb.addField(getString("results_question"), surveyQuestion.getQuestion(), false);

        StringBuilder answerString = new StringBuilder();
        for(int i = 0; i < 2; i++) answerString.append(LetterEmojis.LETTERS[i]).append(" | ").append(surveyQuestion.getAnswers()[i]).append("\n");
        eb.addField(getString("results_answers"), answerString.toString(), false);

        long firstVotesTotal = SurveyManager.getFirstVoteNumbers(lastSurvey);
        long[] firstVotes = new long[2];
        for(byte i = 0; i < 2; i++) firstVotes[i] = SurveyManager.getFirstVoteNumbers(lastSurvey, i);
        double[] firstVotesRelative = new double[2];
        for(byte i = 0; i < 2; i++) firstVotesRelative[i] = firstVotes[i] / (double)firstVotesTotal;

        StringBuilder resultString = new StringBuilder();
        for(int i = 0; i < 2; i++) {
            resultString.append(
                    getString("results_template",
                            LetterEmojis.LETTERS[i],
                            StringTools.getBar(firstVotesRelative[i], 12),
                            String.valueOf(firstVotes[i]),
                            String.valueOf(Math.round(firstVotesRelative[i] * 100))
                    )
            ).append("\n");
        }

        eb.addField(getString("results_results", firstVotesTotal != 1, String.valueOf(firstVotesTotal)), resultString.toString(), false);
        eb.addField(Settings.EMPTY_EMOJI, getString("results_won", SurveyManager.getWon(lastSurvey), surveyQuestion.getAnswers()[0], surveyQuestion.getAnswers()[1]).toUpperCase());

        return eb;
    }

    private EmbedBuilder getSurveyEmbed(SurveyBean surveyBean) throws IOException {
        SurveyQuestion surveyQuestion = SurveyManager.getSurveyQuestionAndAnswers(surveyBean.getSurveyId(), getLocale());
        EmbedBuilder eb = EmbedFactory.getCommandEmbedStandard(this, getString("sdescription"), getString("title") + Settings.EMPTY_EMOJI);

        StringBuilder personalString = new StringBuilder();
        StringBuilder majorityString = new StringBuilder();
        for(int i = 0; i < 2; i++) {
            personalString.append(LetterEmojis.LETTERS[i]).append(" | ").append(surveyQuestion.getAnswers()[i]).append("\n");
            majorityString.append(LetterEmojis.RED_LETTERS[i]).append(" | ").append(surveyQuestion.getAnswers()[i]).append("\n");
        }
        eb.addField(surveyQuestion.getQuestion(), personalString.toString(), false);
        eb.addField(getString("majority"), majorityString.toString(), false);

        return eb;
    }

    @Override
    public String getTitleStartIndicator() {
        return getEmoji();
    }

    @Override
    public TrackerData onTrackerRequest(TrackerData trackerData) throws Throwable {
        while(trackerData.getArg() != null && DBSurvey.getInstance().getCurrentSurveyId() <= Integer.parseInt(trackerData.getArg())) {
            Thread.sleep(60 * 1000);
        }

        ServerTextChannel channel = trackerData.getChannel().get();
        if (!PermissionCheckRuntime.getInstance().botHasPermission(getLocale(), getClass(), channel, Permission.ADD_REACTIONS)) {
            trackerData.setSaveChanges(false);
            return trackerData;
        }
        trackerData.deletePreviousMessage();
        trackerData.setMessageDelete(sendMessages(channel, true));
        Instant nextInstant = trackerData.getInstant();
        do {
            nextInstant = TimeTools.setInstantToNextDay(nextInstant);
        } while(!TimeTools.instantHasWeekday(nextInstant, Calendar.MONDAY) && !TimeTools.instantHasWeekday(nextInstant, Calendar.THURSDAY));

        trackerData.setInstant(nextInstant.plusSeconds(5 * 60));
        trackerData.setArg(String.valueOf(DBSurvey.getInstance().getCurrentSurvey()));
        return trackerData;
    }

    @Override
    public boolean trackerUsesKey() {
        return false;
    }

}
