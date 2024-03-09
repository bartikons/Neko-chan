package org.Discoboto;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import org.Discoboto.Command.AudioCommand;
import org.Discoboto.Command.UserCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;

import static org.Discoboto.Command.UserCommand.*;

public class Main {
    public static Cleaner cleaner;
    private static GatewayDiscordClient client;

    static {
        AudioCommand.getInstance();
        UserCommand.getInstance();
    }

    public static GatewayDiscordClient getClient() {
        return client;
    }

    public static void main(String[] args) {
        try {
            client = DiscordClientBuilder.create(prop.getProperty("key")).build()
                    .login()
                    .block();
            cleaner = new Cleaner(client);

            client.getEventDispatcher().on(MessageCreateEvent.class)
                        // 3.1 Message.getContent() is a String
                    .flatMap(Main::getPublisher
                    )
                    .subscribe();

            Objects.requireNonNull(client).onDisconnect().block();
        } finally {
            cleaner.CleanAll();
        }
    }

    private static Mono<Object> getPublisher(MessageCreateEvent event) {
        return Mono.just(getEventMessage(event).getContent())
                .flatMap(content -> Flux.fromIterable(commands.entrySet())
                        // We will be using ! as our "prefix" to any command in the system.
                        .filter(entry -> content.toLowerCase().split(" ")[0].equalsIgnoreCase('!' + entry.getKey()))
                        .publishOn(Schedulers.boundedElastic())
                        .flatMap(entry -> {
                            addToCleaner(event);
                            if (event.getMessage().getAuthor().flatMap(User::getGlobalName).isEmpty()) {
                                return Mono.empty();
                            }
                            if (!event.getMessage().getAuthor().flatMap(User::getGlobalName).get().equals(prop.getProperty("Name"))) {
                                if (isBlocked(event)) {
                                    return Mono.just(reply(event, "Fuck off")).publishOn(Schedulers.boundedElastic()).then(Mono.just(""));
                                }
                                return entry.getValue().execute(event);
                            }
                            return Mono.empty();
                        })
                        .next()
                );
        }
    }