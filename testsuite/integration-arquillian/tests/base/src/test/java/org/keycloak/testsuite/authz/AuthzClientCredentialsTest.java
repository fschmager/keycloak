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
package org.keycloak.testsuite.authz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.authentication.ClientCredentialsProviderUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.authentication.authenticators.client.JWTClientAuthenticator;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.ClientAuthenticator;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.resource.ProtectionResource;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.PermissionRequest;
import org.keycloak.representations.idm.authorization.PermissionResponse;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.testsuite.util.ClientBuilder;
import org.keycloak.testsuite.util.RealmBuilder;
import org.keycloak.testsuite.util.RolesBuilder;
import org.keycloak.testsuite.util.UserBuilder;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class AuthzClientCredentialsTest extends AbstractAuthzTest {

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        testRealms.add(configureRealm(RealmBuilder.create().name("authz-client-jwt-test"), ClientBuilder.create()
                .attribute(JWTClientAuthenticator.CERTIFICATE_ATTR, "MIICnTCCAYUCBgFPPLDaTzANBgkqhkiG9w0BAQsFADASMRAwDgYDVQQDDAdjbGllbnQxMB4XDTE1MDgxNzE3MjI0N1oXDTI1MDgxNzE3MjQyN1owEjEQMA4GA1UEAwwHY2xpZW50MTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAIUjjgv+V3s96O+Za9002Lp/trtGuHBeaeVL9dFKMKzO2MPqdRmHB4PqNlDdd28Rwf5Xn6iWdFpyUKOnI/yXDLhdcuFpR0sMNK/C9Lt+hSpPFLuzDqgtPgDotlMxiHIWDOZ7g9/gPYNXbNvjv8nSiyqoguoCQiiafW90bPHsiVLdP7ZIUwCcfi1qQm7FhxRJ1NiW5dvUkuCnnWEf0XR+Wzc5eC9EgB0taLFiPsSEIlWMm5xlahYyXkPdNOqZjiRnrTWm5Y4uk8ZcsD/KbPTf/7t7cQXipVaswgjdYi1kK2/zRwOhg1QwWFX/qmvdd+fLxV0R6VqRDhn7Qep2cxwMxLsCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAKE6OA46sf20bz8LZPoiNsqRwBUDkaMGXfnob7s/hJZIIwDEx0IAQ3uKsG7q9wb+aA6s+v7S340zb2k3IxuhFaHaZpAd4CyR5cn1FHylbzoZ7rI/3ASqHDqpljdJaFqPH+m7nZWtyDvtZf+gkZ8OjsndwsSBK1d/jMZPp29qYbl1+XfO7RCp/jDqro/R3saYFaIFiEZPeKn1hUJn6BO48vxH1xspSu9FmlvDOEAOz4AuM58z4zRMP49GcFdCWr1wkonJUHaSptJaQwmBwLFUkCbE5I1ixGMb7mjEud6Y5jhfzJiZMo2U8RfcjNbrN0diZl3jB6LQIwESnhYSghaTjNQ==")
                .authenticatorType(JWTClientAuthenticator.PROVIDER_ID))
                .build());
        testRealms.add(configureRealm(RealmBuilder.create().name("authz-test"), ClientBuilder.create().secret("secret")).build());
        testRealms.add(configureRealm(RealmBuilder.create().name("authz-test-session").accessTokenLifespan(1), ClientBuilder.create().secret("secret")).build());
    }

    @Before
    public void beforeAbstractKeycloakTest() throws Exception {
        super.beforeAbstractKeycloakTest();
        testContext.getTestRealmReps().forEach(realmRepresentation -> {
            Keycloak adminClient = getAdminClient();
            ClientsResource clients = adminClient.realm(realmRepresentation.getRealm()).clients();
            ClientRepresentation client = clients.findByClientId("resource-server-test").get(0);

            client.setAuthorizationServicesEnabled(false);

            clients.get(client.getId()).update(client);

            client.setAuthorizationServicesEnabled(true);

            clients.get(client.getId()).update(client);

            AuthorizationResource authorization = clients.get(client.getId()).authorization();
            ResourceServerRepresentation settings = authorization.getSettings();

            settings.setAllowRemoteResourceManagement(true);

            authorization.update(settings);
        });
    }

    @Test
    public void testSuccessfulJWTAuthentication() {
        assertAccessProtectionAPI(getAuthzClient("keycloak-with-jwt-authentication.json").protection());
    }

    @Test
    public void testSuccessfulAuthorizationRequest() throws Exception {
        AuthzClient authzClient = getAuthzClient("keycloak-with-jwt-authentication.json");
        ProtectionResource protection = authzClient.protection();
        PermissionRequest request = new PermissionRequest("Default Resource");
        PermissionResponse ticketResponse = protection.permission().create(request);
        String ticket = ticketResponse.getTicket();

        AuthorizationResponse authorizationResponse = authzClient.authorization("marta", "password").authorize(new AuthorizationRequest(ticket));
        String rpt = authorizationResponse.getToken();

        assertNotNull(rpt);

        AccessToken accessToken = new JWSInput(rpt).readJsonContent(AccessToken.class);

        AccessToken.Authorization authorization = accessToken.getAuthorization();

        assertNotNull(authorization);

        List<Permission> permissions = new ArrayList<>(authorization.getPermissions());

        assertFalse(permissions.isEmpty());
        assertEquals("Default Resource", permissions.get(0).getResourceName());
    }

    @Test
    public void failJWTAuthentication() {
        try {
            getAuthzClient("keycloak-with-invalid-keys-jwt-authentication.json").protection().resource().findAll();
            fail("Should fail due to invalid signature");
        } catch (Exception cause) {
            assertTrue(HttpResponseException.class.isInstance(cause.getCause().getCause()));
            assertEquals(400, HttpResponseException.class.cast(cause.getCause().getCause()).getStatusCode());
        }
    }

    @Test
    public void testSuccessfulClientSecret() {
        ProtectionResource protection = getAuthzClient("default-keycloak.json").protection();
        assertAccessProtectionAPI(protection);
    }

    @Test
    public void testReusingAccessAndRefreshTokens() throws Exception {
        ClientsResource clients = getAdminClient().realm("authz-test-session").clients();
        ClientRepresentation clientRepresentation = clients.findByClientId("resource-server-test").get(0);
        List<UserSessionRepresentation> userSessions = clients.get(clientRepresentation.getId()).getUserSessions(-1, -1);

        assertEquals(0, userSessions.size());

        AuthzClient authzClient = getAuthzClient("default-session-keycloak.json");
        ProtectionResource protection = authzClient.protection();

        protection.resource().findByName("Default Resource");
        userSessions = clients.get(clientRepresentation.getId()).getUserSessions(null, null);

        assertEquals(1, userSessions.size());

        Thread.sleep(2000);
        protection = authzClient.protection();
        protection.resource().findByName("Default Resource");

        userSessions = clients.get(clientRepresentation.getId()).getUserSessions(null, null);

        assertEquals(1, userSessions.size());
    }

    private RealmBuilder configureRealm(RealmBuilder builder, ClientBuilder clientBuilder) {
        return builder
                .roles(RolesBuilder.create().realmRole(new RoleRepresentation("uma_authorization", "", false)))
                .user(UserBuilder.create().username("marta").password("password").addRoles("uma_authorization"))
                .user(UserBuilder.create().username("kolo").password("password"))
                .client(clientBuilder.clientId("resource-server-test")
                        .authorizationServicesEnabled(true)
                        .redirectUris("http://localhost/resource-server-test")
                        .defaultRoles("uma_protection")
                        .directAccessGrants());
    }

    private void assertAccessProtectionAPI(ProtectionResource protection) {
        ResourceRepresentation expected = new ResourceRepresentation("Resource A", Collections.emptySet());

        String id = protection.resource().create(expected).getId();
        ResourceRepresentation actual = protection.resource().findById(id);

        assertNotNull(actual);
        assertEquals(expected.getName(), actual.getName());
        assertEquals(id, actual.getId());
    }

    private AuthzClient getAuthzClient(String adapterConfig) {
        KeycloakDeployment deployment = KeycloakDeploymentBuilder.build(getConfigurationStream(adapterConfig));

        return AuthzClient.create(new Configuration(deployment.getAuthServerBaseUrl(), deployment.getRealm(), deployment.getResourceName(), deployment.getResourceCredentials(), deployment.getClient()), new ClientAuthenticator() {
            @Override
            public void configureClientCredentials(Map<String, List<String>> requestParams, Map<String, String> requestHeaders) {
                Map<String, String> formparams = new HashMap<>();
                ClientCredentialsProviderUtils.setClientCredentials(deployment, requestHeaders, formparams);
                for (Entry<String, String> param : formparams.entrySet()) {
                    requestParams.put(param.getKey(), Arrays.asList(param.getValue()));
                }
            }
        });
    }

    private InputStream getConfigurationStream(String adapterConfig) {
        return getClass().getResourceAsStream("/authorization-test/" + adapterConfig);
    }
}
