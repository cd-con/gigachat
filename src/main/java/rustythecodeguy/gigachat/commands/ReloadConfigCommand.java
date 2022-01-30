package rustythecodeguy.gigachat.commands;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.source.CommandBlockSource;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import rustythecodeguy.gigachat.GigaChatMain;
import rustythecodeguy.gigachat.utils.classInstancer;

import java.io.IOException;

public class ReloadConfigCommand implements CommandExecutor
{
    private final GigaChatMain gigaChatMain;
    private final classInstancer Instance = new classInstancer();

    public ReloadConfigCommand(GigaChatMain gigaChatMain)
    {
        this.gigaChatMain = gigaChatMain;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException
    {
        try
        {
            if (src instanceof Player) {
                Player player = (Player) src;
                gigaChatMain.sendPM(player, Instance.utilClassInstance.applyOldMinecraftFormat(Text.of(TextColors.GREEN + "Plugin reloaded!")));
            }
            else if(src instanceof ConsoleSource) {
                src.sendMessage(Text.of("Plugin reloaded!"));
            }
            else if(src instanceof CommandBlockSource) {
                src.sendMessage(Text.of("This is not allowed!"));
                throw new CommandException(Text.of("This command cannot be executed from command block!"));
            }
            gigaChatMain.reloadConfig();

        }
        catch (IOException e)
        {
            if (src instanceof Player) {
                Player player = (Player) src;
                gigaChatMain.sendPM(player, Instance.utilClassInstance.applyOldMinecraftFormat(Text.of(TextColors.RED + "An error occurred while plugin reloading. Check console.")));
            }
            throw new CommandException(Text.of("An exception occurred while reloading plugin!"), e);
        }
        return CommandResult.success();
    }
}