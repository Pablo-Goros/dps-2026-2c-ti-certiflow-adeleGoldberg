package ar.edu.itba.dps.certification.application.notification;

import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.PolicyFacts;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class NotifyCorrectiveActionExpiryTest {

    private final FindingRepository findings = mock(FindingRepository.class);
    private final PartyRepository parties = mock(PartyRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    @SuppressWarnings("unchecked")
    private final BiConsumer<Notification, RuntimeException> onFailure = mock(BiConsumer.class);
    private final NotifyCorrectiveActionExpiry handler =
            new NotifyCorrectiveActionExpiry(findings, parties, sender, onFailure);

    private PolicyFacts facts;
    private CorrectiveActionExpired expired;

    @BeforeEach
    void anOverdueFinding() {
        facts = new PolicyFacts(CriterionResult.OBSERVED, Severity.HIGH);
        expired = new CorrectiveActionExpired(facts.inspection.id(), facts.finding.id(),
                CorrectiveActionId.of("action"), PolicyFacts.CRITERION, PolicyFacts.AT);
        when(findings.findById(facts.finding.id())).thenReturn(Optional.of(facts.finding));
        when(parties.findById(PolicyFacts.OWNER))
                .thenReturn(Optional.of(new Party(PolicyFacts.OWNER, "Owner Corp", PartyKind.ORGANIZATION)));
    }

    @Test
    void theFindingsResponsibleIsToldWhatExpired() {
        handler.handle(expired);

        var sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().recipientId()).isEqualTo(PolicyFacts.OWNER.value());
        assertThat(sent.getValue().recipientName()).isEqualTo("Owner Corp");
        assertThat(sent.getValue().subject()).isEqualTo("Corrective action overdue");
        assertThat(sent.getValue().message())
                .contains(PolicyFacts.CRITERION.value(), facts.inspection.id().value(), facts.finding.id().value());
        assertThat(sent.getValue().occurredAt()).isEqualTo(PolicyFacts.AT);
        verifyNoInteractions(onFailure);
    }

    @Test
    void otherEventsAreIgnored() {
        handler.handle(new CorrectiveActionPlanned(facts.inspection.id(), PolicyFacts.CRITERION, PolicyFacts.AT));

        verifyNoInteractions(sender, findings, parties);
    }

    @Test
    void nothingIsSentWhenTheFindingIsGone() {
        when(findings.findById(facts.finding.id())).thenReturn(Optional.empty());

        handler.handle(expired);

        verify(sender, never()).send(any());
    }

    @Test
    void nothingIsSentWhenTheResponsibleIsNotRegistered() {
        when(parties.findById(PolicyFacts.OWNER)).thenReturn(Optional.empty());

        handler.handle(expired);

        verify(sender, never()).send(any());
    }

    @Test
    void aChannelThatFailsIsReportedAndTheEventStillCountsAsHandled() {
        var failure = new NotificationFailedException("channel is down");
        doThrow(failure).when(sender).send(any());

        handler.handle(expired);

        verify(onFailure).accept(any(Notification.class), org.mockito.ArgumentMatchers.eq(failure));
    }
}
