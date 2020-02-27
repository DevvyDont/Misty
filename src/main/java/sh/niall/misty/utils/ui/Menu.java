package sh.niall.misty.utils.ui;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;
import sh.niall.yui.waiter.WaiterStorage;

import java.util.concurrent.TimeUnit;

public class Menu {

    public static int showMenu(Context ctx, String question, String[] options) throws WaiterException, CommandException {
        return showMenu(ctx, question, options, 3);
    }

    public static int showMenu(Context ctx, String question, String[] options, int maxAttempts) throws CommandException, WaiterException {
        int attempts = 0;

        // Ensure it ends with a new line
        if (!question.endsWith("\n"))
            question += "\n";

        // Ask the question
        StringBuilder builder = new StringBuilder(question);
        int menuCount = 0;
        for (String option : options) {
            menuCount++;
            builder.append(String.format("%s. %s\n", menuCount, option));
        }
        ctx.send(builder.toString() + String.format("(Reply menu number to select [1-%s])", menuCount));

        // Loop over for their attempts
        while (attempts < maxAttempts) {
            attempts++;

            // Listen for their message
            WaiterStorage waiterStorage = new WaiterStorage(
                    GuildMessageReceivedEvent.class,
                    check -> {
                        GuildMessageReceivedEvent e = (GuildMessageReceivedEvent) check;
                        return (e.getChannel().getIdLong() == ctx.getChannel().getIdLong()) && (e.getAuthor().getIdLong() == ctx.getAuthor().getIdLong());
                    }, 15, TimeUnit.SECONDS
            );
            ctx.getYui().getEventWaiter().waitForNewEvent(waiterStorage);
            GuildMessageReceivedEvent messageEvent = (GuildMessageReceivedEvent) waiterStorage.getEvent();

            // Quit the menu if they don't respond in time
            if (messageEvent == null)
                throw new CommandException("Quitting menu due to timeout");

            // Get the message and validate it's an int
            String message = messageEvent.getMessage().getContentRaw();
            if (!message.matches("\\d+")) {
                ctx.send("Invalid option, please only provide a number. Which option would you like?");
                continue;
            }

            // Make sure it's a valid option
            int menuOption = Integer.parseInt(message);
            if (menuOption < 0 || menuCount < menuOption) {
                ctx.send("Invalid option, please try again. hich option would you like?");
                continue;
            }

            // Return the result
            return menuOption;
        }
        // If we run out of attempts, throw an error
        throw new CommandException("Quitting menu due to too many failed attempts.");
    }

}
