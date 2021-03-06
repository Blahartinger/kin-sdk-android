package kin.base.responses.effects;

import kin.base.Server;

/**
 * Represents trustline_removed effect response.
 * @see <a href="https://www.stellar.org/developers/horizon/reference/resources/effect.html" target="_blank">Effect documentation</a>
 * @see kin.base.requests.EffectsRequestBuilder
 * @see Server#effects()
 */
public class TrustlineRemovedEffectResponse extends TrustlineCUDResponse {
  TrustlineRemovedEffectResponse(String limit, String assetType, String assetCode, String assetIssuer) {
    super(limit, assetType, assetCode, assetIssuer);
  }
}
