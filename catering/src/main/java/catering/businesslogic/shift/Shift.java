package catering.businesslogic.shift;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Shift {

    private int id;
    private Date date;
    private Time startTime;
    private Time endTime;
    private Set<User> bookedUsers;

    private Shift() {
        bookedUsers = new HashSet<>();
    }

    public Shift(Date date, Time startTime, Time endTime) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        bookedUsers = new HashSet<>();
    }

    /**
     * Sets the ID of this shift.
     * Used when updating an existing shift.
     * 
     * @param id The ID to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Sets the end time for this shift
     * 
     * @param endTime The new end time
     */
    public void setEndTime(Time endTime) {
        this.endTime = endTime;
    }

    // STATIC METHODS FOR PERSISTENCE

    public static ArrayList<Shift> getShiftTable() {
        return loadAllShifts();
    }

    public static ArrayList<Shift> loadAllShifts() {
        String query = "SELECT * FROM Shifts";
        ArrayList<Shift> shiftArrayList = new ArrayList<>();


        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Shift s = new Shift();
                s.id = rs.getInt("id");

                // Use safe date/time handling for SQLite
                try {
                    String dateStr = rs.getString("date");
                    if (dateStr != null && !dateStr.isEmpty()) {
                        s.date = Date.valueOf(dateStr);
                    }

                    String startTimeStr = rs.getString("start_time");
                    if (startTimeStr != null && !startTimeStr.isEmpty()) {
                        s.startTime = Time.valueOf(startTimeStr);
                    }

                    String endTimeStr = rs.getString("end_time");
                    if (endTimeStr != null && !endTimeStr.isEmpty()) {
                        s.endTime = Time.valueOf(endTimeStr);
                    }
                } catch (IllegalArgumentException ex) {
                }

                s.bookedUsers = loadBookings(s);
                shiftArrayList.add(s);
            }
        });

        // Sort the shifts by date and time
        shiftArrayList.sort((a, b) -> {
            if (a.getDate().before(b.getDate()))
                return -1;
            else if (a.getDate().after(b.getDate()))
                return 1;
            else if (a.getStartTime().before(b.getStartTime()))
                return -1;
            else if (a.getStartTime().after(b.getStartTime()))
                return 1;
            else
                return 0;
        });

        return shiftArrayList;
    }

    public static Shift loadItemById(int id) {
        String query = "SELECT * FROM Shifts WHERE id = ?";
        Shift[] shiftHolder = new Shift[1]; // Use array to allow modification in lambda


        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Shift s = new Shift();
                s.id = rs.getInt("id");

                // Use safe date/time handling for SQLite
                try {
                    String dateStr = rs.getString("date");
                    if (dateStr != null && !dateStr.isEmpty()) {
                        s.date = Date.valueOf(dateStr);
                    }

                    String startTimeStr = rs.getString("start_time");
                    if (startTimeStr != null && !startTimeStr.isEmpty()) {
                        s.startTime = Time.valueOf(startTimeStr);
                    }

                    String endTimeStr = rs.getString("end_time");
                    if (endTimeStr != null && !endTimeStr.isEmpty()) {
                        s.endTime = Time.valueOf(endTimeStr);
                    }
                } catch (IllegalArgumentException ex) {
                }

                shiftHolder[0] = s;
            }
        }, id); // Pass id as parameter

        Shift s = shiftHolder[0];
        if (s != null && s.id == id) { // Check if we found the shift
            s.bookedUsers = loadBookings(s);
            return s;
        }

        return null; // Return null if shift not found
    }

    private static Set<User> loadBookings(Shift s) {
        Set<User> bookings = new HashSet<>();
        String query = "SELECT user_id FROM ShiftBookings WHERE shift_id = ?";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                int userId = rs.getInt("user_id");
                User user = User.load(userId);
                if (user != null) {
                    bookings.add(user);
                }
            }
        }, s.id);

        return bookings;
    }

    public static Shift createShift(Date date, Time startTime, Time endTime) {
        Shift s = new Shift();
        s.date = date;
        s.startTime = startTime;
        s.endTime = endTime;
        s.bookedUsers = new HashSet<>();

        String query = "INSERT INTO Shifts (date, start_time, end_time) VALUES (?, ?, ?)";

        PersistenceManager.executeUpdate(query,
                s.date,
                s.startTime,
                s.endTime);

        s.id = PersistenceManager.getLastId();

        return s;
    }

    // Save a new shift to the database
    public void saveShift() {
        if (this.id > 0) {
            updateShift(); // If id exists, update instead of insert
            return;
        }

        String query = "INSERT INTO Shifts (date, start_time, end_time) VALUES (?, ?, ?)";
        PersistenceManager.executeUpdate(query,
                date.toString(),
                startTime.toString(),
                endTime.toString());

        this.id = PersistenceManager.getLastId();
    }

    // Update an existing shift
    public void updateShift() {
        if (this.id <= 0) {
            saveShift(); // If no id, insert instead of update
            return;
        }

        String query = "UPDATE Shifts SET date = ?, start_time = ?, end_time = ? WHERE id = ?";
        PersistenceManager.executeUpdate(query,
                date.toString(),
                startTime.toString(),
                endTime.toString(),
                this.id);
    }

    // Save a booking to the database
    public void saveBooking(User user) {
        String query = "INSERT INTO ShiftBookings (shift_id, user_id) VALUES (?, ?)";
        PersistenceManager.executeUpdate(query, this.id, user.getId());

        bookedUsers.add(user);
    }

    public void removeBooking(User user) {
        String query = "DELETE FROM ShiftBookings WHERE shift_id = ? AND user_id = ?";
        PersistenceManager.executeUpdate(query, this.id, user.getId());

        bookedUsers.remove(user);
    }

    // INSTANCE METHODS

    public Date getDate() {
        return date;
    }

    public Time getStartTime() {
        return startTime;
    }

    public Time getEndTime() {
        return endTime;
    }

    public void addBooking(User u) {
        if (this.bookedUsers.contains(u)) {
            return;
        }

        String query = "INSERT INTO ShiftBookings (shift_id, user_id) VALUES (?, ?)";
        PersistenceManager.executeUpdate(query, this.id, u.getId());

        this.bookedUsers.add(u);
    }

    public User removeBookedUser(User u) {
        if (!this.bookedUsers.contains(u)) {
            return null;
        }

        String query = "DELETE FROM ShiftBookings WHERE shift_id = ? AND user_id = ?";
        int rowsAffected = PersistenceManager.executeUpdate(query, this.id, u.getId());

        if (rowsAffected > 0 && this.bookedUsers.remove(u)) {
            return u;
        }
        return null;
    }

    public boolean isBooked(User u) {
        return bookedUsers.contains(u);
    }

    public int getId() {
        return id;
    }

    public Set<User> getBookedUsers() {
        return new HashSet<>(bookedUsers);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(date)
                .append(" | <")
                .append(startTime)
                .append(" - ")
                .append(endTime)
                .append(">");

        if (!bookedUsers.isEmpty()) {
            for (User u : bookedUsers) {
                sb.append("\n\t - ").append(u.toString());
            }
        }

        return sb.toString();
    }
}
