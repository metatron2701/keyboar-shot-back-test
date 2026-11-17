package com.green.keyboard_project;

import com.green.keyboard_project.dto.payment.TossPaymentResponse;
import com.green.keyboard_project.entity.*;
import com.green.keyboard_project.entity.Order;
import com.green.keyboard_project.service.payment.ConfirmedPaymentWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class ConfirmedPaymentWriterTests {
    @Autowired ConfirmedPaymentWriter writer;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    int id;
    int userId;
    String number;
    final OffsetDateTime approved = OffsetDateTime.parse("2026-11-16T12:00:00.123+09:00");

    @BeforeEach void fixture() {
        assertThat(jdbc.getDataSource()).isNotNull();
        assertThat(jdbc.queryForObject("select current_catalog", String.class)).isEqualToIgnoringCase("keyboard_test");
        number = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            User user = new User(); user.setEmail(number + "@test.local"); user.setPassword("test-only");
            user.setName("test"); user.setRole(User.RoleType.USER);
            user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now()); em.persist(user);
            Order order = new Order(); order.setOrderNumber(number); order.setUser(user);
            order.setOrdererName("test"); order.setOrdererPhone("01012345678"); order.setOrdererEmail(user.getEmail());
            order.setOrderStatus(Order.OrderStatusType.PENDING); order.setFinalPayAmount(10000);
            order.setTotalProductAmount(10000); order.setCreatedAt(LocalDateTime.now()); em.persist(order); em.flush();
            id = order.getOrderId(); userId = user.getUserId();
        });
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from payment where order_id=?", id);
        jdbc.update("delete from orders where order_id=?", id);
        jdbc.update("delete from users where user_id=?", userId);
    }
    TossPaymentResponse response(String state, String key, String method, long amount, String order) {
        return new TossPaymentResponse(key, order, amount, "KRW", state, method, approved);
    }
    TossPaymentResponse done() { return response("DONE", "opaque-test-payment", "카드", 10000, number); }
    int count() { return jdbc.queryForObject("select count(*) from payment where order_id=?", Integer.class, id); }
    void rejected(TossPaymentResponse p, String code) {
        assertThatThrownBy(() -> writer.saveDone(id, p)).hasMessage(code);
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("select order_status from orders where order_id=?", String.class, id)).isEqualTo("PENDING");
    }
    @Test void savesApprovalAndOrderAtomicallyWithSeoulTime() {
        assertThat(writer.saveDone(id, done())).isEqualTo(ConfirmedPaymentWriter.Outcome.SAVED);
        assertThat(count()).isEqualTo(1);
        var row = jdbc.queryForMap("select * from payment where order_id=?", id);
        assertThat(row.get("PAYMENT_STATUS")).isEqualTo("PAID");
        assertThat(row.get("PAYMENT_METHOD")).isEqualTo("CARD");
        assertThat(row.get("PG_PROVIDER")).isEqualTo("TOSS");
        assertThat(jdbc.queryForObject("select order_status from orders where order_id=?", String.class, id)).isEqualTo("ORDERED");
        assertThat(jdbc.queryForObject("select paid_at from payment where order_id=?", LocalDateTime.class, id))
                .isEqualTo(LocalDateTime.of(2026,11,16,12,0));
    }
    @Test void identicalRetryIsIdempotent() {
        writer.saveDone(id, done());
        assertThat(writer.saveDone(id, done())).isEqualTo(ConfirmedPaymentWriter.Outcome.ALREADY_SAVED);
        assertThat(count()).isEqualTo(1);
    }
    @Test void conflictingKeyDoesNotOverwrite() {
        writer.saveDone(id, done());
        assertThatThrownBy(() -> writer.saveDone(id, response("DONE", "different", "카드", 10000, number)))
                .hasMessage("PAYMENT_RECORD_CONFLICT");
        assertThat(count()).isEqualTo(1);
    }
    @Test void amountMismatch() { rejected(response("DONE","test","카드",9999,number), "ORDER_PAYMENT_MISMATCH"); }
    @Test void orderMismatch() { rejected(response("DONE","test","카드",10000,"other_order"), "ORDER_PAYMENT_MISMATCH"); }
    @Test void developmentOrdersNeverPersist() { rejected(response("DONE","test","카드",10000,"dev_"+"a".repeat(32)), "REAL_ORDER_REQUIRED"); }
    @Test void longKeyIsNotTruncated() { rejected(response("DONE","k".repeat(101),"카드",10000,number), "PAYMENT_KEY_SCHEMA_LIMIT"); }
    @Test void waitingIsNotPaid() { rejected(response("WAITING_FOR_DEPOSIT","test","가상계좌",10000,number), "DONE_REQUIRED"); }
    @Test void unknownIsNotFailureOrPaid() { rejected(response("UNKNOWN","test","카드",10000,number), "DONE_REQUIRED"); }
    @Test void failedIsNotSavedAsApproved() { rejected(response("ABORTED","test","카드",10000,number), "DONE_REQUIRED"); }
    @Test void unsupportedMethodDoesNotMutate() { rejected(response("DONE","test","휴대폰",10000,number), "UNSUPPORTED_PAYMENT_METHOD"); }
    @Test void cancelledOrderIsNotRevived() {
        jdbc.update("update orders set order_status='CANCELLED' where order_id=?", id);
        assertThatThrownBy(() -> writer.saveDone(id, done())).hasMessage("ORDER_STATE_CONFLICT");
        assertThat(count()).isZero();
    }
    void ready(String key) {
        jdbc.update("insert into payment(order_id,payment_method,pg_provider,pg_tid,payment_status,paid_amount) values(?, 'CARD','TOSS',?,'READY',10000)",id,key);
    }
    @Test void matchingReadyReservationCompletesWithoutAnotherRow() {
        ready(null);
        int paymentId=jdbc.queryForObject("select payment_id from payment where order_id=?",Integer.class,id);
        assertThat(writer.saveDone(id,done())).isEqualTo(ConfirmedPaymentWriter.Outcome.SAVED);
        assertThat(writer.saveDone(id,done())).isEqualTo(ConfirmedPaymentWriter.Outcome.ALREADY_SAVED);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payment_id from payment where order_id=?",Integer.class,id)).isEqualTo(paymentId);
        assertThat(jdbc.queryForObject("select payment_status from payment where order_id=?",String.class,id)).isEqualTo("PAID");
    }
    @Test void readyWithMatchingKeyCompletes() {
        ready(done().paymentKey());
        assertThat(writer.saveDone(id,done())).isEqualTo(ConfirmedPaymentWriter.Outcome.SAVED);
    }
    @Test void readyWithDifferentCaseKeyCannotBeOverwritten() {
        ready(done().paymentKey().toUpperCase(Locale.ROOT));
        assertThatThrownBy(() -> writer.saveDone(id,done())).hasMessage("PAYMENT_RECORD_CONFLICT");
        assertThat(jdbc.queryForObject("select payment_status from payment where order_id=?",String.class,id)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select order_status from orders where order_id=?",String.class,id)).isEqualTo("PENDING");
    }
    @Test void readyReservationWithDifferentAmountIsNotModified() {
        ready(null); jdbc.update("update payment set paid_amount=9999 where order_id=?",id);
        assertThatThrownBy(() -> writer.saveDone(id,done())).hasMessage("PAYMENT_RECORD_CONFLICT");
        assertThat(jdbc.queryForObject("select paid_amount from payment where order_id=?",Integer.class,id)).isEqualTo(9999);
    }
    @Test void readyReservationRollbackPreservesBothStates() {
        ready(null);
        jdbc.execute("alter table payment add constraint stage8_ready_reject check(payment_status <> 'PAID')");
        try {
            assertThatThrownBy(() -> writer.saveDone(id,done())).hasMessage("APPROVED_PERSISTENCE_PENDING");
            assertThat(jdbc.queryForObject("select payment_status from payment where order_id=?",String.class,id)).isEqualTo("READY");
            assertThat(jdbc.queryForObject("select order_status from orders where order_id=?",String.class,id)).isEqualTo("PENDING");
        } finally { jdbc.execute("alter table payment drop constraint stage8_ready_reject"); }
    }
    @Test void maximumCurrentKeyLengthIsStoredWithoutTruncation() {
        String key="K".repeat(100);
        writer.saveDone(id,response("DONE",key,"카드",10000,number));
        assertThat(jdbc.queryForObject("select pg_tid from payment where order_id=?",String.class,id)).isEqualTo(key);
    }
    @Test void unknownOrderIsNotCreated() {
        assertThatThrownBy(() -> writer.saveDone(-1,done())).hasMessage("ORDER_NOT_FOUND");
        assertThat(count()).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"카드,CARD","계좌이체,TRANSFER","간편결제,EASY_PAY","가상계좌,VIRTUAL_ACCOUNT"})
    void supportedMethodsMapWithoutSchemaChanges(String method,String expected) {
        writer.saveDone(id,response("DONE","test",method,10000,number));
        assertThat(jdbc.queryForObject("select payment_method from payment where order_id=?",String.class,id)).isEqualTo(expected);
    }
    @Test void concurrentSameOrderSavesOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> writer.saveDone(id, done()));
            var b = pool.submit(() -> writer.saveDone(id, done()));
            assertThat(List.of(a.get(10, TimeUnit.SECONDS),b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ConfirmedPaymentWriter.Outcome.SAVED, ConfirmedPaymentWriter.Outcome.ALREADY_SAVED);
            assertThat(count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void databaseRejectionRollsBackBothPaymentAndOrder() {
        jdbc.execute("alter table payment add constraint stage8_test_reject check (paid_amount <> 10000)");
        try {
            assertThatThrownBy(() -> writer.saveDone(id, done())).hasMessage("APPROVED_PERSISTENCE_PENDING");
            assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("select order_status from orders where order_id=?", String.class, id)).isEqualTo("PENDING");
        } finally { jdbc.execute("alter table payment drop constraint stage8_test_reject"); }
    }
    @Test @SuppressWarnings("unchecked") void commitFailureIsPersistencePendingAndRedacted() {
        EntityManager entity = mock(EntityManager.class);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doThrow(new IllegalStateException("sensitive database detail")).when(tm).commit(any());
        Order order = new Order(); order.setOrderNumber(number); order.setFinalPayAmount(10000);
        order.setOrderStatus(Order.OrderStatusType.PENDING);
        when(entity.find(eq(Order.class), eq(id), any(jakarta.persistence.LockModeType.class))).thenReturn(order);
        TypedQuery<Payment> query = mock(TypedQuery.class);
        when(entity.createQuery(anyString(), eq(Payment.class))).thenReturn(query);
        when(query.setParameter(eq("id"), eq(id))).thenReturn(query); when(query.getResultList()).thenReturn(List.of());
        assertThatThrownBy(() -> new ConfirmedPaymentWriter(entity, tm).saveDone(id, done()))
                .hasMessage("APPROVED_PERSISTENCE_PENDING").hasNoCause();
    }
}
