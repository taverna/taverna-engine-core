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
package net.sf.taverna.t2.reference.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import net.sf.taverna.raven.spi.InstanceRegistry;
import net.sf.taverna.raven.spi.InstanceRegistryListener;
import net.sf.taverna.t2.reference.ExternalReferenceBuilderSPI;
import net.sf.taverna.t2.reference.ExternalReferenceSPI;
import net.sf.taverna.t2.reference.ExternalReferenceTranslatorSPI;
import net.sf.taverna.t2.reference.ReferenceContext;
import net.sf.taverna.t2.reference.ReferenceSet;
import net.sf.taverna.t2.reference.ReferenceSetAugmentationException;
import net.sf.taverna.t2.reference.ReferenceSetAugmentor;
import net.sf.taverna.t2.reference.ReferenceSetAugmentorCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implementation of ReferenceSetAugmentor using Dijkstra's shortest path
 * algorithm over a type graph built from SPI instance registries of reference
 * builders and reference translators.
 * 
 * @author Tom Oinn
 * 
 */
public class ReferenceSetAugmentorImpl implements ReferenceSetAugmentor {

	private final Log log = LogFactory.getLog(ReferenceSetAugmentorImpl.class);

	// An instance registry of ExternalReferenceBuilderSPI instances used to
	// construct ExternalReferenceSPI instances from byte streams
	protected InstanceRegistry<ExternalReferenceBuilderSPI<?>> builders;

	// An instance registry of ExternalReferenceTranslatorSPI instances used to
	// construct ExternalReferenceSPI instances from existing
	// ExternalReferenceSPI instances.
	protected InstanceRegistry<ExternalReferenceTranslatorSPI<?, ?>> translators;

	private boolean cacheValid = false;

	// A private listener used to trigger re-compilation of the shortest paths
	// from each node to each other node in the known types set
	private InstanceRegistryListener registryListener = new InstanceRegistryListener() {
		/**
		 * Call the updateAdjacencyList on the enclosing type when any change
		 * occurs in the SPIs
		 */
		@SuppressWarnings("rawtypes")
		public void instanceRegistryUpdated(InstanceRegistry theRegistry) {
			cacheValid = false;
		}
	};

	private final Set<Class<ExternalReferenceSPI>> knownReferenceTypes = new HashSet<Class<ExternalReferenceSPI>>();
	@SuppressWarnings("rawtypes")
	private final Map<Class<ExternalReferenceSPI>, Set<ExternalReferenceTranslatorSPI>> adjacencySets = new HashMap<Class<ExternalReferenceSPI>, Set<ExternalReferenceTranslatorSPI>>();

	private final Map<Class<ExternalReferenceSPI>, ShortestPathSolver> solvers = new HashMap<Class<ExternalReferenceSPI>, ShortestPathSolver>();

	/**
	 * Default constructor to make life easier when using Spring. To be
	 * functional this implementation should be injected with InstanceRegistry
	 * implementations containing lists of known implementations of the
	 * ExternalReferenceBuilderSPI and ExternalReferenceTranslatorSPI
	 * interfaces.
	 */
	public ReferenceSetAugmentorImpl() {
		super();
	}

	/**
	 * Inject an instance registry containing all known implementations of
	 * ExternalReferenceBuilderSPI *
	 * 
	 * @throws IllegalStateException
	 *             if this has already been set, the instance registries should
	 *             only be set on bean construction.
	 */
	public synchronized void setBuilderRegistry(
			InstanceRegistry<ExternalReferenceBuilderSPI<?>> theRegistry) {
		if (this.builders == null) {
			this.builders = theRegistry;
			theRegistry.addRegistryListener(registryListener);
			List<ExternalReferenceBuilderSPI<?>> erb = theRegistry
					.getInstances();
			log.debug("* Builder registry injected :");
			int counter = 0;
			for (ExternalReferenceBuilderSPI<?> builder : erb) {
				log.debug("*   " + (++counter) + ") "
						+ builder.getClass().getSimpleName() + ", builds "
						+ builder.getReferenceType().getSimpleName());
			}
			cacheValid = false;
		} else {
			log.error("Builder registry already injected, invalid operation");
			throw new IllegalStateException(
					"Can't inject the external reference builder registry "
							+ "multiple times.");
		}
	}

	/**
	 * Inject an instance registry containing all known implementations of
	 * ExternalReferenceTranslatorSPI
	 * 
	 * @throws IllegalStateException
	 *             if this has already been set, the instance registries should
	 *             only be set on bean construction.
	 */
	public synchronized void setTranslatorRegistry(
			InstanceRegistry<ExternalReferenceTranslatorSPI<?, ?>> theRegistry) {
		if (this.translators == null) {
			this.translators = theRegistry;
			theRegistry.addRegistryListener(registryListener);
			List<ExternalReferenceTranslatorSPI<?, ?>> ert = theRegistry
					.getInstances();
			log.debug("* Translator registry injected :");
			int counter = 0;
			for (ExternalReferenceTranslatorSPI<?, ?> translator : ert) {
				log.debug("*   " + (++counter) + ") "
						+ translator.getClass().getSimpleName()
						+ ", translates "
						+ translator.getSourceReferenceType().getSimpleName()
						+ " to "
						+ translator.getTargetReferenceType().getSimpleName());
			}
			theRegistry.getInstances();
			cacheValid = false;
		} else {
			log
					.error("Translator registry already injected, invalid operation");
			throw new IllegalStateException(
					"Can't inject the translator registry multiple times.");
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected synchronized final void update() {
		if (builders == null || translators == null || cacheValid) {
			return;
		}
		log.debug("# Refreshing shortest path cache");
		knownReferenceTypes.clear();
		solvers.clear();
		adjacencySets.clear();
		for (ExternalReferenceBuilderSPI erb : builders) {
			knownReferenceTypes.add(erb.getReferenceType());
		}
		for (ExternalReferenceTranslatorSPI ert : translators) {
			knownReferenceTypes.add(ert.getSourceReferenceType());
			knownReferenceTypes.add(ert.getTargetReferenceType());
			getNeighbours(ert.getTargetReferenceType()).add(ert);
		}
		for (Class<ExternalReferenceSPI> type : knownReferenceTypes) {
			try {
				solvers.put(type, new ShortestPathSolver(type));
			} catch (Throwable t) {
				log.error(t);
				if (t instanceof RuntimeException) {
					throw (RuntimeException) t;
				}
			}
		}
		log.debug("# Path cache refresh done");
		cacheValid = true;
	}

	@SuppressWarnings("rawtypes")
	Set<ExternalReferenceTranslatorSPI> getNeighbours(
			Class<ExternalReferenceSPI> node) {
		Set<ExternalReferenceTranslatorSPI> adjacentTo = adjacencySets
				.get(node);
		if (adjacentTo != null) {
			return adjacentTo;
		} else {
			HashSet<ExternalReferenceTranslatorSPI> neighbours = new HashSet<ExternalReferenceTranslatorSPI>();
			adjacencySets.put(node, neighbours);
			return neighbours;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public final Set<ExternalReferenceSPI> augmentReferenceSet(
			ReferenceSet references,
			Set<Class<ExternalReferenceSPI>> targetReferenceTypes,
			ReferenceContext context) throws ReferenceSetAugmentationException {

		synchronized (this) {
			if (!cacheValid) {
				update();
			}
		}

		// Synchronize on the reference set itself
		synchronized (references) {
			// First check whether we actually need to modify the reference set
			// at
			// all - it's perfectly valid to call the augmentor when nothing
			// actually needs to be done (ideally you wouldn't do this, but it's
			// likely to happen)
			for (ExternalReferenceSPI er : references.getExternalReferences()) {
				if (targetReferenceTypes.contains(er.getClass())) {
					return new HashSet<ExternalReferenceSPI>();
				}
			}

			// Need to perform augmentation if we reach this point
			List<TranslationPath> candidatePaths = new ArrayList<TranslationPath>();
			for (Class<ExternalReferenceSPI> target : targetReferenceTypes) {
				ShortestPathSolver solver = solvers.get(target);
				if (solver == null) {
					solver = new ShortestPathSolver(target);
					solvers.put(target, solver);
				}
				if (solver != null) {
					for (TranslationPath path : solver.getTranslationPaths()) {
						for (ExternalReferenceSPI er : references
								.getExternalReferences()) {
							if (er.getClass().equals(path.getSourceType())) {
								candidatePaths.add(path);
							}
						}
						for (TranslationPath dereferenceBasedPath : path
								.getDereferenceBasedPaths(references)) {
							candidatePaths.add(dereferenceBasedPath);
						}
					}
				}
			}
			// Now add candidate paths to represent a no-translator 'direct from
			// byte stream source' path for each target type compatible
			// reference builder
			for (ExternalReferenceBuilderSPI builder : builders) {
				if (targetReferenceTypes.contains(builder.getReferenceType())) {
					// The builder can construct one of the target types, add
					// paths for all possible pairs of 'de-reference existing
					// reference' and the builder
					for (ExternalReferenceSPI er : references
							.getExternalReferences()) {
						TranslationPath newPath = new TranslationPath();
						newPath.setBuilders(builders);
						newPath.setInitialBuilder(builder);
						newPath.setSourceReference(er);
						candidatePaths.add(newPath);
					}
				}
			}

			// Got a list of candidate paths sorted by estimated overall path
			// cost
			Collections.sort(candidatePaths);
			log
					.debug("Found "
							+ candidatePaths.size()
							+ " contextual translation path(s) including builder based :");
			int counter = 0;
			for (TranslationPath path : candidatePaths) {
				log.debug("  " + (++counter) + ") " + path.toString());
			}
			
			if (candidatePaths.isEmpty()) {
				log.warn("No candidate paths found for augmentation");
				throw new ReferenceSetAugmentationException(
						"No candidate translation paths were found");
			} else {
				log.debug("Performing augmentation :");
				counter = 0;
				for (TranslationPath path : candidatePaths) {
					try {
						counter++;
						Set<ExternalReferenceSPI> newReferences = path
								.doTranslation(references, context);
						log.debug("  Success ("+counter+"), created "+printRefSet(newReferences));
						return newReferences;
					} catch (Exception ex) {
						log.debug("  Failed ("+counter+")");
						log.trace(ex);
						// Use next path...
					}
				}
				log.warn("  No paths succeeded, augmentation failed");
				throw new ReferenceSetAugmentationException(
						"All paths threw exceptions, can't perform augmentation");
			}

		}
	}

	private String printRefSet(Set<ExternalReferenceSPI> set) {
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		int counter = 0;
		for (ExternalReferenceSPI ref : set) {
			sb.append(ref.toString());
			counter++;
			if (counter < set.size()) {
				sb.append(",");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void augmentReferenceSetAsynch(final ReferenceSet references,
			final Set<Class<ExternalReferenceSPI>> targetReferenceTypes,
			final ReferenceContext context,
			final ReferenceSetAugmentorCallback callback)
			throws ReferenceSetAugmentationException {
		Runnable r = new Runnable() {
			public void run() {
				try {
					callback.augmentationCompleted(augmentReferenceSet(
							references, targetReferenceTypes, context));

				} catch (ReferenceSetAugmentationException rsae) {
					callback.augmentationFailed(rsae);
				}
			}
		};
		executeRunnable(r);
	}

	/**
	 * Schedule a runnable for execution - current naive implementation uses a
	 * new thread and executes immediately, but this is where any thread pool
	 * logic would go if we wanted to add that.
	 * 
	 * @param r
	 */
	private void executeRunnable(Runnable r) {
		new Thread(r).start();
	}

	class ShortestPathSolver {

		private Map<Class<ExternalReferenceSPI>, Class<ExternalReferenceSPI>> predecessors;
		private Map<Class<ExternalReferenceSPI>, ExternalReferenceTranslatorSPI<?, ?>> translators;
		private Map<Class<ExternalReferenceSPI>, Float> shortestDistances;
		private final Comparator<Class<ExternalReferenceSPI>> shortestDistanceComparator = new Comparator<Class<ExternalReferenceSPI>>() {
			public int compare(Class<ExternalReferenceSPI> left,
					Class<ExternalReferenceSPI> right) {
				float shortestDistanceLeft = shortestDistances.get(left);
				float shortestDistanceRight = shortestDistances.get(right);
				if (shortestDistanceLeft > shortestDistanceRight) {
					return +1;
				} else if (shortestDistanceLeft < shortestDistanceRight) {
					return -1;
				} else {
					return left.getCanonicalName().compareTo(
							right.getCanonicalName());
				}
			}
		};
		private final PriorityQueue<Class<ExternalReferenceSPI>> unsettledNodes = new PriorityQueue<Class<ExternalReferenceSPI>>(
				10, shortestDistanceComparator);
		private final Set<Class<ExternalReferenceSPI>> settledNodes = new HashSet<Class<ExternalReferenceSPI>>();

		private final List<TranslationPath> translationPaths = new ArrayList<TranslationPath>();

		public List<TranslationPath> getTranslationPaths() {
			return this.translationPaths;
		}

		public ShortestPathSolver(Class<ExternalReferenceSPI> targetType) {
			log.debug("# Constructing shortest paths to '"
					+ targetType.getSimpleName() + "'");
			predecessors = new HashMap<Class<ExternalReferenceSPI>, Class<ExternalReferenceSPI>>();
			translators = new HashMap<Class<ExternalReferenceSPI>, ExternalReferenceTranslatorSPI<?, ?>>();
			shortestDistances = new HashMap<Class<ExternalReferenceSPI>, Float>();
			setShortestDistance(targetType, 0.0f);
			unsettledNodes.add(targetType);
			while (! unsettledNodes.isEmpty()) {
				Class<ExternalReferenceSPI> u = extractMin();
				settledNodes.add(u);
				relaxNeighbours(u);
			}
			for (Class<ExternalReferenceSPI> c : settledNodes) {
				if (! c.equals(targetType)) {
					// Don't calculate a path to itself!
					TranslationPath p = new TranslationPath();
					p.setBuilders(builders);
					Class<ExternalReferenceSPI> node = c;
					while (predecessors.get(node) != null) {
						p.pathSteps().add(translators.get(node));
						// Recurse, should terminate at the target type
						node = predecessors.get(node);
					}
					translationPaths.add(p);
				}
			}
			Collections.sort(translationPaths);
			if (translationPaths.isEmpty()) {
				log.debug("#   no paths discovered, type not reachable through translation");
			} else {
				log.debug("#   found " + translationPaths.size()
						+ " distinct path(s) :");
				int counter = 0;
				for (TranslationPath path : translationPaths) {
					log.debug("#     " + (++counter) + ") " + path.toString());
				}
			}
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private void relaxNeighbours(Class<ExternalReferenceSPI> u) {
			log.trace("#     relaxing node " + u.getSimpleName());
			Set<Class<ExternalReferenceSPI>> alreadySeen = new HashSet<Class<ExternalReferenceSPI>>();
			for (ExternalReferenceTranslatorSPI ert : getNeighbours(u)) {
				// all the translators that translate *to* u
				Class<ExternalReferenceSPI> v = ert.getSourceReferenceType();
				log.trace("#     translator found from from '" + v + "' : "
						+ ert.getClass().getSimpleName());
				if (! alreadySeen.contains(v) && ! isSettled(v)) {
					// Avoid duplicate edges, always take the first one where
					// such duplicates exist
					alreadySeen.add(v);
					if (getShortestDistance(v) > getShortestDistance(u)
							+ ert.getTranslationCost()) {
						setShortestDistance(v, getShortestDistance(u)
								+ ert.getTranslationCost());
						setPredecessor(v, u, ert);
						unsettledNodes.add(v);
					}
				}
			}
		}

		private boolean isSettled(Class<ExternalReferenceSPI> node) {
			return settledNodes.contains(node);
		}

		private void setShortestDistance(Class<ExternalReferenceSPI> node,
				float distance) {
			shortestDistances.put(node, distance);
		}

		private float getShortestDistance(Class<ExternalReferenceSPI> node) {
			Float d = shortestDistances.get(node);
			return (d == null) ? Float.MAX_VALUE : d;
		}

		private Class<ExternalReferenceSPI> extractMin() {
			return unsettledNodes.poll();
		}

		private void setPredecessor(Class<ExternalReferenceSPI> child,
				Class<ExternalReferenceSPI> parent,
				ExternalReferenceTranslatorSPI<?, ?> translator) {
			predecessors.put(child, parent);
			translators.put(child, translator);
		}

	}

}
