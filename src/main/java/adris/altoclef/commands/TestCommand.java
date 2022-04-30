package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Playground;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.BonemealNearbyGrassTask;


public class TestCommand extends Command {
    public TestCommand() {super("test", "Generic command for testing");}

    @Override
    protected void call(AltoClef altoClef, ArgParser parser) throws CommandException {
        altoClef.log("Whas Boppin!");
        altoClef.runUserTask(new BonemealNearbyGrassTask(), this::finish);
    }
}