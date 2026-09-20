package catering.businesslogic.user;

import catering.businesslogic.UseCaseLogicException;

public class UserManager {

    private User currentUser;

    public void fakeLogin(String username) throws UseCaseLogicException {
        User loaded = User.load(username);

        if (loaded == null || loaded.getId() == 0) {
            this.currentUser = null;
            throw new UseCaseLogicException("Utente non trovato: " + username);
        }
        this.currentUser = loaded;
    }

    public User getCurrentUser() {
        return this.currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
}
