package catering.businesslogic.event;

import java.util.List;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;

public class ProposalToAdd extends ProposalToModify {

    private ProposalToAdd(int servizioId, Menu menu, List<MenuItem> voci) {
        super(servizioId, menu, voci);
    }

    public static ProposalToAdd create(int servizioId, Menu menu, List<MenuItem> voci) {
        return new ProposalToAdd(servizioId, menu, voci);
    }

    @Override
    public ProposalOperation getOperazione() {
        return ProposalOperation.ADD;
    }
}
