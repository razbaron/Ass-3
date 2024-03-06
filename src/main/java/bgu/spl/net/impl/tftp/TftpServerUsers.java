package bgu.spl.net.impl.tftp;

import java.util.ArrayList;

public class TftpServerUsers {
    private ArrayList<String> loggedInUsers;

    public TftpServerUsers(){
        loggedInUsers = new ArrayList<>();
    }

    public boolean isUserLoggedIn(String name) {
        return loggedInUsers.contains(name);
    }

    public synchronized void logInUser(String name) {
        loggedInUsers.add(name);
    }

    public synchronized void logOutUser(String name) {
        loggedInUsers.remove(name);
    }

    public synchronized int loggedInUsersCount() {
        return loggedInUsers.size();
    }
}
