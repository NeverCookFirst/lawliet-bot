package General.Tracker;

import CommandListeners.onTrackerRequestListener;
import CommandSupporters.Command;
import CommandSupporters.CommandManager;
import Commands.ManagementCategory.TrackerCommand;
import Constants.Permission;
import General.DiscordApiCollection;
import General.PermissionCheckRuntime;
import MySQL.DBBot;
import MySQL.Modules.Server.DBServer;
import MySQL.Modules.Server.ServerBean;
import org.javacord.api.entity.channel.ServerTextChannel;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class TrackerManager {

    private static ArrayList<TrackerConnection> trackerConnections = new ArrayList<>();

    public static void manageTracker(TrackerData trackerData) throws InstantiationException, IllegalAccessException, InterruptedException, ExecutionException {
        if (trackerData == null) {
            return;
        }

        ServerBean serverBean = DBServer.getInstance().getBean(trackerData.getServerId());

        Locale locale = serverBean.getLocale();
        String prefix = serverBean.getPrefix();
        Command command = CommandManager.createCommandByTrigger(trackerData.getCommand(), locale, prefix);

        while (true) {
            try {
                Duration duration = Duration.between(Instant.now(), trackerData.getInstant());
                Thread.sleep(Math.max(5 * 60 * 1000, duration.getSeconds() * 1000 + duration.getNano() / 1000000));

                Optional<ServerTextChannel> channelOptional = trackerData.getChannel();
                if (!channelOptional.isPresent()) return;

                while(true) {
                    if (!PermissionCheckRuntime.getInstance().botHasPermission(locale, TrackerCommand.class, channelOptional.get(),  Permission.READ_MESSAGES | Permission.SEND_MESSAGES | Permission.EMBED_LINKS)) {
                        Thread.sleep(60 * 60 * 1000);
                    } else break;
                }

                TrackerData oldTrackerData = trackerData;
                trackerData = ((onTrackerRequestListener) command).onTrackerRequest(trackerData);
                if (trackerData != null) {
                    if (trackerData.isSaveChanges()) DBBot.saveTracker(trackerData);
                    else {
                        trackerData.setInstant(Instant.now().plusSeconds(5 * 60));
                        trackerData.setSaveChanges(true);
                    }
                } else {
                    trackerConnections.remove(getTrackerConnection(oldTrackerData));
                    return;
                }
            } catch (Throwable e) {
                if (e instanceof InterruptedException) return;
                e.printStackTrace();
                Thread.sleep(5 * 60 * 1000);
            }
        }
    }

    public static void startTracker(TrackerData trackerData) {
        Thread t = new Thread(() -> {
            try {
                TrackerManager.manageTracker(trackerData);
            } catch (InstantiationException | InterruptedException | IllegalAccessException | ExecutionException e) {
                e.printStackTrace();
            }
        });
        t.setName("tracker_" + trackerData.getCommand());
        t.start();
        trackerConnections.add(new TrackerConnection(trackerData, t));
    }

    public static void stopTracker(TrackerData trackerData, boolean removeFromDatabase) throws SQLException {
        TrackerConnection trackerConnectionRemove = getTrackerConnection(trackerData);
        if (trackerConnectionRemove != null) {
            trackerConnectionRemove.getThread().interrupt();
            trackerConnections.remove(trackerConnectionRemove);
            if (removeFromDatabase) DBBot.removeTracker(trackerData);
        }
    }

    public static void stopShard(int shardId) {
        for(TrackerConnection trackerConnection: new ArrayList<>(trackerConnections)) {
            if (DiscordApiCollection.getInstance().getResponsibleShard(trackerConnection.getTrackerData().getServerId()) == shardId) {
                try {
                    stopTracker(trackerConnection.getTrackerData(), false);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void interruptTracker(TrackerData trackerData) {
        TrackerConnection trackerConnectionRemove = getTrackerConnection(trackerData);
        if (trackerConnectionRemove != null) {
            trackerConnectionRemove.getThread().interrupt();
        }
    }

    private static TrackerConnection getTrackerConnection(TrackerData trackerData) {
        if (trackerData == null) return null;

        for(TrackerConnection trackerConnection: new ArrayList<>(trackerConnections)) {
            TrackerData trackerData2 = trackerConnection.getTrackerData();
            if (trackerData.getServerId() == trackerData2.getServerId() && trackerData.getChannelId() == trackerData2.getChannelId() && trackerData.getCommand().equals(trackerData2.getCommand())) {
                return trackerConnection;
            }
        }
        return null;
    }

    public static int getSize() {
        return trackerConnections.size();
    }
}
