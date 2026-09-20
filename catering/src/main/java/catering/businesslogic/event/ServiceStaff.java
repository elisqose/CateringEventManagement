package catering.businesslogic.event;

import java.time.LocalDateTime;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

public class ServiceStaff {

    private final User user;

    public ServiceStaff(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public int getId() {
        return user != null ? user.getId() : 0;
    }

    public boolean isDisponibile(ServiceShift turno) {
        if (user == null || turno == null || turno.getInizio() == null) {
            return false;
        }

        LocalDateTime inizio = turno.getInizio();
        LocalDateTime fine = turno.getFine();

        boolean declared = PersonnelAvailabilityUtil.hasDeclaredAvailability(user.getId(), inizio, fine);
        if (!declared) {
            return false;
        }

        final boolean[] conflict = { false };
        String query = "SELECT sa.id FROM ServiceAssignments sa " +
                "JOIN ServiceShifts ss ON sa.turno_id = ss.id " +
                "WHERE sa.personale_id = ? AND ss.id != ? AND ss.luogo != ? " +
                "AND NOT (ss.fine <= ? OR ss.inizio >= ?)";

        PersistenceManager.executeQuery(query, rs -> conflict[0] = true,
                user.getId(), turno.getId(), turno.getLuogo(),
                inizio.format(ServiceShift.DB_DATETIME), fine.format(ServiceShift.DB_DATETIME));

        return !conflict[0];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ServiceStaff))
            return false;
        ServiceStaff other = (ServiceStaff) obj;
        return this.user != null && this.user.equals(other.user);
    }

    @Override
    public int hashCode() {
        return user != null ? user.hashCode() : 0;
    }
}
