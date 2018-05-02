/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.oauth2.token.impl;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.model.Client;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessTokenCriteria;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.common.utils.UUID;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Optional;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenServiceImpl implements TokenService {

    private int accessTokenValiditySeconds = 60 * 60 * 12; // default 12 hours.
    private int refreshTokenValiditySeconds = 60 * 60 * 24 * 30; // default 30 days.

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ClientService clientService;

    @Override
    public Maybe<AccessToken> get(String accessToken) {
        final Maybe<io.gravitee.am.repository.oauth2.model.AccessToken> result = accessTokenRepository.findByToken(accessToken).cache();
        return result
                .isEmpty()
                .flatMapMaybe(empty -> (empty) ? Maybe.empty() : result.map(this::convert));
    }

    @Override
    public Single<AccessToken> create(OAuth2Request oAuth2Request) {
        // TODO manage token enhancer
        return accessTokenRepository.findByCriteria(convert(oAuth2Request))
                .map(accessToken -> Optional.of(accessToken))
                .defaultIfEmpty(Optional.empty())
                .flatMapSingle(optionalAccessToken -> {
                    if (!optionalAccessToken.isPresent()) {
                        return createAccessToken(oAuth2Request);
                    } else {
                        io.gravitee.am.repository.oauth2.model.AccessToken accessToken = optionalAccessToken.get();
                        // check if the access token is expired
                        if (accessToken.getExpireAt().before(new Date())) {
                            // remove the refresh token of the access token
                            Completable deleteAccessTokenAction = accessTokenRepository.delete(accessToken.getToken());
                            if (accessToken.getRefreshToken() != null) {
                                deleteAccessTokenAction.andThen(refreshTokenRepository.delete(accessToken.getRefreshToken()));
                            }
                            // the access token (and its refresh token) have been removed
                            // re-new them
                            return deleteAccessTokenAction.andThen(createAccessToken(oAuth2Request));
                        } else {
                            // do we need to update something ?
                            return Single.just(accessToken);
                        }
                    }
                }).map(this::convert);
    }

    @Override
    public Single<AccessToken> refresh() {
        return null;
    }

    private Single<io.gravitee.am.repository.oauth2.model.AccessToken> createAccessToken(OAuth2Request oAuth2Request) {
        return clientService.findByClientId(oAuth2Request.getClientId())
                .flatMapSingle(client -> {
                    io.gravitee.am.repository.oauth2.model.AccessToken accessToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
                    accessToken.setId(UUID.random().toString());
                    accessToken.setToken(UUID.random().toString());
                    accessToken.setClientId(oAuth2Request.getClientId());
                    accessToken.setExpireAt(new Date(System.currentTimeMillis() + (getAccessTokenValiditySeconds(client) * 1000L)));
                    accessToken.setCreatedAt(new Date());
                    accessToken.setRefreshToken(null);
                    accessToken.setScopes(oAuth2Request.getScopes());
                    if (!oAuth2Request.isClientOnly()) {
                        accessToken.setSubject(oAuth2Request.getSubject());
                    }
                    return Single.just(oAuth2Request.isSupportRefreshToken())
                            .flatMap(supportRefreshToken -> {
                                if (supportRefreshToken) {
                                    return createRefreshToken(client)
                                            .flatMap(refreshToken -> {
                                                accessToken.setRefreshToken(refreshToken.getToken());
                                                return Single.just(accessToken);
                                            });
                                } else {
                                    return Single.just(accessToken);
                                }
                            })
                            // TODO token enhancer
                            .flatMap(accessTokenRepository::create);
                });
    }

    private Single<RefreshToken> createRefreshToken(Client client) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.random().toString());
        refreshToken.setToken(UUID.random().toString());
        refreshToken.setCreatedAt(new Date());
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + (getRefreshTokenValiditySeconds(client) * 1000L)));

        return refreshTokenRepository.create(refreshToken);
    }

    private Integer getAccessTokenValiditySeconds(Client client) {
        int validitySeconds = client.getAccessTokenValiditySeconds();
        return validitySeconds > 0 ? validitySeconds : accessTokenValiditySeconds;
    }

    private Integer getRefreshTokenValiditySeconds(Client client) {
        int validitySeconds = client.getRefreshTokenValiditySeconds();
        return validitySeconds > 0 ? validitySeconds : refreshTokenValiditySeconds;
    }

    private AccessToken convert(io.gravitee.am.repository.oauth2.model.AccessToken accessToken) {
        DefaultAccessToken token = new DefaultAccessToken(accessToken.getToken());
        if (accessToken.getScopes() != null && !accessToken.getScopes().isEmpty()) {
            token.setScope(String.join(" ", accessToken.getScopes()));
        }
        token.setExpiresIn(accessToken.getExpireAt() != null ? Long.valueOf((accessToken.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L).intValue() : 0);
        token.setRefreshToken(accessToken.getRefreshToken());

        return token;
    }

    // TODO : if we generate an access token with resource owner flow and then with the client credentials flow with the same client_id and scopes
    // the server returns the same token, append grant_type information ?
    private AccessTokenCriteria convert(OAuth2Request oAuth2Request) {
        AccessTokenCriteria.Builder builder = new AccessTokenCriteria.Builder();
        builder.clientId(oAuth2Request.getClientId());
        builder.scopes(oAuth2Request.getScopes());
        if (!oAuth2Request.isClientOnly()) {
            builder.subject(oAuth2Request.getSubject());
        }
        return builder.build();
    }
}