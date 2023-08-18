package org.Discoboto.Command;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.voice.VoiceConnection;
import org.Discoboto.Audio.AudioTrackScheduler;
import org.Discoboto.Audio.GuildAudioManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.Discoboto.Main.cleaner;

public class baseCommand {

    public static final Map<String, InterfaceCommand> commands = new ConcurrentHashMap<>();
    static Map<Snowflake, List<Snowflake>> blockUser = new ConcurrentHashMap<>();

    protected static Snowflake getGuildSnowflake(MessageCreateEvent event) {
        if (event.getGuildId().isPresent()) {
            return event.getGuildId().get();
        } else return null;
    }

    protected static MessageChannel getChannel(MessageCreateEvent event) {
        return getEventMessage(event).getChannel().block();
    }

    protected static void skip(MessageCreateEvent event) {
        int skip = 1;
        try {
            skip = Integer.parseInt(getEventMessage(event).getContent().split(" ")[1]);
        } catch (Exception ignored) {
        }
        for (int i = 0; i < skip; i++) {
            getScheduler(getGuildSnowflake(event)).skip();
        }
    }

    public static Mono<Void> reply(MessageCreateEvent event, String message) {
        MessageChannel channel = getChannel(event);
        Message thisMessage = channel.createMessage(message).block();
        addToCleaner(Objects.requireNonNull(thisMessage));
        return Mono.empty();
    }

    public static Message getEventMessage(MessageCreateEvent event) {
        return event.getMessage();
    }

    protected static Mono<VoiceConnection> join(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                // join returns a VoiceConnection which would be required if we were
                // adding disconnection features, but for now we are just ignoring it.
                .flatMap(channel ->
                        channel.join().withProvider(getAudioManager(channel.getGuildId()).getProvider())
                );
    }

    protected static Mono<Void> getQueue(Snowflake snowflake, MessageCreateEvent channel) {
        try {
            GuildAudioManager guildAudioManager = getAudioManager(snowflake);
            AudioTrack playingTrack = guildAudioManager.getPlayer()
                    .getPlayingTrack();
            if (playingTrack != null) {
                return reply(channel, "Currently Playing " +
                        playingTrack
                                .getInfo().title +
                        "\n" +
                        "In Queue: \n" +
                        guildAudioManager.getScheduler()
                                .getQueueString());
            }
            return reply(channel, "Empty nya~~");

        } catch (Exception e) {
            getLogger().error("Exception: ", e);
            return reply(channel, "BUG~!");
        }
    }

    private static Logger getLogger() {
        return LogManager.getLogger(baseCommand.class);
    }


    protected static AudioTrackScheduler getScheduler(Snowflake event) {
        return getAudioManager(event).getScheduler();
    }

    protected static GuildAudioManager getAudioManager(Snowflake event) {
        return GuildAudioManager.of(event);
    }

    public static void addToCleaner(MessageCreateEvent event) {

        Message message = getEventMessage(event);

        if (message.getGuild().block() != null) {
            addToCleaner(message);
        }
    }

    protected static void addToCleaner(Message message) {
        cleaner.add(message.getChannelId(), message.getId());
    }

    public static boolean isBlocked(MessageCreateEvent event) {
        try {
            if (getEventMessage(event).getAuthor().isEmpty()) {
                return true;
            }
            Snowflake snowflake = getGuildSnowflake(event);
            blockUser.computeIfAbsent(snowflake, k -> new ArrayList<>());
            return blockUser.get(snowflake).contains(getEventMessage(event).getAuthor().get().getId());
        } catch (NullPointerException e) {
            return false;
        } catch (Exception e) {
            getLogger().error("Exception: ", e);
            return false;
        }
    }


}
