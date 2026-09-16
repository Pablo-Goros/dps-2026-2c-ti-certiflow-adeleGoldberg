package ar.edu.itba.dps.certification.domain.shared.answer;

public record YesNoAnswer(boolean affirmative) implements Answer {

    public static YesNoAnswer yes() {
        return new YesNoAnswer(true);
    }

    public static YesNoAnswer no() {
        return new YesNoAnswer(false);
    }

    @Override
    public String describe() {
        return affirmative ? "yes" : "no";
    }
}
