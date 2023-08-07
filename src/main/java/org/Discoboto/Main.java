package org.Discoboto;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.VoiceChannelJoinMono;
import discord4j.rest.RestClient;
import discord4j.voice.AudioProvider;
import org.Discoboto.Object.Command;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
        commands.put("ping", event -> event.getMessage()
                .getChannel().block()
                .createMessage("Pong!").block());
    }

    public static void main(String[] args) {
        String token = args[0];
        DiscordClient client = DiscordClient.create(token);

        client.getGuilds();

        GatewayDiscordClient blocked = client.login().block();
        RestClient restClient = blocked.getRestClient();
        VoiceChannel channel = new VoiceChannel(blocked, restClient.getChannelById(Snowflake.of(689854156785319943L)).getData().block());
        GuildAudioManager guildAudioManager = GuildAudioManager.of(channel.getGuildId());
        AudioProvider provider = guildAudioManager.getProvider();
        client.withGateway(gatewayDiscordClient -> {
            loadToTrack(guildAudioManager);
            return channel.join().withProvider(provider);
        }).block();
//        voiceChannelJoinMono.block();

        // In the AudioLoadResultHandler, add AudioTrack instances to the AudioTrackScheduler (and send notifications to users)
    }

    private static void loadToTrack(GuildAudioManager guildAudioManager) {
        PLAYER_MANAGER.loadItem("https://www.youtube.com/watch?v=UnIhRpIT7nc", new AudioLoadResultHandler() {
            @Override//???? one is not working
            public void trackLoaded(AudioTrack track) {
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
                guildAudioManager.getScheduler().play(track);
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
    }

    public static class AudioTrackScheduler extends AudioEventAdapter {

        private final List<AudioTrack> queue;
        private final AudioPlayer player;

        public AudioTrackScheduler(AudioPlayer player) {
            // The queue may be modifed by different threads so guarantee memory safety
            // This does not, however, remove several race conditions currently present
            queue = Collections.synchronizedList(new LinkedList<>());
            player.setVolume(4);
            this.player = player;
        }

        public List<AudioTrack> getQueue() {
            return queue;
        }

        public boolean play(AudioTrack track) {
            return play(track, false);
        }

        public boolean play(AudioTrack track, boolean force) {
            boolean playing = player.startTrack(track, !force);

            if (!playing) {
                queue.add(track);
            }

            return playing;
        }

        public boolean skip() {
            return !queue.isEmpty() && play(queue.remove(0), true);
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            // Advance the player if the track completed naturally (FINISHED) or if the track cannot play (LOAD_FAILED)
            if (endReason.mayStartNext) {
                skip();
            }
        }
    }
}