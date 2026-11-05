package com.familyhealth.records.timeline;

import java.util.List;
import java.util.UUID;

/** Implemented by each record type that appears on the timeline. The caller has already checked access. */
public interface TimelineSource {

	List<TimelineEntry> timelineEntries(UUID profileId);

}
