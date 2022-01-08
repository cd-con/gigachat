package rustythecodeguy.gigachat.commands;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.text.Text;
import rustythecodeguy.gigachat.GigaChatMain;

import java.io.IOException;

public class ReloadConfigCommand implements CommandExecutor
{
    private GigaChatMain gigaChatMain;

    public ReloadConfigCommand(GigaChatMain gigaChatMain)
    {
        this.gigaChatMain = gigaChatMain;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException
    {
        try
        {
            gigaChatMain.reloadConfig();
        }
        catch (IOException e)
        {
            throw new CommandException(Text.of("An exception occurred while reloading the CWL plugin!"), e);
        }
        return CommandResult.success();
    }
}