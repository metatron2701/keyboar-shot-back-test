# Stage 8 persistence boundary (partial implementation)

## Scope and activation
ConfirmedPaymentWriter.saveDone(existingOrderId, verifiedProviderResponse) is an internal persistence boundary.
It has no HTTP endpoint and is not called by the stage 7 development controller.
Only a future trusted server flow may pass a provider result after checking paymentKey/orderId/amount against its authenticated real-order request.
No browser-provided approval result may be trusted. Stage 11 must add ownership checks, freeze totals and durable approval intent before an external call.
The current development order and confirmation response remain MEMORY_ONLY. No development order is converted to orders.
No real database writes or provider requests were made during implementation.

## Mapping and transactions
DONE -> payment.PAID; method maps 카드/CARD, 계좌이체/TRANSFER, 간편결제/EASY_PAY, 가상계좌/VIRTUAL_ACCOUNT.
Provider is TOSS; paymentKey is stored unchanged in pg_tid; totalAmount must equal orders.final_pay_amount and fit positive INT.
approvedAt is converted to Asia/Seoul DATETIME seconds; order status becomes ORDERED and ordered_at uses the same timestamp.
Existing PENDING order is pessimistically locked; payment and order are committed atomically in REQUIRES_NEW.
An identical saved result returns ALREADY_SAVED; conflicting existing payment, cancelled order or mismatched totals are rejected without overwrite.
Order lock plus existing unique order_id prevents duplicate storage for the same order, not duplicate provider approval.
Cross-order provider-key uniqueness and durable approval idempotency are not implemented; they require DB design approval before real activation.
Matching READY reservations can transition to PAID atomically; conflicting reservations are never overwritten.

## Non-DONE and failures
WAITING_FOR_DEPOSIT is not PAID. Existing enum cannot distinctly represent it and UNKNOWN.
UNKNOWN/transport failure must not become FAILED; no approval retry is initiated by this writer.
FAILED/ABORTED, cancellation and deposit reconciliation are deliberately not written by this DONE-only boundary.
Current schema supports four methods only, so phone/gift-certificate methods are refused rather than misclassified.
Current pg_tid VARCHAR(100) is smaller than the official paymentKey maximum 200. Longer keys are refused, never truncated.
DB flush/commit errors become APPROVED_PERSISTENCE_PENDING without sensitive cause text.
That error means approval may already be successful: retain the verified result and reconcile using provider GET, never reapprove.
There is currently no durable retry/outbox, approval-intent ledger or endpoint integration; a process crash could lose the result. Activation is blocked until designed.

