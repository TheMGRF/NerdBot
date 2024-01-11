package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.bot.Environment;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.api.repository.Repository;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.bot.config.MetricsConfig;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.urlwatcher.FireSaleDataHandler;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.LoggingUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.exception.ProfileMismatchException;
import net.hypixel.nerdbot.util.exception.RepositoryException;
import org.apache.logging.log4j.Level;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class AdminCommands extends ApplicationCommand {

    @JDASlashCommand(name = "curate", description = "Manually run the curation process", defaultLocked = true)
    public void curate(GuildSlashEvent event, @AppOption ForumChannel channel, @Optional @AppOption(description = "Run the curator without greenlighting suggestions") Boolean readOnly) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.getInstance().reply(event, discordUser, "database.not_connected");
            log.error("Couldn't connect to the database!");
            return;
        }

        if (readOnly == null) {
            readOnly = false;
        }

        Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(readOnly);
        NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
            event.deferReply(true).complete();
            List<GreenlitMessage> output = forumChannelCurator.curate(channel);

            if (output.isEmpty()) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "curator.no_greenlit_messages");
            } else {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "curator.greenlit_messages", output.size());
            }
        });
    }

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption @Optional TextChannel channel) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        List<Invite> invites = new ArrayList<>(amount);
        TextChannel selected = Objects.requireNonNullElse(channel, NerdBotApp.getBot().getJDA().getTextChannelsByName("limbo", true).get(0));
        event.deferReply(true).complete();

        ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
            textChannel.sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Created")
                    .setDescription(event.getUser().getAsMention() + " created " + amount + " invite(s) for " + selected.getAsMention() + ".")
                    .build()
            ).queue();
        }, () -> {
            throw new IllegalStateException("Log channel not found!");
        });

        for (int i = 0; i < amount; i++) {
            try {
                InviteAction action = selected.createInvite()
                    .setUnique(true)
                    .setMaxAge(7L, TimeUnit.DAYS)
                    .setMaxUses(1);

                Invite invite = action.complete();
                invites.add(invite);
                log.info("Created new temporary invite '" + invite.getUrl() + "' for channel " + selected.getName() + " by " + event.getUser().getName());
            } catch (InsufficientPermissionException exception) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.invite.no_permission", selected.getAsMention());
                return;
            }
        }

        StringBuilder stringBuilder = new StringBuilder("**" + TranslationManager.getInstance().translate(discordUser, "commands.invite.header", invites.size()) + "**\n");
        invites.forEach(invite -> stringBuilder.append(invite.getUrl()).append("\n"));
        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).complete();

        List<Invite> invites = event.getGuild().retrieveInvites().complete();
        invites.forEach(invite -> {
            invite.delete().complete();
            log.info(event.getUser().getName() + " deleted invite " + invite.getUrl());
        });

        ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
            textChannel.sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Deleted")
                    .setDescription(event.getUser().getAsMention() + " deleted all " + invites.size() + " invite(s).")
                    .build()
            ).queue();
        }, () -> {
            throw new IllegalStateException("Log channel not found!");
        });

        event.getHook().editOriginal("Deleted " + invites.size() + " invites.").queue();
    }

    @JDASlashCommand(name = "lock", description = "Locks the thread that the command is executed in", defaultLocked = true)
    public void lockThread(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        if (!(event.getChannel() instanceof ThreadChannel threadChannel)) {
            TranslationManager.getInstance().reply(event, discordUser, "commands.only_available_in_threads");
            return;
        }

        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        boolean locked = threadChannel.isLocked();
        ThreadChannelManager threadManager = threadChannel.getManager();

        // Add Reviewed Tag
        if (threadChannel.getParentChannel() instanceof ForumChannel forumChannel) { // Is thread inside a forum?
            if (Util.hasTagByName(forumChannel, suggestionConfig.getReviewedTag())) { // Does forum contain the reviewed tag?
                if (!Util.hasTagByName(threadChannel, suggestionConfig.getReviewedTag())) { // Does thread not currently have reviewed tag?
                    List<ForumTag> forumTags = new ArrayList<>(threadChannel.getAppliedTags());
                    forumTags.removeIf(forumTag -> forumTag.getName().equalsIgnoreCase(suggestionConfig.getGreenlitTag())); // Remove Greenlit just in-case
                    forumTags.add(Util.getTagByName(forumChannel, suggestionConfig.getReviewedTag()));
                    threadManager = threadManager.setAppliedTags(forumTags);
                }
            }
        }

        threadManager.setLocked(!locked).queue(unused ->
                event.reply("This thread is now " + (!locked ? "locked" : "unlocked") + "!").queue(),
            throwable -> {
                TranslationManager.getInstance().reply(event, discordUser, "commands.lock.error");
                log.error("An error occurred when locking the thread " + threadChannel.getId() + "!", throwable);
            });
    }

    @JDASlashCommand(name = "archive", subcommand = "channel", description = "Archives a specific channel.", defaultLocked = true)
    public void archive(GuildSlashEvent event, @AppOption TextChannel channel, @AppOption @Optional Boolean nerd, @AppOption @Optional Boolean alpha) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        // By default nerd is true to prevent leaks.
        if (nerd == null) {
            nerd = true;
        }

        // By default, alpha is false.
        if (alpha == null) {
            alpha = false;
        }

        ChannelConfig channelConfig = NerdBotApp.getBot().getConfig().getChannelConfig();
        if (nerd) {
            Category nerdArchive = event.getGuild().getCategoryById(channelConfig.getNerdArchiveCategoryId());
            // Moves Channel to Nerd Archive category here.
            channel.getManager().setParent(nerdArchive).queue();
            channel.getManager().sync(nerdArchive.getPermissionContainer()).queue();
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.archive.channel_moved", channel.getAsMention(), nerdArchive.getName());
            return;
        }

        if (alpha) {
            Category alphaArchive = event.getGuild().getCategoryById(channelConfig.getAlphaArchiveCategoryId());
            // Moves Channel to Alpha Archive category here.
            channel.getManager().setParent(alphaArchive).queue();
            channel.getManager().sync(alphaArchive.getPermissionContainer()).queue();
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.archive.channel_moved", channel.getAsMention(), alphaArchive.getName());
            return;
        }

        Category publicArchive = event.getGuild().getCategoryById(channelConfig.getPublicArchiveCategoryId());
        // Moves Channel to Public Archive category here.
        channel.getManager().setParent(publicArchive).queue();
        channel.getManager().sync(publicArchive.getPermissionContainer()).queue();
        TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.archive.channel_moved", channel.getAsMention(), publicArchive.getName());
    }

    @JDASlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", defaultLocked = true)
    public void showConfig(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        try {
            File file = Util.createTempFile("config-" + System.currentTimeMillis() + ".json", NerdBotApp.GSON.toJson(NerdBotApp.getBot().getConfig()));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.config.read_error");
            log.error("An error occurred when reading the JSON file!", exception);
        }
    }

    @JDASlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", defaultLocked = true)
    public void reloadConfig(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        Bot bot = NerdBotApp.getBot();

        bot.loadConfig();
        bot.getJDA().getPresence().setActivity(Activity.of(bot.getConfig().getActivityType(), bot.getConfig().getActivity()));
        PrometheusMetrics.setMetricsEnabled(bot.getConfig().getMetricsConfig().isEnabled());

        TranslationManager.getInstance().reply(event, discordUser, "commands.config.reloaded");
    }

    @JDASlashCommand(name = "config", subcommand = "edit", description = "Edit the config file", defaultLocked = true)
    public void editConfig(GuildSlashEvent event, @AppOption String key, @AppOption String value) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        // We should store the name of the config file on boot lol this is bad
        String fileName = System.getProperty("bot.config") != null ? System.getProperty("bot.config") : Environment.getEnvironment().name().toLowerCase() + ".config.json";
        JsonObject obj = JsonUtil.readJsonFile(fileName);

        if (obj == null) {
            TranslationManager.getInstance().reply(event, discordUser, "commands.config.read_error");
            return;
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(value);
        } catch (JsonSyntaxException exception) {
            TranslationManager.getInstance().reply(event, discordUser, "commands.config.invalid_value", e.getMessage());
            return;
        }

        JsonUtil.writeJsonFile(fileName, JsonUtil.setJsonValue(obj, key, element));
        log.info(event.getUser().getName() + " edited the config file!");
        TranslationManager.getInstance().reply(event, discordUser, "commands.config.updated");
    }

    @JDASlashCommand(name = "metrics", subcommand = "toggle", description = "Toggle metrics collection", defaultLocked = true)
    public void toggleMetrics(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        Bot bot = NerdBotApp.getBot();
        MetricsConfig metricsConfig = bot.getConfig().getMetricsConfig();
        metricsConfig.setEnabled(!metricsConfig.isEnabled());
        PrometheusMetrics.setMetricsEnabled(metricsConfig.isEnabled());

        TranslationManager.getInstance().reply(event, discordUser, "commands.metrics.toggle", metricsConfig.isEnabled());
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "list",
        description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.",
        defaultLocked = true
    )
    public void userList(GuildSlashEvent event, @Optional @AppOption(description = "Comma-separated role names to search for (default Member).") String roles) throws IOException {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        String[] roleArray = roles != null ? roles.split(", ?") : new String[]{"Member"};
        JsonArray uuidArray = new JsonArray();

        List<MojangProfile> profiles = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> RoleManager.hasAnyRole(member, roleArray))
            .map(member -> discordUserRepository.findById(member.getId()))
            .filter(DiscordUser::isProfileAssigned)
            .map(DiscordUser::getMojangProfile)
            .toList();

        log.info("Found " + profiles.size() + " members meeting requirements.");
        profiles.forEach(profile -> uuidArray.add(profile.getUniqueId().toString()));
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(uuidArray));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "link",
        description = "Link a Mojang Profile to a member's account.",
        defaultLocked = true
    )
    public void linkProfile(
        GuildSlashEvent event,
        @AppOption(description = "Member to link to.") Member member,
        @AppOption(description = "Your Minecraft IGN to link.") String username,
        @Optional @AppOption(description = "Bypass hypixel social check.") Boolean bypassSocial
    ) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        bypassSocial = (bypassSocial == null || !bypassSocial);

        try {
            MojangProfile mojangProfile = MyCommands.requestMojangProfile(member, username, bypassSocial);
            MyCommands.updateMojangProfile(member, mojangProfile);
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.user.linked_by_admin", member.getAsMention(), mojangProfile.getUsername(), mojangProfile.getUniqueId());

            ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
                textChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                        .setTitle("Mojang Profile Change")
                        .setThumbnail(member.getAvatarUrl())
                        .setDescription(event.getMember().getAsMention() + " updated the Mojang Profile for " + member.getAsMention() + ".")
                        .addField("Username", mojangProfile.getUsername(), false)
                        .addField(
                            "UUID / SkyCrypt",
                            String.format(
                                "[%s](https://sky.shiiyu.moe/stats/%s)",
                                mojangProfile.getUniqueId(),
                                mojangProfile.getUniqueId()
                            ),
                            false
                        )
                        .build()
                ).queue();
            }, () -> {
                throw new IllegalStateException("Log channel not found!");
            });
        } catch (HttpException exception) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.user.username_not_found", username, exception.getMessage());
            log.error("Unable to locate Minecraft UUID for " + username + "!", exception);
        } catch (ProfileMismatchException exception) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.user.profile_mismatch", username, exception.getMessage());
        }
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "missing",
        description = "List any user with no assigned Mojang Profile.",
        defaultLocked = true
    )
    public void userMissingProfile(GuildSlashEvent event) {
        event.deferReply(true).queue();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        String missing = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> discordUserRepository.findById(member.getId()).noProfileAssigned())
            .map(IMentionable::getAsMention)
            .collect(Collectors.joining(", "));

        if (!missing.isEmpty()) {
            event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                    .setColor(Color.MAGENTA)
                    .setTitle("Missing Mojang Profiles")
                    .setDescription(missing)
                    .build()
            ).queue();
        } else {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.user.no_missing_profiles");
        }
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "info",
        description = "View information about a user",
        defaultLocked = true
    )
    public void userInfo(GuildSlashEvent event, @AppOption(description = "The user to search") Member member) {
        event.deferReply(true).queue();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = MyCommands.getActivityEmbeds(member);

        String profile = discordUser.isProfileAssigned() ?
            discordUser.getMojangProfile().getUsername() + " (" + discordUser.getMojangProfile().getUniqueId().toString() + ")" :
            "*Missing Data*";

        event.getHook().editOriginalEmbeds(
            new EmbedBuilder()
                .setAuthor(member.getEffectiveName())
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField("ID", member.getId(), false)
                .addField("Mojang Profile", profile, false)
                .build(),
            activityEmbeds.getLeft().build(),
            activityEmbeds.getRight().build()
        ).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "migrate",
        description = "Attempts to migrate any user with no assigned Mojang Profile using their display name.",
        defaultLocked = true
    )
    public void migrateUsernames(GuildSlashEvent event) {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        List<MojangProfile> mojangProfiles = new ArrayList<>();

        event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> discordUserRepository.findById(member.getId()).noProfileAssigned())
            .filter(member -> Util.getScuffedMinecraftIGN(member).isPresent())
            .forEach(member -> {
                String scuffedUsername = Util.getScuffedMinecraftIGN(member).orElseThrow();

                try {
                    MojangProfile mojangProfile = Util.getMojangProfile(scuffedUsername);
                    mojangProfiles.add(mojangProfile);
                    DiscordUser user = discordUserRepository.findById(member.getId());
                    user.setMojangProfile(mojangProfile);
                    log.info("Migrated " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ") to " + mojangProfile.getUsername() + " (" + mojangProfile.getUniqueId() + ")");
                } catch (HttpException exception) {
                    log.error("Unable to migrate " + member.getEffectiveName() + "(ID: " + member.getId() + ")", exception);
                }
            });

        TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.user.migrated_profiles", mojangProfiles.size());
    }

    @JDASlashCommand(name = "flared", description = "Add the Flared tag to a suggestion and lock it", defaultLocked = true)
    public void flareSuggestion(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().reply(event, "user.not_found");
            return;
        }

        Channel channel = event.getChannel();
        if (!(channel instanceof ThreadChannel threadChannel) || !(threadChannel.getParentChannel() instanceof ForumChannel forumChannel)) {
            TranslationManager.getInstance().reply(event, discordUser, "commands.only_available_in_threads");
            return;
        }

        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        if (Util.hasTagByName(forumChannel, suggestionConfig.getFlaredTag())) {
            TranslationManager.getInstance().reply(event, discordUser, "commands.flared.no_tag", "Flared");
            return;
        }

        ForumTag flaredTag = Util.getTagByName(forumChannel, suggestionConfig.getFlaredTag());
        if (threadChannel.getAppliedTags().contains(flaredTag)) {
            TranslationManager.getInstance().reply(event, discordUser, "commands.flared.already_tagged");
            return;
        }

        List<ForumTag> appliedTags = new ArrayList<>(threadChannel.getAppliedTags());
        appliedTags.add(flaredTag);

        threadChannel.getManager()
            .setLocked(true)
            .setAppliedTags(appliedTags)
            .queue();

        TranslationManager.getInstance().reply(event, discordUser, "commands.flared.tagged", event.getUser().getAsMention(), flaredTag.getName());
    }

    @JDASlashCommand(name = "cache", subcommand = "force-save", description = "Force save the specified cache to the database", defaultLocked = true)
    public void forceSaveRepository(GuildSlashEvent event, @AppOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        try {
            Repository<?> repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);
            if (repository == null) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.not_found");
                return;
            }

            repository.saveAllToDatabase();
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.saved_to_database", repository.getCache().estimatedSize());
        } catch (RepositoryException exception) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.save_error", exception.getMessage());
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @JDASlashCommand(name = "cache", subcommand = "force-load", description = "Forcefully load documents from the database into the cache", defaultLocked = true)
    public void forceLoadDocuments(GuildSlashEvent event, @AppOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        try {
            Repository<?> repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);

            if (repository == null) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.not_found");
                return;
            }

            repository.loadAllDocumentsIntoCache();
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.loaded_from_database", repository.getCache().estimatedSize());
        } catch (RepositoryException exception) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.load_error", exception.getMessage());
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @JDASlashCommand(name = "cache", subcommand = "stats", description = "View cache statistics", defaultLocked = true)
    public void cacheStats(GuildSlashEvent event, @AppOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found!").queue();
            return;
        }

        try {
            Repository<?> repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);

            if (repository == null) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.not_found");
                return;
            }

            event.getHook().editOriginal(repository.getCache().stats().toString()).queue();
        } catch (RepositoryException exception) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "repository.stats_error", exception.getMessage());
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @JDASlashCommand(name = "transfer-tag", description = "Transfer forum tag to another.", defaultLocked = true)
    public void transferForumTag(
        GuildSlashEvent event,
        @AppOption(name = "channel", description = "Forum channel to transfer tags in.") ForumChannel channel,
        @AppOption(name = "from", description = "Transfer from this tag.", autocomplete = "forumtags") String from,
        @AppOption(name = "to", description = "Transfer to this tag.", autocomplete = "forumtags") String to
    ) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        ForumTag fromTag;
        ForumTag toTag;

        try {
            // Autocomplete Support
            fromTag = Objects.requireNonNull(channel.getAvailableTagById(from));
            toTag = Objects.requireNonNull(channel.getAvailableTagById(to));
        } catch (NumberFormatException nfex) {
            try {
                // "I can type it myself" Support
                fromTag = channel.getAvailableTagsByName(from, true).get(0);
                toTag = channel.getAvailableTagsByName(to, true).get(0);
            } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.transfer_tag.invalid_tags");
                return;
            }
        }

        // Load Threads
        ForumTag searchTag = fromTag;
        List<ThreadChannel> threadChannels = Stream.concat(
                channel.getThreadChannels().stream(),
                channel.retrieveArchivedPublicThreadChannels().stream()
            )
            .filter(threadChannel -> threadChannel.getAppliedTags().contains(searchTag))
            .distinct() // Prevent Duplicates
            .toList();

        int total = threadChannels.size();
        if (total == 0) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.transfer_tag.no_threads_found", fromTag.getName());
            return;
        }

        int processed = 0;
        int modulo = Math.min(total, 10);
        TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.transfer_tag.updated_threads", 0, total);

        // Process Threads
        for (ThreadChannel threadChannel : threadChannels) {
            List<ForumTag> threadTags = new ArrayList<>(threadChannel.getAppliedTags());
            threadTags.remove(fromTag);

            // Prevent Duplicates
            if (!threadTags.contains(toTag)) {
                threadTags.add(toTag);
            }

            boolean archived = threadChannel.isArchived();

            if (archived) {
                threadChannel.getManager().setArchived(false).complete();
            }

            try {
                threadChannel.getManager().setAppliedTags(threadTags).complete();
            } catch (Exception exception) {
                log.warn("Unable to set applied tags for [" + threadChannel.getId() + "] " + threadChannel.getName(), exception);
            }

            if (archived) {
                threadChannel.getManager().setArchived(true).complete();
            }

            if (++processed % modulo == 0 && processed != total) {
                TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.transfer_tag.updated_threads", processed, total);
            }
        }

        TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.transfer_tag.updated_threads_complete", fromTag.getName(), toTag.getName(), total);
    }

    @AutocompletionHandler(name = "forumchannels", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<ForumChannel> listForumChannels(CommandAutoCompleteInteractionEvent event) {
        return Util.getMainGuild().getForumChannels();
    }

    @AutocompletionHandler(name = "forumtags", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<ForumTag> listForumTags(CommandAutoCompleteInteractionEvent event) {
        List<ForumTag> forumTags = new ArrayList<>();

        ChannelCache.getForumChannelById(event.getOption("channel").getAsString()).ifPresent(forumChannel -> {
            forumTags.addAll(forumChannel.getAvailableTags());
        });

        return forumTags;
    }

    @JDASlashCommand(name = "loglevel", description = "Set the log level", defaultLocked = true)
    public void setLogLevel(GuildSlashEvent event, @AppOption(name = "level", description = "Log level to set", autocomplete = "loglevels") String level) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        Level logLevel = Level.toLevel(level.toUpperCase());

        if (logLevel == null) {
            TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.log_level.invalid_level");
            return;
        }

        LoggingUtil.setGlobalLogLevel(logLevel);
        TranslationManager.getInstance().edit(event.getHook(), discordUser, "commands.log_level.set_level", logLevel);
    }

    @AutocompletionHandler(name = "loglevels", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<String> listLogLevels(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(Level.values()).map(Level::toString).toList();
    }
}
