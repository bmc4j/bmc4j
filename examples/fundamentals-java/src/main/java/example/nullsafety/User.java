package example.nullsafety;

/** An ordinary value object — never null in our data. */
public final class User {

    public final int id;
    public final boolean isAdmin;

    public User(int id, boolean isAdmin) {
        this.id = id;
        this.isAdmin = isAdmin;
    }
}
