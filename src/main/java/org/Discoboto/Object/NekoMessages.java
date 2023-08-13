package org.Discoboto.Object;

import discord4j.common.util.Snowflake;

public class NekoMessages {
    private final long timeOfCreation;
    private final Snowflake messagesId;
    private final Snowflake channelId;

    public NekoMessages(Snowflake channelId, Snowflake messagesId) {
        this.channelId = channelId;
        this.messagesId = messagesId;
        timeOfCreation = System.currentTimeMillis();
    }

    public Snowflake getMessagesId() {
        return messagesId;
    }

    public Snowflake getChannelId() {
        return channelId;
    }

    public long getTimeOfCreation() {
        return timeOfCreation;
    }
}
