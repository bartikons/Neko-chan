package org.Discoboto.Command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public interface InterfaceCommand {
    Mono<Void> execute(MessageCreateEvent event);

}
