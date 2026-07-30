package com.venomproxy.auth;

import com.venomproxy.db.Database;
import com.venomproxy.model.AuthAccount;
import com.venomproxy.model.RequestData;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuthenticationManager {
    private final Database database;
    private final CopyOnWriteArrayList<AuthAccount> accounts = new CopyOnWriteArrayList<>();

    public AuthenticationManager(Database database) {
        this.database = database;
        refresh();
    }

    public void refresh() {
        accounts.clear();
        accounts.addAll(database.listAuthAccounts());
    }

    public List<AuthAccount> accounts() {
        return List.copyOf(accounts);
    }

    public void save(AuthAccount account) {
        database.saveAuthAccount(account);
        refresh();
    }

    public void delete(long id) {
        database.deleteAuthAccount(id);
        refresh();
    }

    public void setActive(long id, boolean active) {
        database.setAuthAccountActive(id, active);
        refresh();
    }

    public RequestData apply(RequestData request) {
        for (AuthAccount account : accounts) {
            if (!account.isActive()) {
                continue;
            }
            if (account.matches(request.getUrl())) {
                if (!account.getBearerToken().isBlank()) {
                    request.getHeaders().put("Authorization", "Bearer " + account.getBearerToken());
                }
                if (!account.getCookieJar().isBlank()) {
                    request.getHeaders().put("Cookie", account.getCookieJar());
                }
                break;
            }
        }
        return request;
    }

    public long expiredCount() {
        return accounts.stream().filter(AuthAccount::isExpired).count();
    }
}
