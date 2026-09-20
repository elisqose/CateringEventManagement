package catering.businesslogic.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.kitchen.SummarySheet;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.user.User;

public class EventManager {

    private ArrayList<EventReceiver> eventReceivers;
    private Event currentEvent;
    private Service currentService;

    public EventManager() {
        eventReceivers = new ArrayList<>();
    }

    public void addEventReceiver(EventReceiver receiver) {
        if (receiver != null && !eventReceivers.contains(receiver)) {
            eventReceivers.add(receiver);
        }
    }

    public void removeEventReceiver(EventReceiver receiver) {
        eventReceivers.remove(receiver);
    }

    public ArrayList<Event> getEvents() {
        return Event.loadAllEvents();
    }

    public Event getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(Event event) {
        this.currentEvent = event;
    }

    public Service getCurrentService() {
        return currentService;
    }

    public void setCurrentService(Service service) {
        this.currentService = service;
    }

    private User currentUser() {
        return CatERing.getInstance().getUserManager().getCurrentUser();
    }

    private void requireCurrentEvent() throws UseCaseLogicException {
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato");
        }
    }

    private User requireOrganizer() throws UseCaseLogicException {
        User user = currentUser();
        if (user == null || !user.isOrganizer()) {
            throw new UseCaseLogicException("Solo un organizzatore puo' eseguire questa operazione");
        }
        return user;
    }

    private void requireOwnership(Organizer organizzatore, User user) throws UseCaseLogicException {
        if (organizzatore == null || !organizzatore.equals(user.asOrganizer())) {
            throw new UseCaseLogicException("L'organizzatore non gestisce l'evento indicato");
        }
    }

    private boolean eventContainsTurno(ServiceShift turno) {
        if (turno == null || currentEvent == null) {
            return false;
        }
        for (Service s : currentEvent.getServizi()) {
            if (s.getTurni().contains(turno)) {
                return true;
            }
        }
        return false;
    }

    private int calcolaSogliaPreavviso(int numeroPartecipanti, int durata) {
        return 14;
    }

    private AbstractFrequency costruisciFrequenza(String tipoFrequenza, LocalDate dataFine, Integer numeroOccorrenze) {
        return AbstractFrequency.create(tipoFrequenza, dataFine, numeroOccorrenze);
    }

    private LocalDate calcolaData(LocalDate dataInizio, int offset) {
        return dataInizio.plusDays(offset);
    }

    private AbstractFrequency aggiornaFrequenza(AbstractFrequency freqCorrente, String tipoFrequenza,
            LocalDate dataFine, Integer numeroOccorrenze) {
        String tipo = tipoFrequenza != null ? tipoFrequenza : freqCorrente.getTipo();
        LocalDate df = (dataFine != null) ? dataFine : (numeroOccorrenze != null ? null : freqCorrente.getDataFine());
        Integer no = (numeroOccorrenze != null) ? numeroOccorrenze
                : (dataFine != null ? null : freqCorrente.getNumeroOccorrenze());
        return AbstractFrequency.create(tipo, df, no);
    }

    public Event creaSchedaEvento(String luogo, LocalDate dataInizio, int durata, int numeroServiziRichiesti,
            int numeroPartecipanti, String note, String cliente) throws UseCaseLogicException {
        User user = requireOrganizer();

        int soglia = calcolaSogliaPreavviso(numeroPartecipanti, durata);
        if (ChronoUnit.DAYS.between(LocalDate.now(), dataInizio) < soglia) {
            throw new UseCaseLogicException("Preavviso insufficiente per la data indicata");
        }

        Event e = Event.create(luogo, dataInizio, durata, numeroServiziRichiesti, numeroPartecipanti, note, cliente,
                user.asOrganizer());
        notifyEventCreated(e);

        this.currentEvent = e;
        this.currentService = null;
        return e;
    }

    public void apriEvento(Event evento) throws UseCaseLogicException {
        User user = requireOrganizer();
        if (evento == null) {
            throw new UseCaseLogicException("Evento non specificato");
        }
        requireOwnership(evento.getOrganizzatore(), user);

        this.currentEvent = evento;
        this.currentService = null;
    }

    public RecurringEvent creaSchedaEventoRicorrente(String luogo, LocalDate dataInizio, int durata,
            int numeroServiziRichiesti, int numeroPartecipanti, String note, String cliente,
            String tipoFrequenza, LocalDate dataFine, Integer numeroOccorrenze) throws UseCaseLogicException {
        User user = requireOrganizer();
        if ((dataFine == null) == (numeroOccorrenze == null)) {
            throw new UseCaseLogicException("Specificare esattamente uno tra dataFine e numeroOccorrenze");
        }

        int soglia = calcolaSogliaPreavviso(numeroPartecipanti, durata);
        if (ChronoUnit.DAYS.between(LocalDate.now(), dataInizio) < soglia) {
            throw new UseCaseLogicException("Preavviso insufficiente per la data indicata");
        }

        AbstractFrequency freq = costruisciFrequenza(tipoFrequenza, dataFine, numeroOccorrenze);
        RecurringEvent me = RecurringEvent.create(luogo, durata, numeroServiziRichiesti, numeroPartecipanti, note,
                cliente, user.asOrganizer(), freq);
        me.saveNew();

        LocalDate data = dataInizio;
        int count = 0;
        while (true) {
            Event e = me.creaEvento(data);
            notifyEventCreated(e);
            count++;
            if (numeroOccorrenze != null && count >= numeroOccorrenze) {
                break;
            }
            LocalDate next = freq.prossimaOccorrenza(data);
            if (dataFine != null && next.isAfter(dataFine)) {
                break;
            }
            data = next;
        }

        this.currentEvent = me.getIstanze().get(0);
        this.currentService = null;
        return me;
    }

    public void modificaSchedaEvento(String luogo, LocalDate dataInizio, Integer durata,
            Integer numeroServiziRichiesti, Integer numeroPartecipanti, String note, String cliente)
            throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabile()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }

        currentEvent.aggiornaAttributi(luogo, dataInizio, durata, numeroServiziRichiesti, numeroPartecipanti, note,
                cliente);
        notifyEventModified(currentEvent);
    }

    public void rimuoviServizio(Service servizio) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabile()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }
        if (!currentEvent.containsService(servizio)) {
            throw new UseCaseLogicException("Il servizio non appartiene all'evento corrente");
        }

        currentEvent.rimuoviServizio(servizio);
        if (currentService != null && currentService.equals(servizio)) {
            currentService = null;
        }
        notifyEventModified(currentEvent);
    }

    public boolean modificaSchedaEventoInCorso(Integer numeroPartecipanti, String note)
            throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabileInCorso()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }

        boolean penale = numeroPartecipanti != null
                && currentEvent.isVariazionePartecipantiEccessiva(numeroPartecipanti);

        currentEvent.aggiornaInCorso(numeroPartecipanti, note);
        notifyEventModified(currentEvent);

        return penale;
    }

    public void eliminaEvento(Event evento) throws UseCaseLogicException {
        User user = requireOrganizer();
        if (evento == null) {
            throw new UseCaseLogicException("Evento non specificato");
        }
        requireOwnership(evento.getOrganizzatore(), user);
        if (!evento.isEliminabile()) {
            throw new UseCaseLogicException("Evento non eliminabile nello stato corrente");
        }

        notifyEventDeleted(evento);
        if (currentEvent != null && currentEvent.getId() == evento.getId()) {
            currentEvent = null;
            currentService = null;
        }
    }

    public boolean annullaEvento(Event evento) throws UseCaseLogicException {
        User user = requireOrganizer();
        if (evento == null) {
            throw new UseCaseLogicException("Evento non specificato");
        }
        requireOwnership(evento.getOrganizzatore(), user);
        if (!evento.isAnnullabile()) {
            throw new UseCaseLogicException("Evento non annullabile nello stato corrente");
        }

        boolean penale = ChronoUnit.DAYS.between(LocalDate.now(), evento.getDataInizio()) < 7;

        evento.annulla();
        notifyEventCancelled(evento);

        return penale;
    }

    public void selezionaIstanza(Event istanza) throws UseCaseLogicException {
        User user = requireOrganizer();
        if (istanza == null || istanza.getMacroEventoId() <= 0) {
            throw new UseCaseLogicException("L'evento selezionato non fa parte di una ricorrenza");
        }
        requireOwnership(istanza.getMacroEvento().getOrganizzatore(), user);

        this.currentEvent = istanza;
    }

    public void operaSuIstanza(Event istanza, EventCommand cmd, Scope scope) throws UseCaseLogicException {
        User user = requireOrganizer();
        if (istanza == null || istanza.getMacroEventoId() <= 0) {
            throw new UseCaseLogicException("L'evento selezionato non fa parte di una ricorrenza");
        }
        RecurringEvent er = istanza.getMacroEvento();
        requireOwnership(er.getOrganizzatore(), user);

        if (scope == Scope.SINGOLA) {
            applyIfModifiable(er, istanza, cmd);
        } else {
            for (Event e : new ArrayList<>(er.getIstanze())) {
                applyIfModifiable(er, e, cmd);
            }
        }
    }

    private void applyIfModifiable(RecurringEvent er, Event e, EventCommand cmd) {
        if (e.getStato() == EventStatus.TERMINATO || e.getStato() == EventStatus.ANNULLATO) {
            return;
        }
        if (!cmd.isApplicabile(e)) {
            return;
        }

        e.eseguiOperazione(cmd);

        if (cmd instanceof EliminaEventoCommand) {
            er.rimuoviIstanza(e);
            if (currentEvent != null && currentEvent.equals(e)) {
                currentEvent = null;
                currentService = null;
            }
            notifyEventDeleted(e);
        } else if (cmd instanceof AnnullaEventoCommand) {
            notifyEventCancelled(e);
        } else {
            notifyEventModified(e);
        }
    }

    public void modificaRicorrenza(RecurringEvent er, String tipoFrequenza, LocalDate dataFine,
            Integer numeroOccorrenze) throws UseCaseLogicException {
        User user = requireOrganizer();
        if (er == null) {
            throw new UseCaseLogicException("Evento ricorrente non specificato");
        }
        requireOwnership(er.getOrganizzatore(), user);

        boolean hasActive = er.getIstanze().stream()
                .anyMatch(e -> e.getStato() != EventStatus.ANNULLATO && e.getStato() != EventStatus.TERMINATO);
        if (!hasActive) {
            throw new UseCaseLogicException("Nessuna istanza ancora attiva per questa ricorrenza");
        }
        if (dataFine != null && numeroOccorrenze != null) {
            throw new UseCaseLogicException("dataFine e numeroOccorrenze sono alternativi tra loro");
        }

        AbstractFrequency freq = aggiornaFrequenza(er.getFrequenza(), tipoFrequenza, dataFine, numeroOccorrenze);
        er.setFrequenza(freq);

        RecurringEvent.RicalcoloResult risultato = er.ricalcolaIstanze();
        for (Event e : risultato.getCreati()) {
            notifyEventCreated(e);
        }
        for (Event e : risultato.getRimossi()) {
            notifyEventDeleted(e);
        }
    }

    public void annullaServizio(Service servizio) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.containsService(servizio)) {
            throw new UseCaseLogicException("Il servizio non appartiene all'evento corrente");
        }
        if (servizio.getStato() == ServiceStatus.ANNULLATO || servizio.getStato() == ServiceStatus.TERMINATO) {
            throw new UseCaseLogicException("Il servizio non e' annullabile nello stato corrente");
        }

        servizio.annulla();
        notifyEventModified(currentEvent);
    }

    public ArrayList<SummarySheet> visualizzaStatoCucina() throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();

        LocalDate inizio = currentEvent.getDataInizio();
        LocalDate fine = currentEvent.getDataFine();

        ArrayList<SummarySheet> result = new ArrayList<>();
        for (Event altro : Event.loadAllEvents()) {
            if (altro.getId() == currentEvent.getId()
                    || altro.getStato() == EventStatus.ANNULLATO
                    || altro.getStato() == EventStatus.TERMINATO) {
                continue;
            }
            if (altro.getDataFine().isBefore(inizio) || altro.getDataInizio().isAfter(fine)) {
                continue;
            }
            for (Service s : altro.getServizi()) {
                result.addAll(SummarySheet.loadSummarySheetsByServiceId(s.getId()));
            }
        }
        return result;
    }

    public Service aggiungiServizio(String tipoServizio, int offsetInizio, LocalTime oraInizio, LocalTime oraFine,
            int numeroCommensali) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabile()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }

        Service s = currentEvent.creaServizio(tipoServizio, offsetInizio, oraInizio, oraFine, numeroCommensali);
        this.currentService = s;

        if (currentEvent.getMacroEventoId() > 0) {
            RecurringEvent er = currentEvent.getMacroEvento();
            for (Event sibling : er.getIstanze()) {
                if (sibling.getId() != currentEvent.getId() && sibling.getStato() == EventStatus.BOZZA) {
                    try {
                        sibling.creaServizio(tipoServizio, offsetInizio, oraInizio, oraFine, numeroCommensali);
                    } catch (UseCaseLogicException ignored) {
                    }
                }
            }
        }

        return s;
    }

    public ServiceShift inserisciTurnoServizio(Service servizio, int tempoPreparazione, int tempoRigoverno)
            throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabile()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }
        if (!currentEvent.containsService(servizio)) {
            throw new UseCaseLogicException("Il servizio non appartiene all'evento corrente");
        }

        LocalDate dataServizio = currentEvent.getDataInizio().plusDays(servizio.getOffsetInizio());
        LocalDateTime inizioServizio = LocalDateTime.of(dataServizio, servizio.getOraInizio());
        LocalDateTime fineServizio = LocalDateTime.of(dataServizio, servizio.getOraFine());
        if (!fineServizio.isAfter(inizioServizio)) {
            fineServizio = fineServizio.plusDays(1);
        }

        return servizio.creaTurnoServizio(tempoPreparazione, tempoRigoverno,
                inizioServizio, fineServizio, currentEvent.getLuogo());
    }

    public void assegnaChef(User chef) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabile()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }
        if (chef == null || !chef.isChef()) {
            throw new UseCaseLogicException("L'utente indicato non e' uno chef");
        }
        if (!new Chef(chef).isDisponibile(currentEvent)) {
            throw new UseCaseLogicException("Lo chef non e' disponibile nelle date dell'evento");
        }

        currentEvent.setChef(chef);
    }

    public Menu consultaMenuServizio(Service servizio) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.containsService(servizio)) {
            throw new UseCaseLogicException("Il servizio non appartiene all'evento corrente");
        }
        return servizio.getMenu();
    }

    public void approvaMenu(Service servizio, Menu menu) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isModificabile()) {
            throw new UseCaseLogicException("Evento non modificabile nello stato corrente");
        }
        if (!currentEvent.containsService(servizio)) {
            throw new UseCaseLogicException("Il servizio non appartiene all'evento corrente");
        }
        if (servizio.getMenu() == null) {
            throw new UseCaseLogicException("**7b.1** menu' non ancora proposto dallo chef");
        }
        if (menu == null || !menu.equals(servizio.getMenu())) {
            throw new UseCaseLogicException("Il menu indicato non e' quello scelto per il servizio");
        }
        if (servizio.getStato() != ServiceStatus.IN_PREPARAZIONE) {
            throw new UseCaseLogicException(
                    "Impossibile procedere con l'approvazione: servizio gia' confermato, annullato o terminato");
        }

        servizio.confermaMenu();

        boolean tuttiConfermati = true;
        for (Service s : currentEvent.getServizi()) {
            if (s.getStato() != ServiceStatus.CONFERMATO) {
                tuttiConfermati = false;
                break;
            }
        }
        if (tuttiConfermati) {
            currentEvent.setStato(EventStatus.IN_CORSO);
            notifyEventModified(currentEvent);
        }
    }

    public ProposalToModify proponiModificaMenu(Service servizio, Menu menu, List<MenuItem> voci,
            ProposalOperation operazione) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.containsService(servizio)) {
            throw new UseCaseLogicException("Il servizio non appartiene all'evento corrente");
        }
        if (servizio.getStato() != ServiceStatus.IN_PREPARAZIONE) {
            throw new UseCaseLogicException("Il menu e' gia' confermato: non e' possibile proporre modifiche");
        }
        if (menu == null || !menu.equals(servizio.getMenu())) {
            throw new UseCaseLogicException("Il menu indicato non e' quello scelto per il servizio");
        }

        return servizio.creaProposta(voci, operazione);
    }

    public ServiceAssignment assegnaPersonaleATurno(ServiceShift turno, User personale, String ruolo)
            throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!eventContainsTurno(turno)) {
            throw new UseCaseLogicException("Il turno non appartiene all'evento corrente");
        }

        if (personale == null || !personale.isServiceStaff()) {
            throw new UseCaseLogicException("L'utente indicato non fa parte del personale di servizio");
        }

        ServiceStaff staff = new ServiceStaff(personale);
        if (!staff.isDisponibile(turno)) {
            throw new UseCaseLogicException("Personale non disponibile o in conflitto per il turno");
        }

        return turno.creaIncarico(staff, ruolo);
    }

    public void assegnaCuocoATurno(ServiceShift turno, User cuoco) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!eventContainsTurno(turno)) {
            throw new UseCaseLogicException("Il turno non appartiene all'evento corrente");
        }

        if (cuoco == null || !cuoco.isCook()) {
            throw new UseCaseLogicException("L'utente indicato non e' un cuoco");
        }

        Cook c = new Cook(cuoco);
        if (!c.isDisponibile(turno)) {
            throw new UseCaseLogicException("Il cuoco non e' disponibile o e' in conflitto per il turno");
        }

        turno.setCuocoDiSupporto(c);
        notifyEventModified(currentEvent);
    }

    public void rimuoviPersonaleDaTurno(ServiceShift turno, User personale) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!eventContainsTurno(turno)) {
            throw new UseCaseLogicException("Il turno non appartiene all'evento corrente");
        }

        ServiceAssignment target = turno.getIncaricoDiPersonale(new ServiceStaff(personale));
        if (target == null) {
            throw new UseCaseLogicException("Nessun incarico trovato per questo membro del personale");
        }

        turno.rimuoviIncarico(target);
        notifyEventModified(currentEvent);
    }

    public void chiudiEvento(String note, String documentazione) throws UseCaseLogicException {
        requireOrganizer();
        requireCurrentEvent();
        if (!currentEvent.isChiudibile()) {
            throw new UseCaseLogicException("Ci sono ancora turni di servizio da svolgere");
        }

        currentEvent.chiudi(note, documentazione);
        notifyEventClosed(currentEvent);
    }

    private void notifyEventCreated(Event event) {
        for (EventReceiver r : eventReceivers) {
            r.updateEventCreated(event);
        }
    }

    private void notifyEventModified(Event event) {
        for (EventReceiver r : eventReceivers) {
            r.updateEventModified(event);
        }
    }

    private void notifyEventDeleted(Event event) {
        for (EventReceiver r : eventReceivers) {
            r.updateEventDeleted(event);
        }
    }

    private void notifyEventCancelled(Event event) {
        for (EventReceiver r : eventReceivers) {
            r.updateEventCancelled(event);
        }
    }

    private void notifyEventClosed(Event event) {
        for (EventReceiver r : eventReceivers) {
            r.updateEventClosed(event);
        }
    }
}
