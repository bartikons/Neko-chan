package org.Discoboto.Command;

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
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.voice.VoiceConnection;
import org.Discoboto.Audio.AudioTrackScheduler;
import org.Discoboto.Audio.GuildAudioManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.Discoboto.Command.UserCommand.prop;

public class AudioCommand extends baseCommand {

    public static final AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();

    static {

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
                        getLogger().error("Exception: ", e);
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
                            playlist.getTracks().parallelStream().forEach(scheduler::play);

                        }

                        @Override
                        public void noMatches() {
                            getLogger().error("Exception: noMatches");
                        }

                        @Override
                        public void loadFailed(FriendlyException e) {
                            getLogger().error("Exception: ", e);
                        } /* overrides */
                    });
                } catch (Exception e) {
                    getLogger().error("Exception: ", e);

                }
            });
            long timo = System.currentTimeMillis();
            do {
                if (System.currentTimeMillis() - timo > 5000)
                    break;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    getLogger().error("Exception: ", e);
                }
            } while ((audioPlayer.getPlayingTrack() == null && scheduler.getQueue().isEmpty()) ||
                    (size != -1 && scheduler.getQueue().size() == size));

        } catch (Exception e) {
            getLogger().error("Exception: ", e);
        }
    }

    private static Logger getLogger() {
        return LogManager.getLogger(AudioCommand.class);
    }

}
