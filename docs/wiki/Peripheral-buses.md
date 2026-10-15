# Storage and network buses

The CPU accesses RAM, storage and networking through separate address spaces. Data transfers remain byte-wide. Existing opcode encodings stay unchanged; network access adds opcodes 29 and 30. System-image revision 13 updates the DOS loader descriptors and drive command while preserving files.

## Multiple storage devices

Device 0 boots from `storage.img`. To add media, set IntelliJ's **Program arguments**, for example:

```text
--disk=1=secondary.img --disk=2=third.img
```

Missing images are created as empty 16 MiB media. Existing images must be exactly 16 MiB. Paths with spaces require quoting the complete argument. Device IDs are 1-255 for these arguments; 0 is reserved for the normal boot image. A single image cannot be mounted under two IDs. Host code can also call `Storage.mount(id, path)` while the CPU is stopped. Mounts last for that emulator session; contents persist in their respective files.

In DOS:

```text
DRIVE 1
MKDIR DOCS
CD DOCS
WRITE HELLO.TXT SECOND DISK
DRIVE 0
DIR
```

`DRIVE` shows the selected device. `DRIVE N` validates that it is mounted, then selects bank 2 and its root directory. Invalid or absent IDs preserve the current selection. Each medium has its own banks, files and folder metadata. COPY/REN operate within the current folder on the current drive; cross-drive copying is not implemented. Boot and DOS REBOOT select device 0.

For bytecode, control register 10 (`STORAGE_PAGE`, retained for compatibility) supplies the unsigned device ID for opcodes 18/19. Their x/y/z registers still form the 24-bit byte address; no RAM address is involved. `READ_INPUT 0x40 out` returns 1 if the currently selected storage device is mounted, otherwise 0. Direct reads/writes to an absent device raise an emulator error; check presence first. Host `Storage.readData`/`writeData` overloads accept a device ID, while the original signatures and installer range APIs always address device 0. Programs must restore their caller's selector before returning unless intentionally switching DOS's current device.

## Network register bus

`READ_NETWORK addressRegister destinationRegister` (29 / `1D`) reads one byte. `WRITE_NETWORK addressRegister sourceRegister` (30 / `1E`) writes one byte. The **value** in the address register selects the network register, independently of RAM and storage. The network controller has one outbound TCP socket and uses the host's configured connection; no Wi-Fi credentials are needed in the guest.

| Address | Read | Write |
| --- | --- | --- |
| 0 | Status flags | Command: 1 connect, 2 send, 3 disconnect, 4 reset |
| 1 | Error code | Any value clears error |
| 2 | 0 | Append printable ASCII hostname byte; 0 clears hostname |
| 3 | TCP port high byte | Set TCP port high byte |
| 4 | TCP port low byte | Set TCP port low byte |
| 5 | 0 | Append a byte to transmit buffer |
| 6 | Pop one received byte, or 0 when empty | Invalid register write |
| 7 | Queued receive count, high byte | Invalid register write |
| 8 | Queued receive count, low byte | Invalid register write |

Status bits: `01` connecting, `02` connected, `04` receive data available, `08` send busy, `10` peer EOF, `80` error (hex masks, combinable). Error codes: 0 none, 1 invalid command/register write, 2 invalid hostname/port, 3 connection failure, 4 transport/not-connected failure, 5 buffer overflow, 6 busy. Error status is sticky until cleared, disconnect, reset or the next accepted connect. Undefined register reads return 0. Empty receives also return 0: check status/count to distinguish an empty queue from a real zero byte. The two count registers are not an atomic snapshot; data may arrive between reads. The RX-ready flag is sufficient for consuming individual bytes.

Set the hostname (up to 253 printable ASCII bytes) and a nonzero port, then issue connect. Poll status until connected or error. Connect clears old transmit/receive buffers and EOF. To send, append up to 4,096 bytes at register 5 and issue command 2. Sending takes a snapshot of that buffer; while send is busy you may stage the next buffer, but wait for busy to clear before issuing another send command. Excess bytes are rejected with overflow instead of overwriting earlier data. A successful send means bytes were passed to the host socket, not acknowledged by the application on the remote peer.

Received bytes queue up to 65,535 bytes. When full, the worker stops draining the socket until the guest consumes bytes, applying TCP backpressure. The socket connect timeout is five seconds after hostname resolution; DNS and transfers run on background virtual threads. Byte instructions themselves never wait on network operations. A peer can take indefinitely to send or accept data; guest software should implement its own operation timeout and disconnect to cancel. EOF disconnects the socket but retains queued received bytes for draining. This version does not support TCP half-close.

Disconnect cancels in-flight work, clears queues/status/errors and retains hostname/port. Reset also clears hostname/port. Results from canceled sessions cannot modify the next session. Startup makes no connection; only a guest connect command opens a socket. JVM shutdown closes it. Connections can reach whatever destinations the host OS and network allow. This controller supplies raw TCP; TLS, UDP, a web browser, radio/SSID simulation and inbound listening sockets are not implemented.

## Guest example

`examples/dos/NETECHO.hex` and its compiled `NETECHO.COM` connect to `127.0.0.1:8080`, send the byte `H`, print reply bytes to the **host console** using CPU OUTPUT, and return to DOS after the peer closes. OUTPUT renders signed numeric byte values. The final printed value is the network status. Rebuild the COM using `examples/build.ps1`; import it as Binary/COM in Disk Workshop and run `RUN NETECHO.COM`.

For a local test, start this one-connection Python server before running the guest:

```python
import socket
with socket.socket() as server:
    server.bind(("127.0.0.1", 8080))
    server.listen(1)
    connection, _ = server.accept()
    with connection:
        print("Guest sent:", connection.recv(1))
        connection.sendall(b"OK\n")
```

Expected reply numbers are 79, 75, 10, followed by EOF status 16. The peer must close to finish this example. The regression suite runs the same guest program against a local server on an ephemeral port, as well as direct CPU/device isolation, binary-byte and queue-limit checks. It requires no external internet service.
