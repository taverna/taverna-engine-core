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
package net.sf.taverna.t2.reference;

/**
 * Thrown when the reference set augmentor is unable to provide at least one of
 * the desired types for any reason.
 * 
 * @author Tom Oinn
 * 
 */
public class ReferenceSetAugmentationException extends RuntimeException {

	private static final long serialVersionUID = -6156508424485682266L;

	public ReferenceSetAugmentationException() {
		//
	}

	public ReferenceSetAugmentationException(String message) {
		super(message);
	}

	public ReferenceSetAugmentationException(Throwable cause) {
		super(cause);
	}

	public ReferenceSetAugmentationException(String message, Throwable cause) {
		super(message, cause);
	}

}
