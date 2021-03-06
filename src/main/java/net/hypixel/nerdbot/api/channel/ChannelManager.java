package net.hypixel.nerdbot.api.channel;

import net.dv8tion.jda.api.entities.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Logger;
import org.jetbrains.annotations.Nullable;

public class ChannelManager {

    @Nullable
    public static TextChannel getChannel(Channel channel) {
        return getChannel(channel.getId());
    }

    @Nullable
    public static TextChannel getChannel(String channel) {
        TextChannel textChannel = NerdBotApp.getBot().getJDA().getTextChannelById(channel);
        if (textChannel == null) {
            Logger.error("Failed to find channel: '" + channel + "'");
            return null;
        }
        return textChannel;
    }

}
