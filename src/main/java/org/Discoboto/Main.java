package org.Discoboto;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Main {
    private static final Map<String, Command> commands = new HashMap<>();

    public static final AudioPlayerManager PLAYER_MANAGER;

    protected static final Properties prop = new Properties();

    static {
        try (InputStream input = new FileInputStream(Main.class.getClassLoader().getResource("yt.properties").getPath())) {
            // load a properties file
            prop.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        PLAYER_MANAGER = new DefaultAudioPlayerManager();
        // This is an optimization strategy that Discord4J can utilize to minimize allocations
        // PLAYER_MANAGER.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager(true, prop.getProperty("email"), prop.getProperty("password")));
        PLAYER_MANAGER.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        PLAYER_MANAGER.registerSourceManager(new BandcampAudioSourceManager());
        PLAYER_MANAGER.registerSourceManager(new VimeoAudioSourceManager());
        PLAYER_MANAGER.registerSourceManager(new TwitchStreamAudioSourceManager());
        PLAYER_MANAGER.registerSourceManager(new BeamAudioSourceManager());
        PLAYER_MANAGER.registerSourceManager(new GetyarnAudioSourceManager());
        PLAYER_MANAGER.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
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
        commands.put("vol", event -> event.getGuild()
                .flatMap(guild -> {
                    try {
                        getAudioManager(guild.getId())
                                .getPlayer()
                                .setVolume(Integer.parseInt(
                                        event.getMessage()
                                                .getContent()
                                                .split(" ")[1]));
                    } catch (Exception ignored) {
                        //if there would be any literal
                    }
                    return Mono.empty();
                })
                .then());


    }

    private static Mono<VoiceConnection> join(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                // join returns a VoiceConnection which would be required if we were
                // adding disconnection features, but for now we are just ignoring it.
                .flatMap(channel ->
                        channel.join().withProvider(getAudioManager(channel.getGuildId()).getProvider())
                );
    }

    private static MessageCreateMono getQueue(MessageCreateEvent event, MessageChannel channel) {
        return channel.createMessage(String.valueOf(getScheduler(event.getGuildId().get()).getQueueString()));
    }

    private static AudioTrackScheduler getScheduler(Snowflake event) {
        return getAudioManager(event).getScheduler();
    }

    private static GuildAudioManager getAudioManager(Snowflake event) {
        return GuildAudioManager.of(event);
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
