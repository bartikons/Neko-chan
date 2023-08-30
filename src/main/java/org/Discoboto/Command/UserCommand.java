package org.Discoboto.Command;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import org.Discoboto.Audio.GuildAudioManager;
import org.Discoboto.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.Discoboto.Main.cleaner;

public class UserCommand extends BaseCommand {

    static UserCommand instance;

    static {
        try {

            commands.put("ping", event -> getEventMessage(event).getChannel()
                    .flatMap(channel -> reply(event, "Pong!"))
                    .then());

            commands.put("join", event -> join(event)
                    .then());

            commands.put("messageToAuthor", event -> {

                cleaner.add(event.getMessage().getChannel().block().getId(), getEventMessage(event).getId());
                Main.getClient()
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
            commands.put("cleanChannel",
                    event -> event.getMessage()
                            .getChannel()
                            .flatMap(channel ->
                                    channel.getMessagesBefore(
                                                    channel.getLastMessageId().get())
                                            .parallel()
                                            .doOnNext(message -> cleaner.add(channel.getId(), message.getId()))
                                            .then()
                                            .doOnNext(e -> reply(event, "It ain't much but it's honest work nya~"))));
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

    public UserCommand() {

    }

    public static UserCommand getInstance() {
        if (instance == null) {
            instance = new UserCommand();
        }
        return instance;
    }


    private static Logger getLogger() {
        return LogManager.getLogger(UserCommand.class);
    }
}
