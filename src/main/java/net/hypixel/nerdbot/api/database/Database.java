package net.hypixel.nerdbot.api.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.Channel;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Region;
import net.hypixel.nerdbot.util.Users;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Database implements ServerMonitorListener {

    private static Database instance;

    private final MongoCollection<GreenlitMessage> greenlitCollection;
    private final MongoCollection<ChannelGroup> channelCollection;
    private final MongoCollection<DiscordUser> userCollection;
    private final MongoClient mongoClient;

    private boolean connected;

    private Database() {
        ConnectionString connectionString = new ConnectionString(System.getProperty("mongodb.uri"));
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .applyToServerSettings(builder -> {
                    builder.heartbeatFrequency(Region.isDev() ? 1 : 10, TimeUnit.SECONDS);
                    builder.addServerMonitorListener(this);
                })
                .build();

        mongoClient = MongoClients.create(clientSettings);
        MongoDatabase database = mongoClient.getDatabase("skyblockNerds");
        greenlitCollection = database.getCollection("greenlitMessages", GreenlitMessage.class);
        channelCollection = database.getCollection("channelGroups", ChannelGroup.class);
        userCollection = database.getCollection("users", DiscordUser.class);
    }


    public static Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    @Override
    public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
        if (!connected) connected = true;

        if (System.getProperty("show-heartbeats") != null && System.getProperty("show-heartbeats").equals("true")) {
            log("Heartbeat successful! Elapsed time: " + event.getElapsedTime(TimeUnit.MILLISECONDS) + "ms");
        }
    }

    @Override
    public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
        error("Heartbeat failed! Reason: " + event.getThrowable().getMessage());

        NerdBotApp.EXECUTOR_SERVICE.submit(() -> {
            if (connected) {
                TextChannel channel = ChannelManager.getChannel(Channel.CURATE);
                User user = Users.getUser(Users.AERH.getUserId());
                if (channel == null || user == null) {
                    error("Couldn't notify of database error on Discord!");
                    return;
                }
                channel.sendMessage(user.getAsMention() + " I lost connection to the database! Pls fix!").queue();
            }
            connected = false;
        });
    }

    public void disconnect() {
        if (connected) {
            mongoClient.close();
            connected = false;
        }
    }

    private void log(String message) {
        Logger.info("[Database] " + message);
    }

    private void error(String message) {
        Logger.error("[Database] " + message);
    }

    public boolean isConnected() {
        return connected;
    }

    public List<Document> get(String collection, String field, Object value) {
        return mongoClient.getDatabase("skyblockNerds").getCollection(collection).find(Filters.eq(field, value)).into(new ArrayList<>());
    }

    public void insertGreenlitMessage(GreenlitMessage greenlitMessage) {
        InsertOneResult result = greenlitCollection.insertOne(greenlitMessage);
        log("Inserted greenlit message " + result.getInsertedId());
    }

    public void insertGreenlitMessages(List<GreenlitMessage> greenlitMessages) {
        InsertManyResult result = greenlitCollection.insertMany(greenlitMessages);
        log("Inserted " + result.getInsertedIds().size() + " greenlit messages");
    }

    public GreenlitMessage getGreenlitMessage(String id) {
        return greenlitCollection.find(Filters.eq("messageId", id)).first();
    }

    public void updateGreenlitMessage(GreenlitMessage greenlitMessage) {
        UpdateResult result = greenlitCollection.updateOne(Filters.eq("messageId", greenlitMessage.getMessageId()), new Document("$set", greenlitMessage));
        if (result.getMatchedCount() == 0) {
            log("Couldn't find greenlit message " + greenlitMessage.getId() + " to update");
        } else {
            log(result.getModifiedCount() + " greenlit message(s) updated");
        }
    }

    public void deleteGreenlitMessage(String field, Object value) {
        DeleteResult result = greenlitCollection.deleteOne(Filters.eq(field, value));
        log(result.getDeletedCount() + " greenlit message(s) deleted");
    }

    public List<GreenlitMessage> getGreenlitCollection() {
        return new ArrayList<>(this.greenlitCollection.find().into(new ArrayList<>()));
    }

    public ChannelGroup getChannelGroup(String channel) {
        return channelCollection.find(Filters.eq("name", channel)).first();
    }

    public void insertChannelGroup(ChannelGroup channelGroup) {
        channelCollection.insertOne(channelGroup);
        log("Inserted channel group " + channelGroup.getName());
    }

    public void insertChannelGroups(List<ChannelGroup> channelGroups) {
        channelCollection.insertMany(channelGroups);
        log("Inserted " + channelGroups.size() + " channel groups");
    }

    public void deleteChannelGroup(ChannelGroup channelGroup) {
        deleteChannelGroup(channelGroup.getName());
    }

    public void deleteChannelGroup(String name) {
        channelCollection.deleteOne(Filters.eq("name", name));
        log("Deleted channel group " + name);
    }

    public List<ChannelGroup> getChannelGroups() {
        return this.channelCollection.find().into(new ArrayList<>());
    }

    public List<DiscordUser> getUsers() {
        return this.userCollection.find().into(new ArrayList<>());
    }

    public DiscordUser getUser(String field, Object value) {
        return this.userCollection.find(Filters.eq(field, value)).first();
    }

    public DiscordUser getUser(String id) {
        return getUser("discordId", id);
    }

    public void insertUser(DiscordUser user) {
        userCollection.insertOne(user);
        log("Inserted user " + user.getDiscordId());
    }

    public void updateUser(String field, Object value, DiscordUser user) {
        UpdateResult result = userCollection.replaceOne(Filters.eq(field, value), user);
        if (result.getMatchedCount() == 0) {
            log("Couldn't find user " + user.getDiscordId() + " to update");
        } else {
            log(result.getModifiedCount() + " user(s) updated");
        }
    }

    public void updateUsers(List<DiscordUser> users) {
        if (users.isEmpty()) return;

        for (DiscordUser user : users) {
            updateUser("discordId", user.getDiscordId(), user);
        }
    }

    public void deleteUser(String field, Object value) {
        userCollection.deleteOne(Filters.eq(field, value));
        log("Deleted user " + field + ":" + value);
    }

    public void deleteUser(DiscordUser user) {
        deleteUser("discordId", user.getDiscordId());
    }

}
