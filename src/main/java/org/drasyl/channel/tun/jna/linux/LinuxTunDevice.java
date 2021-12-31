package org.drasyl.channel.tun.jna.linux;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.Tun6Packet;
import org.drasyl.channel.tun.TunPacket;
import org.drasyl.channel.tun.jna.TunDevice;
import org.drasyl.channel.tun.jna.shared.If.Ifreq;
import org.drasyl.channel.tun.jna.shared.LibC;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.jna.linux.Fcntl.O_RDWR;
import static org.drasyl.channel.tun.jna.linux.IfTun.IFF_NO_PI;
import static org.drasyl.channel.tun.jna.linux.IfTun.IFF_TUN;
import static org.drasyl.channel.tun.jna.linux.IfTun.TUNSETIFF;
import static org.drasyl.channel.tun.jna.linux.Sockios.SIOCGIFMTU;
import static org.drasyl.channel.tun.jna.shared.If.IFNAMSIZ;
import static org.drasyl.channel.tun.jna.shared.LibC.ioctl;
import static org.drasyl.channel.tun.jna.shared.LibC.read;
import static org.drasyl.channel.tun.jna.shared.LibC.socket;
import static org.drasyl.channel.tun.jna.shared.LibC.write;
import static org.drasyl.channel.tun.jna.shared.Socket.AF_INET;
import static org.drasyl.channel.tun.jna.shared.Socket.SOCK_DGRAM;

/**
 * {@link TunDevice} implementation for Linux-based platforms.
 */
public final class LinuxTunDevice implements TunDevice {
    private static final IllegalArgumentException ILLEGAL_NAME_EXCEPTION = new IllegalArgumentException("Device name must be an ASCII string shorter than 16 characters or null.");
    private final int fd;
    private final NativeLong mtu;
    private final String name;
    private boolean closed;

    private LinuxTunDevice(final int fd, final int mtu, final String name) {
        this.fd = fd;
        this.mtu = new NativeLong(mtu);
        this.name = requireNonNull(name);
    }

    public static TunDevice open(String name) throws IOException {
        if (name != null && name.isEmpty()) {
            name = null;
        }
        if (name != null && (name.length() >= IFNAMSIZ || !US_ASCII.newEncoder().canEncode(name))) {
            throw ILLEGAL_NAME_EXCEPTION;
        }

        // open tun device
        final int fd = LibC.open("/dev/net/tun", O_RDWR);

        if (fd == -1) {
            throw new IOException("Create an endpoint for communication failed.");
        }

        // configure/create actual tun device
        final Ifreq ifreq = new Ifreq(name, (short) (IFF_TUN | IFF_NO_PI));
        ioctl(fd, TUNSETIFF, ifreq);

        final String deviceName = Native.toString(ifreq.ifr_name, US_ASCII);

        // get mtu
        final int s = socket(AF_INET, SOCK_DGRAM, 0);
        final Ifreq ifreq2 = new Ifreq(deviceName);
        ioctl(s, SIOCGIFMTU, ifreq2);
        final int mtu = ifreq2.ifr_ifru.ifru_mtu;

        return new LinuxTunDevice(fd, mtu, deviceName);
    }

    @Override
    public String name() {
        return name;
    }

    @SuppressWarnings("java:S109")
    @Override
    public TunPacket readPacket(final ByteBufAllocator alloc) throws IOException {
        if (closed) {
            throw new IOException("Device is closed.");
        }

        // read from socket
        final int capacity = mtu.intValue();
        final ByteBuf maxByteBuf = alloc.buffer(capacity).writerIndex(capacity);
        final ByteBuffer byteBuffer = maxByteBuf.nioBuffer();
        final int bytesRead = read(fd, byteBuffer, mtu);

        // shrink bytebuf to actual required size
        final ByteBuf actualByteBuf = maxByteBuf
                .writerIndex(bytesRead)
                .capacity(bytesRead)
                .slice();

        // extract ip version
        final int version = Byte.toUnsignedInt(actualByteBuf.getByte(0)) >> 4;

        if (version == 4) {
            return new Tun4Packet(actualByteBuf);
        }
        else if (version == 6) {
            return new Tun6Packet(actualByteBuf);
        }
        else {
            throw new IOException("Unknown protocol: " + version);
        }
    }

    @Override
    public void writePacket(final ByteBufAllocator alloc, final TunPacket msg) throws IOException {
        if (closed) {
            throw new IOException("Device is closed.");
        }

        // write to socket
        final ByteBuffer byteBuffer = msg.content().nioBuffer();
        write(fd, byteBuffer, new NativeLong(byteBuffer.remaining()));
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;

            // close tun device
            LibC.close(fd);
        }
    }
}
