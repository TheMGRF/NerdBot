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
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.generator.GeneratorBuilder;
import net.hypixel.nerdbot.generator.ImageMerger;
import net.hypixel.nerdbot.generator.StringColorParser;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Queue;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.hypixel.nerdbot.generator.GeneratorStrings.*;

@Log4j2
public class GeneratorCommands extends ApplicationCommand {
    private final GeneratorBuilder builder;

    public GeneratorCommands() {
        super();
        this.builder = new GeneratorBuilder();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "item", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void generateItem(GuildSlashEvent event,
                             @AppOption(description = DESC_ITEM_NAME) String itemName,
                             @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                             @AppOption(description = DESC_ITEM_LORE) String itemLore,
                             @Optional @AppOption(description = DESC_TYPE) String type,
                             @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                             @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                             @Optional @AppOption(description = DESC_PADDING) Integer padding,
                             @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();
        // building the item's description
        BufferedImage generatedImage = builder.buildItem(event, itemName, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }

        // Log item gen activity
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "text", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock")
    public void generateText(GuildSlashEvent event, @AppOption(description = DESC_TEXT) String message, @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();
        // building the chat message
        BufferedImage generatedImage = builder.buildItem(event, "NONE", "NONE", message, "", true, 0, 1, StringColorParser.MAX_FINAL_LINE_LENGTH, false);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }

        // Log item gen activity
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "display_image", description = "Draws a Minecraft item into a file")
    public void generateItemImage(GuildSlashEvent event,
                             @AppOption(description = DESC_ITEM_ID, name = "item_id") String itemID,
                             @Optional @AppOption(description = DESC_EXTRA_ITEM_MODIFIERS) String extraDetails,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        BufferedImage item = builder.buildUnspecifiedItem(event, itemID, extraDetails, true);
        if (item != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(item))).setEphemeral(hidden).queue();
        }

        // Log item gen activity
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "full", description = "Generates a full item stack!")
    public void generateFullItem(GuildSlashEvent event,
                                 @Optional @AppOption(description = DESC_ITEM_NAME) String itemName,
                                 @Optional @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                                 @Optional @AppOption(description = DESC_ITEM_LORE) String itemLore,
                                 @Optional @AppOption(description = DESC_TYPE) String type,
                                 @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                                 @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                                 @Optional @AppOption(description = DESC_PADDING) Integer padding,
                                 @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                                 @Optional @AppOption(description = DESC_ITEM_ID, name = "display_item_id") String itemID,
                                 @Optional @AppOption(description = DESC_EXTRA_ITEM_MODIFIERS) String extraModifiers,
                                 @Optional @AppOption(description = DESC_RECIPE) String recipe,
                                 @Optional @AppOption(description = DESC_RENDER_INVENTORY) Boolean renderBackground,
                                 @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        // checking that there are two or more different items to merge the images
        if ((itemName == null || rarity == null || itemLore == null) && itemID == null && recipe == null) {
            event.getHook().sendMessage(MISSING_FULL_GEN_ITEM).queue();
            return;
        }

        extraModifiers = Objects.requireNonNullElse(extraModifiers, "");
        renderBackground = Objects.requireNonNullElse(renderBackground, true);

        // building the description for the item
        BufferedImage generatedDescription = null;
        if (itemName != null && rarity != null && itemLore != null) {
            generatedDescription = builder.buildItem(event, itemName, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
            if (generatedDescription == null) {
                return;
            }
        }

        // building the item for the which is beside the description
        BufferedImage generatedItem = null;
        if (itemID != null) {
           generatedItem = builder.buildUnspecifiedItem(event, itemID, extraModifiers, false);
            if (generatedItem == null) {
                return;
            }
        }

        // building the recipe for the item
        BufferedImage generatedRecipe = null;
        if (recipe != null) {
            generatedRecipe = builder.buildRecipe(event, recipe, renderBackground);
            if (generatedRecipe == null) {
                return;
            }
        }

        ImageMerger merger = new ImageMerger(generatedDescription, generatedItem, generatedRecipe);
        merger.drawFinalImage();
        event.getHook().sendFiles(FileUpload.fromData(Util.toFile(merger.getImage()))).setEphemeral(hidden).queue();

        // Log item gen activity
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "recipe", description = "Generates a Minecraft Recipe Image")
    public void generateRecipe(GuildSlashEvent event,
                               @AppOption(description = DESC_RECIPE) String recipe,
                               @Optional @AppOption(description = DESC_RENDER_INVENTORY) Boolean renderBackground,
                               @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        renderBackground = (renderBackground == null || renderBackground);

        // building the Minecraft recipe
        BufferedImage generatedRecipe = builder.buildRecipe(event, recipe, renderBackground);
        if (generatedRecipe != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedRecipe))).queue();
        }

        // Log item gen activity
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "parse", description = "Converts a minecraft item into a Nerd Bot item!")
    public void parseItemDescription(GuildSlashEvent event,
                                     @AppOption(description = DESC_PARSE_ITEM, name = "item_nbt") String itemNBT,
                                     @Optional @AppOption (description = DESC_INCLUDE_ITEM) Boolean includeItem,
                                     @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden
    ) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }

        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();
        includeItem = Objects.requireNonNullElse(includeItem, false);

        // converting the nbt into json
        JsonObject itemJSON;
        try {
            itemJSON = NerdBotApp.GSON.fromJson(itemNBT, JsonObject.class);
        } catch (JsonSyntaxException e) {
            event.getHook().sendMessage(ITEM_PARSE_JSON_FORMAT).queue();
            return;
        }

        // checking if the user has copied the text directly from in game
        JsonObject tagJSON = Util.isJsonObject(itemJSON, "tag");
        if (tagJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("tag")).queue();
            return;
        }

        // checking if there is a display tag
        JsonObject displayJSON = Util.isJsonObject(tagJSON, "display");
        if (displayJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("display")).queue();
            return;
        }
        // checking that there is a name and lore parameters in the JsonObject
        String itemName = Util.isJsonString(displayJSON, "Name");
        JsonArray itemLoreArray = Util.isJsonArray(displayJSON, "Lore");
        if (itemName == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Name")).queue();
            return;
        } else if (itemLoreArray == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Lore")).queue();
            return;
        }
        itemName = itemName.replaceAll("§", "&");

        String itemID = "";
        String extraModifiers = "";
        // checking if the user wants to create full gen
        if (includeItem) {
            itemID = Util.isJsonString(itemJSON, "id");
            if (itemID == null) {
                event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("id")).queue();
                return;
            }
            itemID = itemID.replace("minecraft:", "");

            if (itemID.equals("skull")) {
                // checking if there is a SkullOwner json object within the main tag json
                JsonObject skullOwnerJSON = Util.isJsonObject(tagJSON, "SkullOwner");
                if (skullOwnerJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("SkullOwner")).queue();
                    return;
                }
                // checking if there is a Properties json object within SkullOwner
                JsonObject propertiesJSON = Util.isJsonObject(skullOwnerJSON, "Properties");
                if (propertiesJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Properties")).queue();
                    return;
                }
                // checking if there is a textures json object within properties
                JsonArray texturesJSON = Util.isJsonArray(propertiesJSON, "textures");
                if (texturesJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("textures")).queue();
                    return;
                }
                // checking that there is only one json object in the array
                if (texturesJSON.size() != 1) {
                    event.getHook().sendMessage(MULTIPLE_ITEM_SKULL_DATA).queue();
                    return;
                } else if (!texturesJSON.get(0).isJsonObject()) {
                    event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
                    return;
                }
                // checking that there is a Base64 skin url string
                String base64String = Util.isJsonString(texturesJSON.get(0).getAsJsonObject(), "Value");
                if (base64String == null) {
                    event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
                    return;
                }
                // converting the Base64 string into the Skin URL
                try {
                    extraModifiers = builder.base64ToSkinURL(base64String) + ",false";
                } catch (NullPointerException | IllegalArgumentException e) {
                    event.getHook().sendMessage(INVALID_BASE_64_SKIN_URL).queue();
                    return;
                }
            } else {
                // checking if there is a color attribute present and adding it to the extra attributes
                String color = Util.isJsonString(displayJSON, "color");
                if (color != null) {
                    try {
                        Integer selectedColor = Integer.decode(color);
                        extraModifiers = String.valueOf(selectedColor);
                    } catch (NumberFormatException ignored) {
                    }
                }

                // checking if the item is enchanted and applying the enchantment glint to the extra modifiers
                JsonArray enchantJson = Util.isJsonArray(tagJSON, "ench");
                if (enchantJson != null) {
                    extraModifiers = extraModifiers.length() == 0 ? "enchant" : extraModifiers + ",enchant";
                }
            }
        }

        // adding all the text to the string builders
        StringBuilder itemGenCommand = new StringBuilder("/").append(COMMAND_PREFIX).append(includeItem ? " full" : " item");
        StringBuilder itemText = new StringBuilder();
        itemText.append(itemName).append("\\n");
        itemGenCommand.append(" item_name:").append(itemName).append(" rarity:NONE item_lore:");

        // adding the entire lore to the string builder
        int maxLineLength = 0;
        for (JsonElement element : itemLoreArray) {
            String itemLore = element.getAsString().replaceAll("§", "&").replaceAll("`", "");
            itemText.append(itemLore).append("\\n");
            itemGenCommand.append(itemLore).append("\\n");

            if (maxLineLength < itemLore.length()) {
                maxLineLength = itemLore.length();
            }
        }
        maxLineLength++;
        itemGenCommand.replace(itemGenCommand.length() - 2, itemGenCommand.length(), "").append(" max_line_length:").append(maxLineLength);
        itemText.replace(itemText.length() - 2, itemText.length(), "");
        // checking if there was supposed to be an item stack is displayed with the item
        if (includeItem) {
            itemGenCommand.append(" display_item_id:").append(itemID).append(extraModifiers.length() != 0 ? " extra_modifiers:" + extraModifiers : "");
        }

        // creating the generated description
        BufferedImage generatedDescription = builder.buildItem(event, "NONE", "NONE", itemText.toString(), "NONE", false, 255, 0, maxLineLength, true);
        if (generatedDescription == null) {
            event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();
            return;
        }

        // checking if an item should be displayed alongside the description
        if (includeItem) {
            BufferedImage generatedItem = builder.buildUnspecifiedItem(event, itemID, extraModifiers, false);
            if (generatedItem == null) {
                return;
            }

            ImageMerger merger = new ImageMerger(generatedDescription, generatedItem, null);
            merger.drawFinalImage();
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(merger.getImage()))).setEphemeral(hidden).queue();
        } else {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedDescription))).setEphemeral(false).queue();
        }

        event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();

        // Log item gen activity
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "args", description = "Show help related to the arguments of the Item Generation command.")
    public void genArgsHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
            .setAuthor("SkyBlock Nerd Bot")
            .setTitle("Item Generation")
            .setColor(Color.GREEN)
            .addField("Basic Info", ITEM_BASIC_INFO, true)
            .addField("Item Arguments", ITEM_INFO_ARGUMENTS, false)
            .addField("Head Arguments", HEAD_INFO_ARGUMENTS, false);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "colors", description = "Show help related to the colors of the Item Generation command.")
    public void genColorHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
            .setAuthor("SkyBlock Nerd Bot")
            .setTitle("Item Generation")
            .setColor(Color.GREEN)
            .addField("Basic Info", ITEM_BASIC_INFO, true)
            .addField("Colors", ITEM_COLOR_CODES, false);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "other", description = "Show help related to the other aspects of the Item Generation command.")
    public void genOtherHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
            .setAuthor("SkyBlock Nerd Bot")
            .setTitle("Item Generation")
            .setColor(Color.GREEN)
            .addField("Basic Info", ITEM_BASIC_INFO, true)
            .addField("Item Generation", ITEM_OTHER_INFO, false)
            .addField("Head Generation", HEAD_INFO_OTHER_INFORMATION, false);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "full", description = "Show a full help page for the Item Generation command.")
    public void genFullHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
            .setAuthor("SkyBlock Nerd Bot")
            .setTitle("Item Generation")
            .setColor(Color.GREEN)
            .addField("Full Info", FULL_GEN_INFO, false);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "heads", description = "Show help related to the Head Generation command.")
    public void askForRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
            .setAuthor("SkyBlock Nerd Bot")
            .setTitle("Head Generation")
            .addField("Basic Info", HEAD_INFO_BASIC, true);

        argumentBuilder.setColor(Color.GREEN)
            .addField("Arguments", HEAD_INFO_ARGUMENTS, false);

        extraInfoBuilder.setColor(Color.GRAY)
            .addField("Other Information", HEAD_INFO_OTHER_INFORMATION, false);


        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "recipes", description = "Show help related to the Recipe Generation command.")
    public void askForRecipeRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
            .setAuthor("Skyblock Nerd Bot")
            .setTitle("Recipe Generation Help")
            .addField("Basic Info", RECIPE_INFO_BASIC, true);

        argumentBuilder.setColor(Color.GREEN)
            .addField("Arguments", RECIPE_INFO_ARGUMENTS, false);

        extraInfoBuilder.setColor(Color.GRAY)
            .addField("Other Information", RECIPE_INFO_OTHER_INFORMATION, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "symbols", description = "Show a list of all stats symbols")
    public void showAllStats(GuildSlashEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("All Available Symbols").setColor(Color.GREEN);
        StringBuilder idBuilder = new StringBuilder();
        StringBuilder symbolBuilder = new StringBuilder();
        StringBuilder displayBuilder = new StringBuilder();

        for (Stat stat : Stat.VALUES) {
            idBuilder.append("%%").append(stat.name()).append("%%").append("\n");
            symbolBuilder.append(stat.getIcon()).append("\n");
            displayBuilder.append(stat.getDisplay()).append("\n");
        }

        embedBuilder.addField("ID", idBuilder.toString(), true);
        embedBuilder.addField("Symbol", symbolBuilder.toString(), true);
        embedBuilder.addField("Display", displayBuilder.toString(), true);

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }

    private boolean isIncorrectChannel(GuildSlashEvent event) {
        String senderChannelId = event.getChannel().getId();
        String[] itemGenChannelIds = NerdBotApp.getBot().getConfig().getChannelConfig().getGenChannelIds();

        if (itemGenChannelIds == null) {
            event.reply("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return true;
        }

        if (Util.safeArrayStream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            // The top channel in the config should be considered the 'primary channel', which is referenced in the
            // error message.
            TextChannel channel = ChannelManager.getChannel(itemGenChannelIds[0]);
            if (channel == null) {
                event.reply("This can only be used in the item generating channel.").setEphemeral(true).queue();
                return true;
            }
            event.reply("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue();
            return true;
        }

        return false;
    }
}

