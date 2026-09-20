package catering.businesslogic.event;

import java.time.LocalDateTime;

import catering.persistence.PersistenceManager;

final class PersonnelAvailabilityUtil {

    private PersonnelAvailabilityUtil() {
    }

    static boolean hasDeclaredAvailability(int userId, LocalDateTime inizio, LocalDateTime fine) {
        final boolean[] found = { false };
        String query = "SELECT id FROM PersonnelAvailability " +
                "WHERE user_id = ? AND inizio <= ? AND fine >= ?";

        PersistenceManager.executeQuery(query, rs -> found[0] = true,
                userId, inizio.format(ServiceShift.DB_DATETIME), fine.format(ServiceShift.DB_DATETIME));

        return found[0];
    }
}
