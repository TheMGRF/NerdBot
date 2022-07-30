package net.hypixel.nerdbot.api.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.command.slash.CommandArgument;
import net.hypixel.nerdbot.api.command.slash.RestrictedSlashCommand;
import net.hypixel.nerdbot.api.command.slash.SlashCommand;
import net.hypixel.nerdbot.api.command.slash.SlashCommandArguments;
import net.hypixel.nerdbot.util.Logger;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CommandManager {

    private final List<SlashCommand> commands = new ArrayList<>();

    private String prefix = "!";

    public CommandManager() {
    }

    public CommandManager(String prefix) {
        this.prefix = prefix;
    }

    public List<SlashCommand> getCommands() {
        return commands;
    }

    public void registerCommand(SlashCommand command) {
        commands.add(command);

        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            Logger.error("Couldn't find the guild specified in the bot config!");
            return;
        }

        SlashCommandData data = Commands.slash(command.getCommandName(), command.getDescription());
        if (command instanceof RestrictedSlashCommand) {
            data.setDefaultPermissions(((RestrictedSlashCommand) command).getPermission());
        }

        if (command instanceof SlashCommandArguments) {
            if (!((SlashCommandArguments) command).getArgs().isEmpty()) {
                for (CommandArgument arg : ((SlashCommandArguments) command).getArgs()) {
                    data.addOption(arg.optionType(), arg.argument(), arg.description(), arg.required());
                }
            }
        }

        guild.upsertCommand(data).queue();
        Logger.info("Registered command: " + command.getCommandName() + " (" + command.getClass().getSimpleName() + ")");
    }

    public void registerCommands(SlashCommand... commands) {
        for (SlashCommand command : commands) {
            registerCommand(command);
        }
    }

    public void registerCommandsInPackage(String pkg) {
        Reflections reflections = new Reflections(pkg);
        Set<Class<? extends SlashCommand>> classes = reflections.getSubTypesOf(SlashCommand.class);

        classes.forEach(aClass -> {
            try {
                registerCommand(aClass.getConstructor().newInstance());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                e.printStackTrace();
            }
        });
    }

    public void unregisterCommand(SlashCommand command) {
        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            Logger.error("Couldn't find the guild specified in the bot config!");
            return;
        }

        guild.deleteCommandById(command.getCommandName()).queue();
        commands.remove(command);
    }

    public SlashCommand getCommand(String name) {
        for (SlashCommand command : commands) {
            if (command.getCommandName().equals(name))
                return command;
        }
        return null;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

}
