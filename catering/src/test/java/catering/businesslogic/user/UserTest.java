package catering.businesslogic.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import catering.persistence.PersistenceManager;

class UserTest {

    @BeforeEach
    void init() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
    }

    @Test
    void savedRoles_SurviveAReload() {
        User u = new User("nuovo.organizzatore");
        u.addRole(User.Role.ORGANIZZATORE);
        u.addRole(User.Role.CHEF);
        assertTrue(u.save());

        User reloaded = User.load(u.getId());

        assertTrue(reloaded.isOrganizer());
        assertTrue(reloaded.isChef());
        assertFalse(reloaded.isCook());
        assertFalse(reloaded.isServiceStaff());
    }
}
