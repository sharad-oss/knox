/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.security.token.impl;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import java.util.Map;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JWTToken implements JWT {
  private static JWTProviderMessages log = MessagesFactory.get( JWTProviderMessages.class );

  SignedJWT jwt;

  private JWTToken(String header, String claims, String signature) throws ParseException {
    jwt = new SignedJWT(new Base64URL(header), new Base64URL(claims), new Base64URL(signature));
  }

  public JWTToken(String serializedJWT) throws ParseException {
    try {
      jwt = SignedJWT.parse(serializedJWT);
    } catch (ParseException e) {
      log.unableToParseToken(e);
      throw e;
    }
  }

  public JWTToken(String alg, String[] claimsArray) {
    this(alg, claimsArray, null);
  }

  public JWTToken(String alg, String[] claimsArray, List<String> audiences) {
    JWSHeader header = new JWSHeader(new JWSAlgorithm(alg));

    if (claimsArray[2] != null) {
      if (audiences == null) {
        audiences = new ArrayList<>();
      }
      audiences.add(claimsArray[2]);
    }
    JWTClaimsSet claims;
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
    .issuer(claimsArray[0])
    .subject(claimsArray[1])
    .audience(audiences);
    if(claimsArray[3] != null) {
      builder = builder.expirationTime(new Date(Long.parseLong(claimsArray[3])));
    }

    claims = builder.build();

    jwt = new SignedJWT(header, claims);
  }

  public JWTToken(String alg, String[] claimsArray, Map<String, String> customClaims,
      List<String> audiences) {
    JWSHeader header = new JWSHeader(new JWSAlgorithm(alg));

    if (claimsArray[2] != null) {
      if (audiences == null) {
        audiences = new ArrayList<String>();
      }
      audiences.add(claimsArray[2]);
    }
    JWTClaimsSet claims = null;
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
        .issuer(claimsArray[0])
        .subject(claimsArray[1])
        .audience(audiences);

    customClaims = (customClaims != null) ? customClaims : Collections.emptyMap();
    for (Map.Entry<String, String> entry : customClaims.entrySet()) {
      builder.claim(entry.getKey(), entry.getValue());
    }

    if(claimsArray[3] != null) {
      builder = builder.expirationTime(new Date(Long.parseLong(claimsArray[3])));
    }

    claims = builder.build();

    jwt = new SignedJWT(header, claims);
  }

  @Override
  public String getHeader() {
    JWSHeader header = jwt.getHeader();
    return header.toString();
  }

  @Override
  public String getClaims() {
    String c = null;
    JWTClaimsSet claims;
    try {
      claims = jwt.getJWTClaimsSet();
      c = claims.toJSONObject().toJSONString();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return c;
  }

  @Override
  public String getPayload() {
    Payload payload = jwt.getPayload();
    return payload.toString();
  }

  @Override
  public String toString() {
    return jwt.serialize();
  }

  @Override
  public void setSignaturePayload(byte[] payload) {
//    this.payload = payload;
  }

  @Override
  public byte[] getSignaturePayload() {
    byte[] b = null;
    Base64URL b64 = jwt.getSignature();
    if (b64 != null) {
      b = b64.decode();
    }
    return b;
  }

  public static JWTToken parseToken(String wireToken) throws ParseException {
    log.parsingToken(wireToken);
    String[] parts = wireToken.split("\\.");
    return new JWTToken(parts[0], parts[1], parts[2]);
  }

  @Override
  public String getClaim(String claimName) {
    String claim = null;

    try {
      claim = jwt.getJWTClaimsSet().getStringClaim(claimName);
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }

    return claim;
  }

  @Override
  public String getSubject() {
    return getClaim(JWT.SUBJECT);
  }

  @Override
  public String getIssuer() {
    return getClaim(JWT.ISSUER);
  }

  @Override
  public String getAudience() {
    String[] claim;
    String c = null;

    claim = getAudienceClaims();
    if (claim != null) {
      c = claim[0];
    }

    return c;
  }

  @Override
  public String[] getAudienceClaims() {
    String[] claims = null;

    try {
      claims = jwt.getJWTClaimsSet().getStringArrayClaim(JWT.AUDIENCE);
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }

    return claims;
  }

  @Override
  public String getExpires() {
    Date expires = getExpiresDate();
    if (expires != null) {
      return String.valueOf(expires.getTime());
    }
    return null;
  }

  @Override
  public Date getExpiresDate() {
    Date date = null;
    try {
      date = jwt.getJWTClaimsSet().getExpirationTime();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return date;
  }

  @Override
  public Date getNotBeforeDate() {
    Date date = null;
    try {
      date = jwt.getJWTClaimsSet().getNotBeforeTime();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return date;
  }

  @Override
  public String getPrincipal() {
    return getClaim(JWT.PRINCIPAL);
  }

  @Override
  public void sign(JWSSigner signer) {
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      log.unableToSignToken(e);
    }
  }

  @Override
  public boolean verify(JWSVerifier verifier) {
    boolean rc = false;

    try {
      rc = jwt.verify(verifier);
    } catch (JOSEException e) {
      log.unableToVerifyToken(e);
    }

    return rc;
  }
}
