package org.Discoboto;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import org.apache.logging.log4j.LogManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static org.Discoboto.Command.UserCommand.*;

public class Main {
    public static Cleaner cleaner;

    protected static GatewayDiscordClient client;


    static {

    }

    public static void main(String[] args) {
        try {
            client = DiscordClientBuilder.create(prop.getProperty("key")).build()
                    .login()
                    .block();

            cleaner = new Cleaner(client);
            cleaner.start();

            try {
                Objects.requireNonNull(client).getEventDispatcher().on(MessageCreateEvent.class)
                        // 3.1 Message.getContent() is a String
                        .flatMap(event -> Mono.just(getEventMessage(event).getContent())
                                .flatMap(content -> {
                                            try {
                                                return Flux.fromIterable(commands.entrySet())
                                                        // We will be using ! as our "prefix" to any command in the system.
                                                        .filter(entry -> content.toLowerCase().split(" ")[0].equalsIgnoreCase('!' + entry.getKey()))
                                                        .flatMap(entry -> {
                                                            addToCleaner(event);
                                                            if (event.getMessage().getAuthor().flatMap(User::getGlobalName).isEmpty()) {
                                                                return Mono.empty();
                                                            }
                                                            if (!event.getMessage().getAuthor().flatMap(User::getGlobalName).get().equals(prop.getProperty("Name"))) {
                                                                if (isBlocked(event)) {
                                                                    return Objects.requireNonNull(Mono.just(reply(event, "Fuck off")).block()).then(Mono.just(""));
                                                                }
                                                                return entry.getValue().execute(event);
                                                            }
                                                            return Mono.empty();
                                                        })
                                                        .next();
                                            } catch (Exception e) {
                                                LogManager.getLogger(Main.class).error("Exception: ", e);
                                                return Mono.empty();
                                            }
                                        }
                                )
                        )
                        .subscribe();
            } catch (Exception e) {
                LogManager.getLogger(Main.class).error("Exception: ", e);
            }

            Objects.requireNonNull(client).onDisconnect().block();
        } finally {
            cleaner.CleanAll();
        }
    }


}
