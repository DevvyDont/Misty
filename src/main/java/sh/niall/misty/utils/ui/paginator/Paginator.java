package sh.niall.misty.utils.ui.paginator;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import sh.niall.misty.utils.ui.paginator.buttons.NextPageButton;
import sh.niall.misty.utils.ui.paginator.buttons.PaginatorOption;
import sh.niall.misty.utils.ui.paginator.buttons.PreviousPageButton;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;
import sh.niall.yui.waiter.WaiterStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Paginator {

    // Basic variables
    protected int timeout;
    protected Context ctx;
    protected List<EmbedBuilder> pages;
    protected int currentPage = 0;
    protected List<PaginatorOption> options = new ArrayList<>(Arrays.asList(new PreviousPageButton(this), new NextPageButton(this)));
    private boolean anyoneCanUse;

    // Post message sent items
    protected long globalTimeout;
    private Message embedMessage;

    public Paginator(Context ctx, List<EmbedBuilder> pages, int timeout, boolean anyoneCanUse) {
        this.ctx = ctx;
        this.pages = new ArrayList<>(pages);
        this.timeout = timeout;
        this.anyoneCanUse = anyoneCanUse;
    }

    public void run() throws CommandException, WaiterException {
        if (pages.isEmpty())
            throw new CommandException("Paginator started without any pages!");
        pages = setPageNumbers(pages);
        embedMessage = ctx.send(pages.get(currentPage).build());
        globalTimeout = Instant.now().getEpochSecond() + 300; // 5 minute timeout
        if (updateReactions() != 0)
            waitForReaction();
    }

    private int updateReactions() {
        int amountOfPages = pages.size();
        int posted = 0;
        for (PaginatorOption option : options) {
            if (option.shouldPost(amountOfPages)) {
                embedMessage.addReaction(option.getEmoji()).complete();
                posted++;
            } else
                embedMessage.removeReaction(option.getEmoji()).complete();
        }
        return posted;
    }

    private void stop() {
        embedMessage.clearReactions().queue();
    }

    private void waitForReaction() throws WaiterException {
        WaiterStorage waiterStorage = new WaiterStorage(
                GuildMessageReactionAddEvent.class,
                check -> {
                    GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) check;
                    boolean correctMessage = e.getMessageIdLong() == embedMessage.getIdLong();
                    boolean correctUser;
                    if (anyoneCanUse)
                        correctUser = e.getUser().getIdLong() == ctx.getAuthor().getIdLong();
                    else
                        correctUser = e.getUser().getIdLong() != ctx.getMe().getIdLong();
                    return (correctMessage && correctUser);
                },
                timeout,
                TimeUnit.SECONDS
        );
        ctx.getYui().getEventWaiter().waitForNewEvent(waiterStorage); // Blocks until .getEvent is returned
        GuildMessageReactionAddEvent event = (GuildMessageReactionAddEvent) waiterStorage.getEvent();
        if (event == null) {
            stop();
            return;
        }
        MessageReaction reaction = event.getReaction();
        reaction.removeReaction(ctx.getUser()).queue();
        String reactionEmoji = reaction.getReactionEmote().getEmoji();
        for (PaginatorOption option : options) {
            if (option.equals(reactionEmoji))
                option.run();
        }
        if (globalTimeout < Instant.now().getEpochSecond()) {
            stop();
            return;
        }
        waitForReaction();
    }

    public int addPage(EmbedBuilder embedBuilder) {
        if (pages.contains(embedBuilder))
            return -1;

        pages.add(embedBuilder);
        return pages.indexOf(embedBuilder);
    }

    public boolean deleteCurrentPage() {
        pages.remove(currentPage);
        if (pages.size() == 0)
            return false;

        if (currentPage >= pages.size())
            currentPage = 0;
        return true;
    }

    public void gotoPage(int index) {
        if (index >= pages.size())
            return;
        currentPage = index;
        refresh();
    }

    public void refresh() {
        pages = setPageNumbers(pages);
        embedMessage.editMessage(pages.get(currentPage).build()).queue();
        updateReactions();
    }


    /* Button methods */
    public void nextPage() {
        currentPage++;
        if (currentPage == pages.size()) {
            currentPage = 0;
        }
        refresh();
    }

    public void previousPage() {
        currentPage--;
        if (currentPage == -1) {
            currentPage = pages.size() - 1;
        }
        refresh();
    }

    public static List<EmbedBuilder> setPageNumbers(List<EmbedBuilder> embeds) {
        List<EmbedBuilder> output = new ArrayList<>();
        int page = 1;
        int total = embeds.size();
        for (EmbedBuilder embedBuilder : embeds) {
            embedBuilder.setFooter(String.format("Page %s of %s", page, total));
            output.add(embedBuilder);
            page++;
        }
        return output;
    }
}
