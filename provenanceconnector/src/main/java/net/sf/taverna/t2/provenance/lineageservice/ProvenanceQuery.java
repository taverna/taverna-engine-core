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
package net.sf.taverna.t2.provenance.lineageservice;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.sf.taverna.t2.invocation.InvocationContext;
import net.sf.taverna.t2.provenance.connector.JDBCConnector;
import net.sf.taverna.t2.provenance.lineageservice.utils.Collection;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.CollectionTable;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataBindingTable;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable;
import net.sf.taverna.t2.provenance.lineageservice.utils.DDRecord;
import net.sf.taverna.t2.provenance.lineageservice.utils.DataLink;
import net.sf.taverna.t2.provenance.lineageservice.utils.DataflowInvocation;
import net.sf.taverna.t2.provenance.lineageservice.utils.NestedListNode;
import net.sf.taverna.t2.provenance.lineageservice.utils.Port;
import net.sf.taverna.t2.provenance.lineageservice.utils.PortBinding;
import net.sf.taverna.t2.provenance.lineageservice.utils.ProcessorEnactment;
import net.sf.taverna.t2.provenance.lineageservice.utils.ProvenanceProcessor;
import net.sf.taverna.t2.provenance.lineageservice.utils.Workflow;
import net.sf.taverna.t2.provenance.lineageservice.utils.WorkflowTree;
import net.sf.taverna.t2.reference.ReferenceService;
import net.sf.taverna.t2.reference.T2Reference;
import net.sf.taverna.t2.reference.impl.T2ReferenceImpl;
import net.sf.taverna.t2.provenance.lineageservice.utils.WorkflowRun;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;

/**
 * Handles all the querying of provenance items in the database layer. Uses
 * standard SQL so all specific instances of this class can extend this writer
 * to handle all of the db queries
 * 
 * @author Paolo Missier
 * @author Ian Dunlop
 * @author Stuart Owen
 * 
 */
public abstract class ProvenanceQuery {

	protected Logger logger = Logger.getLogger(ProvenanceQuery.class);
	public Connection getConnection() throws InstantiationException,
	IllegalAccessException, ClassNotFoundException, SQLException {
		return JDBCConnector.getConnection();
	}

	/**
	 * implements a set of query constraints of the form var = value into a
	 * WHERE clause
	 *
	 * @param q0
	 * @param queryConstraints
	 * @return
	 */
	protected String addWhereClauseToQuery(String q0,
			Map<String, String> queryConstraints, boolean terminate) {

		// complete query according to constraints
		StringBuffer q = new StringBuffer(q0);

		boolean first = true;
		if (queryConstraints != null && queryConstraints.size() > 0) {
			q.append(" where ");

			for (Entry<String, String> entry : queryConstraints.entrySet()) {
				if (!first) {
					q.append(" and ");
				}
				q.append(" " + entry.getKey() + " = \'" + entry.getValue() + "\' ");
				first = false;
			}
		}

		return q.toString();
	}

	protected String addOrderByToQuery(String q0, List<String> orderAttr,
			boolean terminate) {

		// complete query according to constraints
		StringBuffer q = new StringBuffer(q0);

		boolean first = true;
		if (orderAttr != null && orderAttr.size() > 0) {
			q.append(" ORDER BY ");

			int i = 1;
			for (String attr : orderAttr) {
				q.append(attr);
				if (i++ < orderAttr.size()) {
					q.append(",");
				}
			}
		}

		return q.toString();
	}




	/**
	 * pass-through query method
	 * @param q valid JDBC query string for the T2provenance schema
	 * @return the executed Statement if successull, null otherwise
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public Statement execQuery(String q) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {

		Statement stmt = null;
		Connection connection = null;
		connection = getConnection();
		stmt = connection.createStatement();
		boolean success = stmt.execute(q);
		if (success) return stmt;
		return null;
	}

	/**
	 * select Port records that satisfy constraints
	 */
	public List<Port> getPorts(Map<String, String> queryConstraints)
	throws SQLException {
		List<Port> result = new ArrayList<Port>();

		String q0 = "SELECT DISTINCT V.* FROM Port V JOIN WorkflowRun W ON W.workflowId = V.workflowId";

		String q = null;
		q= addWhereClauseToQuery(q0, queryConstraints, true);

		List<String> orderAttr = new ArrayList<String>();
		orderAttr.add("V.iterationStrategyOrder");

		String qOrder = addOrderByToQuery(q, orderAttr, true);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(qOrder.toString());

			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {
					Port aPort = new Port();

					aPort.setWorkflowId(rs.getString("workflowId"));
                    aPort.setInputPort(rs.getBoolean("isInputPort"));
					aPort.setIdentifier(rs.getString("portId"));
                    aPort.setProcessorName(rs.getString("processorName"));
                    aPort.setProcessorId(rs.getString("processorId"));
                    aPort.setPortName(rs.getString("portName"));
                    aPort.setDepth(rs.getInt("depth"));
                    if (rs.getString("resolvedDepth") != null) {
						aPort.setResolvedDepth(rs.getInt("resolvedDepth"));
					}
					result.add(aPort);
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}


		return result;
	}


	/**
	 * return the input variables for a given processor and a workflowRunId
	 *
	 * @param pname
	 * @param workflowRunId
	 * @return list of input variables
	 * @throws SQLException
	 */
	public List<Port> getInputPorts(String pname, String wfID)
	throws SQLException {
		// get (var, proc) from Port to see if it's input/output
		Map<String, String> varQueryConstraints = new HashMap<String, String>();

		varQueryConstraints.put("V.workflowId", wfID);
		varQueryConstraints.put("V.processorName", pname);
		varQueryConstraints.put("V.isInputPort", "1");
		return getPorts(varQueryConstraints);
	}

	/**
	 * return the output variables for a given processor and a workflowRunId
	 *
	 * @param pname
	 * @param workflowRunId
	 * @return list of output variables
	 * @throws SQLException
	 */
	public List<Port> getOutputPorts(String pname, String wfID)
	throws SQLException {
		// get (var, proc) from Port to see if it's input/output
		Map<String, String> varQueryConstraints = new HashMap<String, String>();

		varQueryConstraints.put("V.workflowId", wfID);
		varQueryConstraints.put("V.processorName", pname);
		varQueryConstraints.put("V.isInputPort", "0");
		return getPorts(varQueryConstraints);
	}

	/**
	 * selects all Datalinks
	 *
	 * @param queryConstraints
	 * @return
	 * @throws SQLException
	 */
	public List<DataLink> getDataLinks(Map<String, String> queryConstraints)
	throws SQLException {
		List<DataLink> result = new ArrayList<DataLink>();

		String q0 = "SELECT A.* FROM Datalink A";

		String q = addWhereClauseToQuery(q0, queryConstraints, true);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q.toString());

			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {

					DataLink aDataLink = new DataLink();

					aDataLink.setWorkflowId(rs.getString("workflowId"));
					aDataLink.setSourceProcessorName(rs.getString("sourceProcessorName"));
					aDataLink.setSourcePortName(rs.getString("sourcePortName"));
					aDataLink.setDestinationProcessorName(rs.getString("destinationProcessorName"));
					aDataLink.setDestinationPortName(rs.getString("destinationPortName"));
					aDataLink.setSourcePortId(rs.getString("sourcePortId"));
					aDataLink.setDestinationPortId(rs.getString("destinationPortId"));
					result.add(aDataLink);

				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return result;
	}


	public String getTopLevelWorkflowIdForRun(String runID) throws SQLException {

		List<Workflow> workflows = getWorkflowsForRun(runID);

		for (Workflow w:workflows) { 	
			if (w.getParentWorkflowId() == null) { return w.getWorkflowId(); }
		}		
		return null;		
	}

	/**
	 * returns the names of all workflows (top level + nested) for a given runID
	 * @param runID
	 * @return
	 * @throws SQLException
	 */
	public List<String> getWorkflowIdsForRun(String runID) throws SQLException {

		List<String> workflowIds = new ArrayList<String>();

		List<Workflow> workflows = getWorkflowsForRun(runID);

		for (Workflow w:workflows) { workflowIds.add(w.getWorkflowId()); }

		return workflowIds;
	}


	/**
	 * returns the workflows associated to a single runID
	 * @param runID
	 * @return
	 * @throws SQLException
	 */
	public List<Workflow> getWorkflowsForRun(String runID) throws SQLException {

		List<Workflow> result = new ArrayList<Workflow>();

		String q = "SELECT DISTINCT W.* FROM WorkflowRun I JOIN Workflow W ON I.workflowId = W.workflowId WHERE workflowRunId = ?";

		PreparedStatement stmt = null;
		Connection connection = null;

		try {
			connection = getConnection();
			stmt = connection.prepareStatement(q);
			stmt.setString(1, runID);

			boolean success = stmt.execute();

			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {

					Workflow w = new Workflow();
					w.setWorkflowId(rs.getString("workflowId"));
					w.setParentWorkflowId(rs.getString("parentWorkflowId"));

					result.add(w);					
				}
			}
		} catch (InstantiationException e) {
			logger.error("Error finding the workflow reference", e);
		} catch (IllegalAccessException e) {
			logger.error("Error finding the workflow reference", e);
		} catch (ClassNotFoundException e) {
			logger.error("Error finding the workflow reference", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return result;
	}



	public String getLatestRunID() throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;

		String q = "SELECT workflowRunId FROM WorkflowRun ORDER BY timestamp DESC";

		try {
			connection = getConnection();
			ps = connection.prepareStatement(q);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				if (rs.next()) {
					return rs.getString("workflowRunId");
				}
			}
		} catch (Exception e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return null;
	}


	/**
	 * @param dataflowID
	 * @param conditions currently only understands "from" and "to" as timestamps for range queries
	 * @return
	 * @throws SQLException
	 */
	public List<WorkflowRun> getRuns(String dataflowID, Map<String, String> conditions) throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;

		List<WorkflowRun> result = new ArrayList<WorkflowRun>();

		String q = "SELECT * FROM WorkflowRun I join Workflow W on I.workflowId = W.workflowId";

		List<String> conds = new ArrayList<String>();

		if (dataflowID != null) { conds.add("I.workflowId = '"+dataflowID+"'"); }
		if (conditions != null) {
			if (conditions.get("from") != null) { conds.add("timestamp >= "+conditions.get("from")); }
			if (conditions.get("to") != null) { conds.add("timestamp <= "+conditions.get("to")); }
		}
		if (conds.size()>0) { q = q + " where "+conds.get(0); conds.remove(0); }

		while (conds.size()>0) {
			q = q + " and '"+conds.get(0)+"'"; 
			conds.remove(0); 
		}

		q = q + " ORDER BY timestamp desc ";

		try {
			connection = getConnection();
			ps = connection.prepareStatement(q);

			logger.debug(q);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {
					WorkflowRun i = new WorkflowRun();
					i.setWorkflowRunId(rs.getString("workflowRunId"));
					i.setTimestamp(rs.getString("timestamp"));
					i.setWorkflowId(rs.getString("workflowId"));
					i.setWorkflowExternalName(rs.getString("externalName"));
					Blob blob = rs.getBlob("dataflow");
					long length = blob.length();
					blob.getBytes(1, (int) length);
					i.setDataflowBlob(blob.getBytes(1, (int) length));
					result.add(i);
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return result;
	}

	/**
	 * @param constraints
	 *            a Map columnName -> value that defines the query constraints.
	 *            Note: columnName must be fully qualified. This is not done
	 *            well at the moment, i.e., processorNameRef should be
	 *            PortBinding.processorNameRef to avoid ambiguities
	 * @return
	 * @throws SQLException
	 */
	public List<PortBinding> getPortBindings(Map<String, String> constraints)
	throws SQLException {
		List<PortBinding> result = new ArrayList<PortBinding>();

		String q = "SELECT * FROM PortBinding VB " +
		"JOIN Port V ON " +
		"  VB.portName = V.portName " +
		"  AND VB.processorNameRef = V.processorName " +
		"  AND VB.workflowId = V.workflowId ";

		q = addWhereClauseToQuery(q, constraints, true);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {
					PortBinding vb = new PortBinding();

					vb.setWorkflowId(rs.getString("workflowId"));
					vb.setPortName(rs.getString("portName"));
					vb.setWorkflowRunId(rs.getString("workflowRunId"));
					vb.setValue(rs.getString("value"));

					if (rs.getString("collIdRef") == null || rs.getString("collIdRef").equals("null")) {
						vb.setCollIDRef(null);
					} else {
						vb.setCollIDRef(rs.getString("collIdRef"));
					}

					vb.setIteration(rs.getString("iteration"));
					vb.setProcessorName(rs.getString("processorNameRef"));
					vb.setPositionInColl(rs.getInt("positionInColl"));
					vb.setPortId(rs.getString("portId"));
					vb.setIsInputPort(rs.getBoolean("isInputPort"));
					result.add(vb);
				}

			}
		} catch (Exception e) {
			logger.warn("Add VB failed", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return result;
	}

	public List<NestedListNode> getNestedListNodes(
			Map<String, String> constraints) throws SQLException {

		List<NestedListNode> result = new ArrayList<NestedListNode>();

		String q = "SELECT * FROM Collection C ";

		q = addWhereClauseToQuery(q, constraints, true);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
		} catch (InstantiationException e) {
			logger.error("Error finding the nested list nodes", e);
		} catch (IllegalAccessException e) {
			logger.error("Error finding the nested list nodes", e);
		} catch (ClassNotFoundException e) {
			logger.error("Error finding the nested list nodes", e);
		}

		boolean success;
		try {
			success = stmt.execute(q);
			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {
					PortBinding vb = new PortBinding();

					NestedListNode nln = new NestedListNode();

					nln.setCollectionT2Reference(rs.getString("collId"));
					nln.setParentCollIdRef(rs.getString("parentCollIdRef"));
					nln.setWorkflowRunId(rs.getString("workflowRunId"));
					nln.setProcessorName(rs.getString("processorNameRef"));
					nln.setPortName(rs.getString("portName"));
					nln.setIteration(rs.getString("iteration"));

					result.add(nln);

				}
			}
		} finally {
			if (connection != null) {
				connection.close();
			}
		}


		return result;
	}

	public Map<String, Integer> getPredecessorsCount(String workflowRunId) {

		PreparedStatement ps = null;

		Map<String, Integer> result = new HashMap<String, Integer>();

		// get all datalinks for the entire workflow structure for this particular instance
		Connection connection = null;
		try {

			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT A.sourceProcessorName as source , A.destinationProcessorName as sink, A.workflowId as workflowId1, W1.workflowId as workflowId2, W2.workflowId as workflowId3 " +
					"FROM Datalink A join WorkflowRun I on A.workflowId = I.workflowId " +
					"left outer join Workflow W1 on W1.externalName = A.sourceProcessorName " +
					"left outer join Workflow W2 on W2.externalName = A.destinationProcessorName " +
			"where I.workflowRunId = ?");
			ps.setString(1, workflowRunId);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {

					String sink = rs.getString("sink");
					String source = rs.getString("source");

					if (result.get(sink) == null) {
						result.put(sink, 0);
					}

					String name1 = rs.getString("workflowId1");
					String name2 = rs.getString("workflowId2");
					String name3 = rs.getString("workflowId3");

					if (isDataflow(source) && name1.equals(name2)) {
						continue;
					}
					if (isDataflow(sink) && name1.equals(name3)) {
						continue;
					}

					result.put(sink, result.get(sink) + 1);
				}
			}
		} catch (InstantiationException e1) {
			logger.warn("Could not execute query", e1);
		} catch (IllegalAccessException e1) {
			logger.warn("Could not execute query", e1);
		} catch (ClassNotFoundException e1) {
			logger.warn("Could not execute query", e1);
		} catch (SQLException e) {
			logger.error("Error executing query", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
		return result;
	}

	/**
	 * new impl of getProcessorsIncomingLinks whicih avoids complications due to nesting, and relies on the workflowRunId
	 * rather than the workflowId
	 * @param workflowRunId
	 * @return
	 */
	public Map<String, Integer> getPredecessorsCountOld(String workflowRunId) {

		PreparedStatement ps = null;

		Map<String, Integer> result = new HashMap<String, Integer>();

		// get all datalinks for the entire workflow structure for this particular instance
		Connection connection = null;
		try {

			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT destinationProcessorName, P1.firstActivityClass, count(*) as pred " +
					" FROM Datalink A join WorkflowRun I on A.workflowId = I.workflowId " +
					" join Processor P1 on P1.processorName = A.destinationProcessorName " +
					" join Processor P2 on P2.processorName = A.sourceProcessorName " +
					"  where I.workflowRunId = ? " +
					"  and P2.firstActivityClass <> '" + ProvenanceProcessor.DATAFLOW_ACTIVITY + "' " +
					" and ((P1.firstActivityClass = '" + ProvenanceProcessor.DATAFLOW_ACTIVITY + "'  and P1.workflowId = A.workflowId) or " +
					" (P1.firstActivityClass <> '" + ProvenanceProcessor.DATAFLOW_ACTIVITY + "' )) " +
			" group by A.destinationProcessorName, firstActivityClass");
			ps.setString(1, workflowRunId);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {

					int cnt = rs.getInt("pred");

					result.put(rs.getString("destinationProcessorName"), new Integer(cnt));
				}
			}
		} catch (InstantiationException e1) {
			logger.warn("Could not execute query", e1);
		} catch (IllegalAccessException e1) {
			logger.warn("Could not execute query", e1);
		} catch (ClassNotFoundException e1) {
			logger.warn("Could not execute query", e1);
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
		return result;
	}

	/**
	 * used in the toposort phase -- propagation of anl() values through the
	 * graph
	 *
	 * @param workflowId
	 *            reference to static wf name
	 * @return a map <processor name> --> <incoming links count> for each
	 *         processor, without counting the datalinks from the dataflow input to
	 *         processors. So a processor is at the root of the graph if it has
	 *         no incoming links, or all of its incoming links are from dataflow
	 *         inputs.<br/>
	 *         Note: this must be checked for processors that are roots of
	 *         sub-flows... are these counted as top-level root nodes??
	 */
	public Map<String, Integer> getProcessorsIncomingLinks(String workflowId)
	throws SQLException {
		Map<String, Integer> result = new HashMap<String, Integer>();

		boolean success;

		String currentWorkflowProcessor = null;

		PreparedStatement ps = null;

		Statement stmt;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"SELECT processorName, firstActivityClass FROM Processor WHERE workflowId = ?");
			ps.setString(1, workflowId);

			success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {

					// PM CHECK 6/09
					if (rs.getString("firstActivityClass").equals(ProvenanceProcessor.DATAFLOW_ACTIVITY)) {
						currentWorkflowProcessor = rs.getString("processorName");
						logger.info("currentWorkflowProcessor = " + currentWorkflowProcessor);
					}
					result.put(rs.getString("processorName"), new Integer(0));
				}
			}
		} catch (InstantiationException e1) {
			logger.warn("Could not execute query", e1);
		} catch (IllegalAccessException e1) {
			logger.warn("Could not execute query", e1);
		} catch (ClassNotFoundException e1) {
			logger.warn("Could not execute query", e1);
		} finally {
			try {
				connection.close();
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
			connection = null;
		}

		// fetch the name of the top-level dataflow. We use this to exclude datalinks
		// outgoing from its inputs

		////////////////
		// CHECK below -- gets confused on nested workflows
		////////////////
		String parentWF = getParentOfWorkflow(workflowId);
		if (parentWF == null) {
			parentWF = workflowId;  // null parent means we are the top
		}
		logger.debug("parent WF: " + parentWF);

		// get nested dataflows -- we want to avoid these in the toposort algorithm
		List<ProvenanceProcessor> procs = getProcessorsShallow(
				ProvenanceProcessor.DATAFLOW_ACTIVITY,
				parentWF);

		StringBuffer pNames = new StringBuffer();
		pNames.append("(");
		boolean first = true;
		for (ProvenanceProcessor p : procs) {

			if (!first) {
				pNames.append(",");
			} else {
				first = false;
			}
			pNames.append(" '" + p.getProcessorName() + "' ");
		}
		pNames.append(")");


		// exclude processors connected to inputs -- those have 0 predecessors
		// for our purposes
		// and we add them later

		// PM 6/09 not sure we need to exclude datalinks going into sub-flows?? so commented out the condition
		String q = "SELECT destinationProcessorName, count(*) as cnt " + "FROM Datalink " + "WHERE workflowId = \'" + workflowId + "\' " + "AND destinationProcessorName NOT IN " + pNames + " " //		+ "AND sourceProcessorName NOT IN " + pNames
		+ " GROUP BY destinationProcessorName";

		logger.info("executing \n" + q);

		try {
			connection = getConnection();
			stmt = connection.createStatement();
			success = stmt.execute(q);
			if (success) {
				ResultSet rs = stmt.getResultSet();
				while (rs.next()) {

					if (!rs.getString("destinationProcessorName").equals(currentWorkflowProcessor)) {
						result.put(rs.getString("destinationProcessorName"), new Integer(rs.getInt("cnt")));
					}
				}
				result.put(currentWorkflowProcessor, 0);
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			try {
				connection.close();
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}

		return result;
	}

	public List<Port> getSuccPorts(String processorName, String portName,
			String workflowId) throws SQLException {

		List<Port> result = new ArrayList<Port>();
		PreparedStatement ps = null;
		Connection connection = null;

		try {
			connection = getConnection();
			String sql = "SELECT v.* " + "FROM Datalink a JOIN Port v ON a.destinationProcessorName = v.processorName " + "AND  a.destinationPortName = v.portName " + "AND a.workflowId = v.workflowId " + "WHERE sourcePortName=? AND sourceProcessorName=?";
			if (workflowId != null) {
				sql = sql + 
				" AND a.workflowId=?";
			}
			ps = connection.prepareStatement(sql);

			ps.setString(1, portName);
			ps.setString(2, processorName);
			if (workflowId != null) {
				ps.setString(3, workflowId);
			}
			
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {
                    Port aPort = new Port();

                    aPort.setWorkflowId(rs.getString("workflowId"));
                    aPort.setInputPort(rs.getBoolean("isInputPort"));
					aPort.setIdentifier(rs.getString("portId"));
                    aPort.setProcessorName(rs.getString("processorName"));
                    aPort.setProcessorId(rs.getString("processorId"));
                    aPort.setPortName(rs.getString("portName"));
                    aPort.setDepth(rs.getInt("depth"));
                    if (rs.getString("resolvedDepth") != null) {
						aPort.setResolvedDepth(rs.getInt("resolvedDepth"));
					}                    
                    result.add(aPort);
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return result;
	}

	public List<String> getSuccProcessors(String pName, String workflowId, String workflowRunId)
	throws SQLException {
		List<String> result = new ArrayList<String>();

		PreparedStatement ps = null;

		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT distinct destinationProcessorName FROM Datalink A JOIN WorkflowRun I on A.workflowId = I.workflowId " + "WHERE A.workflowId = ? and I.workflowRunId = ? AND sourceProcessorName = ?");
			ps.setString(1, workflowId);
			ps.setString(2, workflowRunId);
			ps.setString(3, pName);

			boolean success = ps.execute();


			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {
					result.add(rs.getString("destinationProcessorName"));
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return result;
	}

	/**
	 * get all processors of a given type within a structure identified by
	 * workflowId (reference to dataflow). type constraint is ignored if value is null.<br>
	 * this only returns the processor for the input workflowId, without going into any neted workflows
	 *
	 * @param workflowId
	 * @param firstActivityClass
	 * @return a list, that contains at most one element
	 * @throws SQLException
	 */
	public List<ProvenanceProcessor> getProcessorsShallow(String firstActivityClass, String workflowId)
	throws SQLException {
		Map<String, String> constraints = new HashMap<String, String>();

		constraints.put("P.workflowId", workflowId);
		if (firstActivityClass != null) {
			constraints.put("P.firstActivityClass", firstActivityClass);
		}
		return getProcessors(constraints);
	}
	

	public ProvenanceProcessor getProvenanceProcessorByName(
			String workflowId, String processorName) {
		
		Map<String, String> constraints = new HashMap<String, String>();
		constraints.put("P.workflowId", workflowId);			
		constraints.put("P.processorName", processorName);
		List<ProvenanceProcessor> processors;
		try {
			processors = getProcessors(constraints);
		} catch (SQLException e1) {
			logger.warn("Could not find processor for " + constraints, e1);
			return null;
		}
		if (processors.size() != 1) {
			logger.warn("Could not uniquely find processor for " + constraints + ", got: " + processors);
			return null;
		}
		return processors.get(0);
	}
	
	public ProvenanceProcessor getProvenanceProcessorById(String processorId) {
		Map<String, String> constraints = new HashMap<String, String>();
		constraints.put("P.processorId", processorId);
		List<ProvenanceProcessor> processors;
		try {
			processors = getProcessors(constraints);
		} catch (SQLException e1) {
			logger.warn("Could not find processor for " + constraints, e1);
			return null;
		}
		if (processors.size() != 1) {
			logger.warn("Could not uniquely find processor for " + constraints + ", got: " + processors);
			return null;
		}
		return processors.get(0);
	}


	/**
	 * this is similar to {@link #getProcessorsShallow(String, String)} but it recursively fetches all processors
	 * within nested workflows. The result is collected in the form of a map: workflowId -> {ProvenanceProcessor}
	 * @param firstActivityClass
	 * @param workflowId
	 * @return a map: workflowId -> {ProvenanceProcessor} where workflowId is the name of a (possibly nested) workflow, and 
	 * the values are the processors within that workflow
	 */
	public Map<String, List<ProvenanceProcessor>> getProcessorsDeep(String firstActivityClass, String workflowId) {

		Map<String, List<ProvenanceProcessor>> result = new HashMap<String, List<ProvenanceProcessor>>();

		List<ProvenanceProcessor> currentProcs;

		try {
			currentProcs = getProcessorsShallow(null, workflowId);
			List<ProvenanceProcessor> matchingProcessors = new ArrayList<ProvenanceProcessor>();
			result.put(workflowId, matchingProcessors);
			for (ProvenanceProcessor pp:currentProcs) {
				if (firstActivityClass==null || pp.getFirstActivityClassName().equals(firstActivityClass)) {
					matchingProcessors.add(pp);
				}				
				if (pp.getFirstActivityClassName().equals(ProvenanceProcessor.DATAFLOW_ACTIVITY)) {
					// Can't recurse as there's no way to find ID of nested workflow
					continue;
					//result.putAll(getProcessorsDeep(firstActivityClass, NESTED_WORKFLOW_ID));					
				}
			}
			
			// Silly fallback - use the broken getChildrenOfWorkflow() assuming that no other workflows
			// have used the same nested workflow
			for (String childWf : getChildrenOfWorkflow(workflowId)) {
				result.putAll(getProcessorsDeep(firstActivityClass, childWf));
			}
			
		} catch (SQLException e) {
			logger.error("Problem getting nested workflow processors for: " + workflowId, e);
		}
		return result;
	}




	public String getDataValue(String valueRef) {

		String q = "SELECT * FROM Data where dataReference = '"+valueRef+"';";

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();

				if (rs.next()) {					
					return rs.getString("data");
				}
			} else return null;
		} catch (Exception e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return null;
	}


	/**
	 * generic method to fetch processors subject to additional query constraints
	 * @param constraints
	 * @return
	 * @throws SQLException
	 */
	public List<ProvenanceProcessor> getProcessors(
			Map<String, String> constraints) throws SQLException {
		List<ProvenanceProcessor> result = new ArrayList<ProvenanceProcessor>();

		String q = "SELECT P.* FROM Processor P";
				//" JOIN WorkflowRun W ON P.workflowRunId = W.workflowId "+
		         //  "JOIN Workflow WF on W.workflowId = WF.workflowId";

		q = addWhereClauseToQuery(q, constraints, true);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {
					ProvenanceProcessor proc = new ProvenanceProcessor();
					proc.setIdentifier(rs.getString("processorId"));
					proc.setProcessorName(rs.getString("processorName"));
					proc.setFirstActivityClassName(rs.getString("firstActivityClass"));
					proc.setWorkflowId(rs.getString("workflowId"));
					proc.setTopLevelProcessor(rs.getBoolean("isTopLevel"));
					result.add(proc);

				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return result;
	}


	public List<ProvenanceProcessor> getProcessorsForWorkflow(String workflowID) {

		PreparedStatement ps = null;
		Connection connection = null;

		List<ProvenanceProcessor> result = new ArrayList<ProvenanceProcessor>();

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT * from Processor WHERE workflowId=?");
			ps.setString(1, workflowID);

			boolean success = ps.execute();
			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {  
					ProvenanceProcessor proc = new ProvenanceProcessor();
					proc.setIdentifier(rs.getString("processorId"));
					proc.setProcessorName(rs.getString("processorName"));
					proc.setFirstActivityClassName(rs.getString("firstActivityClass"));
					proc.setWorkflowId(rs.getString("workflowId"));					
					proc.setTopLevelProcessor(rs.getBoolean("isTopLevel"));
					result.add(proc);
				}
			}
		} catch (SQLException e) {
			logger.error("Problem getting processor for workflow: " + workflowID, e);
		} catch (InstantiationException e) {
			logger.error("Problem getting processor for workflow: " + workflowID, e);
		} catch (IllegalAccessException e) {
			logger.error("Problem getting processor for workflow: " + workflowID, e);
		} catch (ClassNotFoundException e) {
			logger.error("Problem getting processor for workflow: " + workflowID, e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.error("Problem getting processor for workflow: " + workflowID, e);
				}
			}
		}
		return result;
	}

	/**
	 * simplest possible pinpoint query. Uses iteration info straight away. Assumes result is in PortBinding not in Collection
	 *
	 * @param workflowRun
	 * @param pname
	 * @param vname
	 * @param iteration
	 * @return
	 */
	public LineageSQLQuery simpleLineageQuery(String workflowRun, String workflowId, String pname,
			String vname, String iteration) {
		LineageSQLQuery lq = new LineageSQLQuery();

		String q1 = "SELECT * FROM PortBinding VB join Port V " + 
		"on (VB.portName = V.portName and VB.processorNameRef =  V.processorName and VB.workflowId=V.workflowId) " + 
		"JOIN WorkflowRun W ON VB.workflowRunId = W.workflowRunId and VB.workflowId = W.workflowId ";

		// constraints:
		Map<String, String> lineageQueryConstraints = new HashMap<String, String>();

		lineageQueryConstraints.put("W.workflowRunId", workflowRun);
		lineageQueryConstraints.put("VB.processorNameRef", pname);
		lineageQueryConstraints.put("VB.workflowId", workflowId);

		if (vname != null) {
			lineageQueryConstraints.put("VB.portName", vname);
		}
		if (iteration != null) {
			lineageQueryConstraints.put("VB.iteration", iteration);
		}

		q1 = addWhereClauseToQuery(q1, lineageQueryConstraints, false); // false:
		// do
		// not
		// terminate
		// query

		// add order by clause
		List<String> orderAttr = new ArrayList<String>();
		orderAttr.add("V.portName");
		orderAttr.add("iteration");

		q1 = addOrderByToQuery(q1, orderAttr, true);

		logger.debug("Query is: " + q1);
		lq.setVbQuery(q1);

		return lq;
	}

	/**
	 * if var2Path is null this generates a trivial query for the current output
	 * var and current path
	 *
	 * @param workflowRunId
	 * @param proc
	 * @param var2Path
	 * @param outputVar
	 * @param path
	 * @param returnOutputs
	 *            returns inputs *and* outputs if set to true
	 * @return
	 */
	public List<LineageSQLQuery> lineageQueryGen(String workflowRunId, String proc,
			Map<Port, String> var2Path, Port outputVar, String path,
			boolean returnOutputs) {
		// setup
		StringBuffer effectivePath = new StringBuffer();

		List<LineageSQLQuery> newQueries = new ArrayList<LineageSQLQuery>();

		// use the calculated path for each input var
		boolean isInput = true;
		for (Port v : var2Path.keySet()) {
			LineageSQLQuery q = generateSQL2(workflowRunId, proc, v.getPortName(), var2Path.get(v), isInput);
			if (q != null) {
				newQueries.add(q);
			}
		}

		// is returnOutputs is true, then use proc, path for the output var as well
		if (returnOutputs) {

			isInput = false;

			LineageSQLQuery q = generateSQL2(workflowRunId, proc, outputVar.getPortName(), path, isInput);  // && !var2Path.isEmpty());
			if (q != null) {
				newQueries.add(q);
			}
		}
		return newQueries;


	}

	protected LineageSQLQuery generateSQL2(String workflowRun, String proc,
			String var, String path, boolean returnInput) {

		LineageSQLQuery lq = new LineageSQLQuery();

		// constraints:
		Map<String, String> collQueryConstraints = new HashMap<String, String>();

		// base Collection query
		String collQuery = "SELECT C.*,W.workflowId,V.isInputPort FROM Collection C JOIN WorkflowRun W ON "
				+ "C.workflowRunId = W.workflowRunId "
				+ "JOIN Port V on "
				+ "V.workflowId = W.workflowId and C.processorNameRef = V.processorName and C.portName = V.portName ";

		collQueryConstraints.put("W.workflowRunId", workflowRun);
		collQueryConstraints.put("C.processorNameRef", proc);

		if (path != null && path.length() > 0) {
			collQueryConstraints.put("C.iteration", "[" + path + "]"); // PM 1/09 -- path
		}

		// inputs or outputs?
		if (returnInput) {
			collQueryConstraints.put("V.isInputPort", "1");
		} else {
			collQueryConstraints.put("V.isInputPort", "0");
		}

		collQuery = addWhereClauseToQuery(collQuery, collQueryConstraints, false);

		lq.setCollQuery(collQuery);

		//  vb query

		Map<String, String> vbQueryConstraints = new HashMap<String, String>();

		// base PortBinding query
		String vbQuery = "SELECT VB.*,V.isInputPort FROM PortBinding VB JOIN WorkflowRun W ON " + 
						 "VB.workflowRunId = W.workflowRunId " + 
						 "JOIN Port V on " + 
						 "V.workflowId = W.workflowId and VB.processorNameRef = V.processorName and VB.portName = V.portName "; 

		vbQueryConstraints.put("W.workflowRunId", workflowRun);
		vbQueryConstraints.put("VB.processorNameRef", proc);
		vbQueryConstraints.put("VB.portName", var);

		if (path != null && path.length() > 0) {
			vbQueryConstraints.put("VB.iteration", "[" + path + "]"); // PM 1/09 -- path
		}

		// limit to inputs?
		if (returnInput) {
			vbQueryConstraints.put("V.isInputPort", "1");
		} else {
			vbQueryConstraints.put("V.isInputPort", "0");
		}

		vbQuery = addWhereClauseToQuery(vbQuery, vbQueryConstraints, false);

		List<String> orderAttr = new ArrayList<String>();
		orderAttr.add("V.portName");
		orderAttr.add("iteration");

		vbQuery = addOrderByToQuery(vbQuery, orderAttr, true);

		lq.setVbQuery(vbQuery);

		return lq;
	}

	/**
	 * if effectivePath is not null: query varBinding using: workflowRunId =
	 * workflowRun, iteration = effectivePath, processorNameRef = proc if input vars is
	 * null, then use the output var this returns the bindings for the set of
	 * input vars at the correct iteration if effectivePath is null: fetch
	 * PortBindings for all input vars, without constraint on the iteration<br/>
	 * additionally, try querying the collection table first -- if the query succeeds, it means
	 * the path is pointing to an internal node in the collection, and we just got the right node.
	 * Otherwise, query PortBinding for the leaves
	 *
	 * @param workflowRun
	 * @param proc
	 * @param effectivePath
	 * @param returnOutputs
	 *            returns both inputs and outputs if set to true
	 * @return
	 */
	public LineageSQLQuery generateSQL(String workflowRun, String proc,
			String effectivePath, boolean returnOutputs) {

		LineageSQLQuery lq = new LineageSQLQuery();

		// constraints:
		Map<String, String> collQueryConstraints = new HashMap<String, String>();

		// base Collection query
		String collQuery = "SELECT * FROM Collection C JOIN WorkflowRun W ON " + "C.workflowRunId = W.workflowRunId " + "JOIN Port V on " + "V.workflowRunId = W.workflowId and C.processorNameRef = V.processorNameRef and C.portName = V.portName ";

		collQueryConstraints.put("W.workflowRunId", workflowRun);
		collQueryConstraints.put("C.processorNameRef", proc);

		if (effectivePath != null && effectivePath.length() > 0) {
			collQueryConstraints.put("C.iteration", "[" + effectivePath.toString() + "]"); // PM 1/09 -- path
		}

		// limit to inputs?
		if (returnOutputs) {
			collQueryConstraints.put("V.isInputPort", "1");
		}

		collQuery = addWhereClauseToQuery(collQuery, collQueryConstraints, false);

		lq.setCollQuery(collQuery);

		//  vb query

		Map<String, String> vbQueryConstraints = new HashMap<String, String>();

		// base PortBinding query
		String vbQuery = "SELECT * FROM PortBinding VB JOIN WorkflowRun W ON " + 
						 "VB.workflowRunId = W.workflowRunId " + 
						 "JOIN Port V on " + 
						 "V.workflowRunId = W.workflowId and VB.processorNameRef = V.processorNameRef and VB.portName = V.portName "; 

		vbQueryConstraints.put("W.workflowRunId", workflowRun);
		vbQueryConstraints.put("VB.processorNameRef", proc);

		if (effectivePath != null && effectivePath.length() > 0) {
			vbQueryConstraints.put("VB.iteration", "[" + effectivePath.toString() + "]"); // PM 1/09 -- path
		}

		// limit to inputs?
		if (!returnOutputs) {
			vbQueryConstraints.put("V.isInputPort", "1");
		}

		vbQuery = addWhereClauseToQuery(vbQuery, vbQueryConstraints, false);

		List<String> orderAttr = new ArrayList<String>();
		orderAttr.add("portName");
		orderAttr.add("iteration");

		vbQuery = addOrderByToQuery(vbQuery, orderAttr, true);


		lq.setVbQuery(vbQuery);

		return lq;
	}

	public Dependencies runCollectionQuery(LineageSQLQuery lq) throws SQLException {

		String q = lq.getCollQuery();

		Dependencies lqr = new Dependencies();

		if (q == null) {
			return lqr;
		}

		logger.debug("running collection query: " + q);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();


				while (rs.next()) {

					String type = lqr.ATOM_TYPE; // temp -- FIXME

					String workflowId = rs.getString("workflowId");
					String workflowRun = rs.getString("workflowRunId");
					String proc = rs.getString("processorNameRef");
					String var = rs.getString("portName");
					String it = rs.getString("iteration");
					String coll = rs.getString("collID");
					String parentColl = rs.getString("parentCollIDRef");
					boolean isInput = rs.getBoolean("isInputPort");

					lqr.addLineageQueryResultRecord(workflowId, proc, var, workflowRun,
							it, coll, parentColl, null, null, type, false, true);  // true -> is a collection
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return lqr;
	}


	/**
	 * 
	 * @param lq
	 * @param includeDataValue  IGNORED. always false
	 * @return
	 * @throws SQLException
	 */
	public Dependencies runVBQuery(LineageSQLQuery lq, boolean includeDataValue) throws SQLException {

		String q = lq.getVbQuery();

		logger.info("running VB query: " + q);

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();

				Dependencies lqr = new Dependencies();

				while (rs.next()) {

					String type = lqr.ATOM_TYPE; // temp -- FIXME

					String workflowId = rs.getString("workflowId");
					String workflowRun = rs.getString("workflowRunId");
					String proc = rs.getString("processorNameRef");
					String var = rs.getString("portName");
					String it = rs.getString("iteration");
					String coll = rs.getString("collIDRef");
					String value = rs.getString("value");
					boolean isInput = rs.getBoolean("isInputPort");


					// FIXME there is no D and no VB - this is in generateSQL,
					// not simpleLineageQuery
					// commented out as D table no longer available. Need to replace this with deref from DataManager
//					if (includeDataValue) {
//						String resolvedValue = rs.getString("D.data");

						// System.out.println("resolved value: "+resolvedValue);
//						lqr.addLineageQueryResultRecord(workflowId, proc, var, workflowRun,
//								it, coll, null, value, resolvedValue, type, isInput, false);  // false -> not a collection
//					} else {

						// FIXME if the data is required then the query needs
						// fixing
						lqr.addLineageQueryResultRecord(workflowId, proc, var, workflowRun,
								it, coll, null, value, null, type, isInput, false);
//					}
				}
				return lqr;
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return null;
	}

	/**
	 * executes one of the lineage queries produced by the graph visit algorithm. This first executes the collection query, and then
	 * if no result is returned, the varBinding query
	 *
	 * @param lq
	 *            a lineage query computed during the graph traversal
	 * @param includeDataValue
	 *            if true, then the referenced value is included in the result.
	 *            This may only be necessary for testing: the data reference in
	 *            field value (which is a misleading field name, and actually
	 *            refers to the data reference) should be sufficient
	 * @return
	 * @throws SQLException
	 */
	public Dependencies runLineageQuery(LineageSQLQuery lq,
			boolean includeDataValue) throws SQLException {

		Dependencies result = runCollectionQuery(lq);

		if (result.getRecords().size() == 0) // query was really VB
		{
			return runVBQuery(lq, includeDataValue);
		}

		return result;
	}

	public List<Dependencies> runLineageQueries(
			List<LineageSQLQuery> lqList, boolean includeDataValue)
			throws SQLException {

		List<Dependencies> allResults = new ArrayList<Dependencies>();

		if (lqList == null) {
			logger.warn("lineage queries list is NULL, nothing to evaluate");
			return allResults;
		}

		for (LineageSQLQuery lq : lqList) {
			if (lq == null) {
				continue;
			}
			allResults.add(runLineageQuery(lq, includeDataValue));
		}

		return allResults;
	}

	/**
	 * takes an ordered set of records for the same variable with iteration
	 * indexes and builds a collection out of it
	 *
	 * @param lqr
	 * @return a jdom Document with the collection
	 */
	public Document recordsToCollection(Dependencies lqr) {
		// process each var name in turn
		// lqr ordered by var name and by iteration number
		Document d = new Document(new Element("list"));

		String currentVar = null;
		for (ListIterator<LineageQueryResultRecord> it = lqr.iterator(); it.hasNext();) {

			LineageQueryResultRecord record = it.next();

			if (currentVar != null && record.getPortName().equals(currentVar)) { // multiple
				// occurrences
				addToCollection(record, d); // adds record to d in the correct
				// position given by the iteration
				// vector
			}
			if (currentVar == null) {
				currentVar = record.getPortName();
			}
		}
		return d;
	}

	private void addToCollection(LineageQueryResultRecord record, Document d) {

		Element root = d.getRootElement();

		String[] itVector = record.getIteration().split(",");

		Element currentEl = root;
		// each element gives us a corresponding child in the tree
		for (int i = 0; i < itVector.length; i++) {

			int index = Integer.parseInt(itVector[i]);

			List<Element> children = currentEl.getChildren();
			if (index < children.size()) { // we already have the child, just
				// descend
				currentEl = children.get(index);
			} else { // create child
				if (i == itVector.length - 1) { // this is a leaf --> atomic
					// element
					currentEl.addContent(new Element(record.getValue()));
				} else { // create internal element
					currentEl.addContent(new Element("list"));
				}
			}
		}

	}

	/**
	 * 
	 * returns the set of all processors that are structurally contained within
	 * the wf corresponding to the input dataflow name
	 * @param workflowName the name of a processor of type DataFlowActivity
	 * @return
	 * 
	 * @deprecated as workflow 'names' are not globally unique, this method should not be used!
	 */
	@Deprecated
	public List<String> getContainedProcessors(String workflowName) {

		List<String> result = new ArrayList<String>();

		// dataflow name -> wfRef
		String containerDataflow = getWorkflowIdForExternalName(workflowName);

		// get all processors within containerDataflow
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
//					"SELECT processorName FROM Processor P  join workflowRun I on P.workflowId = I.workflowId " +
//			"where workflowRunId = ? and I.workflowRunId = ?");
					"SELECT processorName FROM Processor P " +
					"WHERE workflowId = ?");
			ps.setString(1, containerDataflow);
//			ps.setString(2, workflowRunId);


			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {
					result.add(rs.getString("processorName"));
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} finally {
			try {
				connection.close();
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}
		return result;
	}

	public String getTopLevelDataflowName(String workflowRunId) {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT processorName FROM Processor P  JOIN WorkflowRun I on P.workflowId = I.workflowId " +
			"where  I.workflowRunId =? and isTopLevel = 1");


			ps.setString(1, workflowRunId);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				if (rs.next()) {
					return rs.getString("processorName");
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} finally {
			try {
				connection.close();
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}
		return null;
	}
	
	
	
	/**
	 * retrieve a tree structure starting from the top parent
	 * @param workflowID
	 * @return
	 * @throws SQLException 
	 */
	public WorkflowTree getWorkflowNestingStructure(String workflowID) throws SQLException {
		
		WorkflowTree tree = new WorkflowTree();
		
	    Workflow wf = getWorkflow(workflowID);
	    tree.setNode(wf);
	
	    List<String> children = getChildrenOfWorkflow(workflowID);
	    for (String childWfName:children) {
	    	
	    	WorkflowTree childStructure = getWorkflowNestingStructure(childWfName);
	    	tree.addChild(childStructure);
	    }	    
	    return tree;
	}

	/**
	 * returns the internal ID of a dataflow given its external name
	 * @param externalName
	 * @param workflowRunId
	 * @return
	 * @deprecated as workflow 'names' are not globally unique, this method should not be used!
	 */
	@Deprecated
	public String getWorkflowIdForExternalName(String externalName) {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();

			ps = connection.prepareStatement(
//			"SELECT workflowId FROM Workflow W join WorkflowRun I on W.workflowId = I.workflowId WHERE W.externalName = ? and I.workflowRunId = ?");
			"SELECT workflowId FROM Workflow W WHERE W.externalName = ?");
			ps.setString(1, externalName);
//			ps.setString(2, workflowRunId);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				if (rs.next()) {
					return rs.getString("workflowId");
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} catch (SQLException e) {
			logger.error("Could not execute query", e);
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}
		return null;
	}

	/**
	 * This method is deprecated as parent workflow ID is not correctly
	 * recorded. If two workflows both contain the same nested workflow, only
	 * one of them (the most recently added) will return that nested workflow
	 * from this method.
	 * 
	 * @deprecated
	 * @param parentWorkflowId
	 * @return
	 * @throws SQLException
	 */
	@Deprecated
	public List<String> getChildrenOfWorkflow(String parentWorkflowId)
	throws SQLException {

		List<String> result = new ArrayList<String>();

		PreparedStatement ps = null;
		Connection connection = null;

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"SELECT workflowId FROM Workflow WHERE parentWorkflowId = ? ");
			ps.setString(1, parentWorkflowId);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {
					result.add(rs.getString("workflowId"));
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return result;
	}

	/**
	 * fetch children of parentWorkflowId from the Workflow table
	 *
	 * @return
	 * @param childworkflowId
	 * @throws SQLException
	 */
	public String getParentOfWorkflow(String childworkflowId) throws SQLException {

		PreparedStatement ps = null;
		String result = null;
		Connection connection = null;

		String q = "SELECT parentWorkflowId FROM Workflow WHERE workflowId = ?";
		// Statement stmt;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(q);
			ps.setString(1, childworkflowId);

			logger.debug("getParentOfWorkflow - query: " + q + "  with workflowId = " + childworkflowId);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {

					result = rs.getString("parentWorkflowId");

					logger.debug("result: " + result);
					break;

				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return result;
	}

	public List<String> getAllworkflowIds() throws SQLException {
		List<String> result = new ArrayList<String>();

		String q = "SELECT workflowId FROM Workflow";

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();
				while (rs.next()) {
					result.add(rs.getString("workflowId"));
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return result;
	}

	/**
	 * @deprecated This method is not workflowId aware and should not be used
	 * @param procName
	 * @return true if procName is the external name of a dataflow, false
	 *         otherwise
	 * @throws SQLException
	 */
	public boolean isDataflow(String procName) throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"SELECT firstActivityClass FROM Processor WHERE processorName=?");
			ps.setString(1, procName);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				if (rs.next() && rs.getString("firstActivityClass") != null && rs.getString("firstActivityClass").equals(ProvenanceProcessor.DATAFLOW_ACTIVITY)) {
					return true;
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return false;
	}

	
	public boolean isTopLevelDataflow(String workflowIdID)  {
		
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT * FROM Workflow W " +
					" where W.workflowId = ? ");
			
			ps.setString(1, workflowIdID);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				if (rs.next()) {
					if (rs.getString("parentWorkflowId") == null) return true;
					return false;
				}
			}
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}
		return false;
	}
	
	public boolean isTopLevelDataflow(String workflowId, String workflowRunId) {
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT " + DataflowInvocationTable.parentProcessorEnactmentId + " AS parent" +
					" FROM " + DataflowInvocationTable.DataflowInvocation + " W " +					
					" WHERE "+ DataflowInvocationTable.workflowId + "=? AND " + 
					DataflowInvocationTable.workflowRunId + "=?");
			
			ps.setString(1, workflowId);
			ps.setString(2, workflowRunId);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				if (rs.next()) {
					if (rs.getString("parent") == null) { 
						return true;					
					}
					return false;
				}
			}
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}
		return false;
	}
	
	
	public String getTopDataflow(String workflowRunId) {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT processorName FROM Processor P join WorkflowRun I on P.workflowId = I.workflowId " +
					" where I.workflowRunId = ? " +
			" and isTopLevel = 1 ");
			ps.setString(1, workflowRunId);
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				if (rs.next()) {
					return rs.getString("processorName");
				}
			}
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException ex) {
				logger.error("An error occurred closing the database connection", ex);
			}
		}
		return null;
	}

	/**
	 *
	 * @param p
	 *            pTo processor
	 * @param var
	 *            vTo
	 * @param value
	 *            valTo
	 * @return a set of DDRecord
	 * @throws SQLException
	 */
	public List<DDRecord> queryDD(String p, String var, String value,
			String iteration, String workflowRun) throws SQLException {

		List<DDRecord> result = new ArrayList<DDRecord>();

		Map<String, String> queryConstraints = new HashMap<String, String>();

		queryConstraints.put("pTo", p);
		queryConstraints.put("vTo", var);
		if (value != null) {
			queryConstraints.put("valTo", value);
		}
		if (iteration != null) {
			queryConstraints.put("iteration", iteration);
		}
		if (workflowRun != null) {
			queryConstraints.put("workflowRun", workflowRun);
		}

		String q = "SELECT * FROM   DD ";

		q = addWhereClauseToQuery(q, queryConstraints, true); // true: terminate
		// SQL statement

		Statement stmt;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			boolean success = stmt.execute(q);

			if (success) {
				ResultSet rs = stmt.getResultSet();

				while (rs.next()) {

					DDRecord aDDrecord = new DDRecord();
					aDDrecord.setPFrom(rs.getString("pFrom"));
					aDDrecord.setVFrom(rs.getString("vFrom"));
					aDDrecord.setValFrom(rs.getString("valFrom"));
					aDDrecord.setPTo(rs.getString("pTo"));
					aDDrecord.setVTo(rs.getString("vTo"));
					aDDrecord.setValTo(rs.getString("valTo"));

					result.add(aDDrecord);
				}
				return result;
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return null;
	}

	public Set<DDRecord> queryDataLinksForDD(String p, String v, String val,
			String workflowRun) throws SQLException {

		Set<DDRecord> result = new HashSet<DDRecord>();

		PreparedStatement ps = null;
		Connection connection = null;

		String q = "SELECT DISTINCT A.sourceProcessorName AS p, A.sourcePortName AS var, VB.value AS val " + "FROM   Datalink A JOIN PortBinding VB ON VB.portName = A.destinationPortName AND VB.processorNameRef = A.destinationProcessorName " + "JOIN   WorkflowRun WF ON WF.workflowId = A.workflowId AND WF.workflowRunId = VB.workflowRunId  " + "WHERE  WF.workflowRunId = '" + workflowRun + "' AND A.destinationProcessorName = '" + p + "' AND A.destinationPortName = '" + v + "' AND VB.value = '" + val + "' ";

		// Statement stmt;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT DISTINCT A.sourceProcessorName AS p, A.sourcePortName AS var, VB.value AS val " + "FROM   Datalink A JOIN PortBinding VB ON VB.portName = A.destinationPortName AND VB.processorNameRef = A.destinationProcessorName " + "JOIN   WorkflowRun WF ON WF.workflowId = A.workflowId AND WF.workflowRunId = VB.workflowRunId  " + "WHERE  WF.workflowRunId = ? AND A.destinationProcessorName = ? AND A.destinationPortName = ? AND VB.value = ?");

			ps.setString(1, workflowRun);
			ps.setString(2, p);
			ps.setString(3, v);
			ps.setString(4, val);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {

					DDRecord aDDrecord = new DDRecord();
					aDDrecord.setPTo(rs.getString("p"));
					aDDrecord.setVTo(rs.getString("var"));
					aDDrecord.setValTo(rs.getString("val"));

					result.add(aDDrecord);
				}
				return result;
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return null;
	}

	public Set<DDRecord> queryAllFromValues(String workflowRun)
	throws SQLException {

		Set<DDRecord> result = new HashSet<DDRecord>();

		PreparedStatement ps = null;
		Connection connection = null;

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"SELECT DISTINCT PFrom, vFrom, valFrom FROM DD where workflowRun = ?");
			ps.setString(1, workflowRun);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {

					DDRecord aDDrecord = new DDRecord();
					aDDrecord.setPFrom(rs.getString("PFrom"));
					aDDrecord.setVFrom(rs.getString("vFrom"));
					aDDrecord.setValFrom(rs.getString("valFrom"));

					result.add(aDDrecord);
				}
				return result;
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return null;

	}


	public boolean isRootProcessorOfWorkflow(String procName, String workflowId,
			String workflowRunId) {

		PreparedStatement ps = null;
		Connection connection = null;

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT * FROM Datalink A JOIN WorkflowRun I ON A.workflowId = I.workflowId " +
					"JOIN Processor P on P.processorName = A.sourceProcessorName WHERE sourceProcessorName = ? " +
					"AND P.workflowId <> A.workflowId " +
					"AND I.workflowRunId = ? " +
			"AND destinationProcessorName = ? ");

			ps.setString(1, workflowId);
			ps.setString(2, workflowRunId);
			ps.setString(3, procName);

			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();

				if (rs.next()) {
					return true;
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
		return false;
	}

	/**
	 * returns a Workflow record from the DB given the workflow internal ID
	 * @param dataflowID
	 * @return
	 */
	public Workflow getWorkflow(String dataflowID) {

		PreparedStatement ps = null;
		Connection connection = null;

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"SELECT * FROM Workflow W "+
			"WHERE workflowId = ? ");

			ps.setString(1, dataflowID);

			boolean success = ps.execute();
			if (success) {
				ResultSet rs = ps.getResultSet();

				if (rs.next()) {
					Workflow wf = new Workflow();
					wf.setWorkflowId(rs.getString("workflowId"));
					wf.setParentWorkflowId(rs.getString("parentWorkflowId"));
					wf.setExternalName(rs.getString("externalName"));

					return wf;
				} else {
					logger.warn("Could not find workflow " + dataflowID);
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
		return null;
	}

	/**
	 * @param record a record representing a single value -- possibly within a list hierarchy
	 * @return the URI for topmost containing collection when the input record is within a list hierarchy, or null otherwise
	 */
	public String getContainingCollection(LineageQueryResultRecord record) {

		if (record.getCollectionT2Reference() == null) return null;
		
		String q = "SELECT * FROM Collection where collID = ? and workflowRunId = ? and processorNameRef = ? and portName = ?";

		PreparedStatement stmt = null;
		Connection connection = null;

		String parentCollIDRef = null;
		try {
			connection = getConnection();
			stmt = connection.prepareStatement(q);
			
			stmt.setString(1, record.getCollectionT2Reference());
			stmt.setString(2, record.getWorkflowRunId());
			stmt.setString(3, record.getProcessorName());
			stmt.setString(4, record.getPortName());

			String tmp = stmt.toString();

			boolean success = stmt.execute();

			if (success) {
				ResultSet rs = stmt.getResultSet();

				if (rs.next()) {
					parentCollIDRef = rs.getString("parentCollIDRef");
				}
			}
		} catch (InstantiationException e) {
			logger.warn("Could not execute query", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not execute query", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not execute query", e);
		} catch (SQLException e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}

		while (parentCollIDRef != null) {  // INITIALLY not null -- would be TOP if the initial had no parent

			String oldParentCollIDRef = parentCollIDRef;

			// query Collection again for parent collection
			try {
				connection = getConnection();
				stmt = connection.prepareStatement(q);

				stmt.setString(1, oldParentCollIDRef);
				stmt.setString(2, record.getWorkflowRunId());
				stmt.setString(3, record.getProcessorName());
				stmt.setString(4, record.getPortName());

				//String tmp = stmt.toString();

				boolean success = stmt.execute();
				if (success) {
					ResultSet rs = stmt.getResultSet();
					if (rs.next()) {
						parentCollIDRef = rs.getString("parentCollIDRef");
						if (parentCollIDRef.equals("TOP")) {
							return oldParentCollIDRef;
						}
					}
				}
			} catch (Exception e) {
				logger.warn("Could not execute query", e);
			}
		}
		return null;
	}

	public List<ProcessorEnactment> getProcessorEnactments(
			String workflowRunId, String... processorPath) {
		return getProcessorEnactments(workflowRunId, (List<ProcessorEnactment>)null, Arrays.asList(processorPath));
	}
	
	
	private List<ProcessorEnactment> getProcessorEnactments(
			String workflowRunId, List<ProcessorEnactment> parentProcessorEnactments,
			List<String> processorPath) {
		
		List<String> processorEnactmentIds = null;
		if (parentProcessorEnactments != null) {
			processorEnactmentIds = new ArrayList<String>();
			for (ProcessorEnactment processorEnactment : parentProcessorEnactments) {
				String parentId = processorEnactment.getProcessEnactmentId();
				processorEnactmentIds.add(parentId);
			}
		}		
		if (processorPath.size() > 1) {
			List<ProcessorEnactment> parentEnactments = getProcessorEnactmentsByProcessorName(workflowRunId, 
					processorEnactmentIds, processorPath.get(0));
			List<String> childPath = processorPath.subList(1, processorPath.size());
			return getProcessorEnactments(workflowRunId, parentEnactments, childPath);
		} else if (processorPath.size() == 1) {
			return getProcessorEnactmentsByProcessorName(workflowRunId, processorEnactmentIds, processorPath.get(0));
		} else  {
			return getProcessorEnactmentsByProcessorName(workflowRunId, processorEnactmentIds, null);						
		}
	}

	public List<ProcessorEnactment> getProcessorEnactmentsByProcessorName(
			String workflowRunId, List<String> parentProcessorEnactmentIds, String processorName) {
		ProvenanceConnector.ProcessorEnactmentTable ProcEnact = ProvenanceConnector.ProcessorEnactmentTable.ProcessorEnactment;
		
		StringBuilder query = new StringBuilder();
		query.append( 
				"SELECT " + ProcEnact.enactmentStarted + ","
						+ ProcEnact.enactmentEnded + ","
						+ ProcEnact.finalOutputsDataBindingId + ","
						+ ProcEnact.initialInputsDataBindingId + ","
						+ ProcEnact.ProcessorEnactment + "." + ProcEnact.processorId + " AS procId,"
						+ ProcEnact.processIdentifier + ","
						+ ProcEnact.processEnactmentId + ","
						+ ProcEnact.parentProcessorEnactmentId + ","
						+ ProcEnact.workflowRunId + ","						
						+ ProcEnact.iteration + ","
						+ "Processor.processorName" + " FROM "
						+ ProcEnact.ProcessorEnactment
						+ " INNER JOIN " + "Processor" + " ON "
						+ ProcEnact.ProcessorEnactment + "."+ ProcEnact.processorId 
						+ " = " + "Processor.processorId" + " WHERE "
						+ ProcEnact.workflowRunId + "=? ");
		
		if (processorName != null) {
			// Specific processor
			query.append(" AND Processor.processorName=? ");
		}
		if ((parentProcessorEnactmentIds == null || parentProcessorEnactmentIds.isEmpty()) && processorName != null) {
			// null - ie. top level
			query.append(" AND " + ProcEnact.parentProcessorEnactmentId + " IS NULL");
		} else if (parentProcessorEnactmentIds != null) {
			// not null, ie. inside nested workflow
			query.append(" AND " + ProcEnact.parentProcessorEnactmentId + " IN (");
			for (int i=0; i<parentProcessorEnactmentIds.size(); i++) {
				query.append('?');
				if (i < (parentProcessorEnactmentIds.size()-1)) {
					query.append(',');
				}
			}
			query.append(')');
		}
		
		
		ArrayList<ProcessorEnactment> procEnacts = new ArrayList<ProcessorEnactment>();

		PreparedStatement statement;
		Connection connection = null;
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query.toString());
			int pos = 1;
			statement.setString(pos++, workflowRunId);
			if (processorName != null) {
				statement.setString(pos++, processorName);
			}
			if (parentProcessorEnactmentIds != null) {
				for (String parentId : parentProcessorEnactmentIds) {
					statement.setString(pos++, parentId);
				}
			}
			ResultSet resultSet = statement.executeQuery();
			while (resultSet.next()) {
				ProcessorEnactment procEnact = readProcessorEnactment(resultSet);
				procEnacts.add(procEnact);	
			}			
		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}		
		return procEnacts;
	}
	

	private ProcessorEnactment readProcessorEnactment(ResultSet resultSet) throws SQLException {
		ProvenanceConnector.ProcessorEnactmentTable ProcEnact = ProvenanceConnector.ProcessorEnactmentTable.ProcessorEnactment;
		
		Timestamp enactmentStarted = resultSet.getTimestamp(ProcEnact.enactmentStarted.name());
		Timestamp enactmentEnded = resultSet.getTimestamp(ProcEnact.enactmentEnded.name());
		//String pName = resultSet.getString("processorName");
		String finalOutputsDataBindingId = resultSet.getString(ProcEnact.finalOutputsDataBindingId.name());
		String initialInputsDataBindingId = resultSet.getString(ProcEnact.initialInputsDataBindingId.name());
	
		String iteration = resultSet.getString(ProcEnact.iteration.name());
		String processorId = resultSet.getString("procId");
		String processIdentifier = resultSet.getString(ProcEnact.processIdentifier.name());
		String processEnactmentId = resultSet.getString(ProcEnact.processEnactmentId.name());
		String parentProcessEnactmentId = resultSet.getString(ProcEnact.parentProcessorEnactmentId.name());
		String workflowRunId = resultSet.getString(ProcEnact.workflowRunId.name());
		
		ProcessorEnactment procEnact = new ProcessorEnactment();
		procEnact.setEnactmentEnded(enactmentEnded);
		procEnact.setEnactmentStarted(enactmentStarted);
		procEnact.setFinalOutputsDataBindingId(finalOutputsDataBindingId);
		procEnact.setInitialInputsDataBindingId(initialInputsDataBindingId);
		procEnact.setIteration(iteration);
		procEnact.setParentProcessorEnactmentId(parentProcessEnactmentId);
		procEnact.setProcessEnactmentId(processEnactmentId);
		procEnact.setProcessIdentifier(processIdentifier);
		procEnact.setProcessorId(processorId);
		procEnact.setWorkflowRunId(workflowRunId);
		return procEnact;
	}

	public ProcessorEnactment getProcessorEnactment(String processorEnactmentId) {
		ProvenanceConnector.ProcessorEnactmentTable ProcEnact = ProvenanceConnector.ProcessorEnactmentTable.ProcessorEnactment;		
		String query  = 
				"SELECT " + ProcEnact.enactmentStarted + ","
						+ ProcEnact.enactmentEnded + ","
						+ ProcEnact.finalOutputsDataBindingId + ","
						+ ProcEnact.initialInputsDataBindingId + ","
						+ ProcEnact.ProcessorEnactment + "." 
						+ ProcEnact.processorId + " AS procId,"
						+ ProcEnact.processIdentifier + ","
						+ ProcEnact.workflowRunId + ","						
						+ ProcEnact.processEnactmentId + ","
						+ ProcEnact.parentProcessorEnactmentId + ","						
						+ ProcEnact.iteration 
						+ " FROM "
						+ ProcEnact.ProcessorEnactment
						+ " WHERE "
						+ ProcEnact.processEnactmentId + "=?";
					
		PreparedStatement statement;
		Connection connection = null;
		ProcessorEnactment procEnact = null;
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query);
			statement.setString(1, processorEnactmentId);
			ResultSet resultSet = statement.executeQuery();
			if (! resultSet.next()) {
				logger.warn("Could not find ProcessorEnactment processEnactmentId=" + processorEnactmentId);
				
				return null;
			}
			procEnact = readProcessorEnactment(resultSet);
			if (resultSet.next()) {
				logger.error("Found more than one ProcessorEnactment processEnactmentId=" + processorEnactmentId);
				return null;
			}
		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}
		return procEnact;
	}
	
	
	@SuppressWarnings("static-access")
	public ProcessorEnactment getProcessorEnactmentByProcessId(String workflowRunId,
			String processIdentifier, String iteration) {
ProvenanceConnector.ProcessorEnactmentTable ProcEnact = ProvenanceConnector.ProcessorEnactmentTable.ProcessorEnactment;		
		String query  = 
				"SELECT " + ProcEnact.enactmentStarted + ","
						+ ProcEnact.enactmentEnded + ","
						+ ProcEnact.finalOutputsDataBindingId + ","
						+ ProcEnact.initialInputsDataBindingId + ","
						+ ProcEnact.ProcessorEnactment + "." + ProcEnact.processorId + " AS procId,"
						+ ProcEnact.processIdentifier + ","
						+ ProcEnact.workflowRunId + ","						
						+ ProcEnact.processEnactmentId + ","
						+ ProcEnact.parentProcessorEnactmentId + ","						
						+ ProcEnact.iteration
						+ " FROM "
						+ ProcEnact.ProcessorEnactment
						+ " WHERE "
						+ ProcEnact.workflowRunId + "=?"
						+ " AND " + ProcEnact.processIdentifier + "=?"
						+ " AND " + ProcEnact.iteration+"=?";

		
		PreparedStatement statement;
		Connection connection = null;
		ProcessorEnactment procEnact = null;
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query);
			statement.setString(1, workflowRunId);
			statement.setString(2, processIdentifier);
			statement.setString(3, iteration);

			ResultSet resultSet = statement.executeQuery();
			String debugString = "ProcessorEnactment runId=" + workflowRunId
					+ " processIdentifier=" + processIdentifier + " iteration="
					+ iteration;
			if (! resultSet.next()) {
				logger.warn("Could not find " +
						debugString);
				return null;
			}
			procEnact = readProcessorEnactment(resultSet);
			if (resultSet.next()) {
				logger.error("Found more than one " + debugString);
				return null;
			}
		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}
		return procEnact;
	}

	public Map<Port, String> getDataBindings(String dataBindingId) {
		HashMap<Port, String> dataBindings = new HashMap<Port, String>();
		String query = "SELECT " 
				+ DataBindingTable.t2Reference + ","
				+ "Port.portId AS portId," 
				+ "Port.processorName,"
				+ "Port.processorId,"
				+ "Port.isInputPort,"
				+ "Port.portName," 
				+ "Port.depth,"
				+ "Port.resolvedDepth," 
				+ "Port.workflowId"
				+ " FROM " + DataBindingTable.DataBinding
				+ " INNER JOIN " + "Port" + " ON " 
				+ " Port.portId=" + DataBindingTable.DataBinding + "." + DataBindingTable.portId
				+ " WHERE " + DataBindingTable.dataBindingId + "=?";
		PreparedStatement statement;
		Connection connection = null;
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query);
			statement.setString(1, dataBindingId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String t2Ref = rs.getString(DataBindingTable.t2Reference.name());
				
				Port port = new Port();
				port.setWorkflowId(rs.getString("workflowId"));
				port.setInputPort(rs.getBoolean("isInputPort"));				
				port.setIdentifier(rs.getString("portId"));
				port.setProcessorName(rs.getString("processorName"));
				port.setProcessorId(rs.getString("processorId"));
				port.setPortName(rs.getString("portName"));
				port.setDepth(rs.getInt("depth"));
				if (rs.getString("resolvedDepth") != null) {
					port.setResolvedDepth(rs.getInt("resolvedDepth"));
				}
				dataBindings.put(port, t2Ref);
			}
		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);			
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}
		return dataBindings;		
	}
	

	public List<Port> getAllPortsInDataflow(String workflowID) {
		Workflow w = getWorkflow(workflowID);

		Map<String, String> queryConstraints = new HashMap<String, String>();
		queryConstraints.put("V.workflowId", workflowID);
		try {
			return getPorts(queryConstraints);
		} catch (SQLException e) {
			logger.error("Problem getting ports for dataflow: " + workflowID, e);
		}
		return null;
	}

	public List<Port> getPortsForDataflow(String workflowID) {
		Workflow w = getWorkflow(workflowID);

		Map<String, String> queryConstraints = new HashMap<String, String>();
		queryConstraints.put("V.workflowId", workflowID);
		queryConstraints.put("processorName", w.getExternalName());

		try {
			return getPorts(queryConstraints);
		} catch (SQLException e) {
			logger.error("Problem getting ports for dataflow: " + workflowID, e);
		}
		return null;
	}

	public List<Port> getPortsForProcessor(String workflowID,
			String processorName) {
		Map<String, String> queryConstraints = new HashMap<String, String>();
		queryConstraints.put("V.workflowId", workflowID);
		queryConstraints.put("processorName", processorName);

		try {
			return getPorts(queryConstraints);
		} catch (SQLException e) {
			logger.error("Problem getting ports for processor: " + processorName + " worflow: " + workflowID, e);
		}
		return null;
	}

	@SuppressWarnings("static-access")
	public DataflowInvocation getDataflowInvocation(String workflowRunId) {
		net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable DI = 
			net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable.DataflowInvocation;
		String query = "SELECT " + 
				  DI.dataflowInvocationId + ","
				+ DI.inputsDataBinding + "," 
				+ DI.invocationEnded + ","
				+ DI.invocationStarted + "," 
				+ DI.outputsDataBinding + ","
				+ DI.parentProcessorEnactmentId + ","  
				+ DI.workflowId + "," 
				+ DI.workflowRunId + ","
				+ DI.completed
				+ " FROM "
				+ DI.DataflowInvocation +
				" WHERE "
				+ DI.parentProcessorEnactmentId + " IS NULL AND "
				+ DI.workflowRunId + "=?";
		PreparedStatement statement;
		Connection connection = null;
		DataflowInvocation dataflowInvocation = null;
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query);
			statement.setString(1, workflowRunId);
			ResultSet rs = statement.executeQuery();
			if (! rs.next()) {
				logger.warn("Could not find DataflowInvocation for workflowRunId=" + workflowRunId);
				return null;
			}
			dataflowInvocation = new DataflowInvocation();
			dataflowInvocation.setDataflowInvocationId(rs.getString(DI.dataflowInvocationId.name()));
			dataflowInvocation.setInputsDataBindingId(rs.getString(DI.inputsDataBinding.name()));			
			dataflowInvocation.setInvocationEnded(rs.getTimestamp(DI.invocationEnded.name()));
			dataflowInvocation.setInvocationStarted(rs.getTimestamp(DI.invocationStarted.name()));
			dataflowInvocation.setOutputsDataBindingId(rs.getString(DI.outputsDataBinding.name()));
			dataflowInvocation.setParentProcessorEnactmentId(rs.getString(DI.parentProcessorEnactmentId.name()));
			dataflowInvocation.setWorkflowId(rs.getString(DI.workflowId.name()));
			dataflowInvocation.setWorkflowRunId(rs.getString(DI.workflowRunId.name()));
			dataflowInvocation.setCompleted(rs.getBoolean(DI.completed.name()));
			if (rs.next()) {
				logger.error("Found more than one DataflowInvocation for workflowRunId=" + workflowRunId);
				return null;
			}

		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);			
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}
		return dataflowInvocation;
	}

	public DataflowInvocation getDataflowInvocation(
			ProcessorEnactment processorEnactment) {
		net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable DI = 
			net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable.DataflowInvocation;
		String query = "SELECT " + 
				  DI.dataflowInvocationId + ","
				+ DI.inputsDataBinding + "," 
				+ DI.invocationEnded + ","
				+ DI.invocationStarted + "," 
				+ DI.outputsDataBinding + ","
				+ DI.parentProcessorEnactmentId + ","  
				+ DI.workflowId + ","
				+ DI.workflowRunId + ","
				+ DI.completed 
				+ " FROM "
				+ DI.DataflowInvocation +
				" WHERE "
				+ DI.parentProcessorEnactmentId + "=?";
		PreparedStatement statement;
		Connection connection = null;
		DataflowInvocation dataflowInvocation = null;
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query);
			statement.setString(1, processorEnactment.getProcessEnactmentId());
			ResultSet rs = statement.executeQuery();
			if (! rs.next()) {
				logger.warn("Could not find DataflowInvocation for processorEnactmentId=" + processorEnactment.getProcessEnactmentId());
				return null;
			}
			dataflowInvocation = new DataflowInvocation();
			dataflowInvocation.setDataflowInvocationId(rs.getString(DI.dataflowInvocationId.name()));
			dataflowInvocation.setInputsDataBindingId(rs.getString(DI.inputsDataBinding.name()));			
			dataflowInvocation.setInvocationEnded(rs.getTimestamp(DI.invocationEnded.name()));
			dataflowInvocation.setInvocationStarted(rs.getTimestamp(DI.invocationStarted.name()));
			dataflowInvocation.setOutputsDataBindingId(rs.getString(DI.outputsDataBinding.name()));
			dataflowInvocation.setParentProcessorEnactmentId(rs.getString(DI.parentProcessorEnactmentId.name()));
			dataflowInvocation.setWorkflowId(rs.getString(DI.workflowId.name()));
			dataflowInvocation.setWorkflowRunId(rs.getString(DI.workflowRunId.name()));
			dataflowInvocation.setCompleted(rs.getBoolean(DI.completed.name()));
			
			if (rs.next()) {
				logger.error("Found more than one DataflowInvocation for processorEnactmentId=" + processorEnactment.getProcessEnactmentId());
				return null;
			}
			
		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);			
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}
		return dataflowInvocation;
	}
	
	@SuppressWarnings("static-access")
	public List<DataflowInvocation> getDataflowInvocations(String workflowRunId) {
		net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable DI = 
			net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable.DataflowInvocation;
		String query = "SELECT " + 
				  DI.dataflowInvocationId + ","
				+ DI.inputsDataBinding + "," 
				+ DI.invocationEnded + ","
				+ DI.invocationStarted + "," 
				+ DI.outputsDataBinding + ","
				+ DI.parentProcessorEnactmentId + ","  
				+ DI.workflowId + "," 
				+ DI.workflowRunId + ","
				+ DI.completed
				+ " FROM "
				+ DI.DataflowInvocation +
				" WHERE "
				+ DI.workflowRunId + "=?";
		PreparedStatement statement;
		Connection connection = null;
		List<DataflowInvocation> invocations = new ArrayList<DataflowInvocation>();
		try {
			connection = getConnection();
			statement = connection.prepareStatement(query);
			statement.setString(1, workflowRunId);
			ResultSet rs = statement.executeQuery();
			if (! rs.next()) {
				logger.warn("Could not find DataflowInvocation for workflowRunId=" + workflowRunId);
				return null;
			}
			DataflowInvocation dataflowInvocation = new DataflowInvocation();
			dataflowInvocation.setDataflowInvocationId(rs.getString(DI.dataflowInvocationId.name()));
			dataflowInvocation.setInputsDataBindingId(rs.getString(DI.inputsDataBinding.name()));			
			dataflowInvocation.setInvocationEnded(rs.getTimestamp(DI.invocationEnded.name()));
			dataflowInvocation.setInvocationStarted(rs.getTimestamp(DI.invocationStarted.name()));
			dataflowInvocation.setOutputsDataBindingId(rs.getString(DI.outputsDataBinding.name()));
			dataflowInvocation.setParentProcessorEnactmentId(rs.getString(DI.parentProcessorEnactmentId.name()));
			dataflowInvocation.setWorkflowId(rs.getString(DI.workflowId.name()));
			dataflowInvocation.setWorkflowRunId(rs.getString(DI.workflowRunId.name()));
			dataflowInvocation.setCompleted(rs.getBoolean(DI.completed.name()));
			invocations.add(dataflowInvocation);
		} catch (SQLException e) {
			logger.warn("Could not execute query " + query, e);
		} catch (InstantiationException e) {
			logger.warn("Could not get database connection", e);
		} catch (IllegalAccessException e) {
			logger.warn("Could not get database connection", e);
		} catch (ClassNotFoundException e) {
			logger.warn("Could not get database connection", e);			
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Could not close connection", e);
				}
			}
		}
		return invocations;
	}

	public List<Collection> getCollectionsForRun(String wfInstanceID) {

		PreparedStatement ps = null;
		Connection c = null;

		ArrayList<Collection> result = new ArrayList<Collection>();

		try {
			c = getConnection();
			ps = c.prepareStatement(
			"SELECT * FROM Collection C WHERE workflowRunId = ?");
			ps.setString(1, wfInstanceID);

			boolean success = ps.execute();
			if (success) {
				ResultSet rs = ps.getResultSet();

				while (rs.next()) {
					Collection coll = new Collection();
					coll.setCollId(rs.getString(CollectionTable.collID.name()));
					coll.setParentIdentifier(rs.getString(CollectionTable.parentCollIDRef.name()));
					coll.setWorkflowRunIdentifier(rs.getString(CollectionTable.workflowRunId.name()));
					coll.setProcessorName(rs.getString(CollectionTable.processorNameRef.name()));
					coll.setPortName(rs.getString(CollectionTable.portName.name()));
					coll.setIteration(rs.getString(CollectionTable.iteration.name()));
					result.add(coll);
				}
			}				
		} catch (Exception e) {
			logger.warn("Could not execute query", e);
		} finally {
			if (c != null) {
				try {
					c.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
		return result;		
	}


}


