/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.swssf.policy.secpolicy.model;

import org.apache.neethi.PolicyComponent;
import org.swssf.policy.OperationPolicy;
import org.swssf.policy.assertionStates.AssertionState;
import org.swssf.policy.secpolicy.SP12Constants;
import org.swssf.policy.secpolicy.SPConstants;
import org.swssf.securityEvent.SecurityEvent;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * class lent from apache rampart
 */
public class RequiredParts extends AbstractSecurityAssertion {

    private ArrayList headers = new ArrayList();

    public RequiredParts(SPConstants spConstants) {
        setVersion(spConstants);
    }

    /**
     * @return Returns the headers.
     */
    public ArrayList getHeaders() {
        return this.headers;
    }

    /**
     * @param headers The headers to set.
     */
    public void addHeader(Header header) {
        this.headers.add(header);
    }


    public QName getName() {
        return SP12Constants.REQUIRED_PARTS;
    }

    public PolicyComponent normalize() {
        return this;
    }

    public void serialize(XMLStreamWriter writer) throws XMLStreamException {
        String localName = getName().getLocalPart();
        String namespaceURI = getName().getNamespaceURI();

        String prefix = writer.getPrefix(namespaceURI);

        if (prefix == null) {
            prefix = getName().getPrefix();
            writer.setPrefix(prefix, namespaceURI);
        }

        // <sp:RequiredParts> 
        writer.writeStartElement(prefix, localName, namespaceURI);

        // xmlns:sp=".."
        writer.writeNamespace(prefix, namespaceURI);

        Header header;
        for (Iterator iterator = headers.iterator(); iterator.hasNext(); ) {
            header = (Header) iterator.next();
            // <sp:Header Name=".." Namespace=".." />
            writer.writeStartElement(prefix, SPConstants.HEADER, namespaceURI);
            // Name attribute is optional
            if (header.getName() != null) {
                writer.writeAttribute("Name", header.getName());
            }
            writer.writeAttribute("Namespace", header.getNamespace());

            writer.writeEndElement();
        }

        // </sp:RequiredParts>
        writer.writeEndElement();
    }

    @Override
    public SecurityEvent.Event[] getResponsibleAssertionEvents() {
        //todo
        return new SecurityEvent.Event[0];
    }

    @Override
    public void getAssertions(Map<SecurityEvent.Event, Collection<AssertionState>> assertionStateMap, OperationPolicy operationPolicy) {
        //todo
    }

    /*
    @Override
    public void assertPolicy(SecurityEvent securityEvent) throws PolicyViolationException {
    }

    @Override
    public boolean isAsserted() {
        return true;
    }
    */
}
