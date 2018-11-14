package kin.sdk;

import android.support.annotation.NonNull;
import java.io.IOException;
import kin.sdk.Environment.KinAsset;
import kin.sdk.exception.AccountNotFoundException;
import kin.sdk.exception.OperationFailedException;
import kin.base.ChangeTrustOperation;
import kin.base.KeyPair;
import kin.base.Server;
import kin.base.Transaction;
import kin.base.responses.AccountResponse;
import kin.base.responses.HttpResponseException;
import kin.base.responses.SubmitTransactionResponse;

class AccountActivator {

    //unlimited trust, The largest amount unit possible in Stellar
    //see https://www.stellar.org/developers/guides/concepts/assets.html
    private static final String TRUST_NO_LIMIT_VALUE = "92233720368547.75807";
    private final Server server; //horizon server
    private final KinAsset kinAsset;

    AccountActivator(Server server, KinAsset kinAsset) {
        this.server = server;
        this.kinAsset = kinAsset;
    }

    void activate(@NonNull KeyPair account) throws OperationFailedException {
        verifyParams(account);
        AccountResponse accountResponse;
        try {
            accountResponse = getAccountDetails(account);
            if (kinAsset.hasKinTrust(accountResponse)) {
                return;
            }
            SubmitTransactionResponse response = sendAllowKinTrustOperation(account, accountResponse);
            handleTransactionResponse(response);
        } catch (HttpResponseException httpError) {
            if (httpError.getStatusCode() == 404) {
                throw new AccountNotFoundException(account.getAccountId());
            } else {
                throw new OperationFailedException(httpError);
            }
        } catch (IOException e) {
            throw new OperationFailedException(e);
        }
    }

    private void verifyParams(@NonNull KeyPair account) {
        Utils.checkNotNull(account, "account");
    }

    @NonNull
    private AccountResponse getAccountDetails(@NonNull KeyPair account) throws IOException, OperationFailedException {
        AccountResponse accountResponse;
        accountResponse = server.accounts().account(account);
        if (accountResponse == null) {
            throw new OperationFailedException("can't retrieve data for account " + account.getAccountId());
        }
        return accountResponse;
    }

    private SubmitTransactionResponse sendAllowKinTrustOperation(KeyPair account, AccountResponse accountResponse)
        throws IOException {
        Transaction allowKinTrustTransaction = new Transaction.Builder(accountResponse).addFee(100).addOperation(
            new ChangeTrustOperation.Builder(kinAsset.getStellarAsset(), TRUST_NO_LIMIT_VALUE)
                .build()
        )
            .build();
        allowKinTrustTransaction.sign(account);
        return server.submitTransaction(allowKinTrustTransaction);
    }

    private void handleTransactionResponse(SubmitTransactionResponse response) throws OperationFailedException {
        if (response == null) {
            throw new OperationFailedException("can't get transaction response");
        }
        if (!response.isSuccess()) {
            throw Utils.createTransactionException(response);
        }
    }
}
