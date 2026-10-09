package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class InspectionSchema {

    private final SchemaId id;
    private final String name;
    private final Set<AssetType> applicableAssetTypes = new LinkedHashSet<>();
    private final List<SchemaVersion> publishedVersions = new ArrayList<>();
    private SchemaDraft draft;

    /** Package-private: schemas are created through {@link SchemaApplicability}, which owns "one schema per asset type". */
    InspectionSchema(SchemaId id, String name, Set<AssetType> applicableAssetTypes) {
        this.id = Validate.required(id, "schema id");
        this.name = Validate.requiredText(name, "schema name");
        this.applicableAssetTypes.addAll(Validate.requiredNonEmpty(applicableAssetTypes, "applicable asset types"));
    }

    public SchemaId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<AssetType> applicableAssetTypes() {
        return Set.copyOf(applicableAssetTypes);
    }

    public boolean appliesTo(AssetType assetType) {
        return applicableAssetTypes.contains(assetType);
    }

    void applyTo(AssetType assetType) {
        Validate.required(assetType, "asset type id");
        Validate.ensure(!appliesTo(assetType), "schema already applies to asset type " + assetType);
        applicableAssetTypes.add(assetType);
    }

    void stopApplyingTo(AssetType assetType) {
        Validate.required(assetType, "asset type id");
        Validate.ensure(appliesTo(assetType), "schema does not apply to asset type " + assetType);
        Validate.ensure(applicableAssetTypes.size() > 1,
                "schema " + id + " must remain applicable to at least one asset type");
        applicableAssetTypes.remove(assetType);
    }

    public void addSection(Section section) {
        requireDraft().addSection(section);
    }

    public Section removeSection(String name) {
        return requireDraft().removeSection(name);
    }

    public Optional<SchemaDraft> draft() {
        return Optional.ofNullable(draft);
    }

    public SchemaDraft openDraft() {
        Validate.ensure(draft == null, "schema " + id + " already has an open draft");
        draft = latestPublishedVersion().map(SchemaDraft::new).orElseGet(SchemaDraft::new);
        return draft;
    }

    public SchemaDraft requireDraft() {
        if (draft == null) {
            throw new DomainException("schema " + id + " has no open draft");
        }
        return draft;
    }

    public void discardDraft() {
        requireDraft();
        draft = null;
    }

    public List<SchemaVersion> publishedVersions() {
        return List.copyOf(publishedVersions);
    }

    public Optional<SchemaVersion> latestPublishedVersion() {
        return publishedVersions.stream().max(Comparator.comparingInt(SchemaVersion::number));
    }

    public Optional<SchemaVersion> findVersion(int number) {
        return publishedVersions.stream().filter(version -> version.number() == number).findFirst();
    }

    public Optional<SchemaVersion> findVersion(SchemaVersionId versionId) {
        return versionId.schemaId().equals(id) ? findVersion(versionId.number()) : Optional.empty();
    }

    public Optional<SchemaVersion> effectiveVersionAt(Instant at) {
        Validate.required(at, "evaluation instant");
        return publishedVersions.stream()
                .filter(version -> !version.effectiveFrom().isAfter(at))
                .max(Comparator.comparing(SchemaVersion::effectiveFrom)
                        .thenComparingInt(SchemaVersion::number));
    }

    public PublicationResult publish(Instant publishedAt) {
        return publish(publishedAt, publishedAt);
    }

    public PublicationResult publish(Instant publishedAt, Instant effectiveFrom) {
        SchemaDraft open = requireDraft();
        Validate.required(publishedAt, "publication instant");
        Validate.required(effectiveFrom, "effective from instant");
        Validate.ensure(!effectiveFrom.isBefore(publishedAt), "effectiveFrom cannot be earlier than publishedAt");

        latestPublishedVersion().ifPresent(latest ->
                Validate.ensure(!effectiveFrom.isBefore(latest.effectiveFrom()),
                        "effectiveFrom " + effectiveFrom + " cannot be earlier than previous version effectiveFrom " + latest.effectiveFrom()));

        List<String> violations = new ArrayList<>(open.publicationViolations());
        violations.addAll(partsLeftUnevaluated(open));
        if (!violations.isEmpty()) {
            return PublicationResult.refused(violations);
        }
        SchemaVersion version = new SchemaVersion(
                new SchemaVersionId(id, nextVersionNumber()),
                open.sections(),
                publishedAt,
                effectiveFrom);
        publishedVersions.add(version);
        draft = null;
        return PublicationResult.published(version);
    }

    /**
     * Subsystems that assets of the applicable types may have and that the draft evaluates with no
     * criterion. Publishing such a version would let an asset hold a global certificate while one
     * of its parts was never inspected, so the gap is refused here rather than silently excluded
     * when the global certificate is derived.
     */
    private List<String> partsLeftUnevaluated(SchemaDraft open) {
        Set<Subsystem> evaluated = open.criteria().stream()
                .map(Criterion::subsystem)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        return applicableAssetTypes.stream()
                .flatMap(assetType -> assetType.subsystems().stream()
                        .filter(subsystem -> !evaluated.contains(subsystem))
                        .map(subsystem -> "assets of type " + assetType + " may have subsystem "
                                + subsystem + ", which no criterion of this version evaluates"))
                .distinct()
                .toList();
    }

    private int nextVersionNumber() {
        return latestPublishedVersion().map(SchemaVersion::number).orElse(0) + 1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InspectionSchema that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
