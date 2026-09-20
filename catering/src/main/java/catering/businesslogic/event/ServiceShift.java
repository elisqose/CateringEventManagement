package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

public class ServiceShift {

    static final DateTimeFormatter DB_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int id;
    private int serviceId;
    private LocalDateTime inizio;
    private LocalDateTime fine;
    private String luogo;
    private int offsetPrima;
    private int offsetDopo;
    private Cook cuocoDiSupporto;
    private ArrayList<ServiceAssignment> incarichi = new ArrayList<>();

    public static ServiceShift create(int serviceId, int tempoPreparazione, int tempoRigoverno,
            LocalDateTime inizioServizio, LocalDateTime fineServizio, String luogo) {
        ServiceShift t = new ServiceShift();
        t.serviceId = serviceId;
        t.offsetPrima = tempoPreparazione;
        t.offsetDopo = tempoRigoverno;
        t.inizio = inizioServizio.minusMinutes(tempoPreparazione);
        t.fine = fineServizio.plusMinutes(tempoRigoverno);
        t.luogo = luogo;
        return t;
    }

    public int getId() {
        return id;
    }

    public int getServiceId() {
        return serviceId;
    }

    public LocalDateTime getInizio() {
        return inizio;
    }

    public LocalDateTime getFine() {
        return fine;
    }

    public LocalDate getData() {
        return inizio != null ? inizio.toLocalDate() : null;
    }

    public String getLuogo() {
        return luogo;
    }

    public int getOffsetPrima() {
        return offsetPrima;
    }

    public int getOffsetDopo() {
        return offsetDopo;
    }

    public Cook getCuocoDiSupporto() {
        return cuocoDiSupporto;
    }

    public void setCuocoDiSupporto(Cook cuoco) {
        this.cuocoDiSupporto = cuoco;
        if (id > 0) {
            PersistenceManager.executeUpdate("UPDATE ServiceShifts SET cuoco_supporto_id = ? WHERE id = ?",
                    cuoco != null ? cuoco.getId() : null, id);
        }
    }

    public ArrayList<ServiceAssignment> getIncarichi() {
        return incarichi;
    }

    public boolean isModificabile() {
        if (inizio == null)
            return false;
        return LocalDateTime.now().isBefore(inizio);
    }

    public ServiceAssignment creaIncarico(ServiceStaff personale, String ruolo) {
        ServiceAssignment a = ServiceAssignment.create(this.id, personale, ruolo);
        a.saveNew();
        incarichi.add(a);
        return a;
    }

    public ServiceAssignment getIncaricoDiPersonale(ServiceStaff personale) {
        for (ServiceAssignment a : incarichi) {
            if (a.getPersonale().getUser().equals(personale.getUser())) {
                return a;
            }
        }
        return null;
    }

    public void rimuoviIncarico(ServiceAssignment a) throws UseCaseLogicException {
        if (!isModificabile()) {
            throw new UseCaseLogicException("**8b.1a.1** Turno di servizio non piu' modificabile");
        }
        if (!incarichi.remove(a)) {
            throw new UseCaseLogicException("Incarico non presente in questo turno");
        }
        a.delete();
    }

    void saveNew() {
        String query = "INSERT INTO ServiceShifts (service_id, inizio, fine, luogo, " +
                "offset_prima, offset_dopo, cuoco_supporto_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PersistenceManager.executeUpdate(query, serviceId, inizio.format(DB_DATETIME), fine.format(DB_DATETIME),
                luogo, offsetPrima, offsetDopo, cuocoDiSupporto != null ? cuocoDiSupporto.getId() : null);
        this.id = PersistenceManager.getLastId();
    }

    void delete() {
        for (ServiceAssignment a : new ArrayList<>(incarichi)) {
            a.delete();
        }
        PersistenceManager.executeUpdate("DELETE FROM ServiceShifts WHERE id = ?", id);
    }

    static ArrayList<ServiceShift> loadForService(int serviceId) {
        ArrayList<ServiceShift> result = new ArrayList<>();
        String query = "SELECT * FROM ServiceShifts WHERE service_id = ?";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                ServiceShift t = new ServiceShift();
                t.id = rs.getInt("id");
                t.serviceId = rs.getInt("service_id");
                t.inizio = LocalDateTime.parse(rs.getString("inizio"), DB_DATETIME);
                t.fine = LocalDateTime.parse(rs.getString("fine"), DB_DATETIME);
                t.luogo = rs.getString("luogo");
                t.offsetPrima = rs.getInt("offset_prima");
                t.offsetDopo = rs.getInt("offset_dopo");
                int cuocoId = rs.getInt("cuoco_supporto_id");
                if (!rs.wasNull() && cuocoId > 0) {
                    t.cuocoDiSupporto = new Cook(User.load(cuocoId));
                }
                result.add(t);
            }
        }, serviceId);

        for (ServiceShift t : result) {
            t.incarichi = ServiceAssignment.loadForTurno(t.id);
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ServiceShift))
            return false;
        return this.id > 0 && this.id == ((ServiceShift) obj).id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
