package kin.base.requests;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import kin.base.Asset;
import kin.base.AssetTypeCreditAlphaNum;
import kin.base.responses.TradeResponse;
import okhttp3.OkHttpClient;

/**
 * Builds requests connected to trades.
 */
public class TradesRequestBuilder extends RequestBuilder {

    public TradesRequestBuilder(OkHttpClient httpClient, URI serverURI) {
        super(httpClient, serverURI, "order_book/trades");
    }

    public TradesRequestBuilder buyingAsset(Asset asset) {
        uriBuilder.appendQueryParameter("buying_asset_type", asset.getType());
        if (asset instanceof AssetTypeCreditAlphaNum) {
            AssetTypeCreditAlphaNum creditAlphaNumAsset = (AssetTypeCreditAlphaNum) asset;
            uriBuilder.appendQueryParameter("buying_asset_code", creditAlphaNumAsset.getCode());
            uriBuilder.appendQueryParameter("buying_asset_issuer", creditAlphaNumAsset.getIssuer().getAccountId());
        }
        return this;
    }

    public TradesRequestBuilder sellingAsset(Asset asset) {
        uriBuilder.appendQueryParameter("selling_asset_type", asset.getType());
        if (asset instanceof AssetTypeCreditAlphaNum) {
            AssetTypeCreditAlphaNum creditAlphaNumAsset = (AssetTypeCreditAlphaNum) asset;
            uriBuilder.appendQueryParameter("selling_asset_code", creditAlphaNumAsset.getCode());
            uriBuilder.appendQueryParameter("selling_asset_issuer", creditAlphaNumAsset.getIssuer().getAccountId());
        }
        return this;
    }

    public static TradeResponse execute(OkHttpClient httpClient, URI uri) throws IOException, TooManyRequestsException {
        TypeToken type = new TypeToken<TradeResponse>() {};
        ResponseHandler<TradeResponse> responseHandler = new ResponseHandler<TradeResponse>(httpClient, type);
        return responseHandler.handleGetRequest(uri);
    }

    public TradeResponse execute() throws IOException, TooManyRequestsException {
        return this.execute(httpClient, this.buildUri());
    }
}
