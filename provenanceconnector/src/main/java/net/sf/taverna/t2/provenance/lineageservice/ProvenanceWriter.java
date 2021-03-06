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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.sf.taverna.t2.provenance.connector.JDBCConnector;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.ActivityTable;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataBindingTable;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.DataflowInvocationTable;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.ProcessorEnactmentTable;
import net.sf.taverna.t2.provenance.connector.ProvenanceConnector.ServiceInvocationTable;
import net.sf.taverna.t2.provenance.lineageservice.utils.NestedListNode;
import net.sf.taverna.t2.provenance.lineageservice.utils.Port;
import net.sf.taverna.t2.provenance.lineageservice.utils.PortBinding;
import net.sf.taverna.t2.provenance.lineageservice.utils.ProvenanceProcessor;

import org.apache.log4j.Logger;

/**
 * Handles all the writing out of provenance items to the database layer. Uses
 * standard SQL so all specific instances of this class can extend this writer
 * to handle all of the db writes
 * 
 * @author Paolo Missier
 * @author Ian Dunlop
 * @author Stuart Owen
 * 
 */
public class ProvenanceWriter {

	protected static Logger logger = Logger.getLogger(ProvenanceWriter.class);    
	protected int cnt; // counts number of calls to PortBinding
    protected ProvenanceQuery pq = null;

	public Connection getConnection() throws SQLException {
		return JDBCConnector.getConnection();
	}
		
	public void closeCurrentModel() {
		
	}	



	/**
	 * add each Port as a row into the Port DB table
	 * <strong>note: no static port type available as part of the
	 * dataflow...</strong>
	 *
	 * @param ports
	 * @param wfId
	 * @throws SQLException
	 */
	public void addPorts(List<Port> ports, String wfId) throws SQLException {
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			 "INSERT INTO Port (portName, processorName, isInputPort, depth, workflowId, portId, processorId) VALUES(?,?,?,?,?,?,?)");
			for (Port v : ports) {


				ps.setString(1, v.getPortName());
				ps.setString(2, v.getProcessorName());
				ps.setBoolean(3, v.isInputPort());
				int depth = v.getDepth() >= 0 ? v.getDepth() : 0;
				ps.setInt(4, depth);
				ps.setString(5, wfId);
				ps.setString(6, v.getIdentifier());
				ps.setString(7, v.getProcessorId());

				try {
					ps.executeUpdate();
				} catch (Exception e) {
					logger.warn("Could not insert var " + v.getPortName(), e);
				}

			}
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

	}

	@SuppressWarnings("static-access")
	public void addDataflowInvocation(
			net.sf.taverna.t2.provenance.lineageservice.utils.DataflowInvocation invocation)
			throws SQLException {
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			DataflowInvocationTable DI = DataflowInvocationTable.DataflowInvocation;
			String sql = "INSERT INTO " + DI.DataflowInvocation + "("
					+ DI.dataflowInvocationId + "," + DI.workflowId + ","
					+ DI.invocationStarted + "," + DI.invocationEnded + ","
					+ DI.inputsDataBinding + "," + DI.outputsDataBinding + ","
					+ DI.parentProcessorEnactmentId + "," + DI.workflowRunId + "," + DI.completed
					+ ") " + " VALUES(?,?,?,?,?,?,?,?,?)";
			ps = connection.prepareStatement(sql);
			ps.setString(1, invocation.getDataflowInvocationId());
			ps.setString(2, invocation.getWorkflowId());
			ps.setTimestamp(3, invocation.getInvocationStarted());
			ps.setTimestamp(4, invocation.getInvocationEnded());
			ps.setString(5, invocation.getInputsDataBindingId());
			ps.setString(6, invocation.getOutputsDataBindingId());
			ps.setString(7, invocation.getParentProcessorEnactmentId());
			ps.setString(8, invocation.getWorkflowRunId());
			ps.setBoolean(9, invocation.getCompleted());
			ps.executeUpdate();
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}
	
	/**
	 * inserts one row into the ARC DB table
	 *
	 * @param sourcePort
	 * @param destinationPort
	 * @param workflowId
	 */
	public void addDataLink(Port sourcePort, Port destinationPort, String workflowId)
	throws SQLException {
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO Datalink (workflowId, sourceProcessorName, " +
			" sourcePortName, destinationProcessorName, destinationPortName," +
			" sourcePortId, destinationPortId) " +
			"VALUES(?,?,?,?,?,?,?)");
			ps.setString(1, workflowId);
			ps.setString(2, sourcePort.getProcessorName());
			ps.setString(3, sourcePort.getPortName());
			ps.setString(4, destinationPort.getProcessorName());
			ps.setString(5, destinationPort.getPortName());
			ps.setString(6, sourcePort.getIdentifier());
			ps.setString(7, destinationPort.getIdentifier());
			
			int result = ps.executeUpdate();

		} finally {
			if (connection != null) {
				connection.close();
			}
		}

	}

	public void addDataBinding(net.sf.taverna.t2.provenance.lineageservice.utils.DataBinding dataBinding) throws SQLException {
		
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement("INSERT INTO "
					+ DataBindingTable.DataBinding + "("
					+ DataBindingTable.dataBindingId + ","
					+ DataBindingTable.portId + ","
					+ DataBindingTable.t2Reference + ","
					+ DataBindingTable.workflowRunId 
					+ ") VALUES(?,?,?,?)");
			
			ps.setString(1, dataBinding.getDataBindingId());
			ps.setString(2, dataBinding.getPort().getIdentifier());
			ps.setString(3, dataBinding.getT2Reference());
			ps.setString(4, dataBinding.getWorkflowRunId());
			ps.executeUpdate();			
			if (logger.isDebugEnabled()) {
				logger.debug("adding DataBinding:\n "+dataBinding);
			}
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Can't close connection", e);
				}
			}
		}
		
	}


	public void addWFId(String wfId) throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO Workflow (workflowId) VALUES (?)");
			ps.setString(1, wfId);
			ps.executeUpdate();

		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	public void addWFId(String wfId, String parentWorkflowId, String externalName, Blob dataflow) throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO Workflow (workflowId, parentWorkflowId, externalName, dataflow) VALUES (?,?,?, ?)");
			ps.setString(1, wfId);
			ps.setString(2, parentWorkflowId);
			ps.setString(3, externalName);
			ps.setBlob(4, dataflow);

			ps.executeUpdate();

	
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	public void addWorkflowRun(String wfId, String workflowRunId)
	throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO WorkflowRun (workflowRunId, workflowId) VALUES (?,?)");

			ps.setString(1, workflowRunId);
			ps.setString(2, wfId);

			ps.executeUpdate();
	
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * insert new processor into the provenance DB
	 *
	 * @param name
	 * @throws SQLException
	 */
	public ProvenanceProcessor addProcessor(String name, String wfID, boolean isTopLevel) throws SQLException {
		ProvenanceProcessor provProc = new ProvenanceProcessor();
		provProc.setIdentifier(UUID.randomUUID().toString());
		provProc.setProcessorName(name);
		provProc.setWorkflowId(wfID);
		provProc.setTopLevelProcessor(isTopLevel);
		// pType is unknown
		addProcessor(provProc);
		return provProc;
	}

	/**
	 * add a processor to the static portion of the DB with given name, type and
	 * workflowId scope
	 *
	 * @param name
	 * @param type
	 * @param workflowId
	 * @throws SQLException
	 */
	

	public void addProcessor(ProvenanceProcessor provProc) 	throws SQLException {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO Processor (processorName, firstActivityClass, workflowId, isTopLevel, processorId) VALUES (?,?,?,?,?)");

			ps.setString(1, provProc.getProcessorName());
			ps.setString(2, provProc.getFirstActivityClassName());
			ps.setString(3, provProc.getWorkflowId());
			ps.setBoolean(4, provProc.isTopLevelProcessor());
			ps.setString(5, provProc.getIdentifier());

			ps.executeUpdate();
	
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}
	
	public void addProcessorEnactment(net.sf.taverna.t2.provenance.lineageservice.utils.ProcessorEnactment enactment) throws SQLException {
	
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement("INSERT INTO "
					+ ProcessorEnactmentTable.ProcessorEnactment + "("
					+ ProcessorEnactmentTable.processEnactmentId + ","
					+ ProcessorEnactmentTable.workflowRunId + ","
					+ ProcessorEnactmentTable.processorId + ","
					+ ProcessorEnactmentTable.processIdentifier + ","
					+ ProcessorEnactmentTable.iteration + ","
					+ ProcessorEnactmentTable.parentProcessorEnactmentId + "," 
					+ ProcessorEnactmentTable.enactmentStarted + ","
					+ ProcessorEnactmentTable.enactmentEnded + ","
					+ ProcessorEnactmentTable.initialInputsDataBindingId + ","
					+ ProcessorEnactmentTable.finalOutputsDataBindingId
					+ ") VALUES(?,?,?,?,?,?,?,?,?,?)");
			ps.setString(1, enactment.getProcessEnactmentId());
			ps.setString(2, enactment.getWorkflowRunId());
			ps.setString(3, enactment.getProcessorId());
			ps.setString(4, enactment.getProcessIdentifier());
			ps.setString(5, enactment.getIteration());
			ps.setString(6, enactment.getParentProcessorEnactmentId());
			ps.setTimestamp(7, enactment.getEnactmentStarted());
			ps.setTimestamp(8, enactment.getEnactmentEnded());
			ps.setString(9, enactment.getInitialInputsDataBindingId());
			ps.setString(10, enactment.getFinalOutputsDataBindingId());
			ps.executeUpdate();
			
			if (logger.isDebugEnabled()) {
				logger.debug("adding ProcessorEnactment binding:\n "+enactment);
			}
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.warn("Can't close connection", e);
				}
			}
		}
	}

	public String addCollection(String processorId, String collId,
			String parentCollectionId, String iteration, String portName,
			String dataflowId) throws SQLException {
		String newParentCollectionId = null;

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO Collection (processorNameRef, workflowRunId, portName, iteration, parentCollIdRef, collId) VALUES(?,?,?,?,?,?)");

			if (parentCollectionId == null) {
				// this is a top-level list
				parentCollectionId = "TOP";
			}

			newParentCollectionId = collId;

			ps.setString(1, processorId);
			ps.setString(2, dataflowId);
			ps.setString(3, portName);
			ps.setString(4, iteration);
			ps.setString(5, parentCollectionId);
			ps.setString(6, collId);

			ps.executeUpdate();

		
		} finally {
			if (connection != null) {
				connection.close();
			}
		}


		return newParentCollectionId;
	}

	public void addData(String dataRef, String wfInstanceId, Object data)
	throws SQLException {
		
		Connection connection = null;

		try {
			connection = getConnection();
			PreparedStatement ps = null;
			ps = connection.prepareStatement(
			"INSERT INTO Data (dataReference,wfInstanceID,data) VALUES (?,?,?)");
			ps.setString(1, dataRef);
			ps.setString(2, wfInstanceId);
			ps.setString(3, (String) data);

			ps.executeUpdate();

			cnt++;

			logger.debug("addData executed on data value from char: "+String.valueOf(data));
			
		} catch (SQLException e) {
			// the same ID will come in several times -- duplications are
			// expected, don't panic		
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}


		
	/**
	 * OBSOLETE<p/>
	 * adds (dataRef, data) pairs to the Data table (only for string data)
	 */
	public void addData(String dataRef, String wfInstanceId, byte[] data)
	throws SQLException {

		Connection connection = null;

		try {
			connection = getConnection();
			PreparedStatement ps = null;
			ps = connection.prepareStatement(
			"INSERT INTO Data (dataReference,wfInstanceID,data) VALUES (?,?,?)");
			ps.setString(1, dataRef);
			ps.setString(2, wfInstanceId);
			ps.setBytes(3, data);

			ps.executeUpdate();

			cnt++;

			logger.debug("addData executed on data value from char: "+String.valueOf(data));
			
		} catch (SQLException e) {
			// the same ID will come in several times -- duplications are
			// expected, don't panic	
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	public void addPortBinding(PortBinding vb) throws SQLException {
		PreparedStatement ps = null;
		Connection connection = null;

		logger.debug("START addVarBinding proc "+vb.getProcessorName()+" port "+vb.getPortName());

		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"INSERT INTO PortBinding (workflowId, processorNameRef, workflowRunId, portName, valueType, value, ref, collIdRef, iteration,positionInColl) VALUES(?,?,?,?,?,?,?,?,?,?)");

			ps.setString(1, vb.getWorkflowId());
			ps.setString(2, vb.getProcessorName());
			ps.setString(3, vb.getWorkflowRunId());
			ps.setString(4, vb.getPortName());
			ps.setString(5, vb.getValueType());
			ps.setString(6, vb.getValue());
			ps.setString(7, vb.getReference());
			ps.setString(8, vb.getCollIDRef());
			ps.setString(9, vb.getIteration());
			ps.setInt(10, vb.getPositionInColl());

			
			logger.debug("addVarBinding query: \n"+ps.toString());
			ps.executeUpdate();
			logger.debug("insert done");

			logger.debug("COMPLETE addVarBinding proc "+vb.getProcessorName()+" port "+vb.getPortName());

			cnt++;  // who uses this?

//		} catch (SQLException e) {
//			logger.warn("Port binding insert failed", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * persists var v back to DB
	 *
	 * @param v
	 * @throws SQLException
	 */
	public void updatePort(Port v) throws SQLException {
		PreparedStatement ps = null;
        Connection connection = null;

		try {
			connection = getConnection();
			ps = connection
					.prepareStatement("UPDATE Port SET isInputPort=?, depth=?,"
							+ "resolvedDepth=?, iterationStrategyOrder=? "
							+ "WHERE portId=?");
			int i = v.isInputPort() ? 1 : 0;
			ps.setInt(1, i);
			ps.setInt(2, v.getDepth());
			if (v.isResolvedDepthSet()) {
				ps.setInt(3, v.getResolvedDepth());
			} else {
				ps.setString(3, null);
			}
			ps.setInt(4, v.getIterationStrategyOrder());
			ps.setString(5, v.getIdentifier());
			ps.execute();

		} finally {
			if (connection != null)
				connection.close();
		}

	}

	public void updateProcessorEnactment(net.sf.taverna.t2.provenance.lineageservice.utils.ProcessorEnactment enactment) {
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement("UPDATE "
					+ ProcessorEnactmentTable.ProcessorEnactment + " SET "
					+ ProcessorEnactmentTable.finalOutputsDataBindingId + "=?, "
					+ ProcessorEnactmentTable.enactmentEnded+ "=?"
					+ " WHERE " + ProcessorEnactmentTable.processEnactmentId + "=?");

			ps.setString(1, enactment.getFinalOutputsDataBindingId());				
			ps.setTimestamp(2, enactment.getEnactmentEnded());
			ps.setString(3, enactment.getProcessEnactmentId());

			ps.executeUpdate();


		} catch (SQLException e) {
			logger.warn("****  insert failed for query ", e);
		
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
	}
	
	public void updatePortBinding(PortBinding vb) {

		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"UPDATE PortBinding SET valueType = ?, value = ?, ref = ?, collIdRef = ?, positionInColl = ? "+
			"WHERE portName = ? AND workflowRunId = ? AND processorNameRef = ? AND iteration = ?");

			ps.setString(1, vb.getValueType());
			ps.setString(2, vb.getValue());
			ps.setString(3, vb.getReference());
			ps.setString(4, vb.getCollIDRef());
			ps.setInt(5, vb.getPositionInColl());
			ps.setString(6, vb.getPortName());
			ps.setString(7, vb.getWorkflowRunId());
			ps.setString(8, vb.getProcessorName());
			ps.setString(9, vb.getIteration());

			ps.executeUpdate();

			cnt++;

		} catch (SQLException e) {
			logger.warn("****  insert failed for query ", e);
		
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}
	}

	public void replaceCollectionRecord(NestedListNode nln, String prevPName,
			String prevPortName) {

		// Statement stmt;
		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
					"DELETE FROM Collection WHERE collId = ? and workflowRunId = ?" + " and portName = ? and processorNameRef = ? and iteration = ?");
			ps.setString(1, nln.getCollectionT2Reference());
			ps.setString(2, nln.getWorkflowRunId());
			ps.setString(3, prevPortName);
			ps.setString(4, prevPName);
			ps.setString(5, nln.getIteration());

			int result = ps.executeUpdate();

		} catch (SQLException e) {
			logger.warn("Error replacing collection record", e);
	
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection", ex);
				}
			}
		}

		try {
			addCollection(prevPName, nln.getCollectionT2Reference(), nln.getParentCollIdRef(),
					nln.getIteration(), prevPortName, nln.getWorkflowRunId());
		} catch (SQLException e) {
			logger.warn("Collection insert failed", e);
		}
	}

	/**
	 * deletes DB contents for the static structures -- called prior to each run
	 *
	 * @throws SQLException
	 */
	public void clearDBStatic() throws SQLException {
		String q = null;

		Statement stmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			stmt = connection.createStatement();
			q = "DELETE FROM Workflow";

			stmt.executeUpdate(q);

			q = "DELETE FROM Processor";
			stmt.executeUpdate(q);

			q = "DELETE FROM Datalink";
			stmt.executeUpdate(q);

			q = "DELETE FROM Port";
			stmt.executeUpdate(q);
			
			q = "DELETE FROM " + ActivityTable.Activity;
			stmt.executeUpdate(q);
			
		} catch (SQLException e) {
			logger.warn("Could not clear static database", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		logger.info("DB cleared STATIC");
	}

	/**
	 * deletes DB contents for the static structures -- called prior to each run
	 *
	 * @throws SQLException
	 */
	public void clearDBStatic(String wfID) throws SQLException {
		String q = null;


		PreparedStatement ps = null;
		Connection connection = null;
		try {
			connection = getConnection();
			ps = connection.prepareStatement(
			"DELETE FROM Workflow WHERE workflowId = ?");
			ps.setString(1, wfID);
			ps.executeUpdate();
			ps = connection.prepareStatement(
			"DELETE FROM Processor WHERE workflowId = ?");
			ps.setString(1, wfID);
			ps.executeUpdate();
			ps = connection.prepareStatement(
			"DELETE FROM Datalink WHERE workflowId = ?");
			ps.setString(1, wfID);
			ps.executeUpdate();
			ps = connection.prepareStatement(
			"DELETE FROM Port WHERE workflowId = ?");
			ps.setString(1, wfID);
			ps.executeUpdate();
			
			q = "DELETE FROM " + ActivityTable.Activity + " WHERE " + ActivityTable.workflowId + "=?";
			ps = connection.prepareStatement(q);
			ps.setString(1, wfID);
			ps.executeUpdate();

	
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		logger.info("DB cleared STATICfor wfID " + wfID);
	}


	public Set<String>  clearDBDynamic() throws SQLException {
		return clearDBDynamic(null);
	}

	/**
	 * deletes DB contents for all runs -- for testing purposes
	 *
	 * @throws SQLException
	 */
	public Set<String> clearDBDynamic(String runID) throws SQLException {
		String q = null;

		Set<String>  refsToRemove = collectValueReferences(runID);  // collect all relevant refs from PortBinding and Collection

		Connection connection = null;
		PreparedStatement ps = null;
		try {
			connection = getConnection();

			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM WorkflowRun WHERE workflowRunId = ?");
				ps.setString(1, runID);
			} else 
				ps = connection.prepareStatement("DELETE FROM WorkflowRun");
			ps.executeUpdate();

			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM PortBinding WHERE workflowRunId = ?");
				ps.setString(1, runID);
			} else 
				ps = connection.prepareStatement("DELETE FROM PortBinding");
			ps.executeUpdate();

			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM Collection WHERE workflowRunId = ?");
				ps.setString(1, runID);
			} else 
				ps = connection.prepareStatement("DELETE FROM Collection");
			ps.executeUpdate();
			
			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM "
						+ DataflowInvocationTable.DataflowInvocation + " WHERE "
						+ DataflowInvocationTable.workflowRunId + "=?");
				ps.setString(1, runID);
			} else
				ps = connection.prepareStatement("DELETE FROM "
						+ DataflowInvocationTable.DataflowInvocation);
			ps.executeUpdate();
			
			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM "
						+ ServiceInvocationTable.ServiceInvocation + " WHERE "
						+ ServiceInvocationTable.workflowRunId + "=?");
				ps.setString(1, runID);
			} else
				ps = connection.prepareStatement("DELETE FROM "
						+ ServiceInvocationTable.ServiceInvocation);
			ps.executeUpdate();
			
			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM "
						+ ProcessorEnactmentTable.ProcessorEnactment + " WHERE "
						+ ProcessorEnactmentTable.workflowRunId + "=?");
				ps.setString(1, runID);
			} else
				ps = connection
						.prepareStatement("DELETE FROM "
								+ ProcessorEnactmentTable.ProcessorEnactment);
			ps.executeUpdate();
			
			
			
			if (runID != null) {
				ps = connection.prepareStatement("DELETE FROM "
						+ DataBindingTable.DataBinding + " WHERE "
						+ DataBindingTable.workflowRunId + "=?");
				ps.setString(1, runID);
			} else
				ps = connection.prepareStatement("DELETE FROM "
						+ DataBindingTable.DataBinding);
			ps.executeUpdate();
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		logger.info("DB cleared DYNAMIC");
		return refsToRemove;
	}



	private Set<String> collectValueReferences(String runID) throws SQLException {

		Set<String> refs = new HashSet<String>();

		Connection connection = null;
		PreparedStatement ps = null;

		try {
			connection = getConnection();

			if (runID != null) {
				ps = connection.prepareStatement("SELECT value FROM PortBinding WHERE workflowRunId = ?");
				ps.setString(1, runID);
			} else {
				ps = connection.prepareStatement("SELECT value FROM PortBinding");
			}
			boolean success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {
					refs.add(rs.getString("value"));
				}
			}

			if (runID != null) {
				ps = connection.prepareStatement("SELECT collId FROM Collection WHERE workflowRunId = ?");
				ps.setString(1, runID);
			} else {
				ps = connection.prepareStatement("SELECT collId FROM Collection");
			}
			success = ps.execute();

			if (success) {
				ResultSet rs = ps.getResultSet();
				while (rs.next()) {
					refs.add(rs.getString("collId"));
				}
			}

	
		} catch (SQLException e) {
			logger.error("Problem collecting value references for: " + runID + " : " + e);  
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		return refs;
	}



	public void clearDD() {

		Statement stmt = null;
		Connection connection = null;

		try {
			connection = getConnection();
			stmt = connection.createStatement();
			String q = "DELETE FROM DD";
			stmt.executeUpdate(q);
		} catch (SQLException e) {
			logger.warn("Error execting delete query for provenance records", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection.", ex);
				}
			}
		}
	}

	/**
	 * used to support the implementation of
	 *
	 * @param pname
	 * @param vFrom
	 * @param valFrom
	 * @param vTo
	 * @param valTo
	 * @param iteration
	 * @param workflowRunId
	 */
	// FIXME needs the db statement corrected
	public void writeDDRecord(String pFrom, String vFrom, String valFrom,
			String pTo, String vTo, String valTo, String iteration,
			String workflowRunId) {

		Statement stmt = null;
		Connection connection = null;

		try {
			connection = getConnection();
			stmt = connection.createStatement();
			String q = "INSERT INTO DD (PFrom,VFrom,valFrom,PTo,VTo,valTo,iteration,workflowRun) VALUES (" + "\'" + pFrom + "\'," + "\'" + vFrom + "\",  " + "valFrom = \"" + valFrom + "\", " + "PTo = \"" + pTo + "\", " + "VTo = \"" + vTo + "\", " + "valTo  = \"" + valTo + "\", " + "iteration = \"" + iteration + "\", " + "workflowRun = \"" + workflowRunId + "\"; ";

			stmt.executeUpdate(q);
		
		} catch (SQLException e) {
			logger.warn("Error inserting record into DD", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					logger.error("There was an error closing the database connection.", ex);
				}
			}
		}
	}

   public void setQuery(ProvenanceQuery query) { this.pq  = query; }

   public ProvenanceQuery getQuery() { return this.pq; }


}

