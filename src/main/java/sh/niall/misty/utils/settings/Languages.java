package sh.niall.misty.utils.settings;

public enum Languages {
    English (0);

    private final int id;

    Languages(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
