package catering.businesslogic.shift;

import catering.businesslogic.user.User;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ShiftManager {

    public ShiftManager() {
        Shift.loadAllShifts();
    }

    public ArrayList<Shift> getShiftTable() {
        return Shift.getShiftTable();
    }

    public boolean isAvailable(User u, Shift s) {
        return !s.isBooked(u);
    }

    public Shift createShift(Date date, Time startTime, Time endTime) {
        return Shift.createShift(date, startTime, endTime);
    }

    public Shift loadShiftById(int id) {
        return Shift.loadItemById(id);
    }

    public void updateShift(Shift shift) {
        shift.updateShift();
    }

    public void bookUserForShift(Shift shift, User user) {
        if (isAvailable(user, shift)) {
            shift.addBooking(user);
        }
    }

    public User removeUserFromShift(Shift shift, User user) {
        return shift.removeBookedUser(user);
    }

    public Set<User> getBookedUsers(Shift shift) {
        return shift.getBookedUsers();
    }

    public List<Shift> getShiftsForDate(Date date) {
        List<Shift> dateShifts = new ArrayList<>();
        for (Shift shift : getShiftTable()) {
            if (shift.getDate().equals(date)) {
                dateShifts.add(shift);
            }
        }
        return dateShifts;
    }
}
