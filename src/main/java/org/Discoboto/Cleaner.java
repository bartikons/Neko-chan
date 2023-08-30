package org.Discoboto;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import reactor.core.scheduler.Schedulers;

import java.util.AbstractQueue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Cleaner extends TimerTask {

    AbstractQueue<NekoMessages> listOfLastMessages = new ConcurrentLinkedQueue<>();
    GatewayDiscordClient client;

    public Cleaner(GatewayDiscordClient client) {
        this.client = client;
        new Timer().scheduleAtFixedRate(this, 0, 20000);
    }

    public void add(Snowflake channelId, Snowflake messagesId) {
        listOfLastMessages.add(new NekoMessages(channelId, messagesId));
    }

    public void run() {
        listOfLastMessages.parallelStream().forEach(nekoMessages -> {
            clean(nekoMessages);
            listOfLastMessages.remove(nekoMessages);
        });
    }

    public void CleanAll() {
        try {
            listOfLastMessages.forEach(this::clean);
        } catch (Exception ignored) {
        }

    }

    private void clean(NekoMessages nekoMessages) {
        try {
            client.getMessageById(nekoMessages.getChannelId(), nekoMessages.getMessagesId()).block().delete().publishOn(Schedulers.immediate()).block();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
