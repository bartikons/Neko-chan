package org.Discoboto.Audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import reactor.core.scheduler.Schedulers;
import reactor.util.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.Discoboto.Command.UserCommand.prop;
import static org.Discoboto.Main.cleaner;

public class AudioTrackScheduler extends AudioEventAdapter {
    private final List<AudioTrack> queue;
    private final AudioPlayer player;
    private boolean repeat;
    private static final GatewayDiscordClient client = DiscordClientBuilder.create(prop.getProperty("key")).build()
            .login()
            .block();
    private MessageChannel messageChannel;
    private Message message;
    private boolean skiping = false;

    public boolean isSkiping() {
        return skiping;
    }

    public void setSkiping(boolean skiping) {
        this.skiping = skiping;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void toggleRepeat() {
        this.repeat = !repeat;
    }

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

    @Deprecated
    @Nullable
    public List<String> getQueueString(boolean areYouSure) {
        if (!queue.isEmpty()) {
            return queue.stream().map(e -> e.getInfo().title + "\n").toList().subList(0, Math.min(queue.size(), 20));
        } else return new ArrayList<>();
    }

    public List<EmbedCreateFields.Field> getQueueEmbed() {
        if (!queue.isEmpty()) {
            return new ArrayList<>(queue.stream().map(audioTrack ->
                            EmbedCreateFields
                                    .Field
                                    .of("TRACK", audioTrack != null ? audioTrack.getInfo().title : "Nya?", false)
                    )
                    .toList()
                    .subList(0, Math.min(queue.size(), 20)));
        } else return new ArrayList<>();
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
        boolean skiped = !queue.isEmpty() && play(queue.remove(0), true);
        if (!skiped) {
            if (player.getPlayingTrack() != null) {
                player.stopTrack();
            }
        }
        return skiped;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        if (!isSkiping()) {
            EmbedCreateSpec embedCreateSpec = EmbedCreateSpec.builder()
                    .color(Color.BISMARK)
                    .addField("Now playing", track.getInfo().title, false)
                    .build();
            message = messageChannel.createMessage(embedCreateSpec).publishOn(Schedulers.immediate()).block();
        }
        super.onTrackStart(player, track);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Advance the player if the track completed naturally (FINISHED) or if the track cannot play (LOAD_FAILED)
        cleaner.add(message.getChannelId(), message.getId());
        if (repeat && !skiping) {
            queue.add(track.makeClone());
        }
        if (endReason.mayStartNext) {
            skip();
        }
    }

    public Channel getMessageChannel() {
        return messageChannel;
    }

    public void setMessageChannel(MessageChannel messageChannel) {
        this.messageChannel = messageChannel;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
