package com.familyhealth.document;

import java.util.UUID;

/**
 * Implemented by the module that holds records, so this module needn't know about them. Called in
 * the deleting transaction, before the document row goes (docs/04 section 8).
 */
public interface DocumentReferences {

	/** Records keep their data, and show that their source document was deleted. */
	void documentDeleted(UUID profileId, UUID documentId);

}
