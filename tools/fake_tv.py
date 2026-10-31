"""A fake Android TV for testing remote apps: polo pairing on 6467, remote protocol on 6466.

Usage: python tools/fake_tv.py [outdir] [bind-address] [name]
Writes the pairing code to <outdir>/code.txt and logs every command it receives to stdout.
Point the app at this machine's IP (10.0.2.2 from the Android emulator).
"""
import datetime, hashlib, os, secrets, socket, sys, threading, time
from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from OpenSSL import SSL

outdir = sys.argv[1] if len(sys.argv) > 1 else "."
bind = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
tv_name = sys.argv[3] if len(sys.argv) > 3 else "Fake Xstream"
# The protobuf schemas come from androidtvremote2 (pip install androidtvremote2), the reference implementation.
from androidtvremote2 import polo_pb2 as polo, remotemessage_pb2 as rm

key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, f"atvremote/fake/fake/{tv_name}/AA:BB:CC:DD:EE:FF")])
now = datetime.datetime.now(datetime.timezone.utc)
cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
        .serial_number(7).not_valid_before(now).not_valid_after(now + datetime.timedelta(days=5)).sign(key, hashes.SHA256()))
paired = set()
# Behaves like the Airtel Xstream box and BPL TV did in testing: apps open through their own
# scheme, https links raise the "Open with" chooser (package "android"), market:// and other
# package links are refused with a remote_error, Home goes to the home screen, Power toggles standby.
current_app = "com.airtel.tv"
is_on = True
SCHEMES = {"netflix://": "com.netflix.ninja", "vnd.youtube://": "com.google.android.youtube.tv", "hotstar://": "in.startv.hotstar"}
HOME_APP = "com.airtel.tv"

def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)

def context():
    ctx = SSL.Context(SSL.TLS_SERVER_METHOD)
    ctx.use_privatekey(key)
    ctx.use_certificate(cert)
    ctx.set_verify(SSL.VERIFY_PEER, lambda *args: True)  # accept any self-signed client cert, like a TV
    return ctx

def recv_exact(conn, n):
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk: raise EOFError
        buf += chunk
    return buf

def read_frame(conn):
    length, shift = 0, 0
    while True:
        b = recv_exact(conn, 1)[0]
        length |= (b & 0x7F) << shift
        if not b & 0x80: break
        shift += 7
    return recv_exact(conn, length)

def write_frame(conn, msg):
    data = msg.SerializeToString(); n = len(data); hdr = b""
    while True:
        if n < 0x80: hdr += bytes([n]); break
        hdr += bytes([(n & 0x7F) | 0x80]); n >>= 7
    conn.sendall(hdr + data)

def numbers(c): p = c.public_key().public_numbers(); return p.n, p.e

def handle_pairing(conn):
    client = conn.get_peer_certificate().to_cryptography()
    tail = secrets.token_hex(2).upper()
    while True:
        m = polo.OuterMessage(); m.ParseFromString(read_frame(conn))
        r = polo.OuterMessage(); r.protocol_version = 2; r.status = 200
        if m.HasField("pairing_request"):
            log("PAIR request from", repr(m.pairing_request.client_name), "service", m.pairing_request.service_name)
            r.pairing_request_ack.server_name = tv_name
        elif m.HasField("options"):
            r.options.preferred_role = 1
            e = r.options.input_encodings.add(); e.type = 3; e.symbol_length = 6
        elif m.HasField("configuration"):
            r.configuration_ack.SetInParent()
            cn, ce = numbers(client); sn, se = numbers(cert)
            h = hashlib.sha256()
            for part in (f"{cn:X}", f"0{ce:X}", f"{sn:X}", f"0{se:X}", tail): h.update(bytes.fromhex(part))
            expected = h.digest()
            code = f"{expected[0]:02X}{tail}"
            open(os.path.join(outdir, "code.txt"), "w").write(code)
            log("PAIR showing code", code)
        elif m.HasField("secret"):
            if m.secret.secret == expected:
                paired.add(numbers(client)[0]); r.secret_ack.secret = m.secret.secret; log("PAIR success")
            else:
                r.status = 402; log("PAIR bad secret")
        write_frame(conn, r)

def handle_remote(conn):
    client = conn.get_peer_certificate().to_cryptography()
    if numbers(client)[0] not in paired:
        log("REMOTE rejected unpaired client"); return
    log("REMOTE connected")
    m = rm.RemoteMessage(); m.remote_configure.code1 = 622
    m.remote_configure.device_info.model = tv_name; m.remote_configure.device_info.vendor = "Fake"
    write_frame(conn, m)
    stop = threading.Event()
    def pinger():
        i = 0
        while not stop.wait(5):
            i += 1; p = rm.RemoteMessage(); p.remote_ping_request.val1 = i
            try: write_frame(conn, p)
            except Exception: return
    threading.Thread(target=pinger, daemon=True).start()
    try:
        while True:
            m = rm.RemoteMessage(); m.ParseFromString(read_frame(conn))
            if m.HasField("remote_configure"):
                log("REMOTE client features", m.remote_configure.code1, "pkg", m.remote_configure.device_info.package_name)
                a = rm.RemoteMessage(); a.remote_set_active.SetInParent(); write_frame(conn, a)
            elif m.HasField("remote_set_active"):
                s = rm.RemoteMessage(); s.remote_start.started = is_on; write_frame(conn, s)
                v = rm.RemoteMessage(); v.remote_set_volume_level.volume_max = 100; v.remote_set_volume_level.volume_level = 23; write_frame(conn, v)
                c = rm.RemoteMessage(); c.remote_ime_key_inject.app_info.app_package = current_app; write_frame(conn, c)
            elif m.HasField("remote_ping_response"):
                pass
            elif m.HasField("remote_key_inject"):
                k = m.remote_key_inject
                name = rm.RemoteKeyCode.Name(k.key_code)
                log("KEY", name, rm.RemoteDirection.Name(k.direction))
                if name == "KEYCODE_HOME":
                    threading.Timer(1.0, switch_app, (conn, HOME_APP)).start()
                elif name == "KEYCODE_BACK" and current_app == "android":
                    switch_app(conn, previous_app)
                elif name == "KEYCODE_POWER":
                    set_power(conn, not is_on)
            elif m.HasField("remote_app_link_launch_request"):
                link = m.remote_app_link_launch_request.app_link
                log("LINK", link)
                pkg = next((p for scheme, p in SCHEMES.items() if link.startswith(scheme)), None)
                if pkg:
                    switch_app(conn, pkg)
                elif link.startswith("https://"):
                    switch_app(conn, "android")
                else:
                    e = rm.RemoteMessage(); e.remote_error.value = True; e.remote_error.message.CopyFrom(m)
                    write_frame(conn, e); log("REJECTED", link)
            elif m.HasField("remote_ime_batch_edit"):
                log("TEXT", m.remote_ime_batch_edit.edit_info[0].text_field_status.value)
            else:
                log("OTHER", m)
    finally:
        stop.set()

previous_app = current_app


def switch_app(conn, pkg):
    global current_app, previous_app
    previous_app, current_app = current_app, pkg
    c = rm.RemoteMessage(); c.remote_ime_key_inject.app_info.app_package = pkg
    try: write_frame(conn, c); log("APP", pkg)
    except Exception: pass


def set_power(conn, on):
    global is_on
    is_on = on
    s = rm.RemoteMessage(); s.remote_start.started = on
    write_frame(conn, s); log("POWER", "on" if on else "standby")


def serve(port, handler):
    srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((bind, port)); srv.listen(5)
    while True:
        sock, addr = srv.accept()
        def run(sock=sock):
            conn = SSL.Connection(context(), sock); conn.set_accept_state()
            try:
                conn.do_handshake(); handler(conn)
            except (EOFError, SSL.ZeroReturnError, SSL.SysCallError, ConnectionError):
                pass
            except Exception as e:
                log("ERROR", port, type(e).__name__, e)
            finally:
                try: conn.shutdown()
                except Exception: pass
                sock.close()
        threading.Thread(target=run, daemon=True).start()

threading.Thread(target=serve, args=(6467, handle_pairing), daemon=True).start()
log(f"{tv_name} listening on {bind}: 6467 (pairing) and 6466 (remote)")
serve(6466, handle_remote)
