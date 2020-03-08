package sh.niall.misty.utils.ui.paginator.buttons;

import sh.niall.misty.utils.ui.paginator.Paginator;

public class PaginatorOption {

    protected String emoji = null;
    protected Paginator paginator;
    protected boolean postIfSinglePage = false;

    public PaginatorOption(Paginator paginator) {
        this.paginator = paginator;
    }

    public void run() {
        System.out.println("Run isn't implemented!");
    }

    public String getEmoji() {
        return emoji;
    }

    public boolean shouldPost(int pageAmount) {
        if (pageAmount > 1)
            return true;

        return postIfSinglePage;
    }

    @Override
    public boolean equals(Object obj) {
        if (emoji == null)
            return false;

        if (!obj.getClass().equals(emoji.getClass()))
            return false;

        return emoji.equals(obj);
    }
}
