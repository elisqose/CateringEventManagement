package catering.businesslogic.kitchen;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Time;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.event.Event;
import catering.businesslogic.event.Service;
import catering.businesslogic.shift.Shift;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

/**
 * Integration tests for {@link SummarySheet} that exercise the full
 * generate-then-assign flow against the seeded SQLite database. Each test
 * creates its own fresh {@link SummarySheet} in {@link BeforeEach} and is
 * therefore independent of the others.
 */
class SummarySheetTest {

    private static CatERing app;
    private static User chef;
    private static User cook;
    private static Event event;
    private static Service service;

    @BeforeAll
    static void initializeRuntime() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
        chef = User.load("Antonio");
        cook = User.load("Luca");
        event = Event.loadByName("Gala Aziendale Annuale");
        service = Service.loadByName("Pranzo Buffet Aziendale");
    }

    private SummarySheet sheet;

    @BeforeEach
    void generateFreshSheet() throws UseCaseLogicException {
        app.getUserManager().fakeLogin(chef.getUserName());
        sheet = app.getKitchenTaskManager().generateSummarySheet(event, service);
    }

    @Test
    void testGeneratedSheet_IsOwnedByChefAndPopulatedWithTasks() {
        assertNotNull(sheet);
        assertEquals(chef, sheet.getOwner(), "sheet owner must be the chef who generated it");
        assertFalse(sheet.getTaskList().isEmpty(), "task list must mirror the menu's kitchen processes");
        assertSame(sheet, app.getKitchenTaskManager().getCurrentSummarySheet());
    }

    @Test
    void testAssignTask_WithBookedCook_RecordsAssignmentOnSheet() throws UseCaseLogicException {
        KitchenTask task = sheet.getTaskList().get(0);
        Shift shift = bookedShiftFor(cook);

        Assignment assignment = app.getKitchenTaskManager().assignTask(task, shift, cook);

        assertNotNull(assignment);
        assertEquals(task, assignment.getTask());
        assertEquals(cook, assignment.getCook());
        assertEquals(shift, assignment.getShift());
        assertTrue(sheet.getAssignments().contains(assignment),
                "the new assignment must appear on the current sheet");
    }

    private static Shift bookedShiftFor(User user) {
        Shift shift = new Shift(
                Date.valueOf("2025-04-07"),
                Time.valueOf("09:00:00"),
                Time.valueOf("14:00:00"));
        shift.addBooking(user);
        return shift;
    }
}
