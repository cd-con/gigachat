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
import org.spongepowered.api.command.spec.CommandSpec;
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
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionService;
import rustythecodeguy.gigachat.commands.ReloadConfigCommand;

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
    public String shortMessageInGlobalChat = "Your message is too short!";
    public String noPlayersInChannel = "Nobody heard you!";
    public String joinMessage = "[+] %playername%";
    public String leaveMessage = "[-] %playername%";

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

        Sponge.getCommandManager()
                .register(
                        this,
                        CommandSpec.builder()
                                .executor(new ReloadConfigCommand(this))
                                .permission("gigachat.admin.reload")
                                .build(),
                        "reloadchad"
                );
        Sponge.getServiceManager()
                .provideUnchecked(PermissionService.class)
                .newDescriptionBuilder(this)
                .assign(PermissionDescription.ROLE_ADMIN, true)
                .id("gigachat.admin.reload")
                .description(Text.of("Allows a user to reload plugin config"))
                .register();
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

        // Replace event message
        event.setMessage(applyOldMinecraftFormat(Text.of(joinMessage)));

        // Show plugin guide, if player playing for the first time
        if (!player.hasPlayedBefore()) {
            AbstractMutableMessageChannel PMChannel = new AbstractMutableMessageChannel() {};

            PMChannel.clearMembers();
            PMChannel.addMember(player);

            // TODO add message to config
            PMChannel.send(Text.of(""));
            PMChannel.send(Text.builder("Hello, " + player.getName() + "!").color(TextColors.GOLD).build());
            PMChannel.send(Text.builder("Sending messages without '!' avaliable in radius of %chat_range% blocks").build());
            PMChannel.send(Text.builder("To use the global chat, write '!' at the beginning of the message").build());

        }
    }

    @Listener
    public void onPlayerLeave(ClientConnectionEvent.Disconnect event) {
        // Replace event message
        event.setMessage(applyOldMinecraftFormat(Text.of(leaveMessage)));

        // Remove player from global chat
        Player player = event.getTargetEntity();
        logger.info("Player %player_name% left from server!".replace("%player_name%", player.getName()));
        globalChannel.removeMember(player);
    }

    /**
     * Calls on every message from player
     *
     * @param event
     */
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
                        globalChannel.send(applyOldMinecraftFormat(Text.of(TextColors.GREEN, "[G] ", getPrefix(player), TextColors.RESET, player.getName(), getSuffix(player), TextColors.RESET, ":", clearText.replaceFirst("!", ""))));
                }else{
                    player.sendMessage(applyOldMinecraftFormat(Text.of(shortMessageInGlobalChat.replace("%global_chat_min_message_length%", String.valueOf(minimumWordsInMessage)))));
                }
            }
            else {
                // Local chat code

                // Get nearby players and add them in local channel
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
                localChannel.send(applyOldMinecraftFormat(Text.of(TextColors.YELLOW, "[L] ", getPrefix(player), TextColors.RESET, player.getName(), getSuffix(player), TextColors.RESET, ":", clearText)));

                // Show message for player, if he was alone
                if ((long) localChannel.getMembers().size() < 2){
                    player.sendMessage(applyOldMinecraftFormat(Text.of(noPlayersInChannel)));
                }
            }
            // Log chat event in logger
            // TODO make own file to log messages
            logger.info("%player_name% said: %message%".replace("%player_name%", player.getName()).replace("%message%",clearText));

            // Set event completed
            event.setCancelled(true);
        }
    }

    /**
     * Do some actions on server stop
     *
     * @param event - GameStoppedEvent from SpongeAPI
     */
    @Listener
    public void onServerStopped(GameStoppedEvent event) {
        logger.info("GigaChat has been stopped! See you later!");
    }

    /**
     * Get player prefix from permission plugin
     *
     * @param player - Player class
     * @return String
     */
    private String getPrefix(Player player){
        // TODO implement more permission plugins
        if (!permissionProvider_LuckPerms.isPresent() || useChatFormatting){
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

    /**
     * Get player suffix from permission plugin
     *
     * @param player - Player class
     * @return String
     */
    private String getSuffix(Player player){
        // TODO implement more permission plugins
        if (!permissionProvider_LuckPerms.isPresent() || useChatFormatting){
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

    /**
     * Applies old Minecraft formatting
     *
     * @param text - Text builder object
     * @return Text.builder()
     */
    @Deprecated
    private Text applyOldMinecraftFormat(Text text){
        return Text.builder(TextSerializers.LEGACY_FORMATTING_CODE.serialize(text)).build();
    }


    /**
     * Reload plugin configuration from file
     *
     * @throws IOException - config file not found
     */
    // Config
    public void reloadConfig() throws IOException
    {
        if (!cfgPath.exists() & Sponge.getAssetManager().getAsset(this, "gigachat.cfg").isPresent())
        {
            Sponge.getAssetManager().getAsset(this, "gigachat.cfg").get().copyToFile(cfgPath.toPath());
        }
        CommentedConfigurationNode cfg = configManager.load();
        // Global node
        switchPermissionPlugin(Short.parseShort(cfg.getNode("global", "permissionPluginType").getString()));

        // Chat node
        localChatRadius = Double.parseDouble(cfg.getNode("chat", "localChatRadius").getString());
        globalChatSymbol = cfg.getNode("chat", "globalChatSymbol").getString().charAt(0);
        minimumWordsInMessage = Double.parseDouble(cfg.getNode("chat", "minimumWordsInMessage").getString());

        // Custom messages node
        whitelistMessage = cfg.getNode("customMessages", "whitelistMessage").getString();
        noPlayersInChannel = cfg.getNode("customMessages", "noPlayersNearMessage").getString();
        shortMessageInGlobalChat = cfg.getNode("customMessages", "emptyGlobalChatMessage").getString();
        joinMessage = cfg.getNode("customMessages", "joinMessage").getString();
        leaveMessage = cfg.getNode("customMessages", "leaveMessage").getString();
    }

    /**
     * Reload permission plugin
     *
     * @param permissionPluginID - 0 = disable permission plugin formatting; 1 = LuckPerms
     * @throws NullPointerException -
     */
    private void switchPermissionPlugin(int permissionPluginID) throws NullPointerException
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
            logger.error("Unable to reload permission plugin! Chosen permission plugin (ID " + permissionPluginID + ") is not found");
            logger.warn("If you want learn more about this error, visit https://github.com/cd-con/gigachat/wiki");
        }
    }
}
