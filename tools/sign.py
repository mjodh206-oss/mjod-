#!/usr/bin/env python3
"""
موقّع APK حتمي بدون أدوات جاهزة — ينفّذ:
  v1 (JAR/HASHAEMUAL عبر openssl smime) + v2 + v3 (APK Signing Block)

الاستخدام:
  python3 tools/sign.py smart-unsigned.apk SmartRemote-release.apk /path/to/keydir

حيث keydir يحتوي: key.pem (مفتاح RSA خاص) و cert.pem (شهادة X.509 ذاتية التوقيع).
التشفير يعتمد فقط على openssl المتوفر في النظام + hashlib.
"""
import base64
import hashlib
import os
import struct
import subprocess
import sys
import zipfile

ALG_RSA_SHA256 = 0x0103
SIG_V2 = 0x7109871A
SIG_V3 = 0xF05368C0
MAGIC = b"APK Sig Block 42"
EOCD_SIG = 0x06054B50
CD_SIG = 0x02014B50
LD_SIG = 0x04034B50
CHUNK = 1 << 21  # 2MB

# ───────────────────────── أدوات مساعدة ─────────────────────────

def u32le(n): return struct.pack("<I", n)
def u64le(n): return struct.pack("<Q", n)
def length_prefixed(b): return u32le(len(b)) + b
def seq_field(items):
    return u32le(len(b"".join(length_prefixed(i) for i in items))) + b"".join(length_prefixed(i) for i in items)

def h256(b): return hashlib.sha256(b).digest()

def run_openssl(args, data_in=None):
    r = subprocess.run(["openssl"] + args, input=data_in, capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(f"openssl {' '.join(args)} failed: {r.stderr.decode('utf-8', 'ignore')}")
    return r.stdout

def find_eocd(data):
    # ابحث من النهاية عن توقيع EOCD وتحقق من أن الإزاحة+التعليق تُغلق بآخر الملف بالضبط
    pos = len(data) - 22
    while pos >= 0:
        if data[pos:pos+4] == b"PK\x05\x06":
            comment_len = struct.unpack_from("<H", data, pos + 20)[0]
            if pos + 22 + comment_len == len(data):
                return pos
        pos -= 1
    raise RuntimeError("EOCD not found")

def read_apk_chunks(data):
    # EOCD scan from the end (validated against comment length)
    i = find_eocd(data)
    (sig, disk, cd_disk, entries_disk, entries_tot, cd_size, cd_offset, comment_len) = struct.unpack_from("<IHHHHIIH", data, i)
    if data[i + 22:] != data[i + 22: i + 22 + comment_len]:
        pass
    eocd = data[i: i + 22 + comment_len]
    cd = data[cd_offset: cd_offset + cd_size]
    content = data[:cd_offset]
    assert len(content) + len(cd) + len(eocd) == len(data), "layout mismatch"
    return content, cd, eocd, cd_offset, i

def rebuild_eocd(eocd_orig, new_cd_offset):
    buf = bytearray(eocd_orig)
    struct.pack_into("<I", buf, 16, new_cd_offset)
    return bytes(buf)

# ───────────────────────── v2/v3 ─────────────────────────

def make_digest_entry(d):
    return u32le(ALG_RSA_SHA256) + length_prefixed(d)

def signer_block_sizes(cert_len, pk_len, with_sdk):
    # digests = 3 × (4 + 4+4+32) + outer(4+..) → items: each u32le(len(digest))+digest
    d_items = 3 * (4 + (4 + 4 + 32))
    digests_field = 4 + d_items
    certs_field = 4 + (4 + cert_len)
    tail = 4
    if with_sdk:
        sd_len = digests_field + certs_field + 4 + 4 + tail
    else:
        sd_len = digests_field + certs_field + tail
    sigs_field = 4 + (4 + (4 + 4 + 256))  # single RSA signature
    signer = 4 + sd_len + 4 + sigs_field + 4 + pk_len
    value = 4 + (4 + signer)              # signers: u32le(len(signers-field)) + signer entries... each signer length-prefixed
    return value

def build_v2v3(content, cd, eocd_final, cert_der, key_path, min_sdk=21, max_sdk=0xFFFFFFFF):
    chunks = (content, cd, eocd_final)
    digests = [h256(c) for c in chunks]

    def build_sd(with_sdk):
        digests_field = seq_field([make_digest_entry(d) for d in digests])
        certs_field = seq_field([cert_der])
        tail = u32le(0)
        if with_sdk:
            return digests_field + certs_field + u32le(min_sdk) + u32le(max_sdk) + tail
        return digests_field + certs_field + tail

    def sign_sd(sd):
        return run_openssl(["dgst", "-sha256", "-sign", key_path], sd)

    def make_value(sd, sig, pk_pkcs1):
        sig_item = u32le(ALG_RSA_SHA256) + length_prefixed(sig)
        signatures = seq_field([sig_item])
        signer = length_prefixed(sd) + length_prefixed(signatures) + length_prefixed(pk_pkcs1)
        return seq_field([signer])

    sd2 = build_sd(with_sdk=False)
    sd3 = build_sd(with_sdk=True)
    sig2 = sign_sd(sd2)
    sig3 = sign_sd(sd3)

    pk_pkcs1 = run_openssl(["rsa", "-in", key_path, "-outform", "DER", "-pubout", "-RSAPublicKey_out"])

    v2_value = make_value(sd2, sig2, pk_pkcs1)
    v3_value = make_value(sd3, sig3, pk_pkcs1)

    pair2 = u64le(4 + len(v2_value)) + u32le(SIG_V2) + v2_value
    pair3 = u64le(4 + len(v3_value)) + u32le(SIG_V3) + v3_value
    pairs = pair2 + pair3

    S = len(pairs) + 24  # 8 (u64 ختامي) + 16 (magic)
    block = u64le(S) + pairs + u64le(S) + MAGIC
    return block

# ───────────────────────── v1 (JAR) ─────────────────────────

def v1_sign(src_apk, key_path, cert_path, out_apk):
    with zipfile.ZipFile(src_apk, "r") as zin:
        entries = [(i.filename, zin.read(i.filename)) for i in zin.infolist()]

    # MANIFEST.MF
    sections = []
    for name, data in entries:
        if name.endswith("/") or name.startswith("META-INF/"):
            continue
        sec = f"Name: {name}\r\nSHA-256-Digest: {base64.b64encode(h256(data)).decode()}\r\n"
        sections.append(sec)
    manifest = "Manifest-Version: 1.0\r\nCreated-By: SmartIR signer 1.0\r\n\r\n" + "".join(sec + "\r\n" for sec in sections)
    manifest_bytes = manifest.encode("utf-8")

    # CERT.SF
    sf_lines = [
        "Signature-Version: 1.0\r\n",
        "Created-By: SmartIR signer 1.0\r\n",
        f"SHA-256-Digest-Manifest: {base64.b64encode(h256(manifest_bytes)).decode()}\r\n",
        "\r\n",
    ]
    for sec in sections:
        sec_bytes = sec.encode("utf-8")
        sf_lines.append(f"Name: {sec.split('Name: ')[1].splitlines()[0]}\r\n")
        sf_lines.append(f"SHA-256-Digest: {base64.b64encode(h256(sec_bytes)).decode()}\r\n")
        sf_lines.append("\r\n")
    sf_bytes = "".join(sf_lines).encode("utf-8")

    cert_rsa = run_openssl(
        ["smime", "-sign", "-binary", "-in", "/dev/stdin", "-out", "/dev/stdout",
         "-outform", "DER", "-signer", cert_path, "-inkey", key_path, "-md", "sha256"],
        sf_bytes,
    )

    with zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zout:
        for name, data in entries:
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zout.writestr(zi, data)
        for name, data in (("META-INF/MANIFEST.MF", manifest_bytes),
                           ("META-INF/CERT.SF", sf_bytes),
                           ("META-INF/CERT.RSA", cert_rsa)):
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zout.writestr(zi, data)


# ───────────────────────── v1 التحقق ─────────────────────────

def verify_v1(apk_path, key_dir):
    with zipfile.ZipFile(apk_path, "r") as z:
        bad = z.testzip()
        assert bad is None, f"CRC mismatch in entry: {bad}"
        names = z.namelist()
        cert_pem = os.path.join(key_dir, "cert.pem")
        manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8")
        sf = z.read("META-INF/CERT.SF").decode("utf-8")
        rsa = z.read("META-INF/CERT.RSA")

    def sections(text):
        # يفصل أقسام JAR بالأسطر الفارغة، يعيد (name, raw_section_bytes)
        out = {}
        raw = text
        for chunk in raw.split("\r\n\r\n"):
            chunk = chunk.strip("\r\n")
            if not chunk or not chunk.startswith("Name: "):
                continue
            sec_bytes = (chunk + "\r\n").encode("utf-8")
            name = chunk.split("Name: ", 1)[1].splitlines()[0].strip()
            out[name] = sec_bytes
        return out

    ok = True
    man_sections = sections(manifest)
    sf_sections = sections(sf)
    with zipfile.ZipFile(apk_path, "r") as z:
        for name, data in ((i.filename, z.read(i.filename)) for i in z.infolist()):
            if name.startswith("META-INF/") or name.endswith("/"):
                continue
            entry_ok = base64.b64encode(h256(data)).decode() == _digest_of(man_sections.get(name), "SHA-256-Digest:")
            print(f"  v1 manifest {name}: {'✓' if entry_ok else '✗ FAIL'}")
            ok = ok and entry_ok
    for name, sec_bytes in man_sections.items():
        sf_ok = base64.b64encode(h256(sec_bytes)).decode() == _digest_of(sf_sections.get(name), "SHA-256-Digest:")
        if not sf_ok:
            print(f"  v1 SF {name}: ✗ FAIL")
            ok = False
    man_digest_ok = base64.b64encode(h256(manifest.encode("utf-8"))).decode() in sf
    print(f"  v1 SF manifest digest: {'✓' if man_digest_ok else '✗ FAIL'}")
    ok = ok and man_digest_ok

    # التحقق من توقيع PKCS#7 عبر openssl
    with open("/tmp/_cert.sf", "wb") as f: f.write(sf.encode("utf-8"))
    with open("/tmp/_cert.rsa", "wb") as f: f.write(rsa)
    r = subprocess.run(
        ["openssl", "smime", "-verify", "-binary", "-inform", "DER", "-in", "/tmp/_cert.rsa",
         "-content", "/tmp/_cert.sf", "-noverify", "-out", "/dev/null"],
        capture_output=True)
    smime_ok = r.returncode == 0
    print(f"  v1 PKCS#7 signature: {'✓ VALID' if smime_ok else '✗ FAIL: ' + r.stderr.decode('utf-8', 'ignore')[:200]}")
    return ok and smime_ok

def _digest_of(section_bytes, key):
    if not section_bytes:
        return None
    for line in section_bytes.decode("utf-8").splitlines():
        if line.startswith(key):
            return line.split(": ", 1)[1].strip()
    return None

# ───────────────────────── v2/v3 إدخال ─────────────────────────

def v23_sign(src_apk, key_path, cert_der, out_apk, min_sdk=21, max_sdk=0xFFFFFFFF):
    data = open(src_apk, "rb").read()
    content, cd, eocd, cd_offset, eocd_offset = read_apk_chunks(data)

    # المرحلة 1: حساب حجم البلوك بحساب الأبعاد فقط (طول الشهادة والتوقيع ثابتان)
    pk_pkcs1 = run_openssl(["rsa", "-in", key_path, "-outform", "DER", "-pubout", "-RSAPublicKey_out"])
    sd2_len = (4 + (3 * (4 + 4 + 4 + 32))) + (4 + (4 + len(cert_der))) + 4  # digests_field + certs_field + additionalAttributes
    sd3_len = (4 + (3 * (4 + 4 + 4 + 32))) + (4 + (4 + len(cert_der))) + 4 + 4 + 4
    sig_len = 256

    def value_len(sd_len):
        sigs_field = 4 + (4 + (4 + 4 + sig_len))
        signer = 4 + sd_len + 4 + sigs_field + 4 + len(pk_pkcs1)
        return 4 + (4 + signer)

    pair = lambda vid, vlen: 8 + 4 + vlen  # u64le(len) + u32le(id) + value
    pairs_len = pair(SIG_V2, value_len(sd2_len)) + pair(SIG_V3, value_len(sd3_len))
    S = pairs_len + 24
    block_total = 8 + S

    # المرحلة 2: التخطيط النهائي + التوقيعات الحقيقية
    new_cd_offset = cd_offset + block_total
    eocd_final = rebuild_eocd(eocd, new_cd_offset)
    block = build_v2v3(content, cd, eocd_final, cert_der, key_path, min_sdk, max_sdk)
    assert len(block) == block_total, f"block size mismatch: {len(block)} != {block_total} (sd2={sd2_len}, sd3={sd3_len}, pairs={pairs_len})"

    with open(out_apk, "wb") as f:
        f.write(content + block + cd + eocd_final)

# ───────────────────────── التحقق ─────────────────────────

def u32(b, off): return struct.unpack_from("<I", b, off)[0]
def u64(b, off): return struct.unpack_from("<Q", b, off)[0]

def parse_seq_field(buf, off):
    field_len = u32(buf, off)
    return buf[off + 4: off + 4 + field_len], off + 4 + field_len

def parse_length_prefixed(buf, off):
    item_len = u32(buf, off)
    return buf[off + 4: off + 4 + item_len], off + 4 + item_len

def parse_v2v3_value(value):
    signers, _ = parse_seq_field(value, 0)
    signer, _ = parse_length_prefixed(signers, 0)
    off = 0
    sd, off = parse_length_prefixed(signer, off)
    signatures, off = parse_length_prefixed(signer, off)
    public_key, off = parse_length_prefixed(signer, off)
    sd_off = 0
    digests, sd_off = parse_seq_field(sd, sd_off)
    certificates, sd_off = parse_seq_field(sd, sd_off)
    return digests, certificates, signatures, public_key, sd

def verify(apk_path, key_dir):
    data = open(apk_path, "rb").read()
    i = find_eocd(data)
    (_, _, _, _, _, cd_size, cd_offset, comment_len) = struct.unpack_from("<IHHHHIIH", data, i)
    eocd = data[i: i + 22 + comment_len]
    cd = data[cd_offset: cd_offset + cd_size]
    # موقع البلوك مباشرة قبل الـ CD
    magic_pos = cd_offset - 16
    assert data[magic_pos: magic_pos + 16] == MAGIC, "magic mismatch"
    S = u64(data, magic_pos - 8)
    block_start = cd_offset - 8 - S
    trailing = u64(data, block_start)
    assert trailing == S, "block size trailer mismatch"
    pairs_start = block_start + 8
    pairs_end = cd_offset - 24
    content = data[:block_start]

    ok = True
    for kind, vid in (("v2", SIG_V2), ("v3", SIG_V3)):
        off = pairs_start
        found = None
        while off < pairs_end:
            plen = u64(data, off)
            pid = u32(data, off + 8)
            pval = data[off + 12: off + 8 + plen]
            off += 8 + plen
            if pid == vid:
                found = pval
                break
        assert found is not None, f"{kind}: block not found"
        digests_b, certs_b, signatures_b, public_key, sd = parse_v2v3_value(found)
        # إعادة حساب التجزئات
        exp = [h256(content), h256(cd), h256(eocd)]
        off = 0
        got = []
        while off < len(digests_b):
            item, off = parse_length_prefixed(digests_b, off)
            alg = u32(item, 0)
            dlen = u32(item, 4)
            got.append(item[8:8 + dlen])
        digest_ok = got == exp
        # التحقق من التوقيع: signatures = seq_field([sig_item]) داخل حقل lp في signer
        sigs_content, _ = parse_seq_field(signatures_b, 0)   # lp(sig_item)
        sig_item, _ = parse_length_prefixed(sigs_content, 0)  # alg + lp(sig)
        alg = u32(sig_item, 0)
        sig_len = u32(sig_item, 4)
        sig = sig_item[8:8 + sig_len]
        print(f"  {kind}: alg={alg} sig_len={sig_len}")
        with open("/tmp/_sd.bin", "wb") as f: f.write(sd)
        with open("/tmp/_sig.bin", "wb") as f: f.write(sig)
        cert_files = []
        offc = 0
        ci = 0
        while offc < len(certs_b):
            cert, offc = parse_length_prefixed(certs_b, offc)
            cf = f"/tmp/_cert_{kind}_{ci}.der"
            open(cf, "wb").write(cert)
            cert_files.append(cf)
            ci += 1
        pub = "/tmp/_pub.pem"
        run_openssl(["x509", "-inform", "DER", "-in", cert_files[0], "-pubkey", "-noout", "-out", pub])
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", pub, "-signature", "/tmp/_sig.bin", "/tmp/_sd.bin"], capture_output=True)
        sig_ok = r.returncode == 0
        print(f"  {kind}: digests {'✓' if digest_ok else '✗ FAIL'} · RSA signature {'✓ VALID' if sig_ok else '✗ FAIL'} ({len(cert_files)} شهادة)")
        ok = ok and digest_ok and sig_ok
    return ok

def main():
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    src, dst, key_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    key_path = os.path.join(key_dir, "key.pem")
    cert_path = os.path.join(key_dir, "cert.pem")
    cert_der = run_openssl(["x509", "-in", cert_path, "-outform", "DER"])

    tmp_v1 = dst + ".v1.tmp.apk"
    print("1/3 — توقيع v1 (JAR)…")
    v1_sign(src, key_path, cert_path, tmp_v1)
    print("2/3 — توقيع v2 + v3 (APK Signing Block)…")
    v23_sign(tmp_v1, key_path, cert_der, dst, min_sdk=21)
    os.remove(tmp_v1)
    print("3/4 — التحقق المستقل من v1…")
    ok_v1 = verify_v1(dst, key_dir)
    print("4/4 — التحقق المستقل من v2 + v3…")
    ok = verify(dst, key_dir)
    if not (ok and ok_v1):
        print("❌ فشل التحقق — لا تستخدم هذا الملف")
        sys.exit(2)
    print(f"✅ تم: {dst} ({os.path.getsize(dst) / 1048576:.2f} MB)")

if __name__ == "__main__":
    main()
