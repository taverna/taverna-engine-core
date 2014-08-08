package net.sf.taverna.t2.reference.impl.external.object.patch.t3_1213;

import java.nio.charset.Charset;

import net.sf.taverna.t2.reference.ExternalReferenceTranslatorSPI;
import net.sf.taverna.t2.reference.ReferenceContext;
import net.sf.taverna.t2.reference.impl.external.object.InlineByteArrayReference;
import net.sf.taverna.t2.reference.impl.external.object.InlineStringReference;

public class InlineStringToInlineByteTranslator implements
		ExternalReferenceTranslatorSPI<InlineStringReference, InlineByteArrayReference> {
	
	private final Charset UTF8 = Charset.forName("UTF-8");
	
	@Override
	public InlineByteArrayReference createReference(
			InlineStringReference sourceReference, ReferenceContext context) {
		byte[] bytes = sourceReference.getValue().getBytes(UTF8);
		InlineByteArrayReference ref = new InlineByteArrayReference();
		ref.setValue(bytes);	
		return ref;
	}

	@Override
	public Class<InlineStringReference> getSourceReferenceType() {
		return InlineStringReference.class;
	}

	@Override
	public Class<InlineByteArrayReference> getTargetReferenceType() {
		return InlineByteArrayReference.class;
	}

	@Override
	public boolean isEnabled(ReferenceContext context) {
		return true;
	}

	@Override
	public float getTranslationCost() {
		return 0.001f;
	}

}
