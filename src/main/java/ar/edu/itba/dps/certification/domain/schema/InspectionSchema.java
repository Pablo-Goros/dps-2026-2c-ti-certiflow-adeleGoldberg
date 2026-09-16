package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class InspectionSchema {

    private final SchemaId id;
    private final String name;
    private final Set<AssetType> applicableAssetTypes = new LinkedHashSet<>();
    private final List<SchemaVersion> publishedVersions = new ArrayList<>();
    private SchemaDraft draft;

    public InspectionSchema(SchemaId id, String name, Set<AssetType> applicableAssetTypes) {
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

    public void applyTo(AssetType assetType) {
        applicableAssetTypes.add(Validate.required(assetType, "asset type id"));
    }

    public void stopApplyingTo(AssetType assetType) {
        Validate.ensure(applicableAssetTypes.size() > 1,
                "schema " + id + " must remain applicable to at least one asset type");
        applicableAssetTypes.remove(assetType);
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

    public PublicationResult publish(Instant publishedAt) {
        SchemaDraft open = requireDraft();
        Validate.required(publishedAt, "publication instant");
        List<String> violations = open.publicationViolations();
        if (!violations.isEmpty()) {
            return PublicationResult.refused(violations);
        }
        SchemaVersion version = new SchemaVersion(
                new SchemaVersionId(id, nextVersionNumber()),
                open.sections(),
                publishedAt);
        publishedVersions.add(version);
        draft = null;
        return PublicationResult.published(version);
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
