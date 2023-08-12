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
    static List<Snowflake> blockUser;

    protected static final Properties prop = new Properties();

    static {
        try (InputStream input = new FileInputStream(Main.class.getClassLoader().getResource("yt.properties").getPath())) {
            // load a properties file
            prop.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        try {
            blockUser = Arrays.stream(prop.getProperty("blocked").split(",")).map(Snowflake::of).toList();
        } catch (Exception ignored) {
            blockUser = new ArrayList<>();
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

    //266548533707014144
    static {
        commands.put("ping", event -> event.getMessage().getChannel()
                .flatMap(channel -> replay(channel, "Pong!"))
                .then());

        commands.put("join", event -> join(event)
                .then());
        commands.put("play", event -> Mono.justOrEmpty(event.getMessage().getContent())
                .map(content -> Arrays.asList(content.split(" ")))
                .doOnNext(command -> {
                    join(event).block();
                    loadToTrack(event.getGuildId().get(), command);
                    event.getMessage()
                            .getChannel()
                            .flatMap(
                                    channel -> getQueue(event, channel)).block();
                })
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
                .doOnNext(channel -> {
                            skip(event);
                        }
                ).then());
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

    private static void skip(MessageCreateEvent event) {
        int skip = 1;
        try {
            skip = Integer.parseInt(event.getMessage().getContent().split(" ")[1]);
        } catch (Exception ignored) {
        }
        for (int i = 0; i < skip; i++) {
            getScheduler(event.getGuildId().get()).skip();
        }
    }

    private static MessageCreateMono replay(MessageChannel channel, String message) {
        return channel.createMessage(message);
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
        return replay(channel, "Queue: \n" + getScheduler(event.getGuildId().get()).getQueueString());
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
                                .flatMap(entry -> {
                                    if (isBlocked(event)) {
                                        return Mono.just(replay(event.getMessage().getChannel().block(), "Fuck off")).block().then(Mono.just(""));
                                    }
                                    return entry.getValue().execute(event);
                                })
                                .next()))
                .subscribe();
        client.onDisconnect().block();
    }

    private static boolean isBlocked(MessageCreateEvent event) {
        if (event.getMessage().getAuthor().isEmpty()) {
            return true;
        }
        return blockUser.contains(event.getMessage().getAuthor().get().getId());
    }

    private static Mono<Void> loadToTrack(Snowflake snowflake, List<String> tracks) {
        System.out.println(tracks);
        AudioTrackScheduler audioTrackScheduler = getScheduler(snowflake);

        tracks.parallelStream().forEach(track -> {
            if (Objects.equals(track, "!play")) {
                return;
            }
            System.out.println("track " + track);
            try {
                PLAYER_MANAGER.loadItem(track, new AudioLoadResultHandler() {
                    @Override//???? one is not working
                    public void trackLoaded(AudioTrack track) {
                        audioTrackScheduler.play(track);
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
        while (audioTrackScheduler.getQueue().isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return Mono.empty();
    }

}
