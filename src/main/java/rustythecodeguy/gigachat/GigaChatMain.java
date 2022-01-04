package rustythecodeguy.gigachat;

import java.util.*;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
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
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.AbstractMutableMessageChannel;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.entity.living.player.tab.TabList;
import com.google.inject.Inject;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.text.serializer.TextSerializers;


@Plugin(
        id = "gigachat",
        name = "GigaChat",
        description = "Simple chat manager /w LuckPerms support",
        version = "0.1-BETA-MineReichBuild",
        authors = {
                "RustyTheCodeguy"
        }
)
public class GigaChatMain {
        public AbstractMutableMessageChannel globalChannel;
        public Optional<ProviderRegistration<LuckPerms>> permissionProvider_LuckPerms;
        @Inject
        Logger logger;

        @Listener
        public void onServerStarting(GamePreInitializationEvent event) {
            logger.info("GigaChat starting up and injecting changes...");
        }

        @Listener
        public void onServerStart(GameStartedServerEvent event) {
            logger.info("GigaChat by rusty.the.codeguy (cd-con) version 0.1");
            logger.info("Local chat radius is set to 18 blocks");

            globalChannel = new AbstractMutableMessageChannel(){};
            permissionProvider_LuckPerms = Sponge.getGame().getServiceManager().getRegistration(LuckPerms.class);
        }

        @Listener
        public void onPlayerJoin(ClientConnectionEvent.Join event) {
            Player player = event.getTargetEntity();
            TabList tablist = player.getTabList();
            tablist.setFooter(Text.of(TextColors.RED, TextStyles.BOLD, "MineReich"));
            globalChannel.addMember(player);

            if(!player.hasPlayedBefore()) {
                AbstractMutableMessageChannel playerMessageChannel = new AbstractMutableMessageChannel(){};

                playerMessageChannel.clearMembers();
                playerMessageChannel.addMember(player);

                playerMessageChannel.send(Text.of(""));
                playerMessageChannel.send(Text.builder("Hello, " + player.getName() + "!").color(TextColors.GOLD).build());
                playerMessageChannel.send(Text.builder("Sending messages without '!' avaliable in radius of 32 blocks").build());
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
                // Get player, if avaliable
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
                if (clearText.replace(" ", "").charAt(0) == "!".charAt(0))
                {       
                    // Global chat code
                    globalChannel.send(applyMinecraftFormat(Text.of(TextColors.GREEN, "[G] ", getPrefix(player), TextColors.RESET, player.getName(), getSuffix(player), TextColors.RESET, ":", clearText.replace("!", ""))));
                }
                else {
                    // Local chat code
                    final double localChatRadius = 32;
                    
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
                    localChannel.send(applyMinecraftFormat(Text.of(TextColors.YELLOW, "[L] ", getPrefix(player), TextColors.RESET, player.getName(), getSuffix(player), TextColors.RESET, ":", clearText)));
                    
                    // Show message for player, if he was alone
                    if ((long) localChannel.getMembers().size() < 2){
                        player.sendMessage(Text.of(TextColors.RED, "Nobody heard you"));
                    }
                }
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

        private Text applyMinecraftFormat(Text text){
            return Text.builder(TextSerializers.LEGACY_FORMATTING_CODE.serialize(text)).build();
        }
    }
