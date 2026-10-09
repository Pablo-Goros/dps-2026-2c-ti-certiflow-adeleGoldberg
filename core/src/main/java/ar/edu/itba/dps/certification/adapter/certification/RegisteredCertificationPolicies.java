package ar.edu.itba.dps.certification.adapter.certification;

import ar.edu.itba.dps.certification.application.certification.port.CertificationPolicyRegistry;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicyRef;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.certification.policy.JurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.HashMap;
import java.util.Map;

/** Explicit composition registry. Registration activates a strictly newer revision and retains history. */
public final class RegisteredCertificationPolicies implements CertificationPolicyRegistry {
    private final Map<JurisdictionId, CertificationPolicyRef> active = new HashMap<>();
    private final Map<CertificationPolicyRef, Entry> history = new HashMap<>();
    private record Entry(CertificationPolicySnapshot definition, JurisdictionCertificationPolicy strategy) { }
    public void register(JurisdictionId jurisdiction, JurisdictionCertificationPolicy policy) {
        Validate.required(jurisdiction, "jurisdiction");
        Validate.required(policy, "policy");
        var snapshot = Validate.required(policy.snapshot(), "policy snapshot");
        var ref = Validate.required(policy.reference(), "policy reference");
        if (!ref.equals(snapshot.reference()) || !ref.jurisdiction().equals(jurisdiction)) {
            throw new PolicyResolutionException("policy reference does not match its definition or requested jurisdiction");
        }
        if (history.containsKey(ref)) {
            throw new PolicyResolutionException("policy revision is already registered: " + ref);
        }
        var previous = active.get(jurisdiction);
        if (previous != null && (!previous.policyId().equals(ref.policyId()) || ref.revision() <= previous.revision())) {
            throw new PolicyResolutionException("register a newer revision of the jurisdiction's policy");
        }
        // A strategy must be immutable and its complete rules represented by the stored definition.
        history.put(ref, new Entry(snapshot, policy));
        active.put(jurisdiction, ref);
    }
    @Override public JurisdictionCertificationPolicy resolve(JurisdictionId jurisdiction) {
        Validate.required(jurisdiction, "jurisdiction");
        var ref = active.get(jurisdiction);
        if (ref == null) throw new PolicyResolutionException("no certification policy registered for " + jurisdiction);
        return historical(ref);
    }
    @Override public JurisdictionCertificationPolicy historical(CertificationPolicyRef reference) {
        Validate.required(reference, "policy reference");
        var entry = history.get(reference);
        if (entry == null) throw new PolicyResolutionException("unknown policy revision: " + reference);
        if (!entry.definition().equals(entry.strategy().snapshot()) || !reference.equals(entry.strategy().reference())) {
            throw new PolicyResolutionException("registered policy was mutated: " + reference);
        }
        return entry.strategy();
    }
}
