package catering.businesslogic.event;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

@DisplayName("Evento — comportamento dell'aggregato e caricamento")
class EventTest {

    @BeforeAll
    static void initializeDatabase() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
    }

    @Nested
    @DisplayName("Aggregato in memoria")
    class Aggregate {

        private Event event;
        private Service first;
        private Service second;

        @BeforeEach
        void setUp() {
            User organizer = new User("Test Organizer");
            organizer.setId(99);

            event = Event.create("Villa Reale", LocalDate.of(2026, 6, 15), 2, 2, 100, "note", "Acme",
                    organizer.asOrganizer());
            first = new Service("first");
            first.setId(1);
            second = new Service("second");
            second.setId(2);
        }

        @Test
        void testCreate_SetsAllAttributesAndBozzaState() {
            assertEquals("Villa Reale", event.getLuogo());
            assertEquals(LocalDate.of(2026, 6, 15), event.getDataInizio());
            assertEquals(2, event.getDurata());
            assertEquals(2, event.getNumeroServiziRichiesti());
            assertEquals(100, event.getNumeroPartecipanti());
            assertEquals(100, event.getNumPartecipantiIniziale());
            assertEquals("Acme", event.getCliente());
            assertEquals(EventStatus.BOZZA, event.getStato());
            assertTrue(event.getServizi().isEmpty());
        }

        @Test
        void testId_SetExplicitly_IsReadable() {
            event.setId(42);
            assertEquals(42, event.getId());
        }

        @Test
        void testChef_SetExplicitly_IsReadable() {
            User chef = new User();
            chef.setId(7);

            event.setChef(chef);

            assertEquals(chef, event.getChef());
            assertEquals(7, event.getChefId());
        }

        @Test
        void testAddService_RecordsContainment() {
            event.addService(first);

            assertTrue(event.containsService(first));
            assertFalse(event.containsService(second));
        }

        @Test
        void testRemoveService_DropsOnlyTheTargetedService() {
            event.addService(first);
            event.addService(second);

            event.removeService(first);

            assertFalse(event.containsService(first));
            assertTrue(event.containsService(second));
        }

        @Test
        void testIsModificabile_FreshEvent_IsBozzaSoModifiable() {
            assertTrue(event.isModificabile());
            assertTrue(event.isEliminabile());
            assertFalse(event.isAnnullabile());
        }

        @Test
        void testIsVariazionePartecipantiEccessiva_Over30Percent_ReturnsTrue() {
            assertTrue(event.isVariazionePartecipantiEccessiva(140));
            assertFalse(event.isVariazionePartecipantiEccessiva(110));
        }

        @Test
        void testAggiornaAttributi_UpdatesOnlyProvidedFields() {
            event.aggiornaAttributi(null, null, null, null, 50, "nuove note", null);

            assertEquals("Villa Reale", event.getLuogo());
            assertEquals(50, event.getNumeroPartecipanti());
            assertEquals(50, event.getNumPartecipantiIniziale());
            assertEquals("nuove note", event.getNote());
        }
    }

    @Nested
    @DisplayName("Caricamento dal database")
    class StaticLoaders {

        @Test
        void testLoadAllEvents_ReturnsSeededEvents() {
            List<Event> events = Event.loadAllEvents();

            assertNotNull(events);
            assertFalse(events.isEmpty(), "the seed script must populate at least one event");

            Event sample = events.get(0);
            assertNotNull(sample.getLuogo());
            assertNotNull(sample.getDataInizio());
            assertNotNull(sample.getServizi());
        }

        @Test
        void testLoadById_RoundTripsTheSameEvent() {
            Event sample = Event.loadAllEvents().get(0);

            Event loaded = Event.loadById(sample.getId());

            assertNotNull(loaded);
            assertEquals(sample.getId(), loaded.getId());
            assertEquals(sample.getLuogo(), loaded.getLuogo());
        }

        @Test
        void testLoadByName_FindsTheSeededEvent() {
            Event loaded = Event.loadByName("Gala Aziendale Annuale");

            assertNotNull(loaded);
            assertEquals("Antonio", loaded.getChef().getUserName());
        }
    }
}
