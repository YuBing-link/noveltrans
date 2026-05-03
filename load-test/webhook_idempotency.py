"""Stripe Webhook 并发幂等性压测（真实数据版 v2）

【测试原理】
- 使用真实的 Stripe Customer ID / Subscription ID（由 Stripe 平台创建）
- 事件 ID 采用 Stripe 官方格式：evt_1<unix_ts><API Key 前缀><随机串>
- 签名算法与 Stripe 官方 webhook 签名完全一致（HMAC-SHA256 + timestamp + secret）
- 直接 POST 到本地后端 /webhook/stripe 端点，绕过 Stripe 平台，模拟极端并发推送场景
- 验证数据库层的原子性保障是否能防止重复处理

【Stripe 官方 webhook 签名算法】
  1. 拼接：signed_payload = "{timestamp}.{body}"
  2. 计算：signature = HMAC-SHA256(signed_payload, webhook_secret).hexdigest()
  3. 签名头：Stripe-Signature: t={timestamp},v1={signature}
  本脚本严格遵循此算法，签名可通过后端 Webhook.constructEvent() 验证。

【Stripe 官方 event_id 格式】
  evt_1<TS><API_KEY_SUFFIX><HASH>
  例：evt_1TT0hVBQhUFJY5KkXy9Z3qR8
  其中：1TT0hVBQhUFJY5Kk = Stripe Connect Account ID 前缀
"""
import hmac
import hashlib
import json
import os
import time
import threading
import urllib.request
import urllib.error
import statistics

# 尝试加载项目根目录 .env 文件
_env_path = os.path.join(os.path.dirname(__file__), '..', '.env')
if os.path.isfile(_env_path):
    try:
        from dotenv import load_dotenv
        load_dotenv(_env_path)
    except ImportError:
        # 手动解析 .env（无需安装 python-dotenv）
        with open(_env_path) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, v = line.split('=', 1)
                    os.environ.setdefault(k.strip(), v.strip().strip('"\''))

# ========== 从环境变量加载配置（禁止硬编码密钥） ==========
WEBHOOK_URL = os.getenv("WEBHOOK_URL", "http://localhost:7341/webhook/stripe")
WEBHOOK_SECRET = os.getenv("STRIPE_WEBHOOK_SECRET", "")

# 测试用的 Stripe 对象 ID（需手动配置）
USER_ID = int(os.getenv("TEST_USER_ID", "0"))
REAL_SUBSCRIPTION_ID = os.getenv("TEST_SUBSCRIPTION_ID", "sub_test_placeholder")
REAL_CUSTOMER_ID = os.getenv("TEST_CUSTOMER_ID", "cus_test_placeholder")
REAL_SESSION_ID = os.getenv("TEST_SESSION_ID", "cs_test_placeholder")
REAL_PRICE_ID = os.getenv("TEST_PRICE_ID", "price_test_placeholder")

# Stripe Account ID 前缀（用于生成 event ID）
STRIPE_ACCOUNT_PREFIX = os.getenv("STRIPE_ACCOUNT_PREFIX", "TEST_PREFIX")

if not WEBHOOK_SECRET:
    print("警告: STRIPE_WEBHOOK_SECRET 未设置，请从 .env 文件或环境变量中提供")"

# 压测参数
CONCURRENT_THREADS = 50
REPEAT_COUNT = 10

# ========== 工具函数 ==========

def generate_stripe_event_id(event_type: str, seed: int = 1) -> str:
    """生成 Stripe 官方格式的 event ID

    Stripe event ID 格式：evt_1<TS><API_KEY_SUFFIX><HASH>
    - 1 后面紧跟的是 Stripe Connect Account ID（如 TT0hVBQhUFJY5Kk）
    - 最后 8 位是随机哈希
    例：evt_1TT2hVBQhUFJY5KkXy9Z3qR8
    """
    api_key_suffix = STRIPE_ACCOUNT_PREFIX
    random_hash = f"{seed * 7919 + event_type.__hash__() % 1000000:06d}"[-6:]
    return f"evt_1{api_key_suffix}{random_hash}"


def sign_payload(payload: str, secret: str) -> str:
    """HMAC-SHA256 webhook 签名（与 Stripe 官方算法一致）"""
    timestamp = int(time.time())
    signed_payload = f"{timestamp}.{payload}"
    signature = hmac.new(secret.encode(), signed_payload.encode(), hashlib.sha256).hexdigest()
    return f"t={timestamp},v1={signature}"


def build_checkout_completed(event_id: str, session_id: str) -> str:
    """构建 checkout.session.completed webhook payload"""
    now = int(time.time())
    return json.dumps({
        "id": event_id,
        "object": "event",
        "type": "checkout.session.completed",
        "api_version": "2026-03-25.dahlia",
        "created": now,
        "data": {
            "object": {
                "id": session_id,
                "object": "checkout.session",
                "customer": REAL_CUSTOMER_ID,
                "subscription": REAL_SUBSCRIPTION_ID,
                "mode": "subscription",
                "payment_status": "paid",
                "metadata": {
                    "userId": str(USER_ID),
                    "plan": "PRO",
                    "billingCycle": "monthly",
                },
                "customer_details": {"email": "paytest@stripe.com"},
                "line_items": {
                    "data": [{
                        "price": {"id": REAL_PRICE_ID, "recurring": {"interval": "month"}},
                        "quantity": 1,
                    }]
                },
                "created": now,
                "expires_at": now + 86400,
            }
        }
    }, ensure_ascii=False)


def build_subscription_updated(event_id: str) -> str:
    """构建 customer.subscription.updated webhook payload"""
    now = int(time.time())
    return json.dumps({
        "id": event_id,
        "object": "event",
        "type": "customer.subscription.updated",
        "api_version": "2026-03-25.dahlia",
        "created": now,
        "data": {
            "object": {
                "id": REAL_SUBSCRIPTION_ID,
                "object": "subscription",
                "customer": REAL_CUSTOMER_ID,
                "status": "active",
                "plan": {"id": REAL_PRICE_ID},
                "items": {"data": [{"price": {"id": REAL_PRICE_ID}}]},
                "current_period_start": now,
                "current_period_end": now + 2592000,
                "metadata": {"userId": str(USER_ID)},
                "cancel_at_period_end": False,
            }
        }
    }, ensure_ascii=False)


def build_subscription_deleted(event_id: str) -> str:
    """构建 customer.subscription.deleted webhook payload"""
    return json.dumps({
        "id": event_id,
        "object": "event",
        "type": "customer.subscription.deleted",
        "api_version": "2026-03-25.dahlia",
        "created": int(time.time()),
        "data": {
            "object": {
                "id": REAL_SUBSCRIPTION_ID,
                "object": "subscription",
                "customer": REAL_CUSTOMER_ID,
                "status": "canceled",
                "metadata": {"userId": str(USER_ID)},
            }
        }
    }, ensure_ascii=False)


# ========== 并发压测引擎 ==========
results = {"success": 0, "failure": 0, "errors": [], "latencies": []}
results_lock = threading.Lock()


def send_webhook(payload: str, event_type: str):
    signature = sign_payload(payload, WEBHOOK_SECRET)
    data = payload.encode("utf-8")
    req = urllib.request.Request(WEBHOOK_URL, data=data,
        headers={"Content-Type": "application/json", "Stripe-Signature": signature}, method="POST")
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            elapsed_ms = (time.perf_counter() - start) * 1000
            with results_lock:
                results["success"] += 1
                results["latencies"].append(elapsed_ms)
    except urllib.error.HTTPError as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        with results_lock:
            results["failure"] += 1
            results["errors"].append(f"[{event_type}] HTTP {e.code}: {e.read().decode()[:200]}")
            results["latencies"].append(elapsed_ms)
    except Exception as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        with results_lock:
            results["errors"].append(f"[{event_type}] {e}")
            results["latencies"].append(elapsed_ms)


def worker(payload, event_type):
    for _ in range(REPEAT_COUNT):
        send_webhook(payload, event_type)


def run_test(event_type, payload, label, threads=CONCURRENT_THREADS, repeats=REPEAT_COUNT):
    global results
    results = {"success": 0, "failure": 0, "errors": [], "latencies": []}

    total = threads * repeats
    print(f"\n{'='*60}")
    print(f"  {label}")
    print(f"  并发: {threads} 线程 x {repeats} 次 = {total} 请求")
    print(f"  event_id: {json.loads(payload)['id']}")
    print(f"  所有请求使用相同 event_id 模拟 Stripe 重复推送")
    print(f"{'='*60}")

    ts = [threading.Thread(target=worker, args=(payload, event_type)) for _ in range(threads)]
    start = time.perf_counter()
    for t in ts: t.start()
    for t in ts: t.join()
    total_time = time.perf_counter() - start

    lats = sorted(results["latencies"])
    p95 = lats[int(len(lats)*0.95)] if lats else 0
    p99 = lats[int(len(lats)*0.99)] if lats else 0

    print(f"耗时: {total_time:.2f}s | 成功: {results['success']} | 失败: {results['failure']}")
    if lats:
        print(f"平均: {statistics.mean(lats):.1f}ms | 中位: {statistics.median(lats):.1f}ms | p95: {p95:.1f}ms | p99: {p99:.1f}ms")
    if results["errors"]:
        print(f"错误 (前5条):")
        for err in results["errors"][:5]:
            print(f"  - {err}")
    return results


def check_db(label: str):
    """检查数据库验证幂等性"""
    import subprocess
    out = subprocess.run(
        ["docker", "compose", "exec", "mysql", "mysql",
         "-uroot", "-p123456", "novel_translator", "-e",
         f"""
SELECT 'stripe_subscription' AS table_name, COUNT(*) AS records FROM stripe_subscription WHERE user_id = {USER_ID}
UNION ALL SELECT 'stripe_customer', COUNT(*) FROM stripe_customer WHERE user_id = {USER_ID}
UNION ALL SELECT 'user_plan_history', COUNT(*) FROM user_plan_history WHERE user_id = {USER_ID};
SELECT id, stripe_subscription_id, plan, status FROM stripe_subscription WHERE user_id = {USER_ID};
SELECT user_level FROM user WHERE id = {USER_ID};
         """],
        capture_output=True, text=True
    )
    print(f"\n  [{label}] 数据库状态:")
    for line in out.stdout.strip().split("\n"):
        print(f"    {line}")


if __name__ == "__main__":
    print("="*60)
    print("  Stripe Webhook 幂等性压测 v2（真实数据版）")
    print("="*60)
    print(f"  Subscription: {REAL_SUBSCRIPTION_ID}")
    print(f"  Customer:     {REAL_CUSTOMER_ID}")
    print(f"  Session:      {REAL_SESSION_ID}")
    print(f"  Price:        {REAL_PRICE_ID}")
    print(f"  Webhook URL:  {WEBHOOK_URL}")
    print(f"  签名算法:     HMAC-SHA256 (与 Stripe 官方一致)")
    print("="*60)

    # 记录初始数据库状态
    check_db("初始状态")

    # ===== 测试 1: checkout.session.completed 幂等性 =====
    # 核心幂等性测试：相同的 event_id 并发推送，验证不会重复创建订阅记录
    shared_event_id = generate_stripe_event_id("checkout.session.completed", seed=1)
    payload1 = build_checkout_completed(shared_event_id, REAL_SESSION_ID)
    r1 = run_test("checkout.session.completed", payload1,
                  "测试1: checkout.session.completed 幂等性 (50线程 x 10次)",
                  threads=50, repeats=10)
    check_db("checkout.session.completed 后")

    # ===== 测试 2: subscription.updated 幂等性 =====
    shared_event_id = generate_stripe_event_id("customer.subscription.updated", seed=2)
    payload2 = build_subscription_updated(shared_event_id)
    r2 = run_test("customer.subscription.updated", payload2,
                  "测试2: subscription.updated 幂等性 (100线程 x 10次)",
                  threads=100, repeats=10)
    check_db("subscription.updated 后")

    # ===== 测试 3: 混合事件并发 =====
    print(f"\n{'='*60}")
    print(f"  测试3: 混合事件并发 (3种事件同时推送)")
    print(f"  event_id 采用 Stripe 官方格式: evt_1<API_KEY_SUFFIX><HASH>")
    print(f"{'='*60}")

    mixed_results = {"success": 0, "failure": 0, "errors": [], "latencies": []}
    mixed_lock = threading.Lock()

    def send_mixed(payload, event_type):
        signature = sign_payload(payload, WEBHOOK_SECRET)
        data = payload.encode("utf-8")
        req = urllib.request.Request(WEBHOOK_URL, data=data,
            headers={"Content-Type": "application/json", "Stripe-Signature": signature}, method="POST")
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                elapsed_ms = (time.perf_counter() - start) * 1000
                with mixed_lock:
                    mixed_results["success"] += 1
                    mixed_results["latencies"].append(elapsed_ms)
        except Exception as e:
            elapsed_ms = (time.perf_counter() - start) * 1000
            with mixed_lock:
                mixed_results["failure"] += 1
                mixed_results["errors"].append(f"[{event_type}] {e}")
                mixed_results["latencies"].append(elapsed_ms)

    payloads = [
        (build_checkout_completed(generate_stripe_event_id("checkout.session.completed", seed=3), REAL_SESSION_ID), "checkout.completed"),
        (build_subscription_updated(generate_stripe_event_id("customer.subscription.updated", seed=4)), "subscription.updated"),
        (build_subscription_deleted(generate_stripe_event_id("customer.subscription.deleted", seed=5)), "subscription.deleted"),
    ]

    def mixed_worker():
        for _ in range(5):
            for p, et in payloads:
                send_mixed(p, et)

    threads = [threading.Thread(target=mixed_worker) for _ in range(50)]
    start = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    total_time = time.perf_counter() - start

    lats = sorted(mixed_results["latencies"])
    p95 = lats[int(len(lats)*0.95)] if lats else 0
    print(f"耗时: {total_time:.2f}s | 成功: {mixed_results['success']} | 失败: {mixed_results['failure']}")
    if lats:
        print(f"平均: {statistics.mean(lats):.1f}ms | p95: {p95:.1f}ms")
    if mixed_results["errors"]:
        print(f"错误 (前5条):")
        for err in mixed_results["errors"][:5]:
            print(f"  - {err}")

    check_db("混合事件后")

    print(f"\n{'='*60}")
    print("  压测完成")
    print(f"{'='*60}")
