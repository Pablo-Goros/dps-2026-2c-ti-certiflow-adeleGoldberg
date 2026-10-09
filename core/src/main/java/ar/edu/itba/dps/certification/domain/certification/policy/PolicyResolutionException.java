package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.shared.DomainException;

public final class PolicyResolutionException extends DomainException {
    public PolicyResolutionException(String message) { super(message); }
}
