package org.Discoboto;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateMono;
import discord4j.voice.VoiceConnection;
import org.Discoboto.Object.Command;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

public class Main {
    private static final Map<String, Command> commands = new HashMap<>();

    public static final AudioPlayerManager PLAYER_MANAGER;

    static {
        PLAYER_MANAGER = new DefaultAudioPlayerManager();
        // This is an optimization strategy that Discord4J can utilize to minimize allocations
        // PLAYER_MANAGER.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
        AudioSourceManagers.registerLocalSource(PLAYER_MANAGER);
    }

    static {
        commands.put("ping", event -> event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());

        commands.put("join", event -> join(event)
                .then());
        commands.put("play", event -> Mono.justOrEmpty(event.getMessage().getContent())
                .map(content -> Arrays.asList(content.split(" ")))
                .doOnNext(command -> join(event).then(
                                loadToTrack(event.getGuildId().get(), command))
                        .then(event.getMessage()
                                .getChannel()
                                .flatMap(
                                        channel -> getQueue(event, channel))))
                .then());
        commands.put("leave", event -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getGuild)
                .flatMap(Guild::getVoiceConnection)
                // join returns a VoiceConnection which would be required if we were
                // adding disconnection features, but for now we are just ignoring it.
                .flatMap(VoiceConnection::disconnect)
        );
        commands.put("Queue", event -> event.getMessage().getChannel()
                .flatMap(channel -> getQueue(event, channel))
                .then());
        commands.put("skip", event -> event.getMessage().getChannel()
                .doOnNext(channel ->
                        getScheduler(event.getGuildId().get()).skip())
                .then());


    }

    private static Mono<VoiceConnection> join(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                // join returns a VoiceConnection which would be required if we were
                // adding disconnection features, but for now we are just ignoring it.
                .flatMap(channel ->
                        channel.join().withProvider(GuildAudioManager.of(channel.getGuildId()).getProvider())
                );
    }

    private static MessageCreateMono getQueue(MessageCreateEvent event, MessageChannel channel) {
        return channel.createMessage(String.valueOf(getScheduler(event.getGuildId().get()).getQueueString()));
    }

    private static AudioTrackScheduler getScheduler(Snowflake event) {
        return GuildAudioManager.of(event).getScheduler();
    }


    public static void main(String[] args) {


        final GatewayDiscordClient client = DiscordClientBuilder.create(args[0]).build()
                .login()
                .block();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                // 3.1 Message.getContent() is a String
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .flatMap(content -> Flux.fromIterable(commands.entrySet())
                                // We will be using ! as our "prefix" to any command in the system.
                                .filter(entry -> content.startsWith('!' + entry.getKey()))
                                .flatMap(entry -> entry.getValue().execute(event))
                                .next()))
                .subscribe();
        client.onDisconnect().block();
    }

    private static Mono<Void> loadToTrack(Snowflake snowflake, List<String> tracks) {
        System.out.println(tracks);

        tracks.parallelStream().forEach(track -> {
            if (Objects.equals(track, "!play")) {
                return;
            }
            System.out.println("track " + track);
            try {
                PLAYER_MANAGER.loadItem(track, new AudioLoadResultHandler() {
                    @Override//???? one is not working
                    public void trackLoaded(AudioTrack track) {
                        getScheduler(snowflake).play(track);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {

                    }

                    @Override
                    public void noMatches() {

                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {

                    } /* overrides */
                });
            } catch (Exception e) {
                e.printStackTrace();

            }
        });
        return Mono.empty();
    }

}
