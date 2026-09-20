package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

public class Event {

    private int id;
    private String name;
    private String luogo;
    private LocalDate dataInizio;
    private int durata;
    private int numeroServiziRichiesti;
    private int numeroPartecipanti;
    private int numPartecipantiIniziale;
    private String cliente;
    private String note;
    private String documentazioneFinale;
    private EventStatus stato;
    private User chef;
    private Organizer organizzatore;
    private ArrayList<Service> servizi = new ArrayList<>();
    private int macroEventoId;

    public Event() {
    }

    public Event(String name) {
        this.name = name;
    }

    public static Event create(String luogo, LocalDate dataInizio, int durata, int numeroServiziRichiesti,
            int numeroPartecipanti, String note, String cliente, Organizer organizzatore) {
        Event e = new Event();
        e.luogo = luogo;
        e.dataInizio = dataInizio;
        e.durata = durata;
        e.numeroServiziRichiesti = numeroServiziRichiesti;
        e.numeroPartecipanti = numeroPartecipanti;
        e.numPartecipantiIniziale = numeroPartecipanti;
        e.note = note;
        e.cliente = cliente;
        e.organizzatore = organizzatore;
        e.stato = EventStatus.BOZZA;
        return e;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLuogo() {
        return luogo;
    }

    public LocalDate getDataInizio() {
        return dataInizio;
    }

    public LocalDate getDataFine() {
        return dataInizio != null ? dataInizio.plusDays(Math.max(durata, 1) - 1) : null;
    }

    public int getDurata() {
        return durata;
    }

    public int getNumeroServiziRichiesti() {
        return numeroServiziRichiesti;
    }

    public int getNumeroPartecipanti() {
        return numeroPartecipanti;
    }

    public int getNumPartecipantiIniziale() {
        return numPartecipantiIniziale;
    }

    public String getCliente() {
        return cliente;
    }

    public String getNote() {
        return note;
    }

    public String getDocumentazioneFinale() {
        return documentazioneFinale;
    }

    public EventStatus getStato() {
        return stato;
    }

    public void setStato(EventStatus stato) {
        this.stato = stato;
    }

    public User getChef() {
        return chef;
    }

    public int getChefId() {
        return chef != null ? chef.getId() : 0;
    }

    public void setChef(User chef) {
        this.chef = chef;
        if (id > 0) {
            PersistenceManager.executeUpdate("UPDATE Events SET chef_id = ? WHERE id = ?",
                    chef != null ? chef.getId() : null, id);
        }
    }

    public Organizer getOrganizzatore() {
        return organizzatore;
    }

    public ArrayList<Service> getServizi() {
        return servizi;
    }

    public ArrayList<Service> getServices() {
        return servizi;
    }

    void setMacroEventoId(int macroEventoId) {
        this.macroEventoId = macroEventoId;
    }

    public int getMacroEventoId() {
        return macroEventoId;
    }

    public RecurringEvent getMacroEvento() {
        return macroEventoId > 0 ? RecurringEvent.loadById(macroEventoId) : null;
    }

    public void addService(Service service) {
        servizi.add(service);
    }

    public void removeService(Service service) {
        servizi.remove(service);
    }

    public boolean containsService(Service service) {
        return servizi.contains(service);
    }

    public boolean isModificabile() {
        return stato == EventStatus.BOZZA;
    }

    public boolean isModificabileInCorso() {
        return stato == EventStatus.IN_CORSO;
    }

    public boolean isEliminabile() {
        return stato == EventStatus.BOZZA;
    }

    public boolean isAnnullabile() {
        return stato == EventStatus.IN_CORSO;
    }

    public boolean isChiudibile() {
        if (stato != EventStatus.IN_CORSO) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Service s : servizi) {
            for (ServiceShift t : s.getTurni()) {
                if (t.getFine().isAfter(now)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isVariazionePartecipantiEccessiva(int nuovoNumero) {
        if (numPartecipantiIniziale <= 0) {
            return false;
        }
        double variazione = Math.abs(nuovoNumero - numPartecipantiIniziale) / (double) numPartecipantiIniziale;
        return variazione > 0.30;
    }

    public Service creaServizio(String tipoServizio, int offsetInizio, java.time.LocalTime oraInizio,
            java.time.LocalTime oraFine, int numeroCommensali) throws UseCaseLogicException {
        LocalDate dataServizio = dataInizio.plusDays(offsetInizio);
        LocalDate oggi = LocalDate.now();
        if (dataServizio.isBefore(oggi) || dataServizio.isBefore(dataInizio) || dataServizio.isAfter(getDataFine())) {
            throw new UseCaseLogicException("**3a.1** Data/ora del servizio non valida per l'evento");
        }

        Service s = Service.create(this.id, tipoServizio, offsetInizio, oraInizio, oraFine, numeroCommensali);
        s.saveNewService();
        servizi.add(s);
        return s;
    }

    public void rimuoviServizio(Service servizio) {
        for (ServiceShift t : new ArrayList<>(servizio.getTurni())) {
            t.delete();
        }
        servizio.getTurni().clear();
        servizio.deleteService();
        servizi.remove(servizio);
    }

    public void aggiornaAttributi(String luogo, LocalDate dataInizio, Integer durata,
            Integer numeroServiziRichiesti, Integer numeroPartecipanti, String note, String cliente) {
        if (luogo != null)
            this.luogo = luogo;
        if (dataInizio != null)
            this.dataInizio = dataInizio;
        if (durata != null)
            this.durata = durata;
        if (numeroServiziRichiesti != null)
            this.numeroServiziRichiesti = numeroServiziRichiesti;
        if (numeroPartecipanti != null) {
            this.numeroPartecipanti = numeroPartecipanti;
            this.numPartecipantiIniziale = numeroPartecipanti;
        }
        if (note != null)
            this.note = note;
        if (cliente != null)
            this.cliente = cliente;
    }

    public void aggiornaInCorso(Integer numeroPartecipanti, String note) {
        if (numeroPartecipanti != null) {
            this.numeroPartecipanti = numeroPartecipanti;
        }
        if (note != null) {
            this.note = note;
        }
    }

    public void chiudi(String noteFinali, String documentazione) {
        this.stato = EventStatus.TERMINATO;
        this.note = (this.note != null && !this.note.isEmpty())
                ? this.note + "\n" + (noteFinali != null ? noteFinali : "")
                : noteFinali;
        this.documentazioneFinale = documentazione;

        for (Service s : servizi) {
            s.setStato(ServiceStatus.TERMINATO);
        }
    }

    public void annulla() {
        this.stato = EventStatus.ANNULLATO;
        for (Service s : servizi) {
            s.annulla();
        }
    }

    public void eseguiOperazione(EventCommand cmd) {
        cmd.execute(this);
    }

    public void saveNewEvent() {
        String query = "INSERT INTO Events (name, luogo, data_inizio, durata, numero_servizi_richiesti, " +
                "numero_partecipanti, num_partecipanti_iniziale, cliente, note, documentazione_finale, stato, " +
                "chef_id, organizzatore_id, macro_evento_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PersistenceManager.executeUpdate(query,
                name, luogo, dataInizio != null ? dataInizio.toString() : null, durata, numeroServiziRichiesti,
                numeroPartecipanti, numPartecipantiIniziale, cliente, note, documentazioneFinale,
                stato != null ? stato.name() : EventStatus.BOZZA.name(),
                getChefId() > 0 ? getChefId() : null,
                organizzatore != null ? organizzatore.getId() : null,
                macroEventoId > 0 ? macroEventoId : null);

        id = PersistenceManager.getLastId();
    }

    public void updateEvent() {
        String query = "UPDATE Events SET name = ?, luogo = ?, data_inizio = ?, durata = ?, " +
                "numero_servizi_richiesti = ?, numero_partecipanti = ?, num_partecipanti_iniziale = ?, " +
                "cliente = ?, note = ?, documentazione_finale = ?, stato = ?, chef_id = ?, organizzatore_id = ? " +
                "WHERE id = ?";

        PersistenceManager.executeUpdate(query,
                name, luogo, dataInizio != null ? dataInizio.toString() : null, durata, numeroServiziRichiesti,
                numeroPartecipanti, numPartecipantiIniziale, cliente, note, documentazioneFinale,
                stato.name(), getChefId() > 0 ? getChefId() : null,
                organizzatore != null ? organizzatore.getId() : null, id);
    }

    public boolean deleteEvent() {
        for (Service service : new ArrayList<>(servizi)) {
            rimuoviServizio(service);
        }

        String query = "DELETE FROM Events WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
    }

    public static ArrayList<Event> loadAllEvents() {
        ArrayList<Event> events = new ArrayList<>();
        String query = "SELECT * FROM Events ORDER BY data_inizio DESC";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                events.add(mapRow(rs));
            }
        });

        for (Event e : events) {
            e.servizi = Service.loadServicesForEvent(e.id);
        }

        return events;
    }

    public static Event loadById(int id) {
        return loadEventByQuery("SELECT * FROM Events WHERE id = ?", id);
    }

    public static Event loadByName(String name) {
        return loadEventByQuery("SELECT * FROM Events WHERE name = ?", name);
    }

    static ArrayList<Event> loadInstancesOf(int macroEventoId) {
        ArrayList<Event> events = new ArrayList<>();
        String query = "SELECT * FROM Events WHERE macro_evento_id = ? ORDER BY data_inizio";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                events.add(mapRow(rs));
            }
        }, macroEventoId);

        for (Event e : events) {
            e.servizi = Service.loadServicesForEvent(e.id);
        }

        return events;
    }

    private static Event loadEventByQuery(String query, Object param) {
        final Event[] eventHolder = new Event[1];

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                eventHolder[0] = mapRow(rs);
            }
        }, param);

        Event result = eventHolder[0];
        if (result != null) {
            result.servizi = Service.loadServicesForEvent(result.id);
        }

        return result;
    }

    private static Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.id = rs.getInt("id");
        e.name = rs.getString("name");
        e.luogo = rs.getString("luogo");

        String dataInizioStr = rs.getString("data_inizio");
        e.dataInizio = (dataInizioStr != null && !dataInizioStr.isEmpty()) ? LocalDate.parse(dataInizioStr) : null;

        e.durata = rs.getInt("durata");
        e.numeroServiziRichiesti = rs.getInt("numero_servizi_richiesti");
        e.numeroPartecipanti = rs.getInt("numero_partecipanti");
        e.numPartecipantiIniziale = rs.getInt("num_partecipanti_iniziale");
        e.cliente = rs.getString("cliente");
        e.note = rs.getString("note");
        e.documentazioneFinale = rs.getString("documentazione_finale");

        String stato = rs.getString("stato");
        e.stato = (stato != null) ? EventStatus.valueOf(stato) : EventStatus.BOZZA;

        int chefId = rs.getInt("chef_id");
        if (!rs.wasNull() && chefId > 0) {
            e.chef = User.load(chefId);
        }

        int organizzatoreId = rs.getInt("organizzatore_id");
        if (!rs.wasNull() && organizzatoreId > 0) {
            e.organizzatore = new Organizer(User.load(organizzatoreId));
        }

        int macroEventoId = rs.getInt("macro_evento_id");
        e.macroEventoId = rs.wasNull() ? 0 : macroEventoId;

        return e;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Event))
            return false;
        return this.id > 0 && this.id == ((Event) obj).id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Event [id=" + id + ", luogo=" + luogo + ", dataInizio=" + dataInizio +
                ", stato=" + stato + ", servizi=" + servizi.size() + "]";
    }
}
