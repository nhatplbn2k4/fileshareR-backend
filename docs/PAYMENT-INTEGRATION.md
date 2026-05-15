# Payment Integration — VNPay + MoMo

Tích hợp cổng thanh toán cho phép user/nhóm mua Plan + Storage Addon. Backend-first MVP.

## Kiến trúc

```
PaymentController (REST)
   ├── POST   /api/payments/initiate              (authenticated)
   ├── GET    /api/payments/{id}                  (authenticated)
   ├── GET    /api/payments/vnpay/return          (public — browser)
   ├── GET    /api/payments/vnpay/ipn             (public — server-to-server)
   ├── GET    /api/payments/momo/return           (public — browser)
   └── POST   /api/payments/momo/ipn              (public — server-to-server, JSON)

PaymentService (orchestrator)
   ├── Strategy pattern — selects provider impl by enum
   │      ├── VnpayPaymentStrategy   (HMAC-SHA512)
   │      └── MomoPaymentStrategy    (HMAC-SHA256, REST API v2)
   ├── PaymentRepository (Payment entity, status state machine)
   └── on SUCCESS IPN → BillingService.purchaseUserPlan/Addon/Group*
```

Payment status state machine: `PENDING → SUCCESS | FAILED | CANCELLED | EXPIRED` (terminal). Idempotent: replay IPN with same txnRef on terminal status is a no-op.

## Flow thanh toán

1. **Frontend** gọi `POST /api/payments/initiate`:
   ```json
   {
     "provider": "VNPAY",
     "purchaseType": "PLAN",
     "scope": "USER",
     "planCode": "PREMIUM"
   }
   ```
2. **Backend** validate plan tồn tại, tạo `Payment(PENDING)` với `txnRef` duy nhất, ký URL, trả `{paymentId, txnRef, redirectUrl}`.
3. **Frontend** redirect browser sang `redirectUrl`. User thanh toán trên VNPay/MoMo.
4. **Provider** gửi 2 callback:
   - **Browser return** → `/api/payments/{provider}/return` → backend verify chữ ký + redirect user về `payment.frontend-return-url` với `?status=SUCCESS|FAILED&txnRef=...`. **KHÔNG apply purchase** (vì user có thể đóng browser sớm).
   - **Server-to-server IPN** → `/api/payments/{provider}/ipn` → backend verify chữ ký + verify amount khớp với DB + **apply purchase** qua `BillingService` + flip status thành `SUCCESS`. Idempotent.
5. **Frontend** poll `GET /api/payments/{id}` để biết trạng thái cuối.

## Setup credentials

Mỗi provider có sandbox riêng:

### VNPay sandbox
VNPay không có self-service signup — phải email yêu cầu sandbox credentials:

1. **Developer portal:** https://sandbox.vnpayment.vn/apis/ — đọc tổng quan + downloads
2. **Email yêu cầu credentials:** `kythuatctt@vnpay.vn` (hoặc `hotro@vnpay.vn`). Trong email nói rõ:
   - Tên dự án (ví dụ: fileshareR DATN)
   - Cần TMN Code + Hash Secret cho môi trường sandbox
   - Return URL: `http://localhost:8080/api/payments/vnpay/return` (hoặc ngrok URL)
3. **Nhận credentials qua email** → add vào `.env`:
   ```
   VNPAY_TMN_CODE=YOUR_TMN_CODE
   VNPAY_HASH_SECRET=YOUR_HASH_SECRET
   ```
4. **Demo + test cards công khai (không cần credentials):** https://sandbox.vnpayment.vn/apis/vnpay-demo/
5. Không cần đổi `VNPAY_PAY_URL` — đã trỏ sang sandbox API endpoint `/paymentv2/vpcpay.html`

### MoMo sandbox
1. **Developer docs:** https://developers.momo.vn/v3/docs/payment/api/wallet/onetime/
2. **Onboarding hướng dẫn:** https://developers.momo.vn/v3/docs/payment/onboarding/overall/
3. **Merchant portal đăng ký:** https://business.momo.vn — tạo tài khoản → switch sang sandbox/test
4. **Email hỗ trợ:** `merchant.care@momo.vn` hoặc hotline 1900 636 652
5. Sau khi có credentials → add vào `.env`:
   ```
   MOMO_PARTNER_CODE=YOUR_PARTNER_CODE
   MOMO_ACCESS_KEY=YOUR_ACCESS_KEY
   MOMO_SECRET_KEY=YOUR_SECRET_KEY
   ```
6. Test app MoMo (để bấm thanh toán trên sandbox): https://developers.momo.vn/v3/docs/payment/test/test-app

### Frontend return URL
```
PAYMENT_FRONTEND_RETURN_URL=http://localhost:3000/payment/result
```
SPA cần handle 3 query param: `?status=SUCCESS|FAILED|error&txnRef=<ref>&error=<msg-nếu-có>`.

## Test IPN với ngrok (DEV)

VNPay/MoMo sandbox cần callback URL public, không gọi được `localhost:8080`. Dùng ngrok:

```bash
# 1. Cài ngrok: https://ngrok.com/download (free tier OK)
# 2. Chạy backend trên port 8080
# 3. Mở tunnel:
ngrok http 8080

# 4. Ngrok in ra:
# Forwarding   https://abc123.ngrok-free.app -> http://localhost:8080

# 5. Update .env với public URL:
VNPAY_RETURN_URL=https://abc123.ngrok-free.app/api/payments/vnpay/return
MOMO_RETURN_URL=https://abc123.ngrok-free.app/api/payments/momo/return
MOMO_IPN_URL=https://abc123.ngrok-free.app/api/payments/momo/ipn

# 6. Restart backend
# 7. Trigger thanh toán → ngrok dashboard (http://localhost:4040) hiện request trực tiếp
```

VNPay IPN dùng cùng URL với return (cùng host, khác path); MoMo IPN dùng URL riêng `momo/ipn`.

## Test thẻ sandbox

### VNPay
| Type | Số thẻ | Tên CH | NgayPH | OTP |
|---|---|---|---|---|
| NCB success | `9704198526191432198` | NGUYEN VAN A | 07/15 | `123456` |
| Insufficient funds | `9704195798459170488` | NGUYEN VAN A | 07/15 | `123456` |

(Đầy đủ: https://sandbox.vnpayment.vn/apis/vnpay-demo/)

### MoMo
- Sandbox tự động trả success/fail theo amount range (xem MoMo docs)
- Hoặc dùng MoMo Test App: https://developers.momo.vn/v3/docs/payment/test/test-app

## Verify nhanh không có credentials

Strategy tự fail-fast với `PAYMENT_PROVIDER_CONFIG_MISSING` (HTTP 503) khi credentials trống. Cần đăng ký sandbox trước khi test end-to-end. Tuy nhiên 25 unit test (`mvn test`) verify signature algorithm + idempotency offline — không cần credentials.

## Endpoint cheat-sheet

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| POST | `/api/payments/initiate` | JWT | `InitiatePaymentRequest` | `{paymentId, txnRef, redirectUrl}` |
| GET | `/api/payments/{id}` | JWT | — | `PaymentStatusResponse` |
| GET | `/api/payments/vnpay/return?...` | public | — | 302 redirect to frontend |
| GET | `/api/payments/vnpay/ipn?...` | public | — | `{RspCode, Message}` JSON |
| GET | `/api/payments/momo/return?...` | public | — | 302 redirect to frontend |
| POST | `/api/payments/momo/ipn` | public | JSON IPN | 204 No Content |

## Tham khảo
- VNPay API docs: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- MoMo Payment API v3: https://developers.momo.vn/v3/docs/payment/api/wallet/onetime
- Strategy + Facade pattern: [.claude/rules/design-patterns.md](../../.claude/rules/design-patterns.md) §2
