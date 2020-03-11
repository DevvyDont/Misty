package sh.niall.misty.utils.reminders;

import net.dv8tion.jda.api.EmbedBuilder;
import org.bson.Document;
import sh.niall.misty.cogs.Reminders;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.misty.utils.ui.paginator.Paginator;
import sh.niall.misty.utils.ui.paginator.buttons.PaginatorOption;
import sh.niall.yui.commands.Context;

import java.awt.*;

public class RemindersDeleteButton extends PaginatorOption {

    private Reminders reminders;
    private Context ctx;

    public RemindersDeleteButton(Paginator paginator, Context ctx, Reminders reminders) {
        super(paginator);
        emoji = "❌";
        this.reminders = reminders;
        this.ctx = ctx;
        postIfSinglePage = true;
    }

    @Override
    public void run() {
        Document document = ((RemindersPaginator) paginator).getCurrentDocument();
        if (this.reminders.attemptDelete(ctx, document)) {
            if (!paginator.deleteCurrentPage()) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Reminders");
                embedBuilder.setDescription("You have no more reminders!");
                embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getUser().getEffectiveAvatarUrl());
                embedBuilder.setColor(Color.RED);
                paginator.gotoPage(paginator.addPage(embedBuilder));
            }
            paginator.refresh();
        }
    }
}
