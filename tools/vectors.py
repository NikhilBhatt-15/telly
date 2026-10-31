"""Emit protocol test vectors using androidtvremote2's own protobuf schemas and pairing math.

Usage: python tools/vectors.py > app/src/test/resources/vectors.json
"""
import hashlib, json
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import datetime

# The protobuf schemas come from androidtvremote2 (pip install androidtvremote2), the reference implementation.
from androidtvremote2 import polo_pb2 as polo, remotemessage_pb2 as rm

def outer():
    m = polo.OuterMessage(); m.protocol_version = 2; m.status = polo.OuterMessage.Status.STATUS_OK; return m

v = {}
m = outer(); m.pairing_request.client_name = "Telly"; m.pairing_request.service_name = "atvremote"; v["pairing_request"] = m
m = outer(); m.options.preferred_role = polo.Options.RoleType.ROLE_TYPE_INPUT
e = m.options.input_encodings.add(); e.type = polo.Options.Encoding.ENCODING_TYPE_HEXADECIMAL; e.symbol_length = 6; v["options"] = m
m = outer(); m.configuration.client_role = polo.Options.RoleType.ROLE_TYPE_INPUT
m.configuration.encoding.type = polo.Options.Encoding.ENCODING_TYPE_HEXADECIMAL; m.configuration.encoding.symbol_length = 6; v["configuration"] = m
m = outer(); m.secret.secret = bytes(range(1, 33)); v["secret"] = m
m = outer(); m.configuration_ack.SetInParent(); v["configuration_ack"] = m
m = rm.RemoteMessage(); m.remote_configure.code1 = 615; d = m.remote_configure.device_info
d.unknown1 = 1; d.unknown2 = "1"; d.package_name = "atvremote"; d.app_version = "1.0.0"; v["configure_reply"] = m
m = rm.RemoteMessage(); m.remote_set_active.active = 615; v["set_active"] = m
m = rm.RemoteMessage(); m.remote_ping_response.val1 = 7; v["ping_response"] = m
m = rm.RemoteMessage(); m.remote_key_inject.key_code = rm.RemoteKeyCode.KEYCODE_POWER; m.remote_key_inject.direction = rm.RemoteDirection.SHORT; v["key_power"] = m
m = rm.RemoteMessage(); m.remote_app_link_launch_request.app_link = "https://www.netflix.com/title"; v["app_link"] = m
m = rm.RemoteMessage(); b = m.remote_ime_batch_edit; b.ime_counter = 3; b.field_counter = 5
ei = b.edit_info.add(); ei.insert = 1; ei.text_field_status.start = 4; ei.text_field_status.end = 4; ei.text_field_status.value = "hello"; v["ime_text"] = m
# messages the TV sends, for the parser
m = rm.RemoteMessage(); m.remote_configure.code1 = 622; m.remote_configure.device_info.model = "XStream"; m.remote_configure.device_info.vendor = "Airtel"; v["in_configure"] = m
m = rm.RemoteMessage(); s = m.remote_set_volume_level; s.volume_max = 100; s.volume_level = 37; s.volume_muted = True; s.player_model = "x"; v["in_volume"] = m
m = rm.RemoteMessage(); m.remote_ime_key_inject.app_info.app_package = "com.netflix.ninja"; m.remote_ime_key_inject.app_info.counter = 9; v["in_current_app"] = m
m = rm.RemoteMessage(); m.remote_start.started = True; v["in_start"] = m
m = rm.RemoteMessage(); m.remote_ping_request.val1 = 1234567; m.remote_ping_request.val2 = -5; v["in_ping"] = m
out = {k: msg.SerializeToString().hex() for k, msg in v.items()}

# Pairing secret exactly as androidtvremote2.pairing computes it.
def cert(cn):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, cn)])
    now = datetime.datetime.now(datetime.timezone.utc)
    c = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
         .serial_number(1000).not_valid_before(now).not_valid_after(now + datetime.timedelta(days=30))
         .sign(key, hashes.SHA256()))
    return c
cc, sc = cert("client"), cert("atvremote/box/box/Xstream/AA:BB:CC:DD:EE:0F")
cn, ce = cc.public_key().public_numbers().n, cc.public_key().public_numbers().e
sn, se = sc.public_key().public_numbers().n, sc.public_key().public_numbers().e
tail = "A1B2"
h = hashlib.sha256()
h.update(bytes.fromhex(f"{cn:X}")); h.update(bytes.fromhex(f"0{ce:X}"))
h.update(bytes.fromhex(f"{sn:X}")); h.update(bytes.fromhex(f"0{se:X}"))
h.update(bytes.fromhex(tail))
digest = h.digest()
out["pair_client_cert"] = cc.public_bytes(serialization.Encoding.DER).hex()
out["pair_server_cert"] = sc.public_bytes(serialization.Encoding.DER).hex()
out["pair_code"] = f"{digest[0]:02X}{tail}"
out["pair_secret"] = digest.hex()
print(json.dumps(out, indent=1))
