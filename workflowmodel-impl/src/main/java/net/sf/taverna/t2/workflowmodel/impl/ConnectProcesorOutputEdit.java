/*******************************************************************************
 * Copyright (C) 2007 The University of Manchester   
 * 
 *  Modifications to the initial code base are copyright of their
 *  respective authors, or their employers as appropriate.
 * 
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *    
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *    
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 ******************************************************************************/
package net.sf.taverna.t2.workflowmodel.impl;

import net.sf.taverna.t2.workflowmodel.EditException;
import net.sf.taverna.t2.workflowmodel.EventHandlingInputPort;
import net.sf.taverna.t2.workflowmodel.Processor;

/**
 * Connect the named output port on a given processor to the specified
 * EventHandlingInputPort, updating the connected port list on the output port
 * of the processor such that events will be forwarded as appropriate. If the
 * target port is a FilteringInputPort then also set the filter level
 * appropriately.
 * 
 * @author Tom Oinn
 */
class ConnectProcesorOutputEdit extends AbstractProcessorEdit {
	private EventHandlingInputPort target;
	private String outputName;
	private DatalinkImpl newLink = null;

	public ConnectProcesorOutputEdit(Processor p, String outputName,
			EventHandlingInputPort targetPort) {
		super(p);
		this.target = targetPort;
		this.outputName = outputName;
	}

	@Override
	protected void doEditAction(ProcessorImpl processor) throws EditException {
		for (BasicEventForwardingOutputPort popi : processor.outputPorts)
			if (popi.getName().equals(outputName)) {
				addOutgoingLink(popi);
				return;
			}
		throw new EditException("Cannot locate output port with name '"
				+ outputName + "'");
	}

	private void addOutgoingLink(BasicEventForwardingOutputPort popi) {
		newLink = new DatalinkImpl(popi, target);
		popi.addOutgoingLink(newLink);
		if (target instanceof AbstractEventHandlingInputPort)
			((AbstractEventHandlingInputPort) target).setIncomingLink(newLink);
	}
}
