package org.Discoboto;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
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
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final Map<String, Command> commands = new HashMap<>();
    public static final AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();
    static Map<Snowflake, List<Snowflake>> blockUser = new ConcurrentHashMap<>();
    protected static final Properties prop = new Properties();
    private static GatewayDiscordClient client;

    static {
        try {
            try (InputStream input = new FileInputStream(Main.class.getClassLoader().getResource("yt.properties").getPath())) {
                // load a properties file
                prop.load(input);
            } catch (IOException ex) {
                ex.printStackTrace();
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
                    e.printStackTrace();
                }

            } catch (Exception ignored) {
                blockUser = new ConcurrentHashMap<>();
            }
            // This is an optimization strategy that Discord4J can utilize to minimize allocations
            // PLAYER_MANAGER.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
            if (prop.containsKey("email") && prop.containsKey("password")) {
                PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager(true, prop.getProperty("email"), prop.getProperty("password")));
            } else {
                PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager(true, null, null));
            }
            PLAYER_MANAGER.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
            PLAYER_MANAGER.registerSourceManager(new BandcampAudioSourceManager());
            PLAYER_MANAGER.registerSourceManager(new VimeoAudioSourceManager());
            PLAYER_MANAGER.registerSourceManager(new TwitchStreamAudioSourceManager());
            PLAYER_MANAGER.registerSourceManager(new BeamAudioSourceManager());
            PLAYER_MANAGER.registerSourceManager(new GetyarnAudioSourceManager());
            PLAYER_MANAGER.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
            AudioSourceManagers.registerLocalSource(PLAYER_MANAGER);

            commands.put("ping", event -> event.getMessage().getChannel()
                    .flatMap(channel -> reply(channel, "Pong!"))
                    .then());

            commands.put("join", event -> join(event)
                    .then());
            commands.put("play", event -> Mono.justOrEmpty(event.getMessage().getContent())
                    .map(content -> Arrays.asList(content.split(" ")))
                    .doOnNext(command -> {
                        try {
                            join(event).block();
                            Snowflake snowflake = event.getGuildId().get();
                            loadToTrack(snowflake, command);
                            event.getMessage()
                                    .getChannel()
                                    .flatMap(
                                            channel -> getQueue(snowflake, channel)).block();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .then());
            commands.put("leave", event -> Mono.justOrEmpty(event.getMember())
                    .flatMap(Member::getGuild)
                    .flatMap(Guild::getVoiceConnection)
                    // join returns a VoiceConnection which would be required if we were
                    // adding disconnection features, but for now we are just ignoring it.
                    .flatMap(VoiceConnection::disconnect));
            commands.put("Queue", event -> event.getMessage().getChannel()
                    .flatMap(channel -> getQueue(event.getGuildId().get(), channel))
                    .then());
            commands.put("skip", event -> event.getMessage().getChannel()
                    .doOnNext(channel -> {
                                skip(event);
                                getQueue(event.getGuildId().get(), channel).block();
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
            commands.put("repeat", event -> {
                        GuildAudioManager guildAudioManager = getAudioManager(event.getGuildId().get());
                        guildAudioManager.getScheduler().toggleRepeat();
                        return reply(event.getMessage().getChannel().block(), guildAudioManager.getScheduler().isRepeat() ? "Neko will be repeating song NYA~" : "Neko will stop repeating ...nya!").then();
                    }
            );


//        commands.put("block", event -> event.getGuild().flatMap()
//                .then());
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private static MessageCreateMono reply(MessageChannel channel, String message) {
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

    private static MessageCreateMono getQueue(Snowflake snowflake, MessageChannel channel) {
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
            e.printStackTrace();
            return reply(channel, "BUG~!");
        }
    }

    private static AudioTrackScheduler getScheduler(Snowflake event) {
        return getAudioManager(event).getScheduler();
    }

    private static GuildAudioManager getAudioManager(Snowflake event) {
        return GuildAudioManager.of(event);
    }


    public static void main(String[] args) {


        client = DiscordClientBuilder.create(args[0]).build()
                .login()
                .block();
        try {
            client.getEventDispatcher().on(MessageCreateEvent.class)
                    // 3.1 Message.getContent() is a String
                    .flatMap(event -> Mono.just(event.getMessage().getContent())
                            .flatMap(content -> {
                                        try {
                                            return Flux.fromIterable(commands.entrySet())
                                                    // We will be using ! as our "prefix" to any command in the system.
                                                    .filter(entry -> content.startsWith('!' + entry.getKey()))
                                                    .flatMap(entry -> {
                                                        if (isBlocked(event)) {
                                                            return Mono.just(reply(event.getMessage().getChannel().block(), "Fuck off")).block().then(Mono.just(""));
                                                        }
                                                        return entry.getValue().execute(event);
                                                    })
                                                    .next();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            return Mono.empty();
                                        }
                                    }
                            )
                    )
                    .subscribe();
        } catch (Exception e) {
            e.printStackTrace();
        }

        client.onDisconnect().block();
    }

    private static boolean isBlocked(MessageCreateEvent event) {
        try {
            if (event.getMessage().getAuthor().isEmpty()) {
                return true;
            }
            Snowflake snowflake = event.getGuildId().get();
            blockUser.computeIfAbsent(snowflake, k -> new ArrayList<>());
            return blockUser.get(snowflake).contains(event.getMessage().getAuthor().get().getId());
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    private static void loadToTrack(Snowflake snowflake, List<String> tracks) {
        try {
            GuildAudioManager guildAudioManager = getAudioManager(snowflake);
            AudioPlayer audioPlayer = guildAudioManager.getPlayer();
            AudioTrackScheduler scheduler = guildAudioManager.getScheduler();
            int size = -1;
            if (audioPlayer.getPlayingTrack() != null) {
                size = scheduler.getQueue().size();
            }
            tracks.parallelStream().forEach(track -> {
                if (Objects.equals(track, "!play") || track.trim().isEmpty()) {
                    return;
                }
                try {
                    PLAYER_MANAGER.loadItem(track, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            scheduler.play(track);
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {

                        }

                        @Override
                        public void noMatches() {

                        }

                        @Override
                        public void loadFailed(FriendlyException exception) {
                            exception.printStackTrace();


                        } /* overrides */
                    });
                } catch (Exception e) {
                    e.printStackTrace();

                }
            });
            long timo = System.currentTimeMillis();
            do {
                if (System.currentTimeMillis() - timo > 5000)
                    break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } while ((audioPlayer.getPlayingTrack() == null && scheduler.getQueue().isEmpty()) || (size != -1 && scheduler.getQueue().size() == size));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
