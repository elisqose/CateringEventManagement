package catering.businesslogic.event;

import catering.businesslogic.user.User;

public class Organizer {

    private final User user;

    public Organizer(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public int getId() {
        return user != null ? user.getId() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Organizer))
            return false;
        Organizer other = (Organizer) obj;
        return this.user != null && this.user.equals(other.user);
    }

    @Override
    public int hashCode() {
        return user != null ? user.hashCode() : 0;
    }
}
