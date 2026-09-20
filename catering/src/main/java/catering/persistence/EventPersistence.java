package catering.persistence;

import catering.businesslogic.event.Event;
import catering.businesslogic.event.EventReceiver;

public class EventPersistence implements EventReceiver {

    @Override
    public void updateEventCreated(Event event) {
        event.saveNewEvent();
    }

    @Override
    public void updateEventModified(Event event) {
        event.updateEvent();
    }

    @Override
    public void updateEventDeleted(Event event) {
        event.deleteEvent();
    }

    @Override
    public void updateEventCancelled(Event event) {
        event.updateEvent();
    }

    @Override
    public void updateEventClosed(Event event) {
        event.updateEvent();
    }
}
