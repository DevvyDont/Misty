package sh.niall.misty.utils.reminders;

import net.dv8tion.jda.api.EmbedBuilder;
import org.bson.Document;
import sh.niall.misty.cogs.Reminders;
import sh.niall.misty.utils.ui.paginator.Paginator;
import sh.niall.yui.commands.Context;

import java.util.ArrayList;
import java.util.Map;

public class RemindersPaginator extends Paginator {

    private Map<EmbedBuilder, Document> pagesMap;

    public RemindersPaginator(Context ctx, Map<EmbedBuilder, Document> pagesMap, int timeout, Reminders reminders) {
        super(ctx, new ArrayList<>(pagesMap.keySet()), timeout, false);
        options.add(new RemindersDeleteButton(this, ctx, reminders));
        this.pagesMap = pagesMap;
    }

    protected Document getCurrentDocument() {
        return this.pagesMap.get(pages.get(currentPage));
    }
}
