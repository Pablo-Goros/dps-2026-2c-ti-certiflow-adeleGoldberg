import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.events.OutboxDispatcher;
import ar.edu.itba.dps.certification.domain.catalogue.*;
import ar.edu.itba.dps.certification.domain.shared.*;
import ar.edu.itba.dps.certification.domain.audit.*;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import org.h2.jdbcx.JdbcDataSource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Audit-only assertions against production adapters; never uses the default database. */
class Step3Probe {
  static final Instant AT = Instant.parse("2026-03-01T10:00:00Z");
  static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  static CorrectiveActionPlanned event(String id) {
    return new CorrectiveActionPlanned(InspectionId.of("audit-i"), CriterionId.of(id), AT);
  }
  static JdbcDataSource source(String url) { var ds = new JdbcDataSource(); ds.setURL(url); return ds; }
  static JdbcPersistence memory() { return JdbcPersistence.migrateAndOpen(source("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")); }
  static AuditEntry audit(String id) {
    return new AuditEntry(AuditedElementRef.party(id), AuditAction.PARTY_REGISTERED, AT,
      Actor.system(), Optional.empty(), new AuditDetail.ElementCreated("audit party"));
  }
  static void await(CountDownLatch latch) {
    try { check(latch.await(10, TimeUnit.SECONDS), "latch timeout"); }
    catch (InterruptedException e) { throw new RuntimeException(e); }
  }
  static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try { return method.invoke(target, args); } catch (InvocationTargetException e) { throw e.getCause(); }
  }
  public static void main(String[] args) throws Exception {
    if (args[0].equals("seed") || args[0].equals("recover")) {
      var p = JdbcPersistence.migrateAndOpen(source(args[1]));
      if (args[0].equals("seed")) {
        p.transactions().execute(() -> {
          p.parties().save(new Party(PartyId.of("kept"), "Kept", PartyKind.PERSON));
          p.auditTrail().append(audit("kept")); p.eventOutbox().enqueue(event("restart"));
        });
        System.out.println("F-RESTART-01 seed committed party=1 audit=1 pending=1; normal JVM exit");
      } else {
        check(p.parties().findById(PartyId.of("kept")).isPresent(), "lost party");
        check(p.auditTrail().all().size()==1 && p.eventOutbox().entries("PENDING").size()==1, "lost audit/event");
        var d = new OutboxDispatcher(p.transactions(), p.eventOutbox(), List.of(e -> p.parties().save(new Party(PartyId.of("reaction"), "Reaction", PartyKind.PERSON))), 3);
        check(d.dispatchPending()==1 && d.dispatchPending()==0, "recovery repeat");
        check(p.parties().findById(PartyId.of("reaction")).isPresent() && p.eventOutbox().entries("DONE").size()==1, "lost reaction");
        System.out.println("F-RESTART-01 fresh JVM reconstructed party/audit/event; recovery delivered=1 repeat=0 DONE=1");
      }
      return;
    }
    var p = memory();
    try {
      p.transactions().execute(() -> {
        p.parties().save(new Party(PartyId.of("rollback"), "Rollback", PartyKind.PERSON));
        p.auditTrail().append(audit("rollback")); p.eventOutbox().enqueue(event("rollback"));
        throw new IllegalStateException("injected after all saves");
      });
    } catch (IllegalStateException expected) {}
    check(p.parties().findAll().isEmpty() && p.auditTrail().all().isEmpty() && p.eventOutbox().entries(null).isEmpty(), "partial rollback");
    p.eventOutbox().enqueue(event("handler"));
    var broken = new OutboxDispatcher(p.transactions(), p.eventOutbox(), List.of(e -> {
      p.parties().save(new Party(PartyId.of("handler"), "Handler", PartyKind.PERSON));
      p.auditTrail().append(audit("handler")); throw new IllegalStateException("after handler writes");
    }), 3);
    broken.dispatchPending();
    check(p.parties().findAll().isEmpty() && p.auditTrail().all().isEmpty() && p.eventOutbox().entries("PENDING").getFirst().attempts()==1, "handler rollback");
    System.out.println("F-ROLLBACK-01/02 aggregate+audit+outbox rollback; handler writes rollback and pending attempts=1");

    var mixed = memory();
    mixed.eventOutbox().enqueue(event("bad")); mixed.eventOutbox().enqueue(event("good1")); mixed.eventOutbox().enqueue(event("good2"));
    var md = new OutboxDispatcher(mixed.transactions(), mixed.eventOutbox(), List.of(e -> {
      if (((CorrectiveActionPlanned)e).criterionId().value().equals("bad")) throw new IllegalStateException("bad");
    }), 3);
    check(md.dispatchPending()==2, "mixed delivery");
    check(mixed.eventOutbox().entries("PENDING").getFirst().attempts()==2, "expected retry in same pass");
    System.out.println("F-OUTBOX-06 mixed batch: delivered=2 failing row attempts=2 in ONE dispatchPending call");
    var blocked = memory();
    for (int i=0;i<50;i++) blocked.eventOutbox().enqueue(event("bad"+i));
    blocked.eventOutbox().enqueue(event("good"));
    var bd = new OutboxDispatcher(blocked.transactions(), blocked.eventOutbox(), List.of(e -> {
      if (!((CorrectiveActionPlanned)e).criterionId().value().equals("good")) throw new IllegalStateException("bad");
    }), 10);
    check(bd.dispatchPending()==0 && blocked.eventOutbox().entries("PENDING").get(50).attempts()==0, "batch starvation");
    System.out.println("F-OUTBOX-07 50 failing oldest rows: healthy row 51 unattempted; delivered=0 pending=51");

    var raw = source("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
    var paused = new CountDownLatch(1); var release = new CountDownLatch(1); var pauseOnce = new AtomicBoolean(true);
    DataSource wrapped = (DataSource)Proxy.newProxyInstance(Step3Probe.class.getClassLoader(), new Class[]{DataSource.class}, (proxy, method, parameters) -> {
      Object result = invoke(raw, method, parameters);
      if (!method.getName().equals("getConnection")) return result;
      return Proxy.newProxyInstance(Step3Probe.class.getClassLoader(), new Class[]{Connection.class}, (cp, cm, ca) -> {
        if (cm.getName().equals("prepareStatement") && ((String)ca[0]).contains("SET attempts = attempts + 1") && pauseOnce.compareAndSet(true,false)) {
          paused.countDown(); await(release);
        }
        return invoke(result, cm, ca);
      });
    });
    var raced = JdbcPersistence.migrateAndOpen(wrapped);
    raced.eventOutbox().enqueue(event("race")); var effects = new AtomicInteger(); var failures = new AtomicReference<Throwable>();
    var first = new OutboxDispatcher(raced.transactions(), raced.eventOutbox(), List.of(e -> { throw new IllegalStateException("first fails"); }), 3);
    var second = new OutboxDispatcher(raced.transactions(), raced.eventOutbox(), List.of(e -> {
      effects.incrementAndGet(); raced.auditTrail().append(audit("effect"));
    }), 3);
    Thread thread = new Thread(() -> { try { first.dispatchPending(); } catch(Throwable t) { failures.set(t); } });
    thread.start(); await(paused);
    check(second.dispatchPending()==1 && raced.eventOutbox().entries("DONE").size()==1, "second commit");
    release.countDown(); thread.join(10000); check(!thread.isAlive() && failures.get()==null, "thread failure");
    check(raced.eventOutbox().entries("PENDING").size()==1, "DONE not resurrected");
    check(second.dispatchPending()==1 && effects.get()==2 && raced.auditTrail().all().size()==2, "duplicate absent");
    System.out.println("F-OUTBOX-08 failed dispatcher resumes AFTER competing success: DONE->PENDING; successful effects=2 audit entries=2");
  }
}
