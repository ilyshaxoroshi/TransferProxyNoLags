/*
 * MIT License
 *
 * Copyright (c) 2026 Yvan Mazy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.transferproxy.network.packet.built;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.transferproxy.api.network.packet.Packet;
import net.transferproxy.api.network.packet.built.ProtocolizedBuiltPacket;
import net.transferproxy.api.network.protocol.Protocolized;
import net.transferproxy.util.BiIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static net.transferproxy.util.BufUtil.writeVarInt;

public class ProtocolizedBuiltPacketImpl implements ProtocolizedBuiltPacket {

    private final IntFunction<Packet> packetFactory;
    private final int[] protocols;
    private final ConcurrentHashMap<Integer, byte[]> dataMap;
    private final boolean lazy;

    public ProtocolizedBuiltPacketImpl(final @NotNull Packet packet, final boolean lazy, final int... protocols) {
        this(u -> packet, lazy, protocols);
        Objects.requireNonNull(packet, "packet must not be null");
    }

    public <T> ProtocolizedBuiltPacketImpl(final @NotNull T value,
                                           final @NotNull BiIntFunction<T, Packet> packetFactory,
                                           final boolean lazy,
                                           final int... protocols) {
        this(u -> packetFactory.apply(u, value), lazy, protocols);
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(packetFactory, "packetFactory must not be null");
    }

    public <T> ProtocolizedBuiltPacketImpl(final @NotNull Supplier<T> supplier,
                                           final @NotNull BiIntFunction<T, Packet> packetFactory,
                                           final boolean lazy,
                                           final int... protocols) {
        this(u -> packetFactory.apply(u, supplier.get()), lazy, protocols);
        Objects.requireNonNull(supplier, "supplier must not be null");
        Objects.requireNonNull(packetFactory, "packetFactory must not be null");
    }

    public ProtocolizedBuiltPacketImpl(final @NotNull IntFunction<Packet> packetFactory, final boolean lazy, final int... protocols) {
        this.packetFactory = Objects.requireNonNull(packetFactory);
        this.protocols = Objects.requireNonNull(protocols, "protocols must not be null");
        this.lazy = lazy;
        if (protocols.length == 0) {
            throw new IllegalArgumentException("Protocols must not be empty. Use BuiltPacket instead if the packet is not protocolized.");
        }
        this.dataMap = new ConcurrentHashMap<>(lazy ? 2 : protocols.length);
        if (!lazy) {
            for (final int protocol : protocols) {
                this.dataMap.put(protocol, this.computeBytes(protocol));
            }
        }
    }

    @Override
    public ByteBuf get(final @NotNull ByteBufAllocator allocator, final int protocol) {
        byte[] data = this.dataMap.get(protocol);
        if (data == null) {
            data = this.resolveData(protocol, allocator);
        }
        final ByteBuf buf = allocator.buffer(data.length, data.length);
        buf.writeBytes(data);
        return buf;
    }

    private byte[] resolveData(final int protocol, final ByteBufAllocator allocator) {
        // Double-check after potential concurrent put
        byte[] data = this.dataMap.get(protocol);
        if (data != null) return data;

        final int targetProtocol;
        if (this.lazy && this.isAvailable(protocol)) {
            targetProtocol = protocol;
        } else {
            targetProtocol = this.findLow(protocol);
        }

        // Check if nearest protocol data already exists
        data = this.dataMap.get(targetProtocol);
        if (data != null) {
            this.dataMap.put(protocol, data);
            return data;
        }

        // Compute and cache
        data = this.computeBytes(targetProtocol, allocator);
        final byte[] existing = this.dataMap.putIfAbsent(targetProtocol, data);
        if (existing != null) {
            data = existing;
        }
        if (targetProtocol != protocol) {
            this.dataMap.put(protocol, data);
        }
        return data;
    }

    private int findLow(final int protocol) {
        int nearLow = this.protocols[0];
        for (final int i : this.protocols) {
            if (i < protocol && i > nearLow) {
                nearLow = i;
            }
        }
        return nearLow;
    }

    @VisibleForTesting
    byte[] computeBytes(final int protocol) {
        return this.computeBytes(protocol, ByteBufAllocator.DEFAULT);
    }

    @VisibleForTesting
    byte[] computeBytes(final int protocol, final @NotNull ByteBufAllocator allocator) {
        final Packet packet = this.packetFactory.apply(protocol);

        final ByteBuf buf = allocator.buffer();
        try {
            writeVarInt(buf, packet.getId());
            packet.write(Protocolized.of(protocol), buf);

            final byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), data);

            return data;
        } finally {
            buf.release();
        }
    }

    private boolean isAvailable(final int protocol) {
        for (final int p : this.protocols) {
            if (p == protocol) {
                return true;
            }
        }
        return false;
    }

}