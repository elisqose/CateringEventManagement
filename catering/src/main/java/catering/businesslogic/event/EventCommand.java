package catering.businesslogic.event;

public interface EventCommand {

    boolean isApplicabile(Event e);

    void execute(Event e);
}
