package kin.sdk;


import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import kin.base.KeyPair;
import kin.sdk.exception.CreateAccountException;
import kin.sdk.exception.CryptoException;

/**
 * Fake KeyStore for testing, implementing naive in memory store
 */
class FakeKeyStore implements KeyStore {

    private List<KeyPair> accounts;

    FakeKeyStore(List<KeyPair> preloadedAccounts) {
        accounts = new ArrayList<>(preloadedAccounts);
    }

    FakeKeyStore() {
        accounts = new ArrayList<>();
    }

    @Override
    public void deleteAccount(int index) {
        accounts.remove(index);
    }

    @Nonnull
    @Override
    public List<KeyPair> loadAccounts() {
        return accounts;
    }

    @Override
    public KeyPair newAccount() {
        KeyPair account = KeyPair.random();
        accounts.add(account);
        return account;
    }

    @Override
    public KeyPair importAccount(@Nonnull String json, @Nonnull String passphrase)
        throws CryptoException, CreateAccountException {
        return null;
    }

    @Override
    public void clearAllAccounts() {
        accounts.clear();
    }
}
