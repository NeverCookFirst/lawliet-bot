package DiscordListener;

import CommandListeners.*;
import CommandSupporters.Command;
import CommandSupporters.CommandContainer;
import Constants.Settings;
import Core.ExceptionHandler;
import MySQL.Modules.Server.DBServer;
import MySQL.Modules.Server.ServerBean;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.event.message.reaction.ReactionAddEvent;
import org.javacord.api.event.message.reaction.SingleReactionEvent;

import java.util.concurrent.ExecutionException;

public class ReactionAddListener {

    public static boolean manageReactionCommands(SingleReactionEvent event) {
        for (Command command : CommandContainer.getInstance().getReactionInstances()) {
            if (event.getMessageId() == command.getReactionMessageID()) {
                if (event.getUser().getId() == command.getReactionUserID()) {
                    //RunningCommandManager.getInstance().canUserRunCommand(event.getUser().getId(), event.getApi().getCurrentShard());

                    try {
                        if (command instanceof OnReactionAddListener) command.onReactionAddSuper(event);
                        if (command instanceof OnNavigationListener) command.onNavigationReactionSuper(event);
                    } catch (Throwable e) {
                        ExceptionHandler.handleException(e, command.getLocale(), event.getMessage().get().getChannel());
                    }
                } else {
                    if (event.getChannel().canYouRemoveReactionsOfOthers() && event.getReaction().isPresent()) event.getReaction().get().removeUser(event.getUser());
                }

                return true;
            }
        }

        return false;
    }

    public void onReactionAdd(ReactionAddEvent event) {
        if (event.getUser().isBot()) return;

        //Commands
        if (manageReactionCommands(event) || !event.getServer().isPresent()) return;

        //Download Message
        Message message = null;
        try {
            if (event.getMessage().isPresent()) message = event.getMessage().get();
            else message = event.getChannel().getMessageById(event.getMessageId()).get();
        } catch (InterruptedException | ExecutionException e) {
            //Ignore
            return;
        }

        //Static Reactions
        try {
            if (message.getAuthor().isYourself() && message.getEmbeds().size() > 0) {
                Embed embed = message.getEmbeds().get(0);
                if (embed.getTitle().isPresent() && !embed.getAuthor().isPresent()) {
                    String title = embed.getTitle().get();
                    for (OnReactionAddStaticListener command : CommandContainer.getInstance().getStaticReactionAddCommands()) {
                        if (title.toLowerCase().startsWith(command.getTitleStartIndicator().toLowerCase()) && title.endsWith(Settings.EMPTY_EMOJI)) {
                            ServerBean serverBean = DBServer.getInstance().getBean(event.getServer().get().getId());
                            ((Command) command).setLocale(serverBean.getLocale());
                            ((Command) command).setPrefix(serverBean.getPrefix());
                            command.onReactionAddStatic(message, event);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

}
