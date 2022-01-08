package rustythecodeguy.gigachat;

import java.io.File;
import java.io.IOException;
import java.util.*;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppedEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.ProviderRegistration;
import org.spongepowered.api.service.whitelist.WhitelistService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.AbstractMutableMessageChannel;
import org.spongepowered.api.text.format.TextColors;
import com.google.inject.Inject;
import org.spongepowered.api.text.serializer.TextSerializers;

// TODO add more permission plugins support

@Plugin(
        id = "gigachat",
        name = "GigaChat",
        description = "Simple chat manager /w LuckPerms support",
        version = "0.2-BETA",
        authors = {
                "RustyTheCodeguy"
        }
)
public class GigaChatMain {
    private final info InfoClassInstance = new info();

    public AbstractMutableMessageChannel globalChannel;

    // Messages
    public double minimumWordsInMessage = 1;
    public double localChatRadius = 32;
    public String whitelistMessage = "You're not whitelisted on this server";
    public String shortMessageInGlobalChat = "";
    public String bassistMessage = "Nobody heard you!";

    // Chat formatting
    private Boolean useChatFormatting = false;
    private Character globalChatSymbol;
    private Optional<ProviderRegistration<LuckPerms>> permissionProvider_LuckPerms;


    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;
    @Inject
    @DefaultConfig(sharedRoot = true)
    private File cfgPath;

    @Inject
    Logger logger;

    @Listener
    public void onServerStarting(GamePreInitializationEvent event) {
        logger.info("GigaChat starting up and injecting changes...");
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        logger.info("GigaChat by "+InfoClassInstance.getAuthors()+" version " + InfoClassInstance.getPluginVersion());
        globalChannel = new AbstractMutableMessageChannel(){};
        logger.info("Registered global channel!");

        // TODO Resolve this fucking error
        //Sponge.getCommandManager().register(GigaChatMain.class,
        //        CommandSpec.builder().permission("gigachat.command").child(
        //                CommandSpec.builder().permission("gigachat.command.reload").executor(new ReloadConfigCommand(GigaChatMain.this)).build(), "reload", "reload-config").build(), "cwl");
        try
        {
            reloadConfig();
        }
        catch (java.io.IOException e)
        {
            logger.error("An exception occurred while setting up plugin! Recheck the settings and run /gigachat reload!");
            logger.error("Full error trace watch at debug.log");
            logger.debug("Log trace\n"+e.getMessage());
            logger.warn("Loading fallback settings due initialization error!");

            // Fallback settings
            globalChatSymbol = "!".charAt(0);
        }
        logger.info("Local chat radius is set to %chat_range% blocks".replace("%chat_range%", String.valueOf(localChatRadius)));
    }

    @Listener
    public void onPlayer(ClientConnectionEvent.Auth event)
    {
        // Thanks pearxteam for this code
        // Ported by me
        if (Sponge.getServer().hasWhitelist())
        {
            Optional<WhitelistService> wls = Sponge.getServiceManager().provide(WhitelistService.class);
            if (wls.isPresent()) {
                if (!wls.get().isWhitelisted(event.getProfile())) {
                    event.setCancelled(true);
                    event.setMessage(applyOldMinecraftFormat(Text.of(whitelistMessage)));
                }
            }
        }
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        Player player = event.getTargetEntity();
        globalChannel.addMember(player);

        if (!player.hasPlayedBefore()) {
            AbstractMutableMessageChannel playerMessageChannel = new AbstractMutableMessageChannel() {
            };

            playerMessageChannel.clearMembers();
            playerMessageChannel.addMember(player);

            playerMessageChannel.send(Text.of(""));
            playerMessageChannel.send(Text.builder("Hello, " + player.getName() + "!").color(TextColors.GOLD).build());
            playerMessageChannel.send(Text.builder("Sending messages without '!' avaliable in radius of %chat_range% blocks").build());
            playerMessageChannel.send(Text.builder("To use the global chat, write '!' at the beginning of the message").build());

        }
    }

    @Listener
    public void onPlayerLeave(ClientConnectionEvent.Disconnect event) {
        // Need to remove?
        Player player = event.getTargetEntity();
        logger.info("Player %player_name% left from server, so removing him from global chat".replace("%player_name%", player.getName()));
        globalChannel.removeMember(player);
    }

    @Listener
    public void onChat(MessageChannelEvent.Chat event) {
        Optional<Player> optionalPlayer = event.getCause().first(Player.class);
        if(optionalPlayer.isPresent()) {
            // Get player, if available
            final Player player = optionalPlayer.get();

            // Create local channel and add event caller into it
            AbstractMutableMessageChannel localChannel = new AbstractMutableMessageChannel(){};
            localChannel.clearMembers();
            localChannel.addMember(player);

            // Get message text
            Text message = event.getMessage();
            String messageString = message.toPlain();
            String clearText = messageString.replace(player.getName(), "").replace("<","").replace(">", "");

            // Detect '!'
            if (clearText.replace(" ", "").charAt(0) == globalChatSymbol)
            {
                // Global chat code
                if(clearText.split(" ").length > minimumWordsInMessage){
                    // TODO YANDERE-DEV CODE DETECTED!!!!
                    if (useChatFormatting) {
                        globalChannel.send(applyOldMinecraftFormat(Text.of(TextColors.GREEN, "[G] ", getPrefix(player), TextColors.RESET, player.getName(), getSuffix(player), TextColors.RESET, ":", clearText.replaceFirst("!", ""))));
                    }else{
                        globalChannel.send(applyOldMinecraftFormat(Text.of(TextColors.GREEN, "[G] ", TextColors.RESET, player.getName(), TextColors.RESET, ":", clearText.replaceFirst("!", ""))));
                    }
                }else{
                    player.sendMessage(applyOldMinecraftFormat(Text.of(shortMessageInGlobalChat.replace("%global_chat_min_message_length%", String.valueOf(minimumWordsInMessage)))));
                }
            }
            else {
                // Local chat code
                //Get nearby players and add them in local channel
                Collection<Entity> entities = player.getNearbyEntities(localChatRadius);
                Iterator<Entity> iterator = entities.iterator();
                Entity entity;
                Player targetPlayer;
                while (iterator.hasNext()) {
                    entity = iterator.next();
                    if (entity.getType().equals(EntityTypes.PLAYER)) {
                        targetPlayer = (Player) entity;
                        localChannel.addMember(targetPlayer);
                    }
                }

                // Send message
                // TODO YANDERE-DEV CODE DETECTED
                if (useChatFormatting) {
                    localChannel.send(applyOldMinecraftFormat(Text.of(TextColors.YELLOW, "[L] ", getPrefix(player), TextColors.RESET, player.getName(), getSuffix(player), TextColors.RESET, ":", clearText)));
                }else {
                    localChannel.send(applyOldMinecraftFormat(Text.of(TextColors.YELLOW, "[L] ", TextColors.RESET, player.getName(), TextColors.RESET, ":", clearText)));
                }
                // Show message for player, if he was alone
                if ((long) localChannel.getMembers().size() < 2){
                    player.sendMessage(applyOldMinecraftFormat(Text.of(bassistMessage)));
                }
            }
            // Log chat event in logger
            // TODO make own file to log messages
            logger.info("%player_name% said: %message%".replace("%player_name%", player.getName()).replace("%message%",clearText));

            // Set event completed
            event.setCancelled(true);
        }
    }
    @Listener
    public void onServerStopped(GameStoppedEvent event) {
        logger.info("GigaChat has been stopped! See you later!");
    }

    private String getPrefix(Player player){
        // TODO implement more permission plugins
        if (!permissionProvider_LuckPerms.isPresent()){
            logger.warn("Permission service (LuckPerms) not found! Prefixes was disabled!");
            return "";
        }else{
            User user = permissionProvider_LuckPerms.get().getProvider().getPlayerAdapter(Player.class).getUser(player);
            String prefix = user.getCachedData().getMetaData().getPrefix();
            if (prefix == null){
                return "";
            }
            return prefix;
        }
    }

    private String getSuffix(Player player){
        // TODO implement more permission plugins
        if (!permissionProvider_LuckPerms.isPresent()){
            logger.warn("Permission service (LuckPerms) not found! Prefixes was disabled!");
            return "";
        }else{
            User user = permissionProvider_LuckPerms.get().getProvider().getPlayerAdapter(Player.class).getUser(player);
            String suffix = user.getCachedData().getMetaData().getSuffix();
            if (suffix == null){
                return "";
            }
            return suffix;
        }
    }

    private Text applyOldMinecraftFormat(Text text){
        return Text.builder(TextSerializers.LEGACY_FORMATTING_CODE.serialize(text)).build();
    }


    // Config
    public void reloadConfig() throws IOException
    {
        if (!cfgPath.exists() & Sponge.getAssetManager().getAsset(this, "gigachat.cfg").isPresent())
        {
            Sponge.getAssetManager().getAsset(this, "gigachat.cfg").get().copyToFile(cfgPath.toPath());
        }
        CommentedConfigurationNode cfg = configManager.load();
        // Global node
        reloadPermissionPlugin(Short.parseShort(cfg.getNode("global", "permissionPluginType").getString()));

        // Chat node
        localChatRadius = Double.parseDouble(cfg.getNode("chat", "localChatRadius").getString());
        globalChatSymbol = cfg.getNode("chat", "globalChatSymbol").getString().charAt(0);
        minimumWordsInMessage = Double.parseDouble(cfg.getNode("chat", "minimumWordsInMessage").getString());

        // Custom messages node
        whitelistMessage = cfg.getNode("customMessages", "whitelistMessage").getString();
        bassistMessage = cfg.getNode("customMessages", "noPlayersNearMessage").getString();
        shortMessageInGlobalChat = cfg.getNode("customMessages", "emptyGlobalChatMessage").getString();
    }

    private void reloadPermissionPlugin(short permissionPluginID) throws NullPointerException
    {
        try{
            switch (permissionPluginID){
                case 0:
                    // Permission-based formatting disabled
                    useChatFormatting = false;
                    break;

                case 1:
                    // LuckPerms formatting
                    useChatFormatting = true;
                    permissionProvider_LuckPerms = Sponge.getGame().getServiceManager().getRegistration(LuckPerms.class);
                    break;

                default:
                    logger.error("Unable to reload permission plugin - permissionPluginType is incorrect!");
                    useChatFormatting = false;

            }
        }catch (NullPointerException e){
            logger.error("Unable to reload permission plugin!");
        }
    }
}