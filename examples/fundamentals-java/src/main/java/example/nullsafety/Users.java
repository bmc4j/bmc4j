package example.nullsafety;

/** Lookups over a list of users. The null lives entirely in this code. */
public final class Users {

    private Users() {
    }

    /** The first admin, or {@code null} if there isn't one. */
    public static User admin(User[] users) {
        for (User u : users) {
            if (u.isAdmin) {
                return u;
            }
        }
        return null;
    }

    /** BUG: assumes an admin always exists — NPEs when none does. */
    public static int adminId(User[] users) {
        return admin(users).id;
    }

    /** Safe: handles the no-admin case. */
    public static int adminIdOrDefault(User[] users, int fallback) {
        User found = admin(users);
        return found == null ? fallback : found.id;
    }
}
