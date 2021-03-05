package core.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import constants.LetterEmojis;
import core.utils.StringUtil;
import modules.VoteInfo;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class VoteCache {

    private static final VoteCache ourInstance = new VoteCache();

    public static VoteCache getInstance() {
        return ourInstance;
    }

    private VoteCache() {
    }

    private final Cache<Long, VoteInfo> voteCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public void put(long messageId, VoteInfo voteInfo) {
        voteCache.put(messageId, voteInfo);
    }

    public Optional<VoteInfo> get(Message message, long userId, String emoji, boolean add) {
        VoteInfo voteInfo = voteCache.getIfPresent(message.getIdLong());

        if (voteInfo != null) {
            int i = -1;
            for (int j = 0; j < voteInfo.getSize(); j++) {
                if (emoji.equals(LetterEmojis.LETTERS[j])) {
                    i = j;
                    break;
                }
            }

            if ((i < 0 && !emoji.equals("❌")) || !voteInfo.isActive())
                return Optional.empty();

            if (i >= 0) {
                if (add)
                    voteInfo.addVote(i, userId);
                else
                    voteInfo.removeVote(i, userId);
            }
        } else {
            voteInfo = extractVoteInfoFromMessage(message);
            voteCache.put(message.getIdLong(), voteInfo);
        }

        return Optional.of(voteInfo);
    }

    private VoteInfo extractVoteInfoFromMessage(Message message) {
        ArrayList<HashSet<Long>> votes = new ArrayList<>();

        MessageEmbed embed = message.getEmbeds().get(0);
        List<MessageEmbed.Field> field = embed.getFields();

        String topic = field.get(0).getValue();
        String choiceString = field.get(1).getValue();
        String[] choices = new String[choiceString.split("\n").length];

        for (int i = 0; i < choices.length; i++) {
            String choiceLine = choiceString.split("\n")[i];
            choices[i] = choiceLine.split("\\|")[1];
        }

        for (int i = 0; i < choices.length; i++) {
            HashSet<Long> voteUsers = new HashSet<>();

            for (MessageReaction reaction : message.getReactions()) {
                if (reaction.getReactionEmote().getAsReactionCode().equals(LetterEmojis.LETTERS[i])) {
                    reaction.retrieveUsers().forEach(user -> {
                        if (!user.isBot()) {
                            voteUsers.add(user.getIdLong());
                        }
                    });

                    break;
                }
            }

            votes.add(voteUsers);
        }

        AtomicLong creatorId = new AtomicLong(-1);
        if (embed.getFooter() != null) {
            Optional.ofNullable(embed.getFooter().getText()).ifPresent(footerString -> {
                if (footerString.contains(" ")) {
                    String creatorIdString = footerString.split(" ")[0];
                    if (StringUtil.stringIsLong(creatorIdString)) {
                        creatorId.set(Long.parseLong(creatorIdString));
                    }
                }
            });
        }

        return new VoteInfo(topic, choices, votes, creatorId.get());
    }

}
