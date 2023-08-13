package org.Discoboto.Object;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Cleaner extends Thread {

    List<NekoMessages> listOfLastMessages = new ArrayList<>(25);
    GatewayDiscordClient client;

    public Cleaner(GatewayDiscordClient client) {
        this.client = client;
    }

    public void add(Snowflake channelId, Snowflake messagesId) {
        listOfLastMessages.add(new NekoMessages(channelId, messagesId));
    }

    public void run() {
        while (true) {

            for (int i = 0; i < listOfLastMessages.size(); i++) {
                NekoMessages message = listOfLastMessages.get(i);
                if ((System.currentTimeMillis() - message.getTimeOfCreation()) > 3000) {
                    clean(message);
                    listOfLastMessages.remove(i--);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public Consumer<? super Void> CleanAll() {
        try {
            listOfLastMessages.forEach(this::clean);
        } catch (Exception ignored) {
        }

        return null;
    }

    private void clean(NekoMessages nekoMessages) {
        try {
            client.getMessageById(nekoMessages.getChannelId(), nekoMessages.getMessagesId()).block().delete().publishOn(Schedulers.immediate()).block();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }


}
