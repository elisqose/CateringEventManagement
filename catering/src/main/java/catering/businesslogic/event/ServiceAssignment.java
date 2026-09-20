package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

public class ServiceAssignment {

    private int id;
    private int turnoId;
    private String ruolo;
    private ServiceStaff personale;

    public static ServiceAssignment create(int turnoId, ServiceStaff personale, String ruolo) {
        ServiceAssignment a = new ServiceAssignment();
        a.turnoId = turnoId;
        a.personale = personale;
        a.ruolo = ruolo;
        return a;
    }

    public int getId() {
        return id;
    }

    public int getTurnoId() {
        return turnoId;
    }

    public String getRuolo() {
        return ruolo;
    }

    public ServiceStaff getPersonale() {
        return personale;
    }

    void saveNew() {
        String query = "INSERT INTO ServiceAssignments (turno_id, personale_id, ruolo) VALUES (?, ?, ?)";
        PersistenceManager.executeUpdate(query, turnoId, personale.getId(), ruolo);
        this.id = PersistenceManager.getLastId();
    }

    void delete() {
        PersistenceManager.executeUpdate("DELETE FROM ServiceAssignments WHERE id = ?", id);
    }

    static ArrayList<ServiceAssignment> loadForTurno(int turnoId) {
        ArrayList<ServiceAssignment> result = new ArrayList<>();
        String query = "SELECT * FROM ServiceAssignments WHERE turno_id = ?";

        PersistenceManager.executeQuery(query, new catering.persistence.ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                ServiceAssignment a = new ServiceAssignment();
                a.id = rs.getInt("id");
                a.turnoId = rs.getInt("turno_id");
                a.ruolo = rs.getString("ruolo");
                User u = User.load(rs.getInt("personale_id"));
                a.personale = new ServiceStaff(u);
                result.add(a);
            }
        }, turnoId);

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ServiceAssignment))
            return false;
        ServiceAssignment other = (ServiceAssignment) obj;
        if (this.id > 0 && other.id > 0)
            return this.id == other.id;
        return this.turnoId == other.turnoId && java.util.Objects.equals(this.personale, other.personale);
    }

    @Override
    public int hashCode() {
        return id > 0 ? Integer.hashCode(id) : java.util.Objects.hash(turnoId, personale);
    }
}
