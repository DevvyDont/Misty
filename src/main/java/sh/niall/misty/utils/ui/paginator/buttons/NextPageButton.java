package sh.niall.misty.utils.ui.paginator.buttons;

import sh.niall.misty.utils.ui.paginator.Paginator;

public class NextPageButton extends PaginatorOption {

    public NextPageButton(Paginator paginator) {
        super(paginator);
        emoji = "➡️";
    }

    @Override
    public void run() {
        paginator.nextPage();
    }
}
