package ar.edu.itba.dps.certification.domain.shared;

public sealed interface Actor {

    String displayName();

    record User(PartyId partyId, String name) implements Actor {

        public User {
            Validate.required(partyId, "actor party id");
            name = Validate.requiredText(name, "actor name");
        }

        @Override
        public String displayName() {
            return name;
        }
    }

    record System() implements Actor {

        @Override
        public String displayName() {
            return "system";
        }
    }

    static Actor user(PartyId partyId, String name) {
        return new User(partyId, name);
    }

    static Actor system() {
        return new System();
    }
}
