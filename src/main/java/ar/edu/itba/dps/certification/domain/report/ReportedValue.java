package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public sealed interface ReportedValue<T> {

    T current();

    boolean rectified();

    record Original<T>(T value) implements ReportedValue<T> {

        @Override
        public T current() {
            return value;
        }

        @Override
        public boolean rectified() {
            return false;
        }
    }

    record Rectified<T>(T originalValue, T correctedValue, RectificationId rectificationId,
            String reason) implements ReportedValue<T> {

        public Rectified {
            Validate.required(rectificationId, "rectification id");
            reason = Validate.requiredText(reason, "rectification reason");
        }

        @Override
        public T current() {
            return correctedValue;
        }

        @Override
        public boolean rectified() {
            return true;
        }
    }

    static <T> ReportedValue<T> original(T value) {
        return new Original<>(value);
    }

    static <T> ReportedValue<T> rectified(T originalValue, T correctedValue,
            RectificationId rectificationId, String reason) {
        return new Rectified<>(originalValue, correctedValue, rectificationId, reason);
    }
}
