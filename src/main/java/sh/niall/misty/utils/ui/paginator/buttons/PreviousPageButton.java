package sh.niall.misty.utils.ui.paginator.buttons;

import sh.niall.misty.utils.ui.paginator.Paginator;

public class PreviousPageButton extends PaginatorOption {

    public PreviousPageButton(Paginator paginator) {
        super(paginator);
        emoji = "⬅️";
    }

    @Override
    public void run() {
        paginator.previousPage();
    }
}
