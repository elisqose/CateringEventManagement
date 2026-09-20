package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

public class RecurringEvent {

    private int id;
    private String luogo;
    private int durata;
    private int numeroServiziRichiesti;
    private int numeroPartecipanti;
    private String note;
    private String cliente;
    private Organizer organizzatore;
    private AbstractFrequency frequenza;
    private ArrayList<Event> istanze = new ArrayList<>();

    public static RecurringEvent create(String luogo, int durata, int numeroServiziRichiesti,
            int numeroPartecipanti, String note, String cliente, Organizer organizzatore,
            AbstractFrequency frequenza) {
        RecurringEvent me = new RecurringEvent();
        me.luogo = luogo;
        me.durata = durata;
        me.numeroServiziRichiesti = numeroServiziRichiesti;
        me.numeroPartecipanti = numeroPartecipanti;
        me.note = note;
        me.cliente = cliente;
        me.organizzatore = organizzatore;
        me.frequenza = frequenza;
        return me;
    }

    public int getId() {
        return id;
    }

    public String getLuogo() {
        return luogo;
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

    public String getNote() {
        return note;
    }

    public String getCliente() {
        return cliente;
    }

    public Organizer getOrganizzatore() {
        return organizzatore;
    }

    public AbstractFrequency getFrequenza() {
        return frequenza;
    }

    public void setFrequenza(AbstractFrequency frequenza) {
        this.frequenza = frequenza;
        if (id > 0) {
            PersistenceManager.executeUpdate(
                    "UPDATE RecurringEvents SET tipo_frequenza = ?, data_fine = ?, numero_occorrenze = ? WHERE id = ?",
                    frequenza.getTipo(),
                    frequenza.getDataFine() != null ? frequenza.getDataFine().toString() : null,
                    frequenza.getNumeroOccorrenze(), id);
        }
    }

    public ArrayList<Event> getIstanze() {
        return istanze;
    }

    public Event creaEvento(LocalDate dataInizio) {
        Event e = Event.create(luogo, dataInizio, durata, numeroServiziRichiesti, numeroPartecipanti, note, cliente,
                organizzatore);
        e.setMacroEventoId(this.id);
        istanze.add(e);
        return e;
    }

    public boolean rimuoviIstanza(Event e) {
        return istanze.remove(e);
    }

    public RicalcoloResult ricalcolaIstanze() {
        List<Event> creati = new ArrayList<>();
        List<Event> rimossi = new ArrayList<>();
        if (istanze.isEmpty() || frequenza == null) {
            return new RicalcoloResult(creati, rimossi);
        }

        LocalDate anchor = istanze.get(0).getDataInizio();
        Set<LocalDate> targetDates = new HashSet<>();
        LocalDate current = anchor;
        int count = 0;
        while (true) {
            targetDates.add(current);
            count++;
            if (frequenza.getNumeroOccorrenze() != null && count >= frequenza.getNumeroOccorrenze()) {
                break;
            }
            LocalDate next = frequenza.prossimaOccorrenza(current);
            if (frequenza.getDataFine() != null && next.isAfter(frequenza.getDataFine())) {
                break;
            }
            current = next;
        }

        for (Event e : new ArrayList<>(istanze)) {
            if (e.getStato() == EventStatus.BOZZA && !targetDates.contains(e.getDataInizio())) {
                istanze.remove(e);
                rimossi.add(e);
            } else {
                targetDates.remove(e.getDataInizio());
            }
        }

        for (LocalDate date : targetDates) {
            creati.add(creaEvento(date));
        }

        return new RicalcoloResult(creati, rimossi);
    }

    public static final class RicalcoloResult {
        private final List<Event> creati;
        private final List<Event> rimossi;

        private RicalcoloResult(List<Event> creati, List<Event> rimossi) {
            this.creati = creati;
            this.rimossi = rimossi;
        }

        public List<Event> getCreati() {
            return creati;
        }

        public List<Event> getRimossi() {
            return rimossi;
        }
    }

    void saveNew() {
        String query = "INSERT INTO RecurringEvents (luogo, durata, numero_servizi_richiesti, " +
                "numero_partecipanti, cliente, note, organizzatore_id, tipo_frequenza, data_fine, numero_occorrenze) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PersistenceManager.executeUpdate(query, luogo, durata, numeroServiziRichiesti, numeroPartecipanti,
                cliente, note, organizzatore.getId(), frequenza.getTipo(),
                frequenza.getDataFine() != null ? frequenza.getDataFine().toString() : null,
                frequenza.getNumeroOccorrenze());

        this.id = PersistenceManager.getLastId();
    }

    public static RecurringEvent loadById(int id) {
        final RecurringEvent[] holder = new RecurringEvent[1];
        String query = "SELECT * FROM RecurringEvents WHERE id = ?";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                RecurringEvent me = new RecurringEvent();
                me.id = rs.getInt("id");
                me.luogo = rs.getString("luogo");
                me.durata = rs.getInt("durata");
                me.numeroServiziRichiesti = rs.getInt("numero_servizi_richiesti");
                me.numeroPartecipanti = rs.getInt("numero_partecipanti");
                me.cliente = rs.getString("cliente");
                me.note = rs.getString("note");
                me.organizzatore = new Organizer(User.load(rs.getInt("organizzatore_id")));

                String dataFineStr = rs.getString("data_fine");
                LocalDate dataFine = (dataFineStr != null && !dataFineStr.isEmpty())
                        ? LocalDate.parse(dataFineStr)
                        : null;
                int numOcc = rs.getInt("numero_occorrenze");
                Integer numeroOccorrenze = rs.wasNull() ? null : numOcc;
                me.frequenza = AbstractFrequency.create(rs.getString("tipo_frequenza"), dataFine, numeroOccorrenze);

                holder[0] = me;
            }
        }, id);

        RecurringEvent me = holder[0];
        if (me != null) {
            me.istanze = Event.loadInstancesOf(me.id);
        }
        return me;
    }
}
