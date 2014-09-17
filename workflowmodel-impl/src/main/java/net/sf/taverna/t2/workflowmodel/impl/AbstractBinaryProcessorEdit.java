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
import net.sf.taverna.t2.workflowmodel.OrderedPair;
import net.sf.taverna.t2.workflowmodel.Processor;

/**
 * Generalization over all operations acting on an ordered pair of ProcessorImpl
 * objects. These include most operations where a relationship is created,
 * modified or destroyed between two processors.
 * 
 * @author Tom Oinn
 * 
 */
public abstract class AbstractBinaryProcessorEdit extends
		EditSupport<OrderedPair<Processor>> {
	private OrderedPair<Processor> processors;

	public AbstractBinaryProcessorEdit(Processor a, Processor b) {
		this.processors = new OrderedPair<Processor>(a, b);
	}

	@Override
	public final OrderedPair<Processor> applyEdit() throws EditException {
		if (processors.getA() instanceof ProcessorImpl == false
				|| processors.getB() instanceof ProcessorImpl == false)
			throw new EditException(
					"Edit cannot be applied to a Processor which isn't an instance of ProcessorImpl");

		ProcessorImpl pia = (ProcessorImpl) processors.getA();
		ProcessorImpl pib = (ProcessorImpl) processors.getB();

		synchronized (processors) {
			doEditAction(pia, pib);
			return this.processors;
		}
	}

	@Override
	public final OrderedPair<Processor> getSubject() {
		return this.processors;
	}

	/**
	 * Do the actual edit here
	 * 
	 * @param processorA
	 *            The ProcessorImpl which is in some sense the source of the
	 *            relation between the two being asserted or operated on by this
	 *            edit
	 * @param processorB
	 *            The ProcessorImpl at the other end of the relation. *
	 * @throws EditException
	 */
	protected abstract void doEditAction(ProcessorImpl processorA,
			ProcessorImpl processorB) throws EditException;
}
