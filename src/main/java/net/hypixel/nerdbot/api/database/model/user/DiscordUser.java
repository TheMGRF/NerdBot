package net.hypixel.nerdbot.api.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.util.Util;

import java.time.DateTimeException;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

@AllArgsConstructor
@Getter
@Setter
@Log4j2
public class DiscordUser {

    private String discordId;
    private UserLanguage language;
    private LastActivity lastActivity;
    private BirthdayData birthdayData;
    private MojangProfile mojangProfile;

    public DiscordUser() {
    }

    public DiscordUser(String discordId) {
        this(discordId, UserLanguage.ENGLISH, new LastActivity(), new BirthdayData(), new MojangProfile());
    }

    public DiscordUser(Member member) {
        this(member.getId(), UserLanguage.ENGLISH, new LastActivity(), new BirthdayData(), new MojangProfile());
    }

    public int getTotalMessageCount() {
        return lastActivity.getChannelActivity().values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isProfileAssigned() {
        return this.mojangProfile != null && this.mojangProfile.getUniqueId() != null;
    }

    public boolean noProfileAssigned() {
        return !this.isProfileAssigned();
    }

    public void scheduleBirthdayReminder(Date date) {
        if (!birthdayData.isBirthdaySet()) {
            throw new IllegalStateException("Cannot schedule birthday reminder when birthday is not set!");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR));
        date = calendar.getTime();

        if (date.before(new Date())) {
            calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR) + 1);
            date = calendar.getTime();
            log.debug("Birthday for " + discordId + " is in the past, scheduling for next year: " + date);
        }

        log.info("Scheduling birthday reminder for " + discordId + " at " + date);

        if (birthdayData.getTimer() != null) {
            log.debug("Canceling previous birthday reminder for " + discordId);
            birthdayData.getTimer().cancel();
        }

        birthdayData.setTimer(new Timer());
        Date finalDate = date;
        birthdayData.getTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                String message = "Happy birthday <@%s>!";

                if (birthdayData.isShouldAnnounceAge()) {
                    message += " You are now %d years old!";
                }

                log.info("Sending birthday message for " + discordId + " at " + finalDate);
                String finalMessage = message;

                ChannelCache.getTextChannelByName("general").ifPresentOrElse(channel -> {
                    channel.sendMessage(String.format(finalMessage, discordId, birthdayData.getAge())).queue();
                    log.info("Sent birthday message for " + discordId + " at " + finalDate);
                }, () -> log.warn("Cannot find channel to send birthday message into!"));

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(finalDate);
                calendar.add(Calendar.YEAR, 1);
                scheduleBirthdayReminder(calendar.getTime());
                log.debug("Scheduled next birthday reminder for " + discordId + " at " + calendar.getTime());
            }
        }, date);
    }

    public void setBirthday(Date birthday) {
        if (birthdayData == null) {
            log.debug("Creating new birthday data for " + discordId);
            birthdayData = new BirthdayData();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(birthday);

        if (calendar.get(Calendar.YEAR) > Calendar.getInstance().get(Calendar.YEAR)) {
            throw new DateTimeException("Year cannot be in the future");
        }

        if (calendar.get(Calendar.YEAR) < 1900) {
            throw new DateTimeException("Year cannot be before 1900");
        }

        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);

        log.info("Setting birthday for " + discordId + " to " + calendar.getTime());

        birthdayData.setBirthday(calendar.getTime());
    }

    public Optional<Member> getMember() {
        return Optional.of(Util.getMainGuild().retrieveMemberById(discordId).complete());
    }

    public Optional<User> getUser() {
        return Optional.of(Util.getMainGuild().getJDA().retrieveUserById(discordId).complete());
    }
}
