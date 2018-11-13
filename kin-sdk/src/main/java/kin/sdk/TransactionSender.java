package kin.sdk;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.List;
import kin.sdk.Environment.KinAsset;
import kin.sdk.exception.AccountNotActivatedException;
import kin.sdk.exception.AccountNotFoundException;
import kin.sdk.exception.IllegalAmountException;
import kin.sdk.exception.InsufficientKinException;
import kin.sdk.exception.OperationFailedException;
import kin.sdk.exception.TransactionFailedException;
import kin.base.KeyPair;
import kin.base.Memo;
import kin.base.PaymentOperation;
import kin.base.Server;
import kin.base.Transaction.Builder;
import kin.base.responses.AccountResponse;
import kin.base.responses.HttpResponseException;
import kin.base.responses.SubmitTransactionResponse;

class TransactionSender {

    private static final int MEMO_BYTES_LENGTH_LIMIT = 21; //Memo length limitation(in bytes) is 28 but we add 7 more bytes which includes the appId and some characters.
    private static final int MAX_NUM_OF_DECIMAL_PLACES = 4 ;
    private static String APP_ID_VERSION_PREFIX = "1";
    private static final String INSUFFICIENT_KIN_RESULT_CODE = "op_underfunded";
    private final Server server; //horizon server
    private final KinAsset kinAsset;
    private final String appId;

    TransactionSender(Server server, KinAsset kinAsset, String appId) {
        this.server = server;
        this.kinAsset = kinAsset;
        this.appId = appId;
    }

    Transaction buildTransaction(@NonNull KeyPair from, @NonNull String publicAddress,
                                                 @NonNull BigDecimal amount) throws OperationFailedException {
        return buildTransaction(from, publicAddress, amount, null);
    }

    Transaction buildTransaction(@NonNull KeyPair from, @NonNull String publicAddress,
                                                 @NonNull BigDecimal amount, @Nullable String memo) throws OperationFailedException {
        checkParams(from, publicAddress, amount, memo);
        memo = addAppIdToMemo(memo);

        KeyPair addressee = generateAddresseeKeyPair(publicAddress);
        AccountResponse sourceAccount = loadSourceAccount(from);
        kin.base.Transaction stellarTransaction = buildStellarTransaction(from, amount, addressee, sourceAccount, memo);
        TransactionId id = new TransactionIdImpl(Utils.byteArrayToHex(stellarTransaction.hash()));
        return new Transaction(addressee, from, amount, memo, id, stellarTransaction);
    }

    TransactionId sendTransaction(Transaction transaction) throws OperationFailedException {
        verifyAddresseeAccount(generateAddresseeKeyPair(transaction.getDestination().getAccountId()));
        return sendTransaction(transaction.getStellarTransaction());
    }

    @NonNull
    private String addAppIdToMemo(@Nullable String memo) {
        if (memo == null) {
            memo = "";
        } else {
            memo = memo.trim(); // remove leading and trailing whitespaces.
        }
        StringBuilder sb = new StringBuilder();
        sb.append(APP_ID_VERSION_PREFIX)
          .append("-")
          .append(appId)
          .append("-")
          .append(memo);
        return sb.toString();
    }

    private void checkParams(@NonNull KeyPair from, @NonNull String publicAddress, @NonNull BigDecimal amount,
        @Nullable String memo) throws OperationFailedException {
        Utils.checkNotNull(from, "account");
        Utils.checkNotNull(amount, "amount");
        validateAmountDecimalPoint(amount);
        checkForNegativeAmount(amount);
        checkAddressNotEmpty(publicAddress);
        checkMemo(memo);
    }


    private void validateAmountDecimalPoint(BigDecimal amount) throws OperationFailedException {
        BigDecimal amountWithoutTrailingZeros = amount.stripTrailingZeros();
        int numOfDecimalPlaces = amountWithoutTrailingZeros.scale();
        if (numOfDecimalPlaces > MAX_NUM_OF_DECIMAL_PLACES) {
            throw new IllegalAmountException("amount can't have more then 4 digits after the decimal point");
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void checkAddressNotEmpty(@NonNull String publicAddress) {
        if (publicAddress == null || publicAddress.isEmpty()) {
            throw new IllegalArgumentException("Addressee not valid - public address can't be null or empty");
        }
    }

    private void checkForNegativeAmount(@NonNull BigDecimal amount) {
        if (amount.signum() == -1) {
            throw new IllegalArgumentException("Amount can't be negative");
        }
    }

    private void checkMemo(String memo) {
        try {
            if (memo != null && memo.getBytes("UTF-8").length > MEMO_BYTES_LENGTH_LIMIT) {
                throw new IllegalArgumentException("Memo cannot be longer that " + MEMO_BYTES_LENGTH_LIMIT + " bytes(UTF-8 characters)");
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Memo text have unsupported characters encoding");
        }
    }

    @NonNull
    private KeyPair generateAddresseeKeyPair(@NonNull String publicAddress) throws OperationFailedException {
        try {
            return KeyPair.fromAccountId(publicAddress);
        } catch (Exception e) {
            throw new OperationFailedException("Invalid addressee public address format", e);
        }
    }

    @NonNull
    private kin.base.Transaction buildStellarTransaction(@NonNull KeyPair from, @NonNull BigDecimal amount, KeyPair addressee,
                                                                AccountResponse sourceAccount, @Nullable String memo) {
        Builder transactionBuilder = new Builder(sourceAccount)
            .addOperation(
                new PaymentOperation.Builder(addressee, kinAsset.getStellarAsset(), amount.toString()).build());
        if (memo != null) {
            transactionBuilder.addMemo(Memo.text(memo));
        }
        kin.base.Transaction transaction = transactionBuilder.build();
        transaction.sign(from);
        return transaction;
    }

    private void verifyAddresseeAccount(KeyPair addressee) throws OperationFailedException {
        AccountResponse addresseeAccount;
        addresseeAccount = loadAccount(addressee);
        checkKinTrust(addresseeAccount);
    }

    private AccountResponse loadAccount(@NonNull KeyPair from) throws OperationFailedException {
        AccountResponse sourceAccount;
        try {
            sourceAccount = server.accounts().account(from);
        } catch (HttpResponseException httpError) {
            if (httpError.getStatusCode() == 404) {
                throw new AccountNotFoundException(from.getAccountId());
            } else {
                throw new OperationFailedException(httpError);
            }
        } catch (IOException e) {
            throw new OperationFailedException(e);
        }
        if (sourceAccount == null) {
            throw new OperationFailedException("can't retrieve data for account " + from.getAccountId());
        }
        return sourceAccount;
    }

    private void checkKinTrust(AccountResponse accountResponse) throws AccountNotActivatedException {
        if (!kinAsset.hasKinTrust(accountResponse)) {
            throw new AccountNotActivatedException(accountResponse.getKeypair().getAccountId());
        }
    }

    private AccountResponse loadSourceAccount(@NonNull KeyPair from) throws OperationFailedException {
        AccountResponse sourceAccount;
        sourceAccount = loadAccount(from);
        checkKinTrust(sourceAccount);
        return sourceAccount;
    }

    @NonNull
    private TransactionId sendTransaction(kin.base.Transaction transaction) throws OperationFailedException {
        try {
            SubmitTransactionResponse response = server.submitTransaction(transaction);
            if (response == null) {
                throw new OperationFailedException("can't get transaction response");
            }
            if (response.isSuccess()) {
                return new TransactionIdImpl(response.getHash());
            } else {
                return createFailureException(response);
            }
        } catch (IOException e) {
            throw new OperationFailedException(e);
        }
    }

    private TransactionId createFailureException(SubmitTransactionResponse response)
        throws TransactionFailedException, InsufficientKinException {
        TransactionFailedException transactionException = Utils.createTransactionException(response);
        if (isInsufficientKinException(transactionException)) {
            throw new InsufficientKinException();
        } else {
            throw transactionException;
        }
    }

    private boolean isInsufficientKinException(TransactionFailedException transactionException) {
        List<String> resultCodes = transactionException.getOperationsResultCodes();
        return resultCodes != null && resultCodes.size() > 0 && INSUFFICIENT_KIN_RESULT_CODE.equals(resultCodes.get(0));
    }
}
