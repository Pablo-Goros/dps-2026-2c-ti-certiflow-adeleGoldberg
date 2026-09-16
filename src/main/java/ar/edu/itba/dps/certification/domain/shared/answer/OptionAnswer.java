package ar.edu.itba.dps.certification.domain.shared.answer;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record OptionAnswer(String optionKey) implements Answer {

    public OptionAnswer {
        optionKey = Validate.requiredText(optionKey, "option key");
    }

    public static OptionAnswer of(String optionKey) {
        return new OptionAnswer(optionKey);
    }

    @Override
    public String describe() {
        return optionKey;
    }
}
