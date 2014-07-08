/**
 *
 */
package net.sf.taverna.t2.workflowmodel.processor.activity;

import net.sf.taverna.t2.workflowmodel.Edits;
import org.jdom.Element;

/**
 * An unrecognized activity is an activity that was not recognized when the workflow was opened.
 *
 * @author alanrw
 *
 */
public final class UnrecognizedActivity extends
		NonExecutableActivity<Element> {

	public static final String URI = "http://ns.taverna.org.uk/2010/activity/unrecognized";

	private Element conf;
	/**
	 * It is not possible to create a "naked" UnrecognizedActivity.
	 */
	private UnrecognizedActivity(Edits edits) {
		super(edits);
	}

	public UnrecognizedActivity(Element config, Edits edits) throws ActivityConfigurationException {
	    this(edits);
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