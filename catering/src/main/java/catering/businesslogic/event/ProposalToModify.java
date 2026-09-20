package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

public abstract class ProposalToModify {

    protected int id;
    protected int servizioId;
    protected Menu menu;
    protected List<MenuItem> voci;

    protected ProposalToModify(int servizioId, Menu menu, List<MenuItem> voci) {
        this.servizioId = servizioId;
        this.menu = menu;
        this.voci = voci != null ? voci : new ArrayList<>();
    }

    public abstract ProposalOperation getOperazione();

    public int getId() {
        return id;
    }

    public Menu getMenu() {
        return menu;
    }

    public List<MenuItem> getVoci() {
        return voci;
    }

    static ArrayList<ProposalToModify> loadForService(int serviceId) {
        ArrayList<ProposalToModify> result = new ArrayList<>();
        final List<int[]> rows = new ArrayList<>();
        final List<String> ops = new ArrayList<>();

        PersistenceManager.executeQuery(
                "SELECT id, menu_id, operation FROM ProposalsToModify WHERE service_id = ?",
                new ResultHandler() {
                    @Override
                    public void handle(ResultSet rs) throws SQLException {
                        rows.add(new int[] { rs.getInt("id"), rs.getInt("menu_id") });
                        ops.add(rs.getString("operation"));
                    }
                }, serviceId);

        for (int i = 0; i < rows.size(); i++) {
            int proposalId = rows.get(i)[0];
            int menuId = rows.get(i)[1];

            final List<Integer> itemIds = new ArrayList<>();
            PersistenceManager.executeQuery(
                    "SELECT menu_item_id FROM ProposalItems WHERE proposal_id = ?",
                    new ResultHandler() {
                        @Override
                        public void handle(ResultSet rs) throws SQLException {
                            itemIds.add(rs.getInt("menu_item_id"));
                        }
                    }, proposalId);

            List<MenuItem> voci = new ArrayList<>();
            for (Integer itemId : itemIds) {
                MenuItem mi = MenuItem.loadById(itemId);
                if (mi != null) {
                    voci.add(mi);
                }
            }

            Menu menu = Menu.load(menuId);
            ProposalToModify p = ProposalOperation.ADD.name().equals(ops.get(i))
                    ? ProposalToAdd.create(serviceId, menu, voci)
                    : ProposalToRemove.create(serviceId, menu, voci);
            p.id = proposalId;
            result.add(p);
        }

        return result;
    }

    void saveNew() {
        String query = "INSERT INTO ProposalsToModify (service_id, menu_id, operation) VALUES (?, ?, ?)";
        PersistenceManager.executeUpdate(query, servizioId, menu.getId(), getOperazione().name());
        this.id = PersistenceManager.getLastId();

        for (MenuItem mi : voci) {
            PersistenceManager.executeUpdate(
                    "INSERT INTO ProposalItems (proposal_id, menu_item_id) VALUES (?, ?)", this.id, mi.getId());
        }
    }
}
