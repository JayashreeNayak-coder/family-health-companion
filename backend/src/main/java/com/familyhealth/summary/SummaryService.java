package com.familyhealth.summary;

import com.familyhealth.ai.AiErrors;
import com.familyhealth.ai.AiException;
import com.familyhealth.ai.AiUsageService;
import com.familyhealth.ai.SummaryGenerator;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.PageResponse;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.SummarySources;
import com.familyhealth.summary.SummaryDtos.Conflict;
import com.familyhealth.summary.SummaryDtos.Point;
import com.familyhealth.summary.SummaryDtos.Request;
import com.familyhealth.summary.SummaryDtos.Response;
import com.familyhealth.summary.SummaryDtos.Source;
import com.familyhealth.summary.SummaryDtos.SourceRef;
import com.familyhealth.summary.SummaryDtos.StoredConflict;
import com.familyhealth.summary.SummaryDtos.StoredContent;
import com.familyhealth.summary.SummaryDtos.StoredPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Record-based summaries (docs/02 section 3.4). Not transactional around the provider call, so no
 * database connection is held while waiting. Grounding is checked by the parser: every point must
 * cite records that were sent (docs/06 section 8.3).
 */
@Service
public class SummaryService {

	static final String FEATURE = "HEALTH_SUMMARY";

	static final int MAX_RECORDS = 150;

	private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

	/** Topic, the record type it covers, and how a missing topic is described. */
	private static final Map<String, String[]> TOPICS = Map.of("CONDITIONS", new String[] { "CONDITION", "conditions" },
			"ALLERGIES", new String[] { "ALLERGY", "allergies" }, "MEDICATIONS",
			new String[] { "MEDICATION", "medications" }, "LAB_RESULTS", new String[] { "LAB_RESULT", "lab results" },
			"VISITS", new String[] { "VISIT", "visits" }, "SYMPTOMS", new String[] { "SYMPTOM", "symptoms" });

	private final SummarySources sources;

	private final SummaryGenerator generator;

	private final AiUsageService usage;

	private final HealthSummaryRepository summaries;

	private final ProfileAccess access;

	private final JsonMapper json;

	SummaryService(SummarySources sources, SummaryGenerator generator, AiUsageService usage,
			HealthSummaryRepository summaries, ProfileAccess access, JsonMapper json) {
		this.sources = sources;
		this.generator = generator;
		this.usage = usage;
		this.summaries = summaries;
		this.access = access;
		this.json = json;
	}

	public Response generate(UUID accountId, UUID profileId, Request request) {
		// Access is checked here, before anything about AI availability is revealed.
		SummarySources.Sources loaded = sources.load(accountId, profileId, request.periodFrom(), request.periodTo(),
				MAX_RECORDS);
		if (loaded.records().isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_RECORDS",
					"There are no records to summarise for this period.");
		}
		Map<String, Source> byRef = new LinkedHashMap<>();
		List<SummaryGenerator.Input> inputs = new ArrayList<>();
		for (SummarySources.Snapshot record : loaded.records()) {
			String ref = "R" + (byRef.size() + 1);
			byRef.put(ref, new Source(ref, record.type(), record.id(), record.date(), record.title(), record.detail(),
					record.sourceType()));
			inputs.add(new SummaryGenerator.Input(ref, record.type(),
					record.date() == null ? null : record.date().toString(), record.title(), record.detail(),
					record.sourceType() == null ? null : record.sourceType().name()));
		}
		SummaryGenerator.Request prompt = new SummaryGenerator.Request(
				request.periodFrom() == null ? null : request.periodFrom().toString(),
				request.periodTo() == null ? null : request.periodTo().toString(), inputs);

		SummaryGenerator.Result result = call(accountId, prompt);

		Set<String> cited = new LinkedHashSet<>();
		result.points().forEach(point -> cited.addAll(point.refs()));
		result.conflicts().forEach(conflict -> cited.addAll(conflict.refs()));
		StoredContent content = new StoredContent(
				result.points().stream().map(p -> new StoredPoint(p.topic(), p.text(), p.refs())).toList(),
				result.gaps(),
				result.conflicts().stream().map(c -> new StoredConflict(c.text(), c.refs())).toList(),
				limitations(loaded, request),
				cited.stream().map(byRef::get).toList());
		List<SourceRef> sent = byRef.values().stream().map(source -> new SourceRef(source.type(), source.id())).toList();
		HealthSummary saved = summaries.saveAndFlush(new HealthSummary(profileId, accountId, request.periodFrom(),
				request.periodTo(), json.writeValueAsString(content), json.writeValueAsString(sent),
				generator.provider(), generator.model(), generator.promptVersion()));
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public PageResponse<Response> list(UUID accountId, UUID profileId, Pageable pageable) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		return PageResponse.from(summaries.findByProfileId(profileId, pageable), this::toResponse);
	}

	@Transactional(readOnly = true)
	public Response get(UUID accountId, UUID profileId, UUID id) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		return toResponse(load(profileId, id));
	}

	@Transactional
	public void delete(UUID accountId, UUID profileId, UUID id) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		summaries.delete(load(profileId, id));
	}

	private HealthSummary load(UUID profileId, UUID id) {
		return summaries.findByIdAndProfileId(id, profileId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
	}

	private SummaryGenerator.Result call(UUID accountId, SummaryGenerator.Request prompt) {
		if (!generator.isAvailable()) {
			throw AiErrors.unavailable();
		}
		usage.claim(accountId, FEATURE);
		SummaryGenerator.Result result;
		try {
			result = generator.generate(prompt);
		}
		catch (AiException ex) {
			log.warn("Summary generation failed: {}", ex.code());
			usage.recordCall(accountId, FEATURE, null, null, ex.code().name());
			throw AiErrors.toApi(ex);
		}
		usage.recordCall(accountId, FEATURE, result.inputTokens(), result.outputTokens(), "SUCCEEDED");
		return result;
	}

	/** Said by the backend, not the model, so they're always present and accurate. */
	private static List<String> limitations(SummarySources.Sources loaded, Request request) {
		boolean period = request.periodFrom() != null || request.periodTo() != null;
		Set<String> types = loaded.records().stream().map(SummarySources.Snapshot::type).collect(Collectors.toSet());
		List<String> limitations = new ArrayList<>();
		limitations.add("This summary only restates recorded information. It does not interpret results or give "
				+ "advice, and records may be incomplete.");
		for (String topic : SummaryGenerator.TOPICS) {
			String[] covers = TOPICS.get(topic);
			if (!types.contains(covers[0])) {
				boolean dated = !covers[0].equals("ALLERGY");
				limitations.add("No " + covers[1] + " are recorded" + (period && dated ? " in this period." : "."));
			}
		}
		if (loaded.records().stream().anyMatch(record -> record.date() == null && !record.type().equals("ALLERGY"))) {
			limitations.add("Records with no date are included and marked as date unknown.");
		}
		if (loaded.truncated()) {
			limitations.add("Only the newest " + MAX_RECORDS + " records were included. Choose a shorter period to "
					+ "cover older ones.");
		}
		limitations.add("\"From document\" means taken from an attached document. The documents were not checked.");
		return List.copyOf(limitations);
	}

	private Response toResponse(HealthSummary summary) {
		StoredContent content = json.readValue(summary.content(), StoredContent.class);
		Map<String, Source> byRef = content.sources()
			.stream()
			.collect(Collectors.toMap(Source::ref, Function.identity()));
		return new Response(summary.id(), summary.profileId(), SummaryDtos.AI_GENERATED, summary.promptVersion(),
				summary.model(), summary.periodFrom(), summary.periodTo(), summary.createdAt(),
				content.points()
					.stream()
					.map(p -> new Point(p.topic(), p.text(), p.refs().stream().map(byRef::get).toList()))
					.toList(),
				content.gaps(),
				content.conflicts()
					.stream()
					.map(c -> new Conflict(c.text(), c.refs().stream().map(byRef::get).toList()))
					.toList(),
				content.limitations(), SummaryDtos.DISCLAIMER);
	}

}
