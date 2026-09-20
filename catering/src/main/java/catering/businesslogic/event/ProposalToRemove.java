package catering.businesslogic.event;

import java.util.List;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;

public class ProposalToRemove extends ProposalToModify {

    private ProposalToRemove(int servizioId, Menu menu, List<MenuItem> voci) {
        super(servizioId, menu, voci);
    }

    public static ProposalToRemove create(int servizioId, Menu menu, List<MenuItem> voci) {
        return new ProposalToRemove(servizioId, menu, voci);
    }

    @Override
    public ProposalOperation getOperazione() {
        return ProposalOperation.REMOVE;
    }
}
