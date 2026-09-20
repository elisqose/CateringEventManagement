package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

public class Service {

    private int id;
    private String name;
    private String tipologia;
    private int offsetInizio;
    private LocalTime oraInizio;
    private LocalTime oraFine;
    private int numeroCommensali;
    private ServiceStatus stato;
    private int eventId;
    private String location;
    private Menu menu;
    private ArrayList<ServiceShift> turni = new ArrayList<>();
    private ArrayList<ProposalToModify> proposte = new ArrayList<>();

    public Service() {
    }

    public Service(String name) {
        this.name = name;
    }

    public static Service create(int eventId, String tipoServizio, int offsetInizio,
            LocalTime oraInizio, LocalTime oraFine, int numeroCommensali) {
        Service s = new Service();
        s.eventId = eventId;
        s.tipologia = tipoServizio;
        s.offsetInizio = offsetInizio;
        s.oraInizio = oraInizio;
        s.oraFine = oraFine;
        s.numeroCommensali = numeroCommensali;
        s.stato = ServiceStatus.IN_PREPARAZIONE;
        return s;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : tipologia;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTipologia() {
        return tipologia;
    }

    public int getOffsetInizio() {
        return offsetInizio;
    }

    public LocalTime getOraInizio() {
        return oraInizio;
    }

    public LocalTime getOraFine() {
        return oraFine;
    }

    public int getNumeroCommensali() {
        return numeroCommensali;
    }

    public ServiceStatus getStato() {
        return stato;
    }

    public void setStato(ServiceStatus stato) {
        this.stato = stato;
        if (id > 0) {
            PersistenceManager.executeUpdate("UPDATE Services SET stato = ? WHERE id = ?", stato.name(), id);
        }
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getMenuId() {
        return (menu != null) ? menu.getId() : 0;
    }

    public Menu getMenu() {
        return menu;
    }

    public void setMenu(Menu menu) {
        this.menu = menu;
        if (id > 0) {
            PersistenceManager.executeUpdate("UPDATE Services SET approved_menu_id = ? WHERE id = ?",
                    menu != null ? menu.getId() : 0, id);
        }
    }

    public void confermaMenu() {
        setStato(ServiceStatus.CONFERMATO);
    }

    public void annulla() {
        setStato(ServiceStatus.ANNULLATO);
        for (ServiceShift t : turni) {
            for (ServiceAssignment a : new ArrayList<>(t.getIncarichi())) {
                a.delete();
            }
            t.getIncarichi().clear();
            t.setCuocoDiSupporto(null);
        }
    }

    public ArrayList<ServiceShift> getTurni() {
        return turni;
    }

    public List<ProposalToModify> getProposte() {
        return proposte;
    }

    public ServiceShift creaTurnoServizio(int tempoPreparazione, int tempoRigoverno,
            LocalDateTime inizioServizio, LocalDateTime fineServizio, String luogo) {
        ServiceShift t = ServiceShift.create(this.id, tempoPreparazione, tempoRigoverno,
                inizioServizio, fineServizio, luogo);
        t.saveNew();
        turni.add(t);
        return t;
    }

    public ProposalToModify creaProposta(List<MenuItem> voci, ProposalOperation operazione)
            throws UseCaseLogicException {
        if (menu == null) {
            throw new UseCaseLogicException("Nessun menu scelto per il servizio");
        }
        ProposalToModify p = (operazione == ProposalOperation.ADD)
                ? ProposalToAdd.create(this.id, menu, voci)
                : ProposalToRemove.create(this.id, menu, voci);
        p.saveNew();
        proposte.add(p);
        return p;
    }

    public void saveNewService() {
        String query = "INSERT INTO Services (event_id, name, tipologia, offset_inizio, ora_inizio, ora_fine, " +
                "numero_commensali, stato, location, approved_menu_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PersistenceManager.executeUpdate(query,
                eventId, name, tipologia, offsetInizio,
                oraInizio != null ? oraInizio.toString() : null,
                oraFine != null ? oraFine.toString() : null,
                numeroCommensali, stato != null ? stato.name() : ServiceStatus.IN_PREPARAZIONE.name(),
                location, getMenuId());

        this.id = PersistenceManager.getLastId();
    }

    public void updateService() {
        String query = "UPDATE Services SET name = ?, tipologia = ?, offset_inizio = ?, ora_inizio = ?, " +
                "ora_fine = ?, numero_commensali = ?, stato = ?, location = ?, approved_menu_id = ? WHERE id = ?";

        PersistenceManager.executeUpdate(query,
                name, tipologia, offsetInizio,
                oraInizio != null ? oraInizio.toString() : null,
                oraFine != null ? oraFine.toString() : null,
                numeroCommensali, stato.name(), location, getMenuId(), id);
    }

    public boolean deleteService() {
        for (ServiceShift t : new ArrayList<>(turni)) {
            t.delete();
        }
        turni.clear();

        PersistenceManager.executeUpdate(
                "DELETE FROM ProposalItems WHERE proposal_id IN " +
                        "(SELECT id FROM ProposalsToModify WHERE service_id = ?)",
                id);
        PersistenceManager.executeUpdate("DELETE FROM ProposalsToModify WHERE service_id = ?", id);
        proposte.clear();

        String query = "DELETE FROM Services WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
    }

    public static ArrayList<Service> loadServicesForEvent(int eventId) {
        ArrayList<Service> services = new ArrayList<>();
        String query = "SELECT * FROM Services WHERE event_id = ? ORDER BY offset_inizio, ora_inizio";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                services.add(mapRow(rs));
            }
        }, eventId);

        for (Service s : services) {
            s.turni = ServiceShift.loadForService(s.id);
            s.proposte = ProposalToModify.loadForService(s.id);
        }

        return services;
    }

    public static Service loadById(int id) {
        String query = "SELECT * FROM Services WHERE id = ?";
        return loadServiceByQuery(query, id);
    }

    public static Service loadByName(String name) {
        String query = "SELECT * FROM Services WHERE name = ?";
        return loadServiceByQuery(query, name);
    }

    private static Service loadServiceByQuery(String query, Object param) {
        final Service[] serviceHolder = new Service[1];

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                serviceHolder[0] = mapRow(rs);
            }
        }, param);

        Service result = serviceHolder[0];
        if (result != null) {
            result.turni = ServiceShift.loadForService(result.id);
        }

        return result;
    }

    private static Service mapRow(ResultSet rs) throws SQLException {
        Service s = new Service();
        s.id = rs.getInt("id");
        s.name = rs.getString("name");
        s.tipologia = rs.getString("tipologia");
        s.offsetInizio = rs.getInt("offset_inizio");

        String oi = rs.getString("ora_inizio");
        String of = rs.getString("ora_fine");
        s.oraInizio = (oi != null && !oi.isEmpty()) ? LocalTime.parse(normalizeTime(oi)) : null;
        s.oraFine = (of != null && !of.isEmpty()) ? LocalTime.parse(normalizeTime(of)) : null;

        s.numeroCommensali = rs.getInt("numero_commensali");
        String stato = rs.getString("stato");
        s.stato = (stato != null) ? ServiceStatus.valueOf(stato) : ServiceStatus.IN_PREPARAZIONE;
        s.location = rs.getString("location");
        s.eventId = rs.getInt("event_id");

        int menuId = rs.getInt("approved_menu_id");
        if (menuId > 0) {
            s.menu = Menu.load(menuId);
        }

        return s;
    }

    private static String normalizeTime(String value) {
        return (value.length() == 5) ? value + ":00" : value;
    }

    @Override
    public String toString() {
        return "Service [id=" + id + ", tipologia=" + tipologia + ", stato=" + stato +
                ", menu=" + (menu != null ? menu.getTitle() : "none") + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        Service other = (Service) obj;

        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }

        return java.util.Objects.equals(this.name, other.name)
                && java.util.Objects.equals(this.tipologia, other.tipologia)
                && this.offsetInizio == other.offsetInizio
                && java.util.Objects.equals(this.oraInizio, other.oraInizio)
                && java.util.Objects.equals(this.oraFine, other.oraFine);
    }

    @Override
    public int hashCode() {
        return id > 0 ? Integer.hashCode(id) : java.util.Objects.hash(name, tipologia, offsetInizio);
    }
}
