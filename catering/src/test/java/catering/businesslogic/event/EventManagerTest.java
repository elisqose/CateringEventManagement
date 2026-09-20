package catering.businesslogic.event;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

@DisplayName("Gestire gli eventi — operazioni di sistema")

class EventManagerTest {

    private static CatERing app;

    @BeforeEach
    void init() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
    }

    private EventManager mgr() {
        return app.getEventManager();
    }

    @Nested
    @DisplayName("Creazione della scheda evento (contratto 1)")
    class CreaSchedaEvento {

        @BeforeEach
        void login() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca"); // organizer
        }

        @Test
        void happyPath_CreatesEventInBozzaAndSelectsIt() throws UseCaseLogicException {
            LocalDate data = LocalDate.now().plusDays(30);

            Event e = mgr().creaSchedaEvento("Villa Reale", data, 2, 2, 80, "note", "Cliente X");

            assertNotNull(e);
            assertEquals(EventStatus.BOZZA, e.getStato());
            assertEquals(80, e.getNumPartecipantiIniziale());
            assertSame(e, mgr().getCurrentEvent());
            assertTrue(e.getId() > 0, "the event must have been persisted");
        }

        @Test
        void nonOrganizerCaller_Throws() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Luca"); // cook, not organizer
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().creaSchedaEvento("X", LocalDate.now().plusDays(30), 1, 1, 10, "", "C"));
        }

        @Test
        void insufficientNotice_ThrowsEccezione1a() {
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().creaSchedaEvento("X", LocalDate.now().plusDays(5), 1, 1, 10, "", "C"));
        }
    }

    @Nested
    @DisplayName("Creazione di un evento ricorrente (contratto 1c.1)")
    class CreaSchedaEventoRicorrente {

        @BeforeEach
        void login() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
        }

        @Test
        void happyPath_CreatesOneInstancePerOccurrence() throws UseCaseLogicException {
            LocalDate start = LocalDate.now().plusDays(20);

            RecurringEvent me = mgr().creaSchedaEventoRicorrente("Villa Reale", start, 1, 1, 40, "", "C",
                    "WEEKLY", null, 3);

            assertEquals(3, me.getIstanze().size());
            assertEquals(start, me.getIstanze().get(0).getDataInizio());
            assertEquals(start.plusWeeks(2), me.getIstanze().get(2).getDataInizio());
            for (Event istanza : me.getIstanze()) {
                assertEquals(me.getId(), istanza.getMacroEventoId());
            }
        }

        @ParameterizedTest(name = "frequenza {0}")
        @ValueSource(strings = { "DAILY", "WEEKLY", "MONTHLY" })
        @DisplayName("Ogni frequenza genera il numero di istanze richiesto")
        void happyPath_DifferentFrequencies(String frequenza) throws UseCaseLogicException {
            LocalDate start = LocalDate.now().plusDays(20);

            RecurringEvent me = mgr().creaSchedaEventoRicorrente("Villa Reale", start, 1, 1, 40, "", "Cliente",
                    frequenza, null, 3);

            assertEquals(3, me.getIstanze().size());
            assertEquals(start, me.getIstanze().get(0).getDataInizio());
            assertEquals(frequenza, me.getFrequenza().getTipo());
            assertTrue(me.getIstanze().get(2).getDataInizio().isAfter(me.getIstanze().get(1).getDataInizio()));
        }

        @Test
        void bothEndConditionsSpecified_Throws() {
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().creaSchedaEventoRicorrente("X", LocalDate.now().plusDays(20), 1, 1, 10, "", "C",
                            "WEEKLY", LocalDate.now().plusMonths(2), 3));
        }
    }

    @Nested
    @DisplayName("Aggiunta di un servizio all'evento (contratto 3)")
    class AggiungiServizio {

        private Event event;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(30), 2, 1, 50, "", "C");
        }

        @Test
        void happyPath_AddsServiceToCurrentEvent() throws UseCaseLogicException {
            Service s = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 50);

            assertNotNull(s);
            assertTrue(event.containsService(s));
            assertEquals(ServiceStatus.IN_PREPARAZIONE, s.getStato());
        }

        @Test
        void dateOutsideEventRange_ThrowsEccezione3a() {
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().aggiungiServizio("Cena", 5, LocalTime.of(20, 0), LocalTime.of(23, 0), 30));
        }

        @Test
        void eventNotInBozza_Throws() {
            event.setStato(EventStatus.ANNULLATO);
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 50));
        }
    }

    @Nested
    @DisplayName("Assegnazione dello chef (contratto 5)")
    class AssegnaChef {

        private Event event;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(60), 1, 1, 40, "", "C");
        }

        @Test
        void happyPath_AssignsAvailableChef() throws UseCaseLogicException {
            User antonio = User.load("Antonio");

            mgr().assegnaChef(antonio);

            assertEquals(antonio, event.getChef());
        }

        @Test
        void userWithoutChefRole_Throws() {
            User marco = User.load("Marco"); // service staff, not chef
            assertThrows(UseCaseLogicException.class, () -> mgr().assegnaChef(marco));
        }

        @Test
        void chefAlreadyCommittedOnOverlappingDates_ThrowsEccezione5b() throws UseCaseLogicException {
            User antonio = User.load("Antonio");
            mgr().assegnaChef(antonio); // committed on `event`'s date

            mgr().creaSchedaEvento("Altro luogo", event.getDataInizio(), 1, 1, 20, "", "C2"); // becomes currentEvent

            assertThrows(UseCaseLogicException.class, () -> mgr().assegnaChef(antonio));
        }
    }

    @Nested
    @DisplayName("Approvazione del menu del servizio (contratto 7)")
    class ApprovaMenu {

        @Test
        void allServicesConfirmed_TransitionsEventToInCorso() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            Service s = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            Menu menu = Menu.load(1); // menu dal DB
            s.setMenu(menu);

            mgr().approvaMenu(s, menu);

            assertEquals(ServiceStatus.CONFERMATO, s.getStato());
            assertEquals(EventStatus.IN_CORSO, event.getStato());
        }

        @Test
        void noMenuProposedYet_ThrowsEccezione7b() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            Service s = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            Menu menu = Menu.load(1);

            assertThrows(UseCaseLogicException.class, () -> mgr().approvaMenu(s, menu));
        }
    }

    @Nested
    @DisplayName("Chiusura dell'evento (contratto 9)")
    class ChiudiEvento {

        @Test
        void noPendingShifts_ClosesSuccessfully() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            event.setStato(EventStatus.IN_CORSO);

            mgr().chiudiEvento("Tutto ok", "report.pdf");

            assertEquals(EventStatus.TERMINATO, event.getStato());
            assertEquals("report.pdf", event.getDocumentazioneFinale());
        }

        @Test
        void pendingFutureShift_ThrowsEccezione9a() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 2, 1, 30, "", "C");
            Service s = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            mgr().inserisciTurnoServizio(s, 30, 30);
            event.setStato(EventStatus.IN_CORSO);

            assertThrows(UseCaseLogicException.class, () -> mgr().chiudiEvento("note", "doc"));
        }
    }

    @Nested
    @DisplayName("Annullamento dell'evento (contratto 1f.1)")
    class AnnullaEvento {

        @Test
        void notInCorso_ThrowsEccezione1f1b() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");

            assertThrows(UseCaseLogicException.class, () -> mgr().annullaEvento(event)); // ancora in bozza
        }

        @Test
        void lessThanAWeekAway_SignalsPenalty() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            mgr().modificaSchedaEvento(null, LocalDate.now().plusDays(3), null, null, null, null, null);
            event.setStato(EventStatus.IN_CORSO);

            boolean penale = mgr().annullaEvento(event);

            assertTrue(penale);
            assertEquals(EventStatus.ANNULLATO, event.getStato());
        }

        @Test
        void moreThanAWeekAway_NoPenalty() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(30), 1, 1, 30, "", "C");
            event.setStato(EventStatus.IN_CORSO);

            boolean penale = mgr().annullaEvento(event);

            assertFalse(penale);
        }
    }

    @Nested
    @DisplayName("Apertura di un evento esistente (contratto 1b.1)")
    class ApriEvento {

        @Test
        void happyPath_SelectsTheGivenEvent() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event created = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            Event reloaded = Event.loadById(created.getId());

            mgr().apriEvento(reloaded);

            assertSame(reloaded, mgr().getCurrentEvent());
        }

        @Test
        void nullEvent_Throws() {
            assertThrows(UseCaseLogicException.class, () -> mgr().apriEvento(null));
        }
    }

    @Nested
    @DisplayName("Modifica della scheda evento in bozza (contratto 1d.1)")
    class ModificaSchedaEvento {

        private Event event;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
        }

        @Test
        void happyPath_UpdatesOnlyProvidedFields() throws UseCaseLogicException {
            mgr().modificaSchedaEvento("Nuovo luogo", null, null, null, null, null, null);

            assertEquals("Nuovo luogo", event.getLuogo());
            assertEquals(30, event.getNumeroPartecipanti());
        }

        @Test
        void eventNotInBozza_ThrowsEccezione1d1a() {
            event.setStato(EventStatus.TERMINATO);

            assertThrows(UseCaseLogicException.class,
                    () -> mgr().modificaSchedaEvento("X", null, null, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("Rimozione di un servizio (contratto 1d.1)")
    class RimuoviServizio {

        private Event event;
        private Service service;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
        }

        @Test
        void happyPath_RemovesServiceAndItsShifts() throws UseCaseLogicException {
            mgr().inserisciTurnoServizio(service, 30, 30);

            mgr().rimuoviServizio(service);

            assertFalse(event.containsService(service));
        }

        @Test
        void eventNotInBozza_Throws() {
            event.setStato(EventStatus.IN_CORSO);

            assertThrows(UseCaseLogicException.class, () -> mgr().rimuoviServizio(service));
        }

        @Test
        void serviceNotInCurrentEvent_Throws() throws UseCaseLogicException {
            mgr().creaSchedaEvento("Altro", LocalDate.now().plusDays(20), 1, 1, 10, "", "C2");

            assertThrows(UseCaseLogicException.class, () -> mgr().rimuoviServizio(service));
        }
    }

    @Nested
    @DisplayName("Modifica di un evento in corso (contratto 1d.2)")
    class ModificaSchedaEventoInCorso {

        private Event event;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 100, "", "C");
            event.setStato(EventStatus.IN_CORSO);
        }

        @Test
        void smallVariation_UpdatesWithoutPenalty() throws UseCaseLogicException {
            boolean penale = mgr().modificaSchedaEventoInCorso(110, "nota aggiornata");

            assertFalse(penale);
            assertEquals(110, event.getNumeroPartecipanti());
            assertEquals("nota aggiornata", event.getNote());
        }

        @Test
        void variationOver30Percent_SignalsPenaltyButStillApplies() throws UseCaseLogicException {
            boolean penale = mgr().modificaSchedaEventoInCorso(150, null);

            assertTrue(penale);
            assertEquals(150, event.getNumeroPartecipanti());
        }

        @Test
        void eventNotInCorso_Throws() {
            event.setStato(EventStatus.BOZZA);

            assertThrows(UseCaseLogicException.class, () -> mgr().modificaSchedaEventoInCorso(120, null));
        }
    }

    @Nested
    @DisplayName("Eliminazione dell'evento (contratto 1e.1)")
    class EliminaEvento {

        @Test
        void happyPath_DeletesBozzaEvent() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");

            mgr().eliminaEvento(event);

            assertNull(Event.loadById(event.getId()));
        }

        @Test
        void eventNotInBozza_ThrowsEccezione1e1a() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            event.setStato(EventStatus.IN_CORSO);

            assertThrows(UseCaseLogicException.class, () -> mgr().eliminaEvento(event));
        }
    }

    @Nested
    @DisplayName("Selezione di un'istanza della ricorrenza (contratto 1g.1)")
    class SelezionaIstanza {

        @Test
        void happyPath_SelectsAnInstanceOfARecurringEvent() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            RecurringEvent me = mgr().creaSchedaEventoRicorrente("Villa Reale", LocalDate.now().plusDays(20), 1, 1,
                    40, "", "C", "WEEKLY", null, 3);

            mgr().selezionaIstanza(me.getIstanze().get(1));

            assertSame(me.getIstanze().get(1), mgr().getCurrentEvent());
        }

        @Test
        void standaloneEvent_Throws() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event standalone = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");

            assertThrows(UseCaseLogicException.class, () -> mgr().selezionaIstanza(standalone));
        }
    }

    @Nested
    @DisplayName("Operazione su una o tutte le istanze (contratto 1g.2)")
    class OperaSuIstanza {

        private RecurringEvent me;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            me = mgr().creaSchedaEventoRicorrente("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 40, "", "C",
                    "WEEKLY", null, 3);
        }

        @Test
        void scopeSingola_AffectsOnlyTheSelectedInstance() throws UseCaseLogicException {
            Event first = me.getIstanze().get(0);
            Event second = me.getIstanze().get(1);
            EventCommand cmd = new ModificaEventoCommand(null, null, null, null, null, "aggiornata", null);

            mgr().operaSuIstanza(first, cmd, Scope.SINGOLA);

            assertEquals("aggiornata", first.getNote());
            assertNotEquals("aggiornata", second.getNote());
        }

        @Test
        void scopeTutte_SkipsCancelledOrTerminatedInstances() throws UseCaseLogicException {
            Event third = me.getIstanze().get(2);
            PersistenceManager.executeUpdate("UPDATE Events SET stato = ? WHERE id = ?",
                    EventStatus.ANNULLATO.name(), third.getId());

            EventCommand cmd = new ModificaEventoCommand(null, null, null, null, null, "nota comune", null);
            mgr().operaSuIstanza(me.getIstanze().get(0), cmd, Scope.TUTTE);

            RecurringEvent reloaded = RecurringEvent.loadById(me.getId());
            assertEquals("nota comune", reloaded.getIstanze().get(0).getNote());
            assertEquals("nota comune", reloaded.getIstanze().get(1).getNote());
            assertNotEquals("nota comune", reloaded.getIstanze().get(2).getNote());
        }

        @Test
        void standaloneEvent_Throws() throws UseCaseLogicException {
            Event standalone = mgr().creaSchedaEvento("Altro", LocalDate.now().plusDays(20), 1, 1, 10, "", "C2");
            EventCommand cmd = new ModificaEventoCommand(null, null, null, null, null, "x", null);

            assertThrows(UseCaseLogicException.class, () -> mgr().operaSuIstanza(standalone, cmd, Scope.SINGOLA));
        }

        @Test
        void annullaCommand_SkipsBozzaInstance() throws UseCaseLogicException {
            Event first = me.getIstanze().get(0);

            mgr().operaSuIstanza(first, new AnnullaEventoCommand(), Scope.SINGOLA);

            assertEquals(EventStatus.BOZZA, first.getStato());
        }

        @Test
        void annullaCommand_CancelsAndPersistsInCorsoInstance() throws UseCaseLogicException {
            Event first = me.getIstanze().get(0);
            PersistenceManager.executeUpdate("UPDATE Events SET stato = ? WHERE id = ?",
                    EventStatus.IN_CORSO.name(), first.getId());
            Event reloaded = Event.loadById(first.getId());

            mgr().operaSuIstanza(reloaded, new AnnullaEventoCommand(), Scope.SINGOLA);

            assertEquals(EventStatus.ANNULLATO, Event.loadById(first.getId()).getStato());
        }

        @Test
        void modificaCommand_SkipsInCorsoInstance() throws UseCaseLogicException {
            Event first = me.getIstanze().get(0);
            PersistenceManager.executeUpdate("UPDATE Events SET stato = ? WHERE id = ?",
                    EventStatus.IN_CORSO.name(), first.getId());
            Event reloaded = Event.loadById(first.getId());
            EventCommand cmd = new ModificaEventoCommand(null, null, null, null, null, "non deve arrivare", null);

            mgr().operaSuIstanza(reloaded, cmd, Scope.SINGOLA);

            assertNotEquals("non deve arrivare", Event.loadById(first.getId()).getNote());
        }

        @Test
        void eliminaCommand_DeletesBozzaInstanceFromDb() throws UseCaseLogicException {
            Event first = me.getIstanze().get(0);
            int id = first.getId();

            mgr().operaSuIstanza(first, new EliminaEventoCommand(), Scope.SINGOLA);

            assertNull(Event.loadById(id));
            RecurringEvent reloaded = RecurringEvent.loadById(me.getId());
            assertTrue(reloaded.getIstanze().stream().noneMatch(e -> e.getId() == id));
        }

        @Test
        void eliminaCommand_SkipsInCorsoInstance() throws UseCaseLogicException {
            Event first = me.getIstanze().get(0);
            PersistenceManager.executeUpdate("UPDATE Events SET stato = ? WHERE id = ?",
                    EventStatus.IN_CORSO.name(), first.getId());
            Event reloaded = Event.loadById(first.getId());

            mgr().operaSuIstanza(reloaded, new EliminaEventoCommand(), Scope.SINGOLA);

            assertNotNull(Event.loadById(first.getId()));
        }

        @Test
        void scopeTutte_EliminaRemovesEveryDeletableInstance() throws UseCaseLogicException {
            Event third = me.getIstanze().get(2);
            PersistenceManager.executeUpdate("UPDATE Events SET stato = ? WHERE id = ?",
                    EventStatus.IN_CORSO.name(), third.getId());

            int firstId = me.getIstanze().get(0).getId();
            int secondId = me.getIstanze().get(1).getId();
            int thirdId = third.getId();

            mgr().operaSuIstanza(me.getIstanze().get(0), new EliminaEventoCommand(), Scope.TUTTE);

            assertNull(Event.loadById(firstId));
            assertNull(Event.loadById(secondId));
            assertNotNull(Event.loadById(thirdId));

            RecurringEvent reloaded = RecurringEvent.loadById(me.getId());
            assertEquals(1, reloaded.getIstanze().size());
            assertEquals(thirdId, reloaded.getIstanze().get(0).getId());
        }

        @Test
        void scopeTutte_AnnullaCancelsOnlyInCorsoInstances() throws UseCaseLogicException {
            int firstId = me.getIstanze().get(0).getId();
            int secondId = me.getIstanze().get(1).getId();
            int thirdId = me.getIstanze().get(2).getId();
            for (int id : new int[] { firstId, secondId }) {
                PersistenceManager.executeUpdate("UPDATE Events SET stato = ? WHERE id = ?",
                        EventStatus.IN_CORSO.name(), id);
            }

            mgr().operaSuIstanza(Event.loadById(firstId), new AnnullaEventoCommand(), Scope.TUTTE);

            assertEquals(EventStatus.ANNULLATO, Event.loadById(firstId).getStato());
            assertEquals(EventStatus.ANNULLATO, Event.loadById(secondId).getStato());
            assertEquals(EventStatus.BOZZA, Event.loadById(thirdId).getStato());
        }
    }

    @Nested
    @DisplayName("Modifica della frequenza di ricorrenza (contratto 1h.1)")
    class ModificaRicorrenza {

        private RecurringEvent me;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            me = mgr().creaSchedaEventoRicorrente("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 40, "", "C",
                    "WEEKLY", null, 3);
        }

        @Test
        void increasingOccurrences_AddsMissingInstances() throws UseCaseLogicException {
            mgr().modificaRicorrenza(me, null, null, 5);

            assertEquals(5, me.getIstanze().size());
        }

        @Test
        void decreasingOccurrences_RemovesExcessBozzaInstances() throws UseCaseLogicException {
            mgr().modificaRicorrenza(me, null, null, 2);

            assertEquals(2, me.getIstanze().size());
        }

        @Test
        void bothEndConditionsSpecified_Throws() {
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().modificaRicorrenza(me, null, LocalDate.now().plusMonths(2), 5));
        }

        @Test
        void noActiveInstances_Throws() {
            for (Event e : me.getIstanze()) {
                e.setStato(EventStatus.ANNULLATO);
            }

            assertThrows(UseCaseLogicException.class, () -> mgr().modificaRicorrenza(me, null, null, 5));
        }

        @Test
        void increasingOccurrences_PersistsTheNewInstances() throws UseCaseLogicException {
            mgr().modificaRicorrenza(me, null, null, 5);

            RecurringEvent reloaded = RecurringEvent.loadById(me.getId());
            assertEquals(5, reloaded.getIstanze().size());
        }

        @Test
        void decreasingOccurrences_DeletesTheExcessInstancesFromDb() throws UseCaseLogicException {
            int excessId = me.getIstanze().get(2).getId();

            mgr().modificaRicorrenza(me, null, null, 2);

            assertNull(Event.loadById(excessId));
            RecurringEvent reloaded = RecurringEvent.loadById(me.getId());
            assertEquals(2, reloaded.getIstanze().size());
        }
    }

    @Nested
    @DisplayName("Annullamento di un servizio (contratto 1i.1)")
    class AnnullaServizio {

        private Service service;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
        }

        @Test
        void happyPath_CancelsTheService() throws UseCaseLogicException {
            mgr().annullaServizio(service);

            assertEquals(ServiceStatus.ANNULLATO, service.getStato());
        }

        @Test
        void alreadyCancelled_ThrowsEccezione1i1a() throws UseCaseLogicException {
            mgr().annullaServizio(service);

            assertThrows(UseCaseLogicException.class, () -> mgr().annullaServizio(service));
        }

        @Test
        void serviceNotInCurrentEvent_Throws() throws UseCaseLogicException {
            mgr().creaSchedaEvento("Altro", LocalDate.now().plusDays(20), 1, 1, 10, "", "C2");

            assertThrows(UseCaseLogicException.class, () -> mgr().annullaServizio(service));
        }
    }

    @Nested
    @DisplayName("Consultazione dello stato della cucina (contratto 2)")
    class VisualizzaStatoCucina {

        @Test
        void happyPath_ReturnsAListWithoutError() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);

            assertNotNull(mgr().visualizzaStatoCucina());
        }

        @Test
        void noCurrentEvent_Throws() {
            mgr().setCurrentEvent(null);

            assertThrows(UseCaseLogicException.class, () -> mgr().visualizzaStatoCucina());
        }
    }

    @Nested
    @DisplayName("Inserimento di un turno di servizio (contratto 4)")
    class InserisciTurnoServizio {

        private Event event;
        private Service service;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
        }

        @Test
        void happyPath_ComputesWindowFromPrepAndCleanupTimes() throws UseCaseLogicException {
            ServiceShift turno = mgr().inserisciTurnoServizio(service, 30, 45);

            assertEquals(LocalDateTime.of(event.getDataInizio(), LocalTime.of(11, 30)), turno.getInizio());
            assertEquals(LocalDateTime.of(event.getDataInizio(), LocalTime.of(15, 45)), turno.getFine());
            assertTrue(service.getTurni().contains(turno));
        }

        @Test
        void serviceAcrossMidnight_EndsOnTheFollowingDay() throws UseCaseLogicException {
            Service cena = mgr().aggiungiServizio("Cena", 0, LocalTime.of(21, 0), LocalTime.of(1, 0), 30);

            ServiceShift turno = mgr().inserisciTurnoServizio(cena, 60, 60);

            LocalDate giorno = event.getDataInizio();
            assertEquals(LocalDateTime.of(giorno, LocalTime.of(20, 0)), turno.getInizio());
            assertEquals(LocalDateTime.of(giorno.plusDays(1), LocalTime.of(2, 0)), turno.getFine());
            assertTrue(turno.getFine().isAfter(turno.getInizio()), "la fine non deve precedere l'inizio");
        }

        @Test
        void cleanupPastMidnight_DoesNotWrapAround() throws UseCaseLogicException {
            Service cena = mgr().aggiungiServizio("Cena", 0, LocalTime.of(19, 0), LocalTime.of(23, 30), 30);

            ServiceShift turno = mgr().inserisciTurnoServizio(cena, 0, 60);

            assertEquals(LocalDateTime.of(event.getDataInizio().plusDays(1), LocalTime.of(0, 30)), turno.getFine());
            assertTrue(turno.getFine().isAfter(turno.getInizio()));
        }

        @Test
        void serviceNotInCurrentEvent_Throws() throws UseCaseLogicException {
            mgr().creaSchedaEvento("Altro", LocalDate.now().plusDays(20), 1, 1, 10, "", "C2");

            assertThrows(UseCaseLogicException.class, () -> mgr().inserisciTurnoServizio(service, 30, 30));
        }
    }

    @Nested
    @DisplayName("Consultazione del menu di un servizio (contratto 6)")
    class ConsultaMenuServizio {

        private Service service;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
        }

        @Test
        void happyPath_ReturnsTheChosenMenu() throws UseCaseLogicException {
            Menu menu = Menu.load(1);
            service.setMenu(menu);

            assertEquals(menu, mgr().consultaMenuServizio(service));
        }

        @Test
        void serviceNotInCurrentEvent_Throws() throws UseCaseLogicException {
            mgr().creaSchedaEvento("Altro", LocalDate.now().plusDays(20), 1, 1, 10, "", "C2");

            assertThrows(UseCaseLogicException.class, () -> mgr().consultaMenuServizio(service));
        }
    }

    @Nested
    @DisplayName("Proposta di modifica al menu (contratto 7a.1)")
    class ProponiModificaMenu {

        private Event event;
        private Service service;
        private Menu menu;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            menu = Menu.load(1);
            service.setMenu(menu);
        }

        @ParameterizedTest(name = "{0} crea una {1}")
        @CsvSource({ "ADD, ProposalToAdd", "REMOVE, ProposalToRemove" })
        @DisplayName("Entrambe le operazioni creano la sottoclasse giusta senza toccare il menu")
        void happyPath_RecordsProposalWithoutTouchingOriginalMenu(ProposalOperation operazione,
                String sottoclasseAttesa) throws UseCaseLogicException {
            List<MenuItem> voci = new ArrayList<>(menu.getItems());
            int originalSize = menu.getItems().size();

            ProposalToModify p = mgr().proponiModificaMenu(service, menu, voci, operazione);

            assertNotNull(p);
            assertEquals(operazione, p.getOperazione());
            assertEquals(sottoclasseAttesa, p.getClass().getSimpleName());
            assertTrue(service.getProposte().contains(p));
            assertEquals(originalSize, menu.getItems().size(), "the original menu must be untouched");
        }

        @Test
        @DisplayName("La proposta salvata viene riletta insieme al servizio")
        void savedProposal_IsReloadedWithTheService() throws UseCaseLogicException {
            List<MenuItem> voci = new ArrayList<>(menu.getItems());
            mgr().proponiModificaMenu(service, menu, voci, ProposalOperation.ADD);

            Event reloaded = Event.loadById(event.getId());

            Service reloadedService = reloaded.getServizi().get(0);
            assertEquals(1, reloadedService.getProposte().size());
            assertEquals(ProposalOperation.ADD, reloadedService.getProposte().get(0).getOperazione());
            assertEquals(voci.size(), reloadedService.getProposte().get(0).getVoci().size());
        }

        @Test
        void menuAlreadyConfirmed_ThrowsEccezione7a1a() throws UseCaseLogicException {
            mgr().approvaMenu(service, menu);

            assertThrows(UseCaseLogicException.class,
                    () -> mgr().proponiModificaMenu(service, menu, new ArrayList<>(), ProposalOperation.ADD));
        }
    }

    @Nested
    @DisplayName("Assegnazione del personale a un turno (contratto 8)")
    class AssegnaPersonaleATurno {

        private ServiceShift turno;
        private LocalDate turnoData;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            Service service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            turno = mgr().inserisciTurnoServizio(service, 0, 0);
            turnoData = event.getDataInizio();
        }

        @Test
        void staffWithDeclaredAvailability_IsAssigned() throws UseCaseLogicException {
            declareAvailability(1, turnoData, LocalTime.of(11, 0), LocalTime.of(16, 0)); // Marco

            ServiceAssignment a = mgr().assegnaPersonaleATurno(turno, User.load("Marco"), "Cameriere");

            assertNotNull(a);
            assertEquals("Cameriere", a.getRuolo());
            assertTrue(turno.getIncarichi().contains(a));
        }

        @Test
        void staffWithoutDeclaredAvailability_ThrowsEccezione8c() {
            assertThrows(UseCaseLogicException.class,
                    () -> mgr().assegnaPersonaleATurno(turno, User.load("Marco"), "Cameriere"));
        }
    }

    @Nested
    @DisplayName("Assegnazione del cuoco di supporto (contratto 8a.1)")
    class AssegnaCuocoATurno {

        private ServiceShift turno;
        private LocalDate turnoData;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            Service service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            turno = mgr().inserisciTurnoServizio(service, 0, 0);
            turnoData = event.getDataInizio();
        }

        @Test
        void cookWithDeclaredAvailability_IsAssigned() throws UseCaseLogicException {
            declareAvailability(3, turnoData, LocalTime.of(11, 0), LocalTime.of(16, 0)); // Luca

            mgr().assegnaCuocoATurno(turno, User.load("Luca"));

            assertNotNull(turno.getCuocoDiSupporto());
            assertEquals("Luca", turno.getCuocoDiSupporto().getUser().getUserName());
        }

        @Test
        void cookWithoutDeclaredAvailability_ThrowsEccezione8a1a() {
            assertThrows(UseCaseLogicException.class, () -> mgr().assegnaCuocoATurno(turno, User.load("Luca")));
        }
    }

    @Nested
    @DisplayName("Rimozione del personale da un turno (contratto 8b.1)")
    class RimuoviPersonaleDaTurno {

        private ServiceShift turno;
        private User marco;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            Event event = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 30, "", "C");
            Service service = mgr().aggiungiServizio("Pranzo", 0, LocalTime.of(12, 0), LocalTime.of(15, 0), 30);
            turno = mgr().inserisciTurnoServizio(service, 0, 0);
            marco = User.load("Marco");
            declareAvailability(marco.getId(), event.getDataInizio(), LocalTime.of(11, 0), LocalTime.of(16, 0));
            mgr().assegnaPersonaleATurno(turno, marco, "Cameriere");
        }

        @Test
        void happyPath_RemovesTheAssignment() throws UseCaseLogicException {
            mgr().rimuoviPersonaleDaTurno(turno, marco);

            assertTrue(turno.getIncarichi().isEmpty());
        }

        @Test
        void staffNotAssigned_Throws() {
            User giulia = User.load("Giulia");

            assertThrows(UseCaseLogicException.class, () -> mgr().rimuoviPersonaleDaTurno(turno, giulia));
        }

        @Test
        void shiftAlreadyStarted_ThrowsEccezione8b1a() throws Exception {
            forcePastShift(turno);

            assertThrows(UseCaseLogicException.class, () -> mgr().rimuoviPersonaleDaTurno(turno, marco));
        }
    }

    @Nested
    @DisplayName("Notifiche agli EventReceiver (pattern Observer)")
    class NotificheAgliEventReceiver {

        private EventReceiver receiver;

        @BeforeEach
        void setUp() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Francesca");
            receiver = Mockito.mock(EventReceiver.class);
            mgr().addEventReceiver(receiver);
        }

        @AfterEach
        void tearDown() {
            mgr().removeEventReceiver(receiver);
        }

        @Test
        @DisplayName("La creazione di un evento notifica updateEventCreated")
        void creaSchedaEvento_NotifiesEventReceivers() throws UseCaseLogicException {
            mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 50, "", "Cliente");

            Mockito.verify(receiver).updateEventCreated(Mockito.any(Event.class));
            Mockito.verify(receiver, Mockito.never()).updateEventModified(Mockito.any(Event.class));
        }

        @Test
        @DisplayName("La modifica di un evento notifica updateEventModified")
        void modificaSchedaEvento_NotifiesEventReceivers() throws UseCaseLogicException {
            Event e = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 50, "", "Cliente");

            mgr().modificaSchedaEvento("Castello", null, null, null, 60, null, null);

            Mockito.verify(receiver).updateEventModified(e);
        }

        @Test
        @DisplayName("L'eliminazione di un evento notifica updateEventDeleted")
        void eliminaEvento_NotifiesEventReceivers() throws UseCaseLogicException {
            Event e = mgr().creaSchedaEvento("Villa Reale", LocalDate.now().plusDays(20), 1, 1, 50, "", "Cliente");

            mgr().eliminaEvento(e);

            Mockito.verify(receiver).updateEventDeleted(e);
        }
    }

    // helpers ----------------------------------------------------------------------------------------------

    private static final java.time.format.DateTimeFormatter DB_DATETIME = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    private static void declareAvailability(int userId, LocalDate data, LocalTime start, LocalTime end) {
        LocalDateTime inizio = LocalDateTime.of(data, start);
        LocalDateTime fine = LocalDateTime.of(data, end);
        if (!fine.isAfter(inizio)) {
            fine = fine.plusDays(1);
        }
        PersistenceManager.executeUpdate(
                "INSERT INTO PersonnelAvailability (user_id, inizio, fine) VALUES (?, ?, ?)",
                userId, inizio.format(DB_DATETIME), fine.format(DB_DATETIME));
    }

    private static void forcePastShift(ServiceShift turno) throws Exception {
        Field inizioField = ServiceShift.class.getDeclaredField("inizio");
        inizioField.setAccessible(true);
        inizioField.set(turno, LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(9, 0)));

        Field fineField = ServiceShift.class.getDeclaredField("fine");
        fineField.setAccessible(true);
        fineField.set(turno, LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(13, 0)));
    }
}
