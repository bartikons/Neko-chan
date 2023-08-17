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
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.voice.VoiceConnection;
import org.Discoboto.Audio.AudioTrackScheduler;
import org.Discoboto.Audio.GuildAudioManager;
import org.Discoboto.Object.Cleaner;
import org.Discoboto.Object.Command;
import org.apache.logging.log4j.LogManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final Map<String, Command> commands = new HashMap<>();
    public static final AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();
    static Map<Snowflake, List<Snowflake>> blockUser = new ConcurrentHashMap<>();
    protected static final Properties prop = new Properties();
    protected static Cleaner cleaner;

    protected static GatewayDiscordClient client;

    static {
        try {
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
                    LogManager.getLogger(Main.class).error("Exception: ", e);
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

            commands.put("ping", event -> getEventMessage(event).getChannel()
                    .flatMap(channel -> reply(event, "Pong!"))
                    .then());

            commands.put("join", event -> join(event)
                    .then());
            commands.put("play", event -> Mono.justOrEmpty(getEventMessage(event).getContent())
                    .map(content -> Arrays.asList(content.split(" ")))
                    .doOnNext(command -> {
                        try {
                            join(event).block();
                            Snowflake snowflake = getGuildSnowflake(event);
                            loadToTrack(snowflake, command);
                            getEventMessage(event)
                                    .getChannel()
                                    .flatMap(
                                            channel -> getQueue(snowflake, event)).block();
                        } catch (Exception e) {
                            LogManager.getLogger(Main.class).error("Exception: ", e);
                        }
                    })
                    .then());
            commands.put("leave", event -> Mono.justOrEmpty(event.getMember())
                    .flatMap(Member::getGuild)
                    .flatMap(Guild::getVoiceConnection)
                    // join returns a VoiceConnection which would be required if we were
                    // adding disconnection features, but for now we are just ignoring it.
                    .flatMap(VoiceConnection::disconnect));
            commands.put("queue", event -> getEventMessage(event).getChannel()
                    .flatMap(channel -> getQueue(getGuildSnowflake(event), event))
                    .then());
            commands.put("skip", event -> getEventMessage(event).getChannel()
                    .doOnNext(channel -> {
                                skip(event);
                        getQueue(getGuildSnowflake(event), event).block();
                            }
                    ).then());
            commands.put("vol", event -> event.getGuild()
                    .flatMap(guild -> {
                        try {
                            getAudioManager(guild.getId())
                                    .getPlayer()
                                    .setVolume(Integer.parseInt(
                                            getEventMessage(event)
                                                    .getContent()
                                                    .split(" ")[1]));
                        } catch (Exception ignored) {
                            //if there would be any literal
                        }
                        return Mono.empty();
                    })
                    .then());
            commands.put("repeat", event -> {
                GuildAudioManager guildAudioManager = getAudioManager(getGuildSnowflake(event));
                guildAudioManager.getScheduler().toggleRepeat();
                return reply(event, guildAudioManager.getScheduler().isRepeat() ? "Neko will be repeating song NYA~" : "Neko will stop repeating ...nya!").then();
            });
            commands.put("stop", event -> {
                GuildAudioManager guildAudioManager = getAudioManager(getGuildSnowflake(event));
                if (guildAudioManager.getPlayer().getPlayingTrack() != null) {
                    guildAudioManager.getPlayer().getPlayingTrack().stop();
                    return reply(event, "Neko-chan will stop ...nya~!").then();
                }
                return reply(event, "but there nothing to stop nya~?").then();
            });
            commands.put("pause", event -> {
                GuildAudioManager guildAudioManager = getAudioManager(getGuildSnowflake(event));
                guildAudioManager.getPlayer().setPaused(true);
                return reply(event, "Neko will pause track ...nyaaa!").then();
            });
            commands.put("start", event -> {
                GuildAudioManager guildAudioManager = getAudioManager(getGuildSnowflake(event));
                guildAudioManager.getPlayer().setPaused(false);
                return reply(event, "Neko will resume ...NYA!").then();
            });
            commands.put("messageToAuthor", event -> {

                cleaner.add(event.getMessage().getChannel().block().getId(), getEventMessage(event).getId());
                client.getUserById(Snowflake.of(prop.getProperty("Author"))).block().getPrivateChannel().block().createMessage(event.getMessage().getAuthor().flatMap(User::getGlobalName).get() + ": " + event.getMessage().getContent().replace("!messageToAuthor", "")).block();
                return Mono.empty();
            });
            commands.put("help", event -> {
                GuildAudioManager guildAudioManager = getAudioManager(getGuildSnowflake(event));
                guildAudioManager.getPlayer().setPaused(false);
                return reply(event, "TBD nya~!").then();
            });
        } catch (Exception e) {
            LogManager.getLogger(Main.class).error("Exception: ", e);
        }
    }

    private static Snowflake getGuildSnowflake(MessageCreateEvent event) {
        if (event.getGuildId().isPresent()) {
            return event.getGuildId().get();
        } else return null;
    }

    private static MessageChannel getChannel(MessageCreateEvent event) {
        return getEventMessage(event).getChannel().block();
    }

    private static void skip(MessageCreateEvent event) {
        int skip = 1;
        try {
            skip = Integer.parseInt(getEventMessage(event).getContent().split(" ")[1]);
        } catch (Exception ignored) {
        }
        for (int i = 0; i < skip; i++) {
            getScheduler(getGuildSnowflake(event)).skip();
        }
    }

    private static Mono<Void> reply(MessageCreateEvent event, String message) {
        MessageChannel channel = getChannel(event);
        cleaner.add(channel.getId(), getEventMessage(event).getId());
        Message thisMessage = channel.createMessage(message).block();
        cleaner.add(Objects.requireNonNull(thisMessage).getChannelId(), thisMessage.getId());
        return Mono.empty();
    }

    private static Message getEventMessage(MessageCreateEvent event) {
        return event.getMessage();
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

    private static Mono<Void> getQueue(Snowflake snowflake, MessageCreateEvent channel) {
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
            LogManager.getLogger(Main.class).error("Exception: ", e);
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

    private static boolean isBlocked(MessageCreateEvent event) {
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
            LogManager.getLogger(Main.class).error("Exception: ", e);
            return false;
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
                        public void loadFailed(FriendlyException e) {
                            LogManager.getLogger(Main.class).error("Exception: ", e);


                        } /* overrides */
                    });
                } catch (Exception e) {
                    LogManager.getLogger(Main.class).error("Exception: ", e);

                }
            });
            long timo = System.currentTimeMillis();
            do {
                if (System.currentTimeMillis() - timo > 5000)
                    break;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    LogManager.getLogger(Main.class).error("Exception: ", e);
                }
            } while ((audioPlayer.getPlayingTrack() == null && scheduler.getQueue().isEmpty()) || (size != -1 && scheduler.getQueue().size() == size));

        } catch (Exception e) {
            LogManager.getLogger(Main.class).error("Exception: ", e);
        }
    }

}
