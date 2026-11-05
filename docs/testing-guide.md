# Testing Guide & Test Evidence

> Part 1 shows **how** to test a networked system. Part 2 is **your evidence** — graded under B1/B2.
> All results must come from actually running your system (see `INSTRUCTION.md` §7).

---

## Part 1 — How to Test

### 1.1 Direct TCP protocol test

SCP/1.0 có prefix nhị phân 4 byte nên không thể gõ JSON trực tiếp bằng `nc`/`telnet`. Dùng một script TCP nhỏ để
kiểm tra server độc lập với JavaFX UI:

```python
import json, socket, struct

def send_frame(sock, message):
    body = json.dumps(message, ensure_ascii=False).encode("utf-8")
    sock.sendall(struct.pack(">I", len(body)) + body)

def recv_exact(sock, size):
    chunks = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise EOFError("connection closed during frame")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)

def recv_frame(sock):
    size = struct.unpack(">I", recv_exact(sock, 4))[0]
    return json.loads(recv_exact(sock, size).decode("utf-8"))

with socket.create_connection(("localhost", 5000), timeout=5) as sock:
    send_frame(sock, {
        "type": "HELLO",
        "requestId": "manual-1",
        "payload": {"protocolVersion": "SCP/1.0", "clientName": "manual-test"}
    })
    print(recv_frame(sock))
```

### 1.2 Concurrency / load test

Prove the server serves many clients at once. Example script (adapt the message to your protocol and framing):

```python
# tests/load_test.py — run on the host while the server is up
import json, os, socket, struct, threading, time

HOST = os.getenv("CLIENT_TARGET_HOST", "localhost")
PORT = int(os.getenv("CLIENT_TARGET_PORT", "5000"))
N = int(os.getenv("N_CLIENTS", "50"))
results = []

def recv_exact(sock, size):
    chunks = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise EOFError("connection closed during frame")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)

def client(i):
    start = time.time()
    try:
        with socket.create_connection((HOST, PORT), timeout=5) as s:
            message = {
                "type": "HELLO",
                "requestId": f"load-{i}",
                "payload": {"protocolVersion": "SCP/1.0", "clientName": "load-test"}
            }
            body = json.dumps(message).encode("utf-8")
            s.sendall(struct.pack(">I", len(body)) + body)
            size = struct.unpack(">I", recv_exact(s, 4))[0]
            response = recv_exact(s, size)
            results.append((i, True, time.time() - start, response[:60]))
            time.sleep(2)                # hold the connection open to overlap with others
    except Exception as e:
        results.append((i, False, time.time() - start, str(e)))

threads = [threading.Thread(target=client, args=(i,)) for i in range(N)]
for t in threads: t.start()
for t in threads: t.join()
ok = [r for r in results if r[1]]
print(f"{len(ok)}/{N} succeeded, max latency {max(r[2] for r in ok) if ok else 0:.3f}s")
```

Script này mới kiểm tra nhiều TCP connection cùng handshake. Test tải đầy đủ phải tạo account test riêng, đăng nhập
và giữ các connection đồng thời; không dùng chung một email vì SCP/1.0 chỉ cho một phiên online/account.

### 1.3 Abnormal disconnects

| Scenario | How | Expected |
|----------|-----|----------|
| Client killed | `Ctrl+C`, `kill -9 <pid>`, or `docker kill <container>` | Server detects EOF/reset, cleans up the session, keeps serving others |
| Client goes silent | connect and send nothing | Server times out the connection (if your protocol defines it) |
| Oversized prefix | Send `struct.pack(">I", 65537)` | Server returns 413 if possible, closes only that connection and does not allocate the declared body |
| Malformed JSON | Send a valid length prefix followed by invalid JSON three times | Server returns 400, then closes after the third strike |
| Split frame | Send prefix/body one byte at a time | Server reassembles exactly one request and responds once |
| Server stops | `docker compose stop server` | Client shows a clear error; optionally retries with backoff |
| JavaFX closes | Close window or kill client process | Socket closes; server removes session and broadcasts `USER_LEFT` |

### 1.4 Simulating a bad network

Inside a Linux container that has `iproute2` and the `NET_ADMIN` capability
(`cap_add: [NET_ADMIN]` in `docker-compose.yml`):

```bash
tc qdisc add dev eth0 root netem delay 100ms 20ms   # latency + jitter
tc qdisc change dev eth0 root netem loss 5%          # packet loss
tc qdisc del dev eth0 root                           # restore
```

---

## Part 2 — Our Test Evidence

| # | Test | Method | Result (paste output / screenshot link) | Pass? |
|:-:|------|--------|------------------------------------------|:-----:|
| 1 | N concurrent TCP clients | `tests/load_test.py`, N = | | |
| 2 | Register then login | Direct protocol/integration test | | |
| 3 | Duplicate register and simultaneous duplicate login | Server concurrency test | | |
| 4 | Two users chat in general channel | Browser E2E + protocol assertions | | |
| 5 | Client killed mid-session | Kill JavaFX process | | |
| 6 | Split/coalesced frames | Protocol codec/socket tests | | |
| 7 | Malformed/oversized frame | Protocol robustness tests | | |
| 8 | Pool saturation and recovery | Connect over configured max | | |

*(Add notes on bugs found by these tests and how you fixed them.)*
