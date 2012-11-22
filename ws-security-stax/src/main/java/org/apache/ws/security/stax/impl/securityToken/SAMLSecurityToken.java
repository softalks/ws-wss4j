/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ws.security.stax.impl.securityToken;

import org.apache.ws.security.common.crypto.Crypto;
import org.apache.ws.security.common.ext.WSSecurityException;
import org.apache.ws.security.stax.ext.WSSConstants;
import org.apache.ws.security.stax.ext.WSSecurityContext;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.stax.ext.SecurityToken;
import org.apache.xml.security.stax.ext.XMLSecurityConstants;
import org.apache.xml.security.stax.impl.securityToken.AbstractInboundSecurityToken;
import org.opensaml.common.SAMLVersion;

import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class SAMLSecurityToken extends AbstractInboundSecurityToken {

    private final SAMLVersion samlVersion;
    private SecurityToken subjectSecurityToken;
    private String issuer;
    private Crypto crypto;

    public SAMLSecurityToken(SAMLVersion samlVersion, SecurityToken subjectSecurityToken, String issuer,
                             WSSecurityContext wsSecurityContext, Crypto crypto,
                             String id, WSSConstants.KeyIdentifierType keyIdentifierType) {
        super(wsSecurityContext, id, keyIdentifierType);
        this.samlVersion = samlVersion;
        this.issuer = issuer;
        this.crypto = crypto;
        this.subjectSecurityToken = subjectSecurityToken;
    }

    public SAMLSecurityToken(SAMLVersion samlVersion, Key secretKey, PublicKey publicKey,
                             X509Certificate[] x509Certificates, String issuer,
                             WSSecurityContext wsSecurityContext, Crypto crypto,
                             String id, WSSConstants.KeyIdentifierType keyIdentifierType) {
        super(wsSecurityContext, id, keyIdentifierType);
        this.samlVersion = samlVersion;
        this.issuer = issuer;
        this.crypto = crypto;
        if (secretKey != null) {
            setSecretKey("", secretKey);
        }
        if (publicKey != null) {
            setPublicKey(publicKey);
        }
        if (x509Certificates != null) {
            setX509Certificates(x509Certificates);
        }
    }

    @Override
    public boolean isAsymmetric() throws XMLSecurityException {
        if (this.subjectSecurityToken != null && this.subjectSecurityToken.isAsymmetric()) {
            return true;
        }
        return super.isAsymmetric();
    }

    @Override
    protected Key getKey(String algorithmURI, XMLSecurityConstants.KeyUsage keyUsage, String correlationID) throws XMLSecurityException {
        if (this.subjectSecurityToken != null) {
            return subjectSecurityToken.getSecretKey(algorithmURI, keyUsage, correlationID);
        }
        return super.getKey(algorithmURI, keyUsage, correlationID);
    }

    @Override
    protected PublicKey getPubKey(String algorithmURI, XMLSecurityConstants.KeyUsage keyUsage, String correlationID) throws XMLSecurityException {
        if (this.subjectSecurityToken != null) {
            return subjectSecurityToken.getPublicKey(algorithmURI, keyUsage, correlationID);
        }
        return super.getPubKey(algorithmURI, keyUsage, correlationID);
    }

    @Override
    public PublicKey getPublicKey() throws XMLSecurityException {
        if (this.subjectSecurityToken != null) {
            return subjectSecurityToken.getPublicKey();
        }
        return super.getPublicKey();
    }

    @Override
    public X509Certificate[] getX509Certificates() throws XMLSecurityException {
        if (this.subjectSecurityToken != null) {
            return subjectSecurityToken.getX509Certificates();
        }
        return super.getX509Certificates();
    }

    public Crypto getCrypto() {
        return crypto;
    }

    @Override
    public void verify() throws XMLSecurityException {
        //todo verify public key if exists
        //todo revisit verify for every security token incl. public-key
        //todo should we call verify implicit when accessing the keys?
        try {
            X509Certificate[] x509Certificates = getX509Certificates();
            if (x509Certificates != null && x509Certificates.length > 0) {
                //todo I don't think the checkValidity is necessary because the CertPathChecker
                x509Certificates[0].checkValidity();
                //todo deprecated method:
                getCrypto().verifyTrust(x509Certificates);
            }
        } catch (CertificateExpiredException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION, e);
        } catch (CertificateNotYetValidException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION, e);
        }
    }

    @Override
    public XMLSecurityConstants.TokenType getTokenType() {
        if (samlVersion == SAMLVersion.VERSION_10) {
            return WSSConstants.Saml10Token;
        } else if (samlVersion == SAMLVersion.VERSION_11) {
            return WSSConstants.Saml11Token;
        }
        return WSSConstants.Saml20Token;
    }

    public SAMLVersion getSamlVersion() {
        return samlVersion;
    }

    public String getIssuer() {
        return issuer;
    }
}