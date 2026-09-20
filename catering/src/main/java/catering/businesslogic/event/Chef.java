package catering.businesslogic.event;

import java.time.LocalDate;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

public class Chef {

    private final User user;

    public Chef(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public int getId() {
        return user != null ? user.getId() : 0;
    }

    public boolean isDisponibile(Event evento) {
        if (user == null || evento == null || evento.getDataInizio() == null) {
            return false;
        }

        LocalDate start = evento.getDataInizio();
        LocalDate end = start.plusDays(Math.max(evento.getDurata(), 1) - 1);

        final boolean[] conflict = { false };
        String query = "SELECT data_inizio, durata, stato FROM Events WHERE chef_id = ? AND id != ? "
                + "AND stato NOT IN ('ANNULLATO', 'TERMINATO')";

        PersistenceManager.executeQuery(query, rs -> {
            LocalDate otherStart = LocalDate.parse(rs.getString("data_inizio"));
            int otherDurata = Math.max(rs.getInt("durata"), 1);
            LocalDate otherEnd = otherStart.plusDays(otherDurata - 1);

            if (!otherEnd.isBefore(start) && !otherStart.isAfter(end)) {
                conflict[0] = true;
            }
        }, user.getId(), evento.getId());

        return !conflict[0];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Chef))
            return false;
        Chef other = (Chef) obj;
        return this.user != null && this.user.equals(other.user);
    }

    @Override
    public int hashCode() {
        return user != null ? user.hashCode() : 0;
    }
}
