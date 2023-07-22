package net.hypixel.nerdbot.api.database.model.user.stats;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.NerdBotApp;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
@Setter
public class MojangProfile {

    @SerializedName("id")
    private UUID uniqueId;
    @SerializedName("name")
    private String username;
    private long lastUpdated;

    public MojangProfile() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean requiresCacheUpdate() {
        return Duration.of(System.currentTimeMillis(), ChronoUnit.MILLIS)
            .minus(NerdBotApp.getBot().getConfig().getMojangUsernameCache(), ChronoUnit.HOURS)
            .toMillis() > this.lastUpdated;
    }

}