package kin.sdk;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import kin.base.KeyPair;
import kin.base.Network;
import kin.base.Server;
import kin.sdk.exception.*;
import kin.utils.Request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static kin.sdk.Utils.checkNotNull;

/**
 * An account manager for a {@link KinAccount}.
 */
public class KinClient {

    private static final String STORE_NAME_PREFIX = "KinKeyStore_";
    private static final int TRANSACTIONS_TIMEOUT = 30;
    private final Environment environment;
    private final KeyStore keyStore;
    private final TransactionSender transactionSender;
    private final AccountInfoRetriever accountInfoRetriever;
    private final GeneralBlockchainInfoRetrieverImpl generalBlockchainInfoRetriever;
    private final BlockchainEventsCreator blockchainEventsCreator;
    private final BackupRestore backupRestore;
    private final String appId;
    private final String storeKey;
    @NonNull
    private List<KinAccountImpl> kinAccounts = new ArrayList<>(1);

    /**
     * For more details please look at {@link #KinClient(Context context,Environment environment, String appId, String storeKey)}
     */
    public KinClient(@NonNull Context context, @NonNull Environment environment, String appId) {
        this(context, environment, appId,"");
    }

    /**
     * Build KinClient object.
     * @param context android context
     * @param environment the blockchain network details.
     * @param appId a 4 character string which represent the application id which will be added to each transaction.
     *              <br><b>Note:</b> appId must contain only upper and/or lower case letters and/or digits and that the total string length is between 3 to 4.
     *              For example 1234 or 2ab3 or bcda, etc.</br>
     * @param storeKey an optional param which is the key for storing this KinClient data, different keys will store a different accounts.
     */
    public KinClient(@NonNull Context context, @NonNull Environment environment, @NonNull String appId, @NonNull String storeKey) {
        checkNotNull(storeKey, "storeKey");
        checkNotNull(context, "context");
        checkNotNull(environment, "environment");
        validateAppId(appId);
        this.environment = environment;
        this.backupRestore = new BackupRestoreImpl();
        Server server = initServer();
        this.appId = appId;
        this.storeKey = storeKey;
        keyStore = initKeyStore(context.getApplicationContext(), storeKey);
        transactionSender = new TransactionSender(server, appId);
        accountInfoRetriever = new AccountInfoRetriever(server);
        generalBlockchainInfoRetriever = new GeneralBlockchainInfoRetrieverImpl(server);
        blockchainEventsCreator = new BlockchainEventsCreator(server);
        loadAccounts();
    }

    @VisibleForTesting
    KinClient(Environment environment, KeyStore keyStore, TransactionSender transactionSender,
        AccountInfoRetriever accountInfoRetriever, GeneralBlockchainInfoRetrieverImpl generalBlockchainInfoRetriever,
        BlockchainEventsCreator blockchainEventsCreator, BackupRestore backupRestore, String appId, String storeKey) {
        this.environment = environment;
        this.keyStore = keyStore;
        this.transactionSender = transactionSender;
        this.accountInfoRetriever = accountInfoRetriever;
        this.generalBlockchainInfoRetriever = generalBlockchainInfoRetriever;
        this.blockchainEventsCreator = blockchainEventsCreator;
        this.backupRestore = backupRestore;
        this.appId = appId;
        this.storeKey = storeKey;
        loadAccounts();
    }

    private Server initServer() {
        Network.use(environment.getNetwork());
        return new Server(environment.getNetworkUrl(), TRANSACTIONS_TIMEOUT, TimeUnit.SECONDS);
    }

    private KeyStore initKeyStore(Context context, String id) {
        SharedPrefStore store = new SharedPrefStore(
            context.getSharedPreferences(STORE_NAME_PREFIX + id, Context.MODE_PRIVATE));
        return new KeyStoreImpl(store, backupRestore);
    }

    private void loadAccounts() {
        List<KeyPair> accounts = null;
        try {
            accounts = keyStore.loadAccounts();
        } catch (LoadAccountException e) {
            e.printStackTrace();
        }
        if (accounts != null && !accounts.isEmpty()) {
            updateKinAccounts(accounts);
        }
    }

    private void updateKinAccounts(List<KeyPair> storageAccounts) {
        Map<String, KinAccountImpl> accountsMap = new HashMap<>();
        for (KinAccountImpl kinAccountImpl : kinAccounts) {
            accountsMap.put(kinAccountImpl.getPublicAddress(), kinAccountImpl);
        }

        List<KinAccountImpl> newKinAccountsList = new ArrayList<>();
        for (KeyPair account : storageAccounts) {
            KinAccountImpl inMemoryKinAccount = accountsMap.get(account.getAccountId());
            if (inMemoryKinAccount != null) {
                newKinAccountsList.add(inMemoryKinAccount);
            } else {
                newKinAccountsList.add(createNewKinAccount(account));
            }
        }
        kinAccounts = newKinAccountsList;
    }

    private void validateAppId(String appId) {
        if (appId == null || appId.equals("")) {
            Log.w("KinClient","WARNING: KinClient instance was created without a proper application ID. Is this what you intended to do?");
        }
        else if (!appId.matches("[a-zA-Z0-9]{3,4}")) {
            throw new IllegalArgumentException("appId must contain only upper and/or lower case letters and/or digits and that the total string length is between 3 to 4.\n" +
                    "for example 1234 or 2ab3 or cd2 or fqa, etc.");
        }
    }

    /**
     * Creates and adds an account.
     * <p>Once created, the account information will be stored securely on the device and can
     * be accessed again via the {@link #getAccount(int)} method.</p>
     *
     * @return {@link KinAccount} the account created store the key.
     */
    public @NonNull
    KinAccount addAccount() throws CreateAccountException {
        KeyPair account = keyStore.newAccount();
        return addKeyPair(account);
    }

    /**
     * Import an account from a JSON-formatted string.
     *
     * @param exportedJson The exported JSON-formatted string.
     * @param passphrase The passphrase to decrypt the secret key.
     * @return The imported account
     */
    @NonNull
    public KinAccount importAccount(@NonNull String exportedJson, @NonNull String passphrase)
        throws CryptoException, CreateAccountException, CorruptedDataException {
        KeyPair account = keyStore.importAccount(exportedJson, passphrase);
        KinAccount kinAccount = getAccountByPublicAddress(account.getAccountId());
        return kinAccount != null ? kinAccount : addKeyPair(account);
    }

    @Nullable
    private KinAccount getAccountByPublicAddress(String accountId) { //TODO we should make this method public
        loadAccounts();
        KinAccount kinAccount = null;
        for (int i = 0; i < kinAccounts.size(); i++) {
            final KinAccount account = kinAccounts.get(i);
            if (accountId.equals(account.getPublicAddress())) {
                kinAccount = account;
            }
        }
        return kinAccount;
    }

    @NonNull
    private KinAccount addKeyPair(KeyPair account) {
        KinAccountImpl newAccount = createNewKinAccount(account);
        kinAccounts.add(newAccount);
        return newAccount;
    }

    /**
     * Returns an account at input index.
     *
     * @return the account at the input index or null if there is no such account
     */
    public KinAccount getAccount(int index) {
        loadAccounts();
        if (index >= 0 && kinAccounts.size() > index) {
            return kinAccounts.get(index);
        }
        return null;
    }

    /**
     * @return true if there is an existing account
     */
    public boolean hasAccount() {
        return getAccountCount() != 0;
    }

    /**
     * Returns the number of existing accounts
     */
    public int getAccountCount() {
        loadAccounts();
        return kinAccounts.size();
    }

    /**
     * Deletes the account at input index (if it exists)
     * @return true if the delete was successful or false otherwise
     * @throws DeleteAccountException in case of a delete account exception while trying to delete the account
     */
    public boolean deleteAccount(int index) throws DeleteAccountException {
        boolean deleteSuccess = false;
        if (index >= 0 && getAccountCount() > index) {
            String accountToDelete = kinAccounts.get(index).getPublicAddress();
            keyStore.deleteAccount(accountToDelete);
            KinAccountImpl removedAccount = kinAccounts.remove(index);
            removedAccount.markAsDeleted();
            deleteSuccess = true;
        }
        return deleteSuccess;
    }

    /**
     * Deletes all accounts.
     */
    public void clearAllAccounts() {
        keyStore.clearAllAccounts();
        for (KinAccountImpl kinAccount : kinAccounts) {
            kinAccount.markAsDeleted();
        }
        kinAccounts.clear();
    }

    public Environment getEnvironment() {
        return environment;
    }

    @NonNull
    private KinAccountImpl createNewKinAccount(KeyPair account) {
        return new KinAccountImpl(account, backupRestore, transactionSender,
                accountInfoRetriever, blockchainEventsCreator);
    }

    /**
     * Get the current minimum fee that the network charges per operation.
     * This value is expressed in stroops.
     *
     * @return {@code Request<Integer>} - the minimum fee.
     */
    public Request<Long> getMinimumFee() {
        return new Request<>(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return generalBlockchainInfoRetriever.getMinimumFeeSync();
            }
        });
    }

    /**
     * Get the current minimum fee that the network charges per operation.
     * This value is expressed in stroops.
     * <p><b>Note:</b> This method accesses the network, and should not be called on the android main thread.</p>
     *
     * @return the minimum fee.
     */
    public long getMinimumFeeSync() throws OperationFailedException {
        return generalBlockchainInfoRetriever.getMinimumFeeSync();
    }

    public String getAppId() {
        return appId;
    }

    public String getStoreKey() {
        return storeKey;
    }
}
