package org.Discoboto.Command;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import discord4j.voice.VoiceConnection;
import org.Discoboto.Audio.AudioTrackScheduler;
import org.Discoboto.Audio.GuildAudioManager;
import org.Discoboto.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.Discoboto.Main.cleaner;

public class BaseCommand {


    public static final Map<String, InterfaceCommand> commands = new ConcurrentHashMap<>();
    static Map<Snowflake, List<Snowflake>> blockUser = new ConcurrentHashMap<>();

    public static final Properties prop = new Properties();
    static List<String> skipAll = Arrays.stream("all,queue,hehe,baka,shit,neko,plz".split(",")).toList();

    protected static Snowflake getGuildSnowflake(MessageCreateEvent event) {
        if (event.getGuildId().isPresent()) {
            return event.getGuildId().get();
        } else return null;
    }

    protected static MessageChannel getChannel(MessageCreateEvent event) {
        return getEventMessage(event).getChannel().block();
    }

    static {

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
    }

    protected static void skip(MessageCreateEvent event) {
        int skip = 1;
        AudioTrackScheduler scheduler = getScheduler(getGuildSnowflake(event));
        String skipero = getEventMessage(event).getContent().split(" ")[1];
        boolean skipeAll = false;
        try {
            if (skipAll.contains(skipero)) {
                skip = scheduler.getQueue().size();
                skipeAll = true;
            } else {
                skip = Integer.parseInt(skipero);
            }
        } catch (Exception ignored) {
        }
        scheduler.setSkiping(true);
        for (int i = 0; i < skip; i++) {
            scheduler.skip();
        }
        if (skipeAll) {
            scheduler.play(null, true);
            scheduler.getQueue().removeIf(e -> true);
        }

        scheduler.setSkiping(false);

    }

    public static Mono<Message> reply(MessageCreateEvent event, String message) {
        return reply(event, message, true);
    }

    public static Mono<Message> reply(MessageCreateEvent event, List<EmbedCreateFields.Field> message) {
        return reply(event, message, true);
    }

    public static Mono<Message> reply(MessageCreateEvent event, String message, boolean deleteMessage) {
        MessageChannel channel = getChannel(event);
        Message thisMessage = channel.createMessage(message).block();
        if (deleteMessage) {
            addToCleaner(Objects.requireNonNull(thisMessage));
        }
        return Mono.just(thisMessage);
    }

    public static Mono<Message> reply(MessageCreateEvent event, List<EmbedCreateFields.Field> message, boolean deleteMessage) {

        MessageChannel channel = getChannel(event);

        Message thisMessage = channel
                .createMessage(EmbedCreateSpec
                        .builder()
                        .color(Color.PINK)
                        .addAllFields(message)
                        .build())
                .block();
        if (deleteMessage) {
            addToCleaner(Objects.requireNonNull(thisMessage));
        }
        return Mono.just(thisMessage);
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

    protected static Mono<Message> getQueue(Snowflake snowflake, MessageCreateEvent channel) {
        try {
            GuildAudioManager guildAudioManager = getAudioManager(snowflake);
            AudioTrack playingTrack = guildAudioManager.getPlayer()
                    .getPlayingTrack();
            if (playingTrack != null) {
                List<EmbedCreateFields.Field> queue = guildAudioManager.getScheduler().getQueueEmbed();
                if (!queue.isEmpty())
                    return reply(channel, queue);
            }
            return reply(channel, "Empty nya~~");

        } catch (Exception e) {
            getLogger().error("Exception: ", e);
            return reply(channel, "BUG~!");
        }
    }

    private static Logger getLogger() {
        return LogManager.getLogger(BaseCommand.class);
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
