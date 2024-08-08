/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.social.dingding;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.*;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.social.dingding.models.TokenRequest;

public class DingDingIdentityProvider
        extends AbstractOAuth2IdentityProvider<DingDingProviderConfig>
        implements SocialIdentityProvider<DingDingProviderConfig> {

    public static final String AUTH_URL = "https://login.dingtalk.com/oauth2/auth";
    public static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    public static final String PROFILE_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    public static final String DEFAULT_SCOPE = "openid corpid";
    public static final String DEFAULT_RESPONSE_TYPE = "code";

    public static final String OAUTH2_PARAMETER_CLIENT_ID = "client_id";
    public static final String OAUTH2_PARAMETER_RESPONSE_TYPE = "response_type";
    public static final String OAUTH2_PARAMETER_PROMPT = "prompt";

    public static final String PROFILE_MOBILE = "mobile";

    private final String ACCESS_TOKEN_CACHE_KEY = "ding_talk_access_token";

    private static final DefaultCacheManager cacheManager = new DefaultCacheManager();
    private static final String DING_TALK_CACHE_NAME = "ding_talk";
    private static final ConcurrentMap<String, Cache<String, String>> caches =
            new ConcurrentHashMap<>();
    protected KeycloakSession session;

    private static Cache<String, String> createCache(String suffix) {
        try {
            String cacheName = DING_TALK_CACHE_NAME + ":" + suffix;

            ConfigurationBuilder config = new ConfigurationBuilder();
            cacheManager.defineConfiguration(cacheName, config.build());

            Cache<String, String> cache = cacheManager.getCache(cacheName);
            logger.info(cache);
            return cache;
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace(System.out);
            throw e;
        }
    }

    private Cache<String, String> getCache() {
        return caches.computeIfAbsent(
                getConfig().getClientId() + ":",
                DingDingIdentityProvider::createCache);
    }

    private String getAccessToken(String authCode) {
        logger.infov("Getting Access token by {0}", authCode);
        try {
            String token;
            JsonNode j = renewAccessToken(authCode);
            if (j == null) {
                j = renewAccessToken(authCode);
                if (j == null) {
                    throw new Exception("renew access token error");
                }
                logger.info("retry in renew access token " + j);
            }
            logger.infov("Access token is {0}", j.toString());
            token = getJsonProperty(j, "accessToken");
            logger.infov("Access token is {0}", token);
            logger.infov("expires_in is {0}", getJsonProperty(j, "expireIn"));
            return token;
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace(System.out);
        }
        return null;
    }

    private JsonNode renewAccessToken(String authCode) {
        TokenRequest tokenRequest = new TokenRequest(getConfig().getClientId(), getConfig().getClientSecret(), authCode, null, "authorization_code");

        try {
            return SimpleHttp.doPost(TOKEN_URL, session)
                    .json(tokenRequest)
                    .asJson();
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace(System.out);
        }
        return null;
    }

    private String resetAccessToken(String authCode) {
        getCache().remove(ACCESS_TOKEN_CACHE_KEY);
        return getAccessToken(authCode);
    }

    public DingDingIdentityProvider(KeycloakSession session, DingDingProviderConfig config) {
        super(session, config);
        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        this.session = session;
    }

    @Override
    public Object callback(RealmModel realm, AuthenticationCallback callback, EventBuilder event) {
        return new org.keycloak.social.dingding.Endpoint(this, callback, realm, event);
    }

    @Override
    protected boolean supportsExternalExchange() {
        return true;
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(
            EventBuilder event, JsonNode profile) {
        logger.info(profile.toString());

        String unionId = getJsonProperty(profile, "unionId").toLowerCase();
        String openId = getJsonProperty(profile, "openId").toLowerCase();
        String nick = getJsonProperty(profile, "nick").toLowerCase();
        String email = getJsonProperty(profile, "email");
        String mobile = getJsonProperty(profile, "mobile");
        String uname = nick;
        String firstName = nick;
        String lastName = nick;

        if (!nick.isEmpty()) {
            lastName = nick.substring(0, 1);
            firstName = nick.substring(1);
        }

        // convert nick from chinese to pinyin
        uname = PinyinConverter.convertToPinyin(nick);
        if (email == null) {
            email = uname + "@dcx.com";
        }

        logger.info("unionId: " + unionId);
        logger.info("openId: " + openId);
        logger.info("nick: " + nick);
        logger.info("email: " + email);
        logger.info("mobile: " + mobile);
        logger.info("uname: " + uname);
        logger.info("firstName: " + firstName);
        logger.info("lastName: " + lastName);

        BrokeredIdentityContext identity =
                new BrokeredIdentityContext(unionId);

        identity.setUsername(uname);
        identity.setBrokerUserId(unionId);
        identity.setModelUsername(uname);
        identity.setEmail(email);
        identity.setFirstName(firstName);
        identity.setLastName(lastName);

        // 手机号码，第三方仅通讯录应用可获取
        identity.setUserAttribute(PROFILE_MOBILE, mobile);

        identity.setIdpConfig(getConfig());
        identity.setIdp(this);
        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(
                identity, profile, getConfig().getAlias());
        return identity;
    }

    public BrokeredIdentityContext getFederatedIdentity(String authorizationCode) {
        logger.info("getting federated identity");

        String accessToken = getAccessToken(authorizationCode);
        if (accessToken == null) {
            throw new IdentityBrokerException("No access token available");
        }
        BrokeredIdentityContext context = null;
        try {
            JsonNode profile;
            profile =
                    SimpleHttp.doGet(PROFILE_URL, session)
                            .header("x-acs-dingtalk-access-token", accessToken)
                            .asJson();
            logger.info("profile in federation " + profile.toString());

            context = extractIdentityFromProfile(null, profile);
            context.getContextData().put(FEDERATED_ACCESS_TOKEN, accessToken);
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace(System.out);
        }
        return context;
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }

    @Override
    protected UriBuilder createAuthorizationUrl(AuthenticationRequest request) {
        logger.info("Creating Auth Url...");

        final UriBuilder uriBuilder;

        String ua = request.getSession().getContext().getRequestHeaders().getHeaderString("user-agent");

        logger.info("构建授权链接。user-agent=" + ua + ", request=" + request);

        logger.infov("构建授权链接参数列表：{0}={1}; {2}={3}; {4}={5}; {6}={7}; {8}={9}; {10}={11}", "auth Url", getConfig().getAuthorizationUrl(), OAUTH2_PARAMETER_CLIENT_ID, getConfig().getClientId(), OAUTH2_PARAMETER_REDIRECT_URI,
                request.getRedirectUri(), OAUTH2_PARAMETER_RESPONSE_TYPE, DEFAULT_RESPONSE_TYPE, OAUTH2_PARAMETER_SCOPE,
                getConfig().getDefaultScope(), OAUTH2_PARAMETER_STATE, request.getState().getEncoded());

        uriBuilder = UriBuilder.fromUri(getConfig().getAuthorizationUrl());
        uriBuilder
                .queryParam(OAUTH2_PARAMETER_CLIENT_ID, getConfig().getClientId())
                .queryParam(OAUTH2_PARAMETER_REDIRECT_URI, request.getRedirectUri())
                .queryParam(OAUTH2_PARAMETER_RESPONSE_TYPE, DEFAULT_RESPONSE_TYPE)
                .queryParam(OAUTH2_PARAMETER_SCOPE, getConfig().getDefaultScope())
                .queryParam(OAUTH2_PARAMETER_STATE, request.getState().getEncoded())
                .queryParam(OAUTH2_PARAMETER_PROMPT, "consent");//新版必填 弹出授权框


        logger.info("授权链接是：" + uriBuilder.build().toString());
        return uriBuilder;
    }

    @Override
    public void updateBrokeredUser(
            KeycloakSession session, RealmModel realm, UserModel user, BrokeredIdentityContext context) {
        user.setSingleAttribute(PROFILE_MOBILE, context.getUserAttribute(PROFILE_MOBILE));

        user.setUsername(context.getUsername());
        user.setFirstName(context.getFirstName());
        user.setLastName(context.getLastName());
        user.setEmail(context.getEmail());
    }
}
