package catering.businesslogic.event;

public class EliminaEventoCommand implements EventCommand {

    @Override
    public boolean isApplicabile(Event e) {
        return e.isEliminabile();
    }

    @Override
    public void execute(Event e) {
    }
}
