package Commands.FisheryCategory;

import CommandListeners.CommandProperties;

import CommandSupporters.Command;
import Constants.FishingCategoryInterface;
import Constants.Permission;
import Constants.FisheryStatus;
import General.EmbedFactory;
import General.Fishing.FishingProfile;
import General.Fishing.FishingSlot;
import General.Mention.MentionTools;
import General.TextManager;
import General.Tools.StringTools;
import MySQL.DBServerOld;
import MySQL.DBUser;
import MySQL.Modules.Server.DBServer;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

@CommandProperties(
        trigger = "gear",
        botPermissions = Permission.USE_EXTERNAL_EMOJIS,
        thumbnail = "http://icons.iconarchive.com/icons/thegirltyler/brand-camp/128/Fishing-Worm-icon.png",
        emoji = "\uD83C\uDFA3",
        executable = true,
        aliases = {"equip", "equipment", "inventory", "level"}
)
public class GearCommand extends Command {

    @Override
    public boolean onMessageReceived(MessageCreateEvent event, String followedString) throws SQLException, IOException, ExecutionException, InterruptedException {
        FisheryStatus status = DBServer.getInstance().getBean(event.getServer().get().getId()).getFisheryStatus();
        if (status == FisheryStatus.ACTIVE) {
            Server server = event.getServer().get();
            Message message = event.getMessage();
            ArrayList<User> list = MentionTools.getUsers(message,followedString).getList();
            if (list.size() > 5) {
                event.getChannel().sendMessage(EmbedFactory.getCommandEmbedError(this,
                        TextManager.getString(getLocale(),TextManager.GENERAL,"too_many_users"))).get();
                return false;
            }
            boolean userMentioned = true;
            boolean userBefore = list.size() > 0;
            list.removeIf(User::isBot);
            if (list.size() == 0) {
                if (userBefore) {
                    event.getChannel().sendMessage(EmbedFactory.getCommandEmbedError(this, TextManager.getString(getLocale(),TextManager.COMMANDS,"acc_nobot"))).get();
                    return false;
                } else {
                    list.add(message.getUserAuthor().get());
                    userMentioned = false;
                }
            }

            ArrayList<Role> buyableRoles = DBServerOld.getPowerPlantRolesFromServer(server);
            for(User user: list) {
                FishingProfile fishingProfile = DBUser.getFishingProfile(server, user);
                EmbedBuilder eb = EmbedFactory.getCommandEmbedStandard(this, getString("desc", StringTools.numToString(getLocale(), fishingProfile.getFish()), StringTools.numToString(getLocale(), fishingProfile.getCoins())));
                if (eb != null) {
                    eb.setTitle("");
                    eb.setAuthor(getString("author", user.getDisplayName(server)), "", user.getAvatar());
                    eb.setThumbnail(user.getAvatar());

                    //Gear
                    StringBuilder gearString = new StringBuilder();
                    for(FishingSlot slot: fishingProfile.getSlots()) {
                        gearString.append(getString("gear_slot",
                                FishingCategoryInterface.PRODUCT_EMOJIS[slot.getId()],
                                TextManager.getString(getLocale(), TextManager.COMMANDS, "buy_product_" + slot.getId() + "_0"),
                                String.valueOf(slot.getLevel())
                        )).append("\n");
                    }
                    eb.addField(getString("gear_title"), gearString.toString(), false);

                    int roleLvl = fishingProfile.getSlots().get(FishingCategoryInterface.ROLE).getLevel();
                    eb.addField(getString("stats_title"), getString("stats_content",
                            StringTools.numToString(getLocale(), fishingProfile.getEffect(FishingCategoryInterface.PER_MESSAGE)),
                            StringTools.numToString(getLocale(), fishingProfile.getEffect(FishingCategoryInterface.PER_DAY)),
                            StringTools.numToString(getLocale(), fishingProfile.getEffect(FishingCategoryInterface.PER_VC)),
                            StringTools.numToString(getLocale(), fishingProfile.getEffect(FishingCategoryInterface.PER_TREASURE)),
                            buyableRoles.size() > 0 && roleLvl > 0 && roleLvl <= buyableRoles.size() ? buyableRoles.get(roleLvl - 1).getMentionTag() : "**-**",
                            StringTools.numToString(getLocale(), fishingProfile.getEffect(FishingCategoryInterface.PER_SURVEY))
                    ), false);

                    if (!userMentioned)
                        eb.setFooter(TextManager.getString(getLocale(), TextManager.GENERAL, "mention_optional"));
                    event.getChannel().sendMessage(eb).get();
                }
            }
            return true;
        } else {
            event.getChannel().sendMessage(EmbedFactory.getCommandEmbedError(this, TextManager.getString(getLocale(), TextManager.GENERAL, "fishing_notactive_description").replace("%PREFIX", getPrefix()), TextManager.getString(getLocale(), TextManager.GENERAL, "fishing_notactive_title")));
            return false;
        }
    }

}