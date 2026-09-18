package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

public record Section(String name, int order, List<Criterion> criteria) {

    public Section {
        name = Validate.requiredText(name, "section name");
        criteria = Validate.requiredNonEmpty(criteria, "criteria of section '" + name + "'");
    }

    public static Section of(String name, int order, Criterion... criteria) {
        return new Section(name, order, List.of(criteria));
    }
}
