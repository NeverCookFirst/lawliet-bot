package events.discordevents.guildmessagereactionadd;

import commands.Command;
import commands.CommandManager;
import commands.listeners.OnStaticReactionAddListener;
import core.CustomObservableMap;
import core.MemberCacheController;
import core.cache.MessageCache;
import core.utils.BotPermissionUtil;
import events.discordevents.DiscordEvent;
import events.discordevents.eventtypeabstracts.GuildMessageReactionAddAbstract;
import mysql.hibernate.EntityManagerWrapper;
import mysql.hibernate.entity.GuildEntity;
import mysql.modules.staticreactionmessages.DBStaticReactionMessages;
import mysql.modules.staticreactionmessages.StaticReactionMessageData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import java.util.concurrent.ExecutionException;

@DiscordEvent
public class GuildMessageReactionAddCommandsStatic extends GuildMessageReactionAddAbstract {

    @Override
    public boolean onGuildMessageReactionAdd(MessageReactionAddEvent event, EntityManagerWrapper entityManager) throws Throwable {
        if (!BotPermissionUtil.canReadHistory(event.getGuildChannel(), Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)) {
            return true;
        }

        CustomObservableMap<Long, StaticReactionMessageData> map = DBStaticReactionMessages.getInstance()
                .retrieve(event.getGuild().getIdLong());
        StaticReactionMessageData messageData = map.get(event.getMessageIdLong());

        if (messageData != null) {
            GuildEntity guildEntity = entityManager.findGuildEntity(event.getGuild().getIdLong());
            Command command = CommandManager.createCommandByTrigger(messageData.getCommand(), guildEntity.getLocale(), guildEntity.getPrefix()).get();
            if (command instanceof OnStaticReactionAddListener) {
                Message message;
                try {
                    message = MessageCache.retrieveMessage(event.getGuildChannel(), event.getMessageIdLong()).get();
                } catch (InterruptedException | ExecutionException e) {
                    //Ignore
                    return true;
                }

                if (map.containsKey(event.getMessageIdLong())) {
                    if (command.getCommandProperties().requiresFullMemberCache()) {
                        MemberCacheController.getInstance().loadMembersFull(event.getGuild()).get();
                    } else {
                        MemberCacheController.getInstance().loadMember(event.getGuild(), event.getUserIdLong()).get();
                    }
                    command.setGuildEntity(guildEntity);
                    command.setAtomicGuild(event.getGuild());
                    if (event.getMember() != null && !event.getMember().getUser().isBot()) {
                        ((OnStaticReactionAddListener) command).onStaticReactionAdd(message, event);
                    }
                }
            }
        }

        return true;
    }

}
