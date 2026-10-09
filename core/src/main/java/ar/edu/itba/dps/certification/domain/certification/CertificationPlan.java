package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public sealed interface CertificationPlan {

    String describe();

    record AsAWhole() implements CertificationPlan {

        @Override
        public String describe() {
            return "the asset as a whole";
        }
    }

    record ByParts(Set<Subsystem> subsystems) implements CertificationPlan {

        public ByParts {
            subsystems = Collections.unmodifiableSet(new LinkedHashSet<>(
                    Validate.requiredNonEmpty(subsystems, "subsystems to certify")));
        }

        @Override
        public String describe() {
            return subsystems.size() + (subsystems.size() == 1 ? " part" : " parts")
                    + " separately: " + subsystems;
        }
    }

    static CertificationPlan over(Set<Subsystem> certifiableSubsystems) {
        Validate.required(certifiableSubsystems, "certifiable subsystems");
        return certifiableSubsystems.isEmpty()
                ? new AsAWhole()
                : new ByParts(certifiableSubsystems);
    }
}
