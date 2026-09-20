package catering.businesslogic.kitchen;

import java.util.ArrayList;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.event.Event;
import catering.businesslogic.event.Service;
import catering.businesslogic.shift.Shift;
import catering.businesslogic.user.User;

public class KitchenTaskManager {

    private SummarySheet currentSummarySheet;
    private ArrayList<KitchenTaskEventReceiver> eventReceivers;

    public KitchenTaskManager() {
        eventReceivers = new ArrayList<>();
    }

    public void addEventReceiver(KitchenTaskEventReceiver rec) {
        this.eventReceivers.add(rec);
    }

    public void removeEventReceiver(KitchenTaskEventReceiver rec) {
        this.eventReceivers.remove(rec);
    }

    public SummarySheet generateSummarySheet(Event event, Service service) throws UseCaseLogicException {

        User user = CatERing.getInstance().getUserManager().getCurrentUser();

        if (!user.isChef())
            throw new UseCaseLogicException("User is not a chef");

        if (event == null)
            throw new UseCaseLogicException("Event not specified");

        if (service == null)
            throw new UseCaseLogicException("Service not specified");

        if (!event.containsService(service))
            throw new UseCaseLogicException("Event does not include service");

        if (!user.equals(event.getChef()))
            throw new UseCaseLogicException("User not assigned chef");

        if (service.getMenu() == null)
            throw new UseCaseLogicException("Service lacks menu");

        SummarySheet newSummarySheet = new SummarySheet(service, user);

        service.getMenu().getNeededKitchenProcesses()
                .forEach(kp -> newSummarySheet.addTask(new KitchenTask(kp)));

        this.setCurrentSummarySheet(newSummarySheet);
        this.notifySheetGenerated(newSummarySheet);

        return newSummarySheet;
    }

    public ArrayList<SummarySheet> loadAllSumSheets() {
        return SummarySheet.loadAllSumSheets();
    }

    public SummarySheet openSumSheet(SummarySheet ss) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (!user.isChef())
            throw new UseCaseLogicException();
        if (!ss.isOwner(user))
            throw new UseCaseLogicException("User: " + user.getUserName() + " is not owner of the SummarySheet");
        setCurrentSummarySheet(ss);
        return ss;
    }

    public void addKitchenTask(KitchenTask t) {
        KitchenTask added = currentSummarySheet.addTask(t);
        notifyTaskAdded(added);
    }

    public void moveTask(KitchenTask t, int pos) throws UseCaseLogicException {
        if (currentSummarySheet == null || currentSummarySheet.getTaskPosition(t) < 0)
            throw new UseCaseLogicException();
        if (pos < 0 || pos >= currentSummarySheet.getTaskListSize())
            throw new UseCaseLogicException();
        this.currentSummarySheet.moveTask(t, pos);

        this.notifyTaskListSorted();
    }

    public void addTaskInformation(KitchenTask task, int quantity, int portions, long minutes)
            throws UseCaseLogicException {
        if (currentSummarySheet == null)
            throw new UseCaseLogicException();
        if (currentSummarySheet.getTaskPosition(task) < 0)
            throw new UseCaseLogicException("Task not found in this SummarySheet");
        if (quantity < 0)
            throw new IllegalArgumentException("Quantity must be >= 0");
        if (portions < 0)
            throw new IllegalArgumentException("Portions must be >= 0");
        if (minutes < 0)
            throw new IllegalArgumentException("Minutes must be >= 0");

        KitchenTask t = currentSummarySheet.addTaskInformation(task, quantity, portions, minutes);

        notifyTaskChanged(t);
    }

    public Assignment assignTask(KitchenTask t, Shift s) throws UseCaseLogicException {
        return assignTask(t, s, null);
    }

    public Assignment assignTask(KitchenTask t, Shift s, User cook) throws UseCaseLogicException {
        if (currentSummarySheet == null) {
            throw new UseCaseLogicException("Cannot assign task because there is no active summary sheet.");
        }
        // A cook can only be assigned a kitchen task on a shift they are booked on.
        // ShiftManager.isAvailable returns true when the user is NOT booked (= free),
        // so we throw if the cook is "available" (free) rather than booked.
        if (cook != null && CatERing.getInstance().getShiftManager().isAvailable(cook, s)) {
            throw new UseCaseLogicException("Cook " + cook.getUserName() + " is not booked on the selected shift.");
        }
        Assignment a = currentSummarySheet.addAssignment(t, s, cook);
        this.notifyAssignmentAdded(a);

        return a;
    }

    public void modifyAssignment(Assignment ass) throws UseCaseLogicException {
        Shift shift = ass.getShift();
        modifyAssignment(ass, shift, null);
    }

    public void modifyAssignment(Assignment ass, User cook) throws UseCaseLogicException {
        Shift shift = ass.getShift();
        modifyAssignment(ass, shift, cook);
    }

    public void modifyAssignment(Assignment ass, Shift shift) throws UseCaseLogicException {
        modifyAssignment(ass, shift, null);
    }

    public void modifyAssignment(Assignment ass, Shift shift, User cook)
            throws UseCaseLogicException {
        if (currentSummarySheet == null)
            throw new UseCaseLogicException();
        // Same precondition as assignTask: if a cook is specified, they must be booked
        // on the shift. ShiftManager.isAvailable returns true when the user is free.
        if (cook != null && CatERing.getInstance().getShiftManager().isAvailable(cook, shift)) {
            throw new UseCaseLogicException(
                    "Cook " + cook.getUserName() + " is not booked on the selected shift.");
        }

        Assignment a = currentSummarySheet.modifyAssignment(ass, shift, cook);
        notifyAssignmentChanged(a);
    }

    /**
     * Gets the current summary sheet
     *
     * @return The current summary sheet
     */
    public SummarySheet getCurrentSummarySheet() {
        return currentSummarySheet;
    }

    public void setTaskReady(KitchenTask t) throws UseCaseLogicException {
        KitchenTask task = currentSummarySheet.setTaskReady(t);
        notifyTaskChanged(task);
    }

    public void deleteAssignment(Assignment a) throws UseCaseLogicException {
        Assignment ass = currentSummarySheet.deleteAssignment(a);
        notifyAssignmentDeleted(ass);
    }

    private void setCurrentSummarySheet(SummarySheet summarySheet) {
        currentSummarySheet = summarySheet;
    }

    private void notifyTaskChanged(KitchenTask task) {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateTaskChanged(task);
        }
    }

    private void notifyAssignmentDeleted(Assignment ass) {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateAssignmentDeleted(ass);
        }
    }

    private void notifyAssignmentChanged(Assignment a) {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateAssignmentChanged(a);
        }
    }

    /**
     * Notifies all event receivers about a new assignment
     * 
     * @param assignment The assignment that was added
     */
    private void notifyAssignmentAdded(Assignment assignment) {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateAssignmentAdded(currentSummarySheet, assignment);
        }
    }

    private void notifyTaskListSorted() {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateTaskListSorted(currentSummarySheet);
        }
    }

    private void notifyTaskAdded(KitchenTask added) {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateTaskAdded(currentSummarySheet, added);
        }
    }

    private void notifySheetGenerated(SummarySheet summarySheet) {
        for (KitchenTaskEventReceiver er : eventReceivers) {
            er.updateSheetGenerated(summarySheet);
        }
    }

}
