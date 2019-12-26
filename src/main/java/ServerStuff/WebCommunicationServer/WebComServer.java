package ServerStuff.WebCommunicationServer;

import CommandListeners.onTrackerRequestListener;
import CommandSupporters.Command;
import CommandSupporters.CommandContainer;
import CommandSupporters.CommandManager;
import Constants.Category;
import Constants.Locales;
import General.*;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;

public class WebComServer {

    private static final String EVENT_COMMANDLIST = "command_list";
    private static final String EVENT_SERVERLIST = "server_list";

    public WebComServer(int port) {
        Configuration config = new Configuration();
        config.setHostname("127.0.0.1");
        config.setPort(port);

        final SocketIOServer webComServer = new SocketIOServer(config);

        //When the Lawliet web server connects with
        webComServer.addConnectListener(socketIOClient -> {
            JSONArray mainJSON = new JSONArray();
            HashMap<String, JSONObject> categories = new HashMap<>();

            //Add every command category
            for(String categoryId: Category.LIST) {
                JSONObject categoryJSON = new JSONObject();
                categoryJSON.put("id", categoryId);
                categoryJSON.put("name", getLanguagePack(categoryId));
                categoryJSON.put("commands", new JSONArray());
                categories.put(categoryId, categoryJSON);
                mainJSON.put(categoryJSON);
            }

            //Add every command
            for(Class c: CommandContainer.getInstance().getCommandList()) {
                try {
                    Command command = CommandManager.createCommandByClass(c);
                    String trigger = command.getTrigger();

                    if (!command.isPrivate() && !trigger.equals("help")) {
                        JSONObject commandJSON = new JSONObject();
                        commandJSON.put("trigger", trigger);
                        commandJSON.put("emoji", command.getEmoji());
                        commandJSON.put("title", getLanguagePack(trigger + "_title"));
                        commandJSON.put("desc_short", getLanguagePack(trigger + "_description"));
                        commandJSON.put("desc_long", getLanguagePack(trigger + "_helptext"));
                        commandJSON.put("usage", getLanguagePackSpecs(trigger + "_usage", trigger));
                        commandJSON.put("examples", getLanguagePackSpecs(trigger + "_examples", trigger));
                        commandJSON.put("user_permissions", getCommandPermissions(command));
                        commandJSON.put("nsfw", command.isNsfw());
                        commandJSON.put("requires_user_permissions", command.getUserPermissions() != 0);
                        commandJSON.put("can_be_tracked", command instanceof onTrackerRequestListener);

                        categories.get(command.getCategory()).getJSONArray("commands").put(commandJSON);
                    }
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }

            //Send data
            socketIOClient.sendEvent(EVENT_COMMANDLIST, mainJSON.toString());
        });

        webComServer.addEventListener(EVENT_SERVERLIST, JSONObject.class, (socketIOClient, jsonObject, ackRequest) -> {
            long userId = jsonObject.getLong("user_id");
            Optional<User> userOptional = DiscordApiCollection.getInstance().getUserById(userId);

            JSONObject mainJSON = new JSONObject();
            JSONArray serversArray = new JSONArray();

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                for(Server server: DiscordApiCollection.getInstance().getMutualServers(user)) {
                    JSONObject serverObject = new JSONObject();
                    serverObject
                            .put("server_id", server.getId())
                            .put("name", server.getName());

                    if (server.getIcon().isPresent())
                        serverObject.put("icon", server.getIcon().get().getUrl().toString());

                    serversArray.put(serverObject);
                }
            }

            //Send data
            mainJSON
                    .put("user_id", userId)
                    .put("server_list", serversArray);
            socketIOClient.sendEvent(EVENT_SERVERLIST, mainJSON.toString());
        });

        webComServer.start();
        System.out.println("The WebCom server has been started!");
    }

    private JSONObject getLanguagePack(String key) {
        JSONObject jsonObject = new JSONObject();

        for(String localeString: Locales.LIST) {
            Locale locale = new Locale(localeString);
            try {
                jsonObject.put(locale.getDisplayName(), TextManager.getString(locale, TextManager.COMMANDS, key));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return jsonObject;
    }

    private JSONObject getCommandPermissions(Command command) {
        JSONObject jsonObject = new JSONObject();

        for(String localeString: Locales.LIST) {
            Locale locale = new Locale(localeString);
            String permissionsList = new ListGen<Integer>().getList(PermissionCheck.permissionsToNumberList(command.getUserPermissions()), "", ListGen.SLOT_TYPE_BULLET,
                    i -> {
                        try {
                            return TextManager.getString(locale, TextManager.PERMISSIONS, String.valueOf(i));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return "";
                    }
            );
            jsonObject.put(locale.getDisplayName(), permissionsList);
        }

        return jsonObject;
    }

    private JSONObject getLanguagePackSpecs(String key, String commandTrigger) {
        JSONObject jsonObject = new JSONObject();

        for(String localeString: Locales.LIST) {
            Locale locale = new Locale(localeString);
            try {
                String str = Tools.solveVariablesOfCommandText(TextManager.getString(locale, TextManager.COMMANDS, key));
                if (!str.isEmpty())
                    str = ("\n" + str).replace("\n", "\n• L." + commandTrigger + " ").substring(1);

                jsonObject.put(locale.getDisplayName(), str);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return jsonObject;
    }
}
