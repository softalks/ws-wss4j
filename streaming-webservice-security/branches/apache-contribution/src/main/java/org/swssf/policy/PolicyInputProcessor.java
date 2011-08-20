/**
 * Copyright 2010, 2011 Marc Giger
 *
 * This file is part of the streaming-webservice-security-framework (swssf).
 *
 * The streaming-webservice-security-framework is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The streaming-webservice-security-framework is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the streaming-webservice-security-framework.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.swssf.policy;

import org.swssf.ext.*;
import org.swssf.impl.processor.input.SecurityHeaderInputProcessor;
import org.swssf.securityEvent.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * Processor to generate SecurityEvents regarding not secured elements
 *
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class PolicyInputProcessor extends AbstractInputProcessor {

    private PolicyEnforcer policyEnforcer;
    private boolean firstHeaderCall = true;
    private boolean firstBodyCall = true;

    public PolicyInputProcessor(PolicyEnforcer policyEnforcer, SecurityProperties securityProperties) {
        super(securityProperties);
        this.setPhase(Constants.Phase.POSTPROCESSING);
        this.getBeforeProcessors().add(SecurityHeaderInputProcessor.class.getName());
        this.policyEnforcer = policyEnforcer;
    }

    @Override
    public XMLEvent processNextHeaderEvent(InputProcessorChain inputProcessorChain) throws XMLStreamException, WSSecurityException {
        if (firstHeaderCall) {
            firstHeaderCall = false;
            if (policyEnforcer.isTransportSecurityActive()) {
                inputProcessorChain.getDocumentContext().setIsInEncryptedContent();
                inputProcessorChain.getDocumentContext().setIsInSignedContent();
            }
        }
        XMLEvent xmlEvent = inputProcessorChain.processHeaderEvent();
        //test if non encrypted element have to be encrypted per policy
        if (!inputProcessorChain.getDocumentContext().isInEncryptedContent() && inputProcessorChain.getDocumentContext().isInSecurityHeader()) {
            testEncryptionPolicy(xmlEvent, inputProcessorChain);
        }
        if (xmlEvent.isStartElement() && inputProcessorChain.getDocumentContext().getDocumentLevel() == 3 && inputProcessorChain.getDocumentContext().isInSOAPHeader()) {
            RequiredPartSecurityEvent requiredPartSecurityEvent = new RequiredPartSecurityEvent(SecurityEvent.Event.RequiredPart);
            requiredPartSecurityEvent.setElement(xmlEvent.asStartElement().getName());
            policyEnforcer.registerSecurityEvent(requiredPartSecurityEvent);
            RequiredElementSecurityEvent requiredElementSecurityEvent = new RequiredElementSecurityEvent(SecurityEvent.Event.RequiredElement);
            requiredElementSecurityEvent.setElement(xmlEvent.asStartElement().getName());
            policyEnforcer.registerSecurityEvent(requiredElementSecurityEvent);
        }
        return xmlEvent;
    }

    @Override
    public XMLEvent processNextEvent(InputProcessorChain inputProcessorChain) throws XMLStreamException, WSSecurityException {
        if (firstBodyCall) {
            firstBodyCall = false;
            if (policyEnforcer.isTransportSecurityActive()) {
                inputProcessorChain.getDocumentContext().setIsInEncryptedContent();
                inputProcessorChain.getDocumentContext().setIsInSignedContent();
            }
        }
        XMLEvent xmlEvent = inputProcessorChain.processEvent();

        if (xmlEvent.isStartElement()) {
            if (inputProcessorChain.getDocumentContext().getDocumentLevel() == 3 && inputProcessorChain.getDocumentContext().isInSOAPBody()) {
                OperationSecurityEvent operationSecurityEvent = new OperationSecurityEvent(SecurityEvent.Event.Operation);
                operationSecurityEvent.setOperation(xmlEvent.asStartElement().getName());
                policyEnforcer.registerSecurityEvent(operationSecurityEvent);
            }
        } else if (inputProcessorChain.getDocumentContext().getDocumentLevel() == 0
                && xmlEvent.isEndElement()
                //ns mismatch should be detected by the xml parser so a local-name equality check should be enough
                && xmlEvent.asEndElement().getName().getLocalPart().equals(Constants.TAG_soap_Envelope_LocalName)) {
            policyEnforcer.doFinal();
        }

        //test if non encrypted element have to be encrypted per policy
        if (!inputProcessorChain.getDocumentContext().isInEncryptedContent() && !inputProcessorChain.getDocumentContext().isInSecurityHeader()) {
            testEncryptionPolicy(xmlEvent, inputProcessorChain);
        }

        //test if non signed element have to be signed per policy
        if (!inputProcessorChain.getDocumentContext().isInSignedContent()) {
            testSignaturePolicy(inputProcessorChain, xmlEvent);
        }
        return xmlEvent;
    }

    private void testSignaturePolicy(InputProcessorChain inputProcessorChain, XMLEvent xmlEvent) throws WSSecurityException {
        if (xmlEvent.isStartElement()) {

            if (inputProcessorChain.getDocumentContext().getDocumentLevel() == 3 && inputProcessorChain.getDocumentContext().isInSOAPHeader()) {
                SignedPartSecurityEvent signedPartSecurityEvent = new SignedPartSecurityEvent(SecurityEvent.Event.SignedPart, true);
                signedPartSecurityEvent.setElement(xmlEvent.asStartElement().getName());
                policyEnforcer.registerSecurityEvent(signedPartSecurityEvent);
            } else if (inputProcessorChain.getDocumentContext().getDocumentLevel() == 2 && inputProcessorChain.getDocumentContext().isInSOAPBody()) {
                SignedPartSecurityEvent signedPartSecurityEvent = new SignedPartSecurityEvent(SecurityEvent.Event.SignedPart, true);
                signedPartSecurityEvent.setElement(xmlEvent.asStartElement().getName());
                policyEnforcer.registerSecurityEvent(signedPartSecurityEvent);
            } else if (inputProcessorChain.getDocumentContext().getDocumentLevel() > 3) {
                SignedElementSecurityEvent signedElementSecurityEvent = new SignedElementSecurityEvent(SecurityEvent.Event.SignedElement, true);
                signedElementSecurityEvent.setElement(xmlEvent.asStartElement().getName());
                policyEnforcer.registerSecurityEvent(signedElementSecurityEvent);
            }
        }
    }

    private void testEncryptionPolicy(XMLEvent xmlEvent, InputProcessorChain inputProcessorChain) throws WSSecurityException {
        //the following events are only interesting for policy verification. So call directly the policyEnforcer for these
        if (xmlEvent.isStartElement()) {

            if (inputProcessorChain.getDocumentContext().getDocumentLevel() == 3 && inputProcessorChain.getDocumentContext().isInSOAPHeader()) {
                EncryptedPartSecurityEvent encryptedPartSecurityEvent = new EncryptedPartSecurityEvent(SecurityEvent.Event.EncryptedPart, true);
                encryptedPartSecurityEvent.setElement(xmlEvent.asStartElement().getName());
                policyEnforcer.registerSecurityEvent(encryptedPartSecurityEvent);
            } else if (inputProcessorChain.getDocumentContext().getDocumentLevel() == 3 && inputProcessorChain.getDocumentContext().isInSOAPBody()) {
                EncryptedPartSecurityEvent encryptedPartSecurityEvent = new EncryptedPartSecurityEvent(SecurityEvent.Event.EncryptedPart, true);
                encryptedPartSecurityEvent.setElement(inputProcessorChain.getDocumentContext().getParentElement(xmlEvent.getEventType()));
                policyEnforcer.registerSecurityEvent(encryptedPartSecurityEvent);
            } else if (inputProcessorChain.getDocumentContext().getDocumentLevel() > 3) {
                EncryptedElementSecurityEvent encryptedElementSecurityEvent = new EncryptedElementSecurityEvent(SecurityEvent.Event.EncryptedElement, true);
                encryptedElementSecurityEvent.setElement(xmlEvent.asStartElement().getName());
                policyEnforcer.registerSecurityEvent(encryptedElementSecurityEvent);

                //... or it could be a contentEncryption too...
                ContentEncryptedElementSecurityEvent contentEncryptedElementSecurityEvent = new ContentEncryptedElementSecurityEvent(SecurityEvent.Event.ContentEncrypted, true);
                contentEncryptedElementSecurityEvent.setElement(inputProcessorChain.getDocumentContext().getParentElement(xmlEvent.getEventType()));
                policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
            }

        } else if (xmlEvent.isCharacters() || xmlEvent.isEntityReference() || xmlEvent.isProcessingInstruction()) {
            //can only be a content encryption
            ContentEncryptedElementSecurityEvent contentEncryptedElementSecurityEvent = new ContentEncryptedElementSecurityEvent(SecurityEvent.Event.ContentEncrypted, true);
            contentEncryptedElementSecurityEvent.setElement(inputProcessorChain.getDocumentContext().getParentElement(xmlEvent.getEventType()));
            policyEnforcer.registerSecurityEvent(contentEncryptedElementSecurityEvent);
        }
    }
}
