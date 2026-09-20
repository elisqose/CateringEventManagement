package catering.businesslogic.event;

public class AnnullaEventoCommand implements EventCommand {

    @Override
    public boolean isApplicabile(Event e) {
        return e.isAnnullabile();
    }

    @Override
    public void execute(Event e) {
        e.annulla();
    }
}
