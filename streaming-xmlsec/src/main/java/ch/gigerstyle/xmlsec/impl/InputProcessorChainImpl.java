package ch.gigerstyle.xmlsec.impl;

import ch.gigerstyle.xmlsec.ext.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: giger
 * Date: May 13, 2010
 * Time: 1:46:50 PM
 * Copyright 2010 Marc Giger gigerstyle@gmx.ch
 * <p/>
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
public class InputProcessorChainImpl implements InputProcessorChain {

    protected static final transient Log log = LogFactory.getLog(InputProcessorChainImpl.class);

    private List<InputProcessor> inputProcessors = Collections.synchronizedList(new ArrayList<InputProcessor>());
    private int startPos = 0;
    private int curPos = 0;

    private SecurityContext securityContext;
    private DocumentContextImpl documentContext;

    public InputProcessorChainImpl(SecurityContext securityContext) {
        this(securityContext, 0);
    }

    public InputProcessorChainImpl(SecurityContext securityContext, int startPos) {
        this(securityContext, new DocumentContextImpl(), startPos);
    }

    protected InputProcessorChainImpl(SecurityContext securityContext, DocumentContextImpl documentContextImpl, int startPos) {
        this.securityContext = securityContext;
        this.curPos = this.startPos = startPos;
        documentContext = documentContextImpl;
    }

    public int getCurPos() {
        return curPos;
    }

    public void setCurPos(int curPos) {
        this.curPos = curPos;
    }

    public int getPosAndIncrement() {
        return this.curPos++;
    }

    public void reset() {
        setCurPos(startPos);
    }

    public SecurityContext getSecurityContext() {
        return this.securityContext;
    }

    public DocumentContext getDocumentContext() {
        return this.documentContext;
    }

    private void setInputProcessors(List<InputProcessor> inputProcessors) {
        this.inputProcessors = inputProcessors;
    }

    public void addProcessor(InputProcessor newInputProcessor) {
        int startPhaseIdx = 0;
        int endPhaseIdx = inputProcessors.size();

        Constants.Phase targetPhase = newInputProcessor.getPhase();

        for (int i = inputProcessors.size() - 1; i >= 0; i--) {
            InputProcessor inputProcessor = inputProcessors.get(i);
            if (inputProcessor.getPhase().ordinal() < targetPhase.ordinal()) {
                startPhaseIdx = i + 1;
                break;
            }
        }
        for (int i = startPhaseIdx; i < inputProcessors.size(); i++) {
            InputProcessor inputProcessor = inputProcessors.get(i);
            if (inputProcessor.getPhase().ordinal() > targetPhase.ordinal()) {
                endPhaseIdx = i;
                break;
            }
        }

        //just look for the correct phase and append as last
        if (newInputProcessor.getBeforeProcessors().isEmpty()
                && newInputProcessor.getAfterProcessors().isEmpty()) {
            inputProcessors.add(endPhaseIdx, newInputProcessor);
        } else if (newInputProcessor.getBeforeProcessors().isEmpty()) {
            int idxToInsert = endPhaseIdx;

            for (int i = endPhaseIdx - 1; i >= startPhaseIdx; i--) {
                InputProcessor inputProcessor = inputProcessors.get(i);
                if (newInputProcessor.getAfterProcessors().contains(inputProcessor.getClass().getName())) {
                    idxToInsert = i + 1;
                    break;
                }
            }
            inputProcessors.add(idxToInsert, newInputProcessor);
        } else if (newInputProcessor.getAfterProcessors().isEmpty()) {
            int idxToInsert = startPhaseIdx;

            for (int i = startPhaseIdx; i < endPhaseIdx; i++) {
                InputProcessor inputProcessor = inputProcessors.get(i);
                if (newInputProcessor.getBeforeProcessors().contains(inputProcessor.getClass().getName())) {
                    idxToInsert = i;
                    break;
                }
            }
            inputProcessors.add(idxToInsert, newInputProcessor);
        } else {
            boolean found = false;
            int idxToInsert = endPhaseIdx;

            for (int i = startPhaseIdx; i < endPhaseIdx; i++) {
                InputProcessor inputProcessor = inputProcessors.get(i);
                if (newInputProcessor.getBeforeProcessors().contains(inputProcessor.getClass().getName())) {
                    idxToInsert = i;
                    found = true;
                    break;
                }
            }
            if (found) {
                inputProcessors.add(idxToInsert, newInputProcessor);
            } else {
                for (int i = endPhaseIdx - 1; i >= startPhaseIdx; i--) {
                    InputProcessor inputProcessor = inputProcessors.get(i);
                    if (newInputProcessor.getAfterProcessors().contains(inputProcessor.getClass().getName())) {
                        idxToInsert = i + 1;
                        break;
                    }
                }
                inputProcessors.add(idxToInsert, newInputProcessor);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Added " + newInputProcessor.getClass().getName() + " to input chain: ");
            for (int i = 0; i < inputProcessors.size(); i++) {
                InputProcessor inputProcessor = inputProcessors.get(i);
                log.debug("Name: " + inputProcessor.getClass().getName() + " phase: " + inputProcessor.getPhase());
            }
        }
    }

    public void removeProcessor(InputProcessor inputProcessor) {
        log.debug("Removing processor " + inputProcessor.getClass().getName() + " from input chain");
        if (this.inputProcessors.indexOf(inputProcessor) <= getCurPos()) {
            this.curPos--;
        }
        this.inputProcessors.remove(inputProcessor);
    }

    public void processSecurityHeaderEvent(XMLEvent xmlEvent) throws XMLStreamException, XMLSecurityException {
        boolean removeEndElement = false;
        if (startPos == curPos) {
            if (xmlEvent.isStartElement()) {
                documentContext.addPathElement(xmlEvent.asStartElement().getName());
            } else if (xmlEvent.isEndElement()) {
                removeEndElement = true;
            }
        }
        inputProcessors.get(getPosAndIncrement()).processNextSecurityHeaderEvent(xmlEvent, this);
        if (removeEndElement) {
            documentContext.removePathElement();
        }
    }

    public void processEvent(XMLEvent xmlEvent) throws XMLStreamException, XMLSecurityException {
        boolean removeEndElement = false;
        if (startPos == curPos) {
            if (xmlEvent.isStartElement()) {
                documentContext.addPathElement(xmlEvent.asStartElement().getName());
            } else if (xmlEvent.isEndElement()) {
                removeEndElement = true;
            }
        }
        inputProcessors.get(getPosAndIncrement()).processNextEvent(xmlEvent, this);
        if (removeEndElement) {
            documentContext.removePathElement();
        }
    }

    public void doFinal() throws XMLStreamException, XMLSecurityException {
        inputProcessors.get(getPosAndIncrement()).doFinal(this, securityContext);
    }

    public InputProcessorChain createSubChain(InputProcessor inputProcessor) throws XMLStreamException, XMLSecurityException {
        //we don't clone the processor-list to get updates in the sublist too!
        InputProcessorChainImpl inputProcessorChain = new InputProcessorChainImpl(securityContext, documentContext.clone(), inputProcessors.indexOf(inputProcessor) + 1);
        inputProcessorChain.setInputProcessors(this.inputProcessors);
        return inputProcessorChain;
    }
}
