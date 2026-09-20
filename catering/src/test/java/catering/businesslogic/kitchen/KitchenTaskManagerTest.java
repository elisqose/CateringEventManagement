package catering.businesslogic.kitchen;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.event.Event;
import catering.businesslogic.event.Service;
import catering.persistence.PersistenceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the gestire-compiti-cucina DSD
 * (slides/class-14-16-progettazione/gestire-compiti-cucina/generateSummarySheet.uxf).
 * <p>
 * Each test exercises one clause of the DSD's ALT-block precondition,
 * plus the happy path, per the slide directives in
 * slides/class-18-25-codice/Lab SAS 21 Testing.pdf.
 */
class KitchenTaskManagerTest {

    private static CatERing app;

    private Event event;
    private Service service;

    @BeforeAll
    static void init() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
    }

    @BeforeEach
    void setUp() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Antonio"); // Antonio is the chef of the seeded event
        event = Event.loadByName("Gala Aziendale Annuale");
        service = Service.loadByName("Pranzo Buffet Aziendale");
    }

    @Test
    void testGenerateSummarySheet_HappyPath_OneTaskPerKitchenProcess() throws UseCaseLogicException {
        int expected = service.getMenu().getNeededKitchenProcesses().size();

        SummarySheet sheet = app.getKitchenTaskManager().generateSummarySheet(event, service);

        assertNotNull(sheet);
        assertEquals(expected, sheet.getTaskList().size(),
                "one KitchenTask per element of getNeededKitchenProcesses()");
        assertSame(sheet, app.getKitchenTaskManager().getCurrentSummarySheet());
    }

    @Test
    void testGenerateSummarySheet_NonChefCaller_ThrowsUseCaseLogicException() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Luca"); // cook, not chef
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, service));
    }

    @Test
    void testGenerateSummarySheet_ChefNotEventChef_ThrowsUseCaseLogicException() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Chiara"); // chef, but not the event's chef
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, service));
    }

    @Test
    void testGenerateSummarySheet_ServiceNotInEvent_ThrowsUseCaseLogicException() {
        Service detached = new Service("Detached"); // never added to event
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, detached));
    }

    @Test
    void testGenerateSummarySheet_ServiceWithoutMenu_ThrowsUseCaseLogicException() {
        Service emptyService = new Service("No menu");
        event.addService(emptyService);
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, emptyService));
    }

    @Test
    void testGenerateSummarySheet_NullEvent_ThrowsUseCaseLogicException() {
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(null, service));
    }

    @Test
    void testGenerateSummarySheet_NullService_ThrowsUseCaseLogicException() {
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, null));
    }
}
