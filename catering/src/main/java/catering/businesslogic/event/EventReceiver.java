package catering.businesslogic.event;

public interface EventReceiver {

    void updateEventCreated(Event event);

    void updateEventModified(Event event);

    void updateEventDeleted(Event event);

    void updateEventCancelled(Event event);

    void updateEventClosed(Event event);
}
