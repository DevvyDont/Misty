package sh.niall.misty.utils.ui;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import sh.niall.misty.errors.MistyException;
import sh.niall.yui.Yui;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;
import sh.niall.yui.waiter.WaiterStorage;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class Paginator {

    private long channelId;
    private long guildId;
    private Yui yui;
    private JDA jda;
    private List<MessageEmbed> pages;
    private int timeout;
    private long messageId;

    // Emojis
    String leftArrow = "⬅️";
    String rightArrow = "➡️";

    // Indexs
    private int currentIndex = 0;

    public Paginator(Yui yui, TextChannel channel, List<MessageEmbed> pages, int timeout) throws CommandException {
        if (pages.isEmpty())
            throw new CommandException("No pages were found!");

        this.pages = pages;
        this.yui = yui;
        this.jda = yui.getJda();
        this.channelId = channel.getIdLong();
        this.guildId = channel.getGuild().getIdLong();
        this.timeout = timeout;
    }

    public void run() {
        TextChannel channel = jda.getGuildById(this.guildId).getTextChannelById(this.channelId);
        Message message = channel.sendMessage(pages.get(0)).complete();
        if (pages.size() == 1)
            return;
        messageId = message.getIdLong();
        message.addReaction(leftArrow).complete();
        message.addReaction(rightArrow).complete();
        waitForReaction();
    }

    private void waitForReaction() {
        WaiterStorage waiterStorage = new WaiterStorage(GuildMessageReactionAddEvent.class, check -> {
            GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) check;
            return (e.getMessageIdLong() == messageId) && (e.getMember().getIdLong() != jda.getSelfUser().getIdLong());
        }, timeout, TimeUnit.SECONDS);
        yui.getEventWaiter().waitForNewEvent(waiterStorage);
        try {
            handleNewReaction((GuildMessageReactionAddEvent) waiterStorage.getEvent());
        } catch (WaiterException e) {
            yui.getErrorHandler().onError(null, new MistyException("Error waiting for reaction"));
        }
    }

    private void handleNewReaction(GuildMessageReactionAddEvent event) {
        if (event == null) {
            jda.getGuildById(guildId).getTextChannelById(channelId).retrieveMessageById(messageId).queue(message -> message.clearReactions().queue());
            return;
        }

        MessageReaction reaction = event.getReaction();
        if (reaction.getReactionEmote().getEmoji().equals(leftArrow))
            previousPage();
        else if (reaction.getReactionEmote().getEmoji().equals(rightArrow))
            nextPage();
        reaction.removeReaction(event.getUser()).queue();
        waitForReaction();
    }

    private void nextPage() {
        currentIndex++;
        if (currentIndex == pages.size()) {
            currentIndex = 0;
        }
        refresh();
    }

    private void previousPage() {
        currentIndex--;
        if (currentIndex == -1) {
            currentIndex = pages.size() - 1;
        }
        refresh();
    }

    private void refresh() {
        jda.getGuildById(guildId).getTextChannelById(channelId).retrieveMessageById(messageId).queue(message -> {
            message.editMessage(pages.get(currentIndex)).queue();
        });
    }
}
