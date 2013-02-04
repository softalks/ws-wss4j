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
package org.apache.ws.security.stax.validate;

import org.apache.commons.codec.binary.Base64;
import org.apache.ws.security.binding.wss10.AttributedString;
import org.apache.ws.security.binding.wss10.EncodedString;
import org.apache.ws.security.binding.wss10.PasswordString;
import org.apache.ws.security.binding.wss10.UsernameTokenType;
import org.apache.ws.security.binding.wsu10.AttributedDateTime;
import org.apache.ws.security.common.ext.WSPasswordCallback;
import org.apache.ws.security.common.ext.WSSecurityException;
import org.apache.ws.security.stax.ext.WSSConstants;
import org.apache.ws.security.stax.ext.WSSUtils;
import org.apache.ws.security.stax.impl.securityToken.UsernameSecurityToken;
import org.apache.xml.security.stax.ext.XMLSecurityUtils;
import org.apache.xml.security.stax.impl.securityToken.AbstractInboundSecurityToken;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class UsernameTokenValidatorImpl implements UsernameTokenValidator {

    @Override
    public AbstractInboundSecurityToken validate(UsernameTokenType usernameTokenType, TokenContext tokenContext) throws WSSecurityException {

        // If the UsernameToken is to be used for key derivation, the (1.1)
        // spec says that it cannot contain a password, and it must contain
        // an Iteration element
        final byte[] salt = XMLSecurityUtils.getQNameType(usernameTokenType.getAny(), WSSConstants.TAG_wsse11_Salt);
        PasswordString passwordType = XMLSecurityUtils.getQNameType(usernameTokenType.getAny(), WSSConstants.TAG_wsse_Password);
        final Long iteration = XMLSecurityUtils.getQNameType(usernameTokenType.getAny(), WSSConstants.TAG_wsse11_Iteration);
        if (salt != null && (passwordType != null || iteration == null)) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "badTokenType01");
        }

        boolean handleCustomPasswordTypes = tokenContext.getWssSecurityProperties().getHandleCustomPasswordTypes();

        final byte[] nonceVal;
        final String created;

        WSSConstants.UsernameTokenPasswordType usernameTokenPasswordType = WSSConstants.UsernameTokenPasswordType.PASSWORD_NONE;
        if (passwordType != null && passwordType.getType() != null) {
            usernameTokenPasswordType = WSSConstants.UsernameTokenPasswordType.getUsernameTokenPasswordType(passwordType.getType());
        }

        final AttributedString username = usernameTokenType.getUsername();
        if (username == null) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "badTokenType01");
        }

        final EncodedString encodedNonce =
                XMLSecurityUtils.getQNameType(usernameTokenType.getAny(), WSSConstants.TAG_wsse_Nonce);

        final AttributedDateTime attributedDateTimeCreated =
                XMLSecurityUtils.getQNameType(usernameTokenType.getAny(), WSSConstants.TAG_wsu_Created);

        // TODO revisit this once we add in Validators
        if (usernameTokenPasswordType == WSSConstants.UsernameTokenPasswordType.PASSWORD_DIGEST) {
            if (encodedNonce == null || attributedDateTimeCreated == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "badTokenType01");
            }

            if (!WSSConstants.SOAPMESSAGE_NS10_BASE64_ENCODING.equals(encodedNonce.getEncodingType())) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.UNSUPPORTED_SECURITY_TOKEN, "badTokenType01");
            }

            nonceVal = Base64.decodeBase64(encodedNonce.getValue());
            created = attributedDateTimeCreated.getValue();

            XMLGregorianCalendar xmlGregorianCalendar;
            try {
                xmlGregorianCalendar = attributedDateTimeCreated.getAsXMLGregorianCalendar();
            } catch (IllegalArgumentException e) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }
            GregorianCalendar createdCal = xmlGregorianCalendar.toGregorianCalendar();
            GregorianCalendar now = new GregorianCalendar();
            if (createdCal.after(now)) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }
            now.add(Calendar.MINUTE, 5);
            if (createdCal.after(now)) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }

            WSPasswordCallback pwCb = new WSPasswordCallback(username.getValue(),
                    null,
                    passwordType.getType(),
                    WSPasswordCallback.Usage.USERNAME_TOKEN);
            try {
                WSSUtils.doPasswordCallback(tokenContext.getWssSecurityProperties().getCallbackHandler(), pwCb);
            } catch (WSSecurityException e) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION, e);
            }

            if (pwCb.getPassword() == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }

            String passDigest = WSSUtils.doPasswordDigest(nonceVal, created, pwCb.getPassword());
            if (!passwordType.getValue().equals(passDigest)) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }
            passwordType.setValue(pwCb.getPassword());
        } else if ((usernameTokenPasswordType == WSSConstants.UsernameTokenPasswordType.PASSWORD_TEXT)
                || (passwordType != null && passwordType.getValue() != null
                && usernameTokenPasswordType == WSSConstants.UsernameTokenPasswordType.PASSWORD_NONE)) {
            nonceVal = null;
            created = null;
            WSPasswordCallback pwCb = new WSPasswordCallback(username.getValue(),
                    null,
                    passwordType.getType(),
                    WSPasswordCallback.Usage.USERNAME_TOKEN);
            try {
                WSSUtils.doPasswordCallback(tokenContext.getWssSecurityProperties().getCallbackHandler(), pwCb);
            } catch (WSSecurityException e) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION, e);
            }

            if (pwCb.getPassword() == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }

            if (!passwordType.getValue().equals(pwCb.getPassword())) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }
            passwordType.setValue(pwCb.getPassword());
        } else if (passwordType != null && passwordType.getValue() != null && usernameTokenPasswordType == null) {
            if (!handleCustomPasswordTypes) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }
            nonceVal = null;
            created = null;
            WSPasswordCallback pwCb = new WSPasswordCallback(username.getValue(),
                    null,
                    passwordType.getType(),
                    WSPasswordCallback.Usage.USERNAME_TOKEN);
            try {
                WSSUtils.doPasswordCallback(tokenContext.getWssSecurityProperties().getCallbackHandler(), pwCb);
            } catch (WSSecurityException e) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION, e);
            }

            if (pwCb.getPassword() == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }

            if (!passwordType.getValue().equals(pwCb.getPassword())) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
            }
            passwordType.setValue(pwCb.getPassword());
        } else {
            nonceVal = null;
            created = null;
        }

        final String password;
        if (passwordType != null) {
            password = passwordType.getValue();
        } else {
            password = null;
        }

        UsernameSecurityToken usernameSecurityToken = new UsernameSecurityToken(
                username.getValue(), password, created, nonceVal, salt, iteration,
                tokenContext.getWsSecurityContext(), usernameTokenType.getId(),
                WSSConstants.WSSKeyIdentifierType.SECURITY_TOKEN_DIRECT_REFERENCE);
        usernameSecurityToken.setElementPath(tokenContext.getElementPath());
        usernameSecurityToken.setXMLSecEvent(tokenContext.getFirstXMLSecEvent());

        return usernameSecurityToken;
    }
}