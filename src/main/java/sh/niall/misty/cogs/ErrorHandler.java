package sh.niall.misty.cogs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;
import sh.niall.yui.cogs.cog.Cog;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.exceptions.CommandException;

public class ErrorHandler extends Cog {

    Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    @Override
    public void onError(Context ctx, Exception error, boolean thisCog) {
        boolean printError = true;
        String errorMessage = null;

        if (error instanceof CommandException || error instanceof AudioException || error instanceof MistyException) {
            printError = false;
            errorMessage = "⚠️ " + error.getMessage();
        } else {
            errorMessage = "I don't know how to handle this error, please ask my developer for help!";
        }

        if (ctx != null)
            ctx.sendq(errorMessage, null);

        if (printError) {
            String errorHeader = "I ran into the following error:";
            if (ctx != null)
                errorHeader = String.format(
                        "User %s (%s) in %s (%s) ran into the following error saying %s",
                        ctx.getAuthorUser().getName(),
                        ctx.getAuthorUser().getIdLong(),
                        (ctx.isGuildMessage()) ? ctx.getGuild().getName() : "Dm Channel",
                        (ctx.isGuildMessage()) ? ctx.getGuild().getIdLong() : "N/A",
                        ctx.getMessage()
                );

            logger.error(errorHeader);
            error.printStackTrace();
        }
    }
}
