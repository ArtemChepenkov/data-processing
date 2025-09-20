import socket, struct, os, time
from concurrent.futures import ThreadPoolExecutor

def run_client(host, port, name, idx, delay=0, do_exit=False, outdir="out"):
    try:
        s = socket.create_connection((host, port), timeout=30)
        s.sendall(name.encode('ascii') + b'\x00')
        if do_exit:
            s.close()
            return (idx, "exit")

        if delay:
            time.sleep(delay)

        data = s.recv(4)
        if len(data) < 4:
            return (idx, "no-len1")
        len1 = struct.unpack(">I", data)[0]
        priv = s.recv(len1, socket.MSG_WAITALL)

        data = s.recv(4)
        if len(data) < 4:
            return (idx, "no-len2")
        len2 = struct.unpack(">I", data)[0]
        cert = s.recv(len2, socket.MSG_WAITALL)

        os.makedirs(outdir, exist_ok=True)
        with open(os.path.join(outdir, f"{name}_{idx}.key"), "wb") as f:
            f.write(priv)
        with open(os.path.join(outdir, f"{name}_{idx}.crt"), "wb") as f:
            f.write(cert)

        s.close()
        return (idx, "ok")
    except Exception as e:
        return (idx, f"err:{e}")

def main():
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--name", default="bob")
    p.add_argument("--clients", type=int, default=5)
    p.add_argument("--workers", type=int, default=5)
    args = p.parse_args()

    results = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [ex.submit(run_client, args.host, args.port, args.name, i) for i in range(args.clients)]
        for f in futures:
            try:
                res = f.result(timeout=20)
                print(res)
                results.append(res)
            except Exception as e:
                print(f"Future failed: {e}")

    print("=== Итог ===")
    from collections import Counter
    print(Counter([r[1] for r in results]))

if __name__ == "__main__":
    main()
