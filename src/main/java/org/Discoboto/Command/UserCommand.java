package org.Discoboto.Command;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.object.entity.User;
import org.Discoboto.Audio.GuildAudioManager;
import org.Discoboto.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.Discoboto.Main.cleaner;

public class UserCommand extends baseCommand {


    public static final Properties prop = new Properties();

    static {
        try {
            try (InputStream input = Main.class.getClassLoader().getResourceAsStream("yt.properties")) {
                // load a properties file
                prop.load(input);
            } catch (IOException e) {
                LogManager.getLogger(Main.class).error("Exception: ", e);
            }
            try {
                try {
                    Arrays.stream(prop.getProperty("blocked").split(","))
                            .forEach(guildBlockedUser -> {
                                String[] helper = guildBlockedUser.split(":");
                                List<Snowflake> listOfBlocked = blockUser.computeIfAbsent(Snowflake.of(helper[0]), k -> new ArrayList<>());
                                listOfBlocked.add(Snowflake.of(helper[1]));
                            });
                } catch (Exception e) {
                    getLogger().error("Exception: ", e);
                }

            } catch (Exception ignored) {
                blockUser = new ConcurrentHashMap<>();
            }

            commands.put("ping", event -> getEventMessage(event).getChannel()
                    .flatMap(channel -> reply(event, "Pong!"))
                    .then());

            commands.put("join", event -> join(event)
                    .then());

            commands.put("messageToAuthor", event -> {

                cleaner.add(event.getMessage().getChannel().block().getId(), getEventMessage(event).getId());
                DiscordClientBuilder.create(prop.getProperty("key")).build()
                        .login()
                        .block()
                        .getUserById(Snowflake.of(prop.getProperty("Author"))).block()
                        .getPrivateChannel().block()
                        .createMessage(
                                event.getMessage()
                                        .getAuthor()
                                        .flatMap(User::getGlobalName)
                                        .get() + ": " +
                                        event.getMessage()
                                                .getContent().
                                                replace("!messageToAuthor", "")).block();
                return Mono.empty();
            });
            commands.put("help", event -> {
                GuildAudioManager guildAudioManager = getAudioManager(getGuildSnowflake(event));
                guildAudioManager.getPlayer().setPaused(false);
                List<String> list = new ArrayList<>();
                commands.forEach((key, value) -> list.add(key));
                return reply(event, list.toString()).then();
            });

        } catch (Exception e) {
            getLogger().error("Exception: ", e);
        }
    }

    private static Logger getLogger() {
        return LogManager.getLogger(UserCommand.class);
    }
}
