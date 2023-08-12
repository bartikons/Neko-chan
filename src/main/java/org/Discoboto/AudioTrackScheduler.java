package org.Discoboto;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class AudioTrackScheduler extends AudioEventAdapter {

    private final List<AudioTrack> queue;
    private final AudioPlayer player;

    private boolean repeat;

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

    public List<String> getQueueString() {
        if (!queue.isEmpty()) {
            return queue.stream().map(e -> e.getInfo().title + "\n").collect(Collectors.toList());
        } else return new ArrayList<>();
    }

    public synchronized boolean play(AudioTrack track) {
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

        if (repeat) {
            queue.add(player.getPlayingTrack());
        }
        if (endReason.mayStartNext) {
            skip();
        }
    }
}
