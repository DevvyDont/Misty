package sh.niall.misty.utils.ui;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.YuiException;
import sh.niall.yui.waiter.WaiterStorage;

import java.util.concurrent.TimeUnit;

public class Menu {

    /**
     * Creates a menu and waits for the users input.
     *
     * @param ctx      The context to send it in
     * @param question The question to ask
     * @param options  The options the user can pick
     * @return The option the user picked
     * @throws CommandException Thrown if the user times out
     */
    public static int showMenu(Context ctx, String question, String[] options) throws YuiException {
        return showMenu(ctx, question, options, 3);
    }

    /**
     * Creates a menu and waits for the users input.
     *
     * @param ctx         The context to send it in
     * @param question    The question to ask
     * @param options     The options the user can pick
     * @param maxAttempts How many attempts they're allowed
     * @return The option the user picked
     * @throws CommandException Thrown if the user times out
     */
    public static int showMenu(Context ctx, String question, String[] options, int maxAttempts) throws YuiException {
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
            ctx.getYui().waitForNewEvent(waiterStorage);
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
