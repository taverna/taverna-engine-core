/**
 * 
 */
package net.sf.taverna.t2.workflowmodel.processor.activity;

import org.jdom.Element;

import org.apache.log4j.Logger;

/**
 * An unrecognized activity is an activity that was not recognized when the workflow was opened.
 * 
 * @author alanrw
 * 
 */
public final class UnrecognizedActivity extends
		NonExecutableActivity<Element> {

	private static Logger logger = Logger.getLogger(DisabledActivity.class);

	private Element conf;
	/**
	 * It is not possible to create a "naked" UnrecognizedActivity.
	 */
	private UnrecognizedActivity() {
		super();
	}

	public UnrecognizedActivity(Element config) throws ActivityConfigurationException {
	    this();
	    this.configure(config);
	}

	/* (non-Javadoc)
	 * @see net.sf.taverna.t2.workflowmodel.processor.activity.AbstractAsynchronousActivity#configure(java.lang.Object)
	 */
	@Override
	public void configure(Element conf)
			throws ActivityConfigurationException {
		this.conf = conf;
	}

	/* (non-Javadoc)
	 * @see net.sf.taverna.t2.workflowmodel.processor.activity.AbstractAsynchronousActivity#getConfiguration()
	 */
	@Override
	public Element getConfiguration() {
		return conf;
	}

}